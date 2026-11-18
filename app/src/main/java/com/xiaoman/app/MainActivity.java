package com.xiaoman.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.content.Intent;
import android.net.Uri;
import android.graphics.Color;
import android.graphics.RenderEffect;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

public class MainActivity extends Activity {
    static boolean dark;
    private Store store;
    private Ui ui;
    private FrameLayout root, content, navHost;
    private String screen = "home", tab = "home";
    private String filter = "all", range = "all", categoryFilter = "all";
    private JSONObject draft;
    private boolean editing;
    private String editOriginal;
    private String detailId;
    private View lockView;
    private String entered = "";
    private boolean backgrounded = false;
    private java.util.Map<String, String> pendingImages = new java.util.HashMap<>();
    private static final int APP_VERSION_CODE = BuildConfig.VERSION_CODE;
    private static final int BRAND_SCHEMA = 1;
    private static final String BRAND_DATA_API_URL =
            "https://api.github.com/repos/HanYunChenLuo/xiaoman/contents/data/brands.json";
    private static final String BRAND_DATA_URL =
            "https://raw.githubusercontent.com/HanYunChenLuo/xiaoman/main/data/brands.json";
    private static final String BRAND_DATA_TAG = "XiaomanBrandData";
    private JSONObject brandData;
    private android.content.SharedPreferences brandPrefs;
    private int brandDataVersion = 0;
    private String brandDataVersionName = "内置";
    private final java.util.List<String[]> brandAliases = new java.util.ArrayList<>();
    private boolean brandAuto;
    private ImageView brandRowLogo;
    private TextView brandRowText;
    private ImageView iconPreview;
    private EditText modelInput;
    private View saveButtonRef;
    private ImageView saveCheckRef;
    private final java.util.Map<String, JSONArray> brandModels = new java.util.HashMap<>();
    private static final java.util.Map<String, String[]> MODEL_ICONS = new java.util.LinkedHashMap<>();
    static {
        MODEL_ICONS.put("apple/digital/Mobile-phone.png", new String[]{"iphone","galaxy","pixel","mate","redmi","ipad","平板","matepad","手机","小米","oppo","vivo","一加","oneplus","荣耀","honor","magic"});
        MODEL_ICONS.put("apple/digital/Laptop.png", new String[]{"macbook","笔记本","thinkpad","小新","matebook","rog","拯救者","天选","xps","轻薄本","游戏本"});
        MODEL_ICONS.put("apple/digital/Desktop-computer.png", new String[]{"mac mini","mac studio","imac","台式机","台式","主机","组装机","一体机"});
        MODEL_ICONS.put("apple/digital/Video-game.png", new String[]{"switch","ns2","ns","ps5","ps4","playstation","xbox","游戏机"});
        MODEL_ICONS.put("apple/digital/Movie-camera.png", new String[]{"dji","pocket","osmo","动作相机","运动相机","摄像机","mavic","无人机","drone"});
        MODEL_ICONS.put("apple/digital/Camera.png", new String[]{"相机","camera","单反","微单","镜头","gopro"});
        MODEL_ICONS.put("apple/digital/Headphone.png", new String[]{"airpods","耳机","headphone","音箱","音响","speaker"});
        MODEL_ICONS.put("apple/digital/Watch.png", new String[]{"apple watch","手表","手环","watch"});
        MODEL_ICONS.put("apple/digital/Television.png", new String[]{"电视","tv","显示器","monitor"});
        MODEL_ICONS.put("apple/vehicle/Automobile.png", new String[]{"汽车","理想","特斯拉","比亚迪","蔚来","小鹏","宝马","奔驰","奥迪","大众","丰田","本田","日产","保时捷","沃尔沃","极星","马自达"});
        MODEL_ICONS.put("apple/vehicle/Bicycle.png", new String[]{"自行车","公路车","山地车","单车","bike","giant","捷安特","trek"});
        MODEL_ICONS.put("apple/vehicle/Motorcycle.png", new String[]{"摩托","motorcycle"});
        MODEL_ICONS.put("apple/vehicle/Kick-scooter.png", new String[]{"滑板车","电动滑板","scooter"});
        MODEL_ICONS.put("apple/vehicle/Airplane.png", new String[]{"飞机","airplane"});
    }

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        store = new Store(this);
        store.setSystemDark((getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK)
                == android.content.res.Configuration.UI_MODE_NIGHT_YES);
        ui = new Ui(this);
        brandPrefs = getSharedPreferences("xiaoman_brand_data", MODE_PRIVATE);
        loadBrandData();
        if (!store.bool("modelMigrated", false)) {
            for (JSONObject a : store.objs("assets")) {
                if (a.optString("model").isEmpty()) {
                    try { a.put("model", a.optString("name")); } catch (Exception ignored) {}
                }
            }
            store.save();
            store.setBool("modelMigrated", true);
        }
        if (!store.bool("brandRepair2", false)) {
            for (JSONObject a : store.objs("assets")) {
                if (!a.optBoolean("brandAuto", false)) {
                    try { a.put("brandId", ""); a.put("brandName", ""); } catch (Exception ignored) {}
                }
            }
            store.save();
            store.setBool("brandRepair2", true);
        }
        fillMissingBrands();
        fillMissingIcons();
        fillMissingModels();
        dark = store.isDark();
        root = new FrameLayout(this);
        root.setBackgroundColor(ui.bg());
        content = new FrameLayout(this);
        navHost = new FrameLayout(this);
        root.addView(content, new FrameLayout.LayoutParams(-1, -1));
        root.addView(navHost, new FrameLayout.LayoutParams(-1, -1));
        setContentView(root);
        applySystemBars();
        if (Build.VERSION.SDK_INT >= 33) requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 9001);
        if (store.bool("onboarded", false)) showHome(); else showOnboarding();
        if (!store.str("privacyPassword", "").isEmpty()) showLock();
        checkBrandDataUpdate(false);
    }

    private void loadBrandData() {
        JSONObject root = null;
        String cached = brandPrefs.getString("json", null);
        if (cached != null) {
            try { root = new JSONObject(cached); } catch (Exception ignored) { root = null; }
        }
        if (root == null) {
            try (java.io.InputStream in = getAssets().open("brands/brands.json")) {
                java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
                byte[] buf = new byte[8192]; int n;
                while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
                root = new JSONObject(new String(bos.toByteArray(), java.nio.charset.StandardCharsets.UTF_8));
            } catch (Exception ignored) {}
        }
        if (root != null) applyBrandRoot(root);
    }

    private boolean applyBrandRoot(JSONObject root) {
        if (root == null) return false;
        int schema = root.optInt("schema", 1);
        if (schema > BRAND_SCHEMA) return false;
        JSONObject sup = root.optJSONObject("supportedApp");
        if (sup != null) {
            int lo = sup.optInt("minVersionCode", 0);
            int hi = sup.optInt("maxVersionCode", 0);
            if (APP_VERSION_CODE < lo || (hi > 0 && APP_VERSION_CODE > hi)) return false;
        }
        JSONObject brands = root.optJSONObject("brands");
        if (brands == null) brands = root;
        brandData = brands;
        brandModels.clear();
        brandAliases.clear();
        for (java.util.Iterator<String> it = brands.keys(); it.hasNext(); ) {
            String cat = it.next();
            JSONArray arr = brands.optJSONArray(cat);
            for (int i = 0; arr != null && i < arr.length(); i++) {
                JSONArray item = arr.optJSONArray(i);
                if (item == null) continue;
                String name = item.optString(0), id = item.optString(1);
                JSONArray modelsArr = item.optJSONArray(3);
                if (modelsArr != null) brandModels.put(id, modelsArr);
                JSONArray als = item.optJSONArray(2);
                if (als != null) for (int k = 0; k < als.length(); k++) {
                    String a = als.optString(k);
                    if (a.isEmpty()) continue;
                    boolean latin = a.matches(".*[A-Za-z].*");
                    brandAliases.add(new String[]{a.toLowerCase(java.util.Locale.US), name, id, latin ? "1" : "0"});
                }
            }
        }
        brandAliases.sort((x, y) -> Integer.compare(y[0].length(), x[0].length()));
        brandDataVersion = root.optInt("dataVersion", 0);
        brandDataVersionName = root.optString("dataVersionName", String.valueOf(brandDataVersion));
        return true;
    }

    private String fetchBrandJson(String url, String accept) throws Exception {
        java.net.HttpURLConnection c = (java.net.HttpURLConnection) new java.net.URL(url).openConnection();
        c.setConnectTimeout(8000);
        c.setReadTimeout(8000);
        c.setRequestProperty("Accept", accept);
        c.setRequestProperty("User-Agent", "Xiaoman/" + APP_VERSION_CODE);
        c.setRequestProperty("Cache-Control", "no-cache");
        c.setRequestProperty("Pragma", "no-cache");
        c.setUseCaches(false);
        c.setInstanceFollowRedirects(true);
        int code = c.getResponseCode();
        if (code != 200) throw new java.io.IOException("HTTP " + code);
        java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
        try (java.io.InputStream in = c.getInputStream()) {
            byte[] buf = new byte[8192]; int n;
            while ((n = in.read(buf)) > 0) {
                bos.write(buf, 0, n);
                if (bos.size() > 2 * 1024 * 1024) throw new java.io.IOException("too large");
            }
        }
        return new String(bos.toByteArray(), java.nio.charset.StandardCharsets.UTF_8);
    }

    private void checkBrandDataUpdate(boolean manual) {
        if (manual) toast("正在检查品牌数据更新…");
        new Thread(() -> {
            try {
                String text = null;
                try {
                    // Contents API 不走 raw CDN 缓存，能立即读到最新提交
                    text = fetchBrandJson(BRAND_DATA_API_URL, "application/vnd.github.raw");
                } catch (Exception e) {
                    android.util.Log.w(BRAND_DATA_TAG, "api fetch failed: " + e);
                }
                if (text == null) {
                    text = fetchBrandJson(BRAND_DATA_URL + "?t=" + System.currentTimeMillis(), "application/json");
                }
                JSONObject remote = new JSONObject(text);
                int schema = remote.optInt("schema", 1);
                JSONObject sup = remote.optJSONObject("supportedApp");
                int lo = sup != null ? sup.optInt("minVersionCode", 0) : 0;
                int hi = sup != null ? sup.optInt("maxVersionCode", 0) : 0;
                android.util.Log.i(BRAND_DATA_TAG, "remote schema=" + schema + " ver=" + remote.optInt("dataVersion", 0)
                        + " app=" + APP_VERSION_CODE + " supported=" + lo + "-" + hi);
                if (schema > BRAND_SCHEMA || APP_VERSION_CODE < lo || (hi > 0 && APP_VERSION_CODE > hi)) {
                    if (manual) runOnUiThread(() -> toast("当前版本不支持该数据"));
                    return;
                }
                int ver = remote.optInt("dataVersion", 0);
                if (ver > brandDataVersion) {
                    brandPrefs.edit().putString("json", text).apply();
                    runOnUiThread(() -> {
                        if (applyBrandRoot(remote)) {
                            toast("品牌与型号已更新至 " + brandDataVersionName);
                            if ("settings".equals(screen)) showSettings();
                        }
                    });
                } else if (manual) {
                    runOnUiThread(() -> toast("已是最新版本 v" + brandDataVersionName));
                }
            } catch (Exception e) {
                android.util.Log.w(BRAND_DATA_TAG, "update failed: " + e);
                if (manual) runOnUiThread(() -> toast("检查更新失败，请检查网络"));
            }
        }).start();
    }

    private void applySystemBars() {
        Window w = getWindow();
        w.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(ui.bg()));
        w.setStatusBarColor(ui.bg());
        w.setNavigationBarColor(ui.bg());
        int flags = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
        if (Build.VERSION.SDK_INT >= 26) flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        w.getDecorView().setSystemUiVisibility(dark ? 0 : flags);
        if (Build.VERSION.SDK_INT >= 29) {
            w.setStatusBarContrastEnforced(false);
            w.setNavigationBarContrastEnforced(false);
        }
    }

    // ================= 通用容器 =================
    private int dp(float v) { return ui.dp(v); }

    private LinearLayout.LayoutParams lp(int w, int h) { return new LinearLayout.LayoutParams(w, h); }
    private LinearLayout.LayoutParams lpw(float weight, int h) { return new LinearLayout.LayoutParams(0, h, weight); }
    private LinearLayout.LayoutParams lpm(int w, int h, int l, int t, int r, int bt) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(w, h);
        p.setMargins(dp(l), dp(t), dp(r), dp(bt));
        return p;
    }

    private ScrollView scrollPage(LinearLayout body) {
        ScrollView sv = new ScrollView(this);
        sv.setFillViewport(true);
        sv.setVerticalScrollBarEnabled(false);
        sv.addView(body, new FrameLayout.LayoutParams(-1, -2));
        return sv;
    }

    private LinearLayout pageBody() {
        LinearLayout body = ui.vbox();
        body.setPadding(0, 0, 0, dp(110));
        return body;
    }

    private void setScreen(View v, boolean nav, String activeTab) {
        root.setBackgroundColor(ui.bg());
        content.setBackgroundColor(ui.bg());
        applySystemBars();
        content.removeAllViews();
        content.addView(v, new FrameLayout.LayoutParams(-1, -1));
        navHost.removeAllViews();
        if (nav) {
            navHost.addView(buildNav(activeTab));
            tab = activeTab;
        }
    }

    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); }

    // ================= 底部导航 =================
    private View buildNav(String active) {
        FrameLayout host = new FrameLayout(this);
        LinearLayout bar = ui.hbox();
        bar.setBackground(ui.round(ui.card(), 32));
        bar.setElevation(dp(8));
        bar.setPadding(dp(6), 0, dp(6), 0);
        FrameLayout.LayoutParams bp = new FrameLayout.LayoutParams(-1, dp(64), Gravity.BOTTOM);
        bp.setMargins(dp(14), 0, dp(78), dp(22));
        host.addView(bar, bp);

        String[][] tabs = {{"home", "小满", "tab_home"}, {"wishes", "心愿", "tab_wish"}, {"trends", "趋势", "tab_trend"}, {"settings", "设置", "tab_settings"}};
        for (String[] t : tabs) {
            boolean on = t[0].equals(active);
            LinearLayout item = ui.vbox();
            item.setGravity(Gravity.CENTER);
            ImageView tabIcon = ui.icon(t[2] + (on ? "_s" : "_n"), 22);
            if (dark) tabIcon.setColorFilter(on ? 0xFFF2F2F2 : 0xFF8A8A8A);
            FrameLayout iconWrap = new FrameLayout(this);
            if (on) iconWrap.setBackground(ui.circle(dark ? 0xFF2C2C2C : 0xFFF0F0F0));
            iconWrap.addView(tabIcon, new FrameLayout.LayoutParams(dp(22), dp(22), Gravity.CENTER));
            item.addView(iconWrap, new LinearLayout.LayoutParams(dp(34), dp(34)));
            TextView label = ui.text(t[1], 10, on ? ui.ink() : ui.gray(), on ? ui.medium : ui.regular);
            label.setGravity(Gravity.CENTER);
            label.setPadding(0, dp(2), 0, 0);
            item.addView(label);
            item.setOnClickListener(v -> switchTab(t[0]));
            bar.addView(item, lpw(1, -1));
        }

        TextView fab = ui.text("+", 28, dark ? 0xFF111111 : 0xFFFFFFFF, ui.regular);
        fab.setGravity(Gravity.CENTER);
        fab.setBackground(ui.circle(dark ? 0xFFF2F2F2 : 0xFF111111));
        fab.setElevation(dp(8));
        FrameLayout.LayoutParams fp = new FrameLayout.LayoutParams(dp(64), dp(64), Gravity.BOTTOM | Gravity.END);
        fp.setMargins(0, 0, dp(14), dp(22));
        fab.setOnClickListener(v -> openAdd(null, "wishes".equals(tab) ? "wish" : "asset"));
        host.addView(fab, fp);
        return host;
    }

    private void switchTab(String t) {
        if ("wishes".equals(t)) showWishes();
        else if ("trends".equals(t)) showTrends();
        else if ("settings".equals(t)) showSettings();
        else showHome();
    }

    // ================= 引导页 =================
    private int obPage = 0;
    private static final String[][] OB = {
            {"资产账户", "财务状况一目了然", "img/onboard1.png"},
            {"每日成本", "价值、成本、状态，一键分析", "img/onboard2.png"},
            {"数据统计", "多种统计图表", "img/onboard3.png"},
    };

    private void showOnboarding() {
        screen = "onboard";
        LinearLayout body = ui.vbox();
        body.setBackground(ui.gradient(0xFFFDFEF8, 0xFFF7FBE4, 0xFFEEF8CD));
        body.setPadding(dp(28), dp(56), dp(28), dp(40));

        ImageView art = ui.image(OB[obPage][2], 200);
        LinearLayout.LayoutParams ap = new LinearLayout.LayoutParams(dp(200), dp(200));
        ap.gravity = Gravity.CENTER_HORIZONTAL;
        ap.topMargin = dp(30);
        art.setLayoutParams(ap);
        body.addView(art);

        TextView title = ui.text(OB[obPage][0], 40, 0xFF111111, ui.bold);
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(-2, -2);
        tp.topMargin = dp(70);
        body.addView(title, tp);
        TextView sub = ui.text(OB[obPage][1], 14, 0xFF5F6352);
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(-2, -2);
        sp.topMargin = dp(12);
        body.addView(sub, sp);

        body.addView(new View(this), new LinearLayout.LayoutParams(1, 0, 1));

        LinearLayout foot = ui.hbox();
        LinearLayout dots = ui.hbox();
        for (int i = 0; i < OB.length; i++) {
            View dot = new View(this);
            LinearLayout.LayoutParams dp2 = new LinearLayout.LayoutParams(dp(i == obPage ? 22 : 18), dp(4));
            dp2.rightMargin = dp(8);
            dot.setLayoutParams(dp2);
            dot.setBackground(ui.round(i == obPage ? 0xFF1A1A1A : 0xFFC9CDA9, 2));
            dots.addView(dot);
        }
        foot.addView(dots, lpw(1, -2));

        FrameLayout next = new FrameLayout(this);
        next.setBackground(ui.round(Ui.LIME, 16));
        ImageView arrow = new ImageView(this);
        arrow.setImageResource(R.drawable.ic_next);
        next.addView(arrow, new FrameLayout.LayoutParams(dp(24), dp(24), Gravity.CENTER));
        next.setOnClickListener(v -> {
            if (obPage < OB.length - 1) { obPage++; showOnboarding(); }
            else { store.setBool("onboarded", true); showHome(); }
        });
        foot.addView(next, new LinearLayout.LayoutParams(dp(54), dp(54)));
        body.addView(foot);
        setScreen(body, false, null);
    }

    // ================= 首页 / 心愿 =================
    private void showHome() {
        screen = "home";
        tab = "home";
        LinearLayout body = pageBody();
        body.addView(header());
        body.addView(overviewCard());

        HorizontalScrollView catScroll = new HorizontalScrollView(this);
        catScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout catRow = ui.hbox();
        catRow.setPadding(0, 0, 0, 0);
        java.util.List<JSONObject> catsList = store.objs("categories");
        TextView allCat = ui.text("全部", 13, categoryFilter.equals("all") ? ui.ink() : ui.gray(), categoryFilter.equals("all") ? ui.medium : ui.regular);
        allCat.setGravity(Gravity.CENTER);
        allCat.setPadding(dp(14), 0, dp(14), 0);
        allCat.setOnClickListener(v -> { categoryFilter = "all"; showHome(); });
        catRow.addView(allCat, new LinearLayout.LayoutParams(-2, dp(44)));
        for (JSONObject c : catsList) {
            String cid = c.optString("id");
            boolean on = categoryFilter.equals(cid);
            TextView ct = ui.text(c.optString("name"), 13, on ? ui.ink() : ui.gray(), on ? ui.medium : ui.regular);
            ct.setGravity(Gravity.CENTER);
            ct.setPadding(dp(14), 0, dp(14), 0);
            ct.setOnClickListener(v -> { categoryFilter = cid; showHome(); });
            catRow.addView(ct, new LinearLayout.LayoutParams(-2, dp(44)));
        }
        boolean uncategorized = categoryFilter.equals("__none__");
        TextView nc = ui.text("未分类", 13, uncategorized ? ui.ink() : ui.gray(), uncategorized ? ui.medium : ui.regular);
        nc.setGravity(Gravity.CENTER);
        nc.setPadding(dp(14), 0, dp(14), 0);
        nc.setOnClickListener(v -> { categoryFilter = "__none__"; showHome(); });
        catRow.addView(nc, new LinearLayout.LayoutParams(-2, dp(44)));
        catScroll.addView(catRow);
        body.addView(catScroll);

        LinearLayout filters = ui.hbox();
        filters.setPadding(dp(8), 0, dp(16), dp(4));

        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        LinearLayout chips = ui.hbox();
        String[][] defs = {{"all", "全部"}, {"serving", "服役中"}, {"retired", "已退役"}, {"sold", "已卖出"}};
        for (String[] d : defs) {
            boolean on = filter.equals(d[0]);
            TextView chip = ui.text(d[1], 11, on ? ui.chipOnInk() : ui.gray(), on ? ui.medium : ui.regular);
            chip.setGravity(Gravity.CENTER);
            chip.setPadding(dp(14), 0, dp(14), 0);
            if (on) chip.setBackground(ui.round(ui.chipOnBg(), 20));
            chip.setOnClickListener(v -> { filter = d[0]; showHome(); });
            chips.addView(chip, new LinearLayout.LayoutParams(-2, dp(40)));
        }
        chips.setPadding(dp(2), 0, 0, 0);
        hs.addView(chips);
        filters.addView(hs, lpw(1, -2));

        ImageView sort = ui.icon("ic_sort", 16);
        sort.setAlpha(0.65f);
        if (dark) sort.setColorFilter(0xFFF2F2F2);
        LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(dp(16), dp(16));
        ip.leftMargin = dp(12);
        filters.addView(sort, ip);
        ImageView funnel = ui.icon("ic_funnel", 16);
        funnel.setAlpha(0.65f);
        if (dark) funnel.setColorFilter(0xFFF2F2F2);
        LinearLayout.LayoutParams fp = new LinearLayout.LayoutParams(dp(16), dp(16));
        fp.leftMargin = dp(12);
        filters.addView(funnel, fp);
        ImageView view = ui.icon("ic_view", 16);
        if (dark) view.setColorFilter(0xFFF2F2F2);
        LinearLayout.LayoutParams vp = new LinearLayout.LayoutParams(dp(16), dp(16));
        vp.leftMargin = dp(12);
        filters.addView(view, vp);
        view.setOnClickListener(v -> { grid = !grid; showHome(); });
        body.addView(filters);

        if (!"all".equals(categoryFilter) && !"__none__".equals(categoryFilter)) {
            boolean exists = false;
            for (JSONObject c : catsList) if (c.optString("id").equals(categoryFilter)) exists = true;
            if (!exists) categoryFilter = "all";
        }
        List<JSONObject> list = store.ofKind("asset");
        List<JSONObject> shown = new ArrayList<>();
        for (JSONObject a : list) {
            if (!"all".equals(filter) && !filter.equals(a.optString("status", "serving"))) continue;
            if ("__none__".equals(categoryFilter)) {
                if (!a.optString("categoryId").isEmpty()) continue;
            } else if (!"all".equals(categoryFilter) && !categoryFilter.equals(a.optString("categoryId"))) continue;
            shown.add(a);
        }
        if (shown.isEmpty()) body.addView(emptyState(), new LinearLayout.LayoutParams(-1, -2));
        else body.addView(assetGrid(shown));
        setScreen(scrollPage(body), true, "home");
    }

    private boolean grid = true;

    private View header() {
        FrameLayout head = new FrameLayout(this);
        head.setLayoutParams(new LinearLayout.LayoutParams(-1, dp(150)));
        ImageView bg = new ImageView(this);
        bg.setImageBitmap(ui.bitmap("img/head_bg.png"));
        bg.setScaleType(ImageView.ScaleType.FIT_XY);
        head.addView(bg, new FrameLayout.LayoutParams(-1, -1));
        LinearLayout row = ui.hbox();
        row.setPadding(dp(14), dp(12), dp(14), 0);
        TextView logo = ui.text("小满", 24, 0xFF1A1A1A, ui.bold);
        row.addView(logo, lpw(1, -2));
        ImageView search = ui.icon("ic_search", 22);
        row.addView(search);
        BookmarkView fav = new BookmarkView(this, ui.ink());
        LinearLayout.LayoutParams f = new LinearLayout.LayoutParams(dp(20), dp(22));
        f.leftMargin = dp(16);
        row.addView(fav, f);
        head.addView(row, new FrameLayout.LayoutParams(-1, -2));
        return head;
    }

    private View overviewCard() {
        LinearLayout card = ui.vbox();
        card.setBackground(ui.round(ui.card(), 20));
        card.setElevation(dp(3));
        card.setPadding(dp(20), dp(22), dp(20), dp(16));

        LinearLayout top = ui.hbox();
        TextView title = ui.text("资产总览", 16, ui.ink(), ui.bold);
        top.addView(title);
        TextView count = ui.text(store.count("asset", "serving") + "/" + store.ofKind("asset").size(), 11, ui.gray());
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(-2, -2);
        cp.leftMargin = dp(8);
        top.addView(count, cp);
        card.addView(top);

        LinearLayout nums = ui.hbox();
        LinearLayout.LayoutParams np = new LinearLayout.LayoutParams(-1, -2);
        np.topMargin = dp(16);
        card.addView(nums, np);

        LinearLayout left = ui.vbox();
        left.addView(ui.text("总资产", 11, ui.gray()));
        left.addView(moneyLine(store.total("asset"), 22));
        nums.addView(left, lpw(1, -2));

        LinearLayout right = ui.vbox();
        right.setGravity(Gravity.END);
        TextView lbl = ui.text("日均成本", 11, ui.gray());
        lbl.setGravity(Gravity.END);
        right.addView(lbl);
        right.addView(moneyLine(store.daily("asset"), 22));
        nums.addView(right, lpw(1, -2));

        LinearLayout stats = ui.hbox();
        LinearLayout.LayoutParams stp = new LinearLayout.LayoutParams(-1, -2);
        stp.topMargin = dp(22);
        card.addView(stats, stp);
        String[][] defs = {{"服役中", "serving"}, {"已退役", "retired"}, {"已卖出", "sold"}};
        int total = Math.max(1, store.ofKind("asset").size());
        for (int i = 0; i < defs.length; i++) {
            LinearLayout col = ui.vbox();
            LinearLayout cap = ui.hbox();
            cap.addView(ui.text(defs[i][0], 11, ui.ink2()), lpw(1, -2));
            cap.addView(ui.text(String.valueOf(store.count("asset", defs[i][1])), 11, ui.ink(), ui.medium));
            col.addView(cap);
            View bar = ui.bar(store.count("asset", defs[i][1]) / (float) total, Ui.LIME_DEEP);
            LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(-1, dp(6));
            bp.topMargin = dp(6);
            col.addView(bar, bp);
            stats.addView(col, lpw(1, -2));
            if (i < defs.length - 1) stats.addView(new View(this), new LinearLayout.LayoutParams(dp(14), 1));
        }

        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(-1, -2);
        cardLp.setMargins(dp(12), -dp(100), dp(12), 0);
        FrameLayout holder = new FrameLayout(this);
        holder.addView(card, new FrameLayout.LayoutParams(-1, -2));
        holder.setLayoutParams(cardLp);
        return holder;
    }

    private View moneyLine(double v, float size) {
        LinearLayout row = ui.hbox();
        row.setGravity(Gravity.BOTTOM);
        TextView s = ui.text(store.sym(), 12, ui.ink(), ui.medium);
        TextView m = ui.text(store.fmt(v, store.settingInt("decimals", 2)), size, ui.ink(), ui.bold);
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(-2, -2);
        sp.rightMargin = dp(2);
        row.addView(s, sp);
        row.addView(m);
        return row;
    }

    private FrameLayout circleIconButton(String iconName, Runnable onClick) {
        FrameLayout btn = new FrameLayout(this);
        btn.setBackground(ui.circle(dark ? 0xFF2A2A2A : ui.card()));
        btn.setElevation(dp(2));
        ImageView iv = themedIcon(iconName, 16);
        btn.addView(iv, new FrameLayout.LayoutParams(dp(16), dp(16), Gravity.CENTER));
        btn.setOnClickListener(v -> onClick.run());
        return btn;
    }

    private ImageView themedIcon(String name, int sizeDp) {
        ImageView iv = ui.icon(name, sizeDp);
        if (dark) iv.setColorFilter(ui.ink2());
        return iv;
    }

    private View moneyLineSuffix(double v, float size, String suffix) {
        LinearLayout row = ui.hbox();
        row.setGravity(Gravity.CENTER);
        TextView s = ui.text(store.sym(), 12, ui.ink(), ui.medium);
        TextView m = ui.text(store.fmt(v, store.settingInt("decimals", 2)), size, ui.ink(), ui.bold);
        TextView sfx = ui.text(suffix, 12, ui.ink2());
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(-2, -2);
        sp.rightMargin = dp(2);
        row.addView(s, sp);
        row.addView(m);
        row.addView(sfx);
        return row;
    }

    private View emptyState() {
        LinearLayout box = ui.vbox();
        box.setGravity(Gravity.CENTER);
        box.setPadding(0, dp(90), 0, dp(40));
        ImageView img = ui.image("img/empty.png", 96);
        img.setAlpha(0.9f);
        box.addView(img);
        TextView t = ui.text("空空如也", 14, ui.ink2(), ui.medium);
        t.setGravity(Gravity.CENTER);
        t.setPadding(0, dp(14), 0, 0);
        box.addView(t);
        TextView s = ui.text("点击 + 添加资产", 11, ui.gray());
        s.setGravity(Gravity.CENTER);
        s.setPadding(0, dp(6), 0, 0);
        box.addView(s);
        return box;
    }

    private View assetGrid(List<JSONObject> list) {
        LinearLayout wrap = ui.vbox();
        wrap.setPadding(dp(9), dp(6), dp(9), 0);
        int cols = grid ? 2 : 1;
        LinearLayout row = null;
        for (int i = 0; i < list.size(); i++) {
            if (i % cols == 0) {
                row = ui.hbox();
                row.setGravity(Gravity.TOP);
                wrap.addView(row, new LinearLayout.LayoutParams(-1, -2));
            }
            LinearLayout cell = ui.vbox();
            cell.setPadding(dp(5), dp(5), dp(5), dp(5));
            cell.addView(assetCard(list.get(i)));
            row.addView(cell, new LinearLayout.LayoutParams(0, -2, 1f));
        }
        if (cols == 2 && list.size() % 2 == 1 && row != null)
            row.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1f));
        return wrap;
    }

    private View assetCard(JSONObject a) {
        FrameLayout card = new FrameLayout(this);
        card.setBackground(ui.round(ui.card(), 16));
        card.setElevation(dp(2));
        card.setOnClickListener(v -> showDetail(a.optString("id")));

        LinearLayout inner = ui.vbox();
        inner.setPadding(dp(12), dp(12), dp(12), dp(12));
        View icon;
        String cvp = a.optString("coverPath");
        if (!cvp.isEmpty() && new java.io.File(cvp).exists()) icon = ui.fileImage(cvp, 44);
        else icon = ui.image(a.optString("icon", "emoji/icon_default_object.png"), 44);
        inner.addView(icon);
        TextView name = ui.text(displayName(a), 14, ui.ink(), ui.medium);
        name.setSingleLine(true);
        name.setPadding(0, dp(10), 0, 0);
        inner.addView(name);
        String brandPrefix = a.optString("brandName").isEmpty() ? "" : a.optString("brandName") + " ｜ ";
        TextView sub = ui.text(brandPrefix + store.sym() + store.fmt(a.optDouble("price"), store.settingInt("decimals", 2)) + " ｜ " + store.ageDays(a) + " 天", 10, ui.gray());
        sub.setSingleLine(true);
        sub.setPadding(0, dp(3), 0, 0);
        inner.addView(sub);
        LinearLayout cost = ui.hbox();
        cost.setGravity(Gravity.BOTTOM);
        cost.setPadding(0, dp(8), 0, 0);
        TextView c = ui.text(store.sym() + store.fmt(store.dailyCost(a), store.settingInt("decimals", 2)), 13, ui.ink(), ui.bold);
        TextView per = ui.text("/天", 10, ui.ink2(), ui.regular);
        cost.addView(c);
        cost.addView(per);
        inner.addView(cost);
        card.addView(inner, new FrameLayout.LayoutParams(-1, -2));

        String status = a.optString("status", "serving");
        TextView badge = ui.text(Store.statusText(status), 9, badgeInk(status), ui.medium);
        badge.setBackground(ui.round(badgeBg(status), 10));
        badge.setPadding(dp(7), dp(3), dp(7), dp(3));
        FrameLayout.LayoutParams bp = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.END);
        bp.setMargins(0, dp(10), dp(10), 0);
        card.addView(badge, bp);
        return card;
    }

    private int badgeBg(String s) {
        if ("retired".equals(s)) return dark ? 0xFF2A2A2A : 0xFFF0F0F0;
        if ("sold".equals(s)) return dark ? 0xFF3A2419 : 0xFFFFF0E8;
        return dark ? 0xFF22300C : 0xFFF1F8DC;
    }

    private int badgeInk(String s) {
        if ("retired".equals(s)) return dark ? 0xFF9A9A9A : 0xFF8D8D8D;
        if ("sold".equals(s)) return dark ? 0xFFE08A5E : 0xFFC75B28;
        return dark ? 0xFFB9D97A : 0xFF5D7A14;
    }

    private void showWishes() {
        screen = "wishes";
        LinearLayout body = pageBody();
        LinearLayout head = ui.hbox();
        head.setPadding(dp(18), dp(14), dp(18), dp(10));
        head.addView(ui.text("心愿", 24, ui.ink(), ui.bold));
        body.addView(head);

        List<JSONObject> list = store.ofKind("wish");
        FrameLayout card = new FrameLayout(this);
        card.setBackground(ui.round(ui.card(), 20));
        card.setElevation(dp(2));
        card.setPadding(dp(20), dp(20), dp(20), dp(20));
        LinearLayout info = ui.vbox();
        info.addView(ui.text("心愿总值", 12, ui.gray()));
        info.addView(moneyLine(store.total("wish"), 30));
        TextView cnt = ui.text("心愿数量 " + list.size() + " 个", 11, ui.gray());
        cnt.setPadding(0, dp(10), 0, 0);
        info.addView(cnt);
        card.addView(info, new FrameLayout.LayoutParams(-2, -2));
        ImageView bottle = ui.icon("ic_bottle", 64);
        bottle.setColorFilter(Ui.LIME_DEEP);
        card.addView(bottle, new FrameLayout.LayoutParams(dp(64), dp(64), Gravity.END));
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(-1, -2);
        cp.setMargins(dp(14), dp(4), dp(14), 0);
        card.setLayoutParams(cp);
        body.addView(card);

        if (list.isEmpty()) body.addView(emptyState(), new LinearLayout.LayoutParams(-1, -2));
        else {
            LinearLayout.LayoutParams gp = new LinearLayout.LayoutParams(-1, -2);
            gp.topMargin = dp(14);
            body.addView(assetGrid(list), gp);
        }
        setScreen(scrollPage(body), true, "wishes");
    }

    // ================= 趋势 =================
    private void showTrends() {
        screen = "trends";
        LinearLayout body = pageBody();
        LinearLayout head = ui.hbox();
        head.setPadding(dp(18), dp(14), dp(18), dp(10));
        TextView trendPill = ui.text("趋势", 15, ui.ink(), ui.medium);
        trendPill.setBackground(ui.round(ui.chipBg(), 18));
        trendPill.setPadding(dp(16), dp(7), dp(16), dp(7));
        head.addView(trendPill);
        body.addView(head);

        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        LinearLayout chips = ui.hbox();
        chips.setPadding(dp(16), 0, dp(16), dp(8));
        String[][] defs = {{"all", "全部"}, {"week", "最近一周"}, {"d90", "最近90天"}, {"d180", "最近180天"}, {"year", "最近一年"}};
        for (String[] d : defs) {
            TextView chip = ui.chip(d[1], range.equals(d[0]));
            chip.setOnClickListener(v -> { range = d[0]; showTrends(); });
            LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(-2, -2);
            cp.rightMargin = dp(8);
            chips.addView(chip, cp);
        }
        hs.addView(chips);
        body.addView(hs);

        LinearLayout t1 = trendCard();
        t1.addView(ui.text("资产总值", 12, ui.gray()));
        t1.addView(moneyLine(store.total("asset"), 22));
        String[][] rows = {{"服役", "serving", "0xFFB6EE25"}, {"退役", "retired", "0xFF3ED6B0"}, {"已卖出", "sold", "0xFFFF7A45"}};
        int total = Math.max(1, store.ofKind("asset").size());
        for (String[] r : rows) {
            LinearLayout block = ui.vbox();
            block.setPadding(0, dp(10), 0, dp(10));
            LinearLayout line = ui.hbox();
            View dot = new View(this);
            dot.setBackground(ui.circle(Color.parseColor(r[2].replace("0x", "#"))));
            LinearLayout.LayoutParams dp2 = new LinearLayout.LayoutParams(dp(6), dp(6));
            dp2.rightMargin = dp(8);
            line.addView(dot, dp2);
            line.addView(ui.text(r[0], 12, ui.ink2()));
            int n = store.count("asset", r[1]);
            TextView pct = ui.text("  " + Math.round(n * 100f / total) + "%", 10, ui.gray());
            line.addView(pct);
            block.addView(line);
            TextView v = ui.text(store.money(r[1].equals("serving") ? store.daily("asset") : 0), 15, ui.ink(), ui.bold);
            v.setPadding(0, dp(4), 0, 0);
            block.addView(v);
            t1.addView(block);
            t1.addView(ui.hline());
        }
        body.addView(t1);

        LinearLayout t2 = trendCard();
        t2.addView(ui.text("资产价值趋势", 13, ui.ink(), ui.medium));
        TextView big = ui.text(store.money(store.total("asset")), 17, ui.ink(), ui.bold);
        big.setPadding(0, dp(6), 0, 0);
        t2.addView(big);
        ChartView chart = new ChartView(this);
        chart.setData(trendSeries(rangeDays()), store.fmt(chartMax(trendSeries(rangeDays())), 0), store.fmt(chartMax(trendSeries(rangeDays())) / 2, 0), "0", "", "");
        LinearLayout.LayoutParams chp = new LinearLayout.LayoutParams(-1, dp(150));
        chp.topMargin = dp(8);
        t2.addView(chart, chp);
        body.addView(t2);

        LinearLayout t3 = trendCard();
        t3.addView(ui.text("类型分布", 13, ui.ink(), ui.medium));
        List<JSONObject> assets = store.ofKind("asset");
        if (assets.isEmpty()) t3.addView(ui.text("暂无数据", 11, ui.gray()));
        else {
            int[] colors = {Ui.LIME, Ui.TEAL, Ui.ORANGE, 0xFF7AA2FF, 0xFFFFD166};
            List<String> names = new ArrayList<>();
            List<Integer> counts = new ArrayList<>();
            for (JSONObject a : assets) {
                String cat = "未分类";
                String cid = a.optString("categoryId");
                for (JSONObject c : store.objs("categories"))
                    if (c.optString("id").equals(cid)) cat = c.optString("name");
                int idx = names.indexOf(cat);
                if (idx < 0) { names.add(cat); counts.add(1); } else counts.set(idx, counts.get(idx) + 1);
            }
            for (int i = 0; i < names.size(); i++) {
                LinearLayout row = ui.hbox();
                LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(-1, -2);
                rp.topMargin = dp(8);
                row.addView(ui.text(names.get(i), 11, ui.ink2()), lpw(1, -2));
                row.addView(ui.text(counts.get(i) + " 件", 11, ui.ink2()));
                t3.addView(row, rp);
                View bar = ui.bar(counts.get(i) / (float) assets.size(), colors[i % colors.length]);
                LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(-1, dp(5));
                bp.topMargin = dp(5);
                t3.addView(bar, bp);
            }
            LinearLayout totalBox = ui.vbox();
            totalBox.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(-1, -2);
            tlp.topMargin = dp(16);
            TextView totalLabel = ui.text("总计（件）", 11, ui.gray());
            totalLabel.setGravity(Gravity.CENTER);
            totalBox.addView(totalLabel);
            TextView totalValue = ui.text(String.valueOf(assets.size()), 22, ui.ink(), ui.bold);
            totalValue.setGravity(Gravity.CENTER);
            totalValue.setPadding(0, dp(4), 0, 0);
            totalBox.addView(totalValue);
            t3.addView(totalBox, tlp);
        }
        body.addView(t3);

        setScreen(scrollPage(body), true, "trends");
    }

    private LinearLayout trendCard() {
        LinearLayout card = ui.vbox();
        card.setBackground(ui.round(ui.card(), 18));
        card.setElevation(dp(2));
        card.setPadding(dp(18), dp(18), dp(18), dp(18));
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(-1, -2);
        cp.setMargins(dp(14), 0, dp(14), dp(12));
        card.setLayoutParams(cp);
        return card;
    }

    private int rangeDays() {
        switch (range) {
            case "week": return 7;
            case "d90": return 90;
            case "d180": return 180;
            default: return 365;
        }
    }

    private double[] trendSeries(int days) {
        double[] out = new double[days];
        List<JSONObject> assets = store.ofKind("asset");
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DAY_OF_MONTH, -(days - 1));
        java.text.SimpleDateFormat f = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US);
        for (int i = 0; i < days; i++) {
            String date = f.format(cal.getTime());
            double v = 0;
            for (JSONObject a : assets) {
                String buy = a.optString("buyDate");
                String sold = a.optString("soldDate");
                if (buy.compareTo(date) <= 0 && (sold.isEmpty() || sold.compareTo(date) >= 0)) v += a.optDouble("price");
            }
            out[i] = v;
            cal.add(Calendar.DAY_OF_MONTH, 1);
        }
        return out;
    }

    private double chartMax(double[] data) {
        double m = 0;
        for (double v : data) m = Math.max(m, v);
        return Math.max(m, 100);
    }

    // ================= 设置 =================
    private void showSettings() {
        screen = "settings";
        LinearLayout body = pageBody();
        LinearLayout head = ui.hbox();
        head.setPadding(dp(18), dp(14), dp(18), dp(10));
        head.addView(ui.text("设置", 24, ui.ink(), ui.bold), lpw(1, -2));
        body.addView(head);

        LinearLayout login = ui.hbox();
        login.setPadding(dp(14), dp(10), dp(14), dp(8));
        FrameLayout avatar = new FrameLayout(this);
        avatar.setBackground(ui.circle(dark ? 0xFF2A2A2A : 0xFFE9E9E9));
        ImageView av = ui.icon("ic_view", 20);
        av.setColorFilter(ui.gray());
        avatar.addView(av, new FrameLayout.LayoutParams(dp(20), dp(20), Gravity.CENTER));
        login.addView(avatar, new LinearLayout.LayoutParams(dp(46), dp(46)));
        LinearLayout texts = ui.vbox();
        texts.setPadding(dp(12), 0, 0, 0);
        texts.addView(ui.text("点击登录", 15, ui.ink(), ui.medium));
        texts.addView(ui.text("登录后同步数据，换机不丢", 11, ui.gray()));
        login.addView(texts);
        login.setOnClickListener(v -> toast("登录功能暂未开放"));
        body.addView(login);

        section(body, "数值与单位");
        LinearLayout g1 = group();
        g1.addView(row("ic_currency", "货币单位", store.setting("currency", "CNY"), true, v -> pickCurrency()));
        g1.addView(row("ic_decimal", "小数点设置", "保留 " + store.settingInt("decimals", 2) + " 位", true, v -> pickDecimals()));
        g1.addView(switchRow("ic_comma", "使用千位分隔符", "thousands", store.settingBool("thousands", true)));
        LinearLayout durRow = row("ic_timeformat", "时长显示格式", null, false, null);
        LinearLayout durSeg = segment(new String[]{"天数", "日期"}, "date".equals(store.setting("duration", "days")) ? 1 : 0, 11, i -> {
            store.setSetting("duration", i == 0 ? "days" : "date");
            showSettings();
        });
        durRow.addView(durSeg, new LinearLayout.LayoutParams(-2, -2));
        g1.addView(durRow);
        body.addView(g1);

        section(body, "数据管理");
        LinearLayout g2 = group();
        g2.addView(row("ic_category", "分类管理", store.objs("categories").size() + " 个", true, v -> manageCategories()));
        g2.addView(row("ic_tags", "标签管理", store.objs("tags").size() + " 个", true, v -> manageTags()));
        g2.addView(row("ic_view", "品牌与型号数据", "v" + brandDataVersionName, true, v -> checkBrandDataUpdate(true)));
        g2.addView(row("ic_backup", "备份与恢复", null, true, v -> showBackupPage()));
        g2.addView(row("ic_cloud", "数据同步", null, true, v -> toast("数据同步暂未开放")));
        body.addView(g2);

        section(body, "显示与外观");
        LinearLayout g3 = group();
        LinearLayout themeRow = row("ic_theme", "主题模式", null, false, null);
        LinearLayout dots = ui.hbox();
        String theme = store.setting("theme", "system");
        String[][] modes = {{"system", "ic_mode_system"}, {"light", "ic_mode_light"}, {"dark", "ic_mode_dark"}};
        for (String[] m : modes) {
            FrameLayout dot = new FrameLayout(this);
            boolean on = m[0].equals(theme);
            dot.setBackground(ui.roundStroke(ui.card(), 14, on ? ui.ink() : ui.line(), 1));
            ImageView img = ui.icon(m[1], 16);
            dot.addView(img, new FrameLayout.LayoutParams(dp(16), dp(16), Gravity.CENTER));
            LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(dp(26), dp(26));
            dlp.leftMargin = dp(10);
            dot.setOnClickListener(v -> {
                store.setSetting("theme", m[0]);
                dark = store.isDark();
                applySystemBars();
                showSettings();
            });
            dots.addView(dot, dlp);
        }
        themeRow.addView(dots);
        g3.addView(themeRow);
        LinearLayout styleRow = row("ic_homestyle", "首页风格", null, false, null);
        styleRow.addView(segment(new String[]{"默认", "经典"}, "classic".equals(store.setting("homeStyle", "default")) ? 1 : 0, 11, i -> {
            store.setSetting("homeStyle", i == 0 ? "default" : "classic");
            showSettings();
        }), new LinearLayout.LayoutParams(-2, -2));
        g3.addView(styleRow);
        g3.addView(row("ic_display", "视图样式", grid ? "网格" : "列表", true, v -> { grid = !grid; showSettings(); }));
        body.addView(g3);

        section(body, "通用设置");
        LinearLayout g4 = group();
        g4.addView(row("ic_security", "密码与安全", store.str("privacyPassword", "").isEmpty() ? null : "已开启", true, v -> securityDialog()));
        g4.addView(row("ic_language", "语言", "system".equals(store.str("language", "zh")) ? "跟随系统" : "简体中文", true, v -> languageDialog()));
        body.addView(g4);

        section(body, "反馈与支持");
        LinearLayout g5 = group();
        g5.addView(row("ic_wechat", "客服支持", null, true, v -> doc("客服支持",
                "请通过项目主页提交问题：\nhttps://github.com/HanYunChenLuo/xiaoman/issues")));
        g5.addView(row("ic_wechat", "用户交流", null, true, v -> doc("用户交流",
                "欢迎在项目主页参与讨论：\nhttps://github.com/HanYunChenLuo/xiaoman")));
        g5.addView(row("ic_feedback", "我要提意见", null, true, v -> doc("我要提意见",
                "请通过项目主页提交 Issue：\nhttps://github.com/HanYunChenLuo/xiaoman/issues")));
        g5.addView(row("ic_contact", "联系我们", null, true, v -> doc("联系我们",
                "项目主页：https://github.com/HanYunChenLuo/xiaoman")));
        body.addView(g5);

        section(body, "关于");
        LinearLayout g6 = group();
        g6.addView(row("ic_terms", "用户协议", null, true, v -> doc("用户协议",
                "这里是《用户协议》的示例内容。\n\n小满仅将资产数据保存在本机，不会上传任何内容。")));
        g6.addView(row("ic_privacy", "隐私政策", null, true, v -> doc("隐私政策",
                "这里是《隐私政策》的示例内容。\n\n小满不收集任何个人信息，全部数据仅保存在本机。")));
        body.addView(g6);

        String vn = "";
        long vc = 0;
        try {
            android.content.pm.PackageInfo pi = getPackageManager().getPackageInfo(getPackageName(), 0);
            vn = pi.versionName;
            vc = Build.VERSION.SDK_INT >= 28 ? pi.getLongVersionCode() : pi.versionCode;
        } catch (Exception ignored) {}
        TextView ver = ui.text("版本 " + vn + "(" + vc + ")\n全部数据仅保存在本机", 9, ui.gray());
        ver.setGravity(Gravity.CENTER);
        ver.setPadding(0, dp(14), 0, dp(20));
        body.addView(ver);

        setScreen(scrollPage(body), true, "settings");
    }

    private void section(LinearLayout body, String label) {
        TextView t = ui.text(label, 11, ui.gray());
        t.setPadding(dp(20), dp(4), dp(20), dp(8));
        body.addView(t);
    }

    private LinearLayout group() {
        LinearLayout g = ui.vbox();
        g.setBackground(ui.round(ui.card(), 16));
        g.setElevation(dp(2));
        LinearLayout.LayoutParams gp = new LinearLayout.LayoutParams(-1, -2);
        gp.setMargins(dp(14), 0, dp(14), dp(14));
        g.setLayoutParams(gp);
        return g;
    }

    private interface Click { void on(View v); }

    private LinearLayout row(String icon, String label, String value, boolean chevron, Click click) {
        LinearLayout r = ui.hbox();
        r.setPadding(dp(16), 0, dp(16), 0);
        r.setMinimumHeight(dp(52));
        r.addView(themedIcon(icon, 18));
        TextView t = ui.text(label, 16, ui.ink());
        t.setPadding(dp(10), 0, 0, 0);
        r.addView(t, lpw(1, -2));
        if (value != null) {
            TextView v = ui.text(value, 12, ui.gray());
            r.addView(v);
        }
        if (chevron) {
            ImageView c = themedIcon("ic_chevron", 10);
            c.setAlpha(dark ? 0.75f : 0.35f);
            LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(dp(6), dp(10));
            cp.leftMargin = dp(8);
            r.addView(c, cp);
        }
        if (click != null) {
            r.setOnClickListener(click::on);
            r.setClickable(true);
        }
        return r;
    }

    private LinearLayout switchRow(String icon, String label, String key, boolean checked) {
        LinearLayout r = ui.hbox();
        r.setPadding(dp(16), 0, dp(16), 0);
        r.setMinimumHeight(dp(52));
        r.addView(themedIcon(icon, 18));
        TextView t = ui.text(label, 16, ui.ink());
        t.setPadding(dp(10), 0, 0, 0);
        r.addView(t, lpw(1, -2));
        Switch sw = new Switch(this);
        sw.setChecked(checked);
        if (Build.VERSION.SDK_INT >= 21) {
            sw.setThumbTintList(android.content.res.ColorStateList.valueOf(checked ? Ui.LIME_DEEP : 0xFFCCCCCC));
            sw.setTrackTintList(android.content.res.ColorStateList.valueOf(dark ? 0xFF333333 : 0xFFE0E0E0));
        }
        sw.setOnCheckedChangeListener((v, c) -> {
            store.setSetting(key, c);
            if (Build.VERSION.SDK_INT >= 21)
                sw.setThumbTintList(android.content.res.ColorStateList.valueOf(c ? Ui.LIME_DEEP : 0xFFCCCCCC));
        });
        r.addView(sw);
        return r;
    }

    private LinearLayout segment(String[] labels, int selected, float size, IntClick cb) {
        LinearLayout seg = ui.hbox();
        int bgc = dark ? 0xFF2A2A2A : 0xFFF2F2F2;
        seg.setBackground(ui.round(bgc, 10));
        seg.setPadding(dp(3), dp(3), dp(3), dp(3));
        for (int i = 0; i < labels.length; i++) {
            boolean on = i == selected;
            TextView t = ui.text(labels[i], size, on ? ui.ink() : (dark ? 0xFFB8B8B8 : ui.gray()), on ? ui.medium : ui.regular);
            t.setGravity(Gravity.CENTER);
            t.setPadding(dp(12), dp(5), dp(12), dp(5));
            if (on) t.setBackground(ui.round(dark ? 0xFF3F3F3F : ui.card(), 8));
            final int idx = i;
            t.setOnClickListener(v -> cb.on(idx));
            seg.addView(t);
        }
        return seg;
    }

    private interface IntClick { void on(int i); }

    private void pickCurrency() {
        String[] items = {"CNY", "USD", "EUR", "JPY", "GBP", "HKD"};
        String cur = store.setting("currency", "CNY");
        int sel = 0;
        for (int i = 0; i < items.length; i++) if (items[i].equals(cur)) sel = i;
        DialogKit.sheetSelect(this, "货币单位", items, sel, i -> {
            store.setSetting("currency", items[i]);
            showSettings();
        });
    }

    private void pickDecimals() {
        String[] items = {"0", "1", "2", "3"};
        int sel = 0;
        for (int i = 0; i < items.length; i++) if (i == store.settingInt("decimals", 2)) sel = i;
        DialogKit.sheetSelect(this, "小数位数", items, sel, i -> {
            store.setSetting("decimals", Integer.parseInt(items[i]));
            showSettings();
        });
    }

    private void manageCategories() {
        java.util.List<JSONObject> cats = store.objs("categories");
        String[] names = new String[cats.size()];
        for (int i = 0; i < cats.size(); i++) names[i] = cats.get(i).optString("name");
        DialogKit.manageList(this, "分类管理", "点击添加分类", names,
                () -> inputDialog("新建分类", "请输入分类名称", name -> {
                    try {
                        store.arr("categories").put(new JSONObject().put("id", "cat_" + System.currentTimeMillis()).put("name", name));
                        store.save();
                    } catch (Exception ignored) {}
                    manageCategories();
                }),
                idx -> {
                    String old = names[idx];
                    DialogKit.input(this, "重命名分类", "请输入分类名称", old, "保存", name -> {
                        for (JSONObject c : store.objs("categories"))
                            if (old.equals(c.optString("name"))) { try { c.put("name", name); } catch (Exception ignored) {} }
                        store.save();
                        manageCategories();
                    });
                },
                idx -> {
                    String target = names[idx];
                    DialogKit.confirm(this, "删除分类", "确定删除「" + target + "」？", "取消", "删除", () -> {
                        JSONArray arr = store.arr("categories");
                        for (int i = arr.length() - 1; i >= 0; i--) {
                            JSONObject c = arr.optJSONObject(i);
                            if (c != null && target.equals(c.optString("name"))) arr.remove(i);
                        }
                        store.save();
                        manageCategories();
                    });
                },
                order -> reorderArray("categories", order));
    }

    private void manageTags() {
        java.util.List<JSONObject> tags = store.objs("tags");
        String[] names = new String[tags.size()];
        for (int i = 0; i < tags.size(); i++) names[i] = tags.get(i).optString("name");
        DialogKit.manageList(this, "标签管理", "点击添加标签", names,
                () -> inputDialog("新建标签", "请输入标签名称", name -> {
                    try {
                        store.arr("tags").put(new JSONObject().put("id", "tag_" + System.currentTimeMillis()).put("name", name));
                        store.save();
                    } catch (Exception ignored) {}
                    manageTags();
                }),
                idx -> {
                    String old = names[idx];
                    DialogKit.input(this, "重命名标签", "请输入标签名称", old, "保存", name -> {
                        for (JSONObject t : store.objs("tags"))
                            if (old.equals(t.optString("name"))) { try { t.put("name", name); } catch (Exception ignored) {} }
                        store.save();
                        manageTags();
                    });
                },
                idx -> {
                    String target = names[idx];
                    DialogKit.confirm(this, "删除标签", "确定删除「" + target + "」？", "取消", "删除", () -> {
                        JSONArray arr = store.arr("tags");
                        for (int i = arr.length() - 1; i >= 0; i--) {
                            JSONObject t = arr.optJSONObject(i);
                            if (t != null && target.equals(t.optString("name"))) arr.remove(i);
                        }
                        store.save();
                        manageTags();
                    });
                },
                order -> reorderArray("tags", order));
    }

    private void reorderArray(String key, String[] order) {
        JSONArray old = store.arr(key);
        JSONArray out = new JSONArray();
        for (String name : order) {
            for (int i = 0; i < old.length(); i++) {
                JSONObject o = old.optJSONObject(i);
                if (o != null && name.equals(o.optString("name"))) { out.put(o); break; }
            }
        }
        store.putArray(key, out);
    }

    private interface TextClick { void on(String s); }

    private void inputDialog(String title, String hint, TextClick cb) {
        DialogKit.input(this, title, hint, "创建", cb::on);
    }

    private static final int REQ_RESTORE = 1001, REQ_EXPORT = 1002, REQ_COVER = 1003, REQ_NOTE = 1004;

    private void showBackupPage() {
        screen = "backup";
        LinearLayout body = ui.vbox();
        body.setPadding(0, 0, 0, dp(28));

        LinearLayout head = ui.hbox();
        head.setPadding(dp(18), dp(12), dp(18), dp(8));
        TextView back = ui.pillButton("‹", ui.card(), ui.ink(), 17);
        back.setTextSize(20);
        back.setPadding(dp(12), dp(2), dp(14), dp(4));
        back.setElevation(dp(2));
        back.setOnClickListener(v -> showSettings());
        head.addView(back);
        body.addView(head);

        LinearLayout center = ui.vbox();
        center.setGravity(Gravity.CENTER);
        center.setPadding(0, dp(14), 0, dp(20));
        FrameLayout bigIcon = new FrameLayout(this);
        bigIcon.setBackground(ui.round(Ui.LIME_DEEP, 20));
        TextView arrow = ui.text("↑", 34, 0xFFFFFFFF, ui.bold);
        arrow.setGravity(Gravity.CENTER);
        bigIcon.addView(arrow, new FrameLayout.LayoutParams(dp(76), dp(76), Gravity.CENTER));
        LinearLayout.LayoutParams bip = new LinearLayout.LayoutParams(dp(76), dp(76));
        bip.gravity = Gravity.CENTER_HORIZONTAL;
        center.addView(bigIcon, bip);
        TextView bt = ui.text("备份与恢复", 20, ui.ink(), ui.bold);
        bt.setGravity(Gravity.CENTER);
        center.addView(bt, new LinearLayout.LayoutParams(-1, -2));
        TextView cnt = ui.text(store.ofKind("asset").size() + "条记录", 11, ui.gray());
        cnt.setGravity(Gravity.CENTER);
        cnt.setPadding(0, dp(8), 0, 0);
        center.addView(cnt, new LinearLayout.LayoutParams(-1, -2));
        body.addView(center, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout g = group();
        g.addView(row("ic_backup", "备份到本地", "导出数据包", true, v -> createBackupFile()));
        g.addView(row("ic_restore", "从备份文件恢复", null, true, v -> pickBackupFile()));
        body.addView(g);

        body.addView(new View(this), new LinearLayout.LayoutParams(1, 0, 1));

        TextView wipe = ui.text("抹掉所有内容", 13, Ui.RED, ui.medium);
        wipe.setGravity(Gravity.CENTER);
        wipe.setBackground(ui.round(ui.card(), 26));
        wipe.setElevation(dp(2));
        wipe.setPadding(0, dp(18), 0, dp(18));
        wipe.setOnClickListener(v -> DialogKit.confirm(this, "确定抹掉所有内容？",
                "此操作将删除所有本地数据且无法恢复，请谨慎操作！", "取消", "抹掉", () -> {
                    store.clearAll();
                    showHome();
                    toast("已清空");
                }));
        LinearLayout.LayoutParams wp = new LinearLayout.LayoutParams(-1, -2);
        wp.setMargins(dp(14), 0, dp(14), dp(10));
        body.addView(wipe, wp);

        setScreen(body, false, null);
        screen = "backup";
    }

    private void pickBackupFile() {
        Intent it = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        it.addCategory(Intent.CATEGORY_OPENABLE);
        it.setType("*/*");
        try { startActivityForResult(it, REQ_RESTORE); }
        catch (Exception e) { toast("无法打开文件选择器"); }
    }

    private void createBackupFile() {
        Intent it = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        it.addCategory(Intent.CATEGORY_OPENABLE);
        it.setType("application/octet-stream");
        it.putExtra(Intent.EXTRA_TITLE,
                "小满_Backup_" + new java.text.SimpleDateFormat("yyyyMMdd_HHmm", java.util.Locale.US).format(new java.util.Date()) + ".ias");
        try { startActivityForResult(it, REQ_EXPORT); }
        catch (Exception e) { toast("无法打开文件选择器"); }
    }

    @Override protected void onPause() {
        super.onPause();
        backgrounded = true;
    }

    @Override protected void onResume() {
        super.onResume();
        if (backgrounded && !store.str("privacyPassword", "").isEmpty()) showLock();
        backgrounded = false;
    }

    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (result != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        if (request == REQ_RESTORE) { importBackup(uri); return; }
        if (request == REQ_EXPORT) { exportBackup(uri); return; }
        if (draft == null) return;
        if (request == REQ_COVER) {
            String p = copyImage(uri);
            if (!p.isEmpty()) {
                try { draft.put("coverPath", p); } catch (Exception ignored) {}
                showAdd();
            }
        } else if (request == REQ_NOTE) {
            try {
                JSONArray imgs = draft.optJSONArray("noteImages");
                if (imgs == null) { imgs = new JSONArray(); draft.put("noteImages", imgs); }
                android.content.ClipData clip = data.getClipData();
                if (clip != null) {
                    for (int i = 0; i < clip.getItemCount() && imgs.length() < 9; i++) {
                        String p = copyImage(clip.getItemAt(i).getUri());
                        if (!p.isEmpty()) imgs.put(p);
                    }
                } else {
                    String p = copyImage(uri);
                    if (!p.isEmpty() && imgs.length() < 9) imgs.put(p);
                }
                showAdd();
            } catch (Exception ignored) {}
        }
    }

    private void importBackup(Uri uri) {
        android.app.Dialog progress = DialogKit.progress(this, "备份与恢复", "正在解析备份文件…");
        new Thread(() -> {
            JSONObject root = null;
            String importError = null;
            java.util.Map<String, String> images = new java.util.HashMap<>();
            try (java.io.InputStream in = getContentResolver().openInputStream(uri)) {
                java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
                byte[] buf = new byte[8192];
                int n;
                while (in != null && (n = in.read(buf)) > 0) bos.write(buf, 0, n);
                Backup.Parsed parsed = Backup.readIas(bos.toByteArray());
                root = parsed.root;
                java.io.File dir = new java.io.File(getFilesDir(), "media");
                if (!dir.exists()) dir.mkdirs();
                for (java.util.Map.Entry<String, byte[]> e : parsed.images.entrySet()) {
                    String base = e.getKey().substring("images/".length());
                    java.io.File f = new java.io.File(dir, "imp_" + System.currentTimeMillis() + "_" + base);
                    try (java.io.FileOutputStream out = new java.io.FileOutputStream(f)) { out.write(e.getValue()); }
                    images.put(e.getKey(), f.getAbsolutePath());
                }
            } catch (Exception e) { importError = e.toString(); }
            final JSONObject parsed = root;
            final String errMsg = importError;
            final java.util.Map<String, String> imgMap = images;
            runOnUiThread(() -> {
                progress.dismiss();
                if (parsed == null) { toast("导入失败：" + (errMsg != null ? errMsg : "无法读取文件")); return; }
                pendingImages = imgMap;
                chooseImportMode(parsed);
            });
        }).start();
    }

    private void chooseImportMode(JSONObject root) {
        JSONArray assets = root.optJSONArray("assets");
        int count = assets != null ? assets.length() : 0;
        DialogKit.list(this, "导入设置（" + count + " 条记录）",
                new String[]{"跳过重复（保留本地数据）", "覆盖旧数据（清空后导入）"}, i -> {
                    boolean replace = i == 1;
                    try {
                        int n = Backup.importInto(store, root, replace, pendingImages);
                        categoryFilter = "all";
                        filter = "all";
                        for (JSONObject a : store.objs("assets")) NotificationHelper.schedule(this, a);
                        fillMissingBrands();
                        fillMissingIcons();
                        fillMissingModels();
                        toast("成功导入" + n + "条数据");
                    } catch (Exception e) { toast("导入失败：" + e.getMessage()); }
                    if ("backup".equals(screen)) showBackupPage();
                    else if ("settings".equals(screen)) showSettings(); else showHome();
                }, null, null);
    }

    private void exportBackup(Uri uri) {
        new Thread(() -> {
            String err = null;
            try (java.io.OutputStream out = getContentResolver().openOutputStream(uri)) {
                if (out != null) out.write(Backup.writeIas(store));
            } catch (Exception e) { err = e.getMessage(); }
            final String ferr = err;
            runOnUiThread(() -> {
                toast(ferr == null ? "已导出备份" : "导出失败：" + ferr);
                if ("backup".equals(screen)) showBackupPage();
            });
        }).start();
    }

    private void securityDialog() {
        String pw = store.str("privacyPassword", "");
        if (pw.isEmpty()) {
            DialogKit.input(this, "设置密码", "请输入4位数字密码", "下一步", p1 -> {
                if (!p1.matches("\\d{4}")) { toast("请输入4位数字"); return; }
                DialogKit.input(this, "确认密码", "请再次输入密码", "保存", p2 -> {
                    if (!p1.equals(p2)) { toast("两次输入不一致"); return; }
                    store.setStr("privacyPassword", p1);
                    toast("密码已设置");
                    showSettings();
                });
            });
        } else {
            DialogKit.list(this, "密码与安全", new String[]{"修改密码", "关闭密码"}, idx -> {
                if (idx == 0) {
                    DialogKit.input(this, "原密码", "请输入当前密码", "下一步", old -> {
                        if (!old.equals(pw)) { toast("密码错误"); return; }
                        DialogKit.input(this, "新密码", "请输入4位数字密码", "保存", np -> {
                            if (!np.matches("\\d{4}")) { toast("请输入4位数字"); return; }
                            store.setStr("privacyPassword", np);
                            toast("密码已修改");
                        });
                    });
                } else {
                    DialogKit.input(this, "关闭密码", "请输入当前密码", "确认", old -> {
                        if (!old.equals(pw)) { toast("密码错误"); return; }
                        store.setStr("privacyPassword", "");
                        toast("密码已关闭");
                        showSettings();
                    });
                }
            }, null, null);
        }
    }

    private void languageDialog() {
        String cur = store.str("language", "zh");
        DialogKit.sheetSelect(this, "语言", new String[]{"简体中文", "跟随系统"}, cur.equals("zh") ? 0 : 1, idx -> {
            store.setStr("language", idx == 0 ? "zh" : "system");
            showSettings();
        });
    }

    private void showLock() {
        if (lockView != null) return;
        entered = "";
        FrameLayout lock = new FrameLayout(this);
        lock.setBackgroundColor(ui.bg());
        LinearLayout page = ui.vbox();
        page.setGravity(Gravity.CENTER);
        page.setPadding(dp(30), 0, dp(30), dp(40));
        TextView title = ui.text("请输入密码", 20, ui.ink(), ui.bold);
        title.setGravity(Gravity.CENTER);
        page.addView(title, new LinearLayout.LayoutParams(-1, -2));
        TextView hint = ui.text("输入4位数字密码", 12, ui.gray());
        hint.setGravity(Gravity.CENTER);
        hint.setPadding(0, dp(8), 0, dp(26));
        page.addView(hint, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout dots = ui.hbox();
        dots.setGravity(Gravity.CENTER);
        TextView[] dotViews = new TextView[4];
        for (int i = 0; i < 4; i++) {
            TextView dv = ui.text("○", 22, ui.ink());
            dv.setPadding(dp(10), 0, dp(10), 0);
            dotViews[i] = dv;
            dots.addView(dv);
        }
        page.addView(dots, new LinearLayout.LayoutParams(-1, -2));

        GridLayout pad = new GridLayout(this);
        pad.setColumnCount(3);
        pad.setPadding(0, dp(28), 0, 0);
        LinearLayout.LayoutParams pl = new LinearLayout.LayoutParams(-2, -2);
        pl.gravity = Gravity.CENTER_HORIZONTAL;
        page.addView(pad, pl);
        String[] keys = {"1", "2", "3", "4", "5", "6", "7", "8", "9", "", "0", "⌫"};
        for (String key : keys) {
            TextView btn = ui.text(key, 20, ui.ink(), ui.medium);
            btn.setGravity(Gravity.CENTER);
            btn.setBackground(ui.circle(ui.chipBg()));
            GridLayout.LayoutParams gp = new GridLayout.LayoutParams();
            gp.width = dp(64); gp.height = dp(64);
            gp.setMargins(dp(10), dp(8), dp(10), dp(8));
            if (!key.isEmpty()) {
                btn.setOnClickListener(v -> {
                    if ("⌫".equals(key)) {
                        if (!entered.isEmpty()) entered = entered.substring(0, entered.length() - 1);
                    } else if (entered.length() < 4) {
                        entered += key;
                    }
                    for (int i = 0; i < 4; i++) dotViews[i].setText(i < entered.length() ? "●" : "○");
                    if (entered.length() == 4) {
                        if (entered.equals(store.str("privacyPassword", ""))) {
                            root.removeView(lock);
                            lockView = null;
                            entered = "";
                        } else {
                            toast("密码错误");
                            entered = "";
                            for (TextView dv : dotViews) dv.setText("○");
                        }
                    }
                });
            } else {
                btn.setVisibility(View.INVISIBLE);
            }
            pad.addView(btn, gp);
        }
        lock.addView(page, new FrameLayout.LayoutParams(-1, -1));
        root.addView(lock, new FrameLayout.LayoutParams(-1, -1));
        lockView = lock;
    }

    private void doc(String title, String text) {
        DialogKit.info(this, title, text);
    }

    // ================= 新增 / 编辑 =================
    private void openAdd(String id, String presetKind) {
        returnTab = "wishes".equals(tab) ? "wishes" : "home";
        screen = "add";
        if (id != null) {
            JSONObject a = store.findAsset(id);
            if (a != null) {
                try { draft = new JSONObject(a.toString()); } catch (Exception e) { draft = new JSONObject(); }
                editing = true;
                editOriginal = draft.toString();
                brandAuto = false;
            }
        } else {
            editing = false;
            editOriginal = null;
            brandAuto = false;
            draft = new JSONObject();
            try {
                draft.put("id", "a_" + System.currentTimeMillis());
                draft.put("name", "");
                draft.put("price", 0);
                draft.put("buyDate", Store.today());
                draft.put("kind", presetKind);
                draft.put("status", "serving");
                draft.put("rule", "day");
                draft.put("categoryId", "");
                draft.put("tagIds", new JSONArray());
                draft.put("icon", "emoji/icon_default_object.png");
            } catch (Exception ignored) {}
        }
        showAdd();
    }

    private void showAdd() {
        LinearLayout body = pageBody();
        body.setPadding(0, 0, 0, dp(30));

        LinearLayout top = ui.hbox();
        top.setPadding(dp(18), dp(12), dp(18), dp(8));
        FrameLayout back = circleIconButton("ic_close", this::restoreTab);
        top.addView(back, new LinearLayout.LayoutParams(dp(36), dp(36)));
        TextView title = ui.text(editing ? "编辑资产" : "新增资产", 19, ui.ink(), ui.medium);
        title.setGravity(Gravity.CENTER);
        top.addView(title, lpw(1, -2));
        FrameLayout save = new FrameLayout(this);
        saveButtonRef = save;
        ImageView check = ui.icon("ic_confirm", 16);
        saveCheckRef = check;
        save.addView(check, new FrameLayout.LayoutParams(dp(16), dp(16), Gravity.CENTER));
        refreshSaveButton(save);
        save.setOnClickListener(v -> saveDraft());
        top.addView(save, new LinearLayout.LayoutParams(dp(36), dp(36)));
        body.addView(top);

        LinearLayout iconPick = ui.vbox();
        iconPick.setGravity(Gravity.CENTER);
        iconPick.setPadding(0, dp(16), 0, dp(22));
        iconPreview = ui.image(draft.optString("icon", "emoji/icon_default_object.png"), 84);
        iconPick.addView(iconPreview);
        TextView change = ui.text("更换图标", 10, ui.gray());
        change.setGravity(Gravity.CENTER);
        change.setPadding(0, dp(8), 0, 0);
        iconPick.addView(change);
        iconPick.setOnClickListener(v -> pickIcon());
        body.addView(iconPick, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout g1 = formCard();
        g1.addView(fieldLabel("ic_name", "名称", editText("请输入昵称", false, draft.optString("name"), v -> {
            try { draft.put("name", v); } catch (Exception ignored) {}
            String t = draft.optString("model").isEmpty() ? v : draft.optString("model");
            autoDetectBrand(t);
            autoDetectIcon(t);
            updateBrandRow();
            updateIconPreview();
            refreshSaveButton(save);
        })));
        LinearLayout modelRow = fieldLabel("ic_edit_type", "型号", null);
        modelInput = editText("请输入具体型号", false, draft.optString("model"), v -> {
            try { draft.put("model", v); } catch (Exception ignored) {}
            autoDetectBrand(v);
            autoDetectIcon(v);
            updateBrandRow();
            updateIconPreview();
            refreshSaveButton(save);
        });
        modelRow.addView(modelInput, new LinearLayout.LayoutParams(0, -2, 1.2f));
        ImageView modelChev = themedIcon("ic_chevron", 10);
        modelChev.setAlpha(dark ? 0.75f : 0.35f);
        LinearLayout.LayoutParams mcp = new LinearLayout.LayoutParams(dp(6), dp(10));
        mcp.leftMargin = dp(8);
        modelChev.setOnClickListener(v -> openModelPicker());
        modelRow.addView(modelChev, mcp);
        g1.addView(modelRow);
        g1.addView(fieldLabel("ic_price", "价格", editText("请输入物品价格", true, draft.optDouble("price", 0) == 0 ? "" : String.valueOf(draft.optDouble("price")), v -> {
            try { draft.put("price", parse(v)); } catch (Exception ignored) {}
            refreshSaveButton(save);
        })));
        g1.addView(fieldLabel("ic_earnings", "二手估值", editText("可选，用于计算实际持有成本", true,
                draft.optDouble("secondhandValue", 0) == 0 ? "" : String.valueOf(draft.optDouble("secondhandValue")), v -> {
                    try { draft.put("secondhandValue", parse(v)); } catch (Exception ignored) {}
                })));
        LinearLayout deductRow = fieldLabel("ic_switch", "扣减二手残值", null);
        Switch deduct = new Switch(this);
        deduct.setChecked(draft.optBoolean("secondhandInCost", true));
        deduct.setOnCheckedChangeListener((vv, c) -> {
            try { draft.put("secondhandInCost", c); } catch (Exception ignored) {}
        });
        deductRow.addView(deduct);
        g1.addView(deductRow);
        LinearLayout typeRow = fieldLabel("ic_type", "类型", null);
        LinearLayout typeSeg = segment(new String[]{"资产", "心愿"}, Store.isWish(draft) ? 1 : 0, 11, i -> {
            try { draft.put("kind", i == 0 ? "asset" : "wish"); } catch (Exception ignored) {}
            showAdd();
        });
        typeRow.addView(typeSeg);
        g1.addView(typeRow);
        LinearLayout brandRow = fieldLabel("ic_view", "品牌", null);
        LinearLayout brandVal = ui.hbox();
        brandRowLogo = new ImageView(this);
        brandRowLogo.setLayoutParams(new android.widget.FrameLayout.LayoutParams(dp(16), dp(16), Gravity.CENTER));
        if (dark) {
            FrameLayout plate = new FrameLayout(this);
            plate.setBackground(ui.round(0xFFFFFFFF, 11));
            plate.setPadding(dp(3), dp(3), dp(3), dp(3));
            plate.addView(brandRowLogo);
            brandVal.addView(plate, new LinearLayout.LayoutParams(dp(22), dp(22)));
        } else {
            brandVal.addView(brandRowLogo, new LinearLayout.LayoutParams(dp(18), dp(18)));
        }
        brandRowText = ui.text("未设置", 13, ui.gray());
        brandRowText.setPadding(dp(6), 0, 0, 0);
        brandVal.addView(brandRowText);
        ImageView bchev = themedIcon("ic_chevron", 10);
        bchev.setAlpha(dark ? 0.75f : 0.35f);
        LinearLayout.LayoutParams bcp = new LinearLayout.LayoutParams(dp(6), dp(10));
        bcp.leftMargin = dp(6);
        brandVal.addView(bchev, bcp);
        brandRow.addView(brandVal);
        updateBrandRow();
        brandRow.setOnClickListener(v -> DialogKit.brandPicker(this, draft.optString("brandId"), brandData, (name, id) -> {
            brandAuto = false;
            try { draft.put("brandId", id); draft.put("brandName", name); draft.put("brandAuto", false); } catch (Exception ignored) {}
            updateBrandRow();
            refreshSaveButton(save);
        }));
        g1.addView(brandRow);
        body.addView(g1);

        LinearLayout g2 = formCard();
        LinearLayout dateRow = fieldLabel("ic_calendar", "购买日期", null);
        TextView date = ui.text(draft.optString("buyDate", Store.today()), 13, ui.gray());
        dateRow.addView(date);
        LinearLayout dateWrap = ui.hbox();
        dateWrap.addView(themedIcon("ic_chevron", 10));
        dateRow.setOnClickListener(v -> pickDate(date));
        g2.addView(dateRow);

        LinearLayout catRow = fieldLabel("ic_category2", "分类", null);
        TextView catName = ui.text(categoryName(draft.optString("categoryId")), 13, ui.gray());
        catRow.addView(catName);
        LinearLayout catWrap = ui.hbox();
        ImageView cc = themedIcon("ic_chevron", 10);
        cc.setAlpha(dark ? 0.75f : 0.35f);
        catWrap.addView(cc);
        catRow.addView(catWrap);
        catRow.setOnClickListener(v -> pickCategory(catRow));
        g2.addView(catRow);

        LinearLayout tagRow = fieldLabel("ic_tag2", "标签", null);
        TextView tagNames = ui.text(tagNames(), 13, ui.gray());
        tagRow.addView(tagNames);
        LinearLayout tagWrap = ui.hbox();
        ImageView tc = themedIcon("ic_chevron", 10);
        tc.setAlpha(dark ? 0.75f : 0.35f);
        tagWrap.addView(tc);
        tagRow.addView(tagWrap);
        tagRow.setOnClickListener(v -> pickTags());
        g2.addView(tagRow);

        LinearLayout noteRow = fieldLabel("ic_note", "备注", null);
        TextView noteVal = ui.text(draft.optString("note").isEmpty() ? "添加备注" : draft.optString("note"), 13, ui.gray());
        noteVal.setSingleLine(true);
        noteRow.addView(noteVal);
        ImageView ncv = themedIcon("ic_chevron", 10);
        ncv.setAlpha(dark ? 0.75f : 0.35f);
        noteRow.addView(ncv, new LinearLayout.LayoutParams(dp(6), dp(10)));
        noteRow.setOnClickListener(v -> DialogKit.input(this, "备注", "输入备注，最多200字", "确定", val -> {
            String n = val.length() > 200 ? val.substring(0, 200) : val;
            try { draft.put("note", n); } catch (Exception ignored) {}
            showAdd();
        }));
        g2.addView(noteRow);

        LinearLayout coverRow = fieldLabel("ic_album", "主图", null);
        String coverPath = draft.optString("coverPath");
        if (!coverPath.isEmpty() && new java.io.File(coverPath).exists())
            coverRow.addView(ui.fileImage(coverPath, 40), new LinearLayout.LayoutParams(dp(40), dp(40)));
        TextView coverVal = ui.text(coverPath.isEmpty() ? "未设置" : "更换", 13, ui.gray());
        coverRow.addView(coverVal);
        ImageView ccv = themedIcon("ic_chevron", 10);
        ccv.setAlpha(dark ? 0.75f : 0.35f);
        coverRow.addView(ccv, new LinearLayout.LayoutParams(dp(6), dp(10)));
        coverRow.setOnClickListener(v -> pickCover());
        g2.addView(coverRow);

        LinearLayout noteImgRow = fieldLabel("ic_photo_add", "备注图片", null);
        JSONArray noteImgs = draft.optJSONArray("noteImages");
        int ni = noteImgs != null ? noteImgs.length() : 0;
        TextView niVal = ui.text(ni == 0 ? "添加" : ni + " 张", 13, ui.gray());
        noteImgRow.addView(niVal);
        ImageView ncv2 = themedIcon("ic_chevron", 10);
        ncv2.setAlpha(dark ? 0.75f : 0.35f);
        noteImgRow.addView(ncv2, new LinearLayout.LayoutParams(dp(6), dp(10)));
        noteImgRow.setOnClickListener(v -> pickNoteImages());
        g2.addView(noteImgRow);

        LinearLayout expireRow = fieldLabel("ic_calendar", "到期提醒", null);
        String ex = draft.optString("expireAt");
        String unitLabel = "week".equals(draft.optString("remindUnit", "day")) ? "周" : "month".equals(draft.optString("remindUnit", "day")) ? "月" : "天";
        String timeLabel = String.format(java.util.Locale.US, "%02d:%02d", draft.optInt("remindHour", 9), draft.optInt("remindMinute", 0));
        TextView exVal = ui.text(ex.isEmpty() ? "未设置" : ex + (draft.optInt("remindDays", 0) > 0 ? " · 提前" + draft.optInt("remindDays") + unitLabel + " " + timeLabel : ""), 13, ui.gray());
        expireRow.addView(exVal);
        ImageView ecv = themedIcon("ic_chevron", 10);
        ecv.setAlpha(dark ? 0.75f : 0.35f);
        expireRow.addView(ecv, new LinearLayout.LayoutParams(dp(6), dp(10)));
        expireRow.setOnClickListener(v -> expireDialog(exVal));
        g2.addView(expireRow);

        body.addView(g2);

        if (editing) {
            LinearLayout gs = formCard();
            LinearLayout statusRow = fieldLabel("ic_switch", "状态", null);
            String st = draft.optString("status", "serving");
            int sel = "retired".equals(st) ? 1 : "sold".equals(st) ? 2 : 0;
            statusRow.addView(segment(new String[]{"服役中", "已退役", "已卖出"}, sel, 11, i -> {
                try { draft.put("status", new String[]{"serving", "retired", "sold"}[i]); } catch (Exception ignored) {}
                showAdd();
            }));
            gs.addView(statusRow);
            String st2 = draft.optString("status", "serving");
            if ("retired".equals(st2)) {
                addDateRow(gs, "ic_calendar", "退役日期", "retireDate");
            } else if ("sold".equals(st2)) {
                gs.addView(fieldLabel("ic_price", "卖出价格", editText("请输入卖出价格", true,
                        draft.optDouble("soldPrice", 0) == 0 ? "" : String.valueOf(draft.optDouble("soldPrice")), v -> {
                            try { draft.put("soldPrice", parse(v)); } catch (Exception ignored) {}
                        })));
                addDateRow(gs, "ic_calendar", "卖出日期", "soldDate");
            }
            LinearLayout excludeRow = fieldLabel("ic_switch", "不计入总资产", null);
            Switch exSwitch = new Switch(this);
            exSwitch.setChecked(draft.optBoolean("excludeTotal"));
            exSwitch.setOnCheckedChangeListener((vv, c) -> {
                try { draft.put("excludeTotal", c); } catch (Exception ignored) {}
            });
            excludeRow.addView(exSwitch);
            gs.addView(excludeRow);
            body.addView(gs);
        }

        LinearLayout g3 = formCard();
        g3.setPadding(dp(16), dp(2), dp(16), dp(12));
        LinearLayout ruleHead = ui.hbox();
        ruleHead.setPadding(0, dp(14), 0, dp(10));
        ruleHead.addView(themedIcon("ic_rule", 16));
        TextView rl = ui.text("成本计算规则", 14, ui.ink());
        rl.setPadding(dp(10), 0, 0, 0);
        ruleHead.addView(rl);
        g3.addView(ruleHead);
        String rule = draft.optString("rule", "day");
        int rsel = "count".equals(rule) ? 1 : "custom".equals(rule) ? 2 : 0;
        LinearLayout ruleSeg = ui.hbox();
        ruleSeg.setBackground(ui.round(dark ? 0xFF2A2A2A : 0xFFF4F4F4, 10));
        ruleSeg.setPadding(dp(4), dp(4), dp(4), dp(4));
        String[] rules = {"按天", "按次", "自定义"};
        for (int i = 0; i < rules.length; i++) {
            boolean on = i == rsel;
            TextView t = ui.text(rules[i], 12, on ? 0xFF1A1A1A : ui.gray(), on ? ui.medium : ui.regular);
            t.setGravity(Gravity.CENTER);
            t.setPadding(0, dp(10), 0, dp(10));
            if (on) t.setBackground(ui.round(Ui.LIME, 9));
            final int idx = i;
            t.setOnClickListener(v -> {
                try { draft.put("rule", new String[]{"day", "count", "custom"}[idx]); } catch (Exception ignored) {}
                showAdd();
            });
            ruleSeg.addView(t, lpw(1, -2));
        }
        g3.addView(ruleSeg);
        body.addView(g3);

        if (Store.isWish(draft)) {
            LinearLayout gw = formCard();
            gw.addView(fieldLabel("ic_target", "目标价格", editText("请输入目标价格", true,
                    draft.optDouble("targetCost", 0) == 0 ? "" : String.valueOf(draft.optDouble("targetCost")), v -> {
                        try { draft.put("targetCost", parse(v)); } catch (Exception ignored) {}
                    })));
            addDateRow(gw, "ic_calendar", "目标日期", "targetDate");
            body.addView(gw);
        }

        if ("count".equals(rule) || "custom".equals(rule)) {
            LinearLayout g4 = formCard();
            String key = "count".equals(rule) ? "uses" : "customDays";
            String label = "count".equals(rule) ? "使用次数" : "计费天数";
            String icon = "count".equals(rule) ? "ic_earnings" : "ic_target";
            g4.addView(fieldLabel(icon, label, editText("请输入" + label, true,
                    draft.optDouble(key, 0) == 0 ? "" : String.valueOf(draft.optDouble(key)), v -> {
                        try { draft.put(key, parse(v)); } catch (Exception ignored) {}
                    })));
            body.addView(g4);
        }

        TextView hint = ui.text("按天：价格 ÷ 已使用天数；按次：价格 ÷ 使用次数；自定义：价格 ÷ 自定义天数。", 11, ui.gray());
        hint.setPadding(dp(20), dp(10), dp(20), 0);
        body.addView(hint);

        setScreen(scrollPage(body), false, null);
        screen = "add";
    }

    private void expireDialog(TextView label) {
        DialogKit.list(this, "到期提醒", new String[]{"设置/修改到期时间", "设置提前提醒", "设置提醒时间", "清除到期提醒"}, idx -> {
            if (idx == 0) {
                Calendar cal = Calendar.getInstance();
                try {
                    String[] p = draft.optString("expireAt", Store.today()).split("-");
                    cal.set(Integer.parseInt(p[0]), Integer.parseInt(p[1]) - 1, Integer.parseInt(p[2]));
                } catch (Exception ignored) {}
                new DatePickerDialog(this, (v, y, m, d) -> {
                    try { draft.put("expireAt", String.format(java.util.Locale.US, "%04d-%02d-%02d", y, m + 1, d)); } catch (Exception ignored) {}
                    showAdd();
                }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show();
            } else if (idx == 1) {
                DialogKit.input(this, "提前提醒", "提前多少（数字）", "下一步", amount -> {
                    int n = (int) parse(amount);
                    DialogKit.list(this, "提醒单位", new String[]{"天", "周", "月"}, u -> {
                        try {
                            draft.put("remindDays", n);
                            draft.put("remindUnit", new String[]{"day", "week", "month"}[u]);
                        } catch (Exception ignored) {}
                        showAdd();
                    }, null, null);
                });
            } else if (idx == 2) {
                DialogKit.input(this, "提醒时间", "HH:mm，例如 09:00", "保存", time -> {
                    try {
                        String[] p = time.split(":");
                        draft.put("remindHour", Integer.parseInt(p[0].trim()));
                        draft.put("remindMinute", Integer.parseInt(p[1].trim()));
                    } catch (Exception ignored) {}
                    showAdd();
                });
            } else {
                try {
                    draft.put("expireAt", "");
                    draft.put("remindDays", 0);
                    draft.put("remindUnit", "day");
                } catch (Exception ignored) {}
                showAdd();
            }
        }, null, null);
    }

    private void addDateRow(LinearLayout parent, String icon, String label, String key) {
        LinearLayout row = fieldLabel(icon, label, null);
        String def = Store.today();
        TextView val = ui.text(draft.optString(key, def), 13, ui.gray());
        row.addView(val);
        ImageView c = themedIcon("ic_chevron", 10);
        c.setAlpha(dark ? 0.75f : 0.35f);
        row.addView(c, new LinearLayout.LayoutParams(dp(6), dp(10)));
        row.setOnClickListener(v -> {
            Calendar cal = Calendar.getInstance();
            try {
                String[] p = draft.optString(key, def).split("-");
                cal.set(Integer.parseInt(p[0]), Integer.parseInt(p[1]) - 1, Integer.parseInt(p[2]));
            } catch (Exception ignored) {}
            new DatePickerDialog(this, (vv, y, m, d) -> {
                String date = String.format(java.util.Locale.US, "%04d-%02d-%02d", y, m + 1, d);
                try { draft.put(key, date); } catch (Exception ignored) {}
                val.setText(date);
            }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show();
        });
        parent.addView(row);
    }

    private String displayName(JSONObject a) {
        String n = a.optString("name");
        return n.isEmpty() ? a.optString("model") : n;
    }

    private String matchText(JSONObject a) {
        String m = a.optString("model");
        return m.isEmpty() ? a.optString("name") : m;
    }

    private String[] matchModelIcon(String name) {
        if (name == null || name.trim().isEmpty()) return null;
        String lower = name.toLowerCase(java.util.Locale.US);
        java.util.List<String[]> pairs = new java.util.ArrayList<>();
        for (java.util.Map.Entry<String, String[]> e : MODEL_ICONS.entrySet())
            for (String k : e.getValue()) pairs.add(new String[]{k.toLowerCase(java.util.Locale.US), e.getKey()});
        pairs.sort((x, y) -> Integer.compare(y[0].length(), x[0].length()));
        for (String[] p : pairs) {
            String k = p[0];
            boolean latin = k.matches(".*[a-z].*");
            boolean hit = latin
                    ? java.util.regex.Pattern.compile("(?<![a-z0-9])" + java.util.regex.Pattern.quote(k) + "(?![a-z0-9])").matcher(lower).find()
                    : k.length() >= 2 && lower.contains(k);
            if (hit) return new String[]{p[1]};
        }
        return null;
    }

    private void autoDetectIcon(String name) {
        boolean manual = !draft.optBoolean("iconAuto", true) && !"emoji/icon_default_object.png".equals(draft.optString("icon"));
        if (manual) return;
        String[] m = matchModelIcon(name);
        try {
            if (m != null) { draft.put("icon", m[0]); draft.put("iconAuto", true); }
            else { draft.put("icon", "emoji/icon_default_object.png"); draft.put("iconAuto", false); }
        } catch (Exception ignored) {}
    }

    private void updateIconPreview() {
        if (iconPreview == null) return;
        android.graphics.Bitmap bm = ui.bitmap(draft.optString("icon", "emoji/icon_default_object.png"));
        if (bm != null) iconPreview.setImageBitmap(bm);
    }

    private int fillMissingIcons() {
        int changed = 0;
        for (JSONObject a : store.objs("assets")) {
            boolean auto = a.optBoolean("iconAuto", false);
            String icon = a.optString("icon");
            if (!auto && !icon.isEmpty() && !"emoji/icon_default_object.png".equals(icon)) continue;
            String[] m = matchModelIcon(matchText(a));
            try {
                if (m != null) {
                    if (!m[0].equals(icon)) { a.put("icon", m[0]); a.put("iconAuto", true); changed++; }
                }
            } catch (Exception ignored) {}
        }
        if (changed > 0) store.save();
        return changed;
    }

    private String[] matchBrand(String name) {
        if (name == null || name.trim().isEmpty()) return null;
        String lower = name.toLowerCase(java.util.Locale.US);
        for (String[] a : brandAliases) {
            String alias = a[0];
            boolean latin = "1".equals(a[3]);
            boolean hit;
            if (latin) hit = java.util.regex.Pattern.compile("(?<![a-z0-9])" + java.util.regex.Pattern.quote(alias) + "(?![a-z0-9])").matcher(lower).find();
            else hit = alias.length() >= 2 && lower.contains(alias);
            if (hit) return new String[]{a[1], a[2]};
        }
        return null;
    }

    private int fillMissingModels() {
        int changed = 0;
        for (JSONObject a : store.objs("assets")) {
            if (a.optString("model").isEmpty() && !a.optString("name").isEmpty()) {
                try { a.put("model", a.optString("name")); changed++; } catch (Exception ignored) {}
            }
        }
        if (changed > 0) store.save();
        return changed;
    }

    private int fillMissingBrands() {
        int changed = 0;
        for (JSONObject a : store.objs("assets")) {
            boolean auto = a.optBoolean("brandAuto", false);
            if (!a.optString("brandId").isEmpty() && !auto) continue;
            String[] m = matchBrand(matchText(a));
            try {
                if (m != null) {
                    if (!m[1].equals(a.optString("brandId"))) {
                        a.put("brandId", m[1]); a.put("brandName", m[0]); a.put("brandAuto", true); changed++;
                    }
                } else if (auto) {
                    a.put("brandId", ""); a.put("brandName", ""); a.put("brandAuto", false); changed++;
                }
            } catch (Exception ignored) {}
        }
        if (changed > 0) store.save();
        return changed;
    }

    private void openModelPicker() {
        String brandId = draft.optString("brandId");
        String current = draft.optString("model");
        if (brandId.isEmpty()) {
            DialogKit.input(this, "型号", "请输入具体型号", current, "保存", m -> {
                try { draft.put("model", m); } catch (Exception ignored) {}
                autoDetectBrand(m);
                autoDetectIcon(m);
                if (modelInput != null) modelInput.setText(m);
                updateBrandRow();
                updateIconPreview();
                refreshSaveButton(saveButtonRef);
            });
            return;
        }
        String brandName = draft.optString("brandName");
        JSONArray groups = brandModels.get(brandId);
        if (groups == null) groups = new JSONArray();
        DialogKit.modelPicker(this, brandName, current, groups, m -> {
            try { draft.put("model", m); } catch (Exception ignored) {}
            autoDetectBrand(m);
            autoDetectIcon(m);
            if (modelInput != null) modelInput.setText(m);
            updateBrandRow();
            updateIconPreview();
            refreshSaveButton(saveButtonRef);
        });
    }

    private void autoDetectBrand(String name) {
        if (name == null || name.trim().isEmpty()) {
            if (brandAuto) {
                try { draft.put("brandId", ""); draft.put("brandName", ""); } catch (Exception ignored) {}
                brandAuto = false;
            }
            return;
        }
        if (!brandAuto && !draft.optString("brandId").isEmpty()) return;
        String[] m = matchBrand(name);
        if (m != null) {
            try { draft.put("brandId", m[1]); draft.put("brandName", m[0]); draft.put("brandAuto", true); } catch (Exception ignored) {}
            brandAuto = true;
        } else if (brandAuto) {
            try { draft.put("brandId", ""); draft.put("brandName", ""); draft.put("brandAuto", false); } catch (Exception ignored) {}
            brandAuto = false;
        }
    }

    private void updateBrandRow() {
        if (brandRowLogo == null || brandRowText == null) return;
        String id = draft.optString("brandId");
        if (id.isEmpty()) {
            brandRowLogo.setVisibility(View.GONE);
            brandRowText.setText("未设置");
        } else {
            android.graphics.Bitmap bm = ui.bitmap("brands/" + id + ".png");
            if (bm != null) { brandRowLogo.setImageBitmap(bm); brandRowLogo.setVisibility(View.VISIBLE); }
            else brandRowLogo.setVisibility(View.GONE);
            brandRowText.setText(draft.optString("brandName"));
        }
    }

    private LinearLayout formCard() {
        LinearLayout g = ui.vbox();
        g.setBackground(ui.round(ui.card(), 16));
        g.setElevation(dp(2));
        g.setPadding(dp(24), dp(2), dp(24), dp(2));
        LinearLayout.LayoutParams gp = new LinearLayout.LayoutParams(-1, -2);
        gp.setMargins(dp(28), 0, dp(28), dp(12));
        g.setLayoutParams(gp);
        return g;
    }

    private LinearLayout fieldLabel(String icon, String label, View right) {
        LinearLayout r = ui.hbox();
        r.setMinimumHeight(dp(52));
        r.addView(themedIcon(icon, 16));
        TextView t = ui.text(label, 13.5f, ui.ink());
        t.setPadding(dp(10), 0, 0, 0);
        r.addView(t, lpw(1, -2));
        if (right != null) {
            LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(0, -2, 1.2f);
            right.setLayoutParams(rp);
            r.addView(right);
        }
        return r;
    }

    private EditText editText(String hint, boolean number, String value, TextClick onChange) {
        EditText et = new EditText(this);
        et.setHint(hint);
        et.setText(value);
        et.setTextSize(14);
        et.setTextColor(ui.ink());
        et.setHintTextColor(ui.gray());
        et.setTypeface(ui.regular);
        et.setGravity(Gravity.END);
        et.setBackground(null);
        et.setSingleLine(true);
        et.setPadding(0, 0, 0, 0);
        if (number) et.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        et.addTextChangedListener(new Watcher(onChange));
        return et;
    }

    private static class Watcher implements TextWatcher {
        private final TextClick cb;
        Watcher(TextClick cb) { this.cb = cb; }
        public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
        public void onTextChanged(CharSequence s, int a, int b, int c) {}
        public void afterTextChanged(Editable s) { cb.on(s.toString()); }
    }

    private boolean hasNamePrice() {
        boolean named = !draft.optString("name").trim().isEmpty() || !draft.optString("model").trim().isEmpty();
        return named && draft.optDouble("price", 0) > 0;
    }

    private void refreshSaveButton(View save) {
        if (save == null) return;
        boolean changed = !editing || editOriginal == null || !draft.toString().equals(editOriginal);
        boolean ok = hasNamePrice() && changed;
        int bg, fg;
        if (dark) {
            bg = ok ? 0xFFF2F2F2 : 0xFF333333;
            fg = ok ? 0xFF111111 : 0xFF9A9A9A;
        } else {
            bg = ok ? 0xFF111111 : 0xFFBBBBBB;
            fg = 0xFFFFFFFF;
        }
        save.setBackground(ui.circle(bg));
        save.setAlpha(ok ? 1f : 0.9f);
        if (saveCheckRef != null && save == saveButtonRef) saveCheckRef.setColorFilter(fg);
    }

    private double parse(String s) {
        try { return Double.parseDouble(s.trim()); } catch (Exception e) { return 0; }
    }

    private void pickCover() {
        Intent it = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        it.addCategory(Intent.CATEGORY_OPENABLE);
        it.setType("image/*");
        try { startActivityForResult(it, REQ_COVER); } catch (Exception e) { toast("无法打开图片选择器"); }
    }

    private void pickNoteImages() {
        Intent it = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        it.addCategory(Intent.CATEGORY_OPENABLE);
        it.setType("image/*");
        it.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        try { startActivityForResult(it, REQ_NOTE); } catch (Exception e) { toast("无法打开图片选择器"); }
    }

    private String copyImage(Uri uri) {
        try (java.io.InputStream in = getContentResolver().openInputStream(uri)) {
            java.io.File dir = new java.io.File(getFilesDir(), "media");
            if (!dir.exists()) dir.mkdirs();
            java.io.File f = new java.io.File(dir, "img_" + System.currentTimeMillis() + "_" + (int) (Math.random() * 1000) + ".jpg");
            try (java.io.OutputStream out = new java.io.FileOutputStream(f)) {
                byte[] buf = new byte[8192];
                int n;
                while (in != null && (n = in.read(buf)) > 0) out.write(buf, 0, n);
            }
            return f.getAbsolutePath();
        } catch (Exception e) { return ""; }
    }

    private void pickDate(TextView label) {
        Calendar c = Calendar.getInstance();
        try {
            String[] p = draft.optString("buyDate", Store.today()).split("-");
            c.set(Integer.parseInt(p[0]), Integer.parseInt(p[1]) - 1, Integer.parseInt(p[2]));
        } catch (Exception ignored) {}
        new DatePickerDialog(this, (v, y, m, d) -> {
            String date = String.format("%04d-%02d-%02d", y, m + 1, d);
            try { draft.put("buyDate", date); } catch (Exception ignored) {}
            label.setText(date);
        }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show();
    }

    private String categoryName(String id) {
        for (JSONObject c : store.objs("categories")) if (c.optString("id").equals(id)) return c.optString("name");
        return "未分类";
    }

    private String tagNames() {
        JSONArray ids = draft.optJSONArray("tagIds");
        List<String> names = new ArrayList<>();
        if (ids != null) {
            for (int i = 0; i < ids.length(); i++) {
                for (JSONObject t : store.objs("tags"))
                    if (t.optString("id").equals(ids.optString(i))) names.add(t.optString("name"));
            }
        }
        return names.isEmpty() ? "选择标签" : String.join("、", names);
    }

    private void pickCategory(LinearLayout row) {
        List<JSONObject> cats = store.objs("categories");
        String[] names = new String[cats.size() + 1];
        names[0] = "未分类";
        for (int i = 0; i < cats.size(); i++) names[i + 1] = cats.get(i).optString("name");
        DialogKit.sheetList(this, "选择分类", "点击添加分类", names, i -> {
            try { draft.put("categoryId", i == 0 ? "" : cats.get(i - 1).optString("id")); } catch (Exception ignored) {}
            showAdd();
        }, () -> inputDialog("新建分类", "请输入分类名称", name -> {
            try {
                JSONObject c = new JSONObject().put("id", "cat_" + System.currentTimeMillis()).put("name", name);
                store.arr("categories").put(c);
                store.save();
                draft.put("categoryId", c.optString("id"));
            } catch (Exception ignored) {}
            showAdd();
        }));
    }

    private void pickTags() {
        List<JSONObject> tags = store.objs("tags");
        String[] names = new String[tags.size()];
        boolean[] checked = new boolean[tags.size()];
        JSONArray ids = draft.optJSONArray("tagIds");
        for (int i = 0; i < tags.size(); i++) {
            names[i] = tags.get(i).optString("name");
            if (ids != null) for (int k = 0; k < ids.length(); k++)
                if (tags.get(i).optString("id").equals(ids.optString(k))) checked[i] = true;
        }
        DialogKit.multi(this, "选择标签", names, checked, sel -> {
            JSONArray arr = new JSONArray();
            for (int i = 0; i < tags.size(); i++) if (sel[i]) arr.put(tags.get(i).optString("id"));
            try { draft.put("tagIds", arr); } catch (Exception ignored) {}
            showAdd();
        }, "新建", () -> inputDialog("新建标签", "请输入标签名称", name -> {
            try {
                JSONObject t = new JSONObject().put("id", "tag_" + System.currentTimeMillis()).put("name", name);
                store.arr("tags").put(t);
                store.save();
            } catch (Exception ignored) {}
            pickTags();
        }));
    }

    private void pickIcon() {
        String[] labels = {"默认", "数码", "包包", "服饰", "美妆", "电器", "家具", "房屋", "乐器", "珠宝首饰", "宠物", "体育运动", "玩具", "交通工具"};
        String[] folders = {"", "apple/digital", "apple/bag", "apple/clothing", "apple/beauty", "apple/appliance", "apple/furniture", "apple/house", "apple/instrument", "apple/jewelry", "apple/pet", "apple/sport", "apple/toy", "apple/vehicle"};
        DialogKit.iconPicker(this, draft.optString("icon"), labels, folders, path -> {
            try { draft.put("icon", path); draft.put("iconAuto", false); } catch (Exception ignored) {}
            showAdd();
        });
    }

    private String returnTab = "home";

    private void restoreTab() {
        if ("wishes".equals(returnTab)) showWishes(); else showHome();
        screen = returnTab;
    }

    private void saveDraft() {
        if (!hasNamePrice()) { toast("请填写名称与价格"); return; }
        if (editing && editOriginal != null && draft.toString().equals(editOriginal)) { restoreTab(); return; }
        try {
            if (draft.optString("kind").isEmpty()) draft.put("kind", "asset");
            if (draft.optString("status").isEmpty()) draft.put("status", "serving");
            if (draft.optString("buyDate").isEmpty()) draft.put("buyDate", Store.today());
            if (draft.optString("icon").isEmpty()) draft.put("icon", "emoji/icon_default_object.png");
        } catch (Exception ignored) {}
        store.upsertAsset(draft);
        NotificationHelper.schedule(this, draft);
        boolean first = !editing && !store.bool("ratingShown", false);
        String kind = draft.optString("kind", "asset");
        draft = null;
        editing = false;
        if (first) {
            store.setBool("ratingShown", true);
            DialogKit.confirm(this, "喜欢这个应用吗？", "请给我们评分支持！", "不喜欢", "喜欢",
                    () -> { toast("感谢你的支持 ♥"); if ("wish".equals(kind)) showWishes(); else showHome(); },
                    () -> { if ("wish".equals(kind)) showWishes(); else showHome(); });
        } else {
            if ("wish".equals(kind)) showWishes(); else showHome();
        }
    }

    // ================= 详情 =================
    private void showDetail(String id) {
        JSONObject a = store.findAsset(id);
        if (a == null) { showHome(); return; }
        detailId = id;
        returnTab = "wishes".equals(tab) ? "wishes" : "home";
        screen = "detail";
        LinearLayout body = ui.vbox();
        body.setPadding(0, 0, 0, dp(40));

        LinearLayout top = ui.hbox();
        top.setPadding(dp(18), dp(12), dp(18), dp(8));
        FrameLayout back = circleIconButton("ic_close", this::restoreTab);
        top.addView(back, new LinearLayout.LayoutParams(dp(36), dp(36)));
        top.addView(new View(this), lpw(1, 1));
        FrameLayout edit = circleIconButton("ic_edit", () -> openAdd(a.optString("id"), "asset"));
        top.addView(edit, new LinearLayout.LayoutParams(dp(36), dp(36)));
        body.addView(top, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout center = ui.vbox();
        center.setGravity(Gravity.CENTER);
        center.setPadding(0, dp(8), 0, 0);
        String dcv = a.optString("coverPath");
        if (!dcv.isEmpty() && new java.io.File(dcv).exists()) center.addView(ui.fileImage(dcv, 96));
        else center.addView(ui.image(a.optString("icon", "emoji/icon_default_object.png"), 88));
        TextView name = ui.text(displayName(a), 20, ui.ink(), ui.bold);
        name.setGravity(Gravity.CENTER);
        name.setPadding(0, dp(14), 0, 0);
        center.addView(name);
        center.addView(moneyLineSuffix(store.dailyCost(a), 22, "/天"));
        int days = store.ageDays(a);
        TextView meta = ui.text("总价：" + store.money(a.optDouble("price")) + "  |  已服役 " + days + " 天", 11, ui.gray());
        meta.setGravity(Gravity.CENTER);
        meta.setPadding(0, dp(10), 0, 0);
        center.addView(meta);
        body.addView(center, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout chartCard = ui.vbox();
        chartCard.setBackground(ui.round(ui.card(), 16));
        chartCard.setElevation(0);
        chartCard.setClipToOutline(true);
        chartCard.setPadding(dp(16), dp(16), dp(16), dp(10));
        LinearLayout.LayoutParams ccp = new LinearLayout.LayoutParams(-1, -2);
        ccp.setMargins(dp(14), dp(18), dp(14), dp(12));
        chartCard.setLayoutParams(ccp);
        chartCard.addView(ui.text("日均成本", 13, ui.ink(), ui.medium));
        double[] series = detailSeries(a, days);
        ChartView chart = new ChartView(this);
        String buyDate = a.optString("buyDate");
        String endDate = "sold".equals(a.optString("status")) && !a.optString("soldDate").isEmpty()
                ? a.optString("soldDate") : Store.today();
        boolean crossYear = buyDate.length() >= 4 && !buyDate.substring(0, 4).equals(endDate.substring(0, 4));
        String leftLabel = buyDate.length() >= 10
                ? (crossYear ? buyDate.replace("-", ".") : buyDate.substring(5).replace("-", ".")) : "";
        String rightLabel = "sold".equals(a.optString("status")) && endDate.length() >= 10
                ? (crossYear ? endDate.replace("-", ".") : endDate.substring(5).replace("-", ".")) : "今天";
        chart.setData(series, store.fmt(chartMax(series), 0), store.fmt(chartMax(series) / 2, 0), "0",
                leftLabel, rightLabel);
        LinearLayout.LayoutParams chp = new LinearLayout.LayoutParams(-1, dp(120));
        chp.topMargin = dp(2);
        chartCard.addView(chart, chp);
        body.addView(chartCard);

        LinearLayout rows = ui.vbox();
        rows.setBackgroundColor(0x00000000);
        rows.setPadding(dp(16), dp(0), dp(16), dp(0));
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(-1, -2);
        rlp.setMargins(dp(14), 0, dp(14), 0);
        rows.setLayoutParams(rlp);
        rows.addView(drow("单价", store.money(a.optDouble("price"))));
        rows.addView(drow("类别", categoryName(a.optString("categoryId"))));
        if (!a.optString("brandName").isEmpty()) {
            LinearLayout br = ui.hbox();
            br.setPadding(0, dp(13), 0, dp(13));
            br.addView(ui.text("品牌", 12, ui.ink2()), lpw(1, -2));
            ImageView blogo = ui.image("brands/" + a.optString("brandId") + ".png", 16);
            if (dark) {
                FrameLayout plate = new FrameLayout(this);
                plate.setBackground(ui.round(0xFFFFFFFF, 10));
                plate.setPadding(dp(2), dp(2), dp(2), dp(2));
                plate.addView(blogo, new android.widget.FrameLayout.LayoutParams(dp(16), dp(16), Gravity.CENTER));
                br.addView(plate, new LinearLayout.LayoutParams(dp(20), dp(20)));
            } else {
                br.addView(blogo);
            }
            TextView bname = ui.text(a.optString("brandName"), 12, ui.ink(), ui.medium);
            bname.setPadding(dp(6), 0, 0, 0);
            br.addView(bname);
            LinearLayout wrap = ui.vbox();
            wrap.addView(br);
            wrap.addView(ui.hline());
            rows.addView(wrap);
        }
        String tn = tagNamesOf(a);
        if (!tn.isEmpty()) rows.addView(drow("标签", tn));
        rows.addView(drow("购买日期", a.optString("buyDate")));
        double shv = a.optDouble("secondhandValue", 0);
        if (shv > 0 && !"sold".equals(a.optString("status"))) {
            rows.addView(drow("二手估值", store.money(shv)));
            String label = a.optBoolean("secondhandInCost", true) ? "实际持有成本" : "持有成本（未扣残值）";
            rows.addView(drow(label, store.money(store.netCost(a)) + " ｜ " + store.money(store.dailyCost(a)) + "/天"));
        }
        if (!"serving".equals(a.optString("status", "serving")))
            rows.addView(drow("状态", Store.statusText(a.optString("status"))));
        if ("retired".equals(a.optString("status")) && !a.optString("retireDate").isEmpty())
            rows.addView(drow("退役日期", a.optString("retireDate")));
        if ("sold".equals(a.optString("status"))) {
            if (a.optDouble("soldPrice", 0) > 0) rows.addView(drow("卖出价格", store.money(a.optDouble("soldPrice"))));
            if (!a.optString("soldDate").isEmpty()) rows.addView(drow("卖出日期", a.optString("soldDate")));
        }
        if (Store.isWish(a)) {
            if (a.optDouble("targetCost", 0) > 0) rows.addView(drow("目标价格", store.money(a.optDouble("targetCost"))));
            if (!a.optString("targetDate").isEmpty()) rows.addView(drow("目标日期", a.optString("targetDate")));
        }
        if (!a.optString("note").isEmpty()) rows.addView(drow("备注", a.optString("note")));
        JSONArray noteImgs = a.optJSONArray("noteImages");
        if (noteImgs != null && noteImgs.length() > 0) {
            LinearLayout imgsRow = ui.hbox();
            imgsRow.setPadding(0, dp(8), 0, dp(8));
            for (int i = 0; i < noteImgs.length() && i < 9; i++) {
                String p = noteImgs.optString(i);
                if (!new java.io.File(p).exists()) continue;
                ImageView iv = ui.fileImage(p, 56);
                LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(dp(56), dp(56));
                ip.rightMargin = dp(8);
                imgsRow.addView(iv, ip);
            }
            rows.addView(imgsRow);
        }
        if (a.optBoolean("excludeTotal")) rows.addView(drow("计入总资产", "否"));
        if (!a.optString("expireAt").isEmpty()) {
            boolean expired = a.optString("expireAt").compareTo(Store.today()) < 0;
            boolean todayExpire = a.optString("expireAt").equals(Store.today());
            rows.addView(drow("到期时间", a.optString("expireAt") + (expired ? "（已过期）" : todayExpire ? "（今日到期）" : "")));
            if (a.optInt("remindDays", 0) > 0) {
                String ul = "week".equals(a.optString("remindUnit", "day")) ? "周" : "month".equals(a.optString("remindUnit", "day")) ? "月" : "天";
                String tl = String.format(java.util.Locale.US, "%02d:%02d", a.optInt("remindHour", 9), a.optInt("remindMinute", 0));
                rows.addView(drow("提前提醒", a.optInt("remindDays") + " " + ul + " · " + tl));
            }
        }
        body.addView(rows);


        LinearLayout del = ui.hbox();
        del.setGravity(Gravity.CENTER);
        del.setBackground(ui.round(dark ? 0xFF3A1F1D : 0xFFFFF0EE, 24));
        del.setPadding(0, dp(15), 0, dp(15));
        ImageView delIcon = ui.icon("ic_delete", 16);
        delIcon.setColorFilter(Ui.RED);
        del.addView(delIcon);
        TextView dt = ui.text("删除资产", 14, Ui.RED, ui.medium);
        dt.setPadding(dp(8), 0, 0, 0);
        del.addView(dt);
        LinearLayout.LayoutParams delLp = new LinearLayout.LayoutParams(-1, -2);
        delLp.setMargins(dp(14), dp(28), dp(14), 0);
        body.addView(del, delLp);
        del.setOnClickListener(v -> DialogKit.confirm(this, "删除资产",
                "删除后无法恢复，确定删除「" + displayName(a) + "」？", "取消", "删除", () -> {
                    store.removeAsset(a.optString("id"));
                    restoreTab();
                    toast("已删除");
                }));

        setScreen(scrollPage(body), false, null);
        screen = "detail";
    }

    private View usageCard(JSONObject a) {
        LinearLayout card = ui.vbox();
        card.setBackground(ui.round(ui.card(), 16));
        card.setElevation(dp(2));
        card.setPadding(dp(16), dp(16), dp(16), dp(14));
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(-1, -2);
        cp.setMargins(dp(14), dp(12), dp(14), 0);
        card.setLayoutParams(cp);

        LinearLayout head = ui.hbox();
        head.addView(ui.text("使用记录", 13, ui.ink(), ui.medium), lpw(1, -2));
        TextView add = ui.pillButton("记录使用", dark ? 0xFFF2F2F2 : 0xFF111111, dark ? 0xFF111111 : 0xFFFFFFFF, 16);
        add.setTextSize(11);
        add.setOnClickListener(v -> addUsage(a));
        head.addView(add);
        card.addView(head);

        JSONArray list = a.optJSONArray("usages");
        int count = list != null ? list.length() : 0;
        int recent = 0;
        String today = Store.today();
        java.util.Calendar cal = java.util.Calendar.getInstance();
        cal.add(java.util.Calendar.DAY_OF_MONTH, -30);
        String since = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(cal.getTime());
        for (int i = 0; list != null && i < list.length(); i++) {
            JSONObject r = list.optJSONObject(i);
            if (r != null && r.optString("date").compareTo(since) >= 0) recent++;
        }
        LinearLayout total = ui.hbox();
        total.setGravity(Gravity.BOTTOM);
        total.setPadding(0, dp(10), 0, dp(4));
        total.addView(ui.text(String.valueOf(count), 20, ui.ink(), ui.bold));
        total.addView(ui.text("  次 · 最近30天 " + recent + " 次", 11, ui.gray()));
        card.addView(total);

        for (int i = 0; list != null && i < list.length(); i++) {
            JSONObject r = list.optJSONObject(i);
            if (r == null) continue;
            LinearLayout row = ui.hbox();
            row.setPadding(0, dp(8), 0, dp(8));
            row.addView(ui.text(r.optString("date"), 11, ui.gray()), lpw(1, -2));
            row.addView(ui.text(r.optString("note"), 12, ui.ink2()));
            final int idx = i;
            row.setOnClickListener(v -> usageRecordDialog(a, idx));
            card.addView(row);
            card.addView(ui.hline());
        }

        if ("count".equals(a.optString("rule", "day")) && count > 0) {
            LinearLayout costRow = ui.hbox();
            costRow.setPadding(0, dp(8), 0, 0);
            costRow.addView(ui.text("次均成本", 11, ui.gray()), lpw(1, -2));
            costRow.addView(ui.text(store.money(store.currentPrice(a) / count), 13, ui.ink(), ui.medium));
            card.addView(costRow);
        }
        int targetDays = a.optInt("targetUsageDays", 0);
        if (targetDays > 0) {
            LinearLayout target = ui.hbox();
            target.setPadding(0, dp(6), 0, 0);
            target.addView(ui.text("目标使用天数 " + targetDays + " 天 · 已使用 " + store.ageDays(a) + " 天", 11, ui.gray()));
            card.addView(target);
        }
        if (count > 0) {
            double[] series = new double[30];
            java.util.Calendar cal2 = java.util.Calendar.getInstance();
            cal2.add(java.util.Calendar.DAY_OF_MONTH, -29);
            java.text.SimpleDateFormat f = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US);
            for (int d = 0; d < 30; d++) {
                String day = f.format(cal2.getTime());
                for (int i = 0; list != null && i < list.length(); i++) {
                    JSONObject r = list.optJSONObject(i);
                    if (r != null && day.equals(r.optString("date"))) series[d]++;
                }
                cal2.add(java.util.Calendar.DAY_OF_MONTH, 1);
            }
            card.addView(ui.text("使用趋势（近30天）", 11, ui.gray()), new LinearLayout.LayoutParams(-1, -2));
            ChartView chart = new ChartView(this);
            chart.setData(series, "", "", "", "", "");
            LinearLayout.LayoutParams chp = new LinearLayout.LayoutParams(-1, dp(70));
            chp.topMargin = dp(4);
            card.addView(chart, chp);
        }
        return card;
    }

    private void usageRecordDialog(JSONObject asset, int index) {
        JSONArray list = asset.optJSONArray("usages");
        if (list == null || index >= list.length()) return;
        JSONObject r = list.optJSONObject(index);
        if (r == null) return;
        DialogKit.list(this, "使用记录", new String[]{"修改日期", "修改备注", "删除记录"}, idx -> {
            if (idx == 0) {
                Calendar cal = Calendar.getInstance();
                try {
                    String[] p = r.optString("date", Store.today()).split("-");
                    cal.set(Integer.parseInt(p[0]), Integer.parseInt(p[1]) - 1, Integer.parseInt(p[2]));
                } catch (Exception ignored) {}
                new DatePickerDialog(this, (v, y, m, d) -> {
                    try { r.put("date", String.format(java.util.Locale.US, "%04d-%02d-%02d", y, m + 1, d)); store.save(); } catch (Exception ignored) {}
                    showDetail(asset.optString("id"));
                }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show();
            } else if (idx == 1) {
                DialogKit.input(this, "使用备注", "输入备注", "保存", note -> {
                    try { r.put("note", note); store.save(); } catch (Exception ignored) {}
                    showDetail(asset.optString("id"));
                });
            } else {
                list.remove(index);
                store.save();
                showDetail(asset.optString("id"));
            }
        }, null, null);
    }

    private void addUsage(JSONObject asset) {
        Calendar cal = Calendar.getInstance();
        new DatePickerDialog(this, (v, y, m, d) -> {
            String date = String.format(java.util.Locale.US, "%04d-%02d-%02d", y, m + 1, d);
            try {
                JSONArray list = asset.optJSONArray("usages");
                if (list == null) { list = new JSONArray(); asset.put("usages", list); }
                list.put(new JSONObject().put("id", "use_" + System.currentTimeMillis())
                        .put("date", date).put("note", ""));
                store.save();
            } catch (Exception ignored) {}
            showDetail(asset.optString("id"));
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show();
    }

    private View priceCard(JSONObject a) {
        LinearLayout card = ui.vbox();
        card.setBackground(ui.round(ui.card(), 16));
        card.setElevation(dp(2));
        card.setPadding(dp(16), dp(16), dp(16), dp(14));
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(-1, -2);
        cp.setMargins(dp(14), dp(12), dp(14), 0);
        card.setLayoutParams(cp);

        LinearLayout head = ui.hbox();
        head.addView(ui.text("物品价值", 13, ui.ink(), ui.medium), lpw(1, -2));
        TextView add = ui.pillButton("记录价格", dark ? 0xFFF2F2F2 : 0xFF111111, dark ? 0xFF111111 : 0xFFFFFFFF, 16);
        add.setTextSize(11);
        add.setOnClickListener(v -> addPriceRecord(a));
        head.addView(add);
        card.addView(head);

        LinearLayout total = ui.hbox();
        total.setGravity(Gravity.BOTTOM);
        total.setPadding(0, dp(10), 0, dp(4));
        total.addView(ui.text(store.money(store.currentPrice(a)), 20, ui.ink(), ui.bold));
        JSONArray list = a.optJSONArray("priceRecords");
        total.addView(ui.text("  " + (list != null ? list.length() : 0) + " 条记录", 11, ui.gray()));
        card.addView(total);

        for (int i = 0; list != null && i < list.length(); i++) {
            JSONObject r = list.optJSONObject(i);
            if (r == null) continue;
            LinearLayout row = ui.hbox();
            row.setPadding(0, dp(8), 0, dp(8));
            row.addView(ui.text(r.optString("date"), 11, ui.gray()), lpw(1, -2));
            row.addView(ui.text(store.money(r.optDouble("price", 0)), 13, ui.ink(), ui.medium));
            final int idx = i;
            row.setOnClickListener(v -> priceRecordDialog(a, idx));
            card.addView(row);
            card.addView(ui.hline());
        }
        return card;
    }

    private void priceRecordDialog(JSONObject asset, int index) {
        JSONArray list = asset.optJSONArray("priceRecords");
        if (list == null || index >= list.length()) return;
        JSONObject r = list.optJSONObject(index);
        if (r == null) return;
        DialogKit.list(this, "价格记录", new String[]{"修改价格", "修改日期", "删除记录"}, idx -> {
            if (idx == 0) {
                DialogKit.input(this, "修改价格", "请输入当前价值", "保存", value -> {
                    double v = parse(value);
                    if (v <= 0) { toast("请输入有效价格"); return; }
                    try { r.put("price", v); store.save(); } catch (Exception ignored) {}
                    showDetail(asset.optString("id"));
                });
            } else if (idx == 1) {
                Calendar cal = Calendar.getInstance();
                try {
                    String[] p = r.optString("date", Store.today()).split("-");
                    cal.set(Integer.parseInt(p[0]), Integer.parseInt(p[1]) - 1, Integer.parseInt(p[2]));
                } catch (Exception ignored) {}
                new DatePickerDialog(this, (v, y, m, d) -> {
                    try { r.put("date", String.format(java.util.Locale.US, "%04d-%02d-%02d", y, m + 1, d)); store.save(); } catch (Exception ignored) {}
                    showDetail(asset.optString("id"));
                }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show();
            } else {
                list.remove(index);
                store.save();
                showDetail(asset.optString("id"));
            }
        }, null, null);
    }

    private void addPriceRecord(JSONObject asset) {
        DialogKit.input(this, "记录价格", "请输入当前价值", "下一步", priceStr -> {
            double value = parse(priceStr);
            if (value <= 0) { toast("请输入有效价格"); return; }
            Calendar cal = Calendar.getInstance();
            new DatePickerDialog(this, (v, y, m, d) -> {
                String date = String.format(java.util.Locale.US, "%04d-%02d-%02d", y, m + 1, d);
                try {
                    JSONArray list = asset.optJSONArray("priceRecords");
                    if (list == null) { list = new JSONArray(); asset.put("priceRecords", list); }
                    list.put(new JSONObject().put("id", "price_" + System.currentTimeMillis())
                            .put("date", date).put("price", value));
                    store.save();
                } catch (Exception ignored) {}
                showDetail(asset.optString("id"));
            }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show();
        });
    }

    private View incomeCard(JSONObject a) {
        LinearLayout card = ui.vbox();
        card.setBackground(ui.round(ui.card(), 16));
        card.setElevation(dp(2));
        card.setPadding(dp(16), dp(16), dp(16), dp(14));
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(-1, -2);
        cp.setMargins(dp(14), dp(12), dp(14), 0);
        card.setLayoutParams(cp);

        LinearLayout head = ui.hbox();
        head.addView(ui.text("收入", 13, ui.ink(), ui.medium), lpw(1, -2));
        TextView add = ui.pillButton("添加收入", dark ? 0xFFF2F2F2 : 0xFF111111, dark ? 0xFF111111 : 0xFFFFFFFF, 16);
        add.setTextSize(11);
        add.setOnClickListener(v -> addIncome(a));
        head.addView(add);
        card.addView(head);

        JSONArray list = a.optJSONArray("incomeRecords");
        double sum = 0;
        int count = list != null ? list.length() : 0;
        for (int i = 0; list != null && i < list.length(); i++) {
            JSONObject r = list.optJSONObject(i);
            if (r != null) sum += r.optDouble("amount", 0);
        }
        LinearLayout total = ui.hbox();
        total.setGravity(Gravity.BOTTOM);
        total.setPadding(0, dp(10), 0, dp(4));
        TextView cur = ui.text(store.sym(), 12, ui.ink(), ui.medium);
        TextView tv = ui.text(store.fmt(sum, store.settingInt("decimals", 2)), 20, ui.ink(), ui.bold);
        total.addView(cur);
        total.addView(tv);
        TextView cnt = ui.text("  " + count + " 条记录", 11, ui.gray());
        total.addView(cnt);
        card.addView(total);

        for (int i = 0; list != null && i < list.length(); i++) {
            JSONObject r = list.optJSONObject(i);
            if (r == null) continue;
            LinearLayout row = ui.hbox();
            row.setPadding(0, dp(8), 0, dp(8));
            row.addView(ui.text(r.optString("date") + "  " + incomeCategoryName(r.optString("categoryId")), 11, ui.gray()), lpw(1, -2));
            row.addView(ui.text(store.money(r.optDouble("amount", 0)), 13, ui.ink(), ui.medium));
            card.addView(row);
            card.addView(ui.hline());
        }
        return card;
    }

    private String incomeCategoryName(String id) {
        for (JSONObject c : store.objs("incomeCategories"))
            if (c.optString("id").equals(id)) return c.optString("name");
        return "其他";
    }

    private void addIncome(JSONObject asset) {
        DialogKit.input(this, "添加收入", "请输入金额", "下一步", amountStr -> {
            double amount = parse(amountStr);
            if (amount <= 0) { toast("请输入有效金额"); return; }
            java.util.List<JSONObject> cats = store.objs("incomeCategories");
            String[] names = new String[Math.max(1, cats.size())];
            if (cats.isEmpty()) names[0] = "其他";
            else for (int i = 0; i < cats.size(); i++) names[i] = cats.get(i).optString("name");
            DialogKit.list(this, "收入分类", names, idx -> {
                String cid = cats.isEmpty() ? "" : cats.get(idx).optString("id");
                Calendar cal = Calendar.getInstance();
                new DatePickerDialog(this, (v, y, m, d) -> {
                    String date = String.format(java.util.Locale.US, "%04d-%02d-%02d", y, m + 1, d);
                    try {
                        JSONArray list = asset.optJSONArray("incomeRecords");
                        if (list == null) { list = new JSONArray(); asset.put("incomeRecords", list); }
                        list.put(new JSONObject().put("id", "inc_" + System.currentTimeMillis())
                                .put("amount", amount).put("date", date).put("categoryId", cid).put("note", ""));
                        store.save();
                    } catch (Exception ignored) {}
                    showDetail(asset.optString("id"));
                }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show();
            }, null, null);
        });
    }

    private View drow(String k, String v) {
        LinearLayout row = ui.hbox();
        row.setPadding(0, dp(13), 0, dp(13));
        row.addView(ui.text(k, 12, ui.ink2()), lpw(1, -2));
        row.addView(ui.text(v, 12, ui.ink(), ui.medium));
        LinearLayout wrap = ui.vbox();
        wrap.addView(row);
        wrap.addView(ui.hline());
        return wrap;
    }

    private double[] detailSeries(JSONObject a, int days) {
        int len = Math.min(60, Math.max(days, 2));
        double[] out = new double[len];
        double price = a.optDouble("price");
        double constant = store.dailyCost(a);
        for (int i = 0; i < len; i++) {
            double age = 1 + (days - 1) * (i / (double) (len - 1));
            out[i] = "day".equals(a.optString("rule", "day")) ? price / Math.max(1, age) : constant;
        }
        return out;
    }

    private String tagNamesOf(JSONObject a) {
        JSONArray ids = a.optJSONArray("tagIds");
        List<String> names = new ArrayList<>();
        if (ids != null) {
            for (int i = 0; i < ids.length(); i++)
                for (JSONObject t : store.objs("tags"))
                    if (t.optString("id").equals(ids.optString(i))) names.add(t.optString("name"));
        }
        return String.join("、", names);
    }

    // ================= 返回键 =================
    @Override public void onBackPressed() {
        if ("backup".equals(screen)) { showSettings(); return; }
        if ("add".equals(screen) || "detail".equals(screen)) { restoreTab(); return; }
        if (!"home".equals(screen) && !"onboard".equals(screen)) { showHome(); return; }
        super.onBackPressed();
    }
}
