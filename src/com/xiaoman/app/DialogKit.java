package com.xiaoman.app;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.function.IntConsumer;

import org.json.JSONArray;
import org.json.JSONObject;

/** 对齐原 App 风格的圆角对话框（白卡 + 黑色主按钮）。 */
final class DialogKit {
    interface TextConsumer { void on(String s); }

    private static int cardColor() { return MainActivity.dark ? 0xFF1C1C1C : 0xFFFFFFFF; }
    private static int ink() { return MainActivity.dark ? 0xFFF2F2F2 : 0xFF1A1A1A; }
    private static int ink2() { return MainActivity.dark ? 0xFFB8B8B8 : 0xFF5C5C5C; }
    private static int gray() { return MainActivity.dark ? 0xFF9A9A9A : 0xFFA3A3A3; }

    private static Dialog dialog(Activity a, View card) {
        Dialog d = new Dialog(a);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        d.setContentView(card);
        Window w = d.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        }
        return d;
    }

    private static LinearLayout card(Activity a, int widthPx) {
        LinearLayout card = new LinearLayout(a);
        card.setOrientation(LinearLayout.VERTICAL);
        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
        bg.setColor(cardColor());
        bg.setCornerRadius(dp(a, 20));
        card.setBackground(bg);
        card.setPadding(dp(a, 22), dp(a, 22), dp(a, 22), dp(a, 18));
        return card;
    }

    static int dp(Activity a, float v) { return Math.round(v * a.getResources().getDisplayMetrics().density); }

    private static TextView button(Activity a, String label, boolean primary) {
        TextView t = new TextView(a);
        t.setText(label);
        t.setTextSize(14);
        t.setGravity(Gravity.CENTER);
        t.setTypeface(android.graphics.Typeface.create("sans-serif-medium", 0));
        t.setTextColor(primary ? (MainActivity.dark ? 0xFF111111 : 0xFFFFFFFF) : ink());
        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
        bg.setCornerRadius(dp(a, 23));
        bg.setColor(primary ? (MainActivity.dark ? 0xFFF2F2F2 : 0xFF111111) : (MainActivity.dark ? 0xFF2A2A2A : 0xFFF1F1F1));
        t.setBackground(bg);
        t.setMinHeight(dp(a, 46));
        t.setPadding(dp(a, 12), dp(a, 13), dp(a, 12), dp(a, 13));
        return t;
    }

    static void confirm(Activity a, String title, String message, String cancel, String ok, Runnable onOk) {
        confirm(a, title, message, cancel, ok, onOk, null);
    }

    static void confirm(Activity a, String title, String message, String cancel, String ok, Runnable onOk, Runnable onCancel) {
        int w = (int) (a.getResources().getDisplayMetrics().widthPixels * 0.86f);
        LinearLayout card = card(a, w);
        card.setLayoutParams(new ViewGroup.LayoutParams(w, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView t = new TextView(a);
        t.setText(title);
        t.setTextSize(17);
        t.setTextColor(ink());
        t.setGravity(Gravity.CENTER);
        t.setTypeface(android.graphics.Typeface.create("sans-serif-medium", 0));
        card.addView(t, new LinearLayout.LayoutParams(-1, -2));

        TextView m = new TextView(a);
        m.setText(message);
        m.setTextSize(12);
        m.setTextColor(gray());
        m.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams mp = new LinearLayout.LayoutParams(-1, -2);
        mp.topMargin = dp(a, 10);
        card.addView(m, mp);

        LinearLayout row = new LinearLayout(a);
        row.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(-1, -2);
        rp.topMargin = dp(a, 18);
        card.addView(row, rp);
        TextView c = button(a, cancel, false);
        TextView o = button(a, ok, true);
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(0, -2, 1f);
        row.addView(c, bp);
        LinearLayout.LayoutParams bp2 = new LinearLayout.LayoutParams(0, -2, 1f);
        bp2.leftMargin = dp(a, 10);
        row.addView(o, bp2);

        Dialog d = dialog(a, card);
        c.setOnClickListener(v -> { d.dismiss(); if (onCancel != null) onCancel.run(); });
        o.setOnClickListener(v -> { d.dismiss(); if (onOk != null) onOk.run(); });
        d.show();
    }

    static void list(Activity a, String title, String[] items, IntConsumer onPick, String neutral, Runnable onNeutral) {
        int w = (int) (a.getResources().getDisplayMetrics().widthPixels * 0.86f);
        LinearLayout card = card(a, w);
        card.setLayoutParams(new ViewGroup.LayoutParams(w, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView t = new TextView(a);
        t.setText(title);
        t.setTextSize(17);
        t.setTextColor(ink());
        t.setGravity(Gravity.CENTER);
        t.setTypeface(android.graphics.Typeface.create("sans-serif-medium", 0));
        card.addView(t, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout list = new LinearLayout(a);
        list.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = dp(a, 8);
        card.addView(list, lp);

        Dialog d = dialog(a, card);
        for (int i = 0; i < items.length; i++) {
            final int idx = i;
            TextView row = new TextView(a);
            row.setText(items[i]);
            row.setTextSize(14);
            row.setTextColor(ink());
            row.setPadding(dp(a, 6), dp(a, 13), dp(a, 6), dp(a, 13));
            row.setOnClickListener(v -> { d.dismiss(); if (onPick != null) onPick.accept(idx); });
            list.addView(row, new LinearLayout.LayoutParams(-1, -2));
        }

        LinearLayout row = new LinearLayout(a);
        row.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(-1, -2);
        rp.topMargin = dp(a, 14);
        card.addView(row, rp);
        TextView close = button(a, "关闭", false);
        row.addView(close, new LinearLayout.LayoutParams(0, -2, 1f));
        close.setOnClickListener(v -> d.dismiss());
        if (neutral != null) {
            TextView n = button(a, neutral, true);
            LinearLayout.LayoutParams np = new LinearLayout.LayoutParams(0, -2, 1f);
            np.leftMargin = dp(a, 10);
            row.addView(n, np);
            n.setOnClickListener(v -> { d.dismiss(); if (onNeutral != null) onNeutral.run(); });
        }
        d.show();
    }

    static void info(Activity a, String title, String message) {
        int w = (int) (a.getResources().getDisplayMetrics().widthPixels * 0.86f);
        LinearLayout card = card(a, w);
        card.setLayoutParams(new ViewGroup.LayoutParams(w, ViewGroup.LayoutParams.WRAP_CONTENT));
        TextView t = new TextView(a);
        t.setText(title); t.setTextSize(17); t.setTextColor(ink()); t.setGravity(Gravity.CENTER);
        t.setTypeface(android.graphics.Typeface.create("sans-serif-medium", 0));
        card.addView(t, new LinearLayout.LayoutParams(-1, -2));
        TextView m = new TextView(a);
        m.setText(message); m.setTextSize(12); m.setTextColor(gray()); m.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams mp = new LinearLayout.LayoutParams(-1, -2); mp.topMargin = dp(a, 10);
        card.addView(m, mp);
        Dialog d = dialog(a, card);
        TextView ok = button(a, "知道了", true);
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(-1, -2); bp.topMargin = dp(a, 18);
        card.addView(ok, bp);
        ok.setOnClickListener(v -> d.dismiss());
        d.show();
    }

    static Dialog progress(Activity a, String title, String message) {
        int w = (int) (a.getResources().getDisplayMetrics().widthPixels * 0.7f);
        LinearLayout card = card(a, w);
        card.setPadding(dp(a, 20), dp(a, 20), dp(a, 20), dp(a, 20));
        card.setLayoutParams(new ViewGroup.LayoutParams(w, ViewGroup.LayoutParams.WRAP_CONTENT));
        TextView t = new TextView(a);
        t.setText(title); t.setTextSize(15); t.setTextColor(ink()); t.setGravity(Gravity.CENTER);
        t.setTypeface(android.graphics.Typeface.create("sans-serif-medium", 0));
        card.addView(t, new LinearLayout.LayoutParams(-1, -2));
        TextView m = new TextView(a);
        m.setText(message); m.setTextSize(12); m.setTextColor(gray()); m.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams mp = new LinearLayout.LayoutParams(-1, -2); mp.topMargin = dp(a, 8);
        card.addView(m, mp);
        Dialog d = dialog(a, card);
        d.setCancelable(false);
        d.show();
        return d;
    }

    static void multi(Activity a, String title, String[] items, boolean[] checked, java.util.function.Consumer<boolean[]> onOk, String neutral, Runnable onNeutral) {
        int w = (int) (a.getResources().getDisplayMetrics().widthPixels * 0.86f);
        LinearLayout card = card(a, w);
        card.setLayoutParams(new ViewGroup.LayoutParams(w, ViewGroup.LayoutParams.WRAP_CONTENT));
        TextView t = new TextView(a);
        t.setText(title); t.setTextSize(17); t.setTextColor(ink()); t.setGravity(Gravity.CENTER);
        t.setTypeface(android.graphics.Typeface.create("sans-serif-medium", 0));
        card.addView(t, new LinearLayout.LayoutParams(-1, -2));
        LinearLayout list = new LinearLayout(a);
        list.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2); lp.topMargin = dp(a, 8);
        card.addView(list, lp);
        Dialog d = dialog(a, card);
        TextView[] rows = new TextView[items.length];
        for (int i = 0; i < items.length; i++) {
            final int idx = i;
            TextView row = new TextView(a);
            row.setTextSize(14); row.setTextColor(ink());
            row.setPadding(dp(a, 6), dp(a, 12), dp(a, 6), dp(a, 12));
            rows[i] = row;
            final Runnable[] upd = new Runnable[1];
            upd[0] = () -> row.setText((checked[idx] ? "✓  " : "    ") + items[idx]);
            upd[0].run();
            row.setOnClickListener(v -> { checked[idx] = !checked[idx]; upd[0].run(); });
            list.addView(row, new LinearLayout.LayoutParams(-1, -2));
        }
        LinearLayout row = new LinearLayout(a);
        row.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(-1, -2); rp.topMargin = dp(a, 14);
        card.addView(row, rp);
        TextView close = button(a, "取消", false);
        row.addView(close, new LinearLayout.LayoutParams(0, -2, 1f));
        close.setOnClickListener(v -> d.dismiss());
        TextView ok = button(a, "确定", true);
        LinearLayout.LayoutParams op = new LinearLayout.LayoutParams(0, -2, 1f); op.leftMargin = dp(a, 10);
        row.addView(ok, op);
        ok.setOnClickListener(v -> { d.dismiss(); if (onOk != null) onOk.accept(checked); });
        if (neutral != null) {
            TextView n = button(a, neutral, true);
            LinearLayout.LayoutParams np = new LinearLayout.LayoutParams(-1, -2); np.topMargin = dp(a, 10);
            card.addView(n, np);
            n.setOnClickListener(v -> { d.dismiss(); if (onNeutral != null) onNeutral.run(); });
        }
        d.show();
    }

    static void input(Activity a, String title, String hint, String okText, TextConsumer onOk) {
        int w = (int) (a.getResources().getDisplayMetrics().widthPixels * 0.86f);
        LinearLayout card = card(a, w);
        card.setLayoutParams(new ViewGroup.LayoutParams(w, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView t = new TextView(a);
        t.setText(title);
        t.setTextSize(17);
        t.setTextColor(ink());
        t.setGravity(Gravity.CENTER);
        t.setTypeface(android.graphics.Typeface.create("sans-serif-medium", 0));
        card.addView(t, new LinearLayout.LayoutParams(-1, -2));

        EditText et = new EditText(a);
        et.setHint(hint);
        et.setSingleLine(true);
        et.setTextSize(14);
        et.setTextColor(ink());
        et.setHintTextColor(gray());
        et.setBackground(null);
        android.graphics.drawable.GradientDrawable field = new android.graphics.drawable.GradientDrawable();
        field.setColor(MainActivity.dark ? 0xFF2A2A2A : 0xFFF4F4F4);
        field.setCornerRadius(dp(a, 10));
        et.setBackground(field);
        et.setPadding(dp(a, 12), dp(a, 10), dp(a, 12), dp(a, 10));
        LinearLayout.LayoutParams ep = new LinearLayout.LayoutParams(-1, -2);
        ep.topMargin = dp(a, 14);
        card.addView(et, ep);

        LinearLayout row = new LinearLayout(a);
        row.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(-1, -2);
        rp.topMargin = dp(a, 16);
        card.addView(row, rp);
        TextView c = button(a, "关闭", false);
        TextView o = button(a, okText, true);
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(0, -2, 1f);
        row.addView(c, bp);
        LinearLayout.LayoutParams bp2 = new LinearLayout.LayoutParams(0, -2, 1f);
        bp2.leftMargin = dp(a, 10);
        row.addView(o, bp2);

        Dialog d = dialog(a, card);
        c.setOnClickListener(v -> d.dismiss());
        o.setOnClickListener(v -> {
            String v2 = et.getText().toString().trim();
            if (!v2.isEmpty()) { d.dismiss(); if (onOk != null) onOk.on(v2); }
        });
        d.show();
    }

    // ---------------- 底部弹层（对齐原 App） ----------------
    interface Pick { void on(String path); }

    private static Dialog sheet(Activity a, float heightRatio) {
        Dialog d = new Dialog(a);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        Window w = d.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            w.setGravity(Gravity.BOTTOM);
            w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT,
                    (int) (a.getResources().getDisplayMetrics().heightPixels * heightRatio));
            w.addFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            w.setDimAmount(0.35f);
        }
        return d;
    }

    private static void showSheet(Dialog d, Activity a, LinearLayout sheet, float heightRatio) {
        android.widget.FrameLayout wrap = new android.widget.FrameLayout(a);
        wrap.addView(sheet, new android.widget.FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.BOTTOM));
        d.setContentView(wrap);
        d.show();
        Window w = d.getWindow();
        if (w != null) w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT,
                (int) (a.getResources().getDisplayMetrics().heightPixels * heightRatio));
    }

    private static LinearLayout sheetBody(Activity a) {
        LinearLayout sheet = new LinearLayout(a);
        sheet.setOrientation(LinearLayout.VERTICAL);
        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
        bg.setColor(cardColor());
        float r = dp(a, 20);
        bg.setCornerRadii(new float[]{r, r, r, r, 0, 0, 0, 0});
        sheet.setBackground(bg);
        return sheet;
    }

    private static View sheetHeader(Activity a, String title, Runnable onClose, Runnable onNew) {
        LinearLayout head = new LinearLayout(a);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setPadding(dp(a, 18), dp(a, 14), dp(a, 18), dp(a, 8));
        TextView x = new TextView(a);
        x.setText("✕"); x.setTextSize(16); x.setTextColor(gray());
        x.setGravity(Gravity.CENTER);
        x.setOnClickListener(v -> onClose.run());
        head.addView(x, new LinearLayout.LayoutParams(dp(a, 32), dp(a, 32)));
        TextView t = new TextView(a);
        t.setText(title); t.setTextSize(17); t.setTextColor(ink());
        t.setGravity(Gravity.CENTER);
        t.setTypeface(android.graphics.Typeface.create("sans-serif-medium", 0));
        head.addView(t, new LinearLayout.LayoutParams(0, -2, 1f));
        TextView n = new TextView(a);
        n.setText(onNew != null ? "新建" : "");
        n.setTextSize(13); n.setTextColor(ink2());
        n.setGravity(Gravity.CENTER);
        if (onNew != null) n.setOnClickListener(v -> onNew.run());
        head.addView(n, new LinearLayout.LayoutParams(dp(a, 44), dp(a, 32)));
        return head;
    }

    static void sheetList(Activity a, String title, String emptyHint, String[] items, IntConsumer onPick, Runnable onNew) {
        Dialog d = sheet(a, 0.62f);
        LinearLayout sheet = sheetBody(a);
        Runnable close = d::dismiss;
        sheet.addView(sheetHeader(a, title, close, onNew != null ? () -> { d.dismiss(); onNew.run(); } : null));
        if (items.length == 0) {
            LinearLayout empty = new LinearLayout(a);
            empty.setOrientation(LinearLayout.VERTICAL);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(0, dp(a, 70), 0, dp(a, 40));
            try (java.io.InputStream in = a.getAssets().open("img/empty.png")) {
                android.widget.ImageView iv = new android.widget.ImageView(a);
                iv.setImageBitmap(android.graphics.BitmapFactory.decodeStream(in));
                empty.addView(iv, new LinearLayout.LayoutParams(dp(a, 70), dp(a, 70)));
            } catch (Exception ignored) {}
            TextView t1 = new TextView(a);
            t1.setText("空空如也"); t1.setTextSize(13); t1.setTextColor(ink());
            t1.setGravity(Gravity.CENTER); t1.setPadding(0, dp(a, 12), 0, 0);
            empty.addView(t1);
            TextView t2 = new TextView(a);
            t2.setText(emptyHint); t2.setTextSize(11); t2.setTextColor(gray());
            t2.setGravity(Gravity.CENTER); t2.setPadding(0, dp(a, 4), 0, 0);
            empty.addView(t2);
            sheet.addView(empty, new LinearLayout.LayoutParams(-1, 0, 1f));
        } else {
            android.widget.ScrollView sc = new android.widget.ScrollView(a);
            LinearLayout list = new LinearLayout(a);
            list.setOrientation(LinearLayout.VERTICAL);
            list.setPadding(dp(a, 18), 0, dp(a, 18), dp(a, 16));
            for (int i = 0; i < items.length; i++) {
                final int idx = i;
                TextView row = new TextView(a);
                row.setText(items[i]); row.setTextSize(14); row.setTextColor(ink());
                row.setPadding(dp(a, 2), dp(a, 14), dp(a, 2), dp(a, 14));
                if (onPick != null) row.setOnClickListener(v -> { d.dismiss(); onPick.accept(idx); });
                list.addView(row, new LinearLayout.LayoutParams(-1, -2));
            }
            sc.addView(list);
            sheet.addView(sc, new LinearLayout.LayoutParams(-1, 0, 1f));
        }
        showSheet(d, a, sheet, 0.62f);
    }

    static void iconSheet(Activity a, String current, String[] assets, Pick onPick) {
        Dialog d = sheet(a, 0.58f);
        LinearLayout sheet = sheetBody(a);
        sheet.addView(sheetHeader(a, "选择图标", d::dismiss, null));
        android.widget.ScrollView sc = new android.widget.ScrollView(a);
        android.widget.GridLayout grid = new android.widget.GridLayout(a);
        android.util.DisplayMetrics dm = a.getResources().getDisplayMetrics();
        int gap = dp(a, 6);
        int cols = Math.max(5, (int) Math.floor((dm.widthPixels - dp(a, 28)) / (double) dp(a, 72)));
        grid.setColumnCount(cols);
        grid.setPadding(dp(a, 14), dp(a, 10), dp(a, 14), dp(a, 24));
        for (String path : assets) {
            android.widget.FrameLayout cell = new android.widget.FrameLayout(a);
            cell.setPadding(dp(a, 6), dp(a, 6), dp(a, 6), dp(a, 6));
            try (java.io.InputStream in = a.getAssets().open(path)) {
                android.widget.ImageView iv = new android.widget.ImageView(a);
                iv.setImageBitmap(android.graphics.BitmapFactory.decodeStream(in));
                iv.setScaleType(android.widget.ImageView.ScaleType.FIT_CENTER);
                cell.addView(iv, new android.widget.FrameLayout.LayoutParams(dp(a, 52), dp(a, 52), Gravity.CENTER));
            } catch (Exception ignored) {}
            boolean on = path.equals(current);
            android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
            bg.setColor(on ? (MainActivity.dark ? 0xFF22300C : 0xFFF1F8DC) : Color.TRANSPARENT);
            bg.setCornerRadius(dp(a, 12));
            cell.setBackground(bg);
            cell.setOnClickListener(v -> { d.dismiss(); onPick.on(path); });
            android.widget.GridLayout.LayoutParams gp = new android.widget.GridLayout.LayoutParams();
            gp.width = dp(a, 64); gp.height = dp(a, 64);
            grid.addView(cell, gp);
        }
        sc.addView(grid);
        sheet.addView(sc, new LinearLayout.LayoutParams(-1, 0, 1f));
        showSheet(d, a, sheet, 0.58f);
    }


    /** 单选底部弹层（设置项选择，如货币单位/小数位/语言）。 */
    static void sheetSelect(Activity a, String title, String[] options, int selected, IntConsumer onPick) {
        Dialog d = sheet(a, 0.5f);
        LinearLayout sheet = sheetBody(a);
        sheet.addView(sheetHeader(a, title, d::dismiss, null));
        android.widget.ScrollView sc = new android.widget.ScrollView(a);
        LinearLayout list = new LinearLayout(a);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(dp(a, 18), dp(a, 2), dp(a, 18), dp(a, 18));
        for (int i = 0; i < options.length; i++) {
            final int idx = i;
            LinearLayout row = new LinearLayout(a);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(a, 4), dp(a, 14), dp(a, 4), dp(a, 14));
            TextView label = new TextView(a);
            label.setText(options[i]);
            label.setTextSize(14);
            label.setTextColor(ink());
            row.addView(label, new LinearLayout.LayoutParams(0, -2, 1f));
            TextView check = new TextView(a);
            check.setText(i == selected ? "✓" : "");
            check.setTextSize(15);
            check.setTextColor(0xFFB6EE25);
            row.addView(check);
            row.setOnClickListener(v -> { d.dismiss(); if (onPick != null) onPick.accept(idx); });
            list.addView(row, new LinearLayout.LayoutParams(-1, -2));
        }
        sc.addView(list);
        sheet.addView(sc, new LinearLayout.LayoutParams(-1, 0, 1f));
        showSheet(d, a, sheet, 0.5f);
    }


    /** 管理类弹层：行右侧显示计数与箭头；空态点击新建。 */
    static void sheetListCounts(Activity a, String title, String emptyHint, String[] items, int[] counts,
                                IntConsumer onPick, Runnable onNew) {
        Dialog d = sheet(a, 0.62f);
        LinearLayout sheet = sheetBody(a);
        sheet.addView(sheetHeader(a, title, d::dismiss, onNew == null ? null : () -> { d.dismiss(); onNew.run(); }));
        if (items.length == 0) {
            LinearLayout empty = new LinearLayout(a);
            empty.setOrientation(LinearLayout.VERTICAL);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(0, dp(a, 70), 0, dp(a, 40));
            try (java.io.InputStream in = a.getAssets().open("img/empty.png")) {
                android.widget.ImageView iv = new android.widget.ImageView(a);
                iv.setImageBitmap(android.graphics.BitmapFactory.decodeStream(in));
                empty.addView(iv, new LinearLayout.LayoutParams(dp(a, 70), dp(a, 70)));
            } catch (Exception ignored) {}
            TextView t1 = new TextView(a);
            t1.setText("空空如也"); t1.setTextSize(13); t1.setTextColor(ink());
            t1.setGravity(Gravity.CENTER); t1.setPadding(0, dp(a, 12), 0, 0);
            empty.addView(t1);
            TextView t2 = new TextView(a);
            t2.setText(emptyHint); t2.setTextSize(11); t2.setTextColor(gray());
            t2.setGravity(Gravity.CENTER); t2.setPadding(0, dp(a, 4), 0, 0);
            empty.addView(t2);
            if (onNew != null) {
                empty.setOnClickListener(v -> { d.dismiss(); onNew.run(); });
                t2.setOnClickListener(v -> { d.dismiss(); onNew.run(); });
            }
            sheet.addView(empty, new LinearLayout.LayoutParams(-1, 0, 1f));
        } else {
            android.widget.ScrollView sc = new android.widget.ScrollView(a);
            LinearLayout list = new LinearLayout(a);
            list.setOrientation(LinearLayout.VERTICAL);
            list.setPadding(dp(a, 18), 0, dp(a, 18), dp(a, 16));
            for (int i = 0; i < items.length; i++) {
                final int idx = i;
                LinearLayout row = new LinearLayout(a);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding(dp(a, 2), dp(a, 14), dp(a, 2), dp(a, 14));
                TextView name = new TextView(a);
                name.setText(items[i]); name.setTextSize(14); name.setTextColor(ink());
                row.addView(name, new LinearLayout.LayoutParams(0, -2, 1f));
                TextView cnt = new TextView(a);
                cnt.setText((counts != null && idx < counts.length ? counts[idx] : 0) + " 项");
                cnt.setTextSize(12); cnt.setTextColor(gray());
                row.addView(cnt);
                TextView chev = new TextView(a);
                chev.setText("›"); chev.setTextSize(16); chev.setTextColor(gray());
                LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(-2, -2);
                cp.leftMargin = dp(a, 8);
                row.addView(chev, cp);
                if (onPick != null) row.setOnClickListener(v -> { d.dismiss(); onPick.accept(idx); });
                list.addView(row, new LinearLayout.LayoutParams(-1, -2));
            }
            sc.addView(list);
            sheet.addView(sc, new LinearLayout.LayoutParams(-1, 0, 1f));
        }
        showSheet(d, a, sheet, 0.62f);
    }


    interface OrderCallback { void onOrder(String[] names); }

    /** 管理弹层：名称 + 编辑 / 删除 / 拖动手柄（无计数）。 */
    static void manageList(Activity a, String title, String emptyHint, String[] names,
                           Runnable onNew, IntConsumer onEdit, IntConsumer onDelete, OrderCallback onOrder) {
        Dialog d = sheet(a, 0.62f);
        LinearLayout sheet = sheetBody(a);
        sheet.addView(sheetHeader(a, title, d::dismiss, onNew == null ? null : () -> { d.dismiss(); onNew.run(); }));
        if (names.length == 0) {
            LinearLayout empty = new LinearLayout(a);
            empty.setOrientation(LinearLayout.VERTICAL);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(0, dp(a, 70), 0, dp(a, 40));
            try (java.io.InputStream in = a.getAssets().open("img/empty.png")) {
                android.widget.ImageView iv = new android.widget.ImageView(a);
                iv.setImageBitmap(android.graphics.BitmapFactory.decodeStream(in));
                empty.addView(iv, new LinearLayout.LayoutParams(dp(a, 70), dp(a, 70)));
            } catch (Exception ignored) {}
            TextView t1 = new TextView(a);
            t1.setText("空空如也"); t1.setTextSize(13); t1.setTextColor(ink());
            t1.setGravity(Gravity.CENTER); t1.setPadding(0, dp(a, 12), 0, 0);
            empty.addView(t1);
            TextView t2 = new TextView(a);
            t2.setText(emptyHint); t2.setTextSize(11); t2.setTextColor(gray());
            t2.setGravity(Gravity.CENTER); t2.setPadding(0, dp(a, 4), 0, 0);
            empty.addView(t2);
            if (onNew != null) {
                empty.setOnClickListener(v -> { d.dismiss(); onNew.run(); });
                t2.setOnClickListener(v -> { d.dismiss(); onNew.run(); });
            }
            sheet.addView(empty, new LinearLayout.LayoutParams(-1, 0, 1f));
        } else {
            android.widget.ScrollView sc = new android.widget.ScrollView(a);
            LinearLayout container = new LinearLayout(a);
            container.setOrientation(LinearLayout.VERTICAL);
            container.setPadding(dp(a, 18), 0, dp(a, 18), dp(a, 16));
            final float[] downY = new float[1];
            final int[] startIdx = new int[1];
            final int[] finalTarget = new int[1];
            final String[] currentNames = names.clone();
            for (int i = 0; i < names.length; i++) {
                final int idx = i;
                LinearLayout row = new LinearLayout(a);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding(dp(a, 12), dp(a, 14), dp(a, 12), dp(a, 14));
                android.graphics.drawable.GradientDrawable rowBg = new android.graphics.drawable.GradientDrawable();
                rowBg.setColor(MainActivity.dark ? 0xFF242424 : 0xFFF6F6F6);
                rowBg.setCornerRadius(dp(a, 12));
                row.setBackground(rowBg);
                row.setTag(names[i]);
                TextView name = new TextView(a);
                name.setText(names[i]); name.setTextSize(14); name.setTextColor(ink());
                row.addView(name, new LinearLayout.LayoutParams(0, -2, 1f));
                android.widget.ImageView edit = icon(a, "ic_edit", 18);
                android.widget.ImageView del = icon(a, "ic_delete", 18);
                android.widget.ImageView move = icon(a, "ic_move", 18);
                LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(dp(a, 18), dp(a, 18));
                ip.leftMargin = dp(a, 14);
                row.addView(edit, ip);
                LinearLayout.LayoutParams dp2 = new LinearLayout.LayoutParams(dp(a, 18), dp(a, 18));
                dp2.leftMargin = dp(a, 14);
                row.addView(del, dp2);
                LinearLayout.LayoutParams mp = new LinearLayout.LayoutParams(dp(a, 18), dp(a, 18));
                mp.leftMargin = dp(a, 14);
                row.addView(move, mp);
                if (onEdit != null) edit.setOnClickListener(v -> { d.dismiss(); onEdit.accept(idx); });
                if (onDelete != null) del.setOnClickListener(v -> { d.dismiss(); onDelete.accept(idx); });
                if (onOrder != null) {
                    move.setOnTouchListener((v, e) -> {
                        switch (e.getActionMasked()) {
                            case android.view.MotionEvent.ACTION_DOWN: {
                                downY[0] = e.getRawY();
                                startIdx[0] = container.indexOfChild(row);
                                finalTarget[0] = startIdx[0];
                                android.graphics.drawable.GradientDrawable sel = new android.graphics.drawable.GradientDrawable();
                                sel.setColor(MainActivity.dark ? 0xFF2A3A10 : 0xFFEDF8C9);
                                sel.setCornerRadius(dp(a, 12));
                                row.setBackground(sel);
                                row.setElevation(dp(a, 6));
                                android.view.ViewParent parent = v.getParent();
                                while (parent != null && !(parent instanceof android.widget.ScrollView)) parent = parent.getParent();
                                if (parent != null) parent.requestDisallowInterceptTouchEvent(true);
                                return true;
                            }
                            case android.view.MotionEvent.ACTION_MOVE: {
                                View first = container.getChildAt(0);
                                int rowH = (first != null && first.getHeight() > 0 ? first.getHeight() : dp(a, 47)) + dp(a, 8);
                                float delta = e.getRawY() - downY[0];
                                row.setTranslationY(delta);
                                int steps = Math.round(delta / (float) rowH);
                                int target = Math.max(0, Math.min(container.getChildCount() - 1, startIdx[0] + steps));
                                finalTarget[0] = target;
                                for (int k = 0; k < container.getChildCount(); k++) {
                                    if (k == startIdx[0]) continue;
                                    float ty = 0;
                                    if (startIdx[0] < target && k > startIdx[0] && k <= target) ty = -rowH;
                                    else if (target < startIdx[0] && k >= target && k < startIdx[0]) ty = rowH;
                                    container.getChildAt(k).animate().translationY(ty).setDuration(120).start();
                                }
                                return true;
                            }
                            case android.view.MotionEvent.ACTION_UP:
                            case android.view.MotionEvent.ACTION_CANCEL: {
                                row.setTranslationY(0);
                                row.setElevation(0);
                                android.graphics.drawable.GradientDrawable nb = new android.graphics.drawable.GradientDrawable();
                                nb.setColor(MainActivity.dark ? 0xFF242424 : 0xFFF6F6F6);
                                nb.setCornerRadius(dp(a, 12));
                                row.setBackground(nb);
                                for (int k = 0; k < container.getChildCount(); k++) container.getChildAt(k).setTranslationY(0);
                                if (finalTarget[0] != startIdx[0]) {
                                    String moved = currentNames[startIdx[0]];
                                    java.util.List<String> list = new java.util.ArrayList<>(java.util.Arrays.asList(currentNames));
                                    list.remove(startIdx[0]);
                                    list.add(finalTarget[0], moved);
                                    String[] order = list.toArray(new String[0]);
                                    java.util.Map<String, View> byName = new java.util.HashMap<>();
                                    for (int k = 0; k < container.getChildCount(); k++) byName.put(String.valueOf(container.getChildAt(k).getTag()), container.getChildAt(k));
                                    container.removeAllViews();
                                    for (String n : order) {
                                        View vw = byName.get(n);
                                        if (vw != null) {
                                            LinearLayout.LayoutParams lp2 = new LinearLayout.LayoutParams(-1, -2);
                                            lp2.bottomMargin = dp(a, 8);
                                            container.addView(vw, lp2);
                                        }
                                    }
                                    System.arraycopy(order, 0, currentNames, 0, order.length);
                                    onOrder.onOrder(order);
                                }
                                return true;
                            }
                        }
                        return false;
                    });
                }
                LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(-1, -2);
                rowLp.bottomMargin = dp(a, 8);
                container.addView(row, rowLp);
            }
            sc.addView(container);
            sheet.addView(sc, new LinearLayout.LayoutParams(-1, 0, 1f));
        }
        showSheet(d, a, sheet, 0.62f);
    }

    private static android.widget.ImageView icon(Activity a, String name, int sizeDp) {
        android.widget.ImageView iv = new android.widget.ImageView(a);
        try (java.io.InputStream in = a.getAssets().open("icons/" + name + ".png")) {
            iv.setImageBitmap(android.graphics.BitmapFactory.decodeStream(in));
        } catch (Exception ignored) {}
        iv.setColorFilter(gray());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(a, sizeDp), dp(a, sizeDp));
        iv.setLayoutParams(lp);
        return iv;
    }

    static void input(Activity a, String title, String hint, String initial, String okText, TextConsumer onOk) {
        int w = (int) (a.getResources().getDisplayMetrics().widthPixels * 0.86f);
        LinearLayout card = card(a, w);
        card.setLayoutParams(new ViewGroup.LayoutParams(w, ViewGroup.LayoutParams.WRAP_CONTENT));
        TextView t = new TextView(a);
        t.setText(title); t.setTextSize(17); t.setTextColor(ink()); t.setGravity(Gravity.CENTER);
        t.setTypeface(android.graphics.Typeface.create("sans-serif-medium", 0));
        card.addView(t, new LinearLayout.LayoutParams(-1, -2));
        EditText et = new EditText(a);
        et.setHint(hint);
        et.setText(initial == null ? "" : initial);
        et.setSingleLine(true);
        et.setTextSize(14); et.setTextColor(ink()); et.setHintTextColor(gray());
        android.graphics.drawable.GradientDrawable field = new android.graphics.drawable.GradientDrawable();
        field.setColor(MainActivity.dark ? 0xFF2A2A2A : 0xFFF4F4F4);
        field.setCornerRadius(dp(a, 10));
        et.setBackground(field);
        et.setPadding(dp(a, 12), dp(a, 10), dp(a, 12), dp(a, 10));
        LinearLayout.LayoutParams ep = new LinearLayout.LayoutParams(-1, -2);
        ep.topMargin = dp(a, 14);
        card.addView(et, ep);
        LinearLayout row = new LinearLayout(a);
        row.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(-1, -2);
        rp.topMargin = dp(a, 16);
        card.addView(row, rp);
        TextView c = button(a, "关闭", false);
        TextView o = button(a, okText, true);
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(0, -2, 1f);
        row.addView(c, bp);
        LinearLayout.LayoutParams bp2 = new LinearLayout.LayoutParams(0, -2, 1f);
        bp2.leftMargin = dp(a, 10);
        row.addView(o, bp2);
        Dialog d = dialog(a, card);
        c.setOnClickListener(v -> d.dismiss());
        o.setOnClickListener(v -> {
            String v2 = et.getText().toString().trim();
            if (!v2.isEmpty()) { d.dismiss(); if (onOk != null) onOk.on(v2); }
        });
        d.show();
    }


    /** 分类图标选择：默认 + 3D 图标分类标签页。 */
    static void iconPicker(Activity a, String current, String[] labels, String[] folders, Pick onPick) {
        Dialog d = sheet(a, 0.72f);
        LinearLayout sheet = sheetBody(a);
        sheet.addView(sheetHeader(a, "选择图标", d::dismiss, null));
        android.widget.HorizontalScrollView hs = new android.widget.HorizontalScrollView(a);
        hs.setHorizontalScrollBarEnabled(false);
        LinearLayout tabs = new LinearLayout(a);
        tabs.setOrientation(LinearLayout.HORIZONTAL);
        tabs.setPadding(dp(a, 14), dp(a, 2), dp(a, 14), dp(a, 8));
        hs.addView(tabs);
        sheet.addView(hs, new LinearLayout.LayoutParams(-1, -2));
        View divider = new View(a);
        divider.setBackgroundColor(MainActivity.dark ? 0xFF2A2A2A : 0xFFEDEDED);
        sheet.addView(divider, new LinearLayout.LayoutParams(-1, Math.max(1, dp(a, 0.7f))));
        android.widget.ScrollView sc = new android.widget.ScrollView(a);
        android.widget.GridLayout grid = new android.widget.GridLayout(a);
        android.util.DisplayMetrics dm = a.getResources().getDisplayMetrics();
        int gap = dp(a, 6);
        int cols = Math.max(5, (int) Math.floor((dm.widthPixels - dp(a, 28)) / (double) dp(a, 72)));
        grid.setColumnCount(cols);
        grid.setPadding(dp(a, 14), dp(a, 10), dp(a, 14), dp(a, 24));
        sc.addView(grid);
        sheet.addView(sc, new LinearLayout.LayoutParams(-1, 0, 1f));
        final int[] selected = {0};
        final Runnable[] render = new Runnable[1];
        render[0] = () -> {
            grid.removeAllViews();
            String folder = folders[selected[0]];
            java.util.List<String> paths = new java.util.ArrayList<>();
            if (folder.isEmpty()) {
                paths.add("emoji/icon_default_object.png");
                paths.add("emoji/icon_default_vip.png");
            } else {
                try {
                    for (String f : a.getAssets().list(folder))
                        if (f.toLowerCase(java.util.Locale.US).endsWith(".png")) paths.add(folder + "/" + f);
                } catch (Exception ignored) {}
            }
            java.util.Collections.sort(paths);
            int availW = dm.widthPixels - dp(a, 28) - gap * (cols - 1);
            int cellW = Math.max(dp(a, 56), availW / cols);
            int iconSize = Math.max(dp(a, 36), cellW - dp(a, 14));
            for (String path : paths) {
                android.widget.FrameLayout cell = new android.widget.FrameLayout(a);
                cell.setPadding(dp(a, 5), dp(a, 5), dp(a, 5), dp(a, 5));
                try (java.io.InputStream in = a.getAssets().open(path)) {
                    android.widget.ImageView iv = new android.widget.ImageView(a);
                    iv.setImageBitmap(android.graphics.BitmapFactory.decodeStream(in));
                    iv.setScaleType(android.widget.ImageView.ScaleType.FIT_CENTER);
                    cell.addView(iv, new android.widget.FrameLayout.LayoutParams(iconSize, iconSize, Gravity.CENTER));
                } catch (Exception ignored) {}
                boolean on = path.equals(current);
                android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
                bg.setColor(on ? (MainActivity.dark ? 0xFF22300C : 0xFFEDF8C9) : Color.TRANSPARENT);
                bg.setCornerRadius(dp(a, 12));
                if (on) bg.setStroke(dp(a, 2), 0xFFB6EE25);
                cell.setBackground(bg);
                cell.setOnClickListener(v -> { d.dismiss(); onPick.on(path); });
                android.widget.GridLayout.LayoutParams gp = new android.widget.GridLayout.LayoutParams();
                gp.width = cellW; gp.height = cellW;
                gp.rightMargin = (int) (gap * 0.75f); gp.bottomMargin = gap;
                grid.addView(cell, gp);
            }
        };
        for (int i = 0; i < labels.length; i++) {
            final int idx = i;
            TextView tab = new TextView(a);
            tab.setText(labels[i]);
            tab.setTextSize(12);
            boolean on = i == selected[0];
            tab.setTextColor(on ? (MainActivity.dark ? 0xFF111111 : 0xFFFFFFFF) : ink2());
            android.graphics.drawable.GradientDrawable tb = new android.graphics.drawable.GradientDrawable();
            tb.setCornerRadius(dp(a, 14));
            tb.setColor(on ? 0xFFB6EE25 : (MainActivity.dark ? 0xFF262626 : 0xFFF1F1F1));
            tab.setBackground(tb);
            tab.setPadding(dp(a, 12), dp(a, 6), dp(a, 12), dp(a, 6));
            LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(-2, -2);
            tp.rightMargin = dp(a, 8);
            tab.setOnClickListener(v -> {
                selected[0] = idx;
                for (int k = 0; k < tabs.getChildCount(); k++) {
                    TextView tv = (TextView) tabs.getChildAt(k);
                    boolean sel = k == idx;
                    tv.setTextColor(sel ? (MainActivity.dark ? 0xFF111111 : 0xFFFFFFFF) : ink2());
                    android.graphics.drawable.GradientDrawable tbg = new android.graphics.drawable.GradientDrawable();
                    tbg.setCornerRadius(dp(a, 14));
                    tbg.setColor(sel ? 0xFFB6EE25 : (MainActivity.dark ? 0xFF262626 : 0xFFF1F1F1));
                    tv.setBackground(tbg);
                }
                render[0].run();
            });
            tabs.addView(tab, tp);
        }
        render[0].run();
        showSheet(d, a, sheet, 0.8f);
    }


    interface BrandPick { void on(String name, String id); }

    /** 品牌选择：数码/汽车/自行车 标签 + Logo 网格。 */
    static void brandPicker(Activity a, String currentId, JSONObject brands, BrandPick onPick) {
        if (brands == null) return;
        Dialog d = sheet(a, 0.78f);
        LinearLayout sheet = sheetBody(a);
        sheet.addView(sheetHeader(a, "选择品牌", d::dismiss, null));
        android.widget.HorizontalScrollView hs = new android.widget.HorizontalScrollView(a);
        hs.setHorizontalScrollBarEnabled(false);
        LinearLayout tabs = new LinearLayout(a);
        tabs.setOrientation(LinearLayout.HORIZONTAL);
        tabs.setPadding(dp(a, 14), dp(a, 2), dp(a, 14), dp(a, 8));
        hs.addView(tabs);
        sheet.addView(hs, new LinearLayout.LayoutParams(-1, -2));
        View divider = new View(a);
        divider.setBackgroundColor(MainActivity.dark ? 0xFF2A2A2A : 0xFFEDEDED);
        sheet.addView(divider, new LinearLayout.LayoutParams(-1, Math.max(1, dp(a, 0.7f))));
        android.widget.ScrollView sc = new android.widget.ScrollView(a);
        android.widget.GridLayout grid = new android.widget.GridLayout(a);
        android.util.DisplayMetrics dm = a.getResources().getDisplayMetrics();
        int gap = dp(a, 6);
        int cols = Math.max(4, (int) Math.floor((dm.widthPixels - dp(a, 28)) / (double) dp(a, 116)));
        grid.setColumnCount(cols);
        grid.setPadding(dp(a, 14), dp(a, 10), dp(a, 14), dp(a, 24));
        sc.addView(grid);
        sheet.addView(sc, new LinearLayout.LayoutParams(-1, 0, 1f));
        final String[] keys = {"digital", "car", "bike"};
        final String[] labels = {"数码", "汽车", "自行车"};
        final int[] sel = {0};
        final Runnable[] render = new Runnable[1];
        render[0] = () -> {
            grid.removeAllViews();
            try {
                JSONArray arr = brands.optJSONArray(keys[sel[0]]);
                int availW = dm.widthPixels - dp(a, 28) - gap * (cols - 1);
                int cellW = Math.max(dp(a, 86), availW / cols);
                int cellBg = MainActivity.dark ? 0xFF242424 : 0xFFF6F6F6;
                boolean withClear = currentId != null && !currentId.isEmpty();
                if (withClear) {
                    android.widget.FrameLayout cell = new android.widget.FrameLayout(a);
                    android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
                    bg.setColor(cellBg); bg.setCornerRadius(dp(a, 12)); cell.setBackground(bg);
                    LinearLayout col = new LinearLayout(a);
                    col.setOrientation(LinearLayout.VERTICAL);
                    col.setGravity(Gravity.CENTER);
                    TextView none = new TextView(a);
                    none.setText("无"); none.setTextSize(22); none.setTextColor(gray());
                    col.addView(none);
                    TextView nm = new TextView(a);
                    nm.setText("无品牌"); nm.setTextSize(10); nm.setTextColor(gray());
                    nm.setPadding(0, dp(a, 6), 0, 0);
                    col.addView(nm);
                    cell.addView(col, new android.widget.FrameLayout.LayoutParams(-1, -1));
                    cell.setOnClickListener(v -> { d.dismiss(); onPick.on("", ""); });
                    android.widget.GridLayout.LayoutParams gp = new android.widget.GridLayout.LayoutParams();
                    gp.width = cellW; gp.height = dp(a, 112);
                    gp.rightMargin = (int) (gap * 0.75f); gp.bottomMargin = gap;
                    grid.addView(cell, gp);
                }
                for (int i = 0; arr != null && i < arr.length(); i++) {
                    JSONArray item = arr.optJSONArray(i);
                    if (item == null) continue;
                    String name = item.optString(0), id = item.optString(1);
                    android.widget.FrameLayout cell = new android.widget.FrameLayout(a);
                    boolean on = id.equals(currentId);
                    android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
                    bg.setColor(on ? (MainActivity.dark ? 0xFF22300C : 0xFFEDF8C9) : cellBg);
                    bg.setCornerRadius(dp(a, 12));
                    if (on) bg.setStroke(dp(a, 2), 0xFFB6EE25);
                    cell.setBackground(bg);
                    LinearLayout col = new LinearLayout(a);
                    col.setOrientation(LinearLayout.VERTICAL);
                    col.setGravity(Gravity.CENTER);
                    try (java.io.InputStream in = a.getAssets().open("brands/" + id + ".png")) {
                        android.widget.ImageView iv = new android.widget.ImageView(a);
                        iv.setImageBitmap(android.graphics.BitmapFactory.decodeStream(in));
                        iv.setScaleType(android.widget.ImageView.ScaleType.FIT_CENTER);
                        if (MainActivity.dark) {
                            android.widget.FrameLayout plate = new android.widget.FrameLayout(a);
                            android.graphics.drawable.GradientDrawable pb = new android.graphics.drawable.GradientDrawable();
                            pb.setColor(0xFFFFFFFF); pb.setCornerRadius(dp(a, 10));
                            plate.setBackground(pb);
                            plate.addView(iv, new android.widget.FrameLayout.LayoutParams(dp(a, 54), dp(a, 54), Gravity.CENTER));
                            col.addView(plate, new LinearLayout.LayoutParams(dp(a, 64), dp(a, 64)));
                        } else {
                            col.addView(iv, new LinearLayout.LayoutParams(dp(a, 62), dp(a, 62)));
                        }
                    } catch (Exception ignored) {}
                    TextView nm = new TextView(a);
                    nm.setText(name); nm.setTextSize(10); nm.setTextColor(ink2());
                    nm.setPadding(0, dp(a, 6), 0, 0);
                    col.addView(nm);
                    cell.addView(col, new android.widget.FrameLayout.LayoutParams(-1, -1));
                    cell.setOnClickListener(v -> { d.dismiss(); onPick.on(name, id); });
                    android.widget.GridLayout.LayoutParams gp = new android.widget.GridLayout.LayoutParams();
                    gp.width = cellW; gp.height = dp(a, 112);
                    gp.rightMargin = (int) (gap * 0.75f); gp.bottomMargin = gap;
                    grid.addView(cell, gp);
                }
            } catch (Exception ignored) {}
        };
        for (int i = 0; i < labels.length; i++) {
            final int idx = i;
            TextView tab = new TextView(a);
            tab.setText(labels[i]); tab.setTextSize(12);
            boolean on = i == 0;
            tab.setTextColor(on ? (MainActivity.dark ? 0xFF111111 : 0xFFFFFFFF) : ink2());
            android.graphics.drawable.GradientDrawable tb = new android.graphics.drawable.GradientDrawable();
            tb.setCornerRadius(dp(a, 14));
            tb.setColor(on ? 0xFFB6EE25 : (MainActivity.dark ? 0xFF262626 : 0xFFF1F1F1));
            tab.setBackground(tb);
            tab.setPadding(dp(a, 12), dp(a, 6), dp(a, 12), dp(a, 6));
            LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(-2, -2);
            tp.rightMargin = dp(a, 8);
            tab.setOnClickListener(v -> {
                sel[0] = idx;
                for (int k = 0; k < tabs.getChildCount(); k++) {
                    TextView tv = (TextView) tabs.getChildAt(k);
                    boolean s = k == idx;
                    tv.setTextColor(s ? (MainActivity.dark ? 0xFF111111 : 0xFFFFFFFF) : ink2());
                    android.graphics.drawable.GradientDrawable tbg = new android.graphics.drawable.GradientDrawable();
                    tbg.setCornerRadius(dp(a, 14));
                    tbg.setColor(s ? 0xFFB6EE25 : (MainActivity.dark ? 0xFF262626 : 0xFFF1F1F1));
                    tv.setBackground(tbg);
                }
                render[0].run();
            });
            tabs.addView(tab, tp);
        }
        render[0].run();
        showSheet(d, a, sheet, 0.78f);
    }


    /** 型号选择：品牌型号按产品线分组 + 自定义输入。 */
    static void modelPicker(Activity a, String brandName, String current, JSONArray groups, TextConsumer onPick) {
        Dialog d = sheet(a, 0.6f);
        LinearLayout sheet = sheetBody(a);
        sheet.addView(sheetHeader(a, brandName + " 型号", d::dismiss, null));
        android.widget.ScrollView sc = new android.widget.ScrollView(a);
        LinearLayout list = new LinearLayout(a);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(dp(a, 18), dp(a, 2), dp(a, 18), dp(a, 18));
        for (int g = 0; g < groups.length(); g++) {
            JSONArray entry = groups.optJSONArray(g);
            if (entry != null && entry.length() >= 2) {
                String groupName = entry.optString(0);
                JSONArray ms = entry.optJSONArray(1);
                if (ms == null) continue;
                if (!groupName.isEmpty()) {
                    TextView h = new TextView(a);
                    h.setText(groupName);
                    h.setTextSize(12);
                    h.setTextColor(gray());
                    h.setPadding(dp(a, 4), dp(a, 16), dp(a, 4), dp(a, 6));
                    list.addView(h, new LinearLayout.LayoutParams(-1, -2));
                }
                for (int k = 0; k < ms.length(); k++) addModelRow(a, d, list, ms.optString(k), current, onPick);
            } else {
                String m = groups.optString(g);
                if (!m.isEmpty()) addModelRow(a, d, list, m, current, onPick);
            }
        }
        LinearLayout custom = new LinearLayout(a);
        custom.setGravity(Gravity.CENTER_VERTICAL);
        custom.setPadding(dp(a, 4), dp(a, 16), dp(a, 4), dp(a, 14));
        TextView ct = new TextView(a);
        ct.setText("自定义型号…"); ct.setTextSize(14); ct.setTextColor(ink2());
        custom.addView(ct, new LinearLayout.LayoutParams(-1, -2));
        custom.setOnClickListener(v -> {
            d.dismiss();
            input(a, "自定义型号", "请输入具体型号", current, "保存", onPick::on);
        });
        list.addView(custom, new LinearLayout.LayoutParams(-1, -2));
        sc.addView(list);
        sheet.addView(sc, new LinearLayout.LayoutParams(-1, 0, 1f));
        showSheet(d, a, sheet, 0.6f);
    }

    private static void addModelRow(Activity a, Dialog d, LinearLayout list, String m, String current, TextConsumer onPick) {
        LinearLayout row = new LinearLayout(a);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(a, 4), dp(a, 13), dp(a, 4), dp(a, 13));
        TextView t = new TextView(a);
        t.setText(m); t.setTextSize(14); t.setTextColor(ink());
        row.addView(t, new LinearLayout.LayoutParams(0, -2, 1f));
        TextView check = new TextView(a);
        check.setText(m.equals(current) ? "✓" : "");
        check.setTextSize(15); check.setTextColor(0xFFB6EE25);
        row.addView(check);
        row.setOnClickListener(v -> { d.dismiss(); onPick.on(m); });
        list.addView(row, new LinearLayout.LayoutParams(-1, -2));
    }

}
