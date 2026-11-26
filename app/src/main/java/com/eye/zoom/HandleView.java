package com.eye.zoom;

import android.animation.ValueAnimator;
import android.graphics.*;
import android.os.Handler;
import android.os.Looper;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;

/**
 * 悬浮小圆手柄：负责绘制和手势区分。
 * 手指位置一律用屏幕上的真实坐标（getRawX/Y），不用手柄小窗口里的相对坐标。
 * 外观是扁平的：半透明深色圆 + 细白边 + 中心白点。不做任何倾斜/晃动动画；
 * 长按和跟手期间由服务把它藏起来（手指本来就盖在上面），松手后原位出现。
 */
final class HandleView extends View {
    private static final int NONE = 0, DRAG = 1, HOLD = 2, FOLLOW = 3;

    private final ZoomService s;
    private final Paint pBase = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pRing = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pDot = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pGlow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Handler h = new Handler(Looper.getMainLooper());
    private final int slop;

    private float flash;                  // 找回手柄时的闪烁强度 0..1
    private int mode;
    private boolean moved, longFired, zoomedAtDown;
    private float rawX0, rawY0, lastRawX, lastRawY, startHx, startHy, fx0, fy0;
    private long t0;
    private ValueAnimator flashAnim;

    private final Runnable longPress = new Runnable() {
        @Override public void run() {
            if (mode != NONE) return;
            mode = HOLD;
            longFired = true;
            performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);
            s.holdBegin(lastRawX, lastRawY, vx(), vy());
        }
    };

    HandleView(ZoomService svc) {
        super(svc);
        s = svc;
        slop = ViewConfiguration.get(svc).getScaledTouchSlop();
        pBase.setColor(0xCC1B1F24);
        pRing.setStyle(Paint.Style.STROKE);
        pRing.setColor(0x40FFFFFF);
        pDot.setColor(0xEBFFFFFF);
        pGlow.setStyle(Paint.Style.STROKE);
        pGlow.setColor(0xFF4FC3F7);
    }

    @Override protected void onDetachedFromWindow() {
        h.removeCallbacks(longPress);
        if (flashAnim != null) flashAnim.cancel();
        super.onDetachedFromWindow();
    }

    @Override protected void onDraw(Canvas c) {
        float w = getWidth(), r = w / 2f;
        float sw = Math.max(1f, w * 0.035f);
        pRing.setStrokeWidth(sw);
        c.drawCircle(r, r, r - 1f, pBase);
        c.drawCircle(r, r, r - 1f - sw / 2f, pRing);
        if (flash > 0f) {
            float gw = Math.max(2f, w * 0.12f);
            pGlow.setStrokeWidth(gw);
            pGlow.setAlpha(Math.round(255 * flash));
            c.drawCircle(r, r, r - gw / 2f, pGlow);
        }
        c.drawCircle(r, r, w * 0.17f, pDot);
    }

    /** 闪几下亮蓝色圈，让人一眼找到手柄（只在“找回手柄”时用）。 */
    void flash() {
        if (flashAnim != null) flashAnim.cancel();
        flashAnim = ValueAnimator.ofFloat(0f, 1f, 0f, 1f, 0f, 1f, 0f);
        flashAnim.setDuration(2400);
        flashAnim.addUpdateListener(a -> { flash = (float) a.getAnimatedValue(); invalidate(); });
        flashAnim.start();
    }

    /** 未放大时手指现在的真实屏幕位置 = 按下时的位置 + 之后手指移动的距离（此时原始坐标就是真实坐标）。 */
    private float vx() { return fx0 + lastRawX - rawX0; }
    private float vy() { return fy0 + lastRawY - rawY0; }

    @Override public boolean onTouchEvent(MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                moved = false; longFired = false; mode = NONE;
                rawX0 = lastRawX = e.getRawX();
                rawY0 = lastRawY = e.getRawY();
                t0 = e.getEventTime();
                s.touchBegin();
                zoomedAtDown = s.zoomed();
                startHx = s.hx(); startHy = s.hy();
                // 手指按下时在屏幕上的位置：未放大时手柄窗口位置固定，用它算最可靠
                fx0 = startHx + e.getX(); fy0 = startHy + e.getY();
                if (zoomedAtDown) s.calibrateDown(rawX0, rawY0, e.getX(), e.getY());
                else
                    h.postDelayed(longPress, ViewConfiguration.getLongPressTimeout() * 2 / 3);
                return true;

            case MotionEvent.ACTION_MOVE:
                lastRawX = e.getRawX();
                lastRawY = e.getRawY();
                if (!moved && mode != HOLD
                        && Math.hypot(lastRawX - rawX0, lastRawY - rawY0) > slop) moved = true;
                if (mode == NONE && moved) {
                    h.removeCallbacks(longPress);
                    if (zoomedAtDown) { mode = FOLLOW; s.followBegin(lastRawX, lastRawY); }
                    else mode = DRAG;
                }
                if (mode == DRAG) s.moveHandle(startHx + lastRawX - rawX0, startHy + lastRawY - rawY0);
                else if (mode == HOLD || mode == FOLLOW) s.trackTo(lastRawX, lastRawY);
                return true;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                h.removeCallbacks(longPress);
                boolean tap = e.getActionMasked() == MotionEvent.ACTION_UP && !moved && !longFired
                        && e.getEventTime() - t0 < 300;
                if (mode == HOLD) s.holdEnd();
                else if (mode == FOLLOW) s.followEnd();
                else if (mode == DRAG) s.savePos();
                mode = NONE;
                s.touchEnd();
                if (tap) s.onTap();
                return true;
        }
        return true;
    }
}
