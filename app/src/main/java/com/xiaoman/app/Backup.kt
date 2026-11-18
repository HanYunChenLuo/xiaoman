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
    private val PASSWORD: ByteArray = "iAsset#Support@2025!".toByteArray(StandardCharsets.UTF_8)
    private const val ITER = 1000

    class Parsed(@JvmField var root: JSONObject, @JvmField val images: MutableMap<String, ByteArray>)

    private class Entry(val name: String, val data: ByteArray, val method: Int)

    // ---------------- 读取 ----------------
    @JvmStatic
    fun readIas(zip: ByteArray): Parsed {
        val entries = readEntries(zip)
        val json = entries["data.json"] ?: throw Exception("data.json not found")
        val p = Parsed(JSONObject(String(json, StandardCharsets.UTF_8)), HashMap())
        for ((k, v) in entries) if (k.startsWith("images/")) p.images[k] = v
        return p
    }

    private fun readEntries(zip: ByteArray): Map<String, ByteArray> {
        val out = HashMap<String, ByteArray>()
        var off = 0
        while (off + 30 <= zip.size) {
            if (u32(zip, off) != 0x04034b50L) break
            val flags = u16(zip, off + 6)
            val method = u16(zip, off + 8)
            val csize = u32(zip, off + 18)
            val nlen = u16(zip, off + 26)
            val xlen = u16(zip, off + 28)
            val name = String(zip, off + 30, nlen, StandardCharsets.UTF_8)
            val extraStart = off + 30 + nlen
            val dataStart = extraStart + xlen
            if (method == 99 && csize > 28) {
                val actual = aesMethod(zip, extraStart, xlen)
                val payload = zip.copyOfRange(dataStart, dataStart + csize.toInt())
                val comp = aesDecrypt(payload)
                out[name] = if (actual == 8) inflateBytes(comp) else comp
            } else if ((method == 0 || method == 8) && csize > 0) {
                val payload = zip.copyOfRange(dataStart, dataStart + csize.toInt())
                out[name] = if (method == 8) inflateBytes(payload) else payload
            }
            if ((flags and 8) != 0 && csize == 0L) break
            off = dataStart + csize.toInt()
        }
        return out
    }

    private fun aesMethod(zip: ByteArray, extraStart: Int, xlen: Int): Int {
        var p = extraStart
        val end = extraStart + xlen
        while (p + 4 <= end) {
            val id = u16(zip, p)
            val sz = u16(zip, p + 2)
            if (id == 0x9901 && sz >= 7) return u16(zip, p + 4 + 5)
            p += 4 + sz
        }
        return 8
    }

    private fun aesDecrypt(entry: ByteArray): ByteArray {
        val salt = entry.copyOfRange(0, 16)
        val pvv = entry.copyOfRange(16, 18)
        val cipher = entry.copyOfRange(18, entry.size - 10)
        val auth = entry.copyOfRange(entry.size - 10, entry.size)
        val dk = pbkdf2(salt, 66)
        if (dk[64] != pvv[0] || dk[65] != pvv[1]) throw Exception("bad password")
        val mac = Mac.getInstance("HmacSHA1")
        mac.init(SecretKeySpec(dk.copyOfRange(32, 64), "HmacSHA1"))
        val digest = mac.doFinal(cipher)
        for (i in 0 until 10) if (digest[i] != auth[i]) throw Exception("auth failed")
        return ctr(dk.copyOfRange(0, 32), cipher)
    }

    private fun ctr(key: ByteArray, data: ByteArray): ByteArray {
        val ecb = Cipher.getInstance("AES/ECB/NoPadding")
        ecb.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"))
        val out = ByteArray(data.size)
        val counter = ByteArray(16)
        counter[0] = 1
        val ks = ByteArray(16)
        var i = 0
        while (i < data.size) {
            ecb.doFinal(counter, 0, 16, ks, 0)
            val n = Math.min(16, data.size - i)
            for (j in 0 until n) out[i + j] = (data[i + j].toInt() xor ks[j].toInt()).toByte()
            for (j in 0 until 16) {
                counter[j] = (counter[j] + 1).toByte()
                if (counter[j].toInt() != 0) break
            }
            i += 16
        }
        return out
    }

    private fun pbkdf2(salt: ByteArray, len: Int): ByteArray {
        val f = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1")
        return f.generateSecret(
            PBEKeySpec(String(PASSWORD, StandardCharsets.UTF_8).toCharArray(), salt, ITER, len * 8)
        ).encoded
    }

    // ---------------- 导入 ----------------
    @JvmStatic
    fun importInto(store: Store, root: JSONObject, replace: Boolean, imagePaths: Map<String, String>?): Int {
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
            store.upsertInArray("categories", JSONObject().put("id", c.optString("id")).put("name", c.optString("title")))
        }
        val tags = root.optJSONArray("tags")
        if (tags != null) for (i in 0 until tags.length()) {
            val t = tags.optJSONObject(i)
            if (t == null || t.optBoolean("is_deleted")) continue
            store.upsertInArray("tags", JSONObject().put("id", t.optString("id")).put("name", t.optString("title")))
        }
        val incCats = root.optJSONArray("income_categories")
        if (incCats != null) for (i in 0 until incCats.length()) {
            val c = incCats.optJSONObject(i)
            if (c == null || c.optBoolean("is_deleted")) continue
            store.upsertInArray("incomeCategories", JSONObject().put("id", c.optString("id")).put("name", c.optString("title")))
        }

        var count = 0
        val assets = root.optJSONArray("assets")
        val byId = HashMap<String, JSONObject>()
        for (i in 0 until (assets?.length() ?: 0)) {
            val a = assets!!.optJSONObject(i) ?: continue
            if (a.optBoolean("is_deleted")) continue
            val type = a.optInt("type", 0)
            val item = JSONObject()
            item.put("id", a.optString("id"))
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
            val asset = store.findAsset(r.optString("asset_id", r.optString("assetId"))) ?: continue
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
            val asset = store.findAsset(r.optString("asset_id", r.optString("assetId"))) ?: continue
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
            val asset = store.findAsset(r.optString("asset_id", r.optString("assetId"))) ?: continue
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
            val aid = r.optString("asset_id", r.optString("assetId"))
            val asset = if (aid.isEmpty()) store.findAsset(r.optString("id")) else store.findAsset(aid)
            if (asset == null) continue
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
        if (saveMoney != null) store.setRawArray("rawSaveMoney", saveMoney)

        store.save()
        return count
    }

    private fun date(seconds: Long): String {
        if (seconds <= 0) return Store.today()
        val f = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        return f.format(Date(seconds * 1000L))
    }

    // ---------------- 导出 ----------------
    @JvmStatic
    fun writeIas(store: Store): ByteArray {
        val entryFiles = ArrayList<Entry>()
        val imageRecords = ArrayList<JSONObject>()

        val root = JSONObject()
        root.put("categories", categories(store))
        root.put("tags", tags(store))
        root.put("assets", assets(store, entryFiles, imageRecords))
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

        val entries = ArrayList<Entry>()
        entries.add(Entry("data.json", root.toString().toByteArray(StandardCharsets.UTF_8), 8))
        entries.addAll(entryFiles)
        return zipEncrypted(entries)
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

    private fun assets(store: Store, entryFiles: MutableList<Entry>, imageRecords: MutableList<JSONObject>): JSONArray {
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
                    entryFiles.add(Entry(coverName, readFile(f), 0))
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
                entryFiles.add(Entry(nm, readFile(f), 0))
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
            o.put("is_top", false)
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

    // ---------------- zip 写入（多条目，AES-256） ----------------
    private class ZipEntry {
        var name: String = ""
        var payload: ByteArray = ByteArray(0)
        var crc: Long = 0
        var usize: Long = 0
        var offset: Long = 0
        var time: Int = 0
        var date: Int = 0
        var extra: ByteArray = ByteArray(0)
    }

    private fun zipEncrypted(entries: List<Entry>): ByteArray {
        val zip = ByteArrayOutputStream()
        val meta = ArrayList<ZipEntry>()
        val time = dosTime()
        val date = dosDate()

        for (e in entries) {
            val name = e.name
            val data = e.data
            val method = e.method
            val extra = byteArrayOf(
                0x01, 0x99.toByte(), 0x07, 0x00, 0x01, 0x00,
                0x41, 0x45, 0x03, method.toByte(), 0x00
            )
            val content = if (method == 8) deflate(data) else data

            val salt = ByteArray(16)
            SecureRandom().nextBytes(salt)
            val dk = pbkdf2(salt, 66)
            val enc = ctr(dk.copyOfRange(0, 32), content)
            val mac = Mac.getInstance("HmacSHA1")
            mac.init(SecretKeySpec(dk.copyOfRange(32, 64), "HmacSHA1"))
            val auth = mac.doFinal(enc).copyOfRange(0, 10)
            val payload = ByteArrayOutputStream()
            payload.write(salt)
            payload.write(dk[64].toInt())
            payload.write(dk[65].toInt())
            payload.write(enc)
            payload.write(auth)
            val bytes = payload.toByteArray()
            val crc = CRC32()
            crc.update(data)

            val z = ZipEntry()
            z.name = name
            z.payload = bytes
            z.crc = crc.value
            z.usize = data.size.toLong()
            z.time = time
            z.date = date
            z.offset = zip.size().toLong()
            z.extra = extra.copyOf()
            meta.add(z)

            val nm = name.toByteArray(StandardCharsets.UTF_8)
            writeU32(zip, 0x04034b50L)
            writeU16(zip, 51)
            writeU16(zip, 0x0801)
            writeU16(zip, 99)
            writeU16(zip, time)
            writeU16(zip, date)
            writeU32(zip, z.crc)
            writeU32(zip, bytes.size.toLong())
            writeU32(zip, data.size.toLong())
            writeU16(zip, nm.size)
            writeU16(zip, extra.size)
            zip.write(nm)
            zip.write(extra)
            zip.write(bytes)
        }

        val cdStart = zip.size().toLong()
        for (z in meta) {
            val nm = z.name.toByteArray(StandardCharsets.UTF_8)
            writeU32(zip, 0x02014b50L)
            writeU16(zip, 51)
            writeU16(zip, 51)
            writeU16(zip, 0x0801)
            writeU16(zip, 99)
            writeU16(zip, z.time)
            writeU16(zip, z.date)
            writeU32(zip, z.crc)
            writeU32(zip, z.payload.size.toLong())
            writeU32(zip, z.usize)
            writeU16(zip, nm.size)
            writeU16(zip, z.extra.size)
            writeU16(zip, 0)
            writeU16(zip, 0)
            writeU16(zip, 0)
            writeU32(zip, 0)
            writeU32(zip, z.offset)
            zip.write(nm)
            zip.write(z.extra)
        }
        val cdSize = zip.size() - cdStart
        writeU32(zip, 0x06054b50L)
        writeU16(zip, 0)
        writeU16(zip, 0)
        writeU16(zip, meta.size)
        writeU16(zip, meta.size)
        writeU32(zip, cdSize)
        writeU32(zip, cdStart)
        writeU16(zip, 0)
        return zip.toByteArray()
    }

    private fun epoch(date: String): Long {
        return try {
            SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(date).time / 1000
        } catch (_: Exception) {
            0
        }
    }

    private fun dosTime(): Int {
        val c = Calendar.getInstance()
        return (c.get(Calendar.HOUR_OF_DAY) shl 11) or (c.get(Calendar.MINUTE) shl 5) or (c.get(Calendar.SECOND) / 2)
    }

    private fun dosDate(): Int {
        val c = Calendar.getInstance()
        return ((c.get(Calendar.YEAR) - 1980) shl 9) or ((c.get(Calendar.MONTH) + 1) shl 5) or c.get(Calendar.DAY_OF_MONTH)
    }

    private fun deflate(data: ByteArray): ByteArray {
        val d = Deflater(Deflater.DEFAULT_COMPRESSION, true)
        d.setInput(data)
        d.finish()
        val out = ByteArrayOutputStream()
        val buf = ByteArray(8192)
        while (!d.finished()) out.write(buf, 0, d.deflate(buf))
        d.end()
        return out.toByteArray()
    }

    private fun inflateBytes(comp: ByteArray): ByteArray {
        val inf = Inflater(true)
        inf.setInput(comp)
        val bos = ByteArrayOutputStream()
        val buf = ByteArray(8192)
        while (!inf.finished()) {
            val n = inf.inflate(buf)
            if (n == 0) {
                if (inf.needsInput() || inf.needsDictionary()) break
            }
            bos.write(buf, 0, n)
        }
        inf.end()
        return bos.toByteArray()
    }

    private fun u16(b: ByteArray, o: Int): Int = (b[o].toInt() and 0xff) or ((b[o + 1].toInt() and 0xff) shl 8)

    private fun u32(b: ByteArray, o: Int): Long =
        (u16(b, o).toLong() or (u16(b, o + 2).toLong() shl 16)) and 0xffffffffL

    private fun writeU16(o: ByteArrayOutputStream, v: Int) {
        o.write(v and 0xff)
        o.write((v shr 8) and 0xff)
    }

    private fun writeU32(o: ByteArrayOutputStream, v: Long) {
        writeU16(o, (v and 0xffff).toInt())
        writeU16(o, ((v shr 16) and 0xffff).toInt())
    }
}
