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
    private var quietDepth = 0

    init {
        root = JSONObject()
        load()
    }

    /**
     * 加载、迁移等不属于用户改动的写入不刷新修改时间。
     * 调用期间 `save()` 只落盘，不动 `settings.updatedAt`。
     */
    fun quiet(block: () -> Unit) {
        quietDepth++
        try {
            block()
        } finally {
            quietDepth--
        }
    }

    private fun load() = quiet {
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
        if (quietDepth == 0) touch()
        sp.edit().putString("data", root.toString()).apply()
    }

    /** 记录本地最后一次数据修改时间，用于和云端备份比较新旧。 */
    private fun touch() {
        try {
            settings().put("updatedAt", System.currentTimeMillis())
        } catch (_: Exception) {
        }
    }

    /** 本地最后一次修改时间（毫秒）；0 表示从未修改过。 */
    fun updatedAt(): Long = settings().optLong("updatedAt", 0L)

    fun hasInArray(key: String, id: String): Boolean {
        val a = arr(key)
        for (i in 0 until a.length()) {
            val o = a.optJSONObject(i)
            if (o != null && id == o.optString("id")) return true
        }
        return false
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

    /** 供 Rust 合并使用的本地快照（深拷贝，避免误改内存中的 store）。 */
    fun mergeSnapshot(): JSONObject {
        return JSONObject().apply {
            put("assets", copyArray("assets"))
            put("categories", copyArray("categories"))
            put("tags", copyArray("tags"))
            put("incomeCategories", copyArray("incomeCategories"))
            put("saveMoney", JSONArray(rawArray("rawSaveMoney")?.toString() ?: "[]"))
            put("settings", JSONObject(settings().toString()))
        }
    }

    /** 应用 Rust 合并结果：只替换数据数组，保留设置与其他根字段。 */
    fun applyMergedState(merged: JSONObject) {
        put("assets", merged.optJSONArray("assets") ?: JSONArray())
        put("categories", merged.optJSONArray("categories") ?: JSONArray())
        put("tags", merged.optJSONArray("tags") ?: JSONArray())
        put("incomeCategories", merged.optJSONArray("incomeCategories") ?: JSONArray())
        if (merged.has("saveMoney")) put("rawSaveMoney", merged.optJSONArray("saveMoney") ?: JSONArray())
        save()
    }

    private fun copyArray(key: String): JSONArray =
        try {
            JSONArray(arr(key).toString())
        } catch (_: Exception) {
            JSONArray()
        }

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

    fun ageDays(a: JSONObject): Int = NativeCore.ageDays(a.toString(), today())

    fun currentPrice(a: JSONObject): Double = a.optDouble("price", 0.0)

    fun usageCount(a: JSONObject): Int = NativeCore.usageCount(a.toString())

    fun netCost(a: JSONObject): Double = NativeCore.netCost(a.toString())

    fun dailyCost(a: JSONObject): Double = NativeCore.dailyCost(a.toString(), System.currentTimeMillis(), tzOffsetMs())

    fun ofKind(kind: String): List<JSONObject> {
        val out = ArrayList<JSONObject>()
        for (o in objs("assets")) if (kind == o.optString("kind", "asset")) out.add(o)
        return out
    }

    fun total(kind: String): Double = NativeCore.totalOfKind(arr("assets").toString(), kind)

    fun daily(kind: String): Double =
        NativeCore.dailyOfKind(arr("assets").toString(), kind, System.currentTimeMillis(), tzOffsetMs())

    fun count(kind: String, status: String): Int =
        NativeCore.countOfKind(arr("assets").toString(), kind, status)

    companion object {
        private const val PREFS = "xiaoman_v1"

        private fun tzOffsetMs(): Long =
            java.util.TimeZone.getDefault().getOffset(System.currentTimeMillis()).toLong()

        @JvmStatic
        fun today(): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

        @JvmStatic
        fun daysBetween(from: String, to: String): Int = NativeCore.daysBetween(from, to)

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
