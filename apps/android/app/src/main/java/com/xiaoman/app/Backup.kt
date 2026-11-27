package com.xiaoman.app

import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.HashMap
import java.util.Locale
import java.util.zip.CRC32
import java.util.zip.Deflater
import java.util.zip.Inflater
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/** 官方 .ias 备份：WinZip AES-256 加密的 zip，内含 data.json 与 images/。 */
object Backup {
    class Parsed(@JvmField var root: JSONObject, @JvmField val images: MutableMap<String, ByteArray>)

    // ---------------- 读取 ----------------
    @JvmStatic
    fun readIas(zip: ByteArray): Parsed {
        val obj = JSONObject(NativeCore.decodeIas(zip))
        val data = obj.opt("data") ?: throw Exception("data.json not found")
        val p = Parsed(JSONObject(data.toString()), HashMap())
        val images = obj.optJSONObject("images")
        if (images != null) {
            val keys = images.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                p.images[k] = android.util.Base64.decode(images.optString(k), android.util.Base64.DEFAULT)
            }
        }
        return p
    }

    // ---------------- 导入 ----------------
    /** 导入备份；`replace` 用备份替换本地，否则合并。合并时重复条目取较新一方（`preferIncoming`）。 */
    @JvmStatic
    fun importInto(
        store: Store,
        root: JSONObject,
        replace: Boolean,
        preferIncoming: Boolean,
        imagePaths: Map<String, String>?
    ): Int {
        if (!root.has("assets")) throw Exception("bad backup")
        if (replace) {
            store.putArray("assets", JSONArray())
            store.putArray("categories", JSONArray())
            store.putArray("tags", JSONArray())
        }
        val cats = root.optJSONArray("categories")
        if (cats != null) for (i in 0 until cats.length()) {
            val c = cats.optJSONObject(i)
            if (c == null || c.optBoolean("is_deleted")) continue
            val id = c.optString("id")
            if (!replace && store.hasInArray("categories", id)) continue
            store.upsertInArray("categories", JSONObject().put("id", id).put("name", c.optString("title")))
        }
        val tags = root.optJSONArray("tags")
        if (tags != null) for (i in 0 until tags.length()) {
            val t = tags.optJSONObject(i)
            if (t == null || t.optBoolean("is_deleted")) continue
            val id = t.optString("id")
            if (!replace && store.hasInArray("tags", id)) continue
            store.upsertInArray("tags", JSONObject().put("id", id).put("name", t.optString("title")))
        }
        val incCats = root.optJSONArray("income_categories")
        if (incCats != null) for (i in 0 until incCats.length()) {
            val c = incCats.optJSONObject(i)
            if (c == null || c.optBoolean("is_deleted")) continue
            val id = c.optString("id")
            if (!replace && store.hasInArray("incomeCategories", id)) continue
            store.upsertInArray("incomeCategories", JSONObject().put("id", id).put("name", c.optString("title")))
        }

        var count = 0
        val assets = root.optJSONArray("assets")
        val byId = HashMap<String, JSONObject>()
        val imported = HashSet<String>()
        for (i in 0 until (assets?.length() ?: 0)) {
            val a = assets!!.optJSONObject(i) ?: continue
            if (a.optBoolean("is_deleted")) continue
            val type = a.optInt("type", 0)
            val item = JSONObject()
            item.put("id", a.optString("id"))
            // 合并时重复条目取较新一方；本地较新就整条保留（连同其收入/使用记录）。
            if (!replace && !preferIncoming && store.findAsset(item.optString("id")) != null) continue
            item.put("name", a.optString("name"))
            item.put("model", a.optString("model"))
            var price = a.optDouble("price", 0.0)
            if (price == 0.0) price = a.optDouble("target_cost", 0.0)
            item.put("price", price)
            item.put("buyDate", date(a.optLong("buy_date", 0)))
            item.put("kind", if (type == 1) "wish" else "asset")
            if (a.optBoolean("is_sell")) {
                item.put("status", "sold")
                item.put("soldDate", date(a.optLong("sell_date", 0)))
            } else if (a.optBoolean("is_retirement")) {
                item.put("status", "retired")
            } else item.put("status", "serving")
            item.put("categoryId", a.optString("category_id"))
            val tids = a.optJSONArray("tag_ids")
            item.put("tagIds", tids ?: JSONArray())
            item.put("rule", "day")
            item.put("icon", "emoji/icon_default_object.png")
            item.put("favorite", a.optBoolean("is_top", false))
            item.put("note", a.optString("note"))
            item.put("soldPrice", a.optDouble("used_price", 0.0))
            item.put("secondhandValue", a.optDouble("secondhand_value", 0.0))
            item.put("secondhandInCost", a.optBoolean("secondhand_in_cost", true))
            item.put("brandId", a.optString("brand_id"))
            item.put("brandName", a.optString("brand_name"))
            item.put("excludeTotal", a.optBoolean("is_not_include_total") || a.optBoolean("is_excluded_from_total"))
            if (a.optLong("retirement_date", 0) > 0) item.put("retireDate", date(a.optLong("retirement_date")))
            if (type == 1) {
                item.put("targetCost", a.optDouble("target_cost", 0.0))
                if (a.optLong("target_date", 0) > 0) item.put("targetDate", date(a.optLong("target_date")))
            }
            if (a.optInt("target_usage_days", 0) > 0) item.put("targetUsageDays", a.optInt("target_usage_days"))
            val cover = a.optString("cover_image")
            if (cover.isNotEmpty() && imagePaths != null && imagePaths.containsKey(cover))
                item.put("coverPath", imagePaths[cover])
            val ni = a.optJSONArray("note_images")
            if (ni != null && ni.length() > 0 && imagePaths != null) {
                val local = JSONArray()
                for (k in 0 until ni.length()) {
                    val nm = ni.optString(k)
                    if (imagePaths.containsKey(nm)) local.put(imagePaths[nm])
                }
                if (local.length() > 0) item.put("noteImages", local)
            }
            store.upsertAsset(item)
            byId[item.optString("id")] = item
            imported.add(item.optString("id"))
            count++
        }

        val imageRecords = root.optJSONArray("asset_images")
        if (imageRecords != null) for (i in 0 until imageRecords.length()) {
            val r = imageRecords.optJSONObject(i) ?: continue
            if (r.optBoolean("is_deleted")) continue
            val asset = byId[r.optString("asset_id", r.optString("assetId"))] ?: continue
            val name = r.optString("image_name", r.optString("file_name", r.optString("name")))
            if (name.isEmpty() || imagePaths == null) continue
            var local = imagePaths[name]
            if (local == null) local = imagePaths["images/$name"]
            if (local == null) continue
            val type = r.optString("type", r.optString("image_type"))
            val cover = r.optInt("is_cover", -1) == 1 || "cover" == type || "0" == type
            if (cover) {
                if (asset.optString("coverPath").isEmpty()) asset.put("coverPath", local)
            } else {
                var list = asset.optJSONArray("noteImages")
                if (list == null) {
                    list = JSONArray()
                    asset.put("noteImages", list)
                }
                var exists = false
                for (k in 0 until list.length()) if (local == list.optString(k)) exists = true
                if (!exists) list.put(local)
            }
        }

        val records = root.optJSONArray("income_records")
        if (records != null) for (i in 0 until records.length()) {
            val r = records.optJSONObject(i) ?: continue
            if (r.optBoolean("is_deleted")) continue
            val aid = r.optString("asset_id", r.optString("assetId"))
            if (aid !in imported) continue
            val asset = store.findAsset(aid) ?: continue
            var list = asset.optJSONArray("incomeRecords")
            if (list == null) {
                list = JSONArray()
                asset.put("incomeRecords", list)
            }
            val sec = r.optLong("income_date", r.optLong("date", 0))
            list.put(
                JSONObject().put("id", r.optString("id"))
                    .put("amount", r.optDouble("amount", 0.0))
                    .put("date", if (sec > 0) date(sec) else r.optString("income_date", r.optString("date")))
                    .put("categoryId", r.optString("category_id", r.optString("categoryId")))
                    .put("note", r.optString("note"))
            )
        }
        val usages = root.optJSONArray("usages")
        if (usages != null) for (i in 0 until usages.length()) {
            val r = usages.optJSONObject(i) ?: continue
            if (r.optBoolean("is_deleted")) continue
            val aid = r.optString("asset_id", r.optString("assetId"))
            if (aid !in imported) continue
            val asset = store.findAsset(aid) ?: continue
            var list = asset.optJSONArray("usages")
            if (list == null) {
                list = JSONArray()
                asset.put("usages", list)
            }
            val sec = r.optLong("usage_date", r.optLong("date", 0))
            list.put(
                JSONObject().put("id", r.optString("id"))
                    .put("date", if (sec > 0) date(sec) else r.optString("usage_date", r.optString("date")))
                    .put("note", r.optString("note"))
            )
        }
        val prices = root.optJSONArray("asset_price_records")
        if (prices != null) for (i in 0 until prices.length()) {
            val r = prices.optJSONObject(i) ?: continue
            if (r.optBoolean("is_deleted")) continue
            val aid = r.optString("asset_id", r.optString("assetId"))
            if (aid !in imported) continue
            val asset = store.findAsset(aid) ?: continue
            var list = asset.optJSONArray("priceRecords")
            if (list == null) {
                list = JSONArray()
                asset.put("priceRecords", list)
            }
            val sec = r.optLong("record_date", r.optLong("date", r.optLong("created_at", 0)))
            list.put(
                JSONObject().put("id", r.optString("id"))
                    .put("date", if (sec > 0) date(sec) else r.optString("record_date", r.optString("date")))
                    .put("price", r.optDouble("price", 0.0))
            )
        }
        val reminders = root.optJSONArray("expire_reminders")
        if (reminders != null) for (i in 0 until reminders.length()) {
            val r = reminders.optJSONObject(i) ?: continue
            if (r.optBoolean("is_deleted")) continue
            val aid = r.optString("asset_id", r.optString("assetId")).ifEmpty { r.optString("id") }
            if (aid !in imported) continue
            val asset = store.findAsset(aid) ?: continue
            val sec = r.optLong("expiry_at", 0)
            if (sec > 0) asset.put("expireAt", date(sec))
            val remind = r.optInt("expiry_remind", 0)
            if (remind > 0) asset.put("remindDays", remind)
            val unit = r.optString("remind_time_unit", "day")
            asset.put("remindUnit", if (unit.isEmpty()) "day" else unit)
            val remindAt = r.optLong("remind_at", 0)
            if (remindAt > 0) {
                val rc = Calendar.getInstance()
                rc.timeInMillis = remindAt * 1000L
                asset.put("remindHour", rc.get(Calendar.HOUR_OF_DAY))
                asset.put("remindMinute", rc.get(Calendar.MINUTE))
            }
        }
        val saveMoney = root.optJSONArray("save_money")
        val localSaveMoney = store.rawArray("rawSaveMoney")
        val localSaveMoneyEmpty = localSaveMoney == null || localSaveMoney.length() == 0
        if (saveMoney != null && (replace || (saveMoney.length() > 0 && localSaveMoneyEmpty))) {
            store.setRawArray("rawSaveMoney", saveMoney)
        }

        store.save()
        return count
    }

    /** 备份本身的数据修改时间（毫秒）：优先 settings.updatedAt，旧备份回退 exported_at。 */
    @JvmStatic
    fun backupTimeMs(root: JSONObject): Long {
        val own = root.optJSONObject("settings")?.optLong("updatedAt", 0L) ?: 0L
        if (own > 0) return own
        val exported = root.optLong("exported_at", 0L)
        return if (exported > 0) exported * 1000L else 0L
    }

    private fun date(seconds: Long): String {
        if (seconds <= 0) return Store.today()
        val f = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        return f.format(Date(seconds * 1000L))
    }

    // ---------------- 导出 ----------------
    @JvmStatic
    fun writeIas(store: Store): ByteArray {
        val images = LinkedHashMap<String, ByteArray>()
        val imageRecords = ArrayList<JSONObject>()

        val root = JSONObject()
        root.put("categories", categories(store))
        root.put("tags", tags(store))
        root.put("assets", assets(store, images, imageRecords))
        root.put("asset_images", JSONArray(imageRecords))
        root.put("usages", usages(store))
        root.put("income_categories", incomeCategories(store))
        root.put("income_records", incomeRecords(store))
        root.put("asset_price_records", priceRecords(store))
        val smRaw = store.rawArray("rawSaveMoney")
        root.put("save_money", smRaw ?: JSONArray())
        root.put("expire_reminders", reminders(store))
        root.put("platform", "AOS")
        root.put("version", "1.0.0")
        root.put("exported_at", System.currentTimeMillis() / 1000)
        root.put("settings", JSONObject().put("updatedAt", store.updatedAt()))

        val imageArr = JSONArray()
        for ((name, bytes) in images) {
            imageArr.put(
                JSONObject().put("name", name)
                    .put("data", android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP))
            )
        }
        val payload = JSONObject().put("data", root).put("images", imageArr).toString()
        return NativeCore.encodeIas(payload)
    }

    private fun categories(store: Store): JSONArray {
        val out = JSONArray()
        var sort = 0
        for (c in store.objs("categories")) {
            out.put(
                JSONObject().put("id", c.optString("id")).put("title", c.optString("name"))
                    .put("sort", sort++).put("is_deleted", false).put("created_at", 0).put("update_at", 0)
            )
        }
        return out
    }

    private fun tags(store: Store): JSONArray {
        val out = JSONArray()
        var sort = 0
        for (t in store.objs("tags")) {
            out.put(
                JSONObject().put("id", t.optString("id")).put("title", t.optString("name"))
                    .put("sort", sort++).put("is_deleted", false).put("created_at", 0).put("update_at", 0)
            )
        }
        return out
    }

    private fun incomeCategories(store: Store): JSONArray {
        val out = JSONArray()
        var sort = 0
        for (c in store.objs("incomeCategories")) {
            out.put(
                JSONObject().put("id", c.optString("id")).put("title", c.optString("name"))
                    .put("icon_name", "icon_income_1").put("color_hex", "#A3E635")
                    .put("sort", sort++).put("is_deleted", false).put("created_at", 0).put("update_at", 0)
            )
        }
        return out
    }

    private fun incomeRecords(store: Store): JSONArray {
        val out = JSONArray()
        for (a in store.objs("assets")) {
            val list = a.optJSONArray("incomeRecords") ?: continue
            for (i in 0 until list.length()) {
                val r = list.optJSONObject(i) ?: continue
                out.put(
                    JSONObject().put("id", r.optString("id"))
                        .put("asset_id", a.optString("id"))
                        .put("category_id", r.optString("categoryId"))
                        .put("amount", r.optDouble("amount", 0.0))
                        .put("income_date", epoch(r.optString("date")))
                        .put("note", r.optString("note"))
                        .put("is_deleted", false).put("created_at", 0).put("update_at", 0)
                )
            }
        }
        return out
    }

    private fun usages(store: Store): JSONArray {
        val out = JSONArray()
        for (a in store.objs("assets")) {
            val list = a.optJSONArray("usages") ?: continue
            for (i in 0 until list.length()) {
                val r = list.optJSONObject(i) ?: continue
                out.put(
                    JSONObject().put("id", r.optString("id"))
                        .put("asset_id", a.optString("id"))
                        .put("usage_date", epoch(r.optString("date")))
                        .put("note", r.optString("note"))
                        .put("is_deleted", false).put("created_at", 0).put("update_at", 0)
                )
            }
        }
        return out
    }

    private fun priceRecords(store: Store): JSONArray {
        val out = JSONArray()
        for (a in store.objs("assets")) {
            val list = a.optJSONArray("priceRecords") ?: continue
            for (i in 0 until list.length()) {
                val r = list.optJSONObject(i) ?: continue
                out.put(
                    JSONObject().put("id", r.optString("id"))
                        .put("asset_id", a.optString("id"))
                        .put("price", r.optDouble("price", 0.0))
                        .put("record_date", epoch(r.optString("date")))
                        .put("is_deleted", false).put("created_at", 0).put("update_at", 0)
                )
            }
        }
        return out
    }

    private fun reminders(store: Store): JSONArray {
        val out = JSONArray()
        for (a in store.objs("assets")) {
            val expire = a.optString("expireAt")
            if (expire.isEmpty()) continue
            val days = a.optInt("remindDays", 0)
            val expiry = epoch(expire)
            val unit = a.optString("remindUnit", "day")
            val unitSec = if ("week" == unit) 7 * 86400L else if ("month" == unit) 30 * 86400L else 86400L
            val remindAt = expiry - days * unitSec + a.optInt("remindHour", 9) * 3600L + a.optInt("remindMinute", 0) * 60L
            out.put(
                JSONObject().put("id", a.optString("id"))
                    .put("asset_id", a.optString("id"))
                    .put("expiry_at", expiry)
                    .put("expiry_remind", days)
                    .put("remind_time_unit", unit)
                    .put("remind_at", remindAt)
                    .put("is_deleted", false).put("created_at", 0).put("update_at", 0)
            )
        }
        return out
    }

    private fun assets(store: Store, imagesOut: MutableMap<String, ByteArray>, imageRecords: MutableList<JSONObject>): JSONArray {
        val out = JSONArray()
        var sort = 0
        var imgId = 0
        for (a in store.objs("assets")) {
            val o = JSONObject()
            o.put("id", a.optString("id"))
            o.put("name", a.optString("name"))
            o.put("model", a.optString("model"))
            o.put("icon_name", "icon_default_object")
            var coverName = ""
            val coverPath = a.optString("coverPath")
            if (coverPath.isNotEmpty()) {
                val f = File(coverPath)
                if (f.exists()) {
                    coverName = "images/" + a.optString("id") + "_cover" + ext(coverPath)
                    imagesOut[coverName] = readFile(f)
                    imageRecords.add(
                        JSONObject().put("id", "img_" + imgId++)
                            .put("asset_id", a.optString("id")).put("image_name", coverName)
                            .put("type", "cover").put("sort", 0).put("is_deleted", false)
                            .put("created_at", 0).put("update_at", 0)
                    )
                }
            }
            o.put("cover_image", coverName)
            val noteNames = JSONArray()
            val noteImgs = a.optJSONArray("noteImages")
            for (i in 0 until (noteImgs?.length() ?: 0)) {
                val p = noteImgs!!.optString(i)
                val f = File(p)
                if (!f.exists()) continue
                val nm = "images/" + a.optString("id") + "_note" + i + ext(p)
                imagesOut[nm] = readFile(f)
                noteNames.put(nm)
                imageRecords.add(
                    JSONObject().put("id", "img_" + imgId++)
                        .put("asset_id", a.optString("id")).put("image_name", nm)
                        .put("type", "note").put("sort", i).put("is_deleted", false)
                        .put("created_at", 0).put("update_at", 0)
                )
            }
            o.put("price", a.optDouble("price", 0.0))
            o.put("type", if (Store.isWish(a)) 1 else 0)
            o.put("category_id", a.optString("categoryId"))
            o.put("state", 0)
            o.put("buy_date", epoch(a.optString("buyDate")))
            val tids = a.optJSONArray("tagIds")
            o.put("tag_ids", tids ?: JSONArray())
            o.put("target_type", 0)
            o.put("target_cost_unit", "")
            o.put("target_each_increment", 0)
            o.put("target_date", epoch(a.optString("targetDate")))
            o.put("target_usage_days", a.optInt("targetUsageDays", 0))
            o.put("target_cost", a.optDouble("targetCost", 0.0))
            o.put("target_value", 0)
            o.put("additional_item_ids", JSONArray())
            o.put("additional_calculation_mode", 0)
            o.put("note", a.optString("note"))
            o.put("note_images", noteNames)
            o.put("is_top", a.optBoolean("favorite", false))
            o.put("is_retirement", "retired" == a.optString("status"))
            o.put("retirement_date", epoch(a.optString("retireDate")))
            o.put("is_sell", "sold" == a.optString("status"))
            o.put("used_price", a.optDouble("soldPrice", 0.0))
            o.put("secondhand_value", a.optDouble("secondhandValue", 0.0))
            o.put("secondhand_in_cost", a.optBoolean("secondhandInCost", true))
            o.put("brand_id", a.optString("brandId"))
            o.put("brand_name", a.optString("brandName"))
            o.put("sell_date", epoch(a.optString("soldDate")))
            o.put("wish_date", epoch(a.optString("targetDate")))
            o.put("is_not_include_total", a.optBoolean("excludeTotal"))
            o.put("is_excluded_from_total", a.optBoolean("excludeTotal"))
            o.put("sort", sort++)
            o.put("is_deleted", false)
            o.put("created_at", 0)
            o.put("update_at", 0)
            out.put(o)
        }
        return out
    }

    private fun ext(path: String): String {
        val i = path.lastIndexOf('.')
        val e = if (i >= 0) path.substring(i).lowercase(Locale.US) else ".jpg"
        return if (e.length > 5) ".jpg" else e
    }

    private fun readFile(f: File): ByteArray {
        FileInputStream(f).use { input ->
            ByteArrayOutputStream().use { out ->
                val buf = ByteArray(8192)
                while (true) {
                    val n = input.read(buf)
                    if (n <= 0) break
                    out.write(buf, 0, n)
                }
                return out.toByteArray()
            }
        }
    }

    private fun epoch(date: String): Long {
        return try {
            SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(date).time / 1000
        } catch (_: Exception) {
            0
        }
    }

}
