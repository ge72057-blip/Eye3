package com.eye.zoom;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityService.MagnificationController;
import android.accessibilityservice.MagnificationConfig;
import android.content.res.Configuration;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.Region;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.DisplayMetrics;
import android.view.Choreographer;
import android.view.ContextThemeWrapper;
import android.view.Gravity;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;

/**
 * 核心服务：用系统官方的放大接口做缩放，手柄画在无障碍悬浮层上。
 * 手柄在屏幕上永远固定：放大时按倍数反向缩小窗口，抵消系统的放大。
 *
 * 放大区域的控制：手指在屏幕上移动多少，放大区域就按“灵敏度”乘上一个系数跟着走（相对移动）。
 * 手指不需要走到屏幕边缘，灵敏度越高，动一点点就能走完全程。
 * 长按（松手恢复）和双击后按住手柄移动（松手保持）用的是同一套逻辑。
 *
 * 系统放大和我们自己的手柄窗口没法在同一帧更新，所以放大/缩小的瞬间、以及手指控制期间，
 * 手柄会先藏起来，等一切稳定后原位显示，避免出现闪烁、错位。
 */
public class ZoomService extends AccessibilityService {
    private static final long COVER_MS = 50;      // 先藏手柄，等这么久再放大（让“藏”先生效）
    private static final long REVEAL_MS = 120;    // 放大状态稳定后，再等这么久显示手柄
    private static final long ZOOM_MS = 220;      // 进入放大的过渡时间（由慢到快再缓停，不是瞬间跳到位）
    private static final long SETTLE_MS = 120;    // 放大刚生效后，等系统把新的触摸换算同步好，再开始跟手

    private static ZoomService inst;
    static ZoomService get() { return inst; }

    private final Handler h = new Handler(Looper.getMainLooper());
    private WindowManager wm;
    private Prefs pf;
    private MagnificationController mc;
    private HandleView handle;
    private PanelView panel;
    private WindowManager.LayoutParams hlp;
    private boolean handleAdded;
    private long handleAddedAt;

    private int W, H, size;
    private float hx, hy;                         // 手柄左上角（屏幕坐标）
    private float curS = 1f, curCx, curCy;        // 放大状态（手指控制时是我们设定的值，否则取系统回报）
    private boolean tapPending;

    // ---- 跟手 ----
    private boolean tracking, frameOn, pendingHold, trackNeedScale, revealWhenUnzoomed;
    // tfx/tfy = 手指最新的“原始坐标”，lastFx/lastFy = 上一帧用过的原始坐标
    private float trackS, tfx, tfy, lastFx, lastFy, ccx, ccy, holdX0, holdY0, holdPX, holdPY;
    // 旧版安卓（实测安卓 10）放大后，getRawX/Y 会被除以放大倍数，新版安卓（实测安卓 16）则是真实屏幕坐标。
    // rawFactor 把“原始坐标的变化”换算成“真实屏幕像素的变化”；rawMode 0=未知 1=真实坐标 2=被除以倍数
    private int rawMode;
    private float rawFactor = 1f;
    private long settleUntil;
    // 进入放大的过渡动画：从当前倍数平滑变到目标倍数，围绕参照点放大（该点的内容原地不动）
    private long animT0;
    private float animS0, animPX, animPY;
    private boolean keepAfterAnim;   // true = 双击放大：过渡结束后停止，保持放大
    private boolean calibPending;

    private final MagnificationController.OnMagnificationChangedListener listener =
            new MagnificationController.OnMagnificationChangedListener() {
                @Override public void onMagnificationChanged(MagnificationController c, Region r,
                                                             float scale, float cx, float cy) {
                    if (tracking) return;   // 手指控制时系统回报会滞后，用了会让手柄抖
                    curS = scale; curCx = cx; curCy = cy;
                    layoutHandle();
                    if (revealWhenUnzoomed && scale <= 1.001f) {
                        revealWhenUnzoomed = false;
                        h.removeCallbacks(reveal);
                        h.postDelayed(reveal, REVEAL_MS);
                    }
                }
            };

