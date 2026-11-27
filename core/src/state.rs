//! 备份语义层：官方 `.ias` 的 `data.json` 与各端本地状态之间的映射、合并策略。
//!
//! `backup.rs` 只负责 zip/AES 容器；这里负责字段语义，供 Android / Web
//! （桌面复用 Web）共用，避免两端各写一遍导入导出映射。
//!
//! 规范状态（canonical state）使用 camelCase，图片只保存备份内的名称引用
//! （`coverImage` / `noteImages`），由平台负责名称 ↔ 本地文件 / data URL。
//! 时间一律由调用方显式传入 `now_ms` 与 `tz_offset_ms`，核心内不取系统时钟。
//! 单次调用使用同一个时区偏移；跨夏令时的历史日期可能与平台逐条解析相差一天。

use std::collections::HashMap;

use chrono::{Datelike, Duration, NaiveDate, Timelike};
use serde_json::{json, Value};

/// 导出时写入备份的版本，与旧实现保持一致。
const BACKUP_VERSION: &str = "1.0.0";

// ---------------- 通用取值 ----------------

fn opt_str<'a>(v: &'a Value, key: &str) -> Option<&'a str> {
    v.get(key).and_then(Value::as_str)
}

fn str_of(v: &Value, key: &str) -> String {
    opt_str(v, key).unwrap_or("").to_string()
}

fn str_any(v: &Value, keys: &[&str]) -> String {
    for key in keys {
        if let Some(s) = opt_str(v, key) {
            if !s.is_empty() {
                return s.to_string();
            }
        }
    }
    String::new()
}

fn opt_i64(v: &Value, key: &str) -> Option<i64> {
    v.get(key).and_then(|x| match x {
        Value::Number(n) => n.as_i64().or_else(|| n.as_f64().map(|f| f as i64)),
        Value::String(s) => s
            .trim()
            .parse::<i64>()
            .ok()
            .or_else(|| s.trim().parse::<f64>().ok().map(|f| f as i64)),
        Value::Bool(b) => Some(if *b { 1 } else { 0 }),
        _ => None,
    })
}

fn i64_of(v: &Value, key: &str) -> i64 {
    opt_i64(v, key).unwrap_or(0)
}

fn opt_f64(v: &Value, key: &str) -> Option<f64> {
    v.get(key).and_then(|x| match x {
        Value::Number(n) => n.as_f64(),
        Value::String(s) => s.trim().parse::<f64>().ok(),
        Value::Bool(b) => Some(if *b { 1.0 } else { 0.0 }),
        _ => None,
    })
}

fn f64_of(v: &Value, key: &str) -> f64 {
    opt_f64(v, key).unwrap_or(0.0)
}

fn opt_bool(v: &Value, key: &str) -> Option<bool> {
    v.get(key).and_then(|x| match x {
        Value::Bool(b) => Some(*b),
        Value::Number(n) => n
            .as_i64()
            .map(|i| i != 0)
            .or_else(|| n.as_f64().map(|f| f != 0.0)),
        Value::String(s) => match s.trim().to_ascii_lowercase().as_str() {
            "true" | "1" | "yes" | "y" => Some(true),
            "false" | "0" | "no" | "n" | "" => Some(false),
            _ => None,
        },
        _ => None,
    })
}

fn bool_of(v: &Value, key: &str, default: bool) -> bool {
    opt_bool(v, key).unwrap_or(default)
}

fn arr<'a>(v: &'a Value, key: &str) -> &'a [Value] {
    v.get(key)
        .and_then(Value::as_array)
        .map(Vec::as_slice)
        .unwrap_or(&[])
}

fn type_is_cover(v: &Value) -> bool {
    if bool_of(v, "is_cover", false) {
        return true;
    }
    match v.get("type") {
        Some(Value::String(s)) => s == "cover" || s == "0",
        Some(Value::Number(n)) => n.as_i64() == Some(0),
        _ => false,
    }
}

// ---------------- 日期 ----------------

fn parse_date(s: &str) -> Option<NaiveDate> {
    NaiveDate::parse_from_str(s.trim(), "%Y-%m-%d").ok()
}

/// `YYYY-MM-DD`（本地日期）→ Unix 秒。`tz_offset_ms` 为本地时区相对 UTC 的偏移。
fn epoch_of(date: &str, tz_offset_ms: i64) -> i64 {
    match parse_date(date) {
        Some(d) => {
            let ms = d
                .and_hms_opt(0, 0, 0)
                .expect("midnight")
                .and_utc()
                .timestamp_millis()
                - tz_offset_ms;
            ms.div_euclid(1000)
        }
        None => 0,
    }
}

fn local_datetime(ms: i64, tz_offset_ms: i64) -> chrono::NaiveDateTime {
    let base = NaiveDate::from_ymd_opt(1970, 1, 1)
        .expect("epoch")
        .and_hms_opt(0, 0, 0)
        .expect("midnight");
    base + Duration::milliseconds(ms + tz_offset_ms)
}

