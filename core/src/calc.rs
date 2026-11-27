//! 资产计算：天数、净成本、日均成本、汇总、趋势序列与分布。
use chrono::{DateTime, Duration, NaiveDate, Utc};
use serde_json::{json, Value};

fn parse_date(s: &str) -> Option<NaiveDate> {
    NaiveDate::parse_from_str(s.trim(), "%Y-%m-%d").ok()
}

/// 本地日期（`YYYY-MM-DD`）。`tz_offset_ms` 为本地时区相对 UTC 的偏移。
pub fn today_string(now_ms: i64, tz_offset_ms: i64) -> String {
    DateTime::<Utc>::from_timestamp_millis(now_ms.saturating_add(tz_offset_ms))
        .map(|d| d.format("%Y-%m-%d").to_string())
        .unwrap_or_else(|| "1970-01-01".to_string())
}

fn date_ms(s: &str, tz_offset_ms: i64) -> Option<i64> {
    parse_date(s).map(|d| d.and_hms_opt(0, 0, 0).unwrap().and_utc().timestamp_millis() - tz_offset_ms)
}

pub fn days_between(from: &str, to: &str) -> i32 {
    match (parse_date(from), parse_date(to)) {
        (Some(a), Some(b)) => {
            let diff = (b - a).num_days();
            (diff as f64).floor() as i32 + 1
        }
        _ => 1,
    }
}

fn opt_str<'a>(v: &'a Value, key: &str) -> &'a str {
    v.get(key).and_then(|x| x.as_str()).unwrap_or("")
}

fn opt_bool(v: &Value, key: &str, def: bool) -> bool {
    v.get(key).and_then(|x| x.as_bool()).unwrap_or(def)
}

fn opt_f64(v: &Value, key: &str, def: f64) -> f64 {
    match v.get(key) {
        Some(Value::Number(n)) => n.as_f64().unwrap_or(def),
        Some(Value::String(s)) => s.trim().parse().unwrap_or(def),
        Some(Value::Bool(b)) => {
            if *b {
                1.0
            } else {
                0.0
            }
        }
        _ => def,
    }
}

pub fn age_days(asset: &Value, today: &str) -> i32 {
    let sold = opt_str(asset, "soldDate");
    if opt_str(asset, "status") == "sold" && !sold.is_empty() {
        return std::cmp::max(1, days_between(opt_str(asset, "buyDate"), sold));
    }
    std::cmp::max(1, days_between(opt_str(asset, "buyDate"), today))
}

pub fn usage_count(asset: &Value) -> i32 {
    if let Some(list) = asset.get("usages").and_then(|v| v.as_array()) {
        if !list.is_empty() {
            return list.len() as i32;
        }
    }
    std::cmp::max(1, opt_f64(asset, "uses", 1.0) as i32)
}

pub fn net_cost(asset: &Value) -> f64 {
    let price = opt_f64(asset, "price", 0.0);
    let residual = if opt_str(asset, "status") == "sold" {
        opt_f64(asset, "soldPrice", 0.0)
    } else if opt_bool(asset, "secondhandInCost", true) {
        opt_f64(asset, "secondhandValue", 0.0)
    } else {
        0.0
    };
    (price - residual.max(0.0)).max(0.0)
}

pub fn daily_cost(asset: &Value, now_ms: i64, tz_offset_ms: i64) -> f64 {
    let price = net_cost(asset);
    let rule = opt_str(asset, "rule");
    if rule == "count" {
        return price / usage_count(asset) as f64;
    }
    if rule == "custom" {
        return price / opt_f64(asset, "customDays", 1.0).max(1.0);
    }
    let buy = date_ms(opt_str(asset, "buyDate"), tz_offset_ms);
    let mut end = now_ms;
    let sold = opt_str(asset, "soldDate");
    if opt_str(asset, "status") == "sold" && !sold.is_empty() {
        if let Some(s) = date_ms(sold, tz_offset_ms) {
            end = s;
        }
    }
    let start = buy.unwrap_or(end);
    let days = (end - start) as f64 / 86_400_000.0;
    price / days.max(1e-6)
}

