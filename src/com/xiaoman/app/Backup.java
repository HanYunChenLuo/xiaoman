package com.xiaoman.app;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.zip.CRC32;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

/** 官方 .ias 备份：WinZip AES-256 加密的 zip，内含 data.json 与 images/。 */
final class Backup {
    private static final byte[] PASSWORD = "iAsset#Support@2025!".getBytes(StandardCharsets.UTF_8);
    private static final int ITER = 1000;

    static final class Parsed {
        JSONObject root;
        final Map<String, byte[]> images = new HashMap<>();
    }

    // ---------------- 读取 ----------------
    static Parsed readIas(byte[] zip) throws Exception {
        Map<String, byte[]> entries = readEntries(zip);
        byte[] json = entries.get("data.json");
        if (json == null) throw new Exception("data.json not found");
        Parsed p = new Parsed();
        p.root = new JSONObject(new String(json, StandardCharsets.UTF_8));
        for (Map.Entry<String, byte[]> e : entries.entrySet())
            if (e.getKey().startsWith("images/")) p.images.put(e.getKey(), e.getValue());
        return p;
    }

    private static Map<String, byte[]> readEntries(byte[] zip) throws Exception {
        Map<String, byte[]> out = new HashMap<>();
        int off = 0;
        while (off + 30 <= zip.length) {
            if (u32(zip, off) != 0x04034b50) break;
            int flags = u16(zip, off + 6);
            int method = u16(zip, off + 8);
            long csize = u32(zip, off + 18);
            int nlen = u16(zip, off + 26), xlen = u16(zip, off + 28);
            String name = new String(zip, off + 30, nlen, StandardCharsets.UTF_8);
            int extraStart = off + 30 + nlen;
            int dataStart = extraStart + xlen;
            if (method == 99 && csize > 28) {
                int actual = aesMethod(zip, extraStart, xlen);
                byte[] payload = java.util.Arrays.copyOfRange(zip, dataStart, dataStart + (int) csize);
                byte[] comp = aesDecrypt(payload);
                out.put(name, actual == 8 ? inflateBytes(comp) : comp);
            } else if ((method == 0 || method == 8) && csize > 0) {
                byte[] payload = java.util.Arrays.copyOfRange(zip, dataStart, dataStart + (int) csize);
                out.put(name, method == 8 ? inflateBytes(payload) : payload);
            }
            if ((flags & 8) != 0 && csize == 0) break;
            off = dataStart + (int) csize;
        }
        return out;
    }

    private static int aesMethod(byte[] zip, int extraStart, int xlen) {
        int p = extraStart, end = extraStart + xlen;
        while (p + 4 <= end) {
            int id = u16(zip, p);
            int sz = u16(zip, p + 2);
            if (id == 0x9901 && sz >= 7) return u16(zip, p + 4 + 5);
            p += 4 + sz;
        }
        return 8;
    }

    private static byte[] aesDecrypt(byte[] entry) throws Exception {
        byte[] salt = java.util.Arrays.copyOfRange(entry, 0, 16);
        byte[] pvv = java.util.Arrays.copyOfRange(entry, 16, 18);
        byte[] cipher = java.util.Arrays.copyOfRange(entry, 18, entry.length - 10);
        byte[] auth = java.util.Arrays.copyOfRange(entry, entry.length - 10, entry.length);
        byte[] dk = pbkdf2(salt, 66);
        if (dk[64] != pvv[0] || dk[65] != pvv[1]) throw new Exception("bad password");
        Mac mac = Mac.getInstance("HmacSHA1");
        mac.init(new SecretKeySpec(java.util.Arrays.copyOfRange(dk, 32, 64), "HmacSHA1"));
        byte[] digest = mac.doFinal(cipher);
        for (int i = 0; i < 10; i++) if (digest[i] != auth[i]) throw new Exception("auth failed");
        return ctr(java.util.Arrays.copyOfRange(dk, 0, 32), cipher);
    }

    private static byte[] ctr(byte[] key, byte[] data) throws Exception {
        Cipher ecb = Cipher.getInstance("AES/ECB/NoPadding");
        ecb.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"));
        byte[] out = new byte[data.length];
        byte[] counter = new byte[16];
        counter[0] = 1;
        byte[] ks = new byte[16];
        for (int i = 0; i < data.length; i += 16) {
            ecb.doFinal(counter, 0, 16, ks, 0);
            int n = Math.min(16, data.length - i);
            for (int j = 0; j < n; j++) out[i + j] = (byte) (data[i + j] ^ ks[j]);
            for (int j = 0; j < 16; j++) { counter[j]++; if (counter[j] != 0) break; }
        }
        return out;
    }

