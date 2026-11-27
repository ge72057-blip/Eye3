package com.eye.zoom;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.ClipDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.RippleDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import java.util.function.IntConsumer;
import java.util.function.IntFunction;

/** 全部用代码生成界面时用到的小工具：颜色、卡片、按钮、滑块。 */
final class Ui {
    static final int BG = 0xFF14161A, CARD = 0xFF20232A, BTN = 0xFF2C313A, TEXT = 0xFFEAEFF5,
            SUB = 0xFF9AA6B5, ACCENT = 0xFF5AA9FF, OK = 0xFF4CD08A, WARN = 0xFFFFB454, BAD = 0xFFFF6B6B;

    private Ui() {}

    static int dp(Context c, float v) { return Math.round(v * c.getResources().getDisplayMetrics().density); }

    static TextView text(Context c, CharSequence s, float sp, int color) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        return t;
    }

    static LinearLayout card(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        int p = dp(c, 16);
        l.setPadding(p, p, p, p);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(CARD);
        bg.setCornerRadius(dp(c, 16));
        l.setBackground(bg);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(c, 14);
        l.setLayoutParams(lp);
        return l;
    }

    static TextView button(Context c, CharSequence label, boolean primary, View.OnClickListener click) {
        TextView b = new TextView(c);
        b.setText(label);
        b.setTextSize(15);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setTextColor(primary ? 0xFF08121F : TEXT);
        b.setGravity(Gravity.CENTER);
        b.setMinHeight(dp(c, 46));
        b.setPadding(dp(c, 14), dp(c, 10), dp(c, 14), dp(c, 10));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(primary ? ACCENT : BTN);
        bg.setCornerRadius(dp(c, 12));
        GradientDrawable mask = new GradientDrawable();
        mask.setColor(0xFFFFFFFF);
        mask.setCornerRadius(dp(c, 12));
        b.setBackground(new RippleDrawable(ColorStateList.valueOf(0x33FFFFFF), bg, mask));
        b.setClickable(true);
        b.setOnClickListener(click);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(c, 10);
        b.setLayoutParams(lp);
        return b;
    }

    /** 扁平滑块：细圆角轨道 + 蓝色进度 + 白色圆形滑块。 */
    static SeekBar flatSeekBar(Context c) {
        SeekBar sb = new SeekBar(c);
        int th = dp(c, 4);
        GradientDrawable track = new GradientDrawable();
        track.setColor(0x33FFFFFF);
        track.setCornerRadius(th / 2f);
        track.setSize(0, th);
        GradientDrawable prog = new GradientDrawable();
        prog.setColor(ACCENT);
        prog.setCornerRadius(th / 2f);
        prog.setSize(0, th);
        LayerDrawable ld = new LayerDrawable(new Drawable[]{track, new ClipDrawable(prog, Gravity.START, ClipDrawable.HORIZONTAL)});
        ld.setId(0, android.R.id.background);
        ld.setId(1, android.R.id.progress);
        sb.setProgressDrawable(ld);
        GradientDrawable thumb = new GradientDrawable();
        thumb.setShape(GradientDrawable.OVAL);
        thumb.setColor(0xFFFFFFFF);
        thumb.setSize(dp(c, 20), dp(c, 20));
        sb.setThumb(thumb);
        sb.setSplitTrack(false);
        sb.setPadding(dp(c, 10), dp(c, 8), dp(c, 10), dp(c, 8));
        return sb;
    }

    /** 一行：左边名称、右边数值，下面是滑块。滑块拖动时实时保存。 */
    static void slider(ViewGroup parent, int labelRes, int min, int max, int cur,
                       IntFunction<String> fmt, IntConsumer save) {
        Context c = parent.getContext();
        LinearLayout head = new LinearLayout(c);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setPadding(0, dp(c, 14), 0, 0);
        TextView label = text(c, c.getString(labelRes), 14, 0xFFDDE3EA);
        head.addView(label, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        final TextView value = text(c, fmt.apply(cur), 14, ACCENT);
        value.setTypeface(Typeface.DEFAULT_BOLD);
        head.addView(value);
        SeekBar sb = flatSeekBar(c);
        sb.setMin(min);
        sb.setMax(max);
        sb.setProgress(cur);
        sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar b, int v, boolean user) {
                value.setText(fmt.apply(v));
                if (user) save.accept(v);
            }
            @Override public void onStartTrackingTouch(SeekBar b) {}
            @Override public void onStopTrackingTouch(SeekBar b) {}
        });
        parent.addView(head);
        parent.addView(sb);
    }
}