fn date_of(sec: i64, tz_offset_ms: i64, today: &str) -> String {
    if sec <= 0 {
        return today.to_string();
    }
    let dt = local_datetime(sec * 1000, tz_offset_ms);
    format!("{:04}-{:02}-{:02}", dt.year(), dt.month(), dt.day())
}

fn today_of(now_ms: i64, tz_offset_ms: i64) -> String {
    let sec = now_ms.div_euclid(1000).max(1);
    date_of(sec, tz_offset_ms, "1970-01-01")
}

/// 记录日期：兼容 epoch 秒与 `YYYY-MM-DD` 字符串；缺失时回退今天。
fn record_date(v: &Value, keys: &[&str], tz_offset_ms: i64, today: &str) -> String {
    for key in keys {
        if let Some(x) = v.get(key) {
            if let Some(n) = opt_i64(v, key) {
                if n > 0 {
                    return date_of(n, tz_offset_ms, today);
                }
            }
            if let Some(s) = x.as_str() {
                let s = s.trim();
                if !s.is_empty() {
                    return s.to_string();
                }
            }
        }
    }
    today.to_string()
}

// ---------------- data.json -> 规范状态 ----------------

/// 解析 `backup::decode_ias` 的返回值 `{data, images}`。
/// 输出 `{state, meta}`：`state` 是规范状态，`meta` 含条数/备份时间。
pub fn from_backup(payload: &Value, now_ms: i64, tz_offset_ms: i64) -> Result<Value, String> {
    let data = payload
        .get("data")
        .ok_or_else(|| "data.json not found".to_string())?;
    let today = today_of(now_ms, tz_offset_ms);

    // 分类 / 标签 / 收入分类
    let mut categories = Vec::new();
    for c in arr(data, "categories") {
        if bool_of(c, "is_deleted", false) {
            continue;
        }
        categories.push(json!({ "id": str_of(c, "id"), "name": str_of(c, "title") }));
    }
    let mut tags = Vec::new();
    for t in arr(data, "tags") {
        if bool_of(t, "is_deleted", false) {
            continue;
        }
        tags.push(json!({ "id": str_of(t, "id"), "name": str_of(t, "title") }));
    }
    let mut income_categories = Vec::new();
    for c in arr(data, "income_categories") {
        if bool_of(c, "is_deleted", false) {
            continue;
        }
        let name = str_of(c, "title");
        if name.is_empty() {
            continue;
        }
        income_categories.push(json!({ "id": str_of(c, "id"), "name": name }));
    }

    // 图片记录：asset_id -> 封面名 / 备注名列表
    let mut cover_by_asset: HashMap<String, String> = HashMap::new();
    let mut notes_by_asset: HashMap<String, Vec<String>> = HashMap::new();
    for r in arr(data, "asset_images") {
        if bool_of(r, "is_deleted", false) {
            continue;
        }
        let asset_id = str_any(r, &["asset_id", "assetId"]);
        if asset_id.is_empty() {
            continue;
        }
        let name = str_any(r, &["image_name", "file_name", "name"]);
        if name.is_empty() {
            continue;
        }
        if type_is_cover(r) {
            cover_by_asset.entry(asset_id).or_insert(name);
        } else {
            notes_by_asset.entry(asset_id).or_default().push(name);
        }
    }

    // 收入 / 使用 / 价格记录：asset_id -> 列表
    let mut income_by_asset: HashMap<String, Vec<Value>> = HashMap::new();
    for r in arr(data, "income_records") {
        if bool_of(r, "is_deleted", false) {
            continue;
        }
        let asset_id = str_any(r, &["asset_id", "assetId"]);
        if asset_id.is_empty() {
            continue;
        }
        income_by_asset.entry(asset_id).or_default().push(json!({
            "id": str_of(r, "id"),
            "amount": f64_of(r, "amount"),
            "date": record_date(r, &["income_date", "date"], tz_offset_ms, &today),
            "categoryId": str_any(r, &["category_id", "categoryId"]),
            "note": str_of(r, "note"),
        }));
    }
    let mut usage_by_asset: HashMap<String, Vec<Value>> = HashMap::new();
    for r in arr(data, "usages") {
        if bool_of(r, "is_deleted", false) {
            continue;
        }
        let asset_id = str_any(r, &["asset_id", "assetId"]);
        if asset_id.is_empty() {
            continue;
        }
        usage_by_asset.entry(asset_id).or_default().push(json!({
            "id": str_of(r, "id"),
            "date": record_date(r, &["usage_date", "date"], tz_offset_ms, &today),
            "note": str_of(r, "note"),
        }));
    }
    let mut price_by_asset: HashMap<String, Vec<Value>> = HashMap::new();
    for r in arr(data, "asset_price_records") {
        if bool_of(r, "is_deleted", false) {
            continue;
        }
        let asset_id = str_any(r, &["asset_id", "assetId"]);
        if asset_id.is_empty() {
            continue;
        }
        price_by_asset.entry(asset_id).or_default().push(json!({
            "id": str_of(r, "id"),
            "date": record_date(r, &["record_date", "date", "created_at"], tz_offset_ms, &today),
            "price": f64_of(r, "price"),
        }));
    }

    // 到期提醒：asset_id -> 原始记录
    let mut expire_by_asset: HashMap<String, Value> = HashMap::new();
    for r in arr(data, "expire_reminders") {
        if bool_of(r, "is_deleted", false) {
            continue;
        }
        let mut asset_id = str_any(r, &["asset_id", "assetId"]);
        if asset_id.is_empty() {
            asset_id = str_of(r, "id");
        }
        if asset_id.is_empty() {
            continue;
        }
        expire_by_asset.entry(asset_id).or_insert_with(|| r.clone());
    }

    let mut assets = Vec::new();
    for a in arr(data, "assets") {
        if bool_of(a, "is_deleted", false) {
            continue;
        }
        let id = str_of(a, "id");
        let is_sell = bool_of(a, "is_sell", false);
        let is_retirement = bool_of(a, "is_retirement", false);
        let status = if is_sell {
            "sold"
        } else if is_retirement {
            "retired"
        } else {
            "serving"
        };
        let mut price = f64_of(a, "price");
        if price == 0.0 {
            price = f64_of(a, "target_cost");
        }

        let mut cover = str_of(a, "cover_image");
        if cover.is_empty() {
            cover = cover_by_asset.get(&id).cloned().unwrap_or_default();
        }
        let mut notes: Vec<String> = arr(a, "note_images")
            .iter()
            .filter_map(|v| v.as_str())
            .filter(|s| !s.is_empty())
            .map(str::to_string)
            .collect();
        if let Some(extra) = notes_by_asset.get(&id) {
            for name in extra {
                if !notes.contains(name) {
                    notes.push(name.clone());
                }
            }
        }

        let target_date = i64_of(a, "target_date");
        let mut item = json!({
            "id": id,
            "name": str_of(a, "name"),
            "model": str_of(a, "model"),
            "price": price,
            "buyDate": date_of(i64_of(a, "buy_date"), tz_offset_ms, &today),
            "kind": if i64_of(a, "type") == 1 { "wish" } else { "asset" },
            "status": status,
            "rule": "day",
            "categoryId": str_of(a, "category_id"),
            "tagIds": a
                .get("tag_ids")
                .and_then(Value::as_array)
                .cloned()
                .unwrap_or_default(),
            "note": str_of(a, "note"),
            "soldPrice": f64_of(a, "used_price"),
            "secondhandValue": f64_of(a, "secondhand_value"),
            "secondhandInCost": opt_bool(a, "secondhand_in_cost").unwrap_or(true),
            "excludeTotal": bool_of(a, "is_not_include_total", false)
                || bool_of(a, "is_excluded_from_total", false),
            "brandId": str_of(a, "brand_id"),
            "brandName": str_of(a, "brand_name"),
            "brandAuto": false,
            "favorite": bool_of(a, "is_top", false),
            "targetCost": f64_of(a, "target_cost"),
            "targetDate": if target_date > 0 {
                date_of(target_date, tz_offset_ms, &today)
            } else {
                String::new()
            },
        });

        let o = item.as_object_mut().expect("object");
        if is_sell {
            o.insert(
                "soldDate".into(),
                json!(date_of(i64_of(a, "sell_date"), tz_offset_ms, &today)),
            );
        }
        if is_retirement && i64_of(a, "retirement_date") > 0 {
            o.insert(
                "retireDate".into(),
                json!(date_of(i64_of(a, "retirement_date"), tz_offset_ms, &today)),
            );
        }
        if i64_of(a, "target_usage_days") > 0 {
            o.insert(
                "targetUsageDays".into(),
                json!(i64_of(a, "target_usage_days")),
            );
        }
        if !cover.is_empty() {
            o.insert("coverImage".into(), json!(cover));
        }
        if !notes.is_empty() {
            o.insert("noteImages".into(), json!(notes));
        }
        if let Some(list) = income_by_asset.get(&id) {
            o.insert("incomeRecords".into(), json!(list));
        }
        if let Some(list) = price_by_asset.get(&id) {
            o.insert("priceRecords".into(), json!(list));
        }
        if let Some(list) = usage_by_asset.get(&id) {
            o.insert("usages".into(), json!(list));
        }
        if let Some(r) = expire_by_asset.get(&id) {
            let expiry = i64_of(r, "expiry_at");
            // 空提醒记录（expiry 为 0）不落任何到期字段，保证导入导出往返稳定。
            if expiry > 0 {
                o.insert(
                    "expireAt".into(),
                    json!(date_of(expiry, tz_offset_ms, &today)),
                );
                let days = i64_of(r, "expiry_remind");
                if days > 0 {
                    o.insert("remindDays".into(), json!(days));
                }
                let unit = str_of(r, "remind_time_unit");
                o.insert(
                    "remindUnit".into(),
                    json!(if unit.is_empty() {
                        "day"
                    } else {
                        unit.as_str()
                    }),
                );
                let remind_at = i64_of(r, "remind_at");
                if remind_at > 0 {
                    let dt = local_datetime(remind_at * 1000, tz_offset_ms);
                    o.insert("remindHour".into(), json!(dt.hour()));
                    o.insert("remindMinute".into(), json!(dt.minute()));
                }
            }
        }

        assets.push(item);
    }

    let save_money = data
        .get("save_money")
        .and_then(Value::as_array)
        .cloned()
        .unwrap_or_default();
    let updated_at = data
        .get("settings")
        .map(|s| i64_of(s, "updatedAt"))
        .unwrap_or(0);
    let exported_at = i64_of(data, "exported_at");
    let backup_time_ms = if updated_at > 0 {
        updated_at
    } else {
        exported_at.saturating_mul(1000)
    };

    Ok(json!({
        "state": {
            "assets": assets,
            "categories": categories,
            "tags": tags,
            "incomeCategories": income_categories,
            "saveMoney": save_money,
            "settings": { "updatedAt": updated_at },
        },
        "meta": {
            "count": assets.len(),
            "exportedAt": exported_at,
            "updatedAt": updated_at,
            "backupTimeMs": backup_time_ms,
        },
    }))
}