    private static byte[] pbkdf2(byte[] salt, int len) throws Exception {
        SecretKeyFactory f = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1");
        return f.generateSecret(new PBEKeySpec(new String(PASSWORD, StandardCharsets.UTF_8).toCharArray(), salt, ITER, len * 8)).getEncoded();
    }

    // ---------------- 导入 ----------------
    static int importInto(Store store, JSONObject root, boolean replace, Map<String, String> imagePaths) throws Exception {
        if (!root.has("assets")) throw new Exception("bad backup");
        if (replace) {
            store.putArray("assets", new JSONArray());
            store.putArray("categories", new JSONArray());
            store.putArray("tags", new JSONArray());
        }
        JSONArray cats = root.optJSONArray("categories");
        if (cats != null) for (int i = 0; i < cats.length(); i++) {
            JSONObject c = cats.optJSONObject(i);
            if (c == null || c.optBoolean("is_deleted")) continue;
            store.upsertInArray("categories", new JSONObject().put("id", c.optString("id")).put("name", c.optString("title")));
        }
        JSONArray tags = root.optJSONArray("tags");
        if (tags != null) for (int i = 0; i < tags.length(); i++) {
            JSONObject t = tags.optJSONObject(i);
            if (t == null || t.optBoolean("is_deleted")) continue;
            store.upsertInArray("tags", new JSONObject().put("id", t.optString("id")).put("name", t.optString("title")));
        }
        JSONArray incCats = root.optJSONArray("income_categories");
        if (incCats != null) for (int i = 0; i < incCats.length(); i++) {
            JSONObject c = incCats.optJSONObject(i);
            if (c == null || c.optBoolean("is_deleted")) continue;
            store.upsertInArray("incomeCategories", new JSONObject().put("id", c.optString("id")).put("name", c.optString("title")));
        }

        int count = 0;
        JSONArray assets = root.optJSONArray("assets");
        HashMap<String, JSONObject> byId = new HashMap<>();
        for (int i = 0; assets != null && i < assets.length(); i++) {
            JSONObject a = assets.optJSONObject(i);
            if (a == null || a.optBoolean("is_deleted")) continue;
            int type = a.optInt("type", 0);
            JSONObject item = new JSONObject();
            item.put("id", a.optString("id"));
            item.put("name", a.optString("name"));
            item.put("model", a.optString("model"));
            double price = a.optDouble("price", 0);
            if (price == 0) price = a.optDouble("target_cost", 0);
            item.put("price", price);
            item.put("buyDate", date(a.optLong("buy_date", 0)));
            item.put("kind", type == 1 ? "wish" : "asset");
            if (a.optBoolean("is_sell")) {
                item.put("status", "sold");
                item.put("soldDate", date(a.optLong("sell_date", 0)));
            } else if (a.optBoolean("is_retirement")) {
                item.put("status", "retired");
            } else item.put("status", "serving");
            item.put("categoryId", a.optString("category_id"));
            JSONArray tids = a.optJSONArray("tag_ids");
            item.put("tagIds", tids != null ? tids : new JSONArray());
            item.put("rule", "day");
            item.put("icon", "emoji/icon_default_object.png");
            item.put("note", a.optString("note"));
            item.put("soldPrice", a.optDouble("used_price", 0));
            item.put("secondhandValue", a.optDouble("secondhand_value", 0));
            item.put("secondhandInCost", a.optBoolean("secondhand_in_cost", true));
            item.put("brandId", a.optString("brand_id"));
            item.put("brandName", a.optString("brand_name"));
            item.put("excludeTotal", a.optBoolean("is_not_include_total") || a.optBoolean("is_excluded_from_total"));
            if (a.optLong("retirement_date", 0) > 0) item.put("retireDate", date(a.optLong("retirement_date")));
            if (type == 1) {
                item.put("targetCost", a.optDouble("target_cost", 0));
                if (a.optLong("target_date", 0) > 0) item.put("targetDate", date(a.optLong("target_date")));
            }
            if (a.optInt("target_usage_days", 0) > 0) item.put("targetUsageDays", a.optInt("target_usage_days"));
            String cover = a.optString("cover_image");
            if (!cover.isEmpty() && imagePaths != null && imagePaths.containsKey(cover))
                item.put("coverPath", imagePaths.get(cover));
            JSONArray ni = a.optJSONArray("note_images");
            if (ni != null && ni.length() > 0 && imagePaths != null) {
                JSONArray local = new JSONArray();
                for (int k = 0; k < ni.length(); k++) {
                    String nm = ni.optString(k);
                    if (imagePaths.containsKey(nm)) local.put(imagePaths.get(nm));
                }
                if (local.length() > 0) item.put("noteImages", local);
            }
            store.upsertAsset(item);
            byId.put(item.optString("id"), item);
            count++;
        }

        JSONArray imageRecords = root.optJSONArray("asset_images");
        if (imageRecords != null) for (int i = 0; i < imageRecords.length(); i++) {
            JSONObject r = imageRecords.optJSONObject(i);
            if (r == null || r.optBoolean("is_deleted")) continue;
            JSONObject asset = byId.get(r.optString("asset_id", r.optString("assetId")));
            String name = r.optString("image_name", r.optString("file_name", r.optString("name")));
            if (asset == null || name.isEmpty() || imagePaths == null) continue;
            String local = imagePaths.get(name);
            if (local == null) local = imagePaths.get("images/" + name);
            if (local == null) continue;
            String type = r.optString("type", r.optString("image_type"));
            boolean cover = r.optInt("is_cover", -1) == 1 || "cover".equals(type) || "0".equals(type);
            if (cover) {
                if (asset.optString("coverPath").isEmpty()) asset.put("coverPath", local);
            } else {
                JSONArray list = asset.optJSONArray("noteImages");
                if (list == null) { list = new JSONArray(); asset.put("noteImages", list); }
                boolean exists = false;
                for (int k = 0; k < list.length(); k++) if (local.equals(list.optString(k))) exists = true;
                if (!exists) list.put(local);
            }
        }

        JSONArray records = root.optJSONArray("income_records");
        if (records != null) for (int i = 0; i < records.length(); i++) {
            JSONObject r = records.optJSONObject(i);
            if (r == null || r.optBoolean("is_deleted")) continue;
            JSONObject asset = store.findAsset(r.optString("asset_id", r.optString("assetId")));
            if (asset == null) continue;
            JSONArray list = asset.optJSONArray("incomeRecords");
            if (list == null) { list = new JSONArray(); asset.put("incomeRecords", list); }
            long sec = r.optLong("income_date", r.optLong("date", 0));
            list.put(new JSONObject().put("id", r.optString("id"))
                    .put("amount", r.optDouble("amount", 0))
                    .put("date", sec > 0 ? date(sec) : r.optString("income_date", r.optString("date")))
                    .put("categoryId", r.optString("category_id", r.optString("categoryId")))
                    .put("note", r.optString("note")));
        }
        JSONArray usages = root.optJSONArray("usages");
        if (usages != null) for (int i = 0; i < usages.length(); i++) {
            JSONObject r = usages.optJSONObject(i);
            if (r == null || r.optBoolean("is_deleted")) continue;
            JSONObject asset = store.findAsset(r.optString("asset_id", r.optString("assetId")));
            if (asset == null) continue;
            JSONArray list = asset.optJSONArray("usages");
            if (list == null) { list = new JSONArray(); asset.put("usages", list); }
            long sec = r.optLong("usage_date", r.optLong("date", 0));
            list.put(new JSONObject().put("id", r.optString("id"))
                    .put("date", sec > 0 ? date(sec) : r.optString("usage_date", r.optString("date")))
                    .put("note", r.optString("note")));
        }
        JSONArray prices = root.optJSONArray("asset_price_records");
        if (prices != null) for (int i = 0; i < prices.length(); i++) {
            JSONObject r = prices.optJSONObject(i);
            if (r == null || r.optBoolean("is_deleted")) continue;
            JSONObject asset = store.findAsset(r.optString("asset_id", r.optString("assetId")));
            if (asset == null) continue;
            JSONArray list = asset.optJSONArray("priceRecords");
            if (list == null) { list = new JSONArray(); asset.put("priceRecords", list); }
            long sec = r.optLong("record_date", r.optLong("date", r.optLong("created_at", 0)));
            list.put(new JSONObject().put("id", r.optString("id"))
                    .put("date", sec > 0 ? date(sec) : r.optString("record_date", r.optString("date")))
                    .put("price", r.optDouble("price", 0)));
        }
        JSONArray reminders = root.optJSONArray("expire_reminders");
        if (reminders != null) for (int i = 0; i < reminders.length(); i++) {
            JSONObject r = reminders.optJSONObject(i);
            if (r == null || r.optBoolean("is_deleted")) continue;
            String aid = r.optString("asset_id", r.optString("assetId"));
            JSONObject asset = aid.isEmpty() ? store.findAsset(r.optString("id")) : store.findAsset(aid);
            if (asset == null) continue;
            long sec = r.optLong("expiry_at", 0);
            if (sec > 0) asset.put("expireAt", date(sec));
            int remind = r.optInt("expiry_remind", 0);
            if (remind > 0) asset.put("remindDays", remind);
            String unit = r.optString("remind_time_unit", "day");
            asset.put("remindUnit", unit.isEmpty() ? "day" : unit);
            long remindAt = r.optLong("remind_at", 0);
            if (remindAt > 0) {
                java.util.Calendar rc = java.util.Calendar.getInstance();
                rc.setTimeInMillis(remindAt * 1000L);
                asset.put("remindHour", rc.get(java.util.Calendar.HOUR_OF_DAY));
                asset.put("remindMinute", rc.get(java.util.Calendar.MINUTE));
            }
        }
        JSONArray saveMoney = root.optJSONArray("save_money");
        if (saveMoney != null) store.setRawArray("rawSaveMoney", saveMoney);

        store.save();
        return count;
    }

