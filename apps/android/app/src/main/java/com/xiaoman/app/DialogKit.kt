package com.xiaoman.app

import android.app.Activity
import android.app.Dialog
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import org.json.JSONArray
import org.json.JSONObject
import java.util.function.Consumer
import java.util.function.IntConsumer

/** 对齐原 App 风格的圆角对话框（白卡 + 黑色主按钮）。 */
object DialogKit {
    fun interface TextConsumer {
        fun on(s: String)
    }

    fun interface Pick {
        fun on(path: String)
    }

    fun interface BrandPick {
        fun on(name: String, id: String)
    }

    fun interface OrderCallback {
        fun onOrder(names: Array<String>)
    }

    private fun cardColor(): Int = if (MainActivity.dark) Colors.NEUTRAL_900 else Colors.WHITE
    private fun ink(): Int = if (MainActivity.dark) Colors.NEUTRAL_120 else Colors.NEUTRAL_950
    private fun ink2(): Int = if (MainActivity.dark) Colors.NEUTRAL_360 else Colors.NEUTRAL_600
    private fun gray(): Int = if (MainActivity.dark) Colors.NEUTRAL_400 else Colors.NEUTRAL_380

    private fun dialog(a: Activity, card: View): Dialog {
        val d = Dialog(a)
        d.requestWindowFeature(Window.FEATURE_NO_TITLE)
        // 外层留边，保证卡片自身的圆角不被窗口裁掉
        val wrap = FrameLayout(a)
        val pad = dp(a, 18f)
        wrap.setPadding(pad, 0, pad, 0)
        wrap.addView(card, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        d.setContentView(wrap)
        val w = d.window
        if (w != null) {
            w.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            w.setGravity(Gravity.CENTER)
            w.setLayout(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        return d
    }

    private fun cardView(a: Activity, widthPx: Int): LinearLayout {
        val card = LinearLayout(a)
        card.orientation = LinearLayout.VERTICAL
        val bg = GradientDrawable()
        bg.setColor(cardColor())
        bg.cornerRadius = dp(a, Radius.CARD_LG).toFloat()
        card.background = bg
        card.setPadding(dp(a, 22f), dp(a, 22f), dp(a, 22f), dp(a, 18f))
        return card
    }

    @JvmStatic
    fun dp(a: Activity, v: Float): Int = Math.round(v * a.resources.displayMetrics.density)

    private fun button(a: Activity, label: String, primary: Boolean): TextView {
        val t = TextView(a)
        t.text = label
        t.setTextSize(FontSize.BODY)
        t.gravity = Gravity.CENTER
        t.typeface = Typeface.create("sans-serif-medium", 0)
        t.setTextColor(if (primary) (if (MainActivity.dark) Colors.BLACK else Colors.WHITE) else ink())
        val bg = GradientDrawable()
        bg.cornerRadius = dp(a, Radius.BUTTON).toFloat()
        bg.setColor(
            if (primary) (if (MainActivity.dark) Colors.NEUTRAL_120 else Colors.BLACK)
            else (if (MainActivity.dark) Colors.NEUTRAL_800 else Colors.NEUTRAL_150)
        )
        t.background = bg
        t.minHeight = dp(a, 46f)
        t.setPadding(dp(a, 12f), dp(a, 13f), dp(a, 12f), dp(a, 13f))
        return t
    }

    @JvmStatic
    fun confirm(a: Activity, title: String, message: String, cancel: String, ok: String, onOk: Runnable?) {
        confirm(a, title, message, cancel, ok, onOk, null)
    }

    @JvmStatic
    fun confirm(
        a: Activity, title: String, message: String, cancel: String, ok: String,
        onOk: Runnable?, onCancel: Runnable?
    ) {
        val w = (a.resources.displayMetrics.widthPixels * 0.86f).toInt()
        val card = cardView(a, w)
        card.layoutParams = ViewGroup.LayoutParams(w, ViewGroup.LayoutParams.WRAP_CONTENT)

        val t = TextView(a)
        t.text = title
        t.setTextSize(FontSize.TITLE_LG)
        t.setTextColor(ink())
        t.gravity = Gravity.CENTER
        t.typeface = Typeface.create("sans-serif-medium", 0)
        card.addView(t, LinearLayout.LayoutParams(-1, -2))

        val m = TextView(a)
        m.text = message
        m.setTextSize(FontSize.META)
        m.setTextColor(gray())
        m.gravity = Gravity.CENTER
        val mp = LinearLayout.LayoutParams(-1, -2)
        mp.topMargin = dp(a, Spacing.L)
        card.addView(m, mp)

        val row = LinearLayout(a)
        row.orientation = LinearLayout.HORIZONTAL
        val rp = LinearLayout.LayoutParams(-1, -2)
        rp.topMargin = dp(a, Spacing.XXXL)
        card.addView(row, rp)
        val c = button(a, cancel, false)
        val o = button(a, ok, true)
        row.addView(c, LinearLayout.LayoutParams(0, -2, 1f))
        val bp2 = LinearLayout.LayoutParams(0, -2, 1f)
        bp2.leftMargin = dp(a, Spacing.L)
        row.addView(o, bp2)

        val d = dialog(a, card)
        c.setOnClickListener {
            d.dismiss()
            onCancel?.run()
        }
        o.setOnClickListener {
            d.dismiss()
            onOk?.run()
        }
        d.show()
    }

    @JvmStatic
    fun list(
        a: Activity, title: String, items: Array<String>,
        onPick: IntConsumer?, neutral: String?, onNeutral: Runnable?
    ) {
        val w = (a.resources.displayMetrics.widthPixels * 0.86f).toInt()
        val card = cardView(a, w)
        card.layoutParams = ViewGroup.LayoutParams(w, ViewGroup.LayoutParams.WRAP_CONTENT)

        val t = TextView(a)
        t.text = title
        t.setTextSize(FontSize.TITLE_LG)
        t.setTextColor(ink())
        t.gravity = Gravity.CENTER
        t.typeface = Typeface.create("sans-serif-medium", 0)
        card.addView(t, LinearLayout.LayoutParams(-1, -2))

        val list = LinearLayout(a)
        list.orientation = LinearLayout.VERTICAL
        val lp = LinearLayout.LayoutParams(-1, -2)
        lp.topMargin = dp(a, Spacing.M)
        card.addView(list, lp)

        val d = dialog(a, card)
        for (i in items.indices) {
            val row = TextView(a)
            row.text = items[i]
            row.setTextSize(FontSize.BODY)
            row.setTextColor(ink())
            row.setPadding(dp(a, 6f), dp(a, 13f), dp(a, 6f), dp(a, 13f))
            row.setOnClickListener {
                d.dismiss()
                onPick?.accept(i)
            }
            list.addView(row, LinearLayout.LayoutParams(-1, -2))
        }

        val row = LinearLayout(a)
        row.orientation = LinearLayout.HORIZONTAL
        val rp = LinearLayout.LayoutParams(-1, -2)
        rp.topMargin = dp(a, 14f)
        card.addView(row, rp)
        val close = button(a, "关闭", false)
        row.addView(close, LinearLayout.LayoutParams(0, -2, 1f))
        close.setOnClickListener { d.dismiss() }
        if (neutral != null) {
            val n = button(a, neutral, true)
            val np = LinearLayout.LayoutParams(0, -2, 1f)
            np.leftMargin = dp(a, Spacing.L)
            row.addView(n, np)
            n.setOnClickListener {
                d.dismiss()
                onNeutral?.run()
            }
        }
        d.show()
    }

    @JvmStatic
    fun info(a: Activity, title: String, message: String) {
        val w = (a.resources.displayMetrics.widthPixels * 0.86f).toInt()
        val card = cardView(a, w)
        card.layoutParams = ViewGroup.LayoutParams(w, ViewGroup.LayoutParams.WRAP_CONTENT)
        val t = TextView(a)
        t.text = title
        t.setTextSize(FontSize.TITLE_LG)
        t.setTextColor(ink())
        t.gravity = Gravity.CENTER
        t.typeface = Typeface.create("sans-serif-medium", 0)
        card.addView(t, LinearLayout.LayoutParams(-1, -2))
        val m = TextView(a)
        m.text = message
        m.setTextSize(FontSize.META)
        m.setTextColor(gray())
        m.gravity = Gravity.CENTER
        val mp = LinearLayout.LayoutParams(-1, -2)
        mp.topMargin = dp(a, Spacing.L)
        card.addView(m, mp)
        val d = dialog(a, card)
        val ok = button(a, "知道了", true)
        val bp = LinearLayout.LayoutParams(-1, -2)
        bp.topMargin = dp(a, Spacing.XXXL)
        card.addView(ok, bp)
        ok.setOnClickListener { d.dismiss() }
        d.show()
    }

    @JvmStatic
    fun progress(a: Activity, title: String, message: String): Dialog {
        val w = (a.resources.displayMetrics.widthPixels * 0.7f).toInt()
        val card = cardView(a, w)
        card.setPadding(dp(a, Spacing.HUGE), dp(a, Spacing.HUGE), dp(a, Spacing.HUGE), dp(a, Spacing.HUGE))
        card.layoutParams = ViewGroup.LayoutParams(w, ViewGroup.LayoutParams.WRAP_CONTENT)
        val t = TextView(a)
        t.text = title
        t.setTextSize(FontSize.BODY_LG)
        t.setTextColor(ink())
        t.gravity = Gravity.CENTER
        t.typeface = Typeface.create("sans-serif-medium", 0)
        card.addView(t, LinearLayout.LayoutParams(-1, -2))
        val m = TextView(a)
        m.text = message
        m.setTextSize(FontSize.META)
        m.setTextColor(gray())
        m.gravity = Gravity.CENTER
        val mp = LinearLayout.LayoutParams(-1, -2)
        mp.topMargin = dp(a, Spacing.M)
        card.addView(m, mp)
        val d = dialog(a, card)
        d.setCancelable(false)
        d.show()
        return d
    }

    @JvmStatic
    fun multi(
        a: Activity, title: String, items: Array<String>, checked: BooleanArray,
        onOk: Consumer<BooleanArray>?, neutral: String?, onNeutral: Runnable?
    ) {
        val w = (a.resources.displayMetrics.widthPixels * 0.86f).toInt()
        val card = cardView(a, w)
        card.layoutParams = ViewGroup.LayoutParams(w, ViewGroup.LayoutParams.WRAP_CONTENT)
        val t = TextView(a)
        t.text = title
        t.setTextSize(FontSize.TITLE_LG)
        t.setTextColor(ink())
        t.gravity = Gravity.CENTER
        t.typeface = Typeface.create("sans-serif-medium", 0)
        card.addView(t, LinearLayout.LayoutParams(-1, -2))
        val list = LinearLayout(a)
        list.orientation = LinearLayout.VERTICAL
        val lp = LinearLayout.LayoutParams(-1, -2)
        lp.topMargin = dp(a, Spacing.M)
        card.addView(list, lp)
        val d = dialog(a, card)
        for (i in items.indices) {
            val row = TextView(a)
            row.setTextSize(FontSize.BODY)
            row.setTextColor(ink())
            row.setPadding(dp(a, Spacing.S), dp(a, Spacing.XL), dp(a, Spacing.S), dp(a, Spacing.XL))
            val upd = Runnable { row.text = (if (checked[i]) "✓  " else "    ") + items[i] }
            upd.run()
            row.setOnClickListener {
                checked[i] = !checked[i]
                upd.run()
            }
            list.addView(row, LinearLayout.LayoutParams(-1, -2))
        }
        val row = LinearLayout(a)
        row.orientation = LinearLayout.HORIZONTAL
        val rp = LinearLayout.LayoutParams(-1, -2)
        rp.topMargin = dp(a, 14f)
        card.addView(row, rp)
        val close = button(a, "取消", false)
        row.addView(close, LinearLayout.LayoutParams(0, -2, 1f))
        close.setOnClickListener { d.dismiss() }
        val ok = button(a, "确定", true)
        val op = LinearLayout.LayoutParams(0, -2, 1f)
        op.leftMargin = dp(a, Spacing.L)
        row.addView(ok, op)
        ok.setOnClickListener {
            d.dismiss()
            onOk?.accept(checked)
        }
        if (neutral != null) {
            val n = button(a, neutral, true)
            val np = LinearLayout.LayoutParams(-1, -2)
            np.topMargin = dp(a, Spacing.L)
            card.addView(n, np)
            n.setOnClickListener {
                d.dismiss()
                onNeutral?.run()
            }
        }
        d.show()
    }

    @JvmStatic
    fun input(a: Activity, title: String, hint: String, okText: String, onOk: TextConsumer?) {
        input(a, title, hint, null, okText, onOk)
    }

    @JvmStatic
    fun input(
        a: Activity, title: String, hint: String, initial: String?, okText: String, onOk: TextConsumer?
    ) {
        val w = (a.resources.displayMetrics.widthPixels * 0.86f).toInt()
        val card = cardView(a, w)
        card.layoutParams = ViewGroup.LayoutParams(w, ViewGroup.LayoutParams.WRAP_CONTENT)
        val t = TextView(a)
        t.text = title
        t.setTextSize(FontSize.TITLE_LG)
        t.setTextColor(ink())
        t.gravity = Gravity.CENTER
        t.typeface = Typeface.create("sans-serif-medium", 0)
        card.addView(t, LinearLayout.LayoutParams(-1, -2))
        val et = EditText(a)
        et.hint = hint
        et.setText(initial ?: "")
        et.isSingleLine = true
        et.setTextSize(FontSize.BODY)
        et.setTextColor(ink())
        et.setHintTextColor(gray())
        val field = GradientDrawable()
        field.setColor(if (MainActivity.dark) Colors.NEUTRAL_800 else Colors.NEUTRAL_100)
        field.cornerRadius = dp(a, Radius.FIELD).toFloat()
        et.background = field
        et.setPadding(dp(a, Spacing.XL), dp(a, Spacing.L), dp(a, Spacing.XL), dp(a, Spacing.L))
        val ep = LinearLayout.LayoutParams(-1, -2)
        ep.topMargin = dp(a, 14f)
        card.addView(et, ep)
        val row = LinearLayout(a)
        row.orientation = LinearLayout.HORIZONTAL
        val rp = LinearLayout.LayoutParams(-1, -2)
        rp.topMargin = dp(a, Spacing.XXL)
        card.addView(row, rp)
        val c = button(a, "关闭", false)
        val o = button(a, okText, true)
        row.addView(c, LinearLayout.LayoutParams(0, -2, 1f))
        val bp2 = LinearLayout.LayoutParams(0, -2, 1f)
        bp2.leftMargin = dp(a, Spacing.L)
        row.addView(o, bp2)
        val d = dialog(a, card)
        c.setOnClickListener { d.dismiss() }
        o.setOnClickListener {
            val v2 = et.text.toString().trim()
            if (v2.isNotEmpty()) {
                d.dismiss()
                onOk?.on(v2)
            }
        }
        d.show()
    }

    /** 下载进度卡片（自绘圆角进度条，跟随明暗主题）。 */
    class ProgressHandle(
        private val dialog: Dialog,
        private val label: TextView,
        private val bar: BarView
    ) {
        fun update(text: String, percent: Int) {
            label.text = text
            bar.frac = (percent.coerceIn(0, 100)) / 100f
            bar.invalidate()
        }

        fun dismiss() {
            dialog.dismiss()
        }
    }

    @JvmStatic
    fun downloadProgress(a: Activity, title: String, message: String): ProgressHandle {
        val w = (a.resources.displayMetrics.widthPixels * 0.78f).toInt()
        val card = cardView(a, w)
        val t = TextView(a)
        t.text = title
        t.setTextSize(FontSize.BODY_LG)
        t.setTextColor(ink())
        t.gravity = Gravity.CENTER
        t.typeface = Typeface.create("sans-serif-medium", 0)
        card.addView(t, LinearLayout.LayoutParams(-1, -2))

        val m = TextView(a)
        m.text = message
        m.setTextSize(FontSize.META)
        m.setTextColor(gray())
        m.gravity = Gravity.CENTER
        val mp = LinearLayout.LayoutParams(-1, -2)
        mp.topMargin = dp(a, Spacing.M)
        card.addView(m, mp)

        val bar = BarView(a)
        bar.bgColor = if (MainActivity.dark) Colors.NEUTRAL_700 else Colors.NEUTRAL_250
        bar.fgColor = Colors.LIME_DEEP
        bar.frac = 0f
        val bp = LinearLayout.LayoutParams(-1, dp(a, 6f))
        bp.topMargin = dp(a, Spacing.XL)
        bp.bottomMargin = dp(a, 1f)
        card.addView(bar, bp)

        val d = dialog(a, card)
        d.setCancelable(false)
        d.show()
        return ProgressHandle(d, m, bar)
    }

    // ---------------- 底部弹层（对齐原 App） ----------------
    private fun sheet(a: Activity, heightRatio: Float): Dialog {
        val d = Dialog(a)
        d.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val w = d.window
        if (w != null) {
            w.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            w.setGravity(Gravity.BOTTOM)
            w.setLayout(
                ViewGroup.LayoutParams.MATCH_PARENT,
                (a.resources.displayMetrics.heightPixels * heightRatio).toInt()
            )
            w.addFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            w.setDimAmount(0.35f)
        }
        return d
    }

    private fun showSheet(d: Dialog, a: Activity, sheet: LinearLayout, heightRatio: Float) {
        val wrap = FrameLayout(a)
        wrap.addView(
            sheet,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.BOTTOM
            )
        )
        d.setContentView(wrap)
        d.show()
        val w = d.window
        if (w != null) w.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            (a.resources.displayMetrics.heightPixels * heightRatio).toInt()
        )
    }

    private fun sheetBody(a: Activity): LinearLayout {
        val sheet = LinearLayout(a)
        sheet.orientation = LinearLayout.VERTICAL
        val bg = GradientDrawable()
        bg.setColor(cardColor())
        val r = dp(a, 20f).toFloat()
        bg.cornerRadii = floatArrayOf(r, r, r, r, 0f, 0f, 0f, 0f)
        sheet.background = bg
        return sheet
    }

    private fun sheetHeader(a: Activity, title: String, onClose: Runnable, onNew: Runnable?): View {
        val head = LinearLayout(a)
        head.orientation = LinearLayout.HORIZONTAL
        head.gravity = Gravity.CENTER_VERTICAL
        head.setPadding(dp(a, 18f), dp(a, 14f), dp(a, 18f), dp(a, 8f))
        val x = TextView(a)
        x.text = "✕"
        x.setTextSize(FontSize.TITLE)
        x.setTextColor(gray())
        x.gravity = Gravity.CENTER
        x.setOnClickListener { onClose.run() }
        head.addView(x, LinearLayout.LayoutParams(dp(a, 32f), dp(a, 32f)))
        val t = TextView(a)
        t.text = title
        t.setTextSize(FontSize.TITLE_LG)
        t.setTextColor(ink())
        t.gravity = Gravity.CENTER
        t.typeface = Typeface.create("sans-serif-medium", 0)
        head.addView(t, LinearLayout.LayoutParams(0, -2, 1f))
        val n = TextView(a)
        n.text = if (onNew != null) "新建" else ""
        n.setTextSize(FontSize.BODY_SM)
        n.setTextColor(ink2())
        n.gravity = Gravity.CENTER
        if (onNew != null) n.setOnClickListener { onNew.run() }
        head.addView(n, LinearLayout.LayoutParams(dp(a, 44f), dp(a, 32f)))
        return head
    }

    @JvmStatic
    fun sheetList(
        a: Activity, title: String, emptyHint: String, items: Array<String>,
        onPick: IntConsumer?, onNew: Runnable?
    ) {
        val d = sheet(a, 0.62f)
        val sheet = sheetBody(a)
        val close = Runnable { d.dismiss() }
        sheet.addView(
            sheetHeader(
                a, title, close,
                if (onNew != null) Runnable {
                    d.dismiss()
                    onNew.run()
                } else null
            )
        )
        if (items.isEmpty()) {
            val empty = LinearLayout(a)
            empty.orientation = LinearLayout.VERTICAL
            empty.gravity = Gravity.CENTER
            empty.setPadding(0, dp(a, 70f), 0, dp(a, 40f))
            try {
                a.assets.open("img/empty.png").use { input ->
                    val iv = ImageView(a)
                    iv.setImageBitmap(BitmapFactory.decodeStream(input))
                    empty.addView(iv, LinearLayout.LayoutParams(dp(a, 70f), dp(a, 70f)))
                }
            } catch (_: Exception) {
            }
            val t1 = TextView(a)
            t1.text = "空空如也"
            t1.setTextSize(FontSize.BODY_SM)
            t1.setTextColor(ink())
            t1.gravity = Gravity.CENTER
            t1.setPadding(0, dp(a, 12f), 0, 0)
            empty.addView(t1)
            val t2 = TextView(a)
            t2.text = emptyHint
            t2.setTextSize(FontSize.SMALL)
            t2.setTextColor(gray())
            t2.gravity = Gravity.CENTER
            t2.setPadding(0, dp(a, 4f), 0, 0)
            empty.addView(t2)
            sheet.addView(empty, LinearLayout.LayoutParams(-1, 0, 1f))
        } else {
            val sc = ScrollView(a)
            val list = LinearLayout(a)
            list.orientation = LinearLayout.VERTICAL
            list.setPadding(dp(a, 18f), 0, dp(a, 18f), dp(a, 16f))
            for (i in items.indices) {
                val row = TextView(a)
                row.text = items[i]
                row.setTextSize(FontSize.BODY)
                row.setTextColor(ink())
                row.setPadding(dp(a, 2f), dp(a, 14f), dp(a, 2f), dp(a, 14f))
                if (onPick != null) row.setOnClickListener {
                    d.dismiss()
                    onPick.accept(i)
                }
                list.addView(row, LinearLayout.LayoutParams(-1, -2))
            }
            sc.addView(list)
            sheet.addView(sc, LinearLayout.LayoutParams(-1, 0, 1f))
        }
        showSheet(d, a, sheet, 0.62f)
    }

    @JvmStatic
    fun iconSheet(a: Activity, current: String?, assets: Array<String>, onPick: Pick) {
        val d = sheet(a, 0.58f)
        val sheet = sheetBody(a)
        sheet.addView(sheetHeader(a, "选择图标", Runnable { d.dismiss() }, null))
        val sc = ScrollView(a)
        val grid = GridLayout(a)
        val dm = a.resources.displayMetrics
        val gap = dp(a, 6f)
        val cols = Math.max(5, Math.floor((dm.widthPixels - dp(a, 28f)) / dp(a, 72f).toDouble()).toInt())
        grid.columnCount = cols
        grid.setPadding(dp(a, 14f), dp(a, 10f), dp(a, 14f), dp(a, 24f))
        for (path in assets) {
            val cell = FrameLayout(a)
            cell.setPadding(dp(a, Spacing.S), dp(a, Spacing.S), dp(a, Spacing.S), dp(a, Spacing.S))
            try {
                a.assets.open(path).use { input ->
                    val iv = ImageView(a)
                    iv.setImageBitmap(BitmapFactory.decodeStream(input))
                    iv.scaleType = ImageView.ScaleType.FIT_CENTER
                    cell.addView(iv, FrameLayout.LayoutParams(dp(a, 52f), dp(a, 52f), Gravity.CENTER))
                }
            } catch (_: Exception) {
            }
            val on = path == current
            val bg = GradientDrawable()
            bg.setColor(if (on) (if (MainActivity.dark) Colors.LIME_SURFACE_DARK else Colors.LIME_SOFT) else Color.TRANSPARENT)
            bg.cornerRadius = dp(a, Radius.CELL).toFloat()
            cell.background = bg
            cell.setOnClickListener {
                d.dismiss()
                onPick.on(path)
            }
            val gp = GridLayout.LayoutParams()
            gp.width = dp(a, 64f)
            gp.height = dp(a, 64f)
            grid.addView(cell, gp)
        }
        sc.addView(grid)
        sheet.addView(sc, LinearLayout.LayoutParams(-1, 0, 1f))
        showSheet(d, a, sheet, 0.58f)
    }

    /** 单选底部弹层（设置项选择，如货币单位/小数位/语言）。 */
    @JvmStatic
    fun sheetSelect(a: Activity, title: String, options: Array<String>, selected: Int, onPick: IntConsumer?) {
        val d = sheet(a, 0.5f)
        val sheet = sheetBody(a)
        sheet.addView(sheetHeader(a, title, Runnable { d.dismiss() }, null))
        val sc = ScrollView(a)
        val list = LinearLayout(a)
        list.orientation = LinearLayout.VERTICAL
        list.setPadding(dp(a, 18f), dp(a, 2f), dp(a, 18f), dp(a, 18f))
        for (i in options.indices) {
            val row = LinearLayout(a)
            row.orientation = LinearLayout.HORIZONTAL
            row.gravity = Gravity.CENTER_VERTICAL
            row.setPadding(dp(a, 4f), dp(a, 14f), dp(a, 4f), dp(a, 14f))
            val label = TextView(a)
            label.text = options[i]
            label.setTextSize(FontSize.BODY)
            label.setTextColor(ink())
            row.addView(label, LinearLayout.LayoutParams(0, -2, 1f))
            val check = TextView(a)
            check.text = if (i == selected) "✓" else ""
            check.setTextSize(FontSize.BODY_LG)
            check.setTextColor(Colors.LIME_DEEP)
            row.addView(check)
            row.setOnClickListener {
                d.dismiss()
                onPick?.accept(i)
            }
            list.addView(row, LinearLayout.LayoutParams(-1, -2))
        }
        sc.addView(list)
        sheet.addView(sc, LinearLayout.LayoutParams(-1, 0, 1f))
        showSheet(d, a, sheet, 0.5f)
    }

    /** 管理类弹层：行右侧显示计数与箭头；空态点击新建。 */
    @JvmStatic
    fun sheetListCounts(
        a: Activity, title: String, emptyHint: String, items: Array<String>, counts: IntArray?,
        onPick: IntConsumer?, onNew: Runnable?
    ) {
        val d = sheet(a, 0.62f)
        val sheet = sheetBody(a)
        sheet.addView(
            sheetHeader(
                a, title, Runnable { d.dismiss() },
                if (onNew == null) null else Runnable {
                    d.dismiss()
                    onNew.run()
                }
            )
        )
        if (items.isEmpty()) {
            val empty = LinearLayout(a)
            empty.orientation = LinearLayout.VERTICAL
            empty.gravity = Gravity.CENTER
            empty.setPadding(0, dp(a, 70f), 0, dp(a, 40f))
            try {
                a.assets.open("img/empty.png").use { input ->
                    val iv = ImageView(a)
                    iv.setImageBitmap(BitmapFactory.decodeStream(input))
                    empty.addView(iv, LinearLayout.LayoutParams(dp(a, 70f), dp(a, 70f)))
                }
            } catch (_: Exception) {
            }
            val t1 = TextView(a)
            t1.text = "空空如也"
            t1.setTextSize(FontSize.BODY_SM)
            t1.setTextColor(ink())
            t1.gravity = Gravity.CENTER
            t1.setPadding(0, dp(a, 12f), 0, 0)
            empty.addView(t1)
            val t2 = TextView(a)
            t2.text = emptyHint
            t2.setTextSize(FontSize.SMALL)
            t2.setTextColor(gray())
            t2.gravity = Gravity.CENTER
            t2.setPadding(0, dp(a, 4f), 0, 0)
            empty.addView(t2)
            if (onNew != null) {
                empty.setOnClickListener {
                    d.dismiss()
                    onNew.run()
                }
                t2.setOnClickListener {
                    d.dismiss()
                    onNew.run()
                }
            }
            sheet.addView(empty, LinearLayout.LayoutParams(-1, 0, 1f))
        } else {
            val sc = ScrollView(a)
            val list = LinearLayout(a)
            list.orientation = LinearLayout.VERTICAL
            list.setPadding(dp(a, 18f), 0, dp(a, 18f), dp(a, 16f))
            for (i in items.indices) {
                val row = LinearLayout(a)
                row.orientation = LinearLayout.HORIZONTAL
                row.gravity = Gravity.CENTER_VERTICAL
                row.setPadding(dp(a, 2f), dp(a, 14f), dp(a, 2f), dp(a, 14f))
                val name = TextView(a)
                name.text = items[i]
                name.setTextSize(FontSize.BODY)
                name.setTextColor(ink())
                row.addView(name, LinearLayout.LayoutParams(0, -2, 1f))
                val cnt = TextView(a)
                cnt.text = ((if (counts != null && i < counts.size) counts[i] else 0)).toString() + " 项"
                cnt.setTextSize(FontSize.META)
                cnt.setTextColor(gray())
                row.addView(cnt)
                val chev = TextView(a)
                chev.text = "›"
                chev.setTextSize(FontSize.TITLE)
                chev.setTextColor(gray())
                val cp = LinearLayout.LayoutParams(-2, -2)
                cp.leftMargin = dp(a, Spacing.M)
                row.addView(chev, cp)
                if (onPick != null) row.setOnClickListener {
                    d.dismiss()
                    onPick.accept(i)
                }
                list.addView(row, LinearLayout.LayoutParams(-1, -2))
            }
            sc.addView(list)
            sheet.addView(sc, LinearLayout.LayoutParams(-1, 0, 1f))
        }
        showSheet(d, a, sheet, 0.62f)
    }

    /** 管理弹层：名称 + 编辑 / 删除 / 拖动手柄（无计数）。 */
    @JvmStatic
    fun manageList(
        a: Activity, title: String, emptyHint: String, names: Array<String>,
        onNew: Runnable?, onEdit: IntConsumer?, onDelete: IntConsumer?, onOrder: OrderCallback?
    ) {
        val d = sheet(a, 0.62f)
        val sheet = sheetBody(a)
        sheet.addView(
            sheetHeader(
                a, title, Runnable { d.dismiss() },
                if (onNew == null) null else Runnable {
                    d.dismiss()
                    onNew.run()
                }
            )
        )
        if (names.isEmpty()) {
            val empty = LinearLayout(a)
            empty.orientation = LinearLayout.VERTICAL
            empty.gravity = Gravity.CENTER
            empty.setPadding(0, dp(a, 70f), 0, dp(a, 40f))
            try {
                a.assets.open("img/empty.png").use { input ->
                    val iv = ImageView(a)
                    iv.setImageBitmap(BitmapFactory.decodeStream(input))
                    empty.addView(iv, LinearLayout.LayoutParams(dp(a, 70f), dp(a, 70f)))
                }
            } catch (_: Exception) {
            }
            val t1 = TextView(a)
            t1.text = "空空如也"
            t1.setTextSize(FontSize.BODY_SM)
            t1.setTextColor(ink())
            t1.gravity = Gravity.CENTER
            t1.setPadding(0, dp(a, 12f), 0, 0)
            empty.addView(t1)
            val t2 = TextView(a)
            t2.text = emptyHint
            t2.setTextSize(FontSize.SMALL)
            t2.setTextColor(gray())
            t2.gravity = Gravity.CENTER
            t2.setPadding(0, dp(a, 4f), 0, 0)
            empty.addView(t2)
            if (onNew != null) {
                empty.setOnClickListener {
                    d.dismiss()
                    onNew.run()
                }
                t2.setOnClickListener {
                    d.dismiss()
                    onNew.run()
                }
            }
            sheet.addView(empty, LinearLayout.LayoutParams(-1, 0, 1f))
        } else {
            val sc = ScrollView(a)
            val container = LinearLayout(a)
            container.orientation = LinearLayout.VERTICAL
            container.setPadding(dp(a, 18f), 0, dp(a, 18f), dp(a, 16f))
            val downY = floatArrayOf(0f)
            val startIdx = intArrayOf(0)
            val finalTarget = intArrayOf(0)
            val currentNames = names.copyOf()
            for (i in names.indices) {
                val idx = i
                val row = LinearLayout(a)
                row.orientation = LinearLayout.HORIZONTAL
                row.gravity = Gravity.CENTER_VERTICAL
                row.setPadding(dp(a, 12f), dp(a, 14f), dp(a, 12f), dp(a, 14f))
                val rowBg = GradientDrawable()
                rowBg.setColor(if (MainActivity.dark) Colors.NEUTRAL_850 else Colors.NEUTRAL_80)
                rowBg.cornerRadius = dp(a, Radius.CELL).toFloat()
                row.background = rowBg
                row.tag = names[i]
                val name = TextView(a)
                name.text = names[i]
                name.setTextSize(FontSize.BODY)
                name.setTextColor(ink())
                row.addView(name, LinearLayout.LayoutParams(0, -2, 1f))
                val edit = icon(a, "ic_edit", 18)
                val del = icon(a, "ic_delete", 18)
                val move = icon(a, "ic_move", 18)
                val ip = LinearLayout.LayoutParams(dp(a, 18f), dp(a, 18f))
                ip.leftMargin = dp(a, 14f)
                row.addView(edit, ip)
                val dp2 = LinearLayout.LayoutParams(dp(a, 18f), dp(a, 18f))
                dp2.leftMargin = dp(a, 14f)
                row.addView(del, dp2)
                val mp = LinearLayout.LayoutParams(dp(a, 18f), dp(a, 18f))
                mp.leftMargin = dp(a, 14f)
                row.addView(move, mp)
                if (onEdit != null) edit.setOnClickListener {
                    d.dismiss()
                    onEdit.accept(idx)
                }
                if (onDelete != null) del.setOnClickListener {
                    d.dismiss()
                    onDelete.accept(idx)
                }
                if (onOrder != null) {
                    move.setOnTouchListener { v, e ->
                        when (e.actionMasked) {
                            MotionEvent.ACTION_DOWN -> {
                                downY[0] = e.rawY
                                startIdx[0] = container.indexOfChild(row)
                                finalTarget[0] = startIdx[0]
                                val sel = GradientDrawable()
                                sel.setColor(if (MainActivity.dark) Colors.LIME_SURFACE_DARK_ALT else Colors.LIME_SURFACE_LIGHT)
                                sel.cornerRadius = dp(a, Radius.CELL).toFloat()
                                row.background = sel
                                row.elevation = dp(a, 6f).toFloat()
                                var parent: android.view.ViewParent? = v.parent
                                while (parent != null && parent !is ScrollView) parent = parent.parent
                                parent?.requestDisallowInterceptTouchEvent(true)
                                true
                            }
                            MotionEvent.ACTION_MOVE -> {
                                val first = container.getChildAt(0)
                                val rowH = (if (first != null && first.height > 0) first.height else dp(a, 47f)) + dp(a, 8f)
                                val delta = e.rawY - downY[0]
                                row.translationY = delta
                                val steps = Math.round(delta / rowH.toFloat())
                                val target = Math.max(0, Math.min(container.childCount - 1, startIdx[0] + steps))
                                finalTarget[0] = target
                                for (k in 0 until container.childCount) {
                                    if (k == startIdx[0]) continue
                                    var ty = 0f
                                    if (startIdx[0] < target && k > startIdx[0] && k <= target) ty = -rowH.toFloat()
                                    else if (target < startIdx[0] && k >= target && k < startIdx[0]) ty = rowH.toFloat()
                                    container.getChildAt(k).animate().translationY(ty).setDuration(120).start()
                                }
                                true
                            }
                            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                                row.translationY = 0f
                                row.elevation = 0f
                                val nb = GradientDrawable()
                                nb.setColor(if (MainActivity.dark) Colors.NEUTRAL_850 else Colors.NEUTRAL_80)
                                nb.cornerRadius = dp(a, Radius.CELL).toFloat()
                                row.background = nb
                                for (k in 0 until container.childCount) container.getChildAt(k).translationY = 0f
                                if (finalTarget[0] != startIdx[0]) {
                                    val moved = currentNames[startIdx[0]]
                                    val list = ArrayList(currentNames.toList())
                                    list.removeAt(startIdx[0])
                                    list.add(finalTarget[0], moved)
                                    val order = list.toTypedArray()
                                    val byName = HashMap<String, View>()
                                    for (k in 0 until container.childCount) {
                                        byName[container.getChildAt(k).tag.toString()] = container.getChildAt(k)
                                    }
                                    container.removeAllViews()
                                    for (n in order) {
                                        val vw = byName[n]
                                        if (vw != null) {
                                            val lp2 = LinearLayout.LayoutParams(-1, -2)
                                            lp2.bottomMargin = dp(a, Spacing.M)
                                            container.addView(vw, lp2)
                                        }
                                    }
                                    System.arraycopy(order, 0, currentNames, 0, order.size)
                                    onOrder.onOrder(order)
                                }
                                true
                            }
                            else -> false
                        }
                    }
                }
                val rowLp = LinearLayout.LayoutParams(-1, -2)
                rowLp.bottomMargin = dp(a, Spacing.M)
                container.addView(row, rowLp)
            }
            sc.addView(container)
            sheet.addView(sc, LinearLayout.LayoutParams(-1, 0, 1f))
        }
        showSheet(d, a, sheet, 0.62f)
    }