// ---------------- 规范状态 -> data.json ----------------

/// 生成可直接交给 `backup::encode_ias` 的 `{data, images:[{name,data}]}`。
/// `images` 是 `{备份内名称: base64}`，由平台读取本地文件 / data URL 得到。
pub fn to_payload(
    state: &Value,
    images: &Value,
    platform: &str,
    now_ms: i64,
    tz_offset_ms: i64,
) -> Result<Value, String> {
    let mut categories = Vec::new();
    for (i, c) in arr(state, "categories").iter().enumerate() {
        categories.push(json!({
            "id": str_of(c, "id"),
            "title": str_of(c, "name"),
            "sort": i,
            "is_deleted": false,
            "created_at": 0,
            "update_at": 0,
        }));
    }
    let mut tags = Vec::new();
    for (i, t) in arr(state, "tags").iter().enumerate() {
        tags.push(json!({
            "id": str_of(t, "id"),
            "title": str_of(t, "name"),
            "sort": i,
            "is_deleted": false,
            "created_at": 0,
            "update_at": 0,
        }));
    }
    let mut income_categories = Vec::new();
    for (i, c) in arr(state, "incomeCategories").iter().enumerate() {
        income_categories.push(json!({
            "id": str_of(c, "id"),
            "title": str_of(c, "name"),
            "icon_name": "icon_income_1",
            "color_hex": "#A3E635",
            "sort": i,
            "is_deleted": false,
            "created_at": 0,
            "update_at": 0,
        }));
    }

    let mut assets = Vec::new();
    let mut image_records = Vec::new();
    let mut usages = Vec::new();
    let mut income_records = Vec::new();
    let mut price_records = Vec::new();
    let mut reminders = Vec::new();
    let mut img_id = 0usize;

    for (sort, a) in arr(state, "assets").iter().enumerate() {
        let id = str_of(a, "id");
        let kind = str_of(a, "kind");
        let status = str_of(a, "status");
        let is_sell = status == "sold";
        let is_retirement = status == "retired";

        let cover = str_of(a, "coverImage");
        if !cover.is_empty() {
            image_records.push(json!({
                "id": format!("img_{img_id}"),
                "asset_id": id,
                "image_name": cover,
                "type": "cover",
                "sort": 0,
                "is_deleted": false,
                "created_at": 0,
                "update_at": 0,
            }));
            img_id += 1;
        }
        let note_names: Vec<&str> = arr(a, "noteImages")
            .iter()
            .filter_map(Value::as_str)
            .filter(|s| !s.is_empty())
            .collect();
        for (i, name) in note_names.iter().enumerate() {
            image_records.push(json!({
                "id": format!("img_{img_id}"),
                "asset_id": id,
                "image_name": name,
                "type": "note",
                "sort": i,
                "is_deleted": false,
                "created_at": 0,
                "update_at": 0,
            }));
            img_id += 1;
        }

        let target_date = epoch_of(&str_of(a, "targetDate"), tz_offset_ms);
        let buy_date = epoch_of(&str_of(a, "buyDate"), tz_offset_ms);
        assets.push(json!({
            "id": id,
            "name": str_of(a, "name"),
            "model": str_of(a, "model"),
            "icon_name": "icon_default_object",
            "cover_image": cover,
            "price": f64_of(a, "price"),
            "type": if kind == "wish" { 1 } else { 0 },
            "category_id": str_of(a, "categoryId"),
            "state": 0,
            "buy_date": buy_date,
            "tag_ids": a
                .get("tagIds")
                .and_then(Value::as_array)
                .cloned()
                .unwrap_or_default(),
            "target_type": 0,
            "target_cost_unit": "",
            "target_each_increment": 0,
            "target_date": target_date,
            "target_usage_days": i64_of(a, "targetUsageDays"),
            "target_cost": f64_of(a, "targetCost"),
            "target_value": 0,
            "additional_item_ids": [],
            "additional_calculation_mode": 0,
            "note": str_of(a, "note"),
            "note_images": note_names,
            "is_top": bool_of(a, "favorite", false),
            "is_retirement": is_retirement,
            "retirement_date": epoch_of(&str_of(a, "retireDate"), tz_offset_ms),
            "is_sell": is_sell,
            "used_price": f64_of(a, "soldPrice"),
            "secondhand_value": f64_of(a, "secondhandValue"),
            "secondhand_in_cost": opt_bool(a, "secondhandInCost").unwrap_or(true),
            "brand_id": str_of(a, "brandId"),
            "brand_name": str_of(a, "brandName"),
            "sell_date": epoch_of(&str_of(a, "soldDate"), tz_offset_ms),
            "wish_date": target_date,
            "is_not_include_total": bool_of(a, "excludeTotal", false),
            "is_excluded_from_total": bool_of(a, "excludeTotal", false),
            "sort": sort,
            "is_deleted": false,
            "created_at": 0,
            "update_at": 0,
        }));

        for r in arr(a, "usages") {
            usages.push(json!({
                "id": str_of(r, "id"),
                "asset_id": id,
                "usage_date": epoch_of(&str_of(r, "date"), tz_offset_ms),
                "note": str_of(r, "note"),
                "is_deleted": false,
                "created_at": 0,
                "update_at": 0,
            }));
        }
        for r in arr(a, "incomeRecords") {
            income_records.push(json!({
                "id": str_of(r, "id"),
                "asset_id": id,
                "category_id": str_of(r, "categoryId"),
                "amount": f64_of(r, "amount"),
                "income_date": epoch_of(&str_of(r, "date"), tz_offset_ms),
                "note": str_of(r, "note"),
                "is_deleted": false,
                "created_at": 0,
                "update_at": 0,
            }));
        }
        for r in arr(a, "priceRecords") {
            price_records.push(json!({
                "id": str_of(r, "id"),
                "asset_id": id,
                "price": f64_of(r, "price"),
                "record_date": epoch_of(&str_of(r, "date"), tz_offset_ms),
                "is_deleted": false,
                "created_at": 0,
                "update_at": 0,
            }));
        }

        let expire_at = str_of(a, "expireAt");
        if !expire_at.is_empty() {
            let expiry = epoch_of(&expire_at, tz_offset_ms);
            let days = i64_of(a, "remindDays");
            let unit = str_of(a, "remindUnit");
            let unit_sec = match unit.as_str() {
                "week" => 7 * 86_400,
                "month" => 30 * 86_400,
                _ => 86_400,
            };
            let hour = opt_i64(a, "remindHour").unwrap_or(9);
            let minute = opt_i64(a, "remindMinute").unwrap_or(0);
            let remind_at = expiry - days * unit_sec + hour * 3600 + minute * 60;
            reminders.push(json!({
                "id": id,
                "asset_id": id,
                "expiry_at": expiry,
                "expiry_remind": days,
                "remind_time_unit": if unit.is_empty() { "day" } else { unit.as_str() },
                "remind_at": remind_at,
                "is_deleted": false,
                "created_at": 0,
                "update_at": 0,
            }));
        }
    }

    let mut image_list = Vec::new();
    if let Some(list) = images.as_array() {
        image_list = list.clone();
    } else if let Some(map) = images.as_object() {
        for (name, data) in map {
            if let Some(b64) = data.as_str() {
                image_list.push(json!({ "name": name, "data": b64 }));
            }
        }
    }

    let save_money = state
        .get("saveMoney")
        .and_then(Value::as_array)
        .cloned()
        .unwrap_or_default();
    let updated_at = state
        .get("settings")
        .map(|s| i64_of(s, "updatedAt"))
        .unwrap_or(0);

    Ok(json!({
        "data": {
            "categories": categories,
            "tags": tags,
            "assets": assets,
            "asset_images": image_records,
            "usages": usages,
            "income_categories": income_categories,
            "income_records": income_records,
            "asset_price_records": price_records,
            "save_money": save_money,
            "expire_reminders": reminders,
            "platform": platform,
            "version": BACKUP_VERSION,
            "exported_at": now_ms.div_euclid(1000),
            "settings": { "updatedAt": updated_at },
        },
        "images": image_list,
    }))
}