    private static String date(long seconds) {
        if (seconds <= 0) return Store.today();
        SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        return f.format(new Date(seconds * 1000L));
    }

    // ---------------- 导出 ----------------
    static byte[] writeIas(Store store) throws Exception {
        List<Object[]> entryFiles = new ArrayList<>();
        List<JSONObject> imageRecords = new ArrayList<>();

        JSONObject root = new JSONObject();
        root.put("categories", categories(store));
        root.put("tags", tags(store));
        root.put("assets", assets(store, entryFiles, imageRecords));
        root.put("asset_images", new JSONArray(imageRecords));
        root.put("usages", usages(store));
        root.put("income_categories", incomeCategories(store));
        root.put("income_records", incomeRecords(store));
        root.put("asset_price_records", priceRecords(store));
        JSONArray smRaw = store.rawArray("rawSaveMoney");
        root.put("save_money", smRaw != null ? smRaw : new JSONArray());
        root.put("expire_reminders", reminders(store));
        root.put("platform", "AOS");
        root.put("version", "1.5.3");
        root.put("exported_at", System.currentTimeMillis() / 1000);

        List<Object[]> entries = new ArrayList<>();
        entries.add(new Object[]{"data.json", root.toString().getBytes(StandardCharsets.UTF_8), 8});
        entries.addAll(entryFiles);
        return zipEncrypted(entries);
    }