    private fun icon(a: Activity, name: String, sizeDp: Int): ImageView {
        val iv = ImageView(a)
        try {
            a.assets.open("icons/$name.png").use { input ->
                iv.setImageBitmap(BitmapFactory.decodeStream(input))
            }
        } catch (_: Exception) {
        }
        iv.setColorFilter(gray())
        iv.layoutParams = LinearLayout.LayoutParams(dp(a, sizeDp.toFloat()), dp(a, sizeDp.toFloat()))
        return iv
    }

    /** 分类图标选择：默认 + 3D 图标分类标签页。 */
    @JvmStatic
    fun iconPicker(a: Activity, current: String?, labels: Array<String>, folders: Array<String>, onPick: Pick) {
        val d = sheet(a, 0.72f)
        val sheet = sheetBody(a)
        sheet.addView(sheetHeader(a, "选择图标", Runnable { d.dismiss() }, null))
        val hs = HorizontalScrollView(a)
        hs.isHorizontalScrollBarEnabled = false
        val tabs = LinearLayout(a)
        tabs.orientation = LinearLayout.HORIZONTAL
        tabs.setPadding(dp(a, 14f), dp(a, 2f), dp(a, 14f), dp(a, 8f))
        hs.addView(tabs)
        sheet.addView(hs, LinearLayout.LayoutParams(-1, -2))
        val divider = View(a)
        divider.setBackgroundColor(if (MainActivity.dark) Colors.NEUTRAL_800 else Colors.NEUTRAL_200)
        sheet.addView(divider, LinearLayout.LayoutParams(-1, Math.max(1, dp(a, 0.7f))))
        val sc = ScrollView(a)
        val grid = GridLayout(a)
        val dm = a.resources.displayMetrics
        val gap = dp(a, 6f)
        val cols = Math.max(5, Math.floor((dm.widthPixels - dp(a, 28f)) / dp(a, 72f).toDouble()).toInt())
        grid.columnCount = cols
        grid.setPadding(dp(a, 14f), dp(a, 10f), dp(a, 14f), dp(a, 24f))
        sc.addView(grid)
        sheet.addView(sc, LinearLayout.LayoutParams(-1, 0, 1f))
        val selected = intArrayOf(0)
        val render = Runnable {
            grid.removeAllViews()
            val folder = folders[selected[0]]
            val paths = ArrayList<String>()
            if (folder.isEmpty()) {
                paths.add("emoji/icon_default_object.png")
                paths.add("emoji/icon_default_vip.png")
            } else {
                try {
                    for (f in a.assets.list(folder) ?: emptyArray()) {
                        if (f.lowercase().endsWith(".png")) paths.add("$folder/$f")
                    }
                } catch (_: Exception) {
                }
            }
            paths.sort()
            val availW = dm.widthPixels - dp(a, 28f) - gap * (cols - 1)
            val cellW = Math.max(dp(a, 56f), availW / cols)
            val iconSize = Math.max(dp(a, 36f), cellW - dp(a, 14f))
            for (path in paths) {
                val cell = FrameLayout(a)
                cell.setPadding(dp(a, 5f), dp(a, 5f), dp(a, 5f), dp(a, 5f))
                try {
                    a.assets.open(path).use { input ->
                        val iv = ImageView(a)
                        iv.setImageBitmap(BitmapFactory.decodeStream(input))
                        iv.scaleType = ImageView.ScaleType.FIT_CENTER
                        cell.addView(iv, FrameLayout.LayoutParams(iconSize, iconSize, Gravity.CENTER))
                    }
                } catch (_: Exception) {
                }
                val on = path == current
                val bg = GradientDrawable()
                bg.setColor(if (on) (if (MainActivity.dark) Colors.LIME_SURFACE_DARK else Colors.LIME_SURFACE_LIGHT) else Color.TRANSPARENT)
                bg.cornerRadius = dp(a, Radius.CELL).toFloat()
                if (on) bg.setStroke(dp(a, 2f), Colors.LIME_DEEP)
                cell.background = bg
                cell.setOnClickListener {
                    d.dismiss()
                    onPick.on(path)
                }
                val gp = GridLayout.LayoutParams()
                gp.width = cellW
                gp.height = cellW
                gp.rightMargin = (gap * 0.75f).toInt()
                gp.bottomMargin = gap
                grid.addView(cell, gp)
            }
        }
        for (i in labels.indices) {
            val tab = TextView(a)
            tab.text = labels[i]
            tab.setTextSize(FontSize.META)
            val on = i == selected[0]
            tab.setTextColor(if (on) (if (MainActivity.dark) Colors.BLACK else Colors.WHITE) else ink2())
            val tb = GradientDrawable()
            tb.cornerRadius = dp(a, Radius.CHIP).toFloat()
            tb.setColor(if (on) Colors.LIME_DEEP else (if (MainActivity.dark) Colors.NEUTRAL_830 else Colors.NEUTRAL_150))
            tab.background = tb
            tab.setPadding(dp(a, Spacing.XL), dp(a, Spacing.S), dp(a, Spacing.XL), dp(a, Spacing.S))
            val tp = LinearLayout.LayoutParams(-2, -2)
            tp.rightMargin = dp(a, Spacing.M)
            tab.setOnClickListener {
                selected[0] = i
                for (k in 0 until tabs.childCount) {
                    val tv = tabs.getChildAt(k) as TextView
                    val sel = k == i
                    tv.setTextColor(if (sel) (if (MainActivity.dark) Colors.BLACK else Colors.WHITE) else ink2())
                    val tbg = GradientDrawable()
                    tbg.cornerRadius = dp(a, Radius.CHIP).toFloat()
                    tbg.setColor(if (sel) Colors.LIME_DEEP else (if (MainActivity.dark) Colors.NEUTRAL_830 else Colors.NEUTRAL_150))
                    tv.background = tbg
                }
                render.run()
            }
            tabs.addView(tab, tp)
        }
        render.run()
        showSheet(d, a, sheet, 0.8f)
    }

