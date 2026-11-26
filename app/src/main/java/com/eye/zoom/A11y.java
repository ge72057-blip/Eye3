package com.eye.zoom;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.provider.Settings;
import android.text.TextUtils;
import java.util.ArrayList;
import java.util.List;

/** 无障碍服务的状态检测与开关（开关需要用户用电脑授予一次 WRITE_SECURE_SETTINGS，可选）。 */
final class A11y {
    static final int RUNNING = 0, OFF = 1, STUCK = 2;

    private A11y() {}

    static ComponentName cn(Context c) { return new ComponentName(c, ZoomService.class); }

    /** 系统设置里是否把 Eye 记为“已开启”。 */
    static boolean enabledInSettings(Context c) {
        return parse(Settings.Secure.getString(c.getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)).contains(cn(c));
    }

    /** RUNNING=真的在运行；STUCK=系统说开着但没运行；OFF=没开。 */
    static int state(Context c) {
        if (ZoomService.get() != null) return RUNNING;
        return enabledInSettings(c) ? STUCK : OFF;
    }

    static boolean canWriteSecure(Context c) {
        return c.checkSelfPermission("android.permission.WRITE_SECURE_SETTINGS") == PackageManager.PERMISSION_GRANTED;
    }

    private static List<ComponentName> parse(String s) {
        List<ComponentName> out = new ArrayList<>();
        if (TextUtils.isEmpty(s)) return out;
        for (String part : s.split(":")) {
            ComponentName n = ComponentName.unflattenFromString(part);
            if (n != null) out.add(n);
        }
        return out;
    }

    /** 直接改系统开关。没有授权时返回 false。 */
    static boolean setEnabled(Context c, boolean on) {
        try {
            String cur = Settings.Secure.getString(c.getContentResolver(),
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            ComponentName me = cn(c);
            List<ComponentName> list = parse(cur);
            list.remove(me);
            if (on) list.add(me);
            StringBuilder sb = new StringBuilder();
            for (ComponentName n : list) {
                if (sb.length() > 0) sb.append(':');
                sb.append(n.flattenToString());
            }
            Settings.Secure.putString(c.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, sb.toString());
            if (on) Settings.Secure.putInt(c.getContentResolver(), Settings.Secure.ACCESSIBILITY_ENABLED, 1);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    static boolean openList(Context c) {
        return start(c, new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
    }

    static boolean openAppInfo(Context c) {
        return start(c, new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:" + c.getPackageName())));
    }

    static boolean start(Context c, Intent i) { return tryStart(c, i) == null; }

    /** 打开成功返回 null，失败返回原因（异常类型 + 系统给的说明，用于排查被厂商拒绝还是页面不存在）。 */
    static String tryStart(Context c, Intent i) {
        try {
            c.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            return null;
        } catch (Exception e) {
            String m = e.getMessage() == null ? "" : e.getMessage().replace('\n', ' ');
            if (m.length() > 160) m = m.substring(0, 160);
            return e.getClass().getSimpleName() + ": " + m;
        }
    }
}
