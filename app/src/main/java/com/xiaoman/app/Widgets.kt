package com.xiaoman.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.view.View

/** 细进度条 */
class BarView(c: Context) : View(c) {
    @JvmField var frac: Float = 0f
    @JvmField var bgColor: Int = 0xFFEFEFEF.toInt()
    @JvmField var fgColor: Int = 0xFFB6EE25.toInt()
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val r = h / 2f
        p.color = bgColor
        canvas.drawRoundRect(RectF(0f, 0f, w, h), r, r, p)
        if (frac > 0) {
            p.color = fgColor
            canvas.drawRoundRect(RectF(0f, 0f, Math.max(h, w * frac), h), r, r, p)
        }
    }
}

/** 价值/成本趋势折线图（自绘） */
class ChartView(c: Context) : View(c) {
    @JvmField var data: DoubleArray = DoubleArray(0)
    @JvmField var maxLabel: String = ""
    @JvmField var midLabel: String = ""
    @JvmField var zeroLabel: String = ""
    @JvmField var leftLabel: String = ""
    @JvmField var rightLabel: String = ""
    @JvmField var lineColor: Int = 0xFFB6EE25.toInt()
    @JvmField var fillColor: Int = 0xFFC9F44F.toInt()
    @JvmField var gridColor: Int = 0xFFEFEFEF.toInt()
    @JvmField var textColor: Int = 0xFFA3A3A3.toInt()

    private val line = Paint(Paint.ANTI_ALIAS_FLAG)
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val text = Paint(Paint.ANTI_ALIAS_FLAG)
    private val grid = Paint(Paint.ANTI_ALIAS_FLAG)
    private val density: Float

    init {
        density = c.resources.displayMetrics.density
        line.style = Paint.Style.STROKE
        line.strokeWidth = dp(2f)
        line.strokeCap = Paint.Cap.ROUND
        grid.style = Paint.Style.STROKE
        grid.strokeWidth = dp(1f)
        grid.pathEffect = DashPathEffect(floatArrayOf(dp(3f), dp(4f)), 0f)
        text.textSize = dp(9f)
    }

    fun setData(values: DoubleArray, max: String, mid: String, zero: String, left: String, right: String) {
        data = values
        maxLabel = max
        midLabel = mid
        zeroLabel = zero
        leftLabel = left
        rightLabel = right
        invalidate()
    }

    private fun dp(v: Float): Float = v * density

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (data.isEmpty()) return
        val w = width.toFloat()
        val h = height.toFloat()
        val pt = dp(8f)
        val pb = dp(18f)
        val pl = dp(6f)
        val pr = dp(42f)
        val cw = Math.max(dp(1f), w - pl - pr)
        val ch = Math.max(dp(1f), h - pt - pb)

        var max = 0.0
        for (v in data) max = Math.max(max, v)
        if (max <= 0) max = 1.0

        val xs = FloatArray(data.size)
        val ys = FloatArray(data.size)
        for (i in data.indices) {
            xs[i] = pl + (if (data.size == 1) cw else cw * i / (data.size - 1f))
            ys[i] = pt + ch - (data[i] / max).toFloat() * ch
        }

        grid.color = gridColor
        for (r in floatArrayOf(1f / 3, 2f / 3)) {
            val y = pt + ch * r
            canvas.drawLine(pl, y, pl + cw, y, grid)
        }

        val path = Path()
        path.moveTo(xs[0], ys[0])
        for (i in 1 until data.size) path.lineTo(xs[i], ys[i])
        val area = Path(path)
        area.lineTo(xs[xs.size - 1], pt + ch)
        area.lineTo(xs[0], pt + ch)
        area.close()

        fill.shader = LinearGradient(
            0f, pt, 0f, pt + ch,
            intArrayOf(
                Color.argb(140, Color.red(fillColor), Color.green(fillColor), Color.blue(fillColor)),
                Color.TRANSPARENT
            ),
            null, Shader.TileMode.CLAMP
        )
        canvas.drawPath(area, fill)
        line.color = lineColor
        canvas.drawPath(path, line)

        val dot = Paint(Paint.ANTI_ALIAS_FLAG)
        dot.color = 0xFFFFFFFF.toInt()
        canvas.drawCircle(xs[xs.size - 1], ys[ys.size - 1], dp(3.5f), dot)
        dot.style = Paint.Style.STROKE
        dot.strokeWidth = dp(2f)
        dot.color = lineColor
        canvas.drawCircle(xs[xs.size - 1], ys[ys.size - 1], dp(3.5f), dot)

        text.color = textColor
        text.textAlign = Paint.Align.RIGHT
        canvas.drawText(maxLabel, w - dp(2f), pt + dp(8f), text)
        canvas.drawText(midLabel, w - dp(2f), pt + ch / 2 + dp(3f), text)
        canvas.drawText(zeroLabel, w - dp(2f), pt + ch - dp(1f), text)
        text.textAlign = Paint.Align.LEFT
        canvas.drawText(leftLabel, pl, h - dp(1f), text)
        text.textAlign = Paint.Align.RIGHT
        canvas.drawText(rightLabel, pl + cw, h - dp(1f), text)
    }
}

/** 书签图标（自绘，避免额外位图）。 */
class BookmarkView(c: Context, color: Int) : View(c) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)

    init {
        p.color = color
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val path = Path()
        path.moveTo(w * 0.22f, h * 0.08f)
        path.lineTo(w * 0.78f, h * 0.08f)
        path.lineTo(w * 0.78f, h * 0.92f)
        path.lineTo(w * 0.5f, h * 0.74f)
        path.lineTo(w * 0.22f, h * 0.92f)
        path.close()
        canvas.drawPath(path, p)
    }
}