    /** 品牌选择：数码/汽车/自行车 标签 + Logo 网格。 */
    @JvmStatic
    fun brandPicker(a: Activity, currentId: String?, brands: JSONObject?, onPick: BrandPick) {
        if (brands == null) return
        val d = sheet(a, 0.78f)
        val sheet = sheetBody(a)
        sheet.addView(sheetHeader(a, "选择品牌", Runnable { d.dismiss() }, null))
        val hs = HorizontalScrollView(a)
        hs.isHorizontalScrollBarEnabled = false
        val tabs = LinearLayout(a)
        tabs.orientation = LinearLayout.HORIZONTAL
        tabs.setPadding(dp(a, 14f), dp(a, 2f), dp(a, 14f), dp(a, 8f))
        hs.addView(tabs)
        sheet.addView(hs, LinearLayout.LayoutParams(-1, -2))
        val divider = View(a)
        divider.setBackgroundColor(if (MainActivity.dark) Colors.NEUTRAL_800 else Colors.NEUTRAL_200)
        sheet.addView(divider, LinearLayout.LayoutParams(-1, Math.max(1, dp(a, 0.7f))))
        val sc = ScrollView(a)
        val grid = GridLayout(a)
        val dm = a.resources.displayMetrics
        val gap = dp(a, 6f)
        val cols = Math.max(4, Math.floor((dm.widthPixels - dp(a, 28f)) / dp(a, 116f).toDouble()).toInt())
        grid.columnCount = cols
        grid.setPadding(dp(a, 14f), dp(a, 10f), dp(a, 14f), dp(a, 24f))
        sc.addView(grid)
        sheet.addView(sc, LinearLayout.LayoutParams(-1, 0, 1f))
        val keys = arrayOf("digital", "car", "bike")
        val labels = arrayOf("数码", "汽车", "自行车")
        val sel = intArrayOf(0)
        val render = Runnable {
            grid.removeAllViews()
            try {
                val arr = brands.optJSONArray(keys[sel[0]])
                val availW = dm.widthPixels - dp(a, 28f) - gap * (cols - 1)
                val cellW = Math.max(dp(a, 86f), availW / cols)
                val cellBg = if (MainActivity.dark) Colors.NEUTRAL_850 else Colors.NEUTRAL_80
                val withClear = currentId != null && currentId.isNotEmpty()
                if (withClear) {
                    val cell = FrameLayout(a)
                    val bg = GradientDrawable()
                    bg.setColor(cellBg)
                    bg.cornerRadius = dp(a, Radius.CELL).toFloat()
                    cell.background = bg
                    val col = LinearLayout(a)
                    col.orientation = LinearLayout.VERTICAL
                    col.gravity = Gravity.CENTER
                    val none = TextView(a)
                    none.text = "无"
                    none.setTextSize(FontSize.NUMBER)
                    none.setTextColor(gray())
                    col.addView(none)
                    val nm = TextView(a)
                    nm.text = "无品牌"
                    nm.setTextSize(FontSize.CAPTION)
                    nm.setTextColor(gray())
                    nm.setPadding(0, dp(a, 6f), 0, 0)
                    col.addView(nm)
                    cell.addView(col, FrameLayout.LayoutParams(-1, -1))
                    cell.setOnClickListener {
                        d.dismiss()
                        onPick.on("", "")
                    }
                    val gp = GridLayout.LayoutParams()
                    gp.width = cellW
                    gp.height = dp(a, 112f)
                    gp.rightMargin = (gap * 0.75f).toInt()
                    gp.bottomMargin = gap
                    grid.addView(cell, gp)
                }
                for (i in 0 until (arr?.length() ?: 0)) {
                    val item = arr!!.optJSONArray(i) ?: continue
                    val name = item.optString(0)
                    val id = item.optString(1)
                    val cell = FrameLayout(a)
                    val on = id == currentId
                    val bg = GradientDrawable()
                    bg.setColor(if (on) (if (MainActivity.dark) Colors.LIME_SURFACE_DARK else Colors.LIME_SURFACE_LIGHT) else cellBg)
                    bg.cornerRadius = dp(a, Radius.CELL).toFloat()
                    if (on) bg.setStroke(dp(a, 2f), Colors.LIME_DEEP)
                    cell.background = bg
                    val col = LinearLayout(a)
                    col.orientation = LinearLayout.VERTICAL
                    col.gravity = Gravity.CENTER
                    try {
                        a.assets.open("brands/$id.png").use { input ->
                            val iv = ImageView(a)
                            iv.setImageBitmap(BitmapFactory.decodeStream(input))
                            iv.scaleType = ImageView.ScaleType.FIT_CENTER
                            if (MainActivity.dark) {
                                val plate = FrameLayout(a)
                                val pb = GradientDrawable()
                                pb.setColor(Colors.WHITE)
                                pb.cornerRadius = dp(a, Radius.FIELD).toFloat()
                                plate.background = pb
                                plate.addView(iv, FrameLayout.LayoutParams(dp(a, 54f), dp(a, 54f), Gravity.CENTER))
                                col.addView(plate, LinearLayout.LayoutParams(dp(a, 64f), dp(a, 64f)))
                            } else {
                                col.addView(iv, LinearLayout.LayoutParams(dp(a, 62f), dp(a, 62f)))
                            }
                        }
                    } catch (_: Exception) {
                    }
                    val nm = TextView(a)
                    nm.text = name
                    nm.setTextSize(FontSize.CAPTION)
                    nm.setTextColor(ink2())
                    nm.setPadding(0, dp(a, 6f), 0, 0)
                    col.addView(nm)
                    cell.addView(col, FrameLayout.LayoutParams(-1, -1))
                    cell.setOnClickListener {
                        d.dismiss()
                        onPick.on(name, id)
                    }
                    val gp = GridLayout.LayoutParams()
                    gp.width = cellW
                    gp.height = dp(a, 112f)
                    gp.rightMargin = (gap * 0.75f).toInt()
                    gp.bottomMargin = gap
                    grid.addView(cell, gp)
                }
            } catch (_: Exception) {
            }
        }
        for (i in labels.indices) {
            val tab = TextView(a)
            tab.text = labels[i]
            tab.setTextSize(FontSize.META)
            val on = i == 0
            tab.setTextColor(if (on) (if (MainActivity.dark) Colors.BLACK else Colors.WHITE) else ink2())
            val tb = GradientDrawable()
            tb.cornerRadius = dp(a, Radius.CHIP).toFloat()
            tb.setColor(if (on) Colors.LIME_DEEP else (if (MainActivity.dark) Colors.NEUTRAL_830 else Colors.NEUTRAL_150))
            tab.background = tb
            tab.setPadding(dp(a, Spacing.XL), dp(a, Spacing.S), dp(a, Spacing.XL), dp(a, Spacing.S))
            val tp = LinearLayout.LayoutParams(-2, -2)
            tp.rightMargin = dp(a, Spacing.M)
            tab.setOnClickListener {
                sel[0] = i
                for (k in 0 until tabs.childCount) {
                    val tv = tabs.getChildAt(k) as TextView
                    val s = k == i
                    tv.setTextColor(if (s) (if (MainActivity.dark) Colors.BLACK else Colors.WHITE) else ink2())
                    val tbg = GradientDrawable()
                    tbg.cornerRadius = dp(a, Radius.CHIP).toFloat()
                    tbg.setColor(if (s) Colors.LIME_DEEP else (if (MainActivity.dark) Colors.NEUTRAL_830 else Colors.NEUTRAL_150))
                    tv.background = tbg
                }
                render.run()
            }
            tabs.addView(tab, tp)
        }
        render.run()
        showSheet(d, a, sheet, 0.78f)
    }