fn is_kind(asset: &Value, kind: &str) -> bool {
    let k = asset.get("kind").and_then(|v| v.as_str()).unwrap_or("asset");
    k == kind
}

pub fn total(assets: &Value, kind: &str) -> f64 {
    let mut t = 0.0;
    if let Some(arr) = assets.as_array() {
        for a in arr {
            if !is_kind(a, kind) {
                continue;
            }
            if opt_str(a, "status") == "sold" || opt_bool(a, "excludeTotal", false) {
                continue;
            }
            t += opt_f64(a, "price", 0.0);
        }
    }
    t
}

pub fn daily(assets: &Value, kind: &str, now_ms: i64, tz_offset_ms: i64) -> f64 {
    let mut t = 0.0;
    if let Some(arr) = assets.as_array() {
        for a in arr {
            if !is_kind(a, kind) {
                continue;
            }
            let status = a.get("status").and_then(|v| v.as_str()).unwrap_or("serving");
            if status == "serving" {
                t += daily_cost(a, now_ms, tz_offset_ms);
            }
        }
    }
    t
}

pub fn count(assets: &Value, kind: &str, status: &str) -> i32 {
    let mut n = 0;
    if let Some(arr) = assets.as_array() {
        for a in arr {
            if !is_kind(a, kind) {
                continue;
            }
            let s = a.get("status").and_then(|v| v.as_str()).unwrap_or("serving");
            if s == status {
                n += 1;
            }
        }
    }
    n
}

/// 最近 `days` 天的资产总值序列（含今天，末尾为今天）。
///
/// 与 Android / Web 旧实现一致：买入日 ≤ 当天，且未卖出或卖出日 ≥ 当天时计入原价。
pub fn trend_series(assets: &Value, days: i32, now_ms: i64, tz_offset_ms: i64) -> Vec<f64> {
    let count = days.clamp(1, 3650) as i64;
    let today = today_string(now_ms, tz_offset_ms);
    let Some(end) = parse_date(&today) else {
        return vec![0.0; count as usize];
    };
    let start = end - Duration::days(count - 1);
    let list: Vec<&Value> = assets
        .as_array()
        .map(|a| a.iter().collect())
        .unwrap_or_default();
    (0..count)
        .map(|i| {
            let date = (start + Duration::days(i)).format("%Y-%m-%d").to_string();
            let mut sum = 0.0;
            for a in &list {
                if !is_kind(a, "asset") {
                    continue;
                }
                let buy = opt_str(a, "buyDate");
                let sold = opt_str(a, "soldDate");
                if buy <= date.as_str() && (sold.is_empty() || sold >= date.as_str()) {
                    sum += opt_f64(a, "price", 0.0);
                }
            }
            sum
        })
        .collect()
}

/// 按分类统计资产件数，按件数倒序；无分类的资产归入「未分类」。
pub fn category_distribution(assets: &Value, categories: &Value) -> Value {
    let cats: Vec<&Value> = categories
        .as_array()
        .map(|a| a.iter().collect())
        .unwrap_or_default();
    let mut list: Vec<(String, i32)> = Vec::new();
    if let Some(arr) = assets.as_array() {
        for a in arr {
            // 与趋势/汇总一致：只统计资产，不含心愿。
            if !is_kind(a, "asset") {
                continue;
            }
            let id = opt_str(a, "categoryId");
            let name = cats
                .iter()
                .find(|c| opt_str(c, "id") == id)
                .map(|c| opt_str(c, "name").to_string())
                .filter(|s| !s.is_empty())
                .unwrap_or_else(|| "未分类".to_string());
            match list.iter_mut().find(|(n, _)| n == &name) {
                Some(entry) => entry.1 += 1,
                None => list.push((name, 1)),
            }
        }
    }
    // 稳定排序：件数相同的分类保持首次出现顺序。
    list.sort_by(|a, b| b.1.cmp(&a.1));
    Value::Array(
        list.into_iter()
            .map(|(name, count)| json!({ "name": name, "count": count }))
            .collect(),
    )
}

