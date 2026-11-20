package com.xiaoman.app

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Outline
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import java.io.InputStream
import java.util.HashMap

/** 原生 UI 工具箱：dp、字体、色卡、圆角背景、图标。 */
class Ui(activity: Activity) {
    @JvmField val a: Activity = activity
    @JvmField val regular: Typeface
    @JvmField val medium: Typeface
    @JvmField val bold: Typeface
    private val density: Float
    private val bitmaps = HashMap<String, Bitmap>()

    init {
        density = activity.resources.displayMetrics.density
        // 原 App 的 WeChatSansStd 是仅含拉丁字符的子集且小写被映射成小型大写，
        // 直接用系统字体，保持中英文混排一致。
        regular = Typeface.create("sans-serif", Typeface.NORMAL)
        medium = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        bold = Typeface.create("sans-serif", Typeface.BOLD)
    }

    fun dp(v: Float): Int = Math.round(v * density)

    // ---------- 主题色 ----------
    fun bg(): Int = if (MainActivity.dark) 0xFF101010.toInt() else 0xFFF4F4F4.toInt()
    fun card(): Int = if (MainActivity.dark) 0xFF1C1C1C.toInt() else 0xFFFFFFFF.toInt()
    fun ink(): Int = if (MainActivity.dark) 0xFFF2F2F2.toInt() else 0xFF1A1A1A.toInt()
    fun ink2(): Int = if (MainActivity.dark) 0xFFB8B8B8.toInt() else 0xFF5C5C5C.toInt()
    fun gray(): Int = if (MainActivity.dark) 0xFF9A9A9A.toInt() else 0xFFA3A3A3.toInt()
    fun line(): Int = if (MainActivity.dark) 0xFF2A2A2A.toInt() else 0xFFEFEFEF.toInt()
    fun chipBg(): Int = if (MainActivity.dark) 0xFF262626.toInt() else 0xFFE9E9E9.toInt()
    fun chipInk(): Int = if (MainActivity.dark) 0xFF9A9A9A.toInt() else 0xFF7A7A7A.toInt()
    fun chipOnBg(): Int = if (MainActivity.dark) 0xFFF2F2F2.toInt() else 0xFF111111.toInt()
    fun chipOnInk(): Int = if (MainActivity.dark) 0xFF111111.toInt() else 0xFFFFFFFF.toInt()

    // ---------- 文本 ----------
    fun text(s: String, sp: Float, color: Int): TextView = text(s, sp, color, regular)

    fun text(s: String, sp: Float, color: Int, tf: Typeface): TextView {
        val t = TextView(a)
        t.text = s
        t.setTextSize(sp)
        t.setTextColor(color)
        t.typeface = tf
        t.includeFontPadding = false
        return t
    }

    // ---------- 容器 ----------
    fun vbox(): LinearLayout {
        val l = LinearLayout(a)
        l.orientation = LinearLayout.VERTICAL
        return l
    }

    fun hbox(): LinearLayout {
        val l = LinearLayout(a)
        l.orientation = LinearLayout.HORIZONTAL
        l.gravity = Gravity.CENTER_VERTICAL
        return l
    }

    fun lp(w: Int, h: Int): LinearLayout.LayoutParams = LinearLayout.LayoutParams(w, h)

    fun lpw(weight: Float, h: Int): LinearLayout.LayoutParams = LinearLayout.LayoutParams(0, h, weight)

    // ---------- 间距 ----------
    fun space(h: Int): View {
        val v = View(a)
        v.layoutParams = LinearLayout.LayoutParams(1, dp(h.toFloat()))
        return v
    }