// ---------------- 合并 ----------------

/// 合并本地状态与导入状态。语义与旧实现一致：
/// - 覆盖：资产/分类/标签用备份替换；收入分类备份为空时保留本地；
/// - 合并：资产按 id 去重，重复条目在 `prefer_incoming` 时取备份；分类/标签只补缺；
/// - 存钱记录：本地为空时采用备份，否则保留本地；
/// - 设置始终保留本地。
pub fn merge(
    local: &Value,
    incoming: &Value,
    replace: bool,
    prefer_incoming: bool,
) -> Result<Value, String> {
    let mut out = local
        .as_object()
        .cloned()
        .ok_or_else(|| "local state must be an object".to_string())?;
    let inc = incoming
        .as_object()
        .ok_or_else(|| "incoming state must be an object".to_string())?;

    if replace {
        out.insert(
            "assets".into(),
            inc.get("assets").cloned().unwrap_or_else(|| json!([])),
        );
        out.insert(
            "categories".into(),
            inc.get("categories").cloned().unwrap_or_else(|| json!([])),
        );
        out.insert(
            "tags".into(),
            inc.get("tags").cloned().unwrap_or_else(|| json!([])),
        );
        let income_categories = inc
            .get("incomeCategories")
            .and_then(Value::as_array)
            .cloned()
            .unwrap_or_default();
        if !income_categories.is_empty() {
            out.insert("incomeCategories".into(), Value::Array(income_categories));
        }
        out.insert(
            "saveMoney".into(),
            inc.get("saveMoney").cloned().unwrap_or_else(|| json!([])),
        );
        return Ok(Value::Object(out));
    }

    // 资产：本地顺序优先，备份中的新条目追加；重复条目按 prefer_incoming 决定。
    let mut local_assets = out
        .get("assets")
        .and_then(Value::as_array)
        .cloned()
        .unwrap_or_default();
    for item in inc
        .get("assets")
        .and_then(Value::as_array)
        .cloned()
        .unwrap_or_default()
    {
        let id = str_of(&item, "id");
        if id.is_empty() {
            local_assets.push(item);
            continue;
        }
        match local_assets.iter().position(|x| str_of(x, "id") == id) {
            Some(pos) => {
                if prefer_incoming {
                    local_assets[pos] = item;
                }
            }
            None => local_assets.push(item),
        }
    }
    out.insert("assets".into(), Value::Array(local_assets));

    // 分类 / 标签 / 收入分类：只补缺，不覆盖本地。
    for key in ["categories", "tags", "incomeCategories"] {
        let mut list = out
            .get(key)
            .and_then(Value::as_array)
            .cloned()
            .unwrap_or_default();
        for item in inc
            .get(key)
            .and_then(Value::as_array)
            .cloned()
            .unwrap_or_default()
        {
            let id = str_of(&item, "id");
            if id.is_empty() || !list.iter().any(|x| str_of(x, "id") == id) {
                list.push(item);
            }
        }
        out.insert(key.into(), Value::Array(list));
    }

    // 存钱记录：本地为空时才采用备份。
    let local_save = out
        .get("saveMoney")
        .and_then(Value::as_array)
        .cloned()
        .unwrap_or_default();
    if local_save.is_empty() {
        if let Some(list) = inc.get("saveMoney").and_then(Value::as_array) {
            if !list.is_empty() {
                out.insert("saveMoney".into(), Value::Array(list.clone()));
            }
        }
    }

    Ok(Value::Object(out))
}