    private final Runnable singleTap = () -> { tapPending = false; if (!zoomed()) togglePanel(); };
    private final Runnable fade = () -> {
        if (panel == null && handle != null)
            handle.animate().alpha(pf.fade()).setDuration(500).start();
    };

    /** 放大状态稳定后，把手柄放回原位并显示。 */
    private final Runnable reveal = () -> {
        revealWhenUnzoomed = false;
        if (handle == null || tracking) return;
        layoutHandle();
        handle.animate().cancel();
        handle.setAlpha(1f);
        scheduleFade();
    };

    private final Runnable startHold = () -> {
        if (!pendingHold) return;
        pendingHold = false;
        float s = pf.scale();
        // 在按下的位置平滑放大：该位置的内容原地不动
        animS0 = Math.max(1f, mc.getScale());
        animPX = holdX0; animPY = holdY0;
        animT0 = SystemClock.uptimeMillis();
        keepAfterAnim = false;
        beginTrack(s, centerX(s, holdX0), centerY(s, holdY0), tfx, tfy);
        // 过渡期间以及刚放大完的一小段时间里，系统给我们的触摸坐标可能还没换算同步，先不跟手；
        // 等稳定后再以当时的手指位置为基准，并顺便判断本机的坐标是哪一种
        settleUntil = animT0 + ZOOM_MS + SETTLE_MS;
        calibPending = true;
    };

    private final Runnable doZoomKeep = () -> {
        float s = pf.scale();
        trackS = s;
        animS0 = Math.max(1f, mc.getScale());
        animPX = hcx(); animPY = hcy();
        animT0 = SystemClock.uptimeMillis();
        keepAfterAnim = true;
        settleUntil = 0;
        calibPending = false;
        trackNeedScale = true;
        tracking = true;
        applyTrack();
        if (!frameOn) { frameOn = true; Choreographer.getInstance().postFrameCallback(this.frame); }   // this.frame：frame 写在后面，直接写名字会被判为前向引用
    };

    private final Runnable doZoomOut = () -> {
        revealWhenUnzoomed = true;
        mc.reset(true);
        h.removeCallbacks(reveal);
        h.postDelayed(reveal, 800);      // 兜底：万一系统没回报缩回完成
    };

    private final Choreographer.FrameCallback frame = new Choreographer.FrameCallback() {
        @Override public void doFrame(long t) {
            if (!tracking) { frameOn = false; return; }
            applyTrack();
            Choreographer.getInstance().postFrameCallback(this);
        }
    };

    @Override protected void onServiceConnected() {
        super.onServiceConnected();
        teardown();   // 保险：系统重复回调时先清掉旧的
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        pf = new Prefs(this);
        mc = getMagnificationController();
        if (mc == null) return;
        mc.addListener(listener);
        readScreen();
        curS = 1f; curCx = W / 2f; curCy = H / 2f;
        size = Math.round(pf.sizeDp() * dens());
        int px = pf.get(Prefs.PX, -1), py = pf.get(Prefs.PY, -1);
        defaultPosition();
        if (px >= 0) hx = px;
        if (py >= 0) hy = py;
        clampHandle();
        if (pf.showHandle()) buildHandle();
        inst = this;
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent e) {}
    @Override public void onInterrupt() {}

    @Override public boolean onUnbind(android.content.Intent i) { teardown(); return super.onUnbind(i); }
    @Override public void onDestroy() { teardown(); super.onDestroy(); }

    private void teardown() {
        tracking = false;
        animT0 = 0;
        keepAfterAnim = false;
        tapPending = false;
        h.removeCallbacksAndMessages(null);
        try { if (mc != null) { mc.removeListener(listener); mc.reset(false); } } catch (Exception ignored) {}
        closePanel();
        removeHandle();
        if (inst == this) inst = null;
    }

