package com.xiaoman.app;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.InputStream;
import java.util.HashMap;

/** 原生 UI 工具箱：dp、字体、色卡、圆角背景、图标。 */
final class Ui {
    static final int LIME = 0xFFC9F44F;
    static final int LIME_DEEP = 0xFFB6EE25;
    static final int TEAL = 0xFF3ED6B0;
    static final int ORANGE = 0xFFFF7A45;
    static final int RED = 0xFFFF5A4E;

    final Activity a;
    final Typeface regular, medium, bold;
    private final float density;
    private final HashMap<String, Bitmap> bitmaps = new HashMap<>();

    Ui(Activity activity) {
        this.a = activity;
        this.density = activity.getResources().getDisplayMetrics().density;
        // 原 App 的 WeChatSansStd 是仅含拉丁字符的子集且小写被映射成小型大写，
        // 直接用系统字体，保持与原 App 中英文混排一致。
        regular = Typeface.create("sans-serif", Typeface.NORMAL);
        medium = Typeface.create("sans-serif-medium", Typeface.NORMAL);
        bold = Typeface.create("sans-serif", Typeface.BOLD);
    }

    int dp(float v) { return Math.round(v * density); }

    // ---------- 主题色 ----------
    int bg() { return MainActivity.dark ? 0xFF101010 : 0xFFF4F4F4; }
    int card() { return MainActivity.dark ? 0xFF1C1C1C : 0xFFFFFFFF; }
    int ink() { return MainActivity.dark ? 0xFFF2F2F2 : 0xFF1A1A1A; }
    int ink2() { return MainActivity.dark ? 0xFFB8B8B8 : 0xFF5C5C5C; }
    int gray() { return MainActivity.dark ? 0xFF9A9A9A : 0xFFA3A3A3; }
    int line() { return MainActivity.dark ? 0xFF2A2A2A : 0xFFEFEFEF; }
    int chipBg() { return MainActivity.dark ? 0xFF262626 : 0xFFE9E9E9; }
    int chipInk() { return MainActivity.dark ? 0xFF9A9A9A : 0xFF7A7A7A; }
    int chipOnBg() { return MainActivity.dark ? 0xFFF2F2F2 : 0xFF111111; }
    int chipOnInk() { return MainActivity.dark ? 0xFF111111 : 0xFFFFFFFF; }

    // ---------- 文本 ----------
    TextView text(String s, float sp, int color) { return text(s, sp, color, regular); }

    TextView text(String s, float sp, int color, Typeface tf) {
        TextView t = new TextView(a);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setTypeface(tf);
        t.setIncludeFontPadding(false);
        return t;
    }

    // ---------- 容器 ----------
    LinearLayout vbox() {
        LinearLayout l = new LinearLayout(a);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    LinearLayout hbox() {
        LinearLayout l = new LinearLayout(a);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        return l;
    }

    LinearLayout.LayoutParams lp(int w, int h) { return new LinearLayout.LayoutParams(w, h); }

    LinearLayout.LayoutParams lpw(float weight, int h) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, h, weight);
        return p;
    }

    static void margin(View v, int l, int t, int r, int b) {
        ViewGroup.LayoutParams p = v.getLayoutParams();
        if (p instanceof ViewGroup.MarginLayoutParams) {
            ((ViewGroup.MarginLayoutParams) p).setMargins(l, t, r, b);
            v.setLayoutParams(p);
        }
    }

    View space(int h) {
        View v = new View(a);
        v.setLayoutParams(new LinearLayout.LayoutParams(1, dp(h)));
        return v;
    }

    View hline() {
        View v = new View(a);
        v.setBackgroundColor(line());
        v.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, dp(0.7f))));
        return v;
    }

    // ---------- 背景 ----------
    GradientDrawable round(int fill, float radiusDp) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(fill);
        d.setCornerRadius(dp(radiusDp));
        return d;
    }

    GradientDrawable roundStroke(int fill, float radiusDp, int stroke, float widthDp) {
        GradientDrawable d = round(fill, radiusDp);
        d.setStroke(Math.max(1, dp(widthDp)), stroke);
        return d;
    }

    GradientDrawable circle(int fill) {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.OVAL);
        d.setColor(fill);
        return d;
    }

    GradientDrawable gradient(int... colors) {
        GradientDrawable d = new GradientDrawable(GradientDrawable.Orientation.TL_BR, colors);
        d.setCornerRadius(dp(18));
        return d;
    }

    GradientDrawable gradientSquare(int... colors) {
        GradientDrawable d = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, colors);
        d.setCornerRadius(0);
        return d;
    }

    void card(View v) {
        v.setBackground(round(card(), 16));
        v.setElevation(dp(2));
    }

    // ---------- 图片 ----------
    Bitmap bitmap(String path) {
        Bitmap b = bitmaps.get(path);
        if (b != null) return b;
        try (InputStream in = a.getAssets().open(path)) {
            b = BitmapFactory.decodeStream(in);
        } catch (Exception e) { b = null; }
        if (b != null) bitmaps.put(path, b);
        return b;
    }

    ImageView image(String path, int sizeDp) {
        ImageView iv = new ImageView(a);
        Bitmap b = bitmap(path);
        if (b != null) iv.setImageBitmap(b); else iv.setVisibility(View.GONE);
        iv.setLayoutParams(new LinearLayout.LayoutParams(dp(sizeDp), dp(sizeDp)));
        iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
        return iv;
    }

    ImageView icon(String name, int sizeDp) { return image("icons/" + name + ".png", sizeDp); }

    ImageView fileImage(String path, int sizeDp) {
        ImageView iv = new ImageView(a);
        try { iv.setImageBitmap(BitmapFactory.decodeFile(path)); } catch (Exception ignored) {}
        iv.setLayoutParams(new LinearLayout.LayoutParams(dp(sizeDp), dp(sizeDp)));
        iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
        return iv;
    }

    ImageView iconTint(String name, int sizeDp, int color) {
        ImageView iv = icon(name, sizeDp);
        iv.setColorFilter(color);
        return iv;
    }

    // ---------- 常用控件 ----------
    TextView chip(String label, boolean on) {
        TextView t = text(label, 11, on ? chipOnInk() : chipInk(), medium);
        t.setBackground(round(on ? chipOnBg() : chipBg(), 14));
        t.setPadding(dp(13), dp(7), dp(13), dp(7));
        return t;
    }

    View bar(float fraction, int fillColor) {
        BarView b = new BarView(a);
        b.frac = Math.max(0, Math.min(1, fraction));
        b.bgColor = line();
        b.fgColor = fillColor;
        b.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(5)));
        return b;
    }

    TextView pillButton(String label, int bg, int fg, float radius) {
        TextView t = text(label, 12, fg, medium);
        t.setBackground(round(bg, radius));
        t.setPadding(dp(14), dp(8), dp(14), dp(8));
        return t;
    }

    int alpha(int color, float a) { return Color.argb((int) (Color.alpha(color) * a), Color.red(color), Color.green(color), Color.blue(color)); }
}
