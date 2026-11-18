package com.xiaoman.app;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** 本地数据：资产、分类、标签、设置，SharedPreferences 里存一份 JSON。 */
final class Store {
    private static final String PREFS = "xiaoman_v1";
    private final SharedPreferences sp;
    private JSONObject root;

    Store(Context c) {
        sp = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        load();
    }

    private void load() {
        try {
            root = new JSONObject(sp.getString("data", "{}"));
        } catch (JSONException e) {
            root = new JSONObject();
        }
        if (!root.has("assets")) put("assets", new JSONArray());
        if (!root.has("categories")) put("categories", new JSONArray());
        if (!root.has("tags")) put("tags", new JSONArray());
        if (!root.has("settings")) put("settings", new JSONObject());
        if (arr("incomeCategories").length() == 0) {
            try {
                String[] seed = {"出租", "工资", "理财", "其他"};
                for (int i = 0; i < seed.length; i++)
                    arr("incomeCategories").put(new JSONObject().put("id", "inc_cat_" + i).put("name", seed[i]));
            } catch (JSONException ignored) {}
        }
        JSONObject s = settings();
        if (!s.has("currency")) setSetting("currency", "CNY");
        if (!s.has("decimals")) setSetting("decimals", 2);
        if (!s.has("thousands")) setSetting("thousands", true);
        if (!s.has("duration")) setSetting("duration", "days");
        if (!s.has("theme")) setSetting("theme", "system");
        if (!s.has("homeStyle")) setSetting("homeStyle", "default");
        if (!s.has("view")) setSetting("view", "grid");
        boolean migrated = false;
        for (JSONObject a : objs("assets")) {
            String icon = a.optString("icon");
            if (icon.isEmpty() || "emoji/app_icon_v1.png".equals(icon) || "img/asset_default.png".equals(icon)) {
                try { a.put("icon", "emoji/icon_default_object.png"); } catch (Exception ignored) {}
                migrated = true;
            }
        }
        if (migrated) save(); else save();
    }

    void save() {
        sp.edit().putString("data", root.toString()).apply();
    }

    void putArray(String key, JSONArray arr) {
        put(key, arr);
        save();
    }

    void upsertInArray(String key, JSONObject item) {
        JSONArray a = arr(key);
        String id = item.optString("id");
        for (int i = 0; i < a.length(); i++) {
            JSONObject o = a.optJSONObject(i);
            if (o != null && id.equals(o.optString("id"))) {
                try { a.put(i, item); } catch (JSONException ignored) {}
                return;
            }
        }
        a.put(item);
    }

    private void put(String k, Object v) {
        try { root.put(k, v); } catch (JSONException ignored) {}
    }

    JSONArray arr(String key) {
        JSONArray a = root.optJSONArray(key);
        if (a == null) { a = new JSONArray(); put(key, a); }
        return a;
    }

    List<JSONObject> objs(String key) {
        JSONArray a = arr(key);
        List<JSONObject> out = new ArrayList<>();
        for (int i = 0; i < a.length(); i++) {
            JSONObject o = a.optJSONObject(i);
            if (o != null) out.add(o);
        }
        return out;
    }

    void removeAsset(String id) {
        JSONArray a = arr("assets");
        for (int i = a.length() - 1; i >= 0; i--) {
            JSONObject o = a.optJSONObject(i);
            if (o != null && id.equals(o.optString("id"))) a.remove(i);
        }
        save();
    }

    void upsertAsset(JSONObject asset) {
        JSONArray a = arr("assets");
        String id = asset.optString("id");
        for (int i = 0; i < a.length(); i++) {
            JSONObject o = a.optJSONObject(i);
            if (o != null && id.equals(o.optString("id"))) {
                try { a.put(i, asset); } catch (JSONException ignored) {}
                save();
                return;
            }
        }
        a.put(asset);
        save();
    }

    JSONObject findAsset(String id) {
        for (JSONObject o : objs("assets")) if (id.equals(o.optString("id"))) return o;
        return null;
    }

    // ---------- 设置 ----------
    JSONObject settings() {
        JSONObject s = root.optJSONObject("settings");
        if (s == null) { s = new JSONObject(); put("settings", s); }
        return s;
    }
    String setting(String k, String def) { return settings().optString(k, def); }
    int settingInt(String k, int def) { return settings().optInt(k, def); }
    boolean settingBool(String k, boolean def) { return settings().optBoolean(k, def); }

    void setSetting(String k, Object v) {
        try { settings().put(k, v); } catch (JSONException ignored) {}
        save();
    }

    String str(String k, String def) { return root.optString(k, def); }
    void setStr(String k, String v) { put(k, v); save(); }
    boolean bool(String k, boolean def) { return root.optBoolean(k, def); }
    void setBool(String k, boolean v) { put(k, v); save(); }

