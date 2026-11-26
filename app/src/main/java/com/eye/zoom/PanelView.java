package com.eye.zoom;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.content.res.ColorStateList;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

/** 快捷面板：单击手柄弹出。点面板外面或点右上角“✕”都会收起。完整设置请打开桌面上的 Eye 应用。 */
final class PanelView extends LinearLayout {
    private final ZoomService s;
    private final Prefs p;

    PanelView(Context ctx, ZoomService svc, Prefs prefs) {
        super(ctx);
        s = svc; p = prefs;
        setOrientation(VERTICAL);
        int pad = Ui.dp(ctx, 20);
        setPadding(pad, Ui.dp(ctx, 16), pad, Ui.dp(ctx, 14));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xF2191C21);
        bg.setCornerRadius(Ui.dp(ctx, 24));
        bg.setStroke(Ui.dp(ctx, 1), 0x1FFFFFFF);
        setBackground(bg);

        // 标题行：左边名称，右边关闭
        LinearLayout head = new LinearLayout(ctx);
        head.setOrientation(HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = Ui.text(ctx, ctx.getString(R.string.app_name), 20, Ui.TEXT);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        head.addView(title, new LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView close = Ui.text(ctx, "✕", 16, Ui.TEXT);
        close.setGravity(Gravity.CENTER);
        close.setContentDescription(ctx.getString(R.string.btn_close));
        GradientDrawable cbg = new GradientDrawable();
        cbg.setShape(GradientDrawable.OVAL);
        cbg.setColor(0x1FFFFFFF);
        GradientDrawable mask = new GradientDrawable();
        mask.setShape(GradientDrawable.OVAL);
        mask.setColor(0xFFFFFFFF);
        close.setBackground(new RippleDrawable(ColorStateList.valueOf(0x33FFFFFF), cbg, mask));
        close.setClickable(true);
        close.setOnClickListener(v -> s.closePanel());
        head.addView(close, new LayoutParams(Ui.dp(ctx, 36), Ui.dp(ctx, 36)));
        addView(head);

        Ui.slider(this, R.string.label_zoom, 0, 65, p.get(Prefs.SCALE, Prefs.D_SCALE),
                v -> ctx.getString(R.string.fmt_zoom, 1.5f + v / 10f), v -> p.set(Prefs.SCALE, v));
        Ui.slider(this, R.string.label_sens, 1, 200, p.get(Prefs.SENS, Prefs.D_SENS),
                v -> ctx.getString(R.string.fmt_zoom, v / 10f), v -> p.set(Prefs.SENS, v));
        Ui.slider(this, R.string.label_idle, 1, 30, p.get(Prefs.IDLE, Prefs.D_IDLE),
                v -> ctx.getString(R.string.fmt_sec, v), v -> p.set(Prefs.IDLE, v));
        Ui.slider(this, R.string.label_fade, 5, 100, p.get(Prefs.FADE, Prefs.D_FADE),
                v -> ctx.getString(R.string.fmt_pct, v), v -> p.set(Prefs.FADE, v));
        Ui.slider(this, R.string.label_size, 36, 96, p.get(Prefs.SIZE, Prefs.D_SIZE),
                v -> ctx.getString(R.string.fmt_dp, v), v -> { p.set(Prefs.SIZE, v); s.applySize(); });
    }

    @Override public boolean onTouchEvent(MotionEvent e) {
        if (e.getAction() == MotionEvent.ACTION_OUTSIDE) { s.closePanel(); return true; }
        return super.onTouchEvent(e);
    }
}
