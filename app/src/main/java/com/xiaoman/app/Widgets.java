package com.xiaoman.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.View;

/** 细进度条 */
class BarView extends View {
    float frac = 0;
    int bgColor = 0xFFEFEFEF, fgColor = 0xFFB6EE25;
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

    BarView(Context c) { super(c); }

    @Override protected void onDraw(Canvas canvas) {
        float w = getWidth(), h = getHeight(), r = h / 2f;
        p.setColor(bgColor);
        canvas.drawRoundRect(new RectF(0, 0, w, h), r, r, p);
        if (frac > 0) {
            p.setColor(fgColor);
            canvas.drawRoundRect(new RectF(0, 0, Math.max(h, w * frac), h), r, r, p);
        }
    }
}

/** 价值/成本趋势折线图（自绘） */
class ChartView extends View {
    double[] data = new double[0];
    String maxLabel = "", midLabel = "", zeroLabel = "", leftLabel = "", rightLabel = "";
    int lineColor = 0xFFB6EE25;
    int fillColor = 0xFFC9F44F;
    int gridColor = 0xFFEFEFEF;
    int textColor = 0xFFA3A3A3;

    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint grid = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float d;

    ChartView(Context c) {
        super(c);
        d = c.getResources().getDisplayMetrics().density;
        line.setStyle(Paint.Style.STROKE);
        line.setStrokeWidth(dp(2));
        line.setStrokeCap(Paint.Cap.ROUND);
        grid.setStyle(Paint.Style.STROKE);
        grid.setStrokeWidth(dp(1));
        grid.setPathEffect(new android.graphics.DashPathEffect(new float[]{dp(3), dp(4)}, 0));
        text.setTextSize(dp(9));
    }

    void setData(double[] values, String max, String mid, String zero, String left, String right) {
        data = values;
        maxLabel = max; midLabel = mid; zeroLabel = zero; leftLabel = left; rightLabel = right;
        invalidate();
    }

    private float dp(float v) { return v * d; }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (data == null || data.length == 0) return;
        float w = getWidth(), h = getHeight();
        float pt = dp(8), pb = dp(18), pl = dp(6), pr = dp(42);
        float cw = Math.max(dp(1), w - pl - pr), ch = Math.max(dp(1), h - pt - pb);

        double max = 0;
        for (double v : data) max = Math.max(max, v);
        if (max <= 0) max = 1;

        float[] xs = new float[data.length], ys = new float[data.length];
        for (int i = 0; i < data.length; i++) {
            xs[i] = pl + (data.length == 1 ? cw : cw * i / (data.length - 1f));
            ys[i] = pt + ch - (float) (data[i] / max) * ch;
        }

        grid.setColor(gridColor);
        for (float r : new float[]{1f / 3, 2f / 3}) {
            float y = pt + ch * r;
            canvas.drawLine(pl, y, pl + cw, y, grid);
        }

        Path path = new Path();
        path.moveTo(xs[0], ys[0]);
        for (int i = 1; i < data.length; i++) path.lineTo(xs[i], ys[i]);
        Path area = new Path(path);
        area.lineTo(xs[xs.length - 1], pt + ch);
        area.lineTo(xs[0], pt + ch);
        area.close();

        fill.setShader(new LinearGradient(0, pt, 0, pt + ch,
                new int[]{Color.argb(140, Color.red(fillColor), Color.green(fillColor), Color.blue(fillColor)), Color.TRANSPARENT},
                null, Shader.TileMode.CLAMP));
        canvas.drawPath(area, fill);
        line.setColor(lineColor);
        canvas.drawPath(path, line);

        Paint dot = new Paint(Paint.ANTI_ALIAS_FLAG);
        dot.setColor(0xFFFFFFFF);
        canvas.drawCircle(xs[xs.length - 1], ys[ys.length - 1], dp(3.5f), dot);
        dot.setStyle(Paint.Style.STROKE);
        dot.setStrokeWidth(dp(2));
        dot.setColor(lineColor);
        canvas.drawCircle(xs[xs.length - 1], ys[ys.length - 1], dp(3.5f), dot);

        text.setColor(textColor);
        text.setTextAlign(Paint.Align.RIGHT);
        canvas.drawText(maxLabel, w - dp(2), pt + dp(8), text);
        canvas.drawText(midLabel, w - dp(2), pt + ch / 2 + dp(3), text);
        canvas.drawText(zeroLabel, w - dp(2), pt + ch - dp(1), text);
        text.setTextAlign(Paint.Align.LEFT);
        canvas.drawText(leftLabel, pl, h - dp(1), text);
        text.setTextAlign(Paint.Align.RIGHT);
        canvas.drawText(rightLabel, pl + cw, h - dp(1), text);
    }
}

/** 书签图标（自绘，避免额外位图）。 */
class BookmarkView extends View {
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float d;
    BookmarkView(Context c, int color) {
        super(c);
        d = c.getResources().getDisplayMetrics().density;
        p.setColor(color);
    }
    @Override protected void onDraw(Canvas canvas) {
        float w = getWidth(), h = getHeight();
        Path path = new Path();
        path.moveTo(w * 0.22f, h * 0.08f);
        path.lineTo(w * 0.78f, h * 0.08f);
        path.lineTo(w * 0.78f, h * 0.92f);
        path.lineTo(w * 0.5f, h * 0.74f);
        path.lineTo(w * 0.22f, h * 0.92f);
        path.close();
        canvas.drawPath(path, p);
    }
}