    private static JSONArray categories(Store store) throws Exception {
        JSONArray out = new JSONArray();
        int sort = 0;
        for (JSONObject c : store.objs("categories"))
            out.put(new JSONObject().put("id", c.optString("id")).put("title", c.optString("name"))
                    .put("sort", sort++).put("is_deleted", false).put("created_at", 0).put("update_at", 0));
        return out;
    }

    private static JSONArray tags(Store store) throws Exception {
        JSONArray out = new JSONArray();
        int sort = 0;
        for (JSONObject t : store.objs("tags"))
            out.put(new JSONObject().put("id", t.optString("id")).put("title", t.optString("name"))
                    .put("sort", sort++).put("is_deleted", false).put("created_at", 0).put("update_at", 0));
        return out;
    }

    private static JSONArray incomeCategories(Store store) throws Exception {
        JSONArray out = new JSONArray();
        int sort = 0;
        for (JSONObject c : store.objs("incomeCategories"))
            out.put(new JSONObject().put("id", c.optString("id")).put("title", c.optString("name"))
                    .put("icon_name", "icon_income_1").put("color_hex", "#A3E635")
                    .put("sort", sort++).put("is_deleted", false).put("created_at", 0).put("update_at", 0));
        return out;
    }

    private static JSONArray incomeRecords(Store store) throws Exception {
        JSONArray out = new JSONArray();
        for (JSONObject a : store.objs("assets")) {
            JSONArray list = a.optJSONArray("incomeRecords");
            if (list == null) continue;
            for (int i = 0; i < list.length(); i++) {
                JSONObject r = list.optJSONObject(i);
                if (r == null) continue;
                out.put(new JSONObject().put("id", r.optString("id"))
                        .put("asset_id", a.optString("id"))
                        .put("category_id", r.optString("categoryId"))
                        .put("amount", r.optDouble("amount", 0))
                        .put("income_date", epoch(r.optString("date")))
                        .put("note", r.optString("note"))
                        .put("is_deleted", false).put("created_at", 0).put("update_at", 0));
            }
        }
        return out;
    }

