/* 小满 1.5.3 本地复刻 — 单文件逻辑 */
(() => {
  "use strict";

  window.addEventListener("error", (e) => {
    const s = document.getElementById("screen");
    if (s) s.innerHTML = `<pre style="padding:80px 20px;font-size:12px;white-space:pre-wrap;color:#c00">${e.message}\n${(e.error && e.error.stack) || ""}</pre>`;
  });

  const IMG = "assets/assets/images/";
  const $ = (sel, el = document) => el.querySelector(sel);
  const screenEl = $("#screen");
  const tabbarEl = $("#tabbar");
  const overlayEl = $("#overlay");

  const todayStr = () => {
    const d = new Date();
    return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}`;
  };
  const escapeHtml = (s) => String(s ?? "").replace(/[&<>"']/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]));

  const DEFAULT_STATE = {
    onboarded: false,
    pro: false,
    ratingShown: false,
    assets: [],
    categories: [],
    tags: [],
    settings: { currency: "CNY", decimals: 2, thousands: true, duration: "days", theme: "system", homeStyle: "default", view: "grid" },
  };
  const STORE_KEY = "xiaoman_replica_v1";
  let S = load();
  let ui = { view: "home", filter: "all", range: "all", obPage: 0, add: null, detailId: null, sheet: null, paywall: false };

  function load() {
    try {
      const raw = localStorage.getItem(STORE_KEY);
      if (!raw) return structuredClone(DEFAULT_STATE);
      const parsed = JSON.parse(raw);
      return { ...structuredClone(DEFAULT_STATE), ...parsed, settings: { ...DEFAULT_STATE.settings, ...(parsed.settings || {}) } };
    } catch { return structuredClone(DEFAULT_STATE); }
  }
  function save() { try { localStorage.setItem(STORE_KEY, JSON.stringify(S)); } catch {} }

  /* ---------- 格式化 ---------- */
  const CURRENCIES = { CNY: "¥", USD: "$", EUR: "€", JPY: "¥", GBP: "£", HKD: "HK$" };
  function sym() { return CURRENCIES[S.settings.currency] || "¥"; }
  function fmtMoney(n, digits = S.settings.decimals) {
    let num = Number(n) || 0;
    let str = num.toFixed(digits);
    if (S.settings.thousands) {
      const [i, d] = str.split(".");
      str = i.replace(/\B(?=(\d{3})+(?!\d))/g, ",") + (d ? "." + d : "");
    }
    return str;
  }
  function money(n) { return sym() + fmtMoney(n); }
  function daysSince(dateStr) {
    if (!dateStr) return 1;
    const t = new Date(dateStr + "T00:00:00").getTime();
    if (Number.isNaN(t)) return 1;
    return Math.max(1, Math.floor((Date.now() - t) / 86400000) + 1);
  }
  function assetAge(a) {
    if (a.status === "sold" && a.soldDate) return Math.max(1, Math.round((new Date(a.soldDate) - new Date(a.buyDate)) / 86400000) + 1);
    return daysSince(a.buyDate);
  }
  function dailyCost(a) {
    const days = assetAge(a);
    if (a.rule === "count") return (a.price || 0) / Math.max(1, Number(a.uses) || 1);
    if (a.rule === "custom") return (a.price || 0) / Math.max(1, Number(a.customDays) || 1);
    return (a.price || 0) / days;
  }
  function durationText(a) {
    const days = assetAge(a);
    if (S.settings.duration === "date") return `${a.buyDate || ""}`;
    return `已使用${days}天`;
  }
  const statusText = (s) => ({ serving: "服役中", retired: "已退役", sold: "已卖出" }[s] || "服役中");

  const assetsOf = (kind) => S.assets.filter((a) => (a.kind || "asset") === kind);
  const serving = () => assetsOf("asset").filter((a) => a.status === "serving");

  function stats(kind = "asset") {
    const list = assetsOf(kind);
    const total = list.filter((a) => a.status !== "sold").reduce((s, a) => s + (+a.price || 0), 0);
    const daily = list.filter((a) => a.status === "serving").reduce((s, a) => s + dailyCost(a), 0);
    return {
      list,
      total,
      daily,
      counts: {
        serving: list.filter((a) => a.status === "serving").length,
        retired: list.filter((a) => a.status === "retired").length,
        sold: list.filter((a) => a.status === "sold").length,
      },
    };
  }

  /* ---------- 图标 ---------- */
  const icon = (name, cls = "") => `<img class="${cls}" src="${IMG}${name}" alt="">`;
  const chev = `<img class="chev" src="${IMG}common/icon_common_right.svg" alt="">`;
  const TAB_ICONS = { home: "tab/icon_tab_home", wishes: "tab/icon_tab_wish", trends: "tab/icon_tab_trend", settings: "tab/icon_tab_settings" };
  const TAB_LABELS = { home: "小满", wishes: "心愿", trends: "趋势", settings: "设置" };

  function tabbar(active) {
    tabbarEl.className = "tabbar";
    tabbarEl.innerHTML = ["home", "wishes", "trends", "settings"].map((t) =>
      `<button class="tab ${t === active ? "on" : ""}" data-tab="${t}">${icon(`${TAB_ICONS[t]}_${t === active ? "s" : "n"}.svg`)}<span>${TAB_LABELS[t]}</span></button>`
    ).join("");
    tabbarEl.querySelectorAll("[data-tab]").forEach((b) => b.addEventListener("click", () => go(b.dataset.tab)));
  }

  function go(tab) {
    ui.view = tab;
    ui.sheet = null;
    if (tab === "home") { renderHome(); tabbar("home"); }
    else if (tab === "wishes") { renderWishes(); tabbar("wishes"); }
    else if (tab === "trends") { renderTrends(); tabbar("trends"); }
    else { renderSettings(); tabbar("settings"); }
  }

  /* ---------- 引导页 ---------- */
  const OB = [
    { title: "资产账户", sub: "财务状况一目了然", img: "guide/img_onboarding_1.png" },
    { title: "每日成本", sub: "价值、成本、状态，一键分析", img: "guide/img_onboarding_2.png" },
    { title: "数据统计", sub: "多种统计图表", img: "guide/img_onboarding_3.png" },
  ];
  function renderOnboarding() {
    tabbarEl.className = "tabbar hidden";
    const p = OB[ui.obPage];
    screenEl.innerHTML = `
      <div class="onboarding">
        <img class="art" src="${IMG}${p.img}" alt="">
        <div class="ob-text"><h1>${p.title}</h1><p>${p.sub}</p></div>
        <div class="ob-foot">
          <div class="dots">${OB.map((_, i) => `<i class="${i === ui.obPage ? "on" : ""}"></i>`).join("")}</div>
          <button class="ob-next" id="obNext">${icon("common/icon_common_arrow_down.svg")}</button>
        </div>
      </div>`;
    $("#obNext").addEventListener("click", () => {
      if (ui.obPage < OB.length - 1) { ui.obPage++; renderOnboarding(); }
      else { S.onboarded = true; save(); go("home"); }
    });
  }

  /* ---------- 首页 ---------- */
  function renderHome() {
    ui.view = "home";
    const st = stats("asset");
    const counts = st.counts;
    const totalCount = st.list.length;
    const pct = (n) => totalCount ? Math.round((n / totalCount) * 100) : 0;
    let list = st.list;
    if (ui.filter === "serving") list = list.filter((a) => a.status === "serving");
    if (ui.filter === "retired") list = list.filter((a) => a.status === "retired");
    if (ui.filter === "sold") list = list.filter((a) => a.status === "sold");

    const chip = (key, label) => `<button class="chip ${ui.filter === key ? "on" : ""}" data-filter="${key}">${label}</button>`;
    const body = list.length ? (
      S.settings.view === "list"
        ? `<div class="asset-list">${list.map(assetRow).join("")}</div>`
        : `<div class="asset-grid">${list.map(assetCard).join("")}</div>`
    ) : emptyState();

    screenEl.innerHTML = `
      <div class="screen home ${S.settings.homeStyle === "classic" ? "classic" : ""}">
        <div class="scroll">
          <div class="home-head">
            <div class="logo">小满</div>
            <div class="head-actions">
              ${icon("common/icon_common_search.svg", "hact")}
              ${icon("common/icon_common_sort.svg", "hact")}
            </div>
          </div>
          <div class="overview">
            <div class="overview-top"><b>资产总览</b><span>${counts.serving}/${totalCount}</span></div>
            <div class="overview-nums">
              <div><div class="lbl">总资产</div><div class="val"><small>${sym()}</small>${fmtMoney(st.total)}</div></div>
              <div style="text-align:right"><div class="lbl">日均成本</div><div class="val"><small>${sym()}</small>${fmtMoney(st.daily)}</div></div>
            </div>
            <div class="overview-stats">
              ${statBlock("服役中", counts.serving, pct(counts.serving))}
              ${statBlock("已退役", counts.retired, pct(counts.retired))}
              ${statBlock("已卖出", counts.sold, pct(counts.sold))}
            </div>
          </div>
          <div class="filters">
            <div class="chips">${chip("all", "全部")}${chip("serving", "服役中")}${chip("retired", "已退役")}${chip("sold", "已卖出")}</div>
            <div class="filter-tools">
              ${icon("common/icon_common_sort.svg")}
              ${icon("common/icon_common_funnel.svg")}
              <button id="viewToggle" style="display:flex">${icon("common/icon_common_square_select.svg")}</button>
            </div>
          </div>
          ${body}
        </div>
      </div>
      <button class="fab" id="fab">+</button>`;

    screenEl.querySelectorAll("[data-filter]").forEach((b) => b.addEventListener("click", () => { ui.filter = b.dataset.filter; renderHome(); }));
    screenEl.querySelectorAll("[data-asset]").forEach((el) => el.addEventListener("click", () => openDetail(el.dataset.asset)));
    $("#viewToggle").addEventListener("click", () => { S.settings.view = S.settings.view === "grid" ? "list" : "grid"; save(); renderHome(); });
    $("#fab").addEventListener("click", () => openAdd(null, "asset"));
  }

  function statBlock(label, n, pct) {
    return `<div class="st"><div class="cap"><span>${label}</span><b>${n}</b></div><div class="bar"><i style="width:${pct}%"></i></div></div>`;
  }

  function assetCard(a) {
    return `<div class="asset-card" data-asset="${a.id}">
      <span class="badge ${a.status}"><i></i>${statusText(a.status)}</span>
      <img class="icon" src="${iconSrc(a)}" alt="">
      <div class="name">${escapeHtml(a.name)}</div>
      <div class="sub">${sym()}${fmtMoney(a.price)} ｜ ${assetAge(a)} 天</div>
      <div class="cost">${sym()}${fmtMoney(dailyCost(a))}<small>/天</small></div>
    </div>`;
  }
  function assetRow(a) {
    return `<div class="asset-card" data-asset="${a.id}" style="flex-direction:row;align-items:center;gap:12px;min-height:0;margin:0 0 10px">
      <img class="icon" src="${iconSrc(a)}" alt="" style="width:46px;height:46px">
      <div style="min-width:0;flex:1">
        <div class="name" style="margin:0">${escapeHtml(a.name)}</div>
        <div class="sub">${sym()}${fmtMoney(a.price)} ｜ ${assetAge(a)} 天</div>
      </div>
      <div class="cost" style="margin:0;padding:0">${sym()}${fmtMoney(dailyCost(a))}<small>/天</small></div>
    </div>`;
  }
  function emptyState() {
    return `<div class="empty">${icon("home/png/icon_home_defaul_nofile.png")}<b>空空如也</b><span>点击 <em>+</em> 添加资产</span></div>`;
  }
  const ICON_POOL = [
    ...Array.from({ length: 13 }, (_, i) => `icons/app_icon_v${i + 1}.png`),
    "emojis/digital/3d%20handphone.png", "emojis/digital/3d%20headphone.png", "emojis/digital/3d%20joystick.png",
    "emojis/digital/3d%20printer.png", "emojis/digital/3d%20video.png", "emojis/digital/computer%20mouse.png",
    "home/png/icon_home_asset.png", "tab/icon_tab_home_s.svg",
  ];
  const assetUrl = (p) => IMG + p.replace(/%/g, "%25");
  const iconSrc = (a) => a.icon && ICON_POOL.includes(a.icon) ? assetUrl(a.icon) : IMG + "home/png/icon_home_asset.png";

  /* ---------- 心愿页 ---------- */
  function renderWishes() {
    const st = stats("wish");
    const list = st.list;
    screenEl.innerHTML = `
      <div class="screen">
        <div class="scroll">
          <div class="page-head"><h1>心愿</h1></div>
          <div class="wish-card">
            <div><div class="lbl">心愿总值</div><div class="val">${sym()}${fmtMoney(st.total)}</div><div class="cnt">心愿数量 ${list.length} 个</div></div>
            ${icon("wishes/icon_wishes_wish_bottle.svg")}
          </div>
          ${list.length ? `<div class="asset-grid" style="margin-top:14px">${list.map((a) => assetCard(a)).join("")}</div>` : emptyState()}
        </div>
      </div>
      <button class="fab" id="fab">+</button>`;
    screenEl.querySelectorAll("[data-asset]").forEach((el) => el.addEventListener("click", () => openDetail(el.dataset.asset)));
    $("#fab").addEventListener("click", () => openAdd(null, "wish"));
  }

  /* ---------- 趋势页 ---------- */
  const RANGES = [["all", "全部"], ["week", "最近一周"], ["d90", "最近90天"], ["d180", "最近180天"], ["year", "最近一年"]];
  function seriesDays() { return { all: 365, week: 7, d90: 90, d180: 180, year: 365 }[ui.range] || 365; }

  function renderTrends() {
    const st = stats("asset");
    const rangeChips = RANGES.map(([k, l]) => `<button class="chip ${ui.range === k ? "on" : ""}" data-range="${k}">${l}</button>`).join("");
    const locked = !S.pro;
    const content = `
      <div class="trend-content ${locked ? "locked" : ""}">
        <div class="tcard">
          <div style="font-size:12px;color:var(--gray)">资产总值</div>
          <div class="big">${sym()}${fmtMoney(st.total)}</div>
          ${trendRow("服役", st.counts.serving, st.list.filter(a=>a.status==="serving").reduce((s,a)=>s+dailyCost(a),0), "var(--lime-deep)")}
          ${trendRow("退役", st.counts.retired, 0, "var(--teal)")}
          ${trendRow("已卖出", st.counts.sold, 0, "var(--orange)")}
        </div>
        <div class="tcard">
          <div style="font-size:13px;font-weight:500">资产价值趋势</div>
          <div class="big" style="font-size:17px;margin:6px 0 0">${sym()}${fmtMoney(st.total)}</div>
          ${lineChart(st.list, seriesDays())}
        </div>
        <div class="tcard">
          <div style="font-size:13px;font-weight:500;margin-bottom:8px">类型分布</div>
          ${typeBars(st.list)}
        </div>
      </div>`;
    screenEl.innerHTML = `
      <div class="screen">
        <div class="page-head"><h1>趋势</h1></div>
        <div class="seg">${rangeChips}</div>
        <div class="scroll trend-wrap">
          ${content}
          ${locked ? `<div class="trend-lock">
              ${icon("common/icon_common_viplock.svg", "lockico")}
              <b>加入小满PRO</b><span>解锁所有功能，查看资产数据</span>
              <button id="trendUnlock">解锁</button>
            </div>` : ""}
        </div>
      </div>`;
    screenEl.querySelectorAll("[data-range]").forEach((b) => b.addEventListener("click", () => { ui.range = b.dataset.range; renderTrends(); }));
    const u = $("#trendUnlock");
    if (u) u.addEventListener("click", openPaywall);
  }
  function trendRow(name, count, value, color) {
    const st = stats("asset");
    const pct = st.list.length ? Math.round((count / st.list.length) * 100) : 0;
    return `<div class="trow"><div class="nm"><span class="dot" style="background:${color}"></span>${name}<span class="pct">${pct}%</span></div>
      <div style="text-align:right"><div class="pv">${sym()}${fmtMoney(value)}</div></div></div>`;
  }
  function lineChart(list, days) {
    const W = 340, H = 150, pad = 8;
    const end = new Date(); const start = new Date(Date.now() - (days - 1) * 86400000);
    const pts = [];
    for (let i = 0; i < days; i++) {
      const d = new Date(start.getTime() + i * 86400000).toISOString().slice(0, 10);
      const v = list.filter((a) => (!a.buyDate || a.buyDate <= d) && (a.status !== "sold" || (a.soldDate || d) >= d)).reduce((s, a) => s + (+a.price || 0), 0);
      pts.push(v);
    }
    const maxV = Math.max(...pts, 100);
    const x0 = pad, x1 = W - 46;
    const xy = pts.map((v, i) => [x0 + (i * (x1 - x0)) / (days - 1), H - pad - (v / maxV) * (H - pad * 2)]);
    const path = xy.map((p, i) => `${i ? "L" : "M"}${p[0].toFixed(1)},${p[1].toFixed(1)}`).join(" ");
    const area = `${path} L${x1},${H - pad} L${x0},${H - pad} Z`;
    return `<svg class="chart" viewBox="0 0 ${W} ${H}" style="margin-top:8px">
      <defs><linearGradient id="ga" x1="0" y1="0" x2="0" y2="1">
        <stop offset="0" stop-color="#c9f44f" stop-opacity=".55"/><stop offset="1" stop-color="#c9f44f" stop-opacity="0"/>
      </linearGradient></defs>
      ${[0.33, 0.66].map((r) => `<line class="gridline" x1="${pad}" x2="${W - pad}" y1="${pad + (H - pad * 2) * r}" y2="${pad + (H - pad * 2) * r}"/>`).join("")}
      <path d="${area}" fill="url(#ga)"/>
      <path d="${path}" fill="none" stroke="#b6ee25" stroke-width="2" stroke-linecap="round"/>
      <circle cx="${xy[xy.length - 1][0]}" cy="${xy[xy.length - 1][1]}" r="3.5" fill="#fff" stroke="#b6ee25" stroke-width="2"/>
      <text class="axis" x="${W - 2}" y="${pad + 8}" text-anchor="end">${fmtMoney(maxV, 0)}</text>
      <text class="axis" x="${W - 2}" y="${H / 2 + 3}" text-anchor="end">${fmtMoney(maxV / 2, 0)}</text>
      <text class="axis" x="${W - 2}" y="${H - pad - 1}" text-anchor="end">0</text>
    </svg>`;
  }
  function typeBars(list) {
    if (!list.length) return `<div style="font-size:11px;color:var(--gray);padding:12px 0 4px">暂无数据</div>`;
    const by = {};
    list.forEach((a) => {
      const c = S.categories.find((c) => c.id === a.categoryId);
      const k = c ? c.name : "未分类";
      by[k] = (by[k] || 0) + 1;
    });
    const colors = ["#c9f44f", "#3ed6b0", "#ff7a45", "#7aa2ff", "#ffd166"];
    return Object.entries(by).map(([k, n], i) => `
      <div style="margin:8px 0">
        <div style="display:flex;justify-content:space-between;font-size:11px;color:var(--ink-2);margin-bottom:5px"><span>${escapeHtml(k)}</span><span>${n} 件</span></div>
        <div class="bar"><i style="width:${Math.round((n / list.length) * 100)}%;background:${colors[i % colors.length]}"></i></div>
      </div>`).join("");
  }

  /* ---------- 设置页 ---------- */
  function renderSettings() {
    const t = S.settings.theme;
    const themeBtn = (k, ico, label) => `<button class="${t === k ? "on" : ""}" data-theme-set="${k}">${icon(`settings/${ico}.svg`)}<span class="sr">${label}</span></button>`;
    screenEl.innerHTML = `
      <div class="screen">
        <div class="scroll">
          <div class="page-head"><h1>设置</h1><button class="text-btn" id="restore" style="display:flex;align-items:center;gap:4px">${icon("settings/icon_settings_restore.svg", "rs")}恢复会员</button></div>
          <div class="login"><div class="avatar">${icon("launch_logo.png")}</div><div><b>点击登录</b><span>登录后同步数据，换机不丢</span></div></div>

          <div class="pro-banner">
            <div><h3>加入小满PRO</h3><p>解锁全部权限，让资产心中小满</p><button class="go" id="proGo">了解权益</button></div>
            ${icon("launch_logo.png")}
          </div>

          <div class="glabel" style="padding-left:20px">数值与单位</div>
          <div class="group" style="margin-top:0">
            <div class="row click" data-act="currency"><img class="ico" src="${IMG}settings/icon_settings_unit.svg">货币单位<span class="rt">${S.settings.currency} ${chev}</span></div>
            <div class="row click" data-act="decimals"><img class="ico" src="${IMG}settings/icon_settings_decimal.svg">小数点设置<span class="rt">保留 ${S.settings.decimals} 位 ${chev}</span></div>
            <div class="row click" data-act="thousands"><img class="ico" src="${IMG}settings/icon_settings_comma.svg">使用千位分隔符<span class="rt"><span class="switch ${S.settings.thousands ? "on" : ""}" id="thousandsSw"><i></i></span></span></div>
            <div class="row"><img class="ico" src="${IMG}settings/icon_settings_timeformat.svg">时长显示格式<span class="rt"><span class="mini-seg" id="durSeg"><button class="${S.settings.duration === "days" ? "on" : ""}" data-dur="days">天数</button><button class="${S.settings.duration === "date" ? "on" : ""}" data-dur="date">日期</button></span></span></div>
          </div>

          <div class="glabel" style="padding-left:20px">数据管理</div>
          <div class="group" style="margin-top:0">
            <div class="row click" data-act="categories"><img class="ico" src="${IMG}settings/icon_settings_type.svg">分类管理<span class="rt">${S.categories.length} 个 ${chev}</span></div>
            <div class="row click" data-act="tags"><img class="ico" src="${IMG}settings/icon_settings_tags.svg">标签管理<span class="rt">${S.tags.length} 个 ${chev}</span></div>
            <div class="row click" data-act="backup"><img class="ico" src="${IMG}settings/icon_settings_backup.svg">备份与恢复<span class="rt">${chev}</span></div>
            <div class="row click" data-act="sync"><img class="ico" src="${IMG}settings/icon_settings_cloud.svg">数据同步<span class="rt">${chev}</span></div>
          </div>

          <div class="glabel" style="padding-left:20px">显示与外观</div>
          <div class="group" style="margin-top:0">
            <div class="row"><img class="ico" src="${IMG}settings/icon_settings_theme.svg">主题模式<span class="rt theme-dots">${themeBtn("system", "icon_settings_model_system_normal", "跟随系统")}${themeBtn("light", "icon_settings_model_light_normal", "浅色")}${themeBtn("dark", "icon_settings_model_dark_normal", "深色")}</span></div>
            <div class="row"><img class="ico" src="${IMG}settings/icon_settings_homestyle.svg">首页风格<span class="rt"><span class="mini-seg" id="styleSeg"><button class="${S.settings.homeStyle === "default" ? "on" : ""}" data-style="default">默认</button><button class="${S.settings.homeStyle === "classic" ? "on" : ""}" data-style="classic">经典</button></span></span></div>
            <div class="row click" data-act="view"><img class="ico" src="${IMG}settings/icon_settings_display.svg">视图样式<span class="rt">${S.settings.view === "grid" ? "网格" : "列表"} ${chev}</span></div>
          </div>

          <div class="glabel" style="padding-left:20px">通用设置</div>
          <div class="group" style="margin-top:0">
            <div class="row click" data-act="security"><img class="ico" src="${IMG}settings/icon_settings_privacy_lock.svg">密码与安全<span class="rt">${chev}</span></div>
            <div class="row click" data-act="language"><img class="ico" src="${IMG}settings/icon_settings_language.svg">语言<span class="rt">简体中文 ${chev}</span></div>
          </div>

          <div class="glabel" style="padding-left:20px">反馈与支持</div>
          <div class="group" style="margin-top:0">
            <div class="row click" data-act="cs"><img class="ico" src="${IMG}settings/icon_settings_wechat.svg">微信客服<span class="rt">${chev}</span></div>
            <div class="row click" data-act="group"><img class="ico" src="${IMG}settings/icon_settings_wechat.svg">微信用户群<span class="rt">${chev}</span></div>
            <div class="row click" data-act="feedback"><img class="ico" src="${IMG}settings/icon_settings_feedback.svg">我要提意见<span class="rt">${chev}</span></div>
            <div class="row click" data-act="contact"><img class="ico" src="${IMG}settings/icon_settings_contact.svg">联系我们<span class="rt">${chev}</span></div>
          </div>

          <div class="glabel" style="padding-left:20px">关于</div>
          <div class="group" style="margin-top:0">
            <div class="row click" data-act="terms"><img class="ico" src="${IMG}settings/icon_settings_terms.svg">用户协议<span class="rt">${chev}</span></div>
            <div class="row click" data-act="privacy"><img class="ico" src="${IMG}settings/icon_settings_privacy.svg">隐私政策<span class="rt">${chev}</span></div>
          </div>
          <div style="text-align:center;font-size:9px;color:var(--gray);padding:14px 0 20px">版本 1.5.3(39)<br>粤ICP备2024266440号-4A · 本地复刻版</div>
        </div>
      </div>`;

    $("#restore").addEventListener("click", openPaywall);
    $("#proGo").addEventListener("click", openPaywall);
    screenEl.querySelectorAll("[data-theme-set]").forEach((b) => b.addEventListener("click", () => { S.settings.theme = b.dataset.themeSet; applyTheme(); save(); renderSettings(); }));
    screenEl.querySelectorAll("[data-dur]").forEach((b) => b.addEventListener("click", () => { S.settings.duration = b.dataset.dur; save(); renderSettings(); }));
    screenEl.querySelectorAll("[data-style]").forEach((b) => b.addEventListener("click", () => { S.settings.homeStyle = b.dataset.style; save(); renderSettings(); }));
    $("#thousandsSw").addEventListener("click", (e) => { e.stopPropagation(); S.settings.thousands = !S.settings.thousands; save(); renderSettings(); });
    screenEl.querySelectorAll("[data-act]").forEach((el) => el.addEventListener("click", () => settingsAction(el.dataset.act)));
  }

  const toast = (msg) => {
    const el = document.createElement("div");
    el.className = "toast"; el.textContent = msg;
    overlayEl.appendChild(el);
    setTimeout(() => el.remove(), 1800);
  };

  function settingsAction(act) {
    if (act === "currency") return moneySheet("货币单位", Object.keys(CURRENCIES), S.settings.currency, (v) => { S.settings.currency = v; save(); renderSettings(); });
    if (act === "decimals") return moneySheet("小数位数", [0, 1, 2, 3].map(String), String(S.settings.decimals), (v) => { S.settings.decimals = +v; save(); renderSettings(); });
    if (act === "thousands") { S.settings.thousands = !S.settings.thousands; save(); renderSettings(); return; }
    if (act === "categories") return manageSheet("categories");
    if (act === "tags") return manageSheet("tags");
    if (act === "backup") return backupSheet();
    if (act === "view") { S.settings.view = S.settings.view === "grid" ? "list" : "grid"; save(); renderSettings(); return; }
    if (act === "terms") return docSheet("用户协议", "这里是《用户协议》的本地复刻示意内容。\n\n小满 App 仅将资产数据保存在本机，本次复刻版本使用浏览器 localStorage 模拟同样的本地存储。");
    if (act === "privacy") return docSheet("隐私政策", "这里是《隐私政策》的本地复刻示意内容。\n\n复刻版本不收集任何个人信息，所小满据仅存储在你的浏览器中。");
    toast("本地复刻版暂未接入该功能");
  }

  /* ---------- 弹层工具 ---------- */
  function showOverlay(html, { center = false } = {}) {
    overlayEl.classList.add("active");
    overlayEl.innerHTML = `<div class="scrim ${center ? "center" : ""}" id="scrim">${html}</div>`;
    $("#scrim").addEventListener("click", (e) => { if (e.target.id === "scrim") closeOverlay(); });
    return overlayEl;
  }
  function closeOverlay() { overlayEl.classList.remove("active"); overlayEl.innerHTML = ""; ui.sheet = null; }

  function sheetShell(title, body, { right = "" } = {}) {
    return `<div class="sheet" id="sheet" onclick="event.stopPropagation()">
      <div class="sheet-head"><b>${title}</b><span style="display:flex;align-items:center;gap:14px">${right}<img class="x" src="${IMG}common/icon_common_close.svg" id="sheetX"></span></div>
      <div class="sheet-body">${body}</div>
    </div>`;
  }
  function bindSheet() { const x = $("#sheetX"); if (x) x.addEventListener("click", closeOverlay); }

  function moneySheet(title, options, current, onPick) {
    showOverlay(sheetShell(title, `<div id="pickList">${options.map((o) => `
      <div class="row click" data-pick="${o}" style="border-radius:10px">${title.includes("货币") ? `<b style="width:24px">${CURRENCIES[o]}</b>${o}` : `保留 ${o} 位小数`}
      <span class="rt">${o === current ? "✓" : ""}</span></div>`).join("")}</div>`));
    bindSheet();
    overlayEl.querySelectorAll("[data-pick]").forEach((el) => el.addEventListener("click", () => { onPick(el.dataset.pick); closeOverlay(); }));
  }

  function emptySheet(iconPath, title, sub) {
    return `<div class="sheet-empty">${icon(iconPath)}<b>${title}</b><span>${sub}</span></div>`;
  }

  function manageSheet(kind) {
    const isCat = kind === "categories";
    const items = S[kind];
    const body = items.length
      ? `<div>${items.map((it) => `<div class="row" style="border-radius:10px"><img class="ico" src="${IMG}common/icon_common_classify.svg"><span>${escapeHtml(it.name)}</span><span class="rt">${S.assets.filter((a) => (isCat ? a.categoryId : (a.tagIds || []).includes(it.id))).length} 项</span></div>`).join("")}</div>`
      : emptySheet("home/png/icon_home_defaul_nofile.png", "空空如也", isCat ? "点击添加分类" : "点击添加标签");
    showOverlay(sheetShell(isCat ? "分类管理" : "标签管理", body, { right: `<button class="text-btn" id="newItem" style="color:var(--ink)">新建</button>` }));
    bindSheet();
    $("#newItem").addEventListener("click", () => newItemDialog(kind));
  }

  function newItemDialog(kind) {
    const isCat = kind === "categories";
    showOverlay(`<div class="center-card" onclick="event.stopPropagation()">
      <h3>新建${isCat ? "分类" : "标签"}</h3>
      <div class="sub">给${isCat ? "分类" : "标签"}起一个名字</div>
      <input class="dialog-input" id="newName" placeholder="请输入${isCat ? "分类" : "标签"}名称">
      <div class="dialog-actions"><button id="dlgCancel">关闭</button><button id="dlgOk" class="primary">创建</button></div>
    </div>`, { center: true });
    $("#dlgCancel").addEventListener("click", () => { closeOverlay(); manageSheet(kind); });
    $("#dlgOk").addEventListener("click", () => {
      const name = $("#newName").value.trim();
      if (!name) return toast("请输入名称");
      S[kind].push({ id: "id_" + Date.now(), name });
      save(); closeOverlay(); manageSheet(kind);
    });
  }

  function docSheet(title, text) {
    showOverlay(sheetShell(title, `<div style="font-size:12px;line-height:1.8;color:var(--ink-2);white-space:pre-wrap;padding:6px 2px 14px">${text}</div>`));
    bindSheet();
  }

  function backupSheet() {
    showOverlay(sheetShell("备份与恢复", `
      <div class="row click" id="doExport" style="border-radius:10px"><img class="ico" src="${IMG}settings/icon_settings_csv.svg">导出备份（JSON）${chev}</div>
      <div class="row click" id="doErase" style="border-radius:10px"><img class="ico" src="${IMG}settings/icon_settings_restore.svg">清空本地数据${chev}</div>
      <div style="font-size:11px;color:var(--gray);padding:12px 4px 6px;line-height:1.7">复刻版数据保存在浏览器 localStorage，导出后可手动备份。</div>`));
    bindSheet();
    $("#doExport").addEventListener("click", () => {
      const blob = new Blob([JSON.stringify(S, null, 2)], { type: "application/json" });
      const a = document.createElement("a");
      a.href = URL.createObjectURL(blob); a.download = "xiaoman-backup.json"; a.click();
      URL.revokeObjectURL(a.href); toast("已导出 xiaoman-backup.json");
    });
    $("#doErase").addEventListener("click", () => {
      showOverlay(`<div class="center-card" onclick="event.stopPropagation()">
        <h3>清空本地数据？</h3><div class="sub">所有资产、分类与标签将被删除，无法恢复</div>
        <div class="dialog-actions"><button id="eraseNo">取消</button><button id="eraseYes" class="primary ready" style="background:#ff5a4e;color:#fff">清空</button></div>
      </div>`, { center: true });
      $("#eraseNo").addEventListener("click", () => closeOverlay());
      $("#eraseYes").addEventListener("click", () => { S = structuredClone(DEFAULT_STATE); S.onboarded = true; save(); closeOverlay(); go("home"); toast("已清空"); });
    });
  }

  /* ---------- 会员页 ---------- */
  let paywallSel = 0;
  const PRICES = [
    { nm: "终身会员", ds: "一次费用，永久享用", am: "¥68", old: "¥188", tag: "限时特惠", plan: "lifetime" },
    { nm: "年会员", ds: "低至¥0.13/天", am: "¥48", plan: "year" },
    { nm: "周会员", ds: "≈一杯奶茶", am: "¥15", plan: "week" },
  ];
  function openPaywall() {
    paywallSel = 0;
    overlayEl.classList.add("active");
    overlayEl.innerHTML = paywallHtml();
    bindPaywall();
  }
  function paywallHtml() {
    return `<div class="paywall" id="paywall">
      <div class="top"><img class="x" src="${IMG}common/icon_common_close.svg" id="pwX"><span class="restore" id="pwRestore">${icon("common/icon_common_help.svg")}恢复会员</span></div>
      <div class="hero">
        <img class="logo" src="${IMG}launch_logo.png" alt="">
        <h2>获取小满专业版</h2>
        <p>告别被动消费，拥抱长期主义</p>
      </div>
      <div class="perks">
        <div class="perk">${icon("in_app_purchase/png/icon_subscribe_analysis.png")}多维度数据分析</div>
        <div class="perk">${icon("in_app_purchase/png/icon_subscribe_tag.png")}分类个数无限制</div>
        <div class="perk">${icon("in_app_purchase/png/icon_subscribe_futrue.png")}自定义标签关联</div>
        <div class="perk">${icon("in_app_purchase/png/icon_subscribe_noad.png")}纯净无广告</div>
        <div class="perk">${icon("in_app_purchase/png/icon_subscribe_icons.png")}后续新功能自动获得</div>
      </div>
      <div class="prices">
        ${PRICES.map((p, i) => `<div class="price ${i === paywallSel ? "on" : ""}" data-price="${i}">
          ${p.tag ? `<span class="tag">${p.tag}</span>` : ""}
          <div class="nm">${p.nm}</div><div class="ds">${p.ds}</div><div class="am">${p.am}${p.old ? `<s>${p.old}</s>` : ""}</div>
        </div>`).join("")}
      </div>
      <div class="pay-note">支付宝交易｜七天无理由退款</div>
      <button class="unlock" id="pwUnlock">立即解锁</button>
      <label class="agree"><input type="checkbox" id="pwAgree">已阅读并同意 <u>《用户协议》</u> · <u>《隐私政策》</u> · <u>《会员服务协议》</u></label>
    </div>`;
  }
  function bindPaywall() {
    const rerender = () => { overlayEl.innerHTML = paywallHtml(); bindPaywall(); };
    $("#pwX").addEventListener("click", closeOverlay);
    $("#pwRestore").addEventListener("click", () => toast("未查询到可恢复的会员订单"));
    overlayEl.querySelectorAll("[data-price]").forEach((el) => el.addEventListener("click", () => { paywallSel = +el.dataset.price; rerender(); }));
    $("#pwUnlock").addEventListener("click", () => {
      if (!$("#pwAgree").checked) return toast("请先阅读并同意相关协议");
      S.pro = true; save(); closeOverlay();
      if (ui.view === "trends") renderTrends(); else if (ui.view === "settings") renderSettings();
      toast("已解锁小满专业版（本地演示）");
    });
  }

  /* ---------- 添加/编辑 ---------- */
  const RULES = [["day", "按天"], ["count", "按次"], ["custom", "自定义"]];
  function openAdd(editId, presetKind) {
    const editing = editId ? S.assets.find((a) => a.id === editId) : null;
    ui.add = editing ? { ...editing } : {
      id: "a_" + Date.now(), name: "", price: "", buyDate: todayStr(), kind: presetKind || "asset",
      status: "serving", categoryId: "", tagIds: [], rule: "day", uses: "", customDays: "", icon: "home/png/icon_home_asset.png",
    };
    ui.addEditing = !!editing;
    renderAdd();
  }
  function renderAdd() {
    tabbarEl.className = "tabbar hidden";
    const a = ui.add;
    const isEdit = ui.addEditing;
    const cats = S.categories;
    const catName = cats.find((c) => c.id === a.categoryId)?.name || "未分类";
    const tagNames = S.tags.filter((t) => (a.tagIds || []).includes(t.id)).map((t) => t.name);
    screenEl.innerHTML = `
      <div class="subpage">
        <div class="subhead">
          <button class="round" id="addBack">${icon("common/icon_common_close.svg")}</button>
          <b>${isEdit ? "编辑资产" : "新增资产"}</b>
          <button class="round save ${a.name && a.price ? "" : "disabled"}" id="addSave">${icon("common/icon_common_confirm.svg")}</button>
        </div>
        <div class="subbody">
          <div class="icon-pick" id="iconPick">
            <div class="ico"><img src="${iconSrc(a)}" alt=""></div>
            <div class="change">${icon("common/icon_common_change.svg")}更换图标</div>
          </div>
          <div class="form-card">
            <div class="field"><img class="ico" src="${IMG}edit/icon_edit_name.svg">名称<span class="rt"><input id="fName" placeholder="请输入资产名称" value="${escapeHtml(a.name)}"></span></div>
            <div class="field"><img class="ico" src="${IMG}edit/icon_edit_price.svg">价格<span class="rt"><input id="fPrice" type="number" inputmode="decimal" placeholder="请输入物品价格" value="${a.price}"></span></div>
            <div class="field"><img class="ico" src="${IMG}edit/icon_edit_type.svg">类型<span class="rt"><span class="type-seg" id="kindSeg">
              <button data-kind="asset" class="${a.kind !== "wish" ? "on" : ""}">资产</button>
              <button data-kind="wish" class="${a.kind === "wish" ? "on" : ""}">心愿</button></span></span></div>
          </div>
          <div class="form-card">
            <div class="field"><img class="ico" src="${IMG}edit/icon_edit_calendar.svg">购买日期<span class="rt"><input id="fDate" type="date" value="${a.buyDate}"></span></div>
            <div class="field click" id="fCategory"><img class="ico" src="${IMG}edit/icon_edit_category.svg">分类<span class="rt">${escapeHtml(catName)} <img class="chev" src="${IMG}common/icon_common_right.svg"></span></div>
            <div class="field click" id="fTags"><img class="ico" src="${IMG}edit/icon_edit_tag.svg">标签<span class="rt">${tagNames.length ? escapeHtml(tagNames.join("、")) : "选择标签"} <img class="chev" src="${IMG}common/icon_common_right.svg"></span></div>
          </div>
          ${isEdit ? `<div class="form-card">
            <div class="field"><img class="ico" src="${IMG}edit/icon_edit_switch.svg">状态<span class="rt"><span class="type-seg" id="statusSeg">
              ${["serving", "retired", "sold"].map((s) => `<button data-status="${s}" class="${a.status === s ? "on" : ""}">${statusText(s)}</button>`).join("")}
            </span></span></div>
          </div>` : ""}
          <div class="form-card" style="padding-bottom:12px">
            <div class="rule-head"><img class="ico" src="${IMG}edit/icon_edit_rule.svg">成本计算规则</div>
            <div class="rule-seg" id="ruleSeg">${RULES.map(([k, l]) => `<button data-rule="${k}" class="${a.rule === k ? "on" : ""}">${l}</button>`).join("")}</div>
          </div>
          ${a.rule === "count" ? `<div class="form-card"><div class="field"><img class="ico" src="${IMG}edit/icon_edit_earnings.svg">使用次数<span class="rt"><input id="fUses" type="number" placeholder="请输入次数" value="${a.uses || ""}"></span></div></div>` : ""}
          ${a.rule === "custom" ? `<div class="form-card"><div class="field"><img class="ico" src="${IMG}edit/icon_edit_target.svg">计费天数<span class="rt"><input id="fCustom" type="number" placeholder="请输入天数" value="${a.customDays || ""}"></span></div></div>` : ""}
          <div class="rule-hint">按天：价格 ÷ 已使用天数；按次：价格 ÷ 使用次数；自定义：价格 ÷ 自定义天数。</div>
        </div>
      </div>`;

    $("#addBack").addEventListener("click", () => ui.addEditing ? openDetail(a.id) : go(ui.view === "wishes" ? "wishes" : "home"));
    $("#addSave").addEventListener("click", saveAdd);
    $("#fName").addEventListener("input", (e) => { a.name = e.target.value; refreshSaveBtn(); });
    $("#fPrice").addEventListener("input", (e) => { a.price = e.target.value; refreshSaveBtn(); });
    $("#fDate").addEventListener("change", (e) => { a.buyDate = e.target.value; });
    $("#iconPick").addEventListener("click", pickIconSheet);
    $("#fCategory").addEventListener("click", pickCategorySheet);
    $("#fTags").addEventListener("click", pickTagsSheet);
    screenEl.querySelectorAll("[data-kind]").forEach((b) => b.addEventListener("click", () => { a.kind = b.dataset.kind; renderAdd(); }));
    screenEl.querySelectorAll("[data-rule]").forEach((b) => b.addEventListener("click", () => { a.rule = b.dataset.rule; renderAdd(); }));
    screenEl.querySelectorAll("[data-status]").forEach((b) => b.addEventListener("click", () => { a.status = b.dataset.status; renderAdd(); }));
    const fu = $("#fUses"); if (fu) fu.addEventListener("input", (e) => { a.uses = e.target.value; });
    const fc = $("#fCustom"); if (fc) fc.addEventListener("input", (e) => { a.customDays = e.target.value; });
  }
  function refreshSaveBtn() {
    const b = $("#addSave");
    if (b) b.classList.toggle("disabled", !(ui.add.name && ui.add.price));
  }
  function saveAdd() {
    const a = ui.add;
    if (!a.name || !a.price) return toast("请填写名称与价格");
    const idx = S.assets.findIndex((x) => x.id === a.id);
    if (idx >= 0) S.assets[idx] = a; else S.assets.push(a);
    save();
    if (!ui.addEditing && !S.ratingShown) return ratingDialog();
    afterAdd();
  }
  function afterAdd() {
    const kind = ui.add.kind;
    ui.add = null; ui.detailId = null;
    go(kind === "wish" ? "wishes" : "home");
  }
  function ratingDialog() {
    S.ratingShown = true; save();
    showOverlay(`<div class="center-card" onclick="event.stopPropagation()">
      <h3>喜欢这个应用吗？</h3><div class="sub">请给我们评分支持！</div>
      <div class="dialog-actions"><button id="rateNo">不喜欢</button><button id="rateYes" class="primary ready">喜欢</button></div>
    </div>`, { center: true });
    const done = (like) => { closeOverlay(); toast(like ? "感谢你的支持 ♥" : "我们会继续努力"); afterAdd(); };
    $("#rateNo").addEventListener("click", () => done(false));
    $("#rateYes").addEventListener("click", () => done(true));
  }
  function pickIconSheet() {
    const a = ui.add;
    showOverlay(sheetShell("选择图标", `<div class="picker-grid">${ICON_POOL.map((p) => `
      <button data-icon="${p}" class="${a.icon === p ? "on" : ""}"><img src="${assetUrl(p)}" alt=""></button>`).join("")}</div>`));
    bindSheet();
    overlayEl.querySelectorAll("[data-icon]").forEach((b) => b.addEventListener("click", () => { a.icon = b.dataset.icon; closeOverlay(); renderAdd(); }));
  }
  function pickCategorySheet() {
    const a = ui.add;
    const body = S.categories.length
      ? S.categories.map((c) => `<div class="row click" data-cat="${c.id}" style="border-radius:10px"><img class="ico" src="${IMG}common/icon_common_classify.svg">${escapeHtml(c.name)}<span class="rt">${a.categoryId === c.id ? "✓" : ""}</span></div>`).join("")
      : emptySheet("home/png/icon_home_defaul_nofile.png", "空空如也", "点击添加分类");
    showOverlay(sheetShell("选择分类", body, { right: `<button class="text-btn" id="catNew" style="color:var(--ink)">新建</button>` }));
    bindSheet();
    $("#catNew").addEventListener("click", () => {
      showOverlay(`<div class="center-card" onclick="event.stopPropagation()">
        <h3>新建分类</h3><div class="sub">给分类起一个名字</div>
        <input class="dialog-input" id="catName" placeholder="请输入分类名称">
        <div class="dialog-actions"><button id="catCancel">关闭</button><button id="catOk" class="primary ready">创建</button></div></div>`, { center: true });
      $("#catCancel").addEventListener("click", () => pickCategorySheet());
      $("#catOk").addEventListener("click", () => {
        const name = $("#catName").value.trim(); if (!name) return toast("请输入名称");
        const c = { id: "cat_" + Date.now(), name }; S.categories.push(c); a.categoryId = c.id; save(); renderAdd();
      });
    });
    overlayEl.querySelectorAll("[data-cat]").forEach((el) => el.addEventListener("click", () => { a.categoryId = el.dataset.cat; closeOverlay(); renderAdd(); }));
  }
  function pickTagsSheet() {
    const a = ui.add; a.tagIds = a.tagIds || [];
    const body = S.tags.length
      ? S.tags.map((t) => `<div class="row click" data-tag="${t.id}" style="border-radius:10px"><img class="ico" src="${IMG}common/icon_common_remarks_normal.svg">${escapeHtml(t.name)}<span class="rt">${a.tagIds.includes(t.id) ? "✓" : ""}</span></div>`).join("")
      : emptySheet("home/png/icon_home_defaul_nofile.png", "空空如也", "点击添加标签");
    showOverlay(sheetShell("选择标签", body, { right: `<button class="text-btn" id="tagNew" style="color:var(--ink)">新建</button>` }));
    bindSheet();
    $("#tagNew").addEventListener("click", () => {
      showOverlay(`<div class="center-card" onclick="event.stopPropagation()">
        <h3>新建标签</h3><div class="sub">给标签起一个名字</div>
        <input class="dialog-input" id="tagName" placeholder="请输入标签名称">
        <div class="dialog-actions"><button id="tagCancel">关闭</button><button id="tagOk" class="primary ready">创建</button></div></div>`, { center: true });
      $("#tagCancel").addEventListener("click", () => pickTagsSheet());
      $("#tagOk").addEventListener("click", () => {
        const name = $("#tagName").value.trim(); if (!name) return toast("请输入名称");
        const t = { id: "tag_" + Date.now(), name }; S.tags.push(t); a.tagIds.push(t.id); save(); renderAdd();
      });
    });
    overlayEl.querySelectorAll("[data-tag]").forEach((el) => el.addEventListener("click", () => {
      const id = el.dataset.tag;
      a.tagIds = a.tagIds.includes(id) ? a.tagIds.filter((x) => x !== id) : [...a.tagIds, id];
      closeOverlay(); renderAdd();
    }));
  }

  /* ---------- 详情页 ---------- */
  function openDetail(id) {
    ui.detailId = id;
    renderDetail();
  }
  function renderDetail() {
    tabbarEl.className = "tabbar hidden";
    const a = S.assets.find((x) => x.id === ui.detailId);
    if (!a) return go("home");
    const days = assetAge(a);
    const cat = S.categories.find((c) => c.id === a.categoryId)?.name || "未分类";
    const tags = S.tags.filter((t) => (a.tagIds || []).includes(t.id)).map((t) => t.name);
    // 日均成本曲线：随天数递减
    const len = Math.min(30, Math.max(days, 2));
    const series = Array.from({ length: len }, (_, i) => (a.rule === "day" ? (a.price || 0) / Math.max(1, days - (len - 1) + i) : dailyCost(a)));
    const W = 320, H = 120, pad = 6, maxV = Math.max(...series, 1);
    const x0 = pad, x1 = W - 34;
    const xy = series.map((v, i) => [x0 + (i * (x1 - x0)) / (series.length - 1), H - pad - (v / maxV) * (H - pad * 2)]);
    const path = xy.map((p, i) => `${i ? "L" : "M"}${p[0].toFixed(1)},${p[1].toFixed(1)}`).join(" ");
    const area = `${path} L${x1},${H - pad} L${x0},${H - pad} Z`;

    screenEl.innerHTML = `
      <div class="subpage">
        <div class="subhead">
          <button class="round" id="detBack">${icon("common/icon_common_close.svg")}</button>
          <span style="width:34px"></span>
          <button class="round" id="detEdit">${icon("common/icon_common_edit.svg")}</button>
        </div>
        <div class="subbody">
          <div class="detail-top">
            <div class="ico"><img src="${iconSrc(a)}" alt=""></div>
            <h2>${escapeHtml(a.name)}</h2>
            <div class="d">${sym()}${fmtMoney(dailyCost(a))}<small>/天</small></div>
            <div class="meta"><span>总价：${money(a.price)}</span><span>${a.status === "serving" ? "已服役" : statusText(a.status)} ${days} 天</span></div>
          </div>
          <div class="detail-chart">
            <div class="t"><span>日均成本</span></div>
            <svg class="chart" viewBox="0 0 ${W} ${H}" style="margin-top:2px">
              <defs><linearGradient id="gd" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#c9f44f" stop-opacity=".5"/><stop offset="1" stop-color="#c9f44f" stop-opacity="0"/></linearGradient></defs>
              <path d="${area}" fill="url(#gd)"/><path d="${path}" fill="none" stroke="#b6ee25" stroke-width="2"/>
              <circle cx="${xy[xy.length - 1][0]}" cy="${xy[xy.length - 1][1]}" r="3.5" fill="#fff" stroke="#b6ee25" stroke-width="2"/>
              <text class="axis" x="${W - 2}" y="${pad + 8}" text-anchor="end">${fmtMoney(maxV, 0)}</text>
              <text class="axis" x="${W - 2}" y="${H / 2 + 3}" text-anchor="end">${fmtMoney(maxV / 2, 0)}</text>
              <text class="axis" x="${W - 2}" y="${H - pad - 1}" text-anchor="end">0</text>
              <text class="axis" x="${pad}" y="${H - 1}">${a.buyDate?.slice(5).replace("-", ".") || ""}</text>
              <text class="axis" x="${x1}" y="${H - 1}" text-anchor="end">今天</text>
            </svg>
          </div>
          <div class="detail-rows">
            <div class="drow"><span class="k">单价</span><span class="v">${money(a.price)}</span></div>
            <div class="drow"><span class="k">类别</span><span class="v">${escapeHtml(cat)}</span></div>
            ${tags.length ? `<div class="drow"><span class="k">标签</span><span class="v">${escapeHtml(tags.join("、"))}</span></div>` : ""}
            <div class="drow"><span class="k">购买日期</span><span class="v">${a.buyDate}</span></div>
            <div class="drow"><span class="k">状态</span><span class="v">${statusText(a.status)}</span></div>
          </div>
          <button class="delete-btn" id="detDel">${icon("common/icon_common_delete.svg")}删除</button>
        </div>
      </div>`;
    $("#detBack").addEventListener("click", () => go(ui.view === "wishes" ? "wishes" : "home"));
    $("#detEdit").addEventListener("click", () => openAdd(a.id));
    $("#detDel").addEventListener("click", () => {
      showOverlay(`<div class="center-card" onclick="event.stopPropagation()">
        <h3>删除资产</h3><div class="sub">删除后无法恢复，确定删除「${escapeHtml(a.name)}」？</div>
        <div class="dialog-actions"><button id="delNo">取消</button><button id="delYes" class="primary ready" style="background:#ff5a4e;color:#fff">删除</button></div>
      </div>`, { center: true });
      $("#delNo").addEventListener("click", closeOverlay);
      $("#delYes").addEventListener("click", () => { S.assets = S.assets.filter((x) => x.id !== a.id); save(); closeOverlay(); go("home"); toast("已删除"); });
    });
  }

  /* ---------- 主题 ---------- */
  function applyTheme() {
    const t = S.settings.theme;
    const dark = t === "dark" || (t === "system" && window.matchMedia("(prefers-color-scheme: dark)").matches);
    document.documentElement.dataset.theme = dark ? "dark" : "light";
  }

  /* ---------- 启动 ---------- */
  function clock() {
    const d = new Date();
    $("#clock").textContent = `${d.getHours()}:${String(d.getMinutes()).padStart(2, "0")}`;
  }
  applyTheme();
  window.matchMedia("(prefers-color-scheme: dark)").addEventListener("change", applyTheme);
  clock(); setInterval(clock, 30000);

  // 预置演示分类/标签（仅第一次运行，让分类/标签管理非空，体验更完整）
  if (S.categories.length === 0 && S.assets.length === 0 && !localStorage.getItem(STORE_KEY + "_seeded")) {
    S.categories = [{ id: "cat_digital", name: "数码" }, { id: "cat_home", name: "家居" }];
    S.tags = [{ id: "tag_daily", name: "日常" }, { id: "tag_work", name: "工作" }];
    localStorage.setItem(STORE_KEY + "_seeded", "1");
    save();
  }

  const params = new URLSearchParams(location.search);
  if (params.has("app")) document.documentElement.dataset.app = "1";

  // 返回键（APK 内嵌）：返回 true 表示已处理
  window.__xiaomanBack = () => {
    if (overlayEl.classList.contains("active")) { closeOverlay(); return true; }
    if (document.querySelector(".subpage")) {
      go(ui.view === "wishes" ? "wishes" : "home");
      return true;
    }
    if (ui.view !== "home") { go("home"); return true; }
    return false;
  };

  // 预览模式：?demo=1&view=home|wishes|trends|settings|add|detail|empty|onboarding|paywall（仅内存，不写存储）
  let demoView = null;
  if (params.has("demo")) {
    const now = new Date();
    const ago = (d) => new Date(now.getTime() - d * 86400000).toISOString().slice(0, 10);
    S = structuredClone(DEFAULT_STATE);
    S.onboarded = true;
    S.pro = params.get("pro") === "1";
    S.ratingShown = true;
    S.settings.theme = params.get("theme") || "system";
    S.categories = [{ id: "cat_digital", name: "数码" }, { id: "cat_home", name: "家居" }, { id: "cat_fun", name: "娱乐" }];
    S.tags = [{ id: "tag_daily", name: "日常" }, { id: "tag_work", name: "工作" }];
    if (params.get("data") !== "0") {
      S.assets = [
        { id: "a1", name: "MacBook Pro", price: 12999, buyDate: ago(420), kind: "asset", status: "serving", categoryId: "cat_digital", tagIds: ["tag_work"], rule: "day", icon: "emojis/digital/3d%20handphone.png" },
        { id: "a2", name: "iPhone 16 Pro Max", price: 9299, buyDate: ago(90), kind: "asset", status: "serving", categoryId: "cat_digital", tagIds: ["tag_daily"], rule: "day", icon: "emojis/digital/3d%20video.png" },
        { id: "a3", name: "iPad Air 128G", price: 5299, buyDate: ago(700), kind: "asset", status: "retired", categoryId: "cat_home", tagIds: [], rule: "day", icon: "emojis/digital/3d%20printer.png" },
        { id: "a4", name: "旧耳机", price: 799, buyDate: ago(1000), kind: "asset", status: "sold", categoryId: "cat_fun", tagIds: [], rule: "day", soldDate: ago(300), icon: "emojis/digital/3d%20headphone.png" },
        { id: "w1", name: "富士 X100VI", price: 12990, buyDate: ago(10), kind: "wish", status: "serving", categoryId: "cat_digital", tagIds: [], rule: "day", icon: "emojis/digital/Camera%20icon.png" },
        { id: "w2", name: "人体工学椅", price: 3800, buyDate: ago(4), kind: "wish", status: "serving", categoryId: "cat_home", tagIds: [], rule: "day", icon: "icons/app_icon_v3.png" },
      ];
    }
    demoView = params.get("view");
  }

  if (params.has("demo") && demoView === "onboarding") { ui.obPage = 0; applyTheme(); renderOnboarding(); }
  else if (params.has("demo") && demoView) {
    applyTheme();
    if (demoView === "home" || demoView === "empty") {
      if (demoView === "empty") S.assets = [];
      renderHome(); tabbar("home");
    } else if (demoView === "wishes") { renderWishes(); tabbar("wishes"); }
    else if (demoView === "trends") { renderTrends(); tabbar("trends"); }
    else if (demoView === "settings") { renderSettings(); tabbar("settings"); }
    else if (demoView === "add") { ui.view = "home"; tabbar("home"); openAdd(null, "asset"); }
    else if (demoView === "detail") { ui.view = "home"; tabbar("home"); openDetail("a1"); }
    else if (demoView === "paywall") { go("settings"); setTimeout(openPaywall, 30); }
    else { go("home"); }
  }
  else if (!S.onboarded) renderOnboarding();
  else go("home");
})();