/// 某件资产的累计收入（`incomeRecords` 求和）。
pub fn income_total(asset: &Value) -> f64 {
    asset
        .get("incomeRecords")
        .and_then(Value::as_array)
        .map(|list| list.iter().map(|r| opt_f64(r, "amount", 0.0)).sum())
        .unwrap_or(0.0)
}

/// 最近一条价格记录（按日期最大；同日期取先出现的一条）。
pub fn latest_price(asset: &Value) -> Value {
    let Some(list) = asset.get("priceRecords").and_then(Value::as_array) else {
        return Value::Null;
    };
    let mut best: Option<&Value> = None;
    for r in list {
        let date = opt_str(r, "date");
        match best {
            None => best = Some(r),
            Some(b) if date > opt_str(b, "date") => best = Some(r),
            _ => {}
        }
    }
    best.cloned().unwrap_or(Value::Null)
}

#[cfg(test)]
mod tests {
    use super::*;

    fn asset(id: &str, price: f64, buy: &str, sold: &str) -> Value {
        json!({
            "id": id,
            "kind": "asset",
            "price": price,
            "buyDate": buy,
            "soldDate": sold,
        })
    }

    #[test]
    fn today_uses_offset() {
        // 2026-11-27T18:00:00Z
        let now = 1_795_802_400_000_i64;
        assert_eq!(today_string(now, 0), "2026-11-27");
        // 东八区已到次日 02:00
        assert_eq!(today_string(now, 8 * 3_600_000), "2026-11-28");
        // 西八区仍是 10:00
        assert_eq!(today_string(now, -8 * 3_600_000), "2026-11-27");
    }

    #[test]
    fn trend_counts_buy_and_sold_days() {
        let assets = json!([
            asset("a", 100.0, "2026-11-25", ""),
            asset("b", 50.0, "2026-11-26", "2026-11-26"),
            json!({ "id": "w", "kind": "wish", "price": 999.0, "buyDate": "2026-11-20" }),
        ]);
        let now = 1_795_802_400_000_i64; // 2026-11-27 18:00 UTC
        let v = trend_series(&assets, 3, now, 0);
        assert_eq!(v, vec![100.0, 150.0, 100.0]);
    }

    #[test]
    fn trend_ignores_future_buy_date() {
        let assets = json!([{ "kind": "asset", "price": 10.0, "buyDate": "9999-01-01" }]);
        let v = trend_series(&assets, 2, 1_795_802_400_000, 0);
        assert_eq!(v.len(), 2);
        assert!(v.iter().all(|x| *x == 0.0));
    }

    #[test]
    fn distribution_sorted_desc_and_fallback() {
        let assets = json!([
            { "categoryId": "c1" },
            { "categoryId": "c1" },
            { "categoryId": "c2" },
            { "categoryId": "missing" },
            { "categoryId": "c2" },
            { "categoryId": "c2" },
            { "kind": "wish", "categoryId": "c1" },
        ]);
        let cats = json!([
            { "id": "c1", "name": "数码" },
            { "id": "c2", "name": "家电" },
        ]);
        let v = category_distribution(&assets, &cats);
        assert_eq!(v[0]["name"], "家电");
        assert_eq!(v[0]["count"], 3);
        assert_eq!(v[1]["name"], "数码");
        assert_eq!(v[2]["name"], "未分类");
    }

    #[test]
    fn income_and_latest_price() {
        let a = json!({
            "incomeRecords": [{ "amount": 10 }, { "amount": "5.5" }],
            "priceRecords": [
                { "id": "p1", "date": "2026-01-02", "price": 90 },
                { "id": "p2", "date": "2026-01-05", "price": 80 },
                { "id": "p3", "date": "2026-01-01", "price": 100 },
            ],
        });
        assert!((income_total(&a) - 15.5).abs() < 1e-9);
        assert_eq!(latest_price(&a)["id"], "p2");
        assert!(latest_price(&json!({})).is_null());
    }
}