    private static JSONArray usages(Store store) throws Exception {
        JSONArray out = new JSONArray();
        for (JSONObject a : store.objs("assets")) {
            JSONArray list = a.optJSONArray("usages");
            for (int i = 0; list != null && i < list.length(); i++) {
                JSONObject r = list.optJSONObject(i);
                if (r == null) continue;
                out.put(new JSONObject().put("id", r.optString("id"))
                        .put("asset_id", a.optString("id"))
                        .put("usage_date", epoch(r.optString("date")))
                        .put("note", r.optString("note"))
                        .put("is_deleted", false).put("created_at", 0).put("update_at", 0));
            }
        }
        return out;
    }

    private static JSONArray priceRecords(Store store) throws Exception {
        JSONArray out = new JSONArray();
        for (JSONObject a : store.objs("assets")) {
            JSONArray list = a.optJSONArray("priceRecords");
            for (int i = 0; list != null && i < list.length(); i++) {
                JSONObject r = list.optJSONObject(i);
                if (r == null) continue;
                out.put(new JSONObject().put("id", r.optString("id"))
                        .put("asset_id", a.optString("id"))
                        .put("price", r.optDouble("price", 0))
                        .put("record_date", epoch(r.optString("date")))
                        .put("is_deleted", false).put("created_at", 0).put("update_at", 0));
            }
        }
        return out;
    }

    private static JSONArray reminders(Store store) throws Exception {
        JSONArray out = new JSONArray();
        for (JSONObject a : store.objs("assets")) {
            String expire = a.optString("expireAt");
            if (expire.isEmpty()) continue;
            int days = a.optInt("remindDays", 0);
            long expiry = epoch(expire);
            String unit = a.optString("remindUnit", "day");
            long unitSec = "week".equals(unit) ? 7 * 86400L : "month".equals(unit) ? 30 * 86400L : 86400L;
            long remindAt = expiry - days * unitSec + a.optInt("remindHour", 9) * 3600L + a.optInt("remindMinute", 0) * 60L;
            out.put(new JSONObject().put("id", a.optString("id"))
                    .put("asset_id", a.optString("id"))
                    .put("expiry_at", expiry)
                    .put("expiry_remind", days)
                    .put("remind_time_unit", unit)
                    .put("remind_at", remindAt)
                    .put("is_deleted", false).put("created_at", 0).put("update_at", 0));
        }
        return out;
    }