    fun hline(): View {
        val v = View(a)
        v.setBackgroundColor(line())
        v.layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            Math.max(1, dp(0.7f))
        )
        return v
    }

    // ---------- 背景 ----------
    fun round(fill: Int, radiusDp: Float): GradientDrawable {
        val d = GradientDrawable()
        d.setColor(fill)
        d.cornerRadius = dp(radiusDp).toFloat()
        return d
    }

    fun roundStroke(fill: Int, radiusDp: Float, stroke: Int, widthDp: Float): GradientDrawable {
        val d = round(fill, radiusDp)
        d.setStroke(Math.max(1, dp(widthDp)), stroke)
        return d
    }

    fun circle(fill: Int): GradientDrawable {
        val d = GradientDrawable()
        d.shape = GradientDrawable.OVAL
        d.setColor(fill)
        return d
    }

    fun gradient(vararg colors: Int): GradientDrawable {
        val d = GradientDrawable(GradientDrawable.Orientation.TL_BR, colors)
        d.cornerRadius = dp(Radius.CARD_MD).toFloat()
        return d
    }

    fun gradientSquare(vararg colors: Int): GradientDrawable {
        val d = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, colors)
        d.cornerRadius = 0f
        return d
    }

    fun card(v: View) {
        v.background = round(card(), Radius.CARD)
        v.elevation = dp(2f).toFloat()
    }

    /**
     * 显式指定阴影轮廓。部分 ROM 不会从 GradientDrawable 推导轮廓，
     * 导致圆角卡片/圆形按钮出现方形阴影。
     */
    fun clipRound(v: View, radiusDp: Float): View {
        v.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setRoundRect(0, 0, view.width, view.height, dp(radiusDp).toFloat())
            }
        }
        return v
    }

    fun clipCircle(v: View): View {
        v.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setOval(0, 0, view.width, view.height)
            }
        }
        return v
    }

    // ---------- 图片 ----------
    fun bitmap(path: String): Bitmap? {
        val b = bitmaps[path]
        if (b != null) return b
        var out: Bitmap? = null
        try {
            a.assets.open(path).use { input: InputStream ->
                out = BitmapFactory.decodeStream(input)
            }
        } catch (_: Exception) {
        }
        if (out != null) bitmaps[path] = out!!
        return out
    }

    fun image(path: String, sizeDp: Int): ImageView {
        val iv = ImageView(a)
        val b = bitmap(path)
        if (b != null) iv.setImageBitmap(b) else iv.visibility = View.GONE
        iv.layoutParams = LinearLayout.LayoutParams(dp(sizeDp.toFloat()), dp(sizeDp.toFloat()))
        iv.scaleType = ImageView.ScaleType.FIT_CENTER
        return iv
    }

    fun icon(name: String, sizeDp: Int): ImageView = image("icons/$name.png", sizeDp)

    fun fileImage(path: String, sizeDp: Int, radiusDp: Float = Radius.FIELD): ImageView {
        val iv = ImageView(a)
        try {
            iv.setImageBitmap(BitmapFactory.decodeFile(path))
        } catch (_: Exception) {
        }
        iv.layoutParams = LinearLayout.LayoutParams(dp(sizeDp.toFloat()), dp(sizeDp.toFloat()))
        iv.scaleType = ImageView.ScaleType.CENTER_CROP
        iv.background = round(0x00000000, radiusDp)
        iv.clipToOutline = true
        return iv
    }

    fun iconTint(name: String, sizeDp: Int, color: Int): ImageView {
        val iv = icon(name, sizeDp)
        iv.setColorFilter(color)
        return iv
    }

    // ---------- 常用控件 ----------
    fun chip(label: String, on: Boolean): TextView {
        val t = text(label, FontSize.SMALL, if (on) chipOnInk() else chipInk(), medium)
        t.background = round(if (on) chipOnBg() else chipBg(), Radius.CHIP)
        t.setPadding(dp(13f), dp(7f), dp(13f), dp(7f))
        return t
    }

    fun bar(fraction: Float, fillColor: Int): View {
        val b = BarView(a)
        b.frac = Math.max(0f, Math.min(1f, fraction))
        b.bgColor = line()
        b.fgColor = fillColor
        b.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(Size.BAR_HEIGHT.toFloat()))
        return b
    }

    fun pillButton(label: String, bg: Int, fg: Int, radius: Float): TextView {
        val t = text(label, FontSize.META, fg, medium)
        t.background = round(bg, radius)
        t.setPadding(dp(14f), dp(8f), dp(14f), dp(8f))
        return t
    }

    fun alpha(color: Int, a: Float): Int =
        Color.argb((Color.alpha(color) * a).toInt(), Color.red(color), Color.green(color), Color.blue(color))

    companion object {
        @JvmField val LIME = 0xFFC9F44F.toInt()
        @JvmField val LIME_DEEP = 0xFFB6EE25.toInt()
        @JvmField val TEAL = 0xFF3ED6B0.toInt()
        @JvmField val ORANGE = 0xFFFF7A45.toInt()
        @JvmField val RED = 0xFFFF5A4E.toInt()

        @JvmStatic
        fun margin(v: View, l: Int, t: Int, r: Int, b: Int) {
            val p = v.layoutParams
            if (p is ViewGroup.MarginLayoutParams) {
                p.setMargins(l, t, r, b)
                v.layoutParams = p
            }
        }
    }
}
