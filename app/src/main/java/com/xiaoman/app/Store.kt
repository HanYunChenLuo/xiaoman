package com.xiaoman.app

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.ArrayList
import java.util.Date
import java.util.Locale

/** 本地数据：资产、分类、标签、设置，SharedPreferences 里存一份 JSON。 */
class Store(c: Context) {
    private val sp: SharedPreferences = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private var root: JSONObject

    init {
        root = JSONObject()
        load()
    }

    private fun load() {
        root = try {
            JSONObject(sp.getString("data", "{}"))
        } catch (_: Exception) {
            JSONObject()
        }
        if (!root.has("assets")) put("assets", JSONArray())
        if (!root.has("categories")) put("categories", JSONArray())
        if (!root.has("tags")) put("tags", JSONArray())
        if (!root.has("settings")) put("settings", JSONObject())
        if (arr("incomeCategories").length() == 0) {
            try {
                val seed = arrayOf("出租", "工资", "理财", "其他")
                for (i in seed.indices) {
                    arr("incomeCategories").put(JSONObject().put("id", "inc_cat_$i").put("name", seed[i]))
                }
            } catch (_: Exception) {
            }
        }
        val s = settings()
        if (!s.has("currency")) setSetting("currency", "CNY")
        if (!s.has("decimals")) setSetting("decimals", 2)
        if (!s.has("thousands")) setSetting("thousands", true)
        if (!s.has("duration")) setSetting("duration", "days")
        if (!s.has("theme")) setSetting("theme", "system")
        if (!s.has("homeStyle")) setSetting("homeStyle", "default")
        if (!s.has("view")) setSetting("view", "grid")
        for (a in objs("assets")) {
            val icon = a.optString("icon")
            if (icon.isEmpty() || "emoji/app_icon_v1.png" == icon || "img/asset_default.png" == icon) {
                try {
                    a.put("icon", "emoji/icon_default_object.png")
                } catch (_: Exception) {
                }
            }
        }
        save()
    }

    fun save() {
        sp.edit().putString("data", root.toString()).apply()
    }

    fun putArray(key: String, arr: JSONArray) {
        put(key, arr)
        save()
    }

    fun upsertInArray(key: String, item: JSONObject) {
        val a = arr(key)
        val id = item.optString("id")
        for (i in 0 until a.length()) {
            val o = a.optJSONObject(i)
            if (o != null && id == o.optString("id")) {
                try {
                    a.put(i, item)
                } catch (_: Exception) {
                }
                return
            }
        }
        a.put(item)
    }

    private fun put(k: String, v: Any) {
        try {
            root.put(k, v)
        } catch (_: Exception) {
        }
    }

    fun arr(key: String): JSONArray {
        var a = root.optJSONArray(key)
        if (a == null) {
            a = JSONArray()
            put(key, a)
        }
        return a
    }

    fun objs(key: String): List<JSONObject> {
        val a = arr(key)
        val out = ArrayList<JSONObject>()
        for (i in 0 until a.length()) {
            val o = a.optJSONObject(i)
            if (o != null) out.add(o)
        }
        return out
    }

    fun removeAsset(id: String) {
        val a = arr("assets")
        for (i in a.length() - 1 downTo 0) {
            val o = a.optJSONObject(i)
            if (o != null && id == o.optString("id")) a.remove(i)
        }
        save()
    }

    fun upsertAsset(asset: JSONObject) {
        val a = arr("assets")
        val id = asset.optString("id")
        for (i in 0 until a.length()) {
            val o = a.optJSONObject(i)
            if (o != null && id == o.optString("id")) {
                try {
                    a.put(i, asset)
                } catch (_: Exception) {
                }
                save()
                return
            }
        }
        a.put(asset)
        save()
    }

    fun findAsset(id: String): JSONObject? {
        for (o in objs("assets")) if (id == o.optString("id")) return o
        return null
    }

    // ---------- 设置 ----------
    fun settings(): JSONObject {
        var s = root.optJSONObject("settings")
        if (s == null) {
            s = JSONObject()
            put("settings", s)
        }
        return s
    }

    fun setting(k: String, def: String): String = settings().optString(k, def)
    fun settingInt(k: String, def: Int): Int = settings().optInt(k, def)
    fun settingBool(k: String, def: Boolean): Boolean = settings().optBoolean(k, def)

    fun setSetting(k: String, v: Any) {
        try {
            settings().put(k, v)
        } catch (_: Exception) {
        }
        save()
    }

    fun str(k: String, def: String): String = root.optString(k, def)
    fun setStr(k: String, v: String) {
        put(k, v)
        save()
    }

    fun bool(k: String, def: Boolean): Boolean = root.optBoolean(k, def)
    fun setBool(k: String, v: Boolean) {
        put(k, v)
        save()
    }