// ---------------- 本地状态规范化 ----------------

/// 规范化本地状态：补齐数组/记录数组、默认收入分类与设置项。
///
/// `defaults` 是各端特有的设置默认值（例如 Android 的 `thousands=true`、
/// `duration`/`view`，Web 的 `notifyExpiry=false`），已有设置优先于默认值；
/// 核心只补跨端一致的键（currency/decimals/theme/updatedAt）。
/// 未知的根字段原样保留，因此 SharedPreferences 里的其他状态不会丢失。
pub fn normalize(state: &Value, defaults: &Value) -> Value {
    let mut out = state.as_object().cloned().unwrap_or_default();

    for key in ["assets", "categories", "tags", "saveMoney"] {
        if !out.get(key).map(Value::is_array).unwrap_or(false) {
            out.insert(key.to_string(), json!([]));
        }
    }

    // 收入分类为空时使用内置默认（与导入/旧端行为一致）。
    let empty_income = out
        .get("incomeCategories")
        .and_then(Value::as_array)
        .map(|a| a.is_empty())
        .unwrap_or(true);
    if empty_income {
        out.insert(
            "incomeCategories".to_string(),
            json!([
                { "id": "inc_cat_0", "name": "出租" },
                { "id": "inc_cat_1", "name": "工资" },
                { "id": "inc_cat_2", "name": "理财" },
                { "id": "inc_cat_3", "name": "其他" },
            ]),
        );
    }

    // 每条资产记录都要有可追加的记录数组。
    if let Some(assets) = out.get_mut("assets").and_then(Value::as_array_mut) {
        for asset in assets {
            if let Some(obj) = asset.as_object_mut() {
                for key in ["incomeRecords", "priceRecords", "usages"] {
                    if !obj.get(key).map(Value::is_array).unwrap_or(false) {
                        obj.insert(key.to_string(), json!([]));
                    }
                }
            }
        }
    }

    // 设置：平台默认值打底，已有值优先，再补跨端一致的键。
    let mut settings = defaults.as_object().cloned().unwrap_or_default();
    if let Some(existing) = out.get("settings").and_then(Value::as_object) {
        for (key, value) in existing {
            settings.insert(key.clone(), value.clone());
        }
    }
    for (key, value) in [
        ("currency", json!("CNY")),
        ("decimals", json!(2)),
        ("theme", json!("system")),
        ("updatedAt", json!(0)),
    ] {
        settings.entry(key.to_string()).or_insert(value);
    }
    out.insert("settings".to_string(), Value::Object(settings));

    Value::Object(out)
}

