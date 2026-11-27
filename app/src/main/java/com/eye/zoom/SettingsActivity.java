package com.eye.zoom;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** 桌面图标打开的设置页：状态、恢复、调节、保活、操作说明。全部用代码生成，不依赖界面库。 */
public class SettingsActivity extends Activity {
    private static final String ADB_CMD = "adb shell pm grant com.eye.zoom android.permission.WRITE_SECURE_SETTINGS";
    // 各家手机“自启动管理”页面（尽力而为：打不开就换下一个，全都打不开就进应用信息页）
    private static final String[][] AUTOSTART = {
            // 小米 / Redmi / POCO
            {"com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"},
            // 华为 / 荣耀
            {"com.huawei.systemmanager", "com.huawei.systemmanager.appcontrol.activity.StartupAppControlActivity"},
            {"com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"},
            {"com.huawei.systemmanager", "com.huawei.systemmanager.optimize.bootstart.BootStartActivity"},
            {"com.huawei.systemmanager", "com.huawei.systemmanager.optimize.process.ProtectActivity"},
            // OPPO / realme / 一加（ColorOS）
            {"com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity"},
            {"com.coloros.safecenter", "com.coloros.safecenter.startupapp.StartupAppListActivity"},
            {"com.oppo.safe", "com.oppo.safe.permission.startup.StartupAppListActivity"},
            {"com.oneplus.security", "com.oneplus.security.chainlaunch.view.ChainLaunchAppListActivity"},
            // vivo / iQOO（先试真正的“后台自启动”，白名单页放最后）
            {"com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"},
            {"com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.BgStartUpManager"},
            {"com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity"},
            // 三星
            {"com.samsung.android.lool", "com.samsung.android.sm.ui.battery.BatteryActivity"},
            {"com.samsung.android.lool", "com.samsung.android.sm.battery.ui.BatteryActivity"},
            {"com.samsung.android.lool", "com.samsung.android.sm.battery.ui.usage.CheckableAppListActivity"},
            {"com.samsung.android.sm_cn", "com.samsung.android.sm.ui.ram.AutoRunActivity"},
            // 魅族 / 华硕 / 联想 / 乐视 / 诺基亚 / 传音（后几项来源不一，仅作候选）
            {"com.meizu.safe", "com.meizu.safe.permission.SelfStartManagementActivity"},
            {"com.meizu.safe", "com.meizu.safe.permission.SmartBGActivity"},
            {"com.meizu.safe", "com.meizu.safe.permission.PermissionMainActivity"},
            {"com.asus.mobilemanager", "com.asus.mobilemanager.autostart.AutoStartActivity"},
            {"com.asus.mobilemanager", "com.asus.mobilemanager.powersaver.PowerSaverSettings"},
            {"com.lenovo.security", "com.lenovo.security.purebackground.PureBackgroundActivity"},
            {"com.letv.android.letvsafe", "com.letv.android.letvsafe.AutobootManageActivity"},
            {"com.evenwell.powersaving.g3", "com.evenwell.powersaving.g3.exception.PowerSaverExceptionActivity"},
            {"com.transsion.phonemaster", "com.itel.autobootmanager.activity.AutoBootMgrActivity"},
    };

    // 厂商安全/手机管家类应用（只用来按名字找自启动页面；需要在清单里声明 queries 才看得见）
    static final String[] SEC_PKGS = {
            "com.miui.securitycenter", "com.huawei.systemmanager", "com.hihonor.systemmanager",
            "com.coloros.safecenter", "com.oplus.safecenter", "com.oppo.safe", "com.oplus.battery",
            "com.coloros.oppoguardelf", "com.oneplus.security", "com.vivo.permissionmanager",
            "com.iqoo.secure", "com.vivo.abe", "com.samsung.android.lool", "com.samsung.android.sm_cn",
            "com.asus.mobilemanager", "com.transsion.phonemaster", "com.meizu.safe", "com.zte.heartyservice",
            "com.lenovo.security", "com.letv.android.letvsafe", "com.evenwell.powersaving.g3"};
    private static final String[] KEYS = {"autostart", "autorun", "startupapp", "startupmgr", "bgstartup",
            "bootstart", "chainlaunch", "startup"};