    /** 型号选择：品牌型号按产品线分组 + 自定义输入。 */
    @JvmStatic
    fun modelPicker(a: Activity, brandName: String, current: String, groups: JSONArray, onPick: TextConsumer) {
        val d = sheet(a, 0.6f)
        val sheet = sheetBody(a)
        sheet.addView(sheetHeader(a, "$brandName 型号", Runnable { d.dismiss() }, null))
        val sc = ScrollView(a)
        val list = LinearLayout(a)
        list.orientation = LinearLayout.VERTICAL
        list.setPadding(dp(a, 18f), dp(a, 2f), dp(a, 18f), dp(a, 18f))
        for (g in 0 until groups.length()) {
            val entry = groups.optJSONArray(g)
            if (entry != null && entry.length() >= 2) {
                val groupName = entry.optString(0)
                val ms = entry.optJSONArray(1) ?: continue
                if (groupName.isNotEmpty()) {
                    val h = TextView(a)
                    h.text = groupName
                    h.setTextSize(FontSize.META)
                    h.setTextColor(gray())
                    h.setPadding(dp(a, Spacing.XS), dp(a, Spacing.XXL), dp(a, Spacing.XS), dp(a, Spacing.S))
                    list.addView(h, LinearLayout.LayoutParams(-1, -2))
                }
                for (k in 0 until ms.length()) addModelRow(a, d, list, ms.optString(k), current, onPick)
            } else {
                val m = groups.optString(g)
                if (m.isNotEmpty()) addModelRow(a, d, list, m, current, onPick)
            }
        }
        val custom = LinearLayout(a)
        custom.gravity = Gravity.CENTER_VERTICAL
        custom.setPadding(dp(a, 4f), dp(a, 16f), dp(a, 4f), dp(a, 14f))
        val ct = TextView(a)
        ct.text = "自定义型号…"
        ct.setTextSize(FontSize.BODY)
        ct.setTextColor(ink2())
        custom.addView(ct, LinearLayout.LayoutParams(-1, -2))
        custom.setOnClickListener {
            d.dismiss()
            input(a, "自定义型号", "请输入具体型号", current, "保存", onPick)
        }
        list.addView(custom, LinearLayout.LayoutParams(-1, -2))
        sc.addView(list)
        sheet.addView(sc, LinearLayout.LayoutParams(-1, 0, 1f))
        showSheet(d, a, sheet, 0.6f)
    }

    private fun addModelRow(
        a: Activity, d: Dialog, list: LinearLayout, m: String, current: String, onPick: TextConsumer
    ) {
        val row = LinearLayout(a)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        row.setPadding(dp(a, 4f), dp(a, 13f), dp(a, 4f), dp(a, 13f))
        val t = TextView(a)
        t.text = m
        t.setTextSize(FontSize.BODY)
        t.setTextColor(ink())
        row.addView(t, LinearLayout.LayoutParams(0, -2, 1f))
        val check = TextView(a)
        check.text = if (m == current) "✓" else ""
        check.setTextSize(FontSize.BODY_LG)
        check.setTextColor(Colors.LIME_DEEP)
        row.addView(check)
        row.setOnClickListener {
            d.dismiss()
            onPick.on(m)
        }
        list.addView(row, LinearLayout.LayoutParams(-1, -2))
    }
}