    @Override public void onConfigurationChanged(Configuration c) {
        super.onConfigurationChanged(c);
        if (mc == null) return;
        tracking = false;
        animT0 = 0;
        keepAfterAnim = false;
        pendingHold = false;
        try { mc.reset(false); } catch (Exception ignored) {}
        readScreen();
        curS = 1f; curCx = W / 2f; curCy = H / 2f;
        if (!pf.showHandle()) return;
        clampHandle();
        ensureHandle();
        layoutHandle();
    }

    // ---------- 屏幕与手柄布局 ----------
    private float dens() { return getResources().getDisplayMetrics().density; }

    private void readScreen() {
        if (Build.VERSION.SDK_INT >= 30) {
            Rect b = wm.getMaximumWindowMetrics().getBounds();
            W = b.width(); H = b.height();
        } else {
            DisplayMetrics m = new DisplayMetrics();
            wm.getDefaultDisplay().getRealMetrics(m);
            W = m.widthPixels; H = m.heightPixels;
        }
    }

    private void defaultPosition() {
        hx = W - size - 8 * dens();
        hy = H * 0.4f;
    }

    private void clampHandle() {
        hx = Math.max(0, Math.min(W - size, hx));
        hy = Math.max(0, Math.min(H - size, hy));
    }

    private void buildHandle() {
        handle = new HandleView(this);
        hlp = new WindowManager.LayoutParams(size, size,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_SPLIT_TOUCH   // 一根手指按着手柄时，另一根手指可以操作下面的应用
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        hlp.gravity = Gravity.TOP | Gravity.LEFT;
        if (Build.VERSION.SDK_INT >= 28)
            hlp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        layoutHandle0();
        try {
            wm.addView(handle, hlp);
            handleAdded = true;
            handleAddedAt = SystemClock.uptimeMillis();
        } catch (Exception e) {
            handle = null;
            handleAdded = false;
            return;
        }
        scheduleFade();
    }

    private void removeHandle() {
        if (handle != null && handleAdded) {
            try { wm.removeViewImmediate(handle); } catch (Exception ignored) {}
        }
        handle = null;
        handleAdded = false;
    }

    /** 手柄窗口如果被系统弄丢了就重建。刚添加不久的不算丢（窗口挂载需要一点时间）。 */
    private void ensureHandle() {
        if (handle != null && handleAdded
                && (handle.isAttachedToWindow() || SystemClock.uptimeMillis() - handleAddedAt < 1500)) return;
        removeHandle();
        buildHandle();
    }

    private boolean layoutHandle0() {
        float s = Math.max(curS, 1f);
        float ox = s <= 1.001f ? 0 : curCx - W / (2f * s);
        float oy = s <= 1.001f ? 0 : curCy - H / (2f * s);
        int x = Math.round(ox + hx / s), y = Math.round(oy + hy / s);
        int w = Math.max(1, Math.round(size / s));
        boolean changed = x != hlp.x || y != hlp.y || w != hlp.width || w != hlp.height;
        hlp.x = x; hlp.y = y; hlp.width = hlp.height = w;
        return changed;
    }

    private void layoutHandle() {
        if (handle == null || !handleAdded) return;
        if (!layoutHandle0()) return;
        try { wm.updateViewLayout(handle, hlp); } catch (Exception ignored) {}
    }

    void applySize() {
        size = Math.round(pf.sizeDp() * dens());
        clampHandle();
        layoutHandle();
    }

    // ---------- 给 HandleView 用的状态 ----------
    int size() { return size; }
    float hx() { return hx; }
    float hy() { return hy; }
    float hcx() { return hx + size / 2f; }
    float hcy() { return hy + size / 2f; }
    boolean zoomed() { return mc != null && mc.getScale() > 1.01f; }

    void moveHandle(float x, float y) { hx = x; hy = y; clampHandle(); layoutHandle(); }
    void savePos() { pf.set(Prefs.PX, Math.round(hx)); pf.set(Prefs.PY, Math.round(hy)); }

    // ---------- 变透明 / 藏起来 ----------
    void touchBegin() {
        if (handle == null) return;
        if (!zoomed() && !tracking) {
            // 每次按下都重新读一遍屏幕尺寸，防止尺寸记录过期导致范围不对
            int w0 = W, h0 = H;
            readScreen();
            if (w0 != W || h0 != H) { clampHandle(); layoutHandle(); }
        }
        h.removeCallbacks(fade);
        handle.animate().cancel();
        handle.setAlpha(1f);
    }
    void touchEnd() { scheduleFade(); }
    private void scheduleFade() {
        h.removeCallbacks(fade);
        if (panel == null) h.postDelayed(fade, pf.idleMs());
    }
    private void hideHandle() {
        if (handle == null) return;
        h.removeCallbacks(fade);
        handle.animate().cancel();
        handle.setAlpha(0f);
    }

    // ---------- 放大：手指相对移动 → 放大区域相对移动 ----------
    private float centerX(float s, float fx) { return fx / W * (W - W / s) + W / (2f * s); }
    private float centerY(float s, float fy) { return fy / H * (H - H / s) + H / (2f * s); }
    private float clampX(float v) { return Math.max(0, Math.min(W, v)); }
    private float clampY(float v) { return Math.max(0, Math.min(H, v)); }

    /** 一次性把倍数和中心交给系统。安卓 14 以上用单个调用，避免“先放大、再挪位置”之间闪一帧。 */
    private void applyMag(float s, float cx, float cy, boolean needScale) {
        if (Build.VERSION.SDK_INT >= 34) {
            MagnificationConfig c = new MagnificationConfig.Builder()
                    .setMode(MagnificationConfig.MAGNIFICATION_MODE_FULLSCREEN)
                    .setScale(s).setCenterX(cx).setCenterY(cy).build();
            mc.setMagnificationConfig(c, false);
        } else {
            if (needScale) mc.setScale(s, false);
            mc.setCenter(cx, cy, false);
        }
    }

    private void beginTrack(float s, float cx0, float cy0, float rawX, float rawY) {
        trackS = s;
        ccx = cx0; ccy = cy0;
        tfx = lastFx = rawX; tfy = lastFy = rawY;
        rawFactor = rawMode == 2 ? s : 1f;
        settleUntil = 0;
        calibPending = false;
        trackNeedScale = Math.abs(mc.getScale() - s) > 0.001f;
        tracking = true;
        applyTrack();
        if (!frameOn) { frameOn = true; Choreographer.getInstance().postFrameCallback(frame); }
    }

    /** 判断本机放大后 getRawX/Y 是真实坐标还是被除以了倍数：用已知的真实屏幕位置和同一时刻的原始坐标比一比。 */
    private void calibrateRaw(float rawX, float rawY, float physX, float physY, float s) {
        float den = rawX + rawY, num = physX + physY;
        if (s < 1.2f || den < 50f || num < 50f) return;     // 太靠近左上角，比值不可靠，保持原判断
        rawMode = (num / den) > Math.sqrt(s) ? 2 : 1;
        rawFactor = rawMode == 2 ? s : 1f;
    }

    /** 已放大时按下手柄：窗口此刻稳定地固定在屏幕上，可以用它换算出真实位置来判断坐标类型。 */
    void calibrateDown(float rawX, float rawY, float lx, float ly) {
        float s = curS;
        if (!zoomed() || s < 1.2f) return;
        calibrateRaw(rawX, rawY, hx + lx * s, hy + ly * s, s);
    }

    /** 每一帧：手指这一帧动了多少（换算成真实像素），放大区域就按灵敏度动多少；到边就停，手指一回头立刻往回走。 */
    private void applyTrack() {
        float s = trackS;
        if (animT0 != 0) {
            float u = (SystemClock.uptimeMillis() - animT0) / (float) ZOOM_MS;
            boolean done = u >= 1f;
            float e = done ? 1f : 1f - (1f - u) * (1f - u) * (1f - u);      // 先快后慢，缓缓停住
            float sNow = Math.max(1.01f, animS0 + (s - animS0) * e);
            float cx = centerX(sNow, animPX), cy = centerY(sNow, animPY);
            applyMag(sNow, cx, cy, true);
            trackNeedScale = false;
            curS = sNow; curCx = ccx = cx; curCy = ccy = cy;
            lastFx = tfx; lastFy = tfy;
            if (done) {
                animT0 = 0;
                if (keepAfterAnim) {            // 双击放大：到位后停止，等系统状态稳定再显示手柄
                    keepAfterAnim = false;
                    tracking = false;
        animT0 = 0;
        keepAfterAnim = false;
                    layoutHandle();
                    h.removeCallbacks(reveal);
                    h.postDelayed(reveal, REVEAL_MS);
                }
            }
            return;
        }
        if (settleUntil != 0) {
            if (SystemClock.uptimeMillis() < settleUntil) {
                lastFx = tfx; lastFy = tfy;
            } else {
                settleUntil = 0;
                if (calibPending) { calibPending = false; calibrateRaw(tfx, tfy, holdPX, holdPY, s); }
                lastFx = tfx; lastFy = tfy;
            }
        } else {
            float g = pf.sens() * (1f - 1f / s) * rawFactor;   // 原始坐标 1 单位 → 放大区域中心移动多少像素
            float minX = W / (2f * s), minY = H / (2f * s);
            ccx = Math.max(minX, Math.min(W - minX, ccx + (tfx - lastFx) * g));
            ccy = Math.max(minY, Math.min(H - minY, ccy + (tfy - lastFy) * g));
            lastFx = tfx; lastFy = tfy;
        }
        if (!trackNeedScale && Math.abs(s - curS) < 0.001f
                && Math.abs(ccx - curCx) < 0.05f && Math.abs(ccy - curCy) < 0.05f) return;
        applyMag(s, ccx, ccy, trackNeedScale);
        trackNeedScale = false;
        curS = s; curCx = ccx; curCy = ccy;   // 手柄此时是藏起来的，等松手后再统一摆放
    }

    private void endTrack() {
        if (!tracking) return;
        applyTrack();   // 把最后一个手指位置落实
        tracking = false;
        animT0 = 0;
        keepAfterAnim = false;
    }

    /** 长按：手柄先藏起来，再在按下的位置放大，之后手指怎么动放大区域怎么走。
     *  rawX/Y = 手指原始坐标；physX/Y = 此刻手指在屏幕上的真实位置（按下时没放大，由手柄位置算出）。 */
    void holdBegin(float rawX, float rawY, float physX, float physY) {
        hideHandle();
        pendingHold = true;
        holdPX = physX; holdPY = physY;
        holdX0 = clampX(physX);       // 放大中心的参照点
        holdY0 = clampY(physY);
        tfx = lastFx = rawX;
        tfy = lastFy = rawY;
        h.removeCallbacks(startHold);
        h.postDelayed(startHold, COVER_MS);
    }

    /** 已放大时按住手柄开始移动：从当前画面接着走，不会跳变。 */
    void followBegin(float rawX, float rawY) {
        hideHandle();
        beginTrack(Math.max(1.05f, mc.getScale()), mc.getCenterX(), mc.getCenterY(), rawX, rawY);
    }

    void trackTo(float rawX, float rawY) { tfx = rawX; tfy = rawY; }

    /** 长按松手：恢复原样，缩回完成后手柄原位出现。 */
    void holdEnd() {
        h.removeCallbacks(startHold);
        if (pendingHold) {                 // 还没来得及放大就松手了
            pendingHold = false;
            h.postDelayed(reveal, 0);
            return;
        }
        tracking = false;
        animT0 = 0;
        keepAfterAnim = false;
        settleUntil = 0;
        revealWhenUnzoomed = true;
        mc.reset(true);
        h.removeCallbacks(reveal);
        h.postDelayed(reveal, 800);        // 兜底
    }

    /** 跟手松手：保持当前放大，手柄原位出现。 */
    void followEnd() {
        endTrack();
        curS = mc.getScale(); curCx = mc.getCenterX(); curCy = mc.getCenterY();
        layoutHandle();
        h.removeCallbacks(reveal);
        h.postDelayed(reveal, REVEAL_MS);
    }

    // ---------- 点击：单击开面板，双击放大/复原 ----------
    void onTap() {
        if (tapPending) {
            h.removeCallbacks(singleTap);
            tapPending = false;
            hideHandle();                  // 缩放瞬间手柄窗口跟不上，先藏起来
            h.postDelayed(zoomed() ? doZoomOut : doZoomKeep, COVER_MS);
        } else {
            tapPending = true;
            h.postDelayed(singleTap, 280);
        }
    }

    // ---------- 找回手柄（设置页的“恢复”按钮调用） ----------
    /** 退出放大、手柄回到默认位置、完全显示并闪几下；手柄窗口丢了会重建。 */
    void restoreHandle() {
        pf.set(Prefs.SHOW, 1);      // “找回手柄”同时会把悬浮球开关打开
        tracking = false;
        animT0 = 0;
        keepAfterAnim = false;
        pendingHold = false;
        revealWhenUnzoomed = false;
        h.removeCallbacks(singleTap);
        h.removeCallbacks(startHold);
        h.removeCallbacks(doZoomKeep);
        h.removeCallbacks(doZoomOut);
        h.removeCallbacks(reveal);
        tapPending = false;
        try { mc.reset(false); } catch (Exception ignored) {}
        closePanel();
        readScreen();
        curS = 1f; curCx = W / 2f; curCy = H / 2f;
        pf.remove(Prefs.PX, Prefs.PY);
        size = Math.round(pf.sizeDp() * dens());
        defaultPosition();
        clampHandle();
        ensureHandle();
        layoutHandle();
        if (handle == null) return;
        touchBegin();
        handle.flash();
        h.removeCallbacks(fade);
        h.postDelayed(fade, Math.max(pf.idleMs(), 6000));
    }

    /** 设置页的“悬浮球”开关：关 = 把手柄藏掉（服务仍在运行），开 = 原位重新显示。 */
    void setHandleVisible(boolean on) {
        pf.set(Prefs.SHOW, on ? 1 : 0);
        if (mc == null) return;
        tracking = false;
        animT0 = 0;
        keepAfterAnim = false;
        pendingHold = false;
        revealWhenUnzoomed = false;
        tapPending = false;
        h.removeCallbacks(singleTap);
        h.removeCallbacks(startHold);
        h.removeCallbacks(doZoomKeep);
        h.removeCallbacks(doZoomOut);
        h.removeCallbacks(reveal);
        if (on) {
            readScreen();
            clampHandle();
            ensureHandle();
            layoutHandle();
            if (handle != null) {
                touchBegin();
                handle.flash();
                h.removeCallbacks(fade);
                h.postDelayed(fade, Math.max(pf.idleMs(), 6000));
            }
        } else {
            try { mc.reset(false); } catch (Exception ignored) {}
            closePanel();
            removeHandle();
            curS = 1f; curCx = W / 2f; curCy = H / 2f;
        }
    }

    // ---------- 设置面板 ----------
    private void togglePanel() { if (panel != null) closePanel(); else openPanel(); }

    private void openPanel() {
        if (handle == null) return;
        h.removeCallbacks(fade);
        handle.animate().cancel();
        handle.setAlpha(1f);
        panel = new PanelView(new ContextThemeWrapper(this, android.R.style.Theme_DeviceDefault), this, pf);
        int w = Math.min(Math.round(W * 0.9f), Math.round(340 * dens()));
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(w,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.CENTER;
        try { wm.addView(panel, lp); } catch (Exception e) { panel = null; scheduleFade(); }
    }

    void closePanel() {
        if (panel == null) return;
        try { wm.removeView(panel); } catch (Exception ignored) {}
        panel = null;
        scheduleFade();
    }
}
