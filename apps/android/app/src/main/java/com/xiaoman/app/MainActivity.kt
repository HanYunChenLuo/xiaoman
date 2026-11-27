package com.xiaoman.app

import android.app.Activity
import android.app.DatePickerDialog
import android.app.Dialog
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.ArrayList
import java.util.Date
import java.util.Calendar
import java.util.HashMap
import java.util.LinkedHashMap
import java.util.Locale
import java.util.function.IntConsumer

class MainActivity : Activity() {
    private lateinit var store: Store
    private lateinit var ui: Ui
    private lateinit var cloud: CloudSync
    private lateinit var root: FrameLayout
    private lateinit var content: FrameLayout
    private lateinit var navHost: FrameLayout
    private var screen = "home"
    private var tab = "home"
    private var filter = "all"
    private var range = "all"
    private var categoryFilter = "all"
    private var draft: JSONObject = JSONObject()
    private var editing = false
    private var editOriginal: String? = null
    private var detailId: String? = null
    private var lockView: View? = null
    private var entered = ""
    private var backgrounded = false
    private var pendingImages: MutableMap<String, String> = HashMap()
    private var brandData: JSONObject? = null
    private lateinit var brandPrefs: android.content.SharedPreferences
    private var brandDataVersion = 0
    private var brandDataVersionName = "内置"
    private val brandAliases = ArrayList<Array<String>>()
    private var brandAuto = false
    private var brandRowLogo: ImageView? = null
    private var brandRowText: TextView? = null
    private var iconPreview: ImageView? = null
    private var modelInput: EditText? = null
    private var saveButtonRef: View? = null
    private var saveCheckRef: ImageView? = null
    private val brandModels: MutableMap<String, JSONArray> = HashMap()
    private var obPage = 0
    private var grid = true
    private var returnTab = "home"
    private var favoriteOnly = false
    private var sortKey = "default"
    private var sortAsc = false
    private var searchQuery = ""

    private fun interface Click {
        fun on()
    }

    private fun interface IntClick {
        fun on(i: Int)
    }

    private fun interface TextClick {
        fun on(s: String)
    }

    private fun dp(v: Float): Int = ui.dp(v)

    private fun lp(w: Int, h: Int) = LinearLayout.LayoutParams(w, h)

    private fun lpw(weight: Float, h: Int) = LinearLayout.LayoutParams(0, h, weight)

    private fun lpm(w: Int, h: Int, l: Int, t: Int, r: Int, bt: Int): LinearLayout.LayoutParams {
        val p = LinearLayout.LayoutParams(w, h)
        p.setMargins(dp(l.toFloat()), dp(t.toFloat()), dp(r.toFloat()), dp(bt.toFloat()))
        return p
    }

    private fun scrollPage(body: LinearLayout): ScrollView {
        val sv = ScrollView(this)
        sv.isFillViewport = true
        sv.isVerticalScrollBarEnabled = false
        sv.addView(body, FrameLayout.LayoutParams(-1, -2))
        return sv
    }

    private fun pageBody(): LinearLayout {
        val body = ui.vbox()
        body.setPadding(0, 0, 0, dp(110f))
        return body
    }

    private fun setScreen(v: View, nav: Boolean, activeTab: String?) {
        root.setBackgroundColor(ui.bg())
        content.setBackgroundColor(ui.bg())
        applySystemBars()
        content.removeAllViews()
        content.addView(v, FrameLayout.LayoutParams(-1, -1))
        navHost.removeAllViews()
        if (nav) {
            navHost.addView(buildNav(activeTab ?: "home"))
            tab = activeTab ?: "home"
        }
    }