    private static JSONArray assets(Store store, List<Object[]> entryFiles, List<JSONObject> imageRecords) throws Exception {
        JSONArray out = new JSONArray();
        int sort = 0, imgId = 0;
        for (JSONObject a : store.objs("assets")) {
            JSONObject o = new JSONObject();
            o.put("id", a.optString("id"));
            o.put("name", a.optString("name"));
            o.put("model", a.optString("model"));
            o.put("icon_name", "icon_default_object");
            String coverName = "";
            String coverPath = a.optString("coverPath");
            if (!coverPath.isEmpty()) {
                File f = new File(coverPath);
                if (f.exists()) {
                    coverName = "images/" + a.optString("id") + "_cover" + ext(coverPath);
                    entryFiles.add(new Object[]{coverName, readFile(f), 0});
                    imageRecords.add(new JSONObject().put("id", "img_" + (imgId++))
                            .put("asset_id", a.optString("id")).put("image_name", coverName)
                            .put("type", "cover").put("sort", 0).put("is_deleted", false)
                            .put("created_at", 0).put("update_at", 0));
                }
            }
            o.put("cover_image", coverName);
            JSONArray noteNames = new JSONArray();
            JSONArray noteImgs = a.optJSONArray("noteImages");
            for (int i = 0; noteImgs != null && i < noteImgs.length(); i++) {
                String p = noteImgs.optString(i);
                File f = new File(p);
                if (!f.exists()) continue;
                String nm = "images/" + a.optString("id") + "_note" + i + ext(p);
                entryFiles.add(new Object[]{nm, readFile(f), 0});
                noteNames.put(nm);
                imageRecords.add(new JSONObject().put("id", "img_" + (imgId++))
                        .put("asset_id", a.optString("id")).put("image_name", nm)
                        .put("type", "note").put("sort", i).put("is_deleted", false)
                        .put("created_at", 0).put("update_at", 0));
            }
            o.put("price", a.optDouble("price", 0));
            o.put("type", Store.isWish(a) ? 1 : 0);
            o.put("category_id", a.optString("categoryId"));
            o.put("state", 0);
            o.put("buy_date", epoch(a.optString("buyDate")));
            JSONArray tids = a.optJSONArray("tagIds");
            o.put("tag_ids", tids != null ? tids : new JSONArray());
            o.put("target_type", 0);
            o.put("target_cost_unit", "");
            o.put("target_each_increment", 0);
            o.put("target_date", epoch(a.optString("targetDate")));
            o.put("target_usage_days", a.optInt("targetUsageDays", 0));
            o.put("target_cost", a.optDouble("targetCost", 0));
            o.put("target_value", 0);
            o.put("additional_item_ids", new JSONArray());
            o.put("additional_calculation_mode", 0);
            o.put("note", a.optString("note"));
            o.put("note_images", noteNames);
            o.put("is_top", false);
            o.put("is_retirement", "retired".equals(a.optString("status")));
            o.put("retirement_date", epoch(a.optString("retireDate")));
            o.put("is_sell", "sold".equals(a.optString("status")));
            o.put("used_price", a.optDouble("soldPrice", 0));
            o.put("secondhand_value", a.optDouble("secondhandValue", 0));
            o.put("secondhand_in_cost", a.optBoolean("secondhandInCost", true));
            o.put("brand_id", a.optString("brandId"));
            o.put("brand_name", a.optString("brandName"));
            o.put("sell_date", epoch(a.optString("soldDate")));
            o.put("wish_date", epoch(a.optString("targetDate")));
            o.put("is_not_include_total", a.optBoolean("excludeTotal"));
            o.put("is_excluded_from_total", a.optBoolean("excludeTotal"));
            o.put("sort", sort++);
            o.put("is_deleted", false);
            o.put("created_at", 0);
            o.put("update_at", 0);
            out.put(o);
        }
        return out;
    }

    private static String ext(String path) {
        int i = path.lastIndexOf('.');
        String e = i >= 0 ? path.substring(i).toLowerCase(Locale.US) : ".jpg";
        return e.length() > 5 ? ".jpg" : e;
    }

    private static byte[] readFile(File f) throws Exception {
        try (java.io.FileInputStream in = new java.io.FileInputStream(f);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            return out.toByteArray();
        }
    }

    // ---------------- zip 写入（多条目，AES-256） ----------------
    private static final class ZipEntry {
        String name;
        byte[] payload;
        long crc;
        long usize;
        long offset;
        int time, date;
        byte[] extra;
    }