    fun setSystemDark(d: Boolean) {
        sp.edit().putInt("sys_dark", if (d) 1 else 0).apply()
    }

    fun isDark(): Boolean {
        val t = setting("theme", "system")
        if ("dark" == t) return true
        if ("light" == t) return false
        return sp.getInt("sys_dark", 0) == 1
    }

    fun rawArray(k: String): JSONArray? = root.optJSONArray(k)

    fun setRawArray(k: String, a: JSONArray) {
        put(k, a)
        save()
    }

    fun export(): String = root.toString()

    fun clearAll() {
        try {
            root.put("assets", JSONArray())
            root.put("categories", JSONArray())
            root.put("tags", JSONArray())
            root.put("settings", JSONObject())
            root.put("pro", false)
            root.put("onboarded", true)
            root.put("ratingShown", false)
        } catch (_: Exception) {
        }
        save()
    }

    // ---------- 金额与日期 ----------
    fun sym(): String = when (setting("currency", "CNY")) {
        "USD" -> "$"
        "EUR" -> "€"
        "JPY" -> "¥"
        "GBP" -> "£"
        "HKD" -> "HK$"
        else -> "¥"
    }

    fun money(v: Double): String = sym() + fmt(v, settingInt("decimals", 2))

    fun fmt(v: Double, digits: Int): String {
        val df = DecimalFormat()
        df.minimumFractionDigits = digits
        df.maximumFractionDigits = digits
        df.isGroupingUsed = settingBool("thousands", true)
        return df.format(v)
    }

    fun ageDays(a: JSONObject): Int {
        if ("sold" == a.optString("status") && a.optString("soldDate").isNotEmpty()) {
            return Math.max(1, daysBetween(a.optString("buyDate"), a.optString("soldDate")))
        }
        return Math.max(1, daysBetween(a.optString("buyDate"), today()))
    }

    fun currentPrice(a: JSONObject): Double = a.optDouble("price", 0.0)

    fun usageCount(a: JSONObject): Int {
        val list = a.optJSONArray("usages")
        if (list != null && list.length() > 0) return list.length()
        return Math.max(1.0, a.optDouble("uses", 1.0)).toInt()
    }

    fun netCost(a: JSONObject): Double {
        val price = currentPrice(a)
        val residual: Double = if ("sold" == a.optString("status")) {
            a.optDouble("soldPrice", 0.0)
        } else {
            if (a.optBoolean("secondhandInCost", true)) a.optDouble("secondhandValue", 0.0) else 0.0
        }
        return Math.max(0.0, price - Math.max(0.0, residual))
    }

    fun dailyCost(a: JSONObject): Double {
        val price = netCost(a)
        val rule = a.optString("rule", "day")
        if ("count" == rule) return price / usageCount(a)
        if ("custom" == rule) return price / Math.max(1.0, a.optDouble("customDays", 1.0))
        return try {
            val f = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            val buy = f.parse(a.optString("buyDate"))
            var end = System.currentTimeMillis()
            if ("sold" == a.optString("status") && a.optString("soldDate").isNotEmpty()) {
                val sold = f.parse(a.optString("soldDate"))
                if (sold != null) end = sold.time
            }
            val days = (end - (buy?.time ?: end)) / 86400000.0
            price / Math.max(1e-6, days)
        } catch (_: Exception) {
            price / ageDays(a)
        }
    }

    fun ofKind(kind: String): List<JSONObject> {
        val out = ArrayList<JSONObject>()
        for (o in objs("assets")) if (kind == o.optString("kind", "asset")) out.add(o)
        return out
    }

    fun total(kind: String): Double {
        var t = 0.0
        for (o in ofKind(kind)) {
            if ("sold" != o.optString("status") && !o.optBoolean("excludeTotal")) t += currentPrice(o)
        }
        return t
    }

    fun daily(kind: String): Double {
        var t = 0.0
        for (o in ofKind(kind)) if ("serving" == o.optString("status", "serving")) t += dailyCost(o)
        return t
    }

    fun count(kind: String, status: String): Int {
        var n = 0
        for (o in ofKind(kind)) if (status == o.optString("status", "serving")) n++
        return n
    }

    companion object {
        private const val PREFS = "xiaoman_v1"

        @JvmStatic
        fun today(): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

        @JvmStatic
        fun daysBetween(from: String, to: String): Int = try {
            val f = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            val a = f.parse(from)
            val b = f.parse(to)
            if (a == null || b == null) 1 else Math.floor((b.time - a.time) / 86400000.0).toInt() + 1
        } catch (_: Exception) {
            1
        }

        @JvmStatic
        fun statusText(s: String): String {
            if ("retired" == s) return "已退役"
            if ("sold" == s) return "已卖出"
            return "服役中"
        }

        @JvmStatic
        fun isWish(a: JSONObject): Boolean = "wish" == a.optString("kind")
    }
}
