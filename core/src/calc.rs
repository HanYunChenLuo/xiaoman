//! 资产计算：天数、净成本、日均成本、汇总。
use chrono::NaiveDate;
use serde_json::Value;

fn parse_date(s: &str) -> Option<NaiveDate> {
    NaiveDate::parse_from_str(s, "%Y-%m-%d").ok()
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
    v.get(key).and_then(|x| x.as_f64()).unwrap_or(def)
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
