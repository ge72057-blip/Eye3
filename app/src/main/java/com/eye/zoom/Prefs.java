package com.eye.zoom;

import android.content.Context;
import android.content.SharedPreferences;

/** 所有设置的存取。数值都存成整数；默认值集中写在这里，快捷面板和设置页共用。 */
final class Prefs {
    static final String SCALE = "scale", IDLE = "idle", FADE = "fade", SIZE = "size", SENS = "sens2",
            PX = "px", PY = "py", GUIDE = "guide", SHOW = "show", STEADY = "steady";
    static final int D_SCALE = 15, D_IDLE = 3, D_FADE = 30, D_SIZE = 56, D_SENS = 10, D_SHOW = 1, D_STEADY = 5;   // 灵敏度显示值 = 存储值 / 10，范围 1.0 ~ 20.0，默认 1.0

    private final SharedPreferences sp;

    Prefs(Context c) { sp = c.getApplicationContext().getSharedPreferences("eye", Context.MODE_PRIVATE); }

    int get(String k, int d) { return sp.getInt(k, d); }
    void set(String k, int v) { sp.edit().putInt(k, v).apply(); }

    void remove(String... keys) {
        SharedPreferences.Editor e = sp.edit();
        for (String k : keys) e.remove(k);
        e.apply();
    }

    /** 恢复默认：清掉所有设置和手柄位置（只保留“已看过开启引导”的标记）。 */
    void resetAll() {
        int g = get(GUIDE, 0);
        sp.edit().clear().putInt(GUIDE, g).apply();
    }

    float scale()  { return 1.5f + get(SCALE, D_SCALE) / 10f; } // 1.5x ~ 8x
    int idleMs()   { return get(IDLE, D_IDLE) * 1000; }
    float fade()   { return get(FADE, D_FADE) / 100f; }
    /** 界面上显示的灵敏度（0.1 ~ 20.0）。 */
    float sensShown() { return get(SENS, D_SENS) / 10f; }
    /**
     * 实际使用的灵敏度系数：显示 1 对应系数 8（手指走屏幕高度的 1/8，画面走完全程），
     * 显示每加 1，系数加 8/7；显示 8 对应 16，显示 20 约为 29.7；
     * 显示低于 1 的是更慢的几档，系数 = 显示值 × 8（0.1 → 0.8，0.5 → 4）。
     */
    float sens() {
        float d = sensShown();
        return d >= 1f ? 8f + (d - 1f) * 8f / 7f : d * 8f;   // 显示 0.1~0.9 是更慢的几档：系数 0.8~7.2
    }
    /** 防抖强度 0~10：0 = 关；越大画面越稳，同时跟手会稍微滞后一点。 */
    int steady() { return get(STEADY, D_STEADY); }
    /** 悬浮球开关：关闭时手柄不显示，但服务仍在运行。 */
    boolean showHandle() { return get(SHOW, D_SHOW) == 1; }
    int sizeDp()   { return get(SIZE, D_SIZE); }
}