    private fun toast(s: String) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show()
    }

    // ================= 底部导航 =================
    private fun buildNav(active: String): View {
        val host = FrameLayout(this)
        val bar = ui.hbox()
        bar.background = ui.roundStroke(ui.card(), Radius.NAV , ui.line(), 1f)
        bar.elevation = 0f
        bar.setPadding(dp(6f), 0, dp(6f), 0)
        val group = ui.hbox()
        group.gravity = Gravity.CENTER_VERTICAL
        group.addView(bar, LinearLayout.LayoutParams(-2, dp(Size.NAV_BAR_H.toFloat())))
        val gp = FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL)
        gp.bottomMargin = dp(Size.NAV_BOTTOM_MARGIN.toFloat())
        host.addView(group, gp)

        val tabs = arrayOf(
            arrayOf("home", "小满", "tab_home"),
            arrayOf("wishes", "心愿", "tab_wish"),
            arrayOf("trends", "趋势", "tab_trend"),
            arrayOf("settings", "设置", "tab_settings")
        )
        for (t in tabs) {
            val on = t[0] == active
            val item = ui.vbox()
            item.gravity = Gravity.CENTER
            val tabIcon = ui.icon(t[2] + if (on) "_s" else "_n", 22)
            if (dark) tabIcon.setColorFilter(if (on) Colors.NEUTRAL_120 else Colors.NEUTRAL_460)
            val iconWrap = FrameLayout(this)
            if (on) iconWrap.background = ui.circle(if (dark) Colors.NEUTRAL_780 else Colors.NEUTRAL_160)
            iconWrap.addView(tabIcon, FrameLayout.LayoutParams(dp(22f), dp(22f), Gravity.CENTER))
            item.addView(iconWrap, LinearLayout.LayoutParams(dp(40f), dp(40f)))
            val label = ui.text(t[1], FontSize.CAPTION , if (on) ui.ink() else ui.gray(), if (on) ui.medium else ui.regular)
            label.gravity = Gravity.CENTER
            label.setPadding(0, dp(4f), 0, 0)
            item.addView(label)
            item.setOnClickListener { switchTab(t[0]) }
            bar.addView(item, LinearLayout.LayoutParams(dp(Size.NAV_ITEM_W.toFloat()), -1))
        }

        val fab = ui.text("+", FontSize.LARGE , if (dark) Colors.BLACK else Colors.WHITE, ui.regular)
        fab.gravity = Gravity.CENTER
        fab.background = ui.circle(if (dark) Colors.NEUTRAL_120 else Colors.BLACK)
        ui.clipCircle(fab)
        fab.elevation = 0f
        fab.setOnClickListener { openAdd(null, if ("wishes" == tab) "wish" else "asset") }
        val fp = LinearLayout.LayoutParams(dp(Size.FAB.toFloat()), dp(Size.FAB.toFloat()))
        fp.leftMargin = dp(Spacing.XXL)
        group.addView(fab, fp)
        return host
    }

    private fun switchTab(t: String) {
        when (t) {
            "wishes" -> showWishes()
            "trends" -> showTrends()
            "settings" -> showSettings()
            else -> showHome()
        }
    }

    // ================= 引导页 =================
    private fun showOnboarding() {
        screen = "onboard"
        val body = ui.vbox()
        body.background = ui.gradient(Colors.HERO_START, Colors.HERO_MID, Colors.HERO_END)
        body.setPadding(dp(28f), dp(56f), dp(28f), dp(40f))

        val art = ui.image(OB[obPage][2], 200)
        val ap = LinearLayout.LayoutParams(dp(200f), dp(200f))
        ap.gravity = Gravity.CENTER_HORIZONTAL
        ap.topMargin = dp(30f)
        art.layoutParams = ap
        body.addView(art)

        val title = ui.text(OB[obPage][0], FontSize.HERO, Colors.BLACK, ui.bold)
        val tp = LinearLayout.LayoutParams(-2, -2)
        tp.topMargin = dp(70f)
        body.addView(title, tp)
        val sub = ui.text(OB[obPage][1], FontSize.BODY , Colors.NEUTRAL_580)
        val sp = LinearLayout.LayoutParams(-2, -2)
        sp.topMargin = dp(Spacing.XL)
        body.addView(sub, sp)

        body.addView(View(this), LinearLayout.LayoutParams(1, 0, 1f))

        val foot = ui.hbox()
        val dots = ui.hbox()
        for (i in OB.indices) {
            val dot = View(this)
            val dp2 = LinearLayout.LayoutParams(dp(if (i == obPage) 22f else 18f), dp(4f))
            dp2.rightMargin = dp(Spacing.M)
            dot.layoutParams = dp2
            dot.background = ui.round(if (i == obPage) Colors.NEUTRAL_950 else Colors.ONBOARD_DOT, Radius.INDICATOR)
            dots.addView(dot)
        }
        foot.addView(dots, lpw(1f, -2))

        val next = FrameLayout(this)
        next.background = ui.round(Ui.LIME, Radius.CARD )
        val arrow = ImageView(this)
        arrow.setImageResource(R.drawable.ic_next)
        next.addView(arrow, FrameLayout.LayoutParams(dp(24f), dp(24f), Gravity.CENTER))
        next.setOnClickListener {
            if (obPage < OB.size - 1) {
                obPage++
                showOnboarding()
            } else {
                store.setBool("onboarded", true)
                showHome()
            }
        }
        foot.addView(next, LinearLayout.LayoutParams(dp(54f), dp(54f)))
        body.addView(foot)
        setScreen(body, false, null)
    }

    // ================= 首页 / 心愿 =================
    private fun showHome() {
        screen = "home"
        tab = "home"
        val body = pageBody()
        body.addView(header())
        body.addView(overviewCard())

        val catScroll = HorizontalScrollView(this)
        catScroll.isHorizontalScrollBarEnabled = false
        val catRow = ui.hbox()
        catRow.setPadding(0, 0, 0, 0)
        val catsList = store.objs("categories")
        val allCat = ui.text(
            "全部", FontSize.BODY_SM ,
            if (categoryFilter == "all") ui.ink() else ui.gray(),
            if (categoryFilter == "all") ui.medium else ui.regular
        )
        allCat.gravity = Gravity.CENTER
        allCat.setPadding(dp(14f), 0, dp(14f), 0)
        allCat.setOnClickListener {
            categoryFilter = "all"
            showHome()
        }
        catRow.addView(allCat, LinearLayout.LayoutParams(-2, dp(44f)))
        for (c in catsList) {
            val cid = c.optString("id")
            val on = categoryFilter == cid
            val ct = ui.text(
                c.optString("name"), FontSize.BODY_SM ,
                if (on) ui.ink() else ui.gray(),
                if (on) ui.medium else ui.regular
            )
            ct.gravity = Gravity.CENTER
            ct.setPadding(dp(14f), 0, dp(14f), 0)
            ct.setOnClickListener {
                categoryFilter = cid
                showHome()
            }
            catRow.addView(ct, LinearLayout.LayoutParams(-2, dp(44f)))
        }
        val uncategorized = categoryFilter == "__none__"
        val nc = ui.text(
            "未分类", FontSize.BODY_SM ,
            if (uncategorized) ui.ink() else ui.gray(),
            if (uncategorized) ui.medium else ui.regular
        )
        nc.gravity = Gravity.CENTER
        nc.setPadding(dp(14f), 0, dp(14f), 0)
        nc.setOnClickListener {
            categoryFilter = "__none__"
            showHome()
        }
        catRow.addView(nc, LinearLayout.LayoutParams(-2, dp(44f)))
        catScroll.addView(catRow)
        body.addView(catScroll)

        val filters = ui.hbox()
        filters.setPadding(dp(8f), 0, dp(16f), dp(4f))

        val hs = HorizontalScrollView(this)
        hs.isHorizontalScrollBarEnabled = false
        val chips = ui.hbox()
        val defs = arrayOf(
            arrayOf("all", "全部"), arrayOf("serving", "服役中"),
            arrayOf("retired", "已退役"), arrayOf("sold", "已卖出")
        )
        for (d in defs) {
            val on = filter == d[0]
            val chip = ui.text(d[1], FontSize.SMALL , if (on) ui.chipOnInk() else ui.gray(), if (on) ui.medium else ui.regular)
            chip.gravity = Gravity.CENTER
            chip.setPadding(dp(14f), 0, dp(14f), 0)
            if (on) chip.background = ui.round(ui.chipOnBg(), Radius.CARD_LG )
            chip.setOnClickListener {
                filter = d[0]
                showHome()
            }
            chips.addView(chip, LinearLayout.LayoutParams(-2, dp(40f)))
        }
        chips.setPadding(dp(2f), 0, 0, 0)
        hs.addView(chips)
        filters.addView(hs, lpw(1f, -2))

        val sort = ui.icon("ic_sort", 16)
        sort.alpha = 0.65f
        if (dark) sort.setColorFilter(Colors.NEUTRAL_120)
        val ip = LinearLayout.LayoutParams(dp(16f), dp(16f))
        ip.leftMargin = dp(Spacing.XL)
        filters.addView(sort, ip)
        sort.setOnClickListener { chooseSortMode() }
        val funnel = ui.icon("ic_funnel", 16)
        funnel.alpha = 0.65f
        if (dark) funnel.setColorFilter(Colors.NEUTRAL_120)
        val fp = LinearLayout.LayoutParams(dp(16f), dp(16f))
        fp.leftMargin = dp(Spacing.XL)
        filters.addView(funnel, fp)
        funnel.setOnClickListener { chooseCategoryFilter() }
        val viewBtn = ui.pillButton(if (grid) "网格" else "列表", ui.chipBg(), ui.ink2(), Radius.CHIP)
        viewBtn.setTextSize(FontSize.SMALL)
        viewBtn.setPadding(dp(Spacing.XL), dp(Spacing.S), dp(Spacing.XL), dp(Spacing.S))
        val vp = LinearLayout.LayoutParams(-2, dp(32f))
        vp.leftMargin = dp(Spacing.XL)
        filters.addView(viewBtn, vp)
        viewBtn.setOnClickListener {
            grid = !grid
            store.setSetting("viewGrid", grid)
            showHome()
        }
        body.addView(filters)

        if (categoryFilter != "all" && categoryFilter != "__none__") {
            var exists = false
            for (c in catsList) if (c.optString("id") == categoryFilter) exists = true
            if (!exists) categoryFilter = "all"
        }
        val list = store.ofKind("asset")
        val shown = ArrayList<JSONObject>()
        for (a in list) {
            if (favoriteOnly && !a.optBoolean("favorite", false)) continue
            if (filter != "all" && filter != a.optString("status", "serving")) continue
            if (categoryFilter == "__none__") {
                if (a.optString("categoryId").isNotEmpty()) continue
            } else if (categoryFilter != "all" && categoryFilter != a.optString("categoryId")) continue
            shown.add(a)
        }
        val comparator: Comparator<JSONObject> = when (sortKey) {
            "price" -> compareBy { it.optDouble("price", 0.0) }
            "daily" -> compareBy { store.dailyCost(it) }
            else -> compareBy { store.ageDays(it) }
        }
        shown.sortWith(comparator)
        if (!sortAsc) shown.reverse()
        if (shown.isEmpty()) body.addView(emptyState(), LinearLayout.LayoutParams(-1, -2))
        else if (!grid) body.addView(assetClassicList(shown))
        else body.addView(assetGrid(shown))
        setScreen(scrollPage(body), true, "home")
    }


    private fun chooseSortMode() {
        val keys = arrayOf("price", "daily", "days")
        val labels = arrayOf("价格", "日均成本", "使用天数")
        val selected = keys.indexOf(sortKey).coerceAtLeast(0)
        DialogKit.sheetSelect(this, "排序参考", labels, selected, IntConsumer { i ->
            sortKey = keys[i]
            store.setSetting("sortKey", sortKey)
            chooseSortDirection()
        })
    }

    private fun chooseSortDirection() {
        DialogKit.sheetSelect(
            this, "排序方式",
            arrayOf("从高到低", "从低到高"),
            if (sortAsc) 1 else 0,
            IntConsumer { i ->
                sortAsc = i == 1
                store.setSetting("sortAsc", sortAsc)
                showHome()
            }
        )
    }

    private fun chooseCategoryFilter() {
        val cats = store.objs("categories")
        val names = ArrayList<String>()
        names.add("全部")
        for (c in cats) names.add(c.optString("name"))
        names.add("未分类")
        var selected = 0
        if (categoryFilter == "__none__") {
            selected = names.size - 1
        } else if (categoryFilter != "all") {
            for (i in cats.indices) if (cats[i].optString("id") == categoryFilter) selected = i + 1
        }
        DialogKit.sheetSelect(this, "筛选分类", names.toTypedArray(), selected, IntConsumer { i ->
            categoryFilter = when {
                i == 0 -> "all"
                i == names.size - 1 -> "__none__"
                else -> cats[i - 1].optString("id")
            }
            showHome()
        })
    }

    private fun showSearchPage(query: String) {
        screen = "search"
        val body = ui.vbox()
        body.setPadding(0, 0, 0, dp(28f))

        val head = ui.hbox()
        head.gravity = Gravity.CENTER_VERTICAL
        head.setPadding(dp(Spacing.XXXL), dp(Spacing.XL), dp(Spacing.XXXL), dp(Spacing.M))
        val back = ui.pillButton("‹", ui.card(), ui.ink(), Radius.PILL)
        back.setTextSize(FontSize.HEADLINE)
        back.setPadding(dp(12f), dp(2f), dp(14f), dp(4f))
        ui.clipRound(back, Radius.PILL)
        back.elevation = dp(2f).toFloat()
        back.setOnClickListener { showHome() }
        head.addView(back)

        val input = EditText(this)
        input.hint = "搜索名称、型号或品牌"
        input.isSingleLine = true
        input.setTextSize(FontSize.BODY)
        input.setTextColor(ui.ink())
        input.setHintTextColor(ui.gray())
        input.background = ui.round(if (dark) Colors.NEUTRAL_800 else Colors.NEUTRAL_120, Radius.CARD_LG)
        input.setPadding(dp(14f), dp(10f), dp(14f), dp(10f))
        val ip = LinearLayout.LayoutParams(0, -2, 1f)
        ip.leftMargin = dp(Spacing.M)
        head.addView(input, ip)
        body.addView(head)

        val results = ui.vbox()
        body.addView(results)

        val render = { q: String ->
            results.removeAllViews()
            val key = q.trim().lowercase(Locale.getDefault())
            val hits = store.objs("assets").filter { a ->
                key.isEmpty() ||
                    a.optString("name").lowercase(Locale.getDefault()).contains(key) ||
                    a.optString("model").lowercase(Locale.getDefault()).contains(key) ||
                    a.optString("brandName").lowercase(Locale.getDefault()).contains(key)
            }
            if (hits.isEmpty()) {
                val hint = ui.text(
                    if (key.isEmpty()) "输入关键词开始搜索" else "没有找到匹配的资产",
                    FontSize.BODY_SM, ui.gray()
                )
                hint.gravity = Gravity.CENTER
                hint.setPadding(0, dp(48f), 0, 0)
                results.addView(hint, LinearLayout.LayoutParams(-1, -2))
            } else if (!grid) {
                results.addView(assetClassicList(hits))
            } else {
                results.addView(assetGrid(hits))
            }
        }
        input.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                searchQuery = s?.toString() ?: ""
                render(searchQuery)
            }
        })
        searchQuery = query
        input.setText(query)
        render(query)
        setScreen(scrollPage(body), false, null)
        screen = "search"
        input.requestFocus()
        input.post {
            val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT)
        }
    }

    private fun assetClassicList(list: List<JSONObject>): View {
        val wrap = ui.vbox()
        wrap.setPadding(dp(Spacing.XXL), dp(Spacing.M), dp(Spacing.XXL), 0)
        val group = ui.vbox()
        group.background = ui.round(ui.card(), Radius.CARD)
        ui.clipRound(group, Radius.CARD)
        group.elevation = dp(2f).toFloat()
        list.forEachIndexed { index, a ->
            val row = ui.hbox()
            row.gravity = Gravity.CENTER_VERTICAL
            row.setPadding(dp(Spacing.XL), dp(Spacing.XL), dp(Spacing.XL), dp(Spacing.XL))
            val cv = a.optString("coverPath")
            val icon = if (cv.isNotEmpty() && java.io.File(cv).exists()) ui.fileImage(cv, 44)
            else ui.image(a.optString("icon", "emoji/icon_default_object.png"), 44)
            row.addView(icon)

            val mid = ui.vbox()
            mid.setPadding(dp(Spacing.XL), 0, 0, 0)
            val nameRow = ui.hbox()
            nameRow.gravity = Gravity.CENTER_VERTICAL
            val dot = View(this)
            dot.background = ui.circle(badgeInk(a.optString("status", "serving")))
            nameRow.addView(dot, LinearLayout.LayoutParams(dp(6f), dp(6f)))
            val nm = ui.text(displayName(a), FontSize.BODY, ui.ink(), ui.medium)
            val np = LinearLayout.LayoutParams(-2, -2)
            np.leftMargin = dp(Spacing.M)
            nameRow.addView(nm, np)
            if (a.optBoolean("favorite", false)) {
                val star = ui.text("★", FontSize.SMALL, Ui.LIME_DEEP)
                val sp = LinearLayout.LayoutParams(-2, -2)
                sp.leftMargin = dp(Spacing.XS)
                nameRow.addView(star, sp)
            }
            mid.addView(nameRow)
            val brand = a.optString("brandName")
            val sub = (if (brand.isEmpty()) "" else brand + " ｜ ") +
                store.money(a.optDouble("price")) + " ｜ " + store.ageDays(a) + " 天"
            val st = ui.text(sub, FontSize.SMALL, ui.gray())
            st.setPadding(0, dp(4f), 0, 0)
            mid.addView(st)
            row.addView(mid, lpw(1f, -2))

            val costBox = ui.vbox()
            costBox.gravity = Gravity.END
            val c = ui.text(store.money(store.dailyCost(a)), FontSize.BODY_SM, ui.ink(), ui.bold)
            c.gravity = Gravity.END
            costBox.addView(c)
            val per = ui.text("/天", FontSize.CAPTION, ui.gray())
            per.gravity = Gravity.END
            costBox.addView(per)
            row.addView(costBox)

            row.setOnClickListener { showDetail(a.optString("id")) }
            group.addView(row)
            if (index < list.size - 1) group.addView(ui.hline())
        }
        wrap.addView(group)
        return wrap
    }

    private fun header(): View {
        val head = FrameLayout(this)
        head.layoutParams = LinearLayout.LayoutParams(-1, dp(150f))
        val bg = ImageView(this)
        bg.setImageBitmap(ui.bitmap("img/head_bg.png"))
        bg.scaleType = ImageView.ScaleType.FIT_XY
        head.addView(bg, FrameLayout.LayoutParams(-1, -1))
        val row = ui.hbox()
        row.setPadding(dp(14f), dp(8f), dp(14f), 0)
        val logo = ImageView(this)
        logo.setImageBitmap(ui.bitmap(if (dark) "img/logo_dark.png" else "img/logo_light.png"))
        logo.scaleType = ImageView.ScaleType.FIT_CENTER
        row.addView(logo, LinearLayout.LayoutParams(dp(44f), dp(44f)))
        row.addView(View(this), lpw(1f, 1))
        val search = ui.icon("ic_search", 22)
        row.addView(search)
        search.setOnClickListener { showSearchPage("") }
        val fav = BookmarkView(this, if (favoriteOnly) Ui.LIME_DEEP else ui.ink())
        fav.setOnClickListener {
            favoriteOnly = !favoriteOnly
            showHome()
        }
        val f = LinearLayout.LayoutParams(dp(20f), dp(22f))
        f.leftMargin = dp(Spacing.XXL)
        row.addView(fav, f)
        head.addView(row, FrameLayout.LayoutParams(-1, -2))
        return head
    }

    private fun overviewCard(): View {
        val card = ui.vbox()
        card.background = ui.round(ui.card(), Radius.CARD_LG )
        ui.clipRound(card, Radius.CARD_LG )
        card.elevation = dp(3f).toFloat()
        card.setPadding(dp(20f), dp(22f), dp(20f), dp(16f))

        val top = ui.hbox()
        val title = ui.text("资产总览", FontSize.TITLE , ui.ink(), ui.bold)
        top.addView(title)
        val count = ui.text(store.count("asset", "serving").toString() + "/" + store.ofKind("asset").size, FontSize.SMALL , ui.gray())
        val cp = LinearLayout.LayoutParams(-2, -2)
        cp.leftMargin = dp(Spacing.M)
        top.addView(count, cp)
        card.addView(top)

        val nums = ui.hbox()
        val np = LinearLayout.LayoutParams(-1, -2)
        np.topMargin = dp(Spacing.XXL)
        card.addView(nums, np)

        val left = ui.vbox()
        left.addView(ui.text("总资产", FontSize.SMALL , ui.gray()))
        left.addView(moneyLine(store.total("asset"), 22f))
        nums.addView(left, lpw(1f, -2))

        val right = ui.vbox()
        right.gravity = Gravity.END
        val lbl = ui.text("日均成本", FontSize.SMALL , ui.gray())
        lbl.gravity = Gravity.END
        right.addView(lbl)
        right.addView(moneyLine(store.daily("asset"), 22f))
        nums.addView(right, lpw(1f, -2))

        val stats = ui.hbox()
        val stp = LinearLayout.LayoutParams(-1, -2)
        stp.topMargin = dp(22f)
        card.addView(stats, stp)
        val defs = arrayOf(arrayOf("服役中", "serving"), arrayOf("已退役", "retired"), arrayOf("已卖出", "sold"))
        val total = Math.max(1, store.ofKind("asset").size)
        for (i in defs.indices) {
            val col = ui.vbox()
            val cap = ui.hbox()
            cap.addView(ui.text(defs[i][0], FontSize.SMALL , ui.ink2()), lpw(1f, -2))
            cap.addView(
                ui.text(
                    store.count("asset", defs[i][1]).toString(), FontSize.SMALL , ui.ink(), ui.medium
                )
            )
            col.addView(cap)
            val bar = ui.bar(store.count("asset", defs[i][1]) / total.toFloat(), Ui.LIME_DEEP)
            val bp = LinearLayout.LayoutParams(-1, dp(6f))
            bp.topMargin = dp(Spacing.S)
            col.addView(bar, bp)
            stats.addView(col, lpw(1f, -2))
            if (i < defs.size - 1) stats.addView(View(this), LinearLayout.LayoutParams(dp(14f), 1))
        }

        val cardLp = LinearLayout.LayoutParams(-1, -2)
        cardLp.setMargins(dp(12f), -dp(84f), dp(12f), 0)
        val holder = FrameLayout(this)
        holder.addView(card, FrameLayout.LayoutParams(-1, -2))
        holder.layoutParams = cardLp
        return holder
    }

    private fun moneyLine(v: Double, size: Float): View {
        val row = ui.hbox()
        row.gravity = Gravity.BOTTOM
        val s = ui.text(store.sym(), FontSize.META , ui.ink(), ui.medium)
        val m = ui.text(store.fmt(v, store.settingInt("decimals", 2)), size, ui.ink(), ui.bold)
        val sp = LinearLayout.LayoutParams(-2, -2)
        sp.rightMargin = dp(2f)
        row.addView(s, sp)
        row.addView(m)
        return row
    }

    private fun circleIconButton(iconName: String, onClick: Runnable): FrameLayout {
        val btn = FrameLayout(this)
        btn.background = ui.circle(if (dark) Colors.NEUTRAL_800 else ui.card())
        ui.clipCircle(btn)
        btn.elevation = dp(2f).toFloat()
        val iv = themedIcon(iconName, 16)
        btn.addView(iv, FrameLayout.LayoutParams(dp(16f), dp(16f), Gravity.CENTER))
        btn.setOnClickListener { onClick.run() }
        return btn
    }

    private fun themedIcon(name: String, sizeDp: Int): ImageView {
        val iv = ui.icon(name, sizeDp)
        if (dark) iv.setColorFilter(ui.ink2())
        return iv
    }

    private fun moneyLineSuffix(v: Double, size: Float, suffix: String): View {
        val row = ui.hbox()
        row.gravity = Gravity.CENTER
        val s = ui.text(store.sym(), FontSize.META , ui.ink(), ui.medium)
        val m = ui.text(store.fmt(v, store.settingInt("decimals", 2)), size, ui.ink(), ui.bold)
        val sfx = ui.text(suffix, FontSize.META , ui.ink2())
        val sp = LinearLayout.LayoutParams(-2, -2)
        sp.rightMargin = dp(2f)
        row.addView(s, sp)
        row.addView(m)
        row.addView(sfx)
        return row
    }

    private fun emptyState(): View {
        val box = ui.vbox()
        box.gravity = Gravity.CENTER
        box.setPadding(0, dp(90f), 0, dp(40f))
        val img = ui.image("img/empty.png", 96)
        img.alpha = 0.9f
        box.addView(img)
        val t = ui.text("空空如也", FontSize.BODY , ui.ink2(), ui.medium)
        t.gravity = Gravity.CENTER
        t.setPadding(0, dp(14f), 0, 0)
        box.addView(t)
        val s = ui.text("点击 + 添加资产", FontSize.SMALL , ui.gray())
        s.gravity = Gravity.CENTER
        s.setPadding(0, dp(6f), 0, 0)
        box.addView(s)
        return box
    }

    private fun assetGrid(list: List<JSONObject>): View {
        val wrap = ui.vbox()
        wrap.setPadding(dp(9f), dp(6f), dp(9f), 0)
        val cols = if (grid) 2 else 1
        var row: LinearLayout? = null
        for (i in list.indices) {
            if (i % cols == 0) {
                row = ui.hbox()
                row.gravity = Gravity.TOP
                wrap.addView(row, LinearLayout.LayoutParams(-1, -2))
            }
            val cell = ui.vbox()
            cell.setPadding(dp(5f), dp(5f), dp(5f), dp(5f))
            cell.addView(assetCard(list[i]))
            row!!.addView(cell, LinearLayout.LayoutParams(0, -2, 1f))
        }
        if (cols == 2 && list.size % 2 == 1) {
            row?.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
        }
        return wrap
    }

    private fun assetCard(a: JSONObject): View {
        val card = FrameLayout(this)
        card.background = ui.round(ui.card(), Radius.CARD )
        ui.clipRound(card, Radius.CARD )
        card.elevation = dp(2f).toFloat()
        card.setOnClickListener { showDetail(a.optString("id")) }

        val inner = ui.vbox()
        inner.setPadding(dp(Spacing.XL), dp(Spacing.XL), dp(Spacing.XL), dp(Spacing.XL))
        val icon: View
        val cvp = a.optString("coverPath")
        icon = if (cvp.isNotEmpty() && java.io.File(cvp).exists()) ui.fileImage(cvp, 44)
        else ui.image(a.optString("icon", "emoji/icon_default_object.png"), 44)
        inner.addView(icon)
        val name = ui.text(displayName(a), FontSize.BODY , ui.ink(), ui.medium)
        name.isSingleLine = true
        name.setPadding(0, dp(10f), 0, 0)
        inner.addView(name)
        val brandPrefix = if (a.optString("brandName").isEmpty()) "" else a.optString("brandName") + " ｜ "
        val sub = ui.text(
            brandPrefix + store.sym() + store.fmt(a.optDouble("price"), store.settingInt("decimals", 2)) +
                " ｜ " + store.ageDays(a) + " 天", FontSize.CAPTION , ui.gray()
        )
        sub.isSingleLine = true
        sub.setPadding(0, dp(3f), 0, 0)
        inner.addView(sub)
        val cost = ui.hbox()
        cost.gravity = Gravity.BOTTOM
        cost.setPadding(0, dp(8f), 0, 0)
        val c = ui.text(
            store.sym() + store.fmt(store.dailyCost(a), store.settingInt("decimals", 2)), FontSize.BODY_SM , ui.ink(), ui.bold
        )
        val per = ui.text("/天", FontSize.CAPTION , ui.ink2(), ui.regular)
        cost.addView(c)
        cost.addView(per)
        inner.addView(cost)
        card.addView(inner, FrameLayout.LayoutParams(-1, -2))

        val status = a.optString("status", "serving")
        val badge = ui.text("● " + Store.statusText(status), FontSize.TINY , badgeInk(status), ui.medium)
        val bp = FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.END)
        bp.setMargins(0, dp(Spacing.L), dp(Spacing.L), 0)
        card.addView(badge, bp)
        return card
    }

    private fun badgeBg(s: String): Int {
        if ("retired" == s) return if (dark) Colors.NEUTRAL_800 else Colors.NEUTRAL_160
        if ("sold" == s) return if (dark) Colors.SOLD_SURFACE_DARK else Colors.SOLD_SURFACE_LIGHT
        return if (dark) Colors.LIME_SURFACE_DARK else Colors.LIME_SOFT
    }

    private fun badgeInk(s: String): Int {
        if ("retired" == s) return if (dark) Colors.NEUTRAL_400 else Colors.NEUTRAL_450
        if ("sold" == s) return if (dark) Colors.SOLD_INK_DARK else Colors.SOLD_INK_LIGHT
        return if (dark) Colors.LIME_INK_SOFT else Colors.LIME_INK
    }

    private fun showWishes() {
        screen = "wishes"
        val body = pageBody()
        val head = ui.hbox()
        head.setPadding(dp(18f), dp(14f), dp(18f), dp(10f))
        head.addView(ui.text("心愿", FontSize.DISPLAY , ui.ink(), ui.bold))
        body.addView(head)

        val list = store.ofKind("wish")
        val card = FrameLayout(this)
        card.background = ui.round(ui.card(), Radius.CARD_LG )
        ui.clipRound(card, Radius.CARD_LG )
        card.elevation = dp(2f).toFloat()
        card.setPadding(dp(Spacing.HUGE), dp(Spacing.HUGE), dp(Spacing.HUGE), dp(Spacing.HUGE))
        val info = ui.vbox()
        info.addView(ui.text("心愿总值", FontSize.META , ui.gray()))
        info.addView(moneyLine(store.total("wish"), 30f))
        val cnt = ui.text("心愿数量 " + list.size + " 个", FontSize.SMALL , ui.gray())
        cnt.setPadding(0, dp(10f), 0, 0)
        info.addView(cnt)
        card.addView(info, FrameLayout.LayoutParams(-2, -2))
        val bottle = ui.icon("ic_bottle", 64)
        bottle.setColorFilter(Ui.LIME_DEEP)
        card.addView(bottle, FrameLayout.LayoutParams(dp(64f), dp(64f), Gravity.END))
        val cp = LinearLayout.LayoutParams(-1, -2)
        cp.setMargins(dp(14f), dp(Spacing.XS), dp(14f), 0)
        card.layoutParams = cp
        body.addView(card)

        if (list.isEmpty()) body.addView(emptyState(), LinearLayout.LayoutParams(-1, -2))
        else {
            val gp = LinearLayout.LayoutParams(-1, -2)
            gp.topMargin = dp(14f)
            body.addView(assetGrid(list), gp)
        }
        setScreen(scrollPage(body), true, "wishes")
    }

    // ================= 趋势 =================
    private fun showTrends() {
        screen = "trends"
        val body = pageBody()
        val head = ui.hbox()
        head.setPadding(dp(18f), dp(14f), dp(18f), dp(10f))
        val trendPill = ui.text("趋势", FontSize.BODY_LG , ui.ink(), ui.medium)
        trendPill.background = ui.round(ui.chipBg(), Radius.CARD_MD )
        trendPill.setPadding(dp(16f), dp(7f), dp(16f), dp(7f))
        head.addView(trendPill)
        body.addView(head)

        val hs = HorizontalScrollView(this)
        hs.isHorizontalScrollBarEnabled = false
        val chips = ui.hbox()
        chips.setPadding(dp(16f), 0, dp(16f), dp(8f))
        val defs = arrayOf(
            arrayOf("all", "全部"), arrayOf("week", "最近一周"),
            arrayOf("d90", "最近90天"), arrayOf("d180", "最近180天"), arrayOf("year", "最近一年")
        )
        for (d in defs) {
            val chip = ui.chip(d[1], range == d[0])
            chip.setOnClickListener {
                range = d[0]
                showTrends()
            }
            val cp = LinearLayout.LayoutParams(-2, -2)
            cp.rightMargin = dp(Spacing.M)
            chips.addView(chip, cp)
        }
        hs.addView(chips)
        body.addView(hs)

        val t1 = trendCard()
        t1.addView(ui.text("资产总值", FontSize.META , ui.gray()))
        t1.addView(moneyLine(store.total("asset"), FontSize.NUMBER))
        val rows = listOf(
            Triple("服役", "serving", Colors.LIME_DEEP),
            Triple("退役", "retired", Colors.TEAL),
            Triple("已卖出", "sold", Colors.ORANGE)
        )
        val total = Math.max(1, store.ofKind("asset").size)
        for ((label, key, color) in rows) {
            val block = ui.vbox()
            block.setPadding(0, dp(10f), 0, dp(10f))
            val line = ui.hbox()
            val dot = View(this)
            dot.background = ui.circle(color)
            val dp2 = LinearLayout.LayoutParams(dp(6f), dp(6f))
            dp2.rightMargin = dp(Spacing.M)
            line.addView(dot, dp2)
            line.addView(ui.text(label, FontSize.META , ui.ink2()))
            val n = store.count("asset", key)
            val pct = ui.text("  " + Math.round(n * 100f / total) + "%", FontSize.CAPTION , ui.gray())
            line.addView(pct)
            block.addView(line)
            val v = ui.text(store.money(if (key == "serving") store.daily("asset") else 0.0), FontSize.BODY_LG , ui.ink(), ui.bold)
            v.setPadding(0, dp(4f), 0, 0)
            block.addView(v)
            t1.addView(block)
            t1.addView(ui.hline())
        }
        body.addView(t1)

        val t2 = trendCard()
        t2.addView(ui.text("资产价值趋势", FontSize.BODY_SM , ui.ink(), ui.medium))
        val big = ui.text(store.money(store.total("asset")), FontSize.TITLE_LG , ui.ink(), ui.bold)
        big.setPadding(0, dp(6f), 0, 0)
        t2.addView(big)
        val chart = ChartView(this)
        chart.setData(
            trendSeries(rangeDays()),
            store.fmt(chartMax(trendSeries(rangeDays())), 0),
            store.fmt(chartMax(trendSeries(rangeDays())) / 2, 0), "0", "", ""
        )
        val chp = LinearLayout.LayoutParams(-1, dp(150f))
        chp.topMargin = dp(Spacing.M)
        t2.addView(chart, chp)
        body.addView(t2)

        val t3 = trendCard()
        t3.addView(ui.text("类型分布", FontSize.BODY_SM , ui.ink(), ui.medium))
        val assets = store.ofKind("asset")
        if (assets.isEmpty()) t3.addView(ui.text("暂无数据", FontSize.SMALL , ui.gray()))
        else {
            val colors = intArrayOf(Ui.LIME, Ui.TEAL, Ui.ORANGE, Colors.BLUE, Colors.AMBER)
            val names = ArrayList<String>()
            val counts = ArrayList<Int>()
            for (a in assets) {
                var cat = "未分类"
                val cid = a.optString("categoryId")
                for (c in store.objs("categories")) if (c.optString("id") == cid) cat = c.optString("name")
                val idx = names.indexOf(cat)
                if (idx < 0) {
                    names.add(cat)
                    counts.add(1)
                } else counts[idx] = counts[idx] + 1
            }
            for (i in names.indices) {
                val row = ui.hbox()
                val rp = LinearLayout.LayoutParams(-1, -2)
                rp.topMargin = dp(Spacing.M)
                row.addView(ui.text(names[i], FontSize.SMALL , ui.ink2()), lpw(1f, -2))
                row.addView(ui.text(counts[i].toString() + " 件", FontSize.SMALL , ui.ink2()))
                t3.addView(row, rp)
                val bar = ui.bar(counts[i] / assets.size.toFloat(), colors[i % colors.size])
                val bp = LinearLayout.LayoutParams(-1, dp(5f))
                bp.topMargin = dp(5f)
                t3.addView(bar, bp)
            }
            val totalBox = ui.vbox()
            totalBox.gravity = Gravity.CENTER
            val tlp = LinearLayout.LayoutParams(-1, -2)
            tlp.topMargin = dp(Spacing.XXL)
            val totalLabel = ui.text("总计（件）", FontSize.SMALL , ui.gray())
            totalLabel.gravity = Gravity.CENTER
            totalBox.addView(totalLabel)
            val totalValue = ui.text(assets.size.toString(), FontSize.NUMBER , ui.ink(), ui.bold)
            totalValue.gravity = Gravity.CENTER
            totalValue.setPadding(0, dp(4f), 0, 0)
            totalBox.addView(totalValue)
            t3.addView(totalBox, tlp)
        }
        body.addView(t3)

        setScreen(scrollPage(body), true, "trends")
    }

    private fun trendCard(): LinearLayout {
        val card = ui.vbox()
        card.background = ui.round(ui.card(), Radius.CARD_MD )
        ui.clipRound(card, Radius.CARD_MD )
        card.elevation = dp(2f).toFloat()
        card.setPadding(dp(Spacing.XXXL), dp(Spacing.XXXL), dp(Spacing.XXXL), dp(Spacing.XXXL))
        val cp = LinearLayout.LayoutParams(-1, -2)
        cp.setMargins(dp(14f), 0, dp(14f), dp(Spacing.XL))
        card.layoutParams = cp
        return card
    }

    private fun rangeDays(): Int = when (range) {
        "week" -> 7
        "d90" -> 90
        "d180" -> 180
        else -> 365
    }

    private fun trendSeries(days: Int): DoubleArray {
        val out = DoubleArray(days)
        val assets = store.ofKind("asset")
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_MONTH, -(days - 1))
        val f = java.text.SimpleDateFormat("yyyy-MM-dd", Locale.US)
        for (i in 0 until days) {
            val date = f.format(cal.time)
            var v = 0.0
            for (a in assets) {
                val buy = a.optString("buyDate")
                val sold = a.optString("soldDate")
                if (buy.compareTo(date) <= 0 && (sold.isEmpty() || sold.compareTo(date) >= 0)) v += a.optDouble("price")
            }
            out[i] = v
            cal.add(Calendar.DAY_OF_MONTH, 1)
        }
        return out
    }

    private fun chartMax(data: DoubleArray): Double {
        var m = 0.0
        for (v in data) m = Math.max(m, v)
        return Math.max(m, 100.0)
    }

    // ================= 设置 =================
    private fun showSettings() {
        screen = "settings"
        val body = pageBody()
        val head = ui.hbox()
        head.setPadding(dp(18f), dp(14f), dp(18f), dp(10f))
        head.addView(ui.text("设置", FontSize.DISPLAY , ui.ink(), ui.bold), lpw(1f, -2))
        body.addView(head)

        val login = ui.hbox()
        login.setPadding(dp(14f), dp(10f), dp(14f), dp(8f))
        val avatar = FrameLayout(this)
        avatar.background = ui.circle(if (dark) Colors.NEUTRAL_800 else Colors.NEUTRAL_220)
        val av = ui.icon("ic_view", 20)
        av.setColorFilter(ui.gray())
        avatar.addView(av, FrameLayout.LayoutParams(dp(20f), dp(20f), Gravity.CENTER))
        login.addView(avatar, LinearLayout.LayoutParams(dp(46f), dp(46f)))
        val texts = ui.vbox()
        texts.setPadding(dp(12f), 0, 0, 0)
        val g = cloud.googleLoggedIn()
        val primary = if (g) "Google · " + cloud.googleEmail().ifEmpty { "已登录" } else "点击登录"
        val secondary = if (g) "已登录，点击管理云备份" else "使用 Google 账号登录并同步数据"
        texts.addView(ui.text(primary, FontSize.BODY_LG , ui.ink(), ui.medium))
        texts.addView(ui.text(secondary, FontSize.SMALL , ui.gray()))
        login.addView(texts)
        login.setOnClickListener {
            if (g) showCloudPage() else googleAccountAction()
        }
        body.addView(login)

        section(body, "数值与单位")
        val g1 = group()
        g1.addView(row("ic_currency", "货币单位", store.setting("currency", "CNY"), true) { pickCurrency() })
        g1.addView(row("ic_decimal", "小数点设置", "保留 " + store.settingInt("decimals", 2) + " 位", true) { pickDecimals() })
        g1.addView(switchRow("ic_comma", "使用千位分隔符", "thousands", store.settingBool("thousands", true)))
        val durRow = row("ic_timeformat", "时长显示格式", null, false, null)
        val durSeg = segment(
            arrayOf("天数", "日期"),
            if ("date" == store.setting("duration", "days")) 1 else 0,
            11f
        ) { i ->
            store.setSetting("duration", if (i == 0) "days" else "date")
            showSettings()
        }
        durRow.addView(durSeg, LinearLayout.LayoutParams(-2, -2))
        g1.addView(durRow)
        body.addView(g1)

        section(body, "数据管理")
        val g2 = group()
        g2.addView(row("ic_category", "分类管理", store.objs("categories").size.toString() + " 个", true) { manageCategories() })
        g2.addView(row("ic_tags", "标签管理", store.objs("tags").size.toString() + " 个", true) { manageTags() })
        g2.addView(row("ic_view", "品牌与型号数据", "v$brandDataVersionName", true) { checkBrandDataUpdate(true) })
        g2.addView(row("ic_backup", "备份与恢复", null, true) { showBackupPage() })
        g2.addView(row("ic_cloud", "数据同步", cloudSyncSummary(), true) { showCloudPage() })
        body.addView(g2)

        section(body, "显示与外观")
        val g3 = group()
        val themeRow = row("ic_theme", "主题模式", null, false, null)
        val dots = ui.hbox()
        val theme = store.setting("theme", "system")
        val modes = arrayOf(
            arrayOf("system", "ic_mode_system"),
            arrayOf("light", "ic_mode_light"),
            arrayOf("dark", "ic_mode_dark")
        )
        for (m in modes) {
            val dot = FrameLayout(this)
            val on = m[0] == theme
            dot.background = ui.roundStroke(ui.card(), Radius.CHIP , if (on) ui.ink() else ui.line(), 1f)
            val img = ui.icon(m[1], 16)
            dot.addView(img, FrameLayout.LayoutParams(dp(16f), dp(16f), Gravity.CENTER))
            val dlp = LinearLayout.LayoutParams(dp(26f), dp(26f))
            dlp.leftMargin = dp(Spacing.L)
            dot.setOnClickListener {
                store.setSetting("theme", m[0])
                dark = store.isDark()
                applySystemBars()
                showSettings()
            }
            dots.addView(dot, dlp)
        }
        themeRow.addView(dots)
        g3.addView(themeRow)
        body.addView(g3)

        section(body, "通用设置")
        val g4 = group()
        g4.addView(
            row(
                "ic_security", "密码与安全",
                if (store.str("privacyPassword", "").isEmpty()) null else "已开启", true
            ) { securityDialog() }
        )
        g4.addView(
            row(
                "ic_language", "语言",
                if ("system" == store.str("language", "zh")) "跟随系统" else "简体中文", true
            ) { languageDialog() }
        )
        body.addView(g4)

        section(body, "反馈与支持")
        val g5 = group()
        g5.addView(row("ic_wechat", "客服支持", null, true) {
            doc("客服支持", "请通过项目主页提交问题：\nhttps://github.com/HanYunChenLuo/xiaoman/issues")
        })
        g5.addView(row("ic_wechat", "用户交流", null, true) {
            doc("用户交流", "欢迎在项目主页参与讨论：\nhttps://github.com/HanYunChenLuo/xiaoman")
        })
        g5.addView(row("ic_feedback", "我要提意见", null, true) {
            doc("我要提意见", "请通过项目主页提交 Issue：\nhttps://github.com/HanYunChenLuo/xiaoman/issues")
        })
        g5.addView(row("ic_contact", "联系我们", null, true) {
            doc("联系我们", "项目主页：https://github.com/HanYunChenLuo/xiaoman")
        })
        body.addView(g5)

        section(body, "关于")
        val g6 = group()
        g6.addView(row("ic_restore", "检查更新", "v${BuildConfig.VERSION_NAME}", true) { checkUpdate() })
        g6.addView(row("ic_terms", "用户协议", null, true) {
            doc(
                "用户协议",
                "欢迎使用小满。使用本应用即表示你已阅读并同意以下条款；如不同意，请停止使用。\n\n" +
                    "1. 小满是一款本地资产记录工具，按「现状」提供；作者不对数据丢失、计算偏差或使用后果作担保。\n" +
                    "2. 数据默认保存在本机，卸载应用或清除数据会删除本地记录；请自行定期导出 .ias 备份。\n" +
                    "3. 云同步为可选功能，使用你自己的 Google 账号与应用数据文件夹，同时受 Google 服务条款约束。\n" +
                    "4. 不得利用本应用从事违法活动。本应用不包含 AI 生成功能，不使用 Google API 生成、传播 AI 生成的私密影像或其他 AI 内容，并遵守 Google APIs 服务条款。\n" +
                    "5. 本项目为开源软件，许可条款以仓库 LICENSE 为准；应用名称与图标归项目所有。\n" +
                    "6. 在法律允许的最大范围内，作者不对因使用或无法使用本应用造成的间接、附带或后果性损失承担责任。\n" +
                    "7. 本协议可能随版本更新调整；完整条款：https://hanyunchenluo.github.io/xiaoman/terms.html"
            )
        })
        g6.addView(row("ic_privacy", "隐私政策", null, true) {
            doc(
                "隐私政策",
                "小满重视你的隐私：\n\n" +
                    "1. 资产、图片与设置默认仅保存在本机；不收集设备标识或使用统计，无第三方分析/广告 SDK。\n" +
                    "2. 仅在你主动登录 Google 时读取邮箱地址用于显示当前账号；仅在你主动上传或恢复时访问 Google Drive 应用数据文件夹（appDataFolder）中的备份，无法读取 Drive 中的其他文件。\n" +
                    "3. Google 用户数据仅用于上述功能，遵守 Google API 服务用户数据政策及 Limited Use 要求：不出售、不用于广告、不用于 AI 训练、不允许人工读取。\n" +
                    "4. OAuth 令牌仅保存在本机并以系统安全能力加密，退出登录即清除；云端备份保留在你自己的 Drive 中，可覆盖、撤销授权或联系我们删除。\n" +
                    "5. 品牌数据更新与检查更新会访问 GitHub 公共 API/Release，不携带个人数据。\n" +
                    "6. 完整政策：https://hanyunchenluo.github.io/xiaoman/privacy.html"
            )
        })
        body.addView(g6)

        var vn = ""
        var vc = 0L
        try {
            val pi = packageManager.getPackageInfo(packageName, 0)
            vn = pi.versionName ?: ""
            vc = if (Build.VERSION.SDK_INT >= 28) pi.longVersionCode else pi.versionCode.toLong()
        } catch (_: Exception) {
        }
        val ver = ui.text("版本 $vn($vc)\n数据默认保存在本机，可选云备份", FontSize.TINY , ui.gray())
        ver.gravity = Gravity.CENTER
        ver.setPadding(0, dp(14f), 0, dp(20f))
        body.addView(ver)

        setScreen(scrollPage(body), true, "settings")
    }

    // ---------- 应用更新 ----------
    private fun checkUpdate() {
        toast("正在检查更新…")
        Updater(this).check({ info ->
            if (info == null) {
                toast("已是最新版本 v${BuildConfig.VERSION_NAME}")
                return@check
            }
            val notes = info.notes.trim()
            val message = (if (notes.isEmpty()) "" else notes.take(600) + "\n\n") + "是否下载并安装？"
            DialogKit.confirm(
                this, "发现新版本 ${info.version}", message, "取消", "下载并安装",
                Runnable { downloadUpdate(info) }
            )
        }, { msg ->
            toast("检查更新失败：$msg")
        })
    }

    private fun downloadUpdate(info: Updater.Info) {
        val sizeText = if (info.size > 0) {
            " · " + store.fmt(info.size / 1024.0 / 1024.0, 1) + " MB"
        } else ""
        val progress = DialogKit.downloadProgress(this, "下载更新", "小满 v${info.version}$sizeText")
        Updater(this).download(info, { pct ->
            progress.update("正在下载… $pct%", pct)
        }, { apk ->
            progress.dismiss()
            Updater(this).install(apk)
        }, { msg ->
            progress.dismiss()
            toast("更新失败：$msg")
        })
    }

    private fun section(body: LinearLayout, label: String) {
        val t = ui.text(label, FontSize.SMALL , ui.gray())
        t.setPadding(dp(Spacing.HUGE), dp(Spacing.XS), dp(Spacing.HUGE), dp(Spacing.M))
        body.addView(t)
    }

    private fun group(): LinearLayout {
        val g = ui.vbox()
        g.background = ui.round(ui.card(), Radius.CARD )
        ui.clipRound(g, Radius.CARD )
        g.elevation = dp(2f).toFloat()
        val gp = LinearLayout.LayoutParams(-1, -2)
        gp.setMargins(dp(14f), 0, dp(14f), dp(14f))
        g.layoutParams = gp
        return g
    }

    private fun row(icon: String, label: String, value: String?, chevron: Boolean, click: Click?): LinearLayout {
        val r = ui.hbox()
        r.setPadding(dp(16f), 0, dp(16f), 0)
        r.minimumHeight = dp(52f)
        r.addView(themedIcon(icon, 18))
        val t = ui.text(label, FontSize.TITLE , ui.ink())
        t.setPadding(dp(10f), 0, 0, 0)
        r.addView(t, lpw(1f, -2))
        if (value != null) {
            val v = ui.text(value, FontSize.META , ui.gray())
            r.addView(v)
        }
        if (chevron) {
            val c = themedIcon("ic_chevron", 10)
            c.alpha = if (dark) 0.75f else 0.35f
            val cp = LinearLayout.LayoutParams(dp(6f), dp(10f))
            cp.leftMargin = dp(Spacing.M)
            r.addView(c, cp)
        }
        if (click != null) {
            r.setOnClickListener { click.on() }
            r.isClickable = true
        }
        return r
    }

    private fun switchRow(icon: String, label: String, key: String, checked: Boolean): LinearLayout {
        val r = ui.hbox()
        r.setPadding(dp(16f), 0, dp(16f), 0)
        r.minimumHeight = dp(52f)
        r.addView(themedIcon(icon, 18))
        val t = ui.text(label, FontSize.TITLE , ui.ink())
        t.setPadding(dp(10f), 0, 0, 0)
        r.addView(t, lpw(1f, -2))
        val sw = Switch(this)
        sw.isChecked = checked
        sw.thumbTintList = ColorStateList.valueOf(if (checked) Ui.LIME_DEEP else Colors.NEUTRAL_300)
        sw.trackTintList = ColorStateList.valueOf(if (dark) Colors.NEUTRAL_700 else Colors.NEUTRAL_250)
        sw.setOnCheckedChangeListener { _, c ->
            store.setSetting(key, c)
            sw.thumbTintList = ColorStateList.valueOf(if (c) Ui.LIME_DEEP else Colors.NEUTRAL_300)
        }
        r.addView(sw)
        return r
    }

    private fun segment(labels: Array<String>, selected: Int, size: Float, cb: IntClick): LinearLayout {
        val seg = ui.hbox()
        val bgc = if (dark) Colors.NEUTRAL_800 else Colors.NEUTRAL_120
        seg.background = ui.round(bgc, Radius.FIELD )
        seg.setPadding(dp(3f), dp(3f), dp(3f), dp(3f))
        for (i in labels.indices) {
            val on = i == selected
            val t = ui.text(
                labels[i], size,
                if (on) ui.ink() else (if (dark) Colors.NEUTRAL_360 else ui.gray()),
                if (on) ui.medium else ui.regular
            )
            t.gravity = Gravity.CENTER
            t.setPadding(dp(12f), dp(5f), dp(12f), dp(5f))
            if (on) t.background = ui.round(if (dark) Colors.NEUTRAL_650 else ui.card(), Radius.SEGMENT )
            t.setOnClickListener { cb.on(i) }
            seg.addView(t)
        }
        return seg
    }

    private fun pickCurrency() {
        val items = arrayOf("CNY", "USD", "EUR", "JPY", "GBP", "HKD")
        val cur = store.setting("currency", "CNY")
        var sel = 0
        for (i in items.indices) if (items[i] == cur) sel = i
        DialogKit.sheetSelect(this, "货币单位", items, sel, IntConsumer { i ->
            store.setSetting("currency", items[i])
            showSettings()
        })
    }

    private fun pickDecimals() {
        val items = arrayOf("0", "1", "2", "3")
        var sel = 0
        for (i in items.indices) if (i == store.settingInt("decimals", 2)) sel = i
        DialogKit.sheetSelect(this, "小数位数", items, sel, IntConsumer { i ->
            store.setSetting("decimals", items[i].toInt())
            showSettings()
        })
    }

    private fun manageCategories() {
        val cats = store.objs("categories")
        val names = Array(cats.size) { cats[it].optString("name") }
        DialogKit.manageList(
            this, "分类管理", "点击添加分类", names,
            Runnable {
                inputDialog("新建分类", "请输入分类名称") { name ->
                    try {
                        store.arr("categories").put(JSONObject().put("id", "cat_" + System.currentTimeMillis()).put("name", name))
                        store.save()
                    } catch (_: Exception) {
                    }
                    manageCategories()
                }
            },
            IntConsumer { idx ->
                val old = names[idx]
                DialogKit.input(this, "重命名分类", "请输入分类名称", old, "保存") { name ->
                    for (c in store.objs("categories"))
                        if (old == c.optString("name")) c.put("name", name)
                    store.save()
                    manageCategories()
                }
            },
            IntConsumer { idx ->
                val target = names[idx]
                DialogKit.confirm(this, "删除分类", "确定删除「$target」？", "取消", "删除", Runnable {
                    val arr = store.arr("categories")
                    for (i in arr.length() - 1 downTo 0) {
                        val c = arr.optJSONObject(i)
                        if (c != null && target == c.optString("name")) arr.remove(i)
                    }
                    store.save()
                    manageCategories()
                })
            },
            DialogKit.OrderCallback { order -> reorderArray("categories", order) }
        )
    }

    private fun manageTags() {
        val tags = store.objs("tags")
        val names = Array(tags.size) { tags[it].optString("name") }
        DialogKit.manageList(
            this, "标签管理", "点击添加标签", names,
            Runnable {
                inputDialog("新建标签", "请输入标签名称") { name ->
                    try {
                        store.arr("tags").put(JSONObject().put("id", "tag_" + System.currentTimeMillis()).put("name", name))
                        store.save()
                    } catch (_: Exception) {
                    }
                    manageTags()
                }
            },
            IntConsumer { idx ->
                val old = names[idx]
                DialogKit.input(this, "重命名标签", "请输入标签名称", old, "保存") { name ->
                    for (t in store.objs("tags"))
                        if (old == t.optString("name")) t.put("name", name)
                    store.save()
                    manageTags()
                }
            },
            IntConsumer { idx ->
                val target = names[idx]
                DialogKit.confirm(this, "删除标签", "确定删除「$target」？", "取消", "删除", Runnable {
                    val arr = store.arr("tags")
                    for (i in arr.length() - 1 downTo 0) {
                        val t = arr.optJSONObject(i)
                        if (t != null && target == t.optString("name")) arr.remove(i)
                    }
                    store.save()
                    manageTags()
                })
            },
            DialogKit.OrderCallback { order -> reorderArray("tags", order) }
        )
    }

    private fun reorderArray(key: String, order: Array<String>) {
        val old = store.arr(key)
        val out = JSONArray()
        for (name in order) {
            for (i in 0 until old.length()) {
                val o = old.optJSONObject(i)
                if (o != null && name == o.optString("name")) {
                    out.put(o)
                    break
                }
            }
        }
        store.putArray(key, out)
    }

    private fun inputDialog(title: String, hint: String, cb: TextClick) {
        DialogKit.input(this, title, hint, "创建", DialogKit.TextConsumer { cb.on(it) })
    }

    private fun showBackupPage() {
        screen = "backup"
        val body = ui.vbox()
        body.setPadding(0, 0, 0, dp(28f))

        val head = ui.hbox()
        head.setPadding(dp(Spacing.XXXL), dp(Spacing.XL), dp(Spacing.XXXL), dp(Spacing.M))
        val back = ui.pillButton("‹", ui.card(), ui.ink(), Radius.PILL )
        back.setTextSize(20f)
        back.setPadding(dp(12f), dp(2f), dp(14f), dp(4f))
        ui.clipRound(back, Radius.PILL )
        back.elevation = dp(2f).toFloat()
        back.setOnClickListener { showSettings() }
        head.addView(back)
        body.addView(head)

        val center = ui.vbox()
        center.gravity = Gravity.CENTER
        center.setPadding(0, dp(14f), 0, dp(20f))
        val bigIcon = FrameLayout(this)
        bigIcon.background = ui.round(Ui.LIME_DEEP, Radius.CARD_LG )
        val arrow = ui.text("↑", FontSize.ARROW, Colors.WHITE, ui.bold)
        arrow.gravity = Gravity.CENTER
        bigIcon.addView(arrow, FrameLayout.LayoutParams(dp(76f), dp(76f), Gravity.CENTER))
        val bip = LinearLayout.LayoutParams(dp(76f), dp(76f))
        bip.gravity = Gravity.CENTER_HORIZONTAL
        center.addView(bigIcon, bip)
        val bt = ui.text("备份与恢复", FontSize.HEADLINE , ui.ink(), ui.bold)
        bt.gravity = Gravity.CENTER
        center.addView(bt, LinearLayout.LayoutParams(-1, -2))
        val cnt = ui.text(store.ofKind("asset").size.toString() + "条记录", FontSize.SMALL , ui.gray())
        cnt.gravity = Gravity.CENTER
        cnt.setPadding(0, dp(8f), 0, 0)
        center.addView(cnt, LinearLayout.LayoutParams(-1, -2))
        body.addView(center, LinearLayout.LayoutParams(-1, -2))

        val g = group()
        g.addView(row("ic_backup", "备份到本地", "导出数据包", true) { createBackupFile() })
        g.addView(row("ic_restore", "从备份文件恢复", null, true) { pickBackupFile() })
        body.addView(g)

        body.addView(View(this), LinearLayout.LayoutParams(1, 0, 1f))

        val wipe = ui.text("抹掉所有内容", FontSize.BODY_SM , Ui.RED, ui.medium)
        wipe.gravity = Gravity.CENTER
        wipe.background = ui.round(ui.card(), Radius.ACTION )
        ui.clipRound(wipe, Radius.ACTION )
        wipe.elevation = dp(2f).toFloat()
        wipe.setPadding(0, dp(18f), 0, dp(18f))
        wipe.setOnClickListener {
            DialogKit.confirm(
                this, "确定抹掉所有内容？",
                "此操作将删除全部本地数据且无法恢复，请谨慎操作！", "取消", "抹掉", Runnable {
                    store.clearAll()
                    showHome()
                    toast("已清空")
                })
        }
        val wp = LinearLayout.LayoutParams(-1, -2)
        wp.setMargins(dp(14f), 0, dp(14f), dp(Spacing.L))
        body.addView(wipe, wp)

        setScreen(body, false, null)
        screen = "backup"
    }

    private fun pickBackupFile() {
        val it = Intent(Intent.ACTION_OPEN_DOCUMENT)
        it.addCategory(Intent.CATEGORY_OPENABLE)
        it.type = "*/*"
        try {
            startActivityForResult(it, REQ_RESTORE)
        } catch (_: Exception) {
            toast("无法打开文件选择器")
        }
    }

    private fun createBackupFile() {
        val it = Intent(Intent.ACTION_CREATE_DOCUMENT)
        it.addCategory(Intent.CATEGORY_OPENABLE)
        it.type = "application/octet-stream"
        it.putExtra(
            Intent.EXTRA_TITLE,
            "小满_Backup_" + java.text.SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(java.util.Date()) + ".ias"
        )
        try {
            startActivityForResult(it, REQ_EXPORT)
        } catch (_: Exception) {
            toast("无法打开文件选择器")
        }
    }

    override fun onPause() {
        super.onPause()
        backgrounded = true
    }

    override fun onResume() {
        super.onResume()
        if (backgrounded && store.str("privacyPassword", "").isNotEmpty()) showLock()
        backgrounded = false
    }

    override fun onActivityResult(request: Int, result: Int, data: Intent?) {
        super.onActivityResult(request, result, data)
        if (result != RESULT_OK || data?.data == null) return
        val uri = data.data!!
        if (request == REQ_RESTORE) {
            importBackup(uri)
            return
        }
        if (request == REQ_EXPORT) {
            exportBackup(uri)
            return
        }
        if (screen != "add") return
        if (request == REQ_COVER) {
            val p = copyImage(uri)
            if (p.isNotEmpty()) {
                draft.put("coverPath", p)
                showAdd()
            }
        } else if (request == REQ_NOTE) {
            val imgs = draft.optJSONArray("noteImages") ?: JSONArray().also { draft.put("noteImages", it) }
            val clip = data.clipData
            if (clip != null) {
                var i = 0
                while (i < clip.itemCount && imgs.length() < 9) {
                    val p = copyImage(clip.getItemAt(i).uri)
                    if (p.isNotEmpty()) imgs.put(p)
                    i++
                }
            } else {
                val p = copyImage(uri)
                if (p.isNotEmpty() && imgs.length() < 9) imgs.put(p)
            }
            showAdd()
        }
    }

    private fun importBackup(uri: Uri) {
        val progress = DialogKit.progress(this, "备份与恢复", "正在解析备份文件…")
        Thread {
            var root: JSONObject? = null
            var importError: String? = null
            val images = HashMap<String, String>()
            try {
                contentResolver.openInputStream(uri).use { input ->
                    val bos = java.io.ByteArrayOutputStream()
                    val buf = ByteArray(8192)
                    while (input != null) {
                        val n = input.read(buf)
                        if (n <= 0) break
                        bos.write(buf, 0, n)
                    }
                    val parsed = Backup.readIas(bos.toByteArray())
                    root = parsed.root
                    val dir = java.io.File(filesDir, "media")
                    if (!dir.exists()) dir.mkdirs()
                    for (e in parsed.images.entries) {
                        val base = e.key.substring("images/".length)
                        val f = java.io.File(dir, "imp_" + System.currentTimeMillis() + "_" + base)
                        java.io.FileOutputStream(f).use { out -> out.write(e.value) }
                        images[e.key] = f.absolutePath
                    }
                }
            } catch (e: Exception) {
                importError = e.toString()
            }
            val parsedRoot = root
            val errMsg = importError
            runOnUiThread {
                progress.dismiss()
                if (parsedRoot == null) {
                    toast("导入失败：" + (errMsg ?: "无法读取文件"))
                    return@runOnUiThread
                }
                pendingImages = images
                chooseImportMode(parsedRoot, "本地备份")
            }
        }.start()
    }

    private fun chooseImportMode(root: JSONObject, source: String) {
        val count = root.optJSONArray("assets")?.length() ?: 0
        val localCount = store.objs("assets").size
        val backupMs = Backup.backupTimeMs(root)
        val localMs = store.updatedAt()
        DialogKit.choice(
            this, "导入备份",
            "来源：$source\n备份：$count 条，最后修改 ${timeText(backupMs)}\n本地：$localCount 条，最后修改 ${timeText(localMs)}\n\n覆盖 = 用备份替换本地；合并 = 保留双方，重复条目取较新的一方。",
            arrayOf("覆盖本地", "合并", "取消"), IntConsumer { i ->
                if (i <= 1) {
                    val replace = i == 0
                    val preferIncoming = backupMs > localMs
                    try {
                        val n = Backup.importInto(store, root, replace, preferIncoming, pendingImages)
                        categoryFilter = "all"
                        filter = "all"
                        for (a in store.objs("assets")) NotificationHelper.schedule(this, a)
                        fillMissingBrands()
                        fillMissingIcons()
                        fillMissingModels()
                        toast(if (replace) "已覆盖导入 $n 条数据" else "已合并 $n 条数据")
                    } catch (e: Exception) {
                        toast("导入失败：" + e.message)
                    }
                }
                afterBackupAction()
            }
        )
    }

    /** 导入完成后回到来源页面（或首页）。 */
    private fun afterBackupAction() {
        when (screen) {
            "backup" -> showBackupPage()
            "settings" -> showSettings()
            "cloud" -> showCloudPage()
            else -> showHome()
        }
    }

    /** 本地时间显示；0 表示没有记录。 */
    private fun timeText(ms: Long): String =
        if (ms <= 0) "未知"
        else SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(ms))

    private fun exportBackup(uri: Uri) {
        Thread {
            var err: String? = null
            try {
                contentResolver.openOutputStream(uri).use { out ->
                    out?.write(Backup.writeIas(store))
                }
            } catch (e: Exception) {
                err = e.message
            }
            val ferr = err
            runOnUiThread {
                toast(if (ferr == null) "已导出备份" else "导出失败：$ferr")
                if (screen == "backup") showBackupPage()
            }
        }.start()
    }

    // ================= 云同步 =================
    private fun cloudSyncSummary(): String? =
        if (cloud.googleLoggedIn()) "Google" else null

    private fun showCloudPage() {
        screen = "cloud"
        val body = ui.vbox()
        body.setPadding(0, 0, 0, dp(28f))

        val head = ui.hbox()
        head.setPadding(dp(Spacing.XXXL), dp(Spacing.XL), dp(Spacing.XXXL), dp(Spacing.M))
        val back = ui.pillButton("‹", ui.card(), ui.ink(), Radius.PILL )
        back.setTextSize(20f)
        back.setPadding(dp(12f), dp(2f), dp(14f), dp(4f))
        ui.clipRound(back, Radius.PILL )
        back.elevation = dp(2f).toFloat()
        back.setOnClickListener { showSettings() }
        head.addView(back)
        body.addView(head)

        val center = ui.vbox()
        center.gravity = Gravity.CENTER
        center.setPadding(0, dp(10f), 0, dp(16f))
        val bigIcon = FrameLayout(this)
        bigIcon.background = ui.round(Ui.LIME_DEEP, Radius.CARD_LG )
        val icon = themedIcon("ic_cloud", 34)
        icon.alpha = 1f
        bigIcon.addView(icon, FrameLayout.LayoutParams(dp(76f), dp(76f), Gravity.CENTER))
        val bip = LinearLayout.LayoutParams(dp(76f), dp(76f))
        bip.gravity = Gravity.CENTER_HORIZONTAL
        center.addView(bigIcon, bip)
        val bt = ui.text("数据同步", FontSize.HEADLINE , ui.ink(), ui.bold)
        bt.gravity = Gravity.CENTER
        center.addView(bt, LinearLayout.LayoutParams(-1, -2))
        val sub = ui.text("Google Drive", FontSize.SMALL , ui.gray())
        sub.gravity = Gravity.CENTER
        sub.setPadding(0, dp(6f), 0, 0)
        center.addView(sub)
        body.addView(center, LinearLayout.LayoutParams(-1, -2))

        section(body, "账号")
        val g1 = group()
        g1.addView(row("ic_cloud", "Google", if (cloud.googleLoggedIn()) cloud.googleEmail().ifEmpty { "已登录" } else "未登录", true) { googleAccountAction() })
        body.addView(g1)

        section(body, "云端备份")
        val g2 = group()
        g2.addView(row("ic_backup", "上传备份到云端", cloudSyncLast(), true) { chooseCloudProvider(true) })
        g2.addView(row("ic_restore", "从云端恢复", null, true) { chooseCloudProvider(false) })
        body.addView(g2)

        // 仅当构建时未内置 Client ID，或用户已填写过自定义 ID 时，才显示开发者配置入口。
        val customId = cloud.customGoogleClientId()
        if (BuildConfig.GOOGLE_CLIENT_ID.isEmpty() || customId.isNotEmpty()) {
            section(body, "开发者配置")
            val g3 = group()
            g3.addView(row("ic_cloud", "Google Client ID", maskId(customId), true) { inputClientId() })
            body.addView(g3)
        }

        val hint = ui.text(
            "云端保存的是加密的 .ias 备份，仅支持 Google 账号。", FontSize.SMALL , ui.gray()
        )
        hint.setPadding(dp(22f), dp(12f), dp(22f), 0)
        body.addView(hint)

        setScreen(scrollPage(body), false, null)
        screen = "cloud"
    }

    private fun cloudSyncLast(): String? {
        val g = cloud.lastSync("google")
        return if (g.isNotEmpty()) "Google $g" else null
    }

    private fun maskId(id: String): String =
        if (id.isEmpty()) "未配置" else id.take(6) + "…" + id.takeLast(4)

    private fun inputClientId() {
        DialogKit.input(this, "Google Client ID", "粘贴 OAuth Client ID", cloud.customGoogleClientId(), "保存") { id ->
            cloud.setGoogleClientId(id)
            toast("已保存")
            showCloudPage()
        }
    }

    private fun googleAccountAction() {
        if (cloud.googleLoggedIn()) {
            DialogKit.list(this, "Google", arrayOf("退出登录"), IntConsumer {
                cloud.logoutGoogle()
                toast("已退出 Google")
                showCloudPage()
            }, null, null)
            return
        }
        if (cloud.googleClientId().isEmpty()) {
            toast("请先配置 Google Client ID")
            inputClientId()
            return
        }
        val progress = DialogKit.progress(this, "Google 登录", "正在打开浏览器…")
        cloud.startGoogleLogin(this, { _ ->
        }, { email ->
            progress.dismiss()
            toast("已登录 Google：${email}")
            showCloudPage()
        }, { msg ->
            progress.dismiss()
            toast(msg)
        })
    }

    private fun chooseCloudProvider(upload: Boolean) {
        if (!cloud.googleLoggedIn()) {
            toast("请先登录 Google 账号")
            return
        }
        if (upload) cloudUpload() else cloudRestore()
    }

    private fun cloudUpload() {
        val progress = DialogKit.progress(this, "上传备份", "正在检查云端…")
        cloud.googleFileInfo({ file ->
            runOnUiThread {
                progress.dismiss()
                val remoteMs = CloudSync.driveTimeMs(file?.optString("modifiedTime").orEmpty())
                val localMs = store.updatedAt()
                // 云端有别人更新过（晚于本地最后修改、也晚于我们上次见到的时间）时提醒。
                if (remoteMs > 0 && remoteMs > localMs && remoteMs > cloud.lastRemoteMs()) {
                    DialogKit.choice(
                        this, "云端备份较新",
                        "云端：最后修改 ${timeText(remoteMs)}\n本地：最后修改 ${timeText(localMs)}\n\n上传会用本地数据覆盖云端备份。",
                        arrayOf("取消", "仍然上传", "先恢复云端"), IntConsumer { i ->
                            when (i) {
                                1 -> doCloudUpload()
                                2 -> cloudRestore()
                            }
                        }
                    )
                } else {
                    doCloudUpload()
                }
            }
        }, { msg ->
            runOnUiThread {
                progress.dismiss()
                toast(msg)
            }
        })
    }

    private fun doCloudUpload() {
        val progress = DialogKit.progress(this, "上传备份", "正在生成并上传…")
        Thread {
            try {
                val bytes = Backup.writeIas(store)
                val done = Runnable {
                    progress.dismiss()
                    toast("已上传到 Google Drive")
                    if (screen == "cloud") showCloudPage()
                }
                val fail = { msg: String ->
                    runOnUiThread {
                        progress.dismiss()
                        toast(msg)
                    }
                }
                cloud.googleUpload(bytes, { runOnUiThread(done) }, fail)
            } catch (e: Exception) {
                runOnUiThread {
                    progress.dismiss()
                    toast("上传失败：" + (e.message ?: ""))
                }
            }
        }.start()
    }

    private fun cloudRestore() {
        val progress = DialogKit.progress(this, "云端恢复", "正在下载备份…")
        val onData = { bytes: ByteArray ->
            runOnUiThread {
                progress.dismiss()
                importCloudBytes(bytes)
            }
        }
        val onError = { msg: String ->
            runOnUiThread {
                progress.dismiss()
                toast(msg)
            }
        }
        cloud.googleDownload(onData, onError)
    }

    private fun importCloudBytes(bytes: ByteArray) {
        val progress = DialogKit.progress(this, "备份与恢复", "正在解析备份文件…")
        Thread {
            var root: JSONObject? = null
            var importError: String? = null
            val images = HashMap<String, String>()
            try {
                val parsed = Backup.readIas(bytes)
                root = parsed.root
                val dir = java.io.File(filesDir, "media")
                if (!dir.exists()) dir.mkdirs()
                for (e in parsed.images.entries) {
                    val base = e.key.substring("images/".length)
                    val f = java.io.File(dir, "imp_" + System.currentTimeMillis() + "_" + base)
                    java.io.FileOutputStream(f).use { out -> out.write(e.value) }
                    images[e.key] = f.absolutePath
                }
            } catch (e: Exception) {
                importError = e.toString()
            }
            val parsedRoot = root
            val errMsg = importError
            runOnUiThread {
                progress.dismiss()
                if (parsedRoot == null) {
                    toast("导入失败：" + (errMsg ?: "无法读取文件"))
                    return@runOnUiThread
                }
                pendingImages = images
                chooseImportMode(parsedRoot, "云端备份")
            }
        }.start()
    }

    private fun securityDialog() {
        val pw = store.str("privacyPassword", "")
        if (pw.isEmpty()) {
            DialogKit.input(this, "设置密码", "请输入4位数字密码", "下一步") { p1 ->
                if (!p1.matches(Regex("\\d{4}"))) {
                    toast("请输入4位数字")
                    return@input
                }
                DialogKit.input(this, "确认密码", "请再次输入密码", "保存") { p2 ->
                    if (p1 != p2) {
                        toast("两次输入不一致")
                        return@input
                    }
                    store.setStr("privacyPassword", p1)
                    toast("密码已设置")
                    showSettings()
                }
            }
        } else {
            DialogKit.list(this, "密码与安全", arrayOf("修改密码", "关闭密码"), IntConsumer { idx ->
                if (idx == 0) {
                    DialogKit.input(this, "原密码", "请输入当前密码", "下一步") { old ->
                        if (old != pw) {
                            toast("密码错误")
                            return@input
                        }
                        DialogKit.input(this, "新密码", "请输入4位数字密码", "保存") { np ->
                            if (!np.matches(Regex("\\d{4}"))) {
                                toast("请输入4位数字")
                                return@input
                            }
                            store.setStr("privacyPassword", np)
                            toast("密码已修改")
                        }
                    }
                } else {
                    DialogKit.input(this, "关闭密码", "请输入当前密码", "确认") { old ->
                        if (old != pw) {
                            toast("密码错误")
                            return@input
                        }
                        store.setStr("privacyPassword", "")
                        toast("密码已关闭")
                        showSettings()
                    }
                }
            }, null, null)
        }
    }

    private fun languageDialog() {
        val cur = store.str("language", "zh")
        DialogKit.sheetSelect(this, "语言", arrayOf("简体中文", "跟随系统"), if (cur == "zh") 0 else 1, IntConsumer { idx ->
            store.setStr("language", if (idx == 0) "zh" else "system")
            showSettings()
        })
    }

    private fun showLock() {
        if (lockView != null) return
        entered = ""
        val lock = FrameLayout(this)
        lock.setBackgroundColor(ui.bg())
        val page = ui.vbox()
        page.gravity = Gravity.CENTER
        page.setPadding(dp(30f), 0, dp(30f), dp(40f))
        val title = ui.text("请输入密码", FontSize.HEADLINE , ui.ink(), ui.bold)
        title.gravity = Gravity.CENTER
        page.addView(title, LinearLayout.LayoutParams(-1, -2))
        val hint = ui.text("输入4位数字密码", FontSize.META , ui.gray())
        hint.gravity = Gravity.CENTER
        hint.setPadding(0, dp(8f), 0, dp(26f))
        page.addView(hint, LinearLayout.LayoutParams(-1, -2))

        val dots = ui.hbox()
        dots.gravity = Gravity.CENTER
        val dotViews = arrayOfNulls<TextView>(4)
        for (i in 0 until 4) {
            val dv = ui.text("○", FontSize.NUMBER , ui.ink())
            dv.setPadding(dp(10f), 0, dp(10f), 0)
            dotViews[i] = dv
            dots.addView(dv)
        }
        page.addView(dots, LinearLayout.LayoutParams(-1, -2))

        val pad = GridLayout(this)
        pad.columnCount = 3
        pad.setPadding(0, dp(28f), 0, 0)
        val pl = LinearLayout.LayoutParams(-2, -2)
        pl.gravity = Gravity.CENTER_HORIZONTAL
        page.addView(pad, pl)
        val keys = arrayOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "", "0", "⌫")
        for (key in keys) {
            val btn = ui.text(key, FontSize.HEADLINE , ui.ink(), ui.medium)
            btn.gravity = Gravity.CENTER
            btn.background = ui.circle(ui.chipBg())
            val gp = GridLayout.LayoutParams()
            gp.width = dp(64f)
            gp.height = dp(64f)
            gp.setMargins(dp(Spacing.L), dp(Spacing.M), dp(Spacing.L), dp(Spacing.M))
            if (key.isNotEmpty()) {
                btn.setOnClickListener {
                    if ("⌫" == key) {
                        if (entered.isNotEmpty()) entered = entered.substring(0, entered.length - 1)
                    } else if (entered.length < 4) {
                        entered += key
                    }
                    for (i in 0 until 4) dotViews[i]?.text = if (i < entered.length) "●" else "○"
                    if (entered.length == 4) {
                        if (entered == store.str("privacyPassword", "")) {
                            root.removeView(lock)
                            lockView = null
                            entered = ""
                        } else {
                            toast("密码错误")
                            entered = ""
                            for (dv in dotViews) dv?.text = "○"
                        }
                    }
                }
            } else {
                btn.visibility = View.INVISIBLE
            }
            pad.addView(btn, gp)
        }
        lock.addView(page, FrameLayout.LayoutParams(-1, -1))
        root.addView(lock, FrameLayout.LayoutParams(-1, -1))
        lockView = lock
    }

    private fun doc(title: String, text: String) {
        DialogKit.info(this, title, text)
    }

    // ================= 新增 / 编辑 =================
    private fun openAdd(id: String?, presetKind: String) {
        returnTab = if ("wishes" == tab) "wishes" else "home"
        screen = "add"
        if (id != null) {
            val a = store.findAsset(id)
            if (a != null) {
                draft = try {
                    JSONObject(a.toString())
                } catch (_: Exception) {
                    JSONObject()
                }
                editing = true
                editOriginal = draft.toString()
                brandAuto = false
            }
        } else {
            editing = false
            editOriginal = null
            brandAuto = false
            draft = JSONObject()
            draft.put("id", "a_" + System.currentTimeMillis())
            draft.put("name", "")
            draft.put("price", 0)
            draft.put("buyDate", Store.today())
            draft.put("kind", presetKind)
            draft.put("status", "serving")
            draft.put("rule", "day")
            draft.put("categoryId", "")
            draft.put("tagIds", JSONArray())
            draft.put("icon", "emoji/icon_default_object.png")
        }
        showAdd()
    }

    private fun showAdd() {
        val body = pageBody()
        body.setPadding(0, 0, 0, dp(30f))

        val top = ui.hbox()
        top.setPadding(dp(Spacing.XXXL), dp(Spacing.XL), dp(Spacing.XXXL), dp(Spacing.M))
        val back = circleIconButton("ic_close", Runnable { restoreTab() })
        top.addView(back, LinearLayout.LayoutParams(dp(36f), dp(36f)))
        val title = ui.text(if (editing) "编辑资产" else "新增资产", FontSize.HEADING, ui.ink(), ui.medium)
        title.gravity = Gravity.CENTER
        top.addView(title, lpw(1f, -2))
        val save = FrameLayout(this)
        saveButtonRef = save
        val check = ui.icon("ic_confirm", 16)
        saveCheckRef = check
        save.addView(check, FrameLayout.LayoutParams(dp(16f), dp(16f), Gravity.CENTER))
        refreshSaveButton(save)
        save.setOnClickListener { saveDraft() }
        top.addView(save, LinearLayout.LayoutParams(dp(36f), dp(36f)))
        body.addView(top)

        val iconPick = ui.vbox()
        iconPick.gravity = Gravity.CENTER
        iconPick.setPadding(0, dp(16f), 0, dp(22f))
        iconPreview = ui.image(draft.optString("icon", "emoji/icon_default_object.png"), 84)
        iconPick.addView(iconPreview)
        val change = ui.text("更换图标", FontSize.CAPTION , ui.gray())
        change.gravity = Gravity.CENTER
        change.setPadding(0, dp(8f), 0, 0)
        iconPick.addView(change)
        iconPick.setOnClickListener { pickIcon() }
        body.addView(iconPick, LinearLayout.LayoutParams(-1, -2))

        val g1 = formCard()
        g1.addView(fieldLabel("ic_name", "名称", editText("请输入昵称", false, draft.optString("name")) { v ->
            draft.put("name", v)
            val t = if (draft.optString("model").isEmpty()) v else draft.optString("model")
            autoDetectBrand(t)
            autoDetectIcon(t)
            updateBrandRow()
            updateIconPreview()
            refreshSaveButton(save)
        }))
        val modelRow = fieldLabel("ic_edit_type", "型号", null)
        modelInput = editText("请输入具体型号", false, draft.optString("model")) { v ->
            draft.put("model", v)
            autoDetectBrand(v)
            autoDetectIcon(v)
            updateBrandRow()
            updateIconPreview()
            refreshSaveButton(save)
        }
        modelRow.addView(modelInput, LinearLayout.LayoutParams(0, -2, 1.2f))
        val modelChev = themedIcon("ic_chevron", 10)
        modelChev.alpha = if (dark) 0.75f else 0.35f
        val mcp = LinearLayout.LayoutParams(dp(6f), dp(10f))
        mcp.leftMargin = dp(Spacing.M)
        modelChev.setOnClickListener { openModelPicker() }
        modelRow.addView(modelChev, mcp)
        g1.addView(modelRow)
        g1.addView(fieldLabel("ic_price", "价格", editText(
            "请输入物品价格", true,
            if (draft.optDouble("price", 0.0) == 0.0) "" else draft.optDouble("price").toString()
        ) { v ->
            draft.put("price", parse(v))
            refreshSaveButton(save)
        }))
        g1.addView(fieldLabel("ic_earnings", "二手估值", editText(
            "可选，用于计算实际持有成本", true,
            if (draft.optDouble("secondhandValue", 0.0) == 0.0) "" else draft.optDouble("secondhandValue").toString()
        ) { v ->
            draft.put("secondhandValue", parse(v))
        }))
        val deductRow = fieldLabel("ic_switch", "扣减二手残值", null)
        val deduct = Switch(this)
        deduct.isChecked = draft.optBoolean("secondhandInCost", true)
        deduct.setOnCheckedChangeListener { _, c -> draft.put("secondhandInCost", c) }
        deductRow.addView(deduct)
        g1.addView(deductRow)
        val typeRow = fieldLabel("ic_type", "类型", null)
        val typeSeg = segment(arrayOf("资产", "心愿"), if (Store.isWish(draft)) 1 else 0, 11f) { i ->
            draft.put("kind", if (i == 0) "asset" else "wish")
            showAdd()
        }
        typeRow.addView(typeSeg)
        g1.addView(typeRow)
        val brandRow = fieldLabel("ic_view", "品牌", null)
        val brandVal = ui.hbox()
        brandRowLogo = ImageView(this)
        brandRowLogo!!.layoutParams = FrameLayout.LayoutParams(dp(16f), dp(16f), Gravity.CENTER)
        if (dark) {
            val plate = FrameLayout(this)
            plate.background = ui.round(Colors.WHITE, Radius.PLATE)
            plate.setPadding(dp(3f), dp(3f), dp(3f), dp(3f))
            plate.addView(brandRowLogo)
            brandVal.addView(plate, LinearLayout.LayoutParams(dp(22f), dp(22f)))
        } else {
            brandVal.addView(brandRowLogo, LinearLayout.LayoutParams(dp(18f), dp(18f)))
        }
        brandRowText = ui.text("未设置", FontSize.BODY_SM , ui.gray())
        brandRowText!!.setPadding(dp(6f), 0, 0, 0)
        brandVal.addView(brandRowText)
        val bchev = themedIcon("ic_chevron", 10)
        bchev.alpha = if (dark) 0.75f else 0.35f
        val bcp = LinearLayout.LayoutParams(dp(6f), dp(10f))
        bcp.leftMargin = dp(Spacing.S)
        brandVal.addView(bchev, bcp)
        brandRow.addView(brandVal)
        updateBrandRow()
        brandRow.setOnClickListener {
            DialogKit.brandPicker(this, draft.optString("brandId"), brandData) { name, id ->
                brandAuto = false
                draft.put("brandId", id)
                draft.put("brandName", name)
                draft.put("brandAuto", false)
                updateBrandRow()
                refreshSaveButton(save)
            }
        }
        g1.addView(brandRow)
        body.addView(g1)

        val g2 = formCard()
        val dateRow = fieldLabel("ic_calendar", "购买日期", null)
        val date = ui.text(draft.optString("buyDate", Store.today()), FontSize.BODY_SM , ui.gray())
        dateRow.addView(date)
        dateRow.setOnClickListener { pickDate(date) }
        g2.addView(dateRow)

        val catRow = fieldLabel("ic_category2", "分类", null)
        val catName = ui.text(categoryName(draft.optString("categoryId")), FontSize.BODY_SM , ui.gray())
        catRow.addView(catName)
        val catWrap = ui.hbox()
        val cc = themedIcon("ic_chevron", 10)
        cc.alpha = if (dark) 0.75f else 0.35f
        catWrap.addView(cc)
        catRow.addView(catWrap)
        catRow.setOnClickListener { pickCategory(catRow) }
        g2.addView(catRow)

        val tagRow = fieldLabel("ic_tag2", "标签", null)
        val tagNames = ui.text(tagNames(), FontSize.BODY_SM , ui.gray())
        tagRow.addView(tagNames)
        val tagWrap = ui.hbox()
        val tc = themedIcon("ic_chevron", 10)
        tc.alpha = if (dark) 0.75f else 0.35f
        tagWrap.addView(tc)
        tagRow.addView(tagWrap)
        tagRow.setOnClickListener { pickTags() }
        g2.addView(tagRow)

        val noteRow = fieldLabel("ic_note", "备注", null)
        val noteVal = ui.text(if (draft.optString("note").isEmpty()) "添加备注" else draft.optString("note"), FontSize.BODY_SM , ui.gray())
        noteVal.isSingleLine = true
        noteRow.addView(noteVal)
        val ncv = themedIcon("ic_chevron", 10)
        ncv.alpha = if (dark) 0.75f else 0.35f
        noteRow.addView(ncv, LinearLayout.LayoutParams(dp(6f), dp(10f)))
        noteRow.setOnClickListener {
            DialogKit.input(this, "备注", "输入备注，最多200字", "确定") { value ->
                val n = if (value.length > 200) value.substring(0, 200) else value
                draft.put("note", n)
                showAdd()
            }
        }
        g2.addView(noteRow)

        val coverRow = fieldLabel("ic_album", "主图", null)
        val coverPath = draft.optString("coverPath")
        if (coverPath.isNotEmpty() && java.io.File(coverPath).exists())
            coverRow.addView(ui.fileImage(coverPath, 40), LinearLayout.LayoutParams(dp(40f), dp(40f)))
        val coverVal = ui.text(if (coverPath.isEmpty()) "未设置" else "更换", FontSize.BODY_SM , ui.gray())
        coverRow.addView(coverVal)
        val ccv = themedIcon("ic_chevron", 10)
        ccv.alpha = if (dark) 0.75f else 0.35f
        coverRow.addView(ccv, LinearLayout.LayoutParams(dp(6f), dp(10f)))
        coverRow.setOnClickListener { pickCover() }
        g2.addView(coverRow)

        val noteImgRow = fieldLabel("ic_photo_add", "备注图片", null)
        val noteImgs = draft.optJSONArray("noteImages")
        val ni = noteImgs?.length() ?: 0
        val niVal = ui.text(if (ni == 0) "添加" else "$ni 张", FontSize.BODY_SM , ui.gray())
        noteImgRow.addView(niVal)
        val ncv2 = themedIcon("ic_chevron", 10)
        ncv2.alpha = if (dark) 0.75f else 0.35f
        noteImgRow.addView(ncv2, LinearLayout.LayoutParams(dp(6f), dp(10f)))
        noteImgRow.setOnClickListener { pickNoteImages() }
        g2.addView(noteImgRow)

        val expireRow = fieldLabel("ic_calendar", "到期提醒", null)
        val ex = draft.optString("expireAt")
        val unitLabel = if ("week" == draft.optString("remindUnit", "day")) "周"
        else if ("month" == draft.optString("remindUnit", "day")) "月" else "天"
        val timeLabel = String.format(Locale.US, "%02d:%02d", draft.optInt("remindHour", 9), draft.optInt("remindMinute", 0))
        val exVal = ui.text(
            if (ex.isEmpty()) "未设置"
            else ex + (if (draft.optInt("remindDays", 0) > 0)
                " · 提前" + draft.optInt("remindDays") + unitLabel + " " + timeLabel else ""), FontSize.BODY_SM , ui.gray()
        )
        expireRow.addView(exVal)
        val ecv = themedIcon("ic_chevron", 10)
        ecv.alpha = if (dark) 0.75f else 0.35f
        expireRow.addView(ecv, LinearLayout.LayoutParams(dp(6f), dp(10f)))
        expireRow.setOnClickListener { expireDialog(exVal) }
        g2.addView(expireRow)

        body.addView(g2)

        if (editing) {
            val gs = formCard()
            val statusRow = fieldLabel("ic_switch", "状态", null)
            val st = draft.optString("status", "serving")
            val sel = if ("retired" == st) 1 else if ("sold" == st) 2 else 0
            statusRow.addView(segment(arrayOf("服役中", "已退役", "已卖出"), sel, 11f) { i ->
                draft.put("status", arrayOf("serving", "retired", "sold")[i])
                showAdd()
            })
            gs.addView(statusRow)
            val st2 = draft.optString("status", "serving")
            if ("retired" == st2) {
                addDateRow(gs, "ic_calendar", "退役日期", "retireDate")
            } else if ("sold" == st2) {
                gs.addView(fieldLabel("ic_price", "卖出价格", editText(
                    "请输入卖出价格", true,
                    if (draft.optDouble("soldPrice", 0.0) == 0.0) "" else draft.optDouble("soldPrice").toString()
                ) { v ->
                    draft.put("soldPrice", parse(v))
                }))
                addDateRow(gs, "ic_calendar", "卖出日期", "soldDate")
            }
            val excludeRow = fieldLabel("ic_switch", "不计入总资产", null)
            val exSwitch = Switch(this)
            exSwitch.isChecked = draft.optBoolean("excludeTotal")
            exSwitch.setOnCheckedChangeListener { _, c -> draft.put("excludeTotal", c) }
            excludeRow.addView(exSwitch)
            gs.addView(excludeRow)
            body.addView(gs)
        }

        val g3 = formCard()
        g3.setPadding(dp(16f), dp(2f), dp(16f), dp(12f))
        val ruleHead = ui.hbox()
        ruleHead.setPadding(0, dp(14f), 0, dp(10f))
        ruleHead.addView(themedIcon("ic_rule", 16))
        val rl = ui.text("成本计算规则", FontSize.BODY , ui.ink())
        rl.setPadding(dp(10f), 0, 0, 0)
        ruleHead.addView(rl)
        g3.addView(ruleHead)
        val rule = draft.optString("rule", "day")
        val rsel = if ("count" == rule) 1 else if ("custom" == rule) 2 else 0
        val ruleSeg = ui.hbox()
        ruleSeg.background = ui.round(if (dark) Colors.NEUTRAL_800 else Colors.NEUTRAL_100, Radius.FIELD )
        ruleSeg.setPadding(dp(Spacing.XS), dp(Spacing.XS), dp(Spacing.XS), dp(Spacing.XS))
        val rules = arrayOf("按天", "按次", "自定义")
        for (i in rules.indices) {
            val on = i == rsel
            val t = ui.text(
                rules[i], FontSize.META ,
                if (on) Colors.NEUTRAL_950 else ui.gray(),
                if (on) ui.medium else ui.regular
            )
            t.gravity = Gravity.CENTER
            t.setPadding(0, dp(10f), 0, dp(10f))
            if (on) t.background = ui.round(Ui.LIME, Radius.SEGMENT_SM)
            t.setOnClickListener {
                draft.put("rule", arrayOf("day", "count", "custom")[i])
                showAdd()
            }
            ruleSeg.addView(t, lpw(1f, -2))
        }
        g3.addView(ruleSeg)
        body.addView(g3)

        if (Store.isWish(draft)) {
            val gw = formCard()
            gw.addView(fieldLabel("ic_target", "目标价格", editText(
                "请输入目标价格", true,
                if (draft.optDouble("targetCost", 0.0) == 0.0) "" else draft.optDouble("targetCost").toString()
            ) { v ->
                draft.put("targetCost", parse(v))
            }))
            addDateRow(gw, "ic_calendar", "目标日期", "targetDate")
            body.addView(gw)
        }

        if ("count" == rule || "custom" == rule) {
            val g4 = formCard()
            val key = if ("count" == rule) "uses" else "customDays"
            val label = if ("count" == rule) "使用次数" else "计费天数"
            val icon = if ("count" == rule) "ic_earnings" else "ic_target"
            g4.addView(fieldLabel(icon, label, editText(
                "请输入$label", true,
                if (draft.optDouble(key, 0.0) == 0.0) "" else draft.optDouble(key).toString()
            ) { v ->
                draft.put(key, parse(v))
            }))
            body.addView(g4)
        }

        val hint = ui.text("按天：价格 ÷ 已使用天数；按次：价格 ÷ 使用次数；自定义：价格 ÷ 自定义天数。", FontSize.SMALL , ui.gray())
        hint.setPadding(dp(20f), dp(10f), dp(20f), 0)
        body.addView(hint)

        setScreen(scrollPage(body), false, null)
        screen = "add"
    }

    private fun expireDialog(label: TextView) {
        DialogKit.list(this, "到期提醒", arrayOf("设置/修改到期时间", "设置提前提醒", "设置提醒时间", "清除到期提醒"), IntConsumer { idx ->
            when (idx) {
                0 -> {
                    val cal = Calendar.getInstance()
                    try {
                        val p = draft.optString("expireAt", Store.today()).split("-")
                        cal.set(p[0].toInt(), p[1].toInt() - 1, p[2].toInt())
                    } catch (_: Exception) {
                    }
                    DatePickerDialog(this, DatePickerDialog.OnDateSetListener { _, y, m, d ->
                        draft.put("expireAt", String.format(Locale.US, "%04d-%02d-%02d", y, m + 1, d))
                        showAdd()
                    }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
                }
                1 -> {
                    DialogKit.input(this, "提前提醒", "提前多少（数字）", "下一步") { amount ->
                        val n = parse(amount).toInt()
                        DialogKit.list(this, "提醒单位", arrayOf("天", "周", "月"), IntConsumer { u ->
                            draft.put("remindDays", n)
                            draft.put("remindUnit", arrayOf("day", "week", "month")[u])
                            showAdd()
                        }, null, null)
                    }
                }
                2 -> {
                    DialogKit.input(this, "提醒时间", "HH:mm，例如 09:00", "保存") { time ->
                        try {
                            val p = time.split(":")
                            draft.put("remindHour", p[0].trim().toInt())
                            draft.put("remindMinute", p[1].trim().toInt())
                        } catch (_: Exception) {
                        }
                        showAdd()
                    }
                }
                else -> {
                    draft.put("expireAt", "")
                    draft.put("remindDays", 0)
                    draft.put("remindUnit", "day")
                    showAdd()
                }
            }
        }, null, null)
    }

    private fun addDateRow(parent: LinearLayout, icon: String, label: String, key: String) {
        val row = fieldLabel(icon, label, null)
        val def = Store.today()
        val v = ui.text(draft.optString(key, def), FontSize.BODY_SM , ui.gray())
        row.addView(v)
        val c = themedIcon("ic_chevron", 10)
        c.alpha = if (dark) 0.75f else 0.35f
        row.addView(c, LinearLayout.LayoutParams(dp(6f), dp(10f)))
        row.setOnClickListener {
            val cal = Calendar.getInstance()
            try {
                val p = draft.optString(key, def).split("-")
                cal.set(p[0].toInt(), p[1].toInt() - 1, p[2].toInt())
            } catch (_: Exception) {
            }
            DatePickerDialog(this, DatePickerDialog.OnDateSetListener { _, y, m, d ->
                val date = String.format(Locale.US, "%04d-%02d-%02d", y, m + 1, d)
                draft.put(key, date)
                v.text = date
            }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
        }
        parent.addView(row)
    }

    private fun displayName(a: JSONObject): String {
        val n = a.optString("name")
        return if (n.isEmpty()) a.optString("model") else n
    }

    private fun matchText(a: JSONObject): String {
        val m = a.optString("model")
        return if (m.isEmpty()) a.optString("name") else m
    }

    private fun matchModelIcon(name: String?): Array<String>? {
        if (name == null || name.trim().isEmpty()) return null
        val lower = name.lowercase(Locale.US)
        val pairs = ArrayList<Array<String>>()
        for ((key, value) in MODEL_ICONS) {
            for (k in value) pairs.add(arrayOf(k.lowercase(Locale.US), key))
        }
        pairs.sortByDescending { it[0].length }
        for (p in pairs) {
            val k = p[0]
            val latin = k.contains(Regex("[a-z]"))
            val hit = if (latin) {
                java.util.regex.Pattern.compile("(?<![a-z0-9])" + java.util.regex.Pattern.quote(k) + "(?![a-z0-9])").matcher(lower).find()
            } else {
                k.length >= 2 && lower.contains(k)
            }
            if (hit) return arrayOf(p[1])
        }
        return null
    }

    private fun autoDetectIcon(name: String?) {
        val manual = !draft.optBoolean("iconAuto", true) && draft.optString("icon") != "emoji/icon_default_object.png"
        if (manual) return
        val m = matchModelIcon(name)
        if (m != null) {
            draft.put("icon", m[0])
            draft.put("iconAuto", true)
        } else {
            draft.put("icon", "emoji/icon_default_object.png")
            draft.put("iconAuto", false)
        }
    }

    private fun updateIconPreview() {
        val preview = iconPreview ?: return
        val bm = ui.bitmap(draft.optString("icon", "emoji/icon_default_object.png"))
        if (bm != null) preview.setImageBitmap(bm)
    }

    private fun fillMissingIcons(): Int {
        var changed = 0
        for (a in store.objs("assets")) {
            val auto = a.optBoolean("iconAuto", false)
            val icon = a.optString("icon")
            if (!auto && icon.isNotEmpty() && icon != "emoji/icon_default_object.png") continue
            val m = matchModelIcon(matchText(a))
            if (m != null && m[0] != icon) {
                a.put("icon", m[0])
                a.put("iconAuto", true)
                changed++
            }
        }
        if (changed > 0) store.save()
        return changed
    }

    private fun matchBrand(name: String?): Array<String>? {
        if (name == null || name.trim().isEmpty()) return null
        val lower = name.lowercase(Locale.US)
        for (a in brandAliases) {
            val alias = a[0]
            val latin = "1" == a[3]
            val hit = if (latin) {
                java.util.regex.Pattern.compile("(?<![a-z0-9])" + java.util.regex.Pattern.quote(alias) + "(?![a-z0-9])").matcher(lower).find()
            } else {
                alias.length >= 2 && lower.contains(alias)
            }
            if (hit) return arrayOf(a[1], a[2])
        }
        return null
    }

    private fun fillMissingModels(): Int {
        var changed = 0
        for (a in store.objs("assets")) {
            if (a.optString("model").isEmpty() && a.optString("name").isNotEmpty()) {
                a.put("model", a.optString("name"))
                changed++
            }
        }
        if (changed > 0) store.save()
        return changed
    }

    private fun fillMissingBrands(): Int {
        var changed = 0
        for (a in store.objs("assets")) {
            val auto = a.optBoolean("brandAuto", false)
            if (a.optString("brandId").isNotEmpty() && !auto) continue
            val m = matchBrand(matchText(a))
            if (m != null) {
                if (m[1] != a.optString("brandId")) {
                    a.put("brandId", m[1])
                    a.put("brandName", m[0])
                    a.put("brandAuto", true)
                    changed++
                }
            } else if (auto) {
                a.put("brandId", "")
                a.put("brandName", "")
                a.put("brandAuto", false)
                changed++
            }
        }
        if (changed > 0) store.save()
        return changed
    }

    private fun openModelPicker() {
        val brandId = draft.optString("brandId")
        val current = draft.optString("model")
        if (brandId.isEmpty()) {
            DialogKit.input(this, "型号", "请输入具体型号", current, "保存") { m ->
                draft.put("model", m)
                autoDetectBrand(m)
                autoDetectIcon(m)
                modelInput?.setText(m)
                updateBrandRow()
                updateIconPreview()
                refreshSaveButton(saveButtonRef)
            }
            return
        }
        val brandName = draft.optString("brandName")
        val groups = brandModels[brandId] ?: JSONArray()
        DialogKit.modelPicker(this, brandName, current, groups) { m ->
            draft.put("model", m)
            autoDetectBrand(m)
            autoDetectIcon(m)
            modelInput?.setText(m)
            updateBrandRow()
            updateIconPreview()
            refreshSaveButton(saveButtonRef)
        }
    }

    private fun autoDetectBrand(name: String?) {
        if (name == null || name.trim().isEmpty()) {
            if (brandAuto) {
                draft.put("brandId", "")
                draft.put("brandName", "")
                brandAuto = false
            }
            return
        }
        if (!brandAuto && draft.optString("brandId").isNotEmpty()) return
        val m = matchBrand(name)
        if (m != null) {
            draft.put("brandId", m[1])
            draft.put("brandName", m[0])
            draft.put("brandAuto", true)
            brandAuto = true
        } else if (brandAuto) {
            draft.put("brandId", "")
            draft.put("brandName", "")
            draft.put("brandAuto", false)
            brandAuto = false
        }
    }

    private fun updateBrandRow() {
        val logo = brandRowLogo ?: return
        val text = brandRowText ?: return
        val id = draft.optString("brandId")
        if (id.isEmpty()) {
            logo.visibility = View.GONE
            text.text = "未设置"
        } else {
            val bm = ui.bitmap("brands/$id.png")
            if (bm != null) {
                logo.setImageBitmap(bm)
                logo.visibility = View.VISIBLE
            } else logo.visibility = View.GONE
            text.text = draft.optString("brandName")
        }
    }

    private fun formCard(): LinearLayout {
        val g = ui.vbox()
        g.background = ui.round(ui.card(), Radius.CARD )
        ui.clipRound(g, Radius.CARD )
        g.elevation = dp(2f).toFloat()
        g.setPadding(dp(24f), dp(2f), dp(24f), dp(2f))
        val gp = LinearLayout.LayoutParams(-1, -2)
        gp.setMargins(dp(28f), 0, dp(28f), dp(Spacing.XL))
        g.layoutParams = gp
        return g
    }

    private fun fieldLabel(icon: String, label: String, right: View?): LinearLayout {
        val r = ui.hbox()
        r.minimumHeight = dp(52f)
        r.addView(themedIcon(icon, 16))
        val t = ui.text(label, 13.5f, ui.ink())
        t.setPadding(dp(10f), 0, 0, 0)
        r.addView(t, lpw(1f, -2))
        if (right != null) {
            val rp = LinearLayout.LayoutParams(0, -2, 1.2f)
            right.layoutParams = rp
            r.addView(right)
        }
        return r
    }

    private fun editText(hint: String, number: Boolean, value: String, onChange: TextClick): EditText {
        val et = EditText(this)
        et.hint = hint
        et.setText(value)
        et.setTextSize(14f)
        et.setTextColor(ui.ink())
        et.setHintTextColor(ui.gray())
        et.typeface = ui.regular
        et.gravity = Gravity.END
        et.background = null
        et.isSingleLine = true
        et.setPadding(0, 0, 0, 0)
        if (number) et.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        et.addTextChangedListener(Watcher(onChange))
        return et
    }

    private class Watcher(private val cb: TextClick) : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
        override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
        override fun afterTextChanged(s: Editable?) {
            cb.on(s?.toString() ?: "")
        }
    }

    private fun hasNamePrice(): Boolean {
        val named = draft.optString("name").trim().isNotEmpty() || draft.optString("model").trim().isNotEmpty()
        return named && draft.optDouble("price", 0.0) > 0
    }

    private fun refreshSaveButton(save: View?) {
        if (save == null) return
        val changed = !editing || editOriginal == null || draft.toString() != editOriginal
        val ok = hasNamePrice() && changed
        val bg: Int
        val fg: Int
        if (dark) {
            bg = if (ok) Colors.NEUTRAL_120 else Colors.NEUTRAL_700
            fg = if (ok) Colors.BLACK else Colors.NEUTRAL_400
        } else {
            bg = if (ok) Colors.BLACK else Colors.NEUTRAL_340
            fg = Colors.WHITE
        }
        save.background = ui.circle(bg)
        save.alpha = if (ok) 1f else 0.9f
        if (saveCheckRef != null && save === saveButtonRef) saveCheckRef!!.setColorFilter(fg)
    }

    private fun parse(s: String): Double = try {
        s.trim().toDouble()
    } catch (_: Exception) {
        0.0
    }

    private fun pickCover() {
        val it = Intent(Intent.ACTION_OPEN_DOCUMENT)
        it.addCategory(Intent.CATEGORY_OPENABLE)
        it.type = "image/*"
        try {
            startActivityForResult(it, REQ_COVER)
        } catch (_: Exception) {
            toast("无法打开图片选择器")
        }
    }

    private fun pickNoteImages() {
        val it = Intent(Intent.ACTION_OPEN_DOCUMENT)
        it.addCategory(Intent.CATEGORY_OPENABLE)
        it.type = "image/*"
        it.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
        try {
            startActivityForResult(it, REQ_NOTE)
        } catch (_: Exception) {
            toast("无法打开图片选择器")
        }
    }

    private fun copyImage(uri: Uri): String {
        return try {
            contentResolver.openInputStream(uri).use { input ->
                val dir = java.io.File(filesDir, "media")
                if (!dir.exists()) dir.mkdirs()
                val f = java.io.File(dir, "img_" + System.currentTimeMillis() + "_" + (Math.random() * 1000).toInt() + ".jpg")
                java.io.FileOutputStream(f).use { out ->
                    val buf = ByteArray(8192)
                    while (input != null) {
                        val n = input.read(buf)
                        if (n <= 0) break
                        out.write(buf, 0, n)
                    }
                }
                f.absolutePath
            }
        } catch (_: Exception) {
            ""
        }
    }

    private fun pickDate(label: TextView) {
        val c = Calendar.getInstance()
        try {
            val p = draft.optString("buyDate", Store.today()).split("-")
            c.set(p[0].toInt(), p[1].toInt() - 1, p[2].toInt())
        } catch (_: Exception) {
        }
        DatePickerDialog(this, DatePickerDialog.OnDateSetListener { _, y, m, d ->
            val date = String.format("%04d-%02d-%02d", y, m + 1, d)
            draft.put("buyDate", date)
            label.text = date
        }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show()
    }

    private fun categoryName(id: String): String {
        for (c in store.objs("categories")) if (c.optString("id") == id) return c.optString("name")
        return "未分类"
    }

    private fun tagNames(): String {
        val ids = draft.optJSONArray("tagIds")
        val names = ArrayList<String>()
        if (ids != null) {
            for (i in 0 until ids.length()) {
                for (t in store.objs("tags"))
                    if (t.optString("id") == ids.optString(i)) names.add(t.optString("name"))
            }
        }
        return if (names.isEmpty()) "选择标签" else names.joinToString("、")
    }

    private fun pickCategory(row: LinearLayout) {
        val cats = store.objs("categories")
        val names = Array(cats.size + 1) { if (it == 0) "未分类" else cats[it - 1].optString("name") }
        DialogKit.sheetList(this, "选择分类", "点击添加分类", names, IntConsumer { i ->
            draft.put("categoryId", if (i == 0) "" else cats[i - 1].optString("id"))
            showAdd()
        }, Runnable {
            inputDialog("新建分类", "请输入分类名称") { name ->
                val c = JSONObject().put("id", "cat_" + System.currentTimeMillis()).put("name", name)
                store.arr("categories").put(c)
                store.save()
                draft.put("categoryId", c.optString("id"))
                showAdd()
            }
        })
    }

    private fun pickTags() {
        val tags = store.objs("tags")
        val names = Array(tags.size) { tags[it].optString("name") }
        val checked = BooleanArray(tags.size)
        val ids = draft.optJSONArray("tagIds")
        for (i in tags.indices) {
            if (ids != null) for (k in 0 until ids.length())
                if (tags[i].optString("id") == ids.optString(k)) checked[i] = true
        }
        DialogKit.multi(this, "选择标签", names, checked, java.util.function.Consumer { sel ->
            val arr = JSONArray()
            for (i in tags.indices) if (sel[i]) arr.put(tags[i].optString("id"))
            draft.put("tagIds", arr)
            showAdd()
        }, "新建", Runnable {
            inputDialog("新建标签", "请输入标签名称") { name ->
                val t = JSONObject().put("id", "tag_" + System.currentTimeMillis()).put("name", name)
                store.arr("tags").put(t)
                store.save()
                pickTags()
            }
        })
    }

    private fun pickIcon() {
        val labels = arrayOf("默认", "数码", "包包", "服饰", "美妆", "电器", "家具", "房屋", "乐器", "珠宝首饰", "宠物", "体育运动", "玩具", "交通工具")
        val folders = arrayOf("", "apple/digital", "apple/bag", "apple/clothing", "apple/beauty", "apple/appliance", "apple/furniture", "apple/house", "apple/instrument", "apple/jewelry", "apple/pet", "apple/sport", "apple/toy", "apple/vehicle")
        DialogKit.iconPicker(this, draft.optString("icon"), labels, folders) { path ->
            draft.put("icon", path)
            draft.put("iconAuto", false)
            showAdd()
        }
    }

    private fun restoreTab() {
        if ("wishes" == returnTab) showWishes() else showHome()
        screen = returnTab
    }

    private fun saveDraft() {
        if (!hasNamePrice()) {
            toast("请填写名称与价格")
            return
        }
        if (editing && editOriginal != null && draft.toString() == editOriginal) {
            restoreTab()
            return
        }
        if (draft.optString("kind").isEmpty()) draft.put("kind", "asset")
        if (draft.optString("status").isEmpty()) draft.put("status", "serving")
        if (draft.optString("buyDate").isEmpty()) draft.put("buyDate", Store.today())
        if (draft.optString("icon").isEmpty()) draft.put("icon", "emoji/icon_default_object.png")
        store.upsertAsset(draft)
        NotificationHelper.schedule(this, draft)
        val first = !editing && !store.bool("ratingShown", false)
        val kind = draft.optString("kind", "asset")
        draft = JSONObject()
        editing = false
        if (first) {
            store.setBool("ratingShown", true)
            DialogKit.confirm(this, "喜欢这个应用吗？", "请给我们评分支持！", "不喜欢", "喜欢",
                Runnable {
                    toast("感谢你的支持 ♥")
                    if ("wish" == kind) showWishes() else showHome()
                },
                Runnable { if ("wish" == kind) showWishes() else showHome() })
        } else {
            if ("wish" == kind) showWishes() else showHome()
        }
    }

    // ================= 详情 =================
    private fun showDetail(id: String) {
        val a = store.findAsset(id)
        if (a == null) {
            showHome()
            return
        }
        detailId = id
        returnTab = if ("wishes" == tab) "wishes" else "home"
        screen = "detail"
        val body = ui.vbox()
        body.setPadding(0, 0, 0, dp(40f))

        val top = ui.hbox()
        top.setPadding(dp(Spacing.XXXL), dp(Spacing.XL), dp(Spacing.XXXL), dp(Spacing.M))
        val back = circleIconButton("ic_close", Runnable { restoreTab() })
        top.addView(back, LinearLayout.LayoutParams(dp(36f), dp(36f)))
        top.addView(View(this), lpw(1f, 1))
        val favorite = a.optBoolean("favorite", false)
        val favBtn = FrameLayout(this)
        favBtn.background = ui.circle(if (dark) Colors.NEUTRAL_800 else ui.card())
        ui.clipCircle(favBtn)
        favBtn.elevation = dp(2f).toFloat()
        val star = ui.text(if (favorite) "★" else "☆", FontSize.TITLE_LG, if (favorite) Ui.LIME_DEEP else ui.ink())
        star.gravity = Gravity.CENTER
        favBtn.addView(star, FrameLayout.LayoutParams(-1, -1))
        favBtn.setOnClickListener {
            try {
                a.put("favorite", !favorite)
                store.save()
            } catch (_: Exception) {
            }
            toast(if (!favorite) "已收藏" else "已取消收藏")
            showDetail(id)
        }
        val fp = LinearLayout.LayoutParams(dp(36f), dp(36f))
        fp.rightMargin = dp(Spacing.L)
        top.addView(favBtn, fp)
        val edit = circleIconButton("ic_edit", Runnable { openAdd(a.optString("id"), "asset") })
        top.addView(edit, LinearLayout.LayoutParams(dp(36f), dp(36f)))
        body.addView(top, LinearLayout.LayoutParams(-1, -2))

        val center = ui.vbox()
        center.gravity = Gravity.CENTER
        center.setPadding(0, dp(8f), 0, 0)
        val dcv = a.optString("coverPath")
        if (dcv.isNotEmpty() && java.io.File(dcv).exists()) center.addView(ui.fileImage(dcv, 96, 16f))
        else center.addView(ui.image(a.optString("icon", "emoji/icon_default_object.png"), 88))
        val name = ui.text(displayName(a), FontSize.HEADLINE , ui.ink(), ui.bold)
        name.gravity = Gravity.CENTER
        name.setPadding(0, dp(14f), 0, 0)
        center.addView(name)
        center.addView(moneyLineSuffix(store.dailyCost(a), 22f, "/天"))
        val days = store.ageDays(a)
        val meta = ui.text("总价：" + store.money(a.optDouble("price")) + "  |  已服役 " + days + " 天", FontSize.SMALL , ui.gray())
        meta.gravity = Gravity.CENTER
        meta.setPadding(0, dp(10f), 0, 0)
        center.addView(meta)
        body.addView(center, LinearLayout.LayoutParams(-1, -2))

        val chartCard = ui.vbox()
        chartCard.background = ui.round(ui.card(), Radius.CARD )
        chartCard.elevation = 0f
        chartCard.clipToOutline = true
        chartCard.setPadding(dp(Spacing.XXL), dp(Spacing.XXL), dp(Spacing.XXL), dp(Spacing.L))
        val ccp = LinearLayout.LayoutParams(-1, -2)
        ccp.setMargins(dp(14f), dp(Spacing.XXXL), dp(14f), dp(Spacing.XL))
        chartCard.layoutParams = ccp
        chartCard.addView(ui.text("日均成本", FontSize.BODY_SM , ui.ink(), ui.medium))
        val series = detailSeries(a, days)
        val chart = ChartView(this)
        val buyDate = a.optString("buyDate")
        val endDate = if ("sold" == a.optString("status") && a.optString("soldDate").isNotEmpty()) a.optString("soldDate") else Store.today()
        val crossYear = buyDate.length >= 4 && buyDate.substring(0, 4) != endDate.substring(0, 4)
        val leftLabel = if (buyDate.length >= 10) {
            if (crossYear) buyDate.replace("-", ".") else buyDate.substring(5).replace("-", ".")
        } else ""
        val rightLabel = if ("sold" == a.optString("status") && endDate.length >= 10) {
            if (crossYear) endDate.replace("-", ".") else endDate.substring(5).replace("-", ".")
        } else "今天"
        chart.setData(series, store.fmt(chartMax(series), 0), store.fmt(chartMax(series) / 2, 0), "0", leftLabel, rightLabel)
        val chp = LinearLayout.LayoutParams(-1, dp(120f))
        chp.topMargin = dp(2f)
        chartCard.addView(chart, chp)
        body.addView(chartCard)

        val rows = ui.vbox()
        rows.setBackgroundColor(Colors.TRANSPARENT)
        rows.setPadding(dp(16f), dp(0f).toInt(), dp(16f), dp(0f).toInt())
        val rlp = LinearLayout.LayoutParams(-1, -2)
        rlp.setMargins(dp(14f), 0, dp(14f), 0)
        rows.layoutParams = rlp
        rows.addView(drow("单价", store.money(a.optDouble("price"))))
        rows.addView(drow("类别", categoryName(a.optString("categoryId"))))
        if (a.optString("brandName").isNotEmpty()) {
            val br = ui.hbox()
            br.setPadding(0, dp(13f), 0, dp(13f))
            br.addView(ui.text("品牌", FontSize.META , ui.ink2()), lpw(1f, -2))
            val blogo = ui.image("brands/" + a.optString("brandId") + ".png", 16)
            if (dark) {
                val plate = FrameLayout(this)
                plate.background = ui.round(Colors.WHITE, Radius.FIELD )
                plate.setPadding(dp(2f), dp(2f), dp(2f), dp(2f))
                plate.addView(blogo, FrameLayout.LayoutParams(dp(16f), dp(16f), Gravity.CENTER))
                br.addView(plate, LinearLayout.LayoutParams(dp(20f), dp(20f)))
            } else {
                br.addView(blogo)
            }
            val bname = ui.text(a.optString("brandName"), FontSize.META , ui.ink(), ui.medium)
            bname.setPadding(dp(6f), 0, 0, 0)
            br.addView(bname)
            val wrap = ui.vbox()
            wrap.addView(br)
            wrap.addView(ui.hline())
            rows.addView(wrap)
        }
        val tn = tagNamesOf(a)
        if (tn.isNotEmpty()) rows.addView(drow("标签", tn))
        rows.addView(drow("购买日期", a.optString("buyDate")))
        val shv = a.optDouble("secondhandValue", 0.0)
        if (shv > 0 && "sold" != a.optString("status")) {
            rows.addView(drow("二手估值", store.money(shv)))
            val label = if (a.optBoolean("secondhandInCost", true)) "实际持有成本" else "持有成本（未扣残值）"
            rows.addView(drow(label, store.money(store.netCost(a)) + " ｜ " + store.money(store.dailyCost(a)) + "/天"))
        }
        if ("serving" != a.optString("status", "serving"))
            rows.addView(drow("状态", Store.statusText(a.optString("status"))))
        if ("retired" == a.optString("status") && a.optString("retireDate").isNotEmpty())
            rows.addView(drow("退役日期", a.optString("retireDate")))
        if ("sold" == a.optString("status")) {
            if (a.optDouble("soldPrice", 0.0) > 0) rows.addView(drow("卖出价格", store.money(a.optDouble("soldPrice"))))
            if (a.optString("soldDate").isNotEmpty()) rows.addView(drow("卖出日期", a.optString("soldDate")))
        }
        if (Store.isWish(a)) {
            if (a.optDouble("targetCost", 0.0) > 0) rows.addView(drow("目标价格", store.money(a.optDouble("targetCost"))))
            if (a.optString("targetDate").isNotEmpty()) rows.addView(drow("目标日期", a.optString("targetDate")))
        }
        if (a.optString("note").isNotEmpty()) rows.addView(drow("备注", a.optString("note")))
        val noteImgs = a.optJSONArray("noteImages")
        if (noteImgs != null && noteImgs.length() > 0) {
            val imgsRow = ui.hbox()
            imgsRow.setPadding(0, dp(8f), 0, dp(8f))
            var i = 0
            while (i < noteImgs.length() && i < 9) {
                val p = noteImgs.optString(i)
                if (java.io.File(p).exists()) {
                    val iv = ui.fileImage(p, 56)
                    val ip = LinearLayout.LayoutParams(dp(56f), dp(56f))
                    ip.rightMargin = dp(Spacing.M)
                    imgsRow.addView(iv, ip)
                }
                i++
            }
            rows.addView(imgsRow)
        }
        if (a.optBoolean("excludeTotal")) rows.addView(drow("计入总资产", "否"))
        if (a.optString("expireAt").isNotEmpty()) {
            val expired = a.optString("expireAt").compareTo(Store.today()) < 0
            val todayExpire = a.optString("expireAt") == Store.today()
            rows.addView(drow("到期时间", a.optString("expireAt") + if (expired) "（已过期）" else if (todayExpire) "（今日到期）" else ""))
            if (a.optInt("remindDays", 0) > 0) {
                val ul = if ("week" == a.optString("remindUnit", "day")) "周" else if ("month" == a.optString("remindUnit", "day")) "月" else "天"
                val tl = String.format(Locale.US, "%02d:%02d", a.optInt("remindHour", 9), a.optInt("remindMinute", 0))
                rows.addView(drow("提前提醒", a.optInt("remindDays").toString() + " " + ul + " · " + tl))
            }
        }
        body.addView(rows)

        val del = ui.hbox()
        del.gravity = Gravity.CENTER
        del.background = ui.round(if (dark) Colors.DANGER_SURFACE_DARK else Colors.DANGER_SURFACE, Radius.ACTION_LG)
        del.setPadding(0, dp(15f), 0, dp(15f))
        val delIcon = ui.icon("ic_delete", 16)
        delIcon.setColorFilter(Ui.RED)
        del.addView(delIcon)
        val dt = ui.text("删除资产", FontSize.BODY , Ui.RED, ui.medium)
        dt.setPadding(dp(8f), 0, 0, 0)
        del.addView(dt)
        val delLp = LinearLayout.LayoutParams(-1, -2)
        delLp.setMargins(dp(14f), dp(28f), dp(14f), 0)
        body.addView(del, delLp)
        del.setOnClickListener {
            DialogKit.confirm(this, "删除资产",
                "删除后无法恢复，确定删除「" + displayName(a) + "」？", "取消", "删除", Runnable {
                    store.removeAsset(a.optString("id"))
                    restoreTab()
                    toast("已删除")
                })
        }

        setScreen(scrollPage(body), false, null)
        screen = "detail"
    }

    private fun drow(k: String, v: String): View {
        val row = ui.hbox()
        row.setPadding(0, dp(13f), 0, dp(13f))
        row.addView(ui.text(k, FontSize.META , ui.ink2()), lpw(1f, -2))
        row.addView(ui.text(v, FontSize.META , ui.ink(), ui.medium))
        val wrap = ui.vbox()
        wrap.addView(row)
        wrap.addView(ui.hline())
        return wrap
    }

    private fun detailSeries(a: JSONObject, days: Int): DoubleArray {
        val len = Math.min(60, Math.max(days, 2))
        val out = DoubleArray(len)
        val price = a.optDouble("price")
        val constant = store.dailyCost(a)
        for (i in 0 until len) {
            val age = 1 + (days - 1) * (i / (len - 1).toDouble())
            out[i] = if ("day" == a.optString("rule", "day")) price / Math.max(1.0, age) else constant
        }
        return out
    }

    private fun tagNamesOf(a: JSONObject): String {
        val ids = a.optJSONArray("tagIds") ?: return ""
        val names = ArrayList<String>()
        for (i in 0 until ids.length()) {
            for (t in store.objs("tags"))
                if (t.optString("id") == ids.optString(i)) names.add(t.optString("name"))
        }
        return names.joinToString("、")
    }

    // ================= 返回键 =================
    override fun onBackPressed() {
        when (screen) {
            "backup", "cloud" -> {
                showSettings()
                return
            }
            "add", "detail" -> {
                restoreTab()
                return
            }
        }
        if (screen != "home" && screen != "onboard") {
            showHome()
            return
        }
        super.onBackPressed()
    }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        store = Store(this)
        grid = store.settingBool("viewGrid", true)
        sortKey = store.setting("sortKey", "")
        if (sortKey.isEmpty()) {
            when (store.setting("sortMode", "default")) {
                "price" -> { sortKey = "price"; sortAsc = false }
                "daily" -> { sortKey = "daily"; sortAsc = true }
                else -> { sortKey = "days"; sortAsc = false }
            }
            store.setSetting("sortKey", sortKey)
            store.setSetting("sortAsc", sortAsc)
        } else {
            if (sortKey == "default") sortKey = "days"
            sortAsc = store.settingBool("sortAsc", false)
        }
        store.setSystemDark(
            (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
                android.content.res.Configuration.UI_MODE_NIGHT_YES
        )
        ui = Ui(this)
        cloud = CloudSync(this)
        android.util.Log.i("XiaomanCore", "rust core " + NativeCore.version())
        brandPrefs = getSharedPreferences("xiaoman_brand_data", MODE_PRIVATE)
        loadBrandData()
        store.quiet {
            if (!store.bool("modelMigrated", false)) {
                for (a in store.objs("assets")) {
                    if (a.optString("model").isEmpty()) a.put("model", a.optString("name"))
                }
                store.save()
                store.setBool("modelMigrated", true)
            }
            if (!store.bool("brandRepair2", false)) {
                for (a in store.objs("assets")) {
                    if (!a.optBoolean("brandAuto", false)) {
                        a.put("brandId", "")
                        a.put("brandName", "")
                    }
                }
                store.save()
                store.setBool("brandRepair2", true)
            }
        }
        fillMissingBrands()
        fillMissingIcons()
        fillMissingModels()
        dark = store.isDark()
        root = FrameLayout(this)
        root.setBackgroundColor(ui.bg())
        content = FrameLayout(this)
        navHost = FrameLayout(this)
        root.addView(content, FrameLayout.LayoutParams(-1, -1))
        root.addView(navHost, FrameLayout.LayoutParams(-1, -1))
        setContentView(root)
        applySystemBars()
        if (Build.VERSION.SDK_INT >= 33) requestPermissions(arrayOf("android.permission.POST_NOTIFICATIONS"), 9001)
        if (store.bool("onboarded", false)) showHome() else showOnboarding()
        if (store.str("privacyPassword", "").isNotEmpty()) showLock()
        checkBrandDataUpdate(false)
        intent?.data?.let { cloud.handleGoogleUri(this, it) }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        intent?.data?.let { cloud.handleGoogleUri(this, it) }
    }

    private fun loadBrandData() {
        var root: JSONObject? = null
        val cached = brandPrefs.getString("json", null)
        if (cached != null) {
            try {
                root = JSONObject(cached)
            } catch (_: Exception) {
                root = null
            }
        }
        if (root == null) {
            try {
                assets.open("brands/brands.json").use { input ->
                    val bos = java.io.ByteArrayOutputStream()
                    val buf = ByteArray(8192)
                    var n: Int
                    while (input.read(buf).also { n = it } > 0) bos.write(buf, 0, n)
                    root = JSONObject(String(bos.toByteArray(), java.nio.charset.StandardCharsets.UTF_8))
                }
            } catch (_: Exception) {
            }
        }
        if (root != null) applyBrandRoot(root!!)
    }

    private fun applyBrandRoot(root: JSONObject?): Boolean {
        if (root == null) return false
        val schema = root.optInt("schema", 1)
        if (schema > BRAND_SCHEMA) return false
        val sup = root.optJSONObject("supportedApp")
        if (sup != null) {
            val lo = sup.optInt("minVersionCode", 0)
            val hi = sup.optInt("maxVersionCode", 0)
            if (APP_VERSION_CODE < lo || (hi > 0 && APP_VERSION_CODE > hi)) return false
        }
        var brands = root.optJSONObject("brands")
        if (brands == null) brands = root
        brandData = brands
        brandModels.clear()
        brandAliases.clear()
        val it = brands.keys()
        while (it.hasNext()) {
            val cat = it.next()
            val arr = brands.optJSONArray(cat)
            for (i in 0 until (arr?.length() ?: 0)) {
                val item = arr!!.optJSONArray(i) ?: continue
                val name = item.optString(0)
                val id = item.optString(1)
                val modelsArr = item.optJSONArray(3)
                if (modelsArr != null) brandModels[id] = modelsArr
                val als = item.optJSONArray(2)
                if (als != null) for (k in 0 until als.length()) {
                    val alias = als.optString(k)
                    if (alias.isEmpty()) continue
                    val latin = alias.contains(Regex("[A-Za-z]"))
                    brandAliases.add(arrayOf(alias.lowercase(Locale.US), name, id, if (latin) "1" else "0"))
                }
            }
        }
        brandAliases.sortByDescending { it[0].length }
        brandDataVersion = root.optInt("dataVersion", 0)
        brandDataVersionName = root.optString("dataVersionName", brandDataVersion.toString())
        return true
    }

    private fun fetchBrandJson(url: String, accept: String): String {
        val c = java.net.URL(url).openConnection() as java.net.HttpURLConnection
        c.connectTimeout = 8000
        c.readTimeout = 8000
        c.setRequestProperty("Accept", accept)
        c.setRequestProperty("User-Agent", "Xiaoman/$APP_VERSION_CODE")
        c.setRequestProperty("Cache-Control", "no-cache")
        c.setRequestProperty("Pragma", "no-cache")
        c.useCaches = false
        c.instanceFollowRedirects = true
        val code = c.responseCode
        if (code != 200) throw java.io.IOException("HTTP $code")
        val bos = java.io.ByteArrayOutputStream()
        c.inputStream.use { input ->
            val buf = ByteArray(8192)
            var n: Int
            while (input.read(buf).also { n = it } > 0) {
                bos.write(buf, 0, n)
                if (bos.size() > 2 * 1024 * 1024) throw java.io.IOException("too large")
            }
        }
        return String(bos.toByteArray(), java.nio.charset.StandardCharsets.UTF_8)
    }

    private fun checkBrandDataUpdate(manual: Boolean) {
        if (manual) toast("正在检查品牌数据更新…")
        Thread {
            try {
                var text: String? = null
                try {
                    text = fetchBrandJson(BRAND_DATA_API_URL, "application/vnd.github.raw")
                } catch (e: Exception) {
                    android.util.Log.w(BRAND_DATA_TAG, "api fetch failed: $e")
                }
                if (text == null) {
                    text = fetchBrandJson(BRAND_DATA_URL + "?t=" + System.currentTimeMillis(), "application/json")
                }
                val remote = JSONObject(text)
                val schema = remote.optInt("schema", 1)
                val sup = remote.optJSONObject("supportedApp")
                val lo = sup?.optInt("minVersionCode", 0) ?: 0
                val hi = sup?.optInt("maxVersionCode", 0) ?: 0
                android.util.Log.i(
                    BRAND_DATA_TAG,
                    "remote schema=$schema ver=${remote.optInt("dataVersion", 0)} app=$APP_VERSION_CODE supported=$lo-$hi"
                )
                if (schema > BRAND_SCHEMA || APP_VERSION_CODE < lo || (hi > 0 && APP_VERSION_CODE > hi)) {
                    if (manual) runOnUiThread { toast("当前版本不支持该数据") }
                    return@Thread
                }
                val ver = remote.optInt("dataVersion", 0)
                if (ver > brandDataVersion) {
                    brandPrefs.edit().putString("json", text).apply()
                    runOnUiThread {
                        if (applyBrandRoot(remote)) {
                            toast("品牌与型号已更新至 " + brandDataVersionName)
                            if (screen == "settings") showSettings()
                        }
                    }
                } else if (manual) {
                    runOnUiThread { toast("已是最新版本 v" + brandDataVersionName) }
                }
            } catch (e: Exception) {
                android.util.Log.w(BRAND_DATA_TAG, "update failed: $e")
                if (manual) runOnUiThread { toast("检查更新失败，请检查网络") }
            }
        }.start()
    }

    private fun applySystemBars() {
        val w = window
        w.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(ui.bg()))
        w.statusBarColor = ui.bg()
        w.navigationBarColor = ui.bg()
        var flags = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        if (Build.VERSION.SDK_INT >= 26) flags = flags or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        w.decorView.systemUiVisibility = if (dark) 0 else flags
        if (Build.VERSION.SDK_INT >= 29) {
            w.isStatusBarContrastEnforced = false
            w.isNavigationBarContrastEnforced = false
        }
    }

    companion object {
        @JvmField var dark = false

        private val APP_VERSION_CODE = BuildConfig.VERSION_CODE
        private const val BRAND_SCHEMA = 1
        private const val BRAND_DATA_API_URL =
            "https://api.github.com/repos/HanYunChenLuo/xiaoman/contents/data/brands.json"
        private const val BRAND_DATA_URL =
            "https://raw.githubusercontent.com/HanYunChenLuo/xiaoman/main/data/brands.json"
        private const val BRAND_DATA_TAG = "XiaomanBrandData"
        private const val REQ_RESTORE = 1001
        private const val REQ_EXPORT = 1002
        private const val REQ_COVER = 1003
        private const val REQ_NOTE = 1004

        @JvmField
        val MODEL_ICONS: MutableMap<String, Array<String>> = LinkedHashMap()

        private val OB = arrayOf(
            arrayOf("资产账户", "财务状况一目了然", "img/onboard1.png"),
            arrayOf("每日成本", "价值、成本、状态，一键分析", "img/onboard2.png"),
            arrayOf("数据统计", "多种统计图表", "img/onboard3.png")
        )

        init {
            MODEL_ICONS["apple/digital/Mobile-phone.png"] = arrayOf(
                "iphone", "galaxy", "pixel", "mate", "redmi", "ipad", "平板", "matepad", "手机", "小米",
                "oppo", "vivo", "一加", "oneplus", "荣耀", "honor", "magic"
            )
            MODEL_ICONS["apple/digital/Laptop.png"] = arrayOf(
                "macbook", "笔记本", "thinkpad", "小新", "matebook", "rog", "拯救者", "天选", "xps", "轻薄本", "游戏本"
            )
            MODEL_ICONS["apple/digital/Desktop-computer.png"] = arrayOf(
                "mac mini", "mac studio", "imac", "台式机", "台式", "主机", "组装机", "一体机"
            )
            MODEL_ICONS["apple/digital/Video-game.png"] = arrayOf(
                "switch", "ns2", "ns", "ps5", "ps4", "playstation", "xbox", "游戏机"
            )
            MODEL_ICONS["apple/digital/Movie-camera.png"] = arrayOf(
                "dji", "pocket", "osmo", "动作相机", "运动相机", "摄像机", "mavic", "无人机", "drone"
            )
            MODEL_ICONS["apple/digital/Camera.png"] = arrayOf("相机", "camera", "单反", "微单", "镜头", "gopro")
            MODEL_ICONS["apple/digital/Headphone.png"] = arrayOf("airpods", "耳机", "headphone", "音箱", "音响", "speaker")
            MODEL_ICONS["apple/digital/Watch.png"] = arrayOf("apple watch", "手表", "手环", "watch")
            MODEL_ICONS["apple/digital/Television.png"] = arrayOf("电视", "tv", "显示器", "monitor")
            MODEL_ICONS["apple/vehicle/Automobile.png"] = arrayOf(
                "汽车", "理想", "特斯拉", "比亚迪", "蔚来", "小鹏", "宝马", "奔驰", "奥迪", "大众",
                "丰田", "本田", "日产", "保时捷", "沃尔沃", "极星", "马自达"
            )
            MODEL_ICONS["apple/vehicle/Bicycle.png"] = arrayOf(
                "自行车", "公路车", "山地车", "单车", "bike", "giant", "捷安特", "trek"
            )
            MODEL_ICONS["apple/vehicle/Motorcycle.png"] = arrayOf("摩托", "motorcycle")
            MODEL_ICONS["apple/vehicle/Kick-scooter.png"] = arrayOf("滑板车", "电动滑板", "scooter")
            MODEL_ICONS["apple/vehicle/Airplane.png"] = arrayOf("飞机", "airplane")
        }
    }
}