    void setSystemDark(boolean d) { sp.edit().putInt("sys_dark", d ? 1 : 0).apply(); }

    boolean isDark() {
        String t = setting("theme", "system");
        if ("dark".equals(t)) return true;
        if ("light".equals(t)) return false;
        return (sp.getInt("sys_dark", 0) == 1);
    }

    JSONArray rawArray(String k) { return root.optJSONArray(k); }
    void setRawArray(String k, JSONArray a) { put(k, a); save(); }

    String export() { return root.toString(); }

    void clearAll() {
        try {
            root.put("assets", new JSONArray());
            root.put("categories", new JSONArray());
            root.put("tags", new JSONArray());
            root.put("settings", new JSONObject());
            root.put("pro", false);
            root.put("onboarded", true);
            root.put("ratingShown", false);
        } catch (JSONException ignored) {}
        save();
    }

    // ---------- 金额与日期 ----------
    String sym() {
        switch (setting("currency", "CNY")) {
            case "USD": return "$";
            case "EUR": return "€";
            case "JPY": return "¥";
            case "GBP": return "£";
            case "HKD": return "HK$";
            default: return "¥";
        }
    }

    String money(double v) { return sym() + fmt(v, settingInt("decimals", 2)); }

    String fmt(double v, int digits) {
        DecimalFormat df = new DecimalFormat();
        df.setMinimumFractionDigits(digits);
        df.setMaximumFractionDigits(digits);
        df.setGroupingUsed(settingBool("thousands", true));
        return df.format(v);
    }

    static String today() {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
    }

    int ageDays(JSONObject a) {
        if ("sold".equals(a.optString("status")) && !a.optString("soldDate").isEmpty()) {
            return Math.max(1, daysBetween(a.optString("buyDate"), a.optString("soldDate")));
        }
        return Math.max(1, daysBetween(a.optString("buyDate"), today()));
    }

    static int daysBetween(String from, String to) {
        try {
            SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
            Date a = f.parse(from), b = f.parse(to);
            if (a == null || b == null) return 1;
            return (int) Math.floor((b.getTime() - a.getTime()) / 86400000.0) + 1;
        } catch (Exception e) { return 1; }
    }

    double currentPrice(JSONObject a) {
        return a.optDouble("price", 0);
    }

    int usageCount(JSONObject a) {
        JSONArray list = a.optJSONArray("usages");
        if (list != null && list.length() > 0) return list.length();
        return (int) Math.max(1, a.optDouble("uses", 1));
    }

    double netCost(JSONObject a) {
        double price = currentPrice(a);
        double residual;
        if ("sold".equals(a.optString("status"))) residual = a.optDouble("soldPrice", 0);
        else residual = a.optBoolean("secondhandInCost", true) ? a.optDouble("secondhandValue", 0) : 0;
        return Math.max(0, price - Math.max(0, residual));
    }

    double dailyCost(JSONObject a) {
        double price = netCost(a);
        String rule = a.optString("rule", "day");
        if ("count".equals(rule)) return price / usageCount(a);
        if ("custom".equals(rule)) return price / Math.max(1, a.optDouble("customDays", 1));
        try {
            SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
            Date buy = f.parse(a.optString("buyDate"));
            long end = System.currentTimeMillis();
            if ("sold".equals(a.optString("status")) && !a.optString("soldDate").isEmpty()) {
                Date sold = f.parse(a.optString("soldDate"));
                if (sold != null) end = sold.getTime();
            }
            double days = (end - (buy != null ? buy.getTime() : end)) / 86400000.0;
            return price / Math.max(1e-6, days);
        } catch (Exception e) { return price / ageDays(a); }
    }

    static String statusText(String s) {
        if ("retired".equals(s)) return "已退役";
        if ("sold".equals(s)) return "已卖出";
        return "服役中";
    }

    static boolean isWish(JSONObject a) { return "wish".equals(a.optString("kind")); }

    List<JSONObject> ofKind(String kind) {
        List<JSONObject> out = new ArrayList<>();
        for (JSONObject o : objs("assets")) if (kind.equals(o.optString("kind", "asset"))) out.add(o);
        return out;
    }

    double total(String kind) {
        double t = 0;
        for (JSONObject o : ofKind(kind))
            if (!"sold".equals(o.optString("status")) && !o.optBoolean("excludeTotal")) t += currentPrice(o);
        return t;
    }

    double daily(String kind) {
        double t = 0;
        for (JSONObject o : ofKind(kind)) if ("serving".equals(o.optString("status", "serving"))) t += dailyCost(o);
        return t;
    }

    int count(String kind, String status) {
        int n = 0;
        for (JSONObject o : ofKind(kind)) if (status.equals(o.optString("status", "serving"))) n++;
        return n;
    }
}