    private final Handler h = new Handler(Looper.getMainLooper());
    private Prefs p;
    private View dot;
    private TextView stTitle, stDesc, batteryState;
    private LinearLayout stButtons;
    private int shownState = -1;
    private Switch ballSwitch;
    private boolean updatingBall;

    private final Runnable poll = new Runnable() {
        @Override public void run() { refresh(); h.postDelayed(this, 1000); }
    };

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        p = new Prefs(this);
        getWindow().setStatusBarColor(Ui.BG);
        getWindow().setNavigationBarColor(Ui.BG);
        build();
        int st = A11y.state(this);
        if (st != A11y.RUNNING) {
            if (A11y.canWriteSecure(this)) h.postDelayed(this::autoHeal, 1200);
            else if (p.get(Prefs.GUIDE, 0) == 0) { p.set(Prefs.GUIDE, 1); showGuide(); }
        }
    }

    @Override protected void onResume() { super.onResume(); h.post(poll); }
    @Override protected void onPause() { h.removeCallbacks(poll); super.onPause(); }
    @Override protected void onDestroy() { h.removeCallbacksAndMessages(null); super.onDestroy(); }

    // ---------- 界面 ----------
    private void build() {
        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(Ui.BG);
        sv.setFillViewport(true);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = Ui.dp(this, 16);
        root.setPadding(pad, Ui.dp(this, 20), pad, Ui.dp(this, 28));
        sv.addView(root, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView title = Ui.text(this, getString(R.string.app_name), 30, Ui.TEXT);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        root.addView(title);
        root.addView(Ui.text(this, getString(R.string.settings_subtitle), 14, Ui.SUB));

        // 状态
        LinearLayout st = Ui.card(this);
        TextView secSt = section(R.string.sec_status);
        st.addView(secSt);
        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(android.view.Gravity.CENTER_VERTICAL);
        head.setPadding(0, Ui.dp(this, 10), 0, 0);
        dot = new View(this);
        head.addView(dot, new LinearLayout.LayoutParams(Ui.dp(this, 12), Ui.dp(this, 12)));
        stTitle = Ui.text(this, "", 18, Ui.TEXT);
        stTitle.setTypeface(Typeface.DEFAULT_BOLD);
        stTitle.setPadding(Ui.dp(this, 10), 0, 0, 0);
        head.addView(stTitle);
        st.addView(head);
        stDesc = Ui.text(this, "", 14, Ui.SUB);
        stDesc.setPadding(0, Ui.dp(this, 6), 0, 0);
        st.addView(stDesc);
        stButtons = new LinearLayout(this);
        stButtons.setOrientation(LinearLayout.VERTICAL);
        st.addView(stButtons);
        root.addView(st);

        // 悬浮球开关
        LinearLayout bc = Ui.card(this);
        LinearLayout brow = new LinearLayout(this);
        brow.setOrientation(LinearLayout.HORIZONTAL);
        brow.setGravity(android.view.Gravity.CENTER_VERTICAL);
        LinearLayout btexts = new LinearLayout(this);
        btexts.setOrientation(LinearLayout.VERTICAL);
        TextView btitle = Ui.text(this, getString(R.string.ball_title), 17, Ui.TEXT);
        btitle.setTypeface(Typeface.DEFAULT_BOLD);
        btexts.addView(btitle);
        TextView bdesc = Ui.text(this, getString(R.string.ball_desc), 13, Ui.SUB);
        bdesc.setPadding(0, Ui.dp(this, 4), Ui.dp(this, 12), 0);
        btexts.addView(bdesc);
        brow.addView(btexts, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        ballSwitch = new Switch(this);
        ballSwitch.setThumbTintList(new ColorStateList(new int[][]{{android.R.attr.state_checked}, {}},
                new int[]{0xFFFFFFFF, 0xFFB8C0CC}));
        ballSwitch.setTrackTintList(new ColorStateList(new int[][]{{android.R.attr.state_checked}, {}},
                new int[]{Ui.ACCENT, 0x55FFFFFF}));
        ballSwitch.setChecked(p.showHandle());
        ballSwitch.setOnCheckedChangeListener((b, on) -> {
            if (updatingBall) return;
            p.set(Prefs.SHOW, on ? 1 : 0);
            ZoomService z = ZoomService.get();
            if (z != null) z.setHandleVisible(on);
            else toast(R.string.toast_ball_pending);
        });
        brow.addView(ballSwitch);
        bc.addView(brow);
        root.addView(bc);

        // 恢复
        LinearLayout rc = Ui.card(this);
        rc.addView(section(R.string.sec_restore));
        rc.addView(body(R.string.restore_desc));
        rc.addView(Ui.button(this, getString(R.string.btn_restore_handle), true, v -> restoreHandle()));
        rc.addView(Ui.button(this, getString(R.string.btn_reset_all), false, v -> confirmReset()));
        root.addView(rc);

        // 调节
        LinearLayout ad = Ui.card(this);
        ad.addView(section(R.string.sec_adjust));
        Ui.slider(ad, R.string.label_zoom, 0, 65, p.get(Prefs.SCALE, Prefs.D_SCALE),
                v -> getString(R.string.fmt_zoom, 1.5f + v / 10f), v -> p.set(Prefs.SCALE, v));
        Ui.slider(ad, R.string.label_sens, 1, 200, p.get(Prefs.SENS, Prefs.D_SENS),
                v -> getString(R.string.fmt_zoom, v / 10f), v -> p.set(Prefs.SENS, v));
        TextView sensHint = Ui.text(this, getString(R.string.sens_hint), 12, Ui.SUB);
        sensHint.setPadding(0, Ui.dp(this, 4), 0, 0);
        ad.addView(sensHint);
        Ui.slider(ad, R.string.label_steady, 0, 10, p.get(Prefs.STEADY, Prefs.D_STEADY),
                v -> v == 0 ? this.getString(R.string.off) : String.valueOf(v), v -> p.set(Prefs.STEADY, v));
        TextView steadyHint = Ui.text(this, getString(R.string.steady_hint), 12, Ui.SUB);
        steadyHint.setPadding(0, Ui.dp(this, 4), 0, 0);
        ad.addView(steadyHint);
        Ui.slider(ad, R.string.label_idle, 1, 30, p.get(Prefs.IDLE, Prefs.D_IDLE),
                v -> getString(R.string.fmt_sec, v), v -> p.set(Prefs.IDLE, v));
        Ui.slider(ad, R.string.label_fade, 5, 100, p.get(Prefs.FADE, Prefs.D_FADE),
                v -> getString(R.string.fmt_pct, v), v -> p.set(Prefs.FADE, v));
        Ui.slider(ad, R.string.label_size, 36, 96, p.get(Prefs.SIZE, Prefs.D_SIZE),
                v -> getString(R.string.fmt_dp, v), v -> {
                    p.set(Prefs.SIZE, v);
                    ZoomService z = ZoomService.get();
                    if (z != null) z.applySize();
                });
        TextView hint = Ui.text(this, getString(R.string.adjust_hint), 12, Ui.SUB);
        hint.setPadding(0, Ui.dp(this, 10), 0, 0);
        ad.addView(hint);
        root.addView(ad);

        // 保活
        LinearLayout al = Ui.card(this);
        al.addView(section(R.string.sec_alive));
        al.addView(body(R.string.alive_desc));
        al.addView(sub(R.string.alive_battery));
        batteryState = Ui.text(this, "", 13, Ui.SUB);
        al.addView(batteryState);
        al.addView(Ui.button(this, getString(R.string.btn_battery), false, v -> openBattery()));
        al.addView(sub(R.string.alive_autostart));
        al.addView(small(R.string.alive_autostart_desc));
        al.addView(Ui.button(this, getString(R.string.btn_autostart), false, v -> openAutostart()));
        al.addView(sub(R.string.alive_lock));
        al.addView(small(R.string.alive_lock_desc));
        al.addView(Ui.button(this, getString(R.string.btn_phone_steps), false, v -> showPhoneSteps("")));
        al.addView(sub(R.string.alive_repair));
        al.addView(small(R.string.alive_repair_desc));
        TextView cmd = Ui.text(this, ADB_CMD, 12, Ui.TEXT);
        cmd.setTypeface(Typeface.MONOSPACE);
        cmd.setTextIsSelectable(true);
        int cp = Ui.dp(this, 10);
        cmd.setPadding(cp, cp, cp, cp);
        GradientDrawable cbg = new GradientDrawable();
        cbg.setColor(Ui.BG);
        cbg.setCornerRadius(Ui.dp(this, 8));
        cmd.setBackground(cbg);
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        clp.topMargin = Ui.dp(this, 8);
        al.addView(cmd, clp);
        if (A11y.canWriteSecure(this)) {
            TextView ready = Ui.text(this, getString(R.string.alive_repair_ready), 13, Ui.OK);
            ready.setPadding(0, Ui.dp(this, 8), 0, 0);
            al.addView(ready);
        }
        al.addView(Ui.button(this, getString(R.string.btn_copy_cmd), false, v -> copyCmd()));
        root.addView(al);

        // 操作方法
        LinearLayout gs = Ui.card(this);
        gs.addView(section(R.string.sec_gestures));
        gs.addView(body(R.string.gestures_text));
        root.addView(gs);

        // 注意
        LinearLayout tp = Ui.card(this);
        tp.addView(section(R.string.sec_tips));
        tp.addView(body(R.string.tips_text));
        tp.addView(Ui.button(this, getString(R.string.btn_open_a11y), false, v -> openA11y()));
        root.addView(tp);

        String ver = "";
        try { ver = "v" + getPackageManager().getPackageInfo(getPackageName(), 0).versionName; } catch (Exception ignored) {}
        TextView foot = Ui.text(this, ver, 12, Ui.SUB);
        foot.setGravity(android.view.Gravity.CENTER);
        foot.setPadding(0, Ui.dp(this, 18), 0, 0);
        root.addView(foot);

        setContentView(sv);
        refresh();
    }

    private TextView section(int res) {
        TextView t = Ui.text(this, getString(res), 13, Ui.ACCENT);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }
    private TextView sub(int res) {
        TextView t = Ui.text(this, getString(res), 15, Ui.TEXT);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setPadding(0, Ui.dp(this, 16), 0, 0);
        return t;
    }
    private TextView body(int res) {
        TextView t = Ui.text(this, getString(res), 14, 0xFFDDE3EA);
        t.setPadding(0, Ui.dp(this, 8), 0, 0);
        t.setLineSpacing(0, 1.15f);
        return t;
    }
    private TextView small(int res) {
        TextView t = Ui.text(this, getString(res), 13, Ui.SUB);
        t.setPadding(0, Ui.dp(this, 4), 0, 0);
        return t;
    }

    // ---------- 状态刷新 ----------
    private void refresh() {
        int st = A11y.state(this);
        if (st != shownState) { shownState = st; renderStatus(st); }
        boolean ok = ((PowerManager) getSystemService(Context.POWER_SERVICE)).isIgnoringBatteryOptimizations(getPackageName());
        batteryState.setText(ok ? R.string.alive_battery_ok : R.string.alive_battery_no);
        batteryState.setTextColor(ok ? Ui.OK : Ui.WARN);
    }

    private void renderStatus(int st) {
        int color = st == A11y.RUNNING ? Ui.OK : st == A11y.STUCK ? Ui.WARN : Ui.BAD;
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.OVAL);
        d.setColor(color);
        dot.setBackground(d);
        stTitle.setText(st == A11y.RUNNING ? R.string.st_running : st == A11y.STUCK ? R.string.st_stuck : R.string.st_off);
        stDesc.setText(st == A11y.RUNNING ? R.string.st_running_desc : st == A11y.STUCK ? R.string.st_stuck_desc : R.string.st_off_desc);
        stButtons.removeAllViews();
        if (st == A11y.RUNNING) return;
        if (A11y.canWriteSecure(this))
            stButtons.addView(Ui.button(this, getString(st == A11y.STUCK ? R.string.btn_auto_restart : R.string.btn_auto_on),
                    true, v -> autoHeal()));
        stButtons.addView(Ui.button(this, getString(R.string.btn_open_a11y), !A11y.canWriteSecure(this), v -> openA11y()));
        stButtons.addView(Ui.button(this, getString(R.string.btn_guide), false, v -> showGuide()));
    }

    // ---------- 动作 ----------
    private void toast(int res) { Toast.makeText(this, res, Toast.LENGTH_LONG).show(); }

    private void openA11y() { if (!A11y.openList(this)) A11y.openAppInfo(this); }

    /** 有“修改安全设置”授权时：自己把服务打开（或关了再开）。 */
    private void autoHeal() {
        int st = A11y.state(this);
        if (st == A11y.RUNNING) return;
        if (st == A11y.OFF) {
            A11y.setEnabled(this, true);
        } else {
            A11y.setEnabled(this, false);
            h.postDelayed(() -> A11y.setEnabled(this, true), 500);
        }
    }

    private void restoreHandle() {
        ZoomService z = ZoomService.get();
        if (z != null) {
            z.restoreHandle();
            toast(R.string.toast_restored);
        } else {
            p.remove(Prefs.PX, Prefs.PY);
            p.set(Prefs.SHOW, 1);
            toast(R.string.toast_not_running);
        }
        updatingBall = true;
        ballSwitch.setChecked(true);     // 找回手柄会同时把悬浮球开关打开
        updatingBall = false;
    }

    private void confirmReset() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.dlg_reset_title)
                .setMessage(R.string.dlg_reset_msg)
                .setPositiveButton(android.R.string.ok, (d, w) -> {
                    p.resetAll();
                    ZoomService z = ZoomService.get();
                    if (z != null) z.restoreHandle();
                    toast(R.string.toast_reset_done);
                    build();   // 重新生成界面，让滑块回到默认位置
                    shownState = -1;
                    refresh();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void openBattery() {
        Intent i = new Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                android.net.Uri.parse("package:" + getPackageName()));
        if (A11y.start(this, i)) return;
        if (A11y.start(this, new Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))) return;
        toast(R.string.toast_fallback);
        showPhoneSteps("");
    }

    /**
     * 自启动没有统一接口，各家手机自己做了一个页面。只试本机已安装的厂商应用里的页面，依次：
     * 1) 已确认的页面（华为：系统设置自己用“动作 + 组件”打开的“应用启动管理”，从日志里确认的）
     * 2) 已知页面  3) 在厂商安全/手机管家类应用里按名字里的 autostart/startup 等字样自己找  4) 动作式入口。
     * 全都打不开时，显示本机品牌的手动步骤，并附上每次失败的原因（不再跳应用信息页，那里没有自启动）。
     */
    private void openAutostart() {
        List<Intent> list = new ArrayList<>();
        list.add(new Intent("com.android.settings.action.EXTRA_APP_SETTINGS").setComponent(new ComponentName(
                "com.huawei.systemmanager", "com.huawei.systemmanager.appcontrol.activity.StartupAppControlActivity")));
        for (String[] t : AUTOSTART) list.add(new Intent().setComponent(new ComponentName(t[0], t[1])));
        list.addAll(discoverAutostart());
        if (installed("com.miui.securitycenter"))
            list.add(new Intent("miui.intent.action.OP_AUTO_START").addCategory(Intent.CATEGORY_DEFAULT));
        if (installed("com.oneplus.security"))
            list.add(new Intent("com.android.settings.action.BACKGROUND_OPTIMIZE"));
        StringBuilder log = new StringBuilder();
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (Intent i : list) {
            ComponentName c = i.getComponent();
            String key = c != null ? c.flattenToString() + "|" + i.getAction() : String.valueOf(i.getAction());
            if (!seen.add(key)) continue;
            if (c != null && !installed(c.getPackageName())) continue;
            String err = A11y.tryStart(this, i);
            if (err == null) return;
            log.append(c != null ? c.flattenToShortString() : i.getAction()).append("\n  ").append(err).append('\n');
        }
        toast(R.string.toast_fallback);
        showPhoneSteps(log.toString());
    }

    private boolean installed(String pkg) {
        try { getPackageManager().getPackageInfo(pkg, 0); return true; } catch (Exception e) { return false; }
    }

    private List<Intent> discoverAutostart() {
        PackageManager pm = getPackageManager();
        List<Intent> out = new ArrayList<>();
        for (String pkg : SEC_PKGS) {
            try {
                ActivityInfo[] acts = pm.getPackageInfo(pkg, PackageManager.GET_ACTIVITIES).activities;
                if (acts == null) continue;
                for (String k : KEYS)
                    for (ActivityInfo a : acts)
                        if (a.exported && a.enabled && a.permission == null
                                && a.name.toLowerCase(Locale.ROOT).contains(k))
                            out.add(new Intent().setComponent(new ComponentName(pkg, a.name)));
            } catch (Exception ignored) {}
        }
        return out;
    }

    private void copyCmd() {
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        cm.setPrimaryClip(ClipData.newPlainText("adb", ADB_CMD));
        Toast.makeText(this, R.string.toast_copied, Toast.LENGTH_SHORT).show();
    }

    // ---------- 引导对话框 ----------
    private void showGuide() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.guide_title)
                .setMessage(R.string.guide_text)
                .setPositiveButton(R.string.btn_open_a11y, (d, w) -> openA11y())
                .setNeutralButton(R.string.btn_allow_restricted, (d, w) -> A11y.openAppInfo(this))
                .setNegativeButton(R.string.btn_close, null)
                .show();
    }

    private void showPhoneSteps(String diag) {
        String m = (Build.MANUFACTURER + " " + Build.BRAND).toLowerCase(Locale.ROOT);
        int brand;
        if (m.contains("xiaomi") || m.contains("redmi") || m.contains("poco")) brand = R.string.alive_xiaomi;
        else if (m.contains("huawei") || m.contains("honor")) brand = R.string.alive_huawei;
        else if (m.contains("oppo") || m.contains("realme") || m.contains("oneplus")) brand = R.string.alive_oppo;
        else if (m.contains("vivo") || m.contains("iqoo")) brand = R.string.alive_vivo;
        else if (m.contains("samsung")) brand = R.string.alive_samsung;
        else brand = 0;
        String msg = (brand != 0 ? getString(brand) + "\n\n" : "") + getString(R.string.alive_generic)
                + "\n\n" + getString(R.string.alive_note);
        if (diag != null && !diag.isEmpty()) msg += "\n\n" + getString(R.string.diag_title) + "\n" + diag;
        AlertDialog dlg = new AlertDialog.Builder(this)
                .setTitle(R.string.phone_steps_title)
                .setMessage(msg)
                .setPositiveButton(R.string.btn_close, null)
                .show();
        TextView tv = dlg.findViewById(android.R.id.message);
        if (tv != null) tv.setTextIsSelectable(true);
    }
}