    private static byte[] zipEncrypted(List<Object[]> entries) throws Exception {
        ByteArrayOutputStream zip = new ByteArrayOutputStream();
        List<ZipEntry> meta = new ArrayList<>();
        int time = dosTime(), date = dosDate();
        byte[] extra = new byte[]{0x01, (byte) 0x99, 0x07, 0x00, 0x01, 0x00, 0x41, 0x45, 0x03, 0x08, 0x00};

        for (Object[] e : entries) {
            String name = (String) e[0];
            byte[] data = (byte[]) e[1];
            int method = (Integer) e[2];
            extra = new byte[]{0x01, (byte) 0x99, 0x07, 0x00, 0x01, 0x00, 0x41, 0x45, 0x03, (byte) method, 0x00};
            byte[] content = method == 8 ? deflate(data) : data;

            byte[] salt = new byte[16];
            new SecureRandom().nextBytes(salt);
            byte[] dk = pbkdf2(salt, 66);
            byte[] enc = ctr(java.util.Arrays.copyOfRange(dk, 0, 32), content);
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(java.util.Arrays.copyOfRange(dk, 32, 64), "HmacSHA1"));
            byte[] auth = java.util.Arrays.copyOfRange(mac.doFinal(enc), 0, 10);
            ByteArrayOutputStream payload = new ByteArrayOutputStream();
            payload.write(salt); payload.write(dk[64]); payload.write(dk[65]); payload.write(enc); payload.write(auth);
            byte[] bytes = payload.toByteArray();
            CRC32 crc = new CRC32(); crc.update(data);

            ZipEntry z = new ZipEntry();
            z.name = name; z.payload = bytes; z.crc = crc.getValue(); z.usize = data.length;
            z.time = time; z.date = date; z.offset = zip.size(); z.extra = extra.clone();
            meta.add(z);

            byte[] nm = name.getBytes(StandardCharsets.UTF_8);
            writeU32(zip, 0x04034b50); writeU16(zip, 51); writeU16(zip, 0x0801);
            writeU16(zip, 99); writeU16(zip, time); writeU16(zip, date);
            writeU32(zip, z.crc); writeU32(zip, bytes.length); writeU32(zip, data.length);
            writeU16(zip, nm.length); writeU16(zip, extra.length);
            zip.write(nm); zip.write(extra); zip.write(bytes);
        }

        long cdStart = zip.size();
        for (ZipEntry z : meta) {
            byte[] nm = z.name.getBytes(StandardCharsets.UTF_8);
            writeU32(zip, 0x02014b50); writeU16(zip, 51); writeU16(zip, 51); writeU16(zip, 0x0801);
            writeU16(zip, 99); writeU16(zip, z.time); writeU16(zip, z.date);
            writeU32(zip, z.crc); writeU32(zip, z.payload.length); writeU32(zip, z.usize);
            writeU16(zip, nm.length); writeU16(zip, z.extra.length); writeU16(zip, 0);
            writeU16(zip, 0); writeU16(zip, 0); writeU32(zip, 0);
            writeU32(zip, z.offset);
            zip.write(nm); zip.write(z.extra);
        }
        long cdSize = zip.size() - cdStart;
        writeU32(zip, 0x06054b50); writeU16(zip, 0); writeU16(zip, 0);
        writeU16(zip, meta.size()); writeU16(zip, meta.size());
        writeU32(zip, cdSize); writeU32(zip, cdStart); writeU16(zip, 0);
        return zip.toByteArray();
    }

    private static long epoch(String date) {
        try {
            SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
            return f.parse(date).getTime() / 1000;
        } catch (Exception e) { return 0; }
    }

    private static int dosTime() {
        Calendar c = Calendar.getInstance();
        return (c.get(Calendar.HOUR_OF_DAY) << 11) | (c.get(Calendar.MINUTE) << 5) | (c.get(Calendar.SECOND) / 2);
    }

    private static int dosDate() {
        Calendar c = Calendar.getInstance();
        return ((c.get(Calendar.YEAR) - 1980) << 9) | ((c.get(Calendar.MONTH) + 1) << 5) | c.get(Calendar.DAY_OF_MONTH);
    }

    private static byte[] deflate(byte[] data) {
        Deflater d = new Deflater(Deflater.DEFAULT_COMPRESSION, true);
        d.setInput(data); d.finish();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        while (!d.finished()) out.write(buf, 0, d.deflate(buf));
        d.end();
        return out.toByteArray();
    }

    private static byte[] inflateBytes(byte[] comp) throws Exception {
        Inflater inf = new Inflater(true);
        inf.setInput(comp);
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        while (!inf.finished()) {
            int n = inf.inflate(buf);
            if (n == 0) { if (inf.needsInput() || inf.needsDictionary()) break; }
            bos.write(buf, 0, n);
        }
        inf.end();
        return bos.toByteArray();
    }

    private static int u16(byte[] b, int o) { return (b[o] & 0xff) | ((b[o + 1] & 0xff) << 8); }
    private static long u32(byte[] b, int o) { return (u16(b, o) | ((long) u16(b, o + 2) << 16)) & 0xffffffffL; }
    private static void writeU16(ByteArrayOutputStream o, int v) { o.write(v & 0xff); o.write((v >> 8) & 0xff); }
    private static void writeU32(ByteArrayOutputStream o, long v) { writeU16(o, (int) (v & 0xffff)); writeU16(o, (int) ((v >> 16) & 0xffff)); }
}