/// 备份本身的数据修改时间（毫秒）：优先 `settings.updatedAt`，旧备份回退 `exported_at`。
pub fn backup_time_ms(payload: &Value) -> i64 {
    let updated = payload
        .get("data")
        .and_then(|d| d.get("settings"))
        .map(|s| i64_of(s, "updatedAt"))
        .unwrap_or(0);
    if updated > 0 {
        return updated;
    }
    payload
        .get("data")
        .map(|d| i64_of(d, "exported_at").saturating_mul(1000))
        .unwrap_or(0)
}

#[cfg(test)]
mod tests {
    use super::*;

    fn sample_payload() -> Value {
        json!({
            "data": {
                "categories": [{"id":"cat_1","title":"数码"}],
                "tags": [{"id":"tag_1","title":"常用"}],
                "income_categories": [{"id":"inc_1","title":"出租"}],
                "assets": [{
                    "id":"a_1","name":"相机","model":"Canon R5","price":20000,
                    "type": 0, "buy_date": 1704067200,
                    "category_id":"cat_1","tag_ids":["tag_1"],
                    "is_top": true, "note":"备注",
                    "secondhand_value": 12000, "secondhand_in_cost": true,
                    "target_usage_days": 30,
                    "cover_image":"images/a_1_cover.jpg",
                    "note_images":["images/a_1_note0.jpg"]
                }, {
                    "id":"a_2","name":"旧镜头","model":"EF 50","price":3000,
                    "type": 0, "buy_date": 1704067200, "is_sell": true,
                    "sell_date": 1706745600, "used_price": 2500
                }],
                "asset_images": [],
                "usages": [{"id":"u_1","asset_id":"a_1","usage_date":1704153600,"note":"拍了一次"}],
                "income_records": [{"id":"i_1","asset_id":"a_1","category_id":"inc_1","amount":500,"income_date":1704153600}],
                "asset_price_records": [{"id":"p_1","asset_id":"a_1","price":19000,"record_date":1704153600}],
                "expire_reminders": [{"id":"a_1","asset_id":"a_1","expiry_at":1706745600,"expiry_remind":3,"remind_time_unit":"day","remind_at":1706486400}],
                "save_money": [{"id":"sm_1","amount":100}],
                "settings": {"updatedAt": 1700000000000i64},
                "exported_at": 1700000000
            },
            "images": {"images/a_1_cover.jpg":"YQ=="}
        })
    }

    #[test]
    fn maps_backup_to_state() {
        let out = from_backup(&sample_payload(), 1700000000000, 0).unwrap();
        let state = &out["state"];
        assert_eq!(out["meta"]["count"], 2);
        assert_eq!(out["meta"]["backupTimeMs"], 1700000000000i64);
        let a = &state["assets"][0];
        assert_eq!(a["name"], "相机");
        assert_eq!(a["buyDate"], "2024-01-01");
        assert_eq!(a["soldPrice"], 0.0);
        assert_eq!(a["favorite"], true);
        assert_eq!(a["coverImage"], "images/a_1_cover.jpg");
        assert_eq!(a["noteImages"][0], "images/a_1_note0.jpg");
        assert_eq!(a["targetUsageDays"], 30);
        assert_eq!(a["usages"][0]["date"], "2024-01-02");
        assert_eq!(a["incomeRecords"][0]["amount"], 500.0);
        assert_eq!(a["priceRecords"][0]["price"], 19000.0);
        assert_eq!(a["expireAt"], "2024-02-01");
        assert_eq!(a["remindDays"], 3);
        assert_eq!(state["saveMoney"][0]["amount"], 100);
        let sold = &state["assets"][1];
        assert_eq!(sold["status"], "sold");
        assert_eq!(sold["soldDate"], "2024-02-01");
    }

    #[test]
    fn round_trips_through_payload() {
        let parsed = from_backup(&sample_payload(), 1700000000000, 0).unwrap();
        let state = parsed["state"].clone();
        let images = json!({"images/a_1_cover.jpg":"YQ==","images/a_1_note0.jpg":"Yg=="});
        let payload = to_payload(&state, &images, "AOS", 1700000000000, 0).unwrap();

        assert_eq!(payload["data"]["platform"], "AOS");
        assert_eq!(payload["data"]["assets"][0]["buy_date"], 1704067200);
        assert_eq!(payload["data"]["usages"][0]["usage_date"], 1704153600);
        assert_eq!(payload["data"]["income_records"][0]["amount"], 500.0);
        assert_eq!(
            payload["data"]["expire_reminders"][0]["remind_at"],
            1706486400
        );
        assert_eq!(payload["data"]["settings"]["updatedAt"], 1700000000000i64);
        assert_eq!(payload["images"].as_array().unwrap().len(), 2);

        let again = from_backup(&payload, 1700000000000, 0).unwrap();
        let a = &again["state"]["assets"][0];
        assert_eq!(a["buyDate"], "2024-01-01");
        assert_eq!(a["coverImage"], "images/a_1_cover.jpg");
        assert_eq!(a["targetUsageDays"], 30);
        assert_eq!(a["usages"][0]["note"], "拍了一次");
    }

    #[test]
    fn merges_and_prefers_incoming() {
        let local = json!({
            "assets":[{"id":"a_1","name":"本地","price":1,"coverPath":"/tmp/a.jpg"}],
            "categories":[{"id":"c_1","name":"本地分类"}],
            "tags":[],
            "incomeCategories":[{"id":"i_1","name":"出租"}],
            "saveMoney":[],
            "settings":{"updatedAt":1},
            "pro":true
        });
        let incoming = json!({
            "assets":[{"id":"a_1","name":"备份","price":2},{"id":"a_2","name":"新增","price":3}],
            "categories":[{"id":"c_1","name":"备份分类"},{"id":"c_2","name":"新分类"}],
            "tags":[],
            "incomeCategories":[],
            "saveMoney":[{"id":"sm_1"}],
            "settings":{"updatedAt":9}
        });

        let merged = merge(&local, &incoming, false, false).unwrap();
        assert_eq!(merged["assets"][0]["name"], "本地");
        assert_eq!(merged["assets"][0]["coverPath"], "/tmp/a.jpg");
        assert_eq!(merged["assets"].as_array().unwrap().len(), 2);
        assert_eq!(merged["categories"][0]["name"], "本地分类");
        assert_eq!(merged["categories"].as_array().unwrap().len(), 2);
        assert_eq!(merged["saveMoney"][0]["id"], "sm_1");
        assert_eq!(merged["settings"]["updatedAt"], 1);
        assert_eq!(merged["pro"], true);

        let merged = merge(&local, &incoming, false, true).unwrap();
        assert_eq!(merged["assets"][0]["name"], "备份");
        assert_eq!(merged["assets"][0].get("coverPath"), None);

        let replaced = merge(&local, &incoming, true, false).unwrap();
        assert_eq!(replaced["assets"].as_array().unwrap().len(), 2);
        assert_eq!(replaced["assets"][0]["name"], "备份");
        // 备份收入分类为空时保留本地
        assert_eq!(replaced["incomeCategories"][0]["name"], "出租");
        assert_eq!(replaced["settings"]["updatedAt"], 1);
    }

    #[test]
    fn backup_time_falls_back_to_exported_at() {
        let payload = json!({"data":{"exported_at": 123}});
        assert_eq!(backup_time_ms(&payload), 123000);
        let payload = json!({"data":{"exported_at": 123,"settings":{"updatedAt":456}}});
        assert_eq!(backup_time_ms(&payload), 456);
    }

    #[test]
    fn normalize_fills_defaults_and_keeps_platform_values() {
        let state = json!({
            "assets":[{"id":"a_1","name":"x","usages":[{"id":"u"}]}],
            "categories":[],
            "settings":{"thousands":false,"currency":"USD"},
            "pro":true
        });
        let defaults = json!({"thousands":true,"duration":"days","view":"grid"});
        let out = normalize(&state, &defaults);

        // 数组与记录数组
        assert!(out["tags"].is_array());
        assert!(out["saveMoney"].is_array());
        assert_eq!(out["assets"][0]["incomeRecords"], json!([]));
        assert_eq!(out["assets"][0]["priceRecords"], json!([]));
        assert_eq!(out["assets"][0]["usages"][0]["id"], "u");

        // 收入分类种子
        assert_eq!(out["incomeCategories"].as_array().unwrap().len(), 4);
        assert_eq!(out["incomeCategories"][0]["name"], "出租");

        // 已有设置优先；平台默认值补齐；核心默认值补齐
        assert_eq!(out["settings"]["currency"], "USD");
        assert_eq!(out["settings"]["thousands"], false);
        assert_eq!(out["settings"]["duration"], "days");
        assert_eq!(out["settings"]["view"], "grid");
        assert_eq!(out["settings"]["decimals"], 2);
        assert_eq!(out["settings"]["theme"], "system");
        assert_eq!(out["settings"]["updatedAt"], 0);

        // 未知根字段保留
        assert_eq!(out["pro"], true);
    }
}
