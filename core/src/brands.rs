//! 品牌/型号数据：解析、别名匹配、型号→图标推断、远程数据校验。
//!
//! 数据源为仓库根目录 `data/brands.json`；Android 从 GitHub 拉取并缓存，
//! Web 构建时复制到 `public/brands.json`。各端只保留图标 key → 本地资源
//! 路径的映射、网络执行与缓存位置。

use serde_json::{json, Map, Value};

pub const API_URL: &str = "https://api.github.com/repos/HanYunChenLuo/xiaoman/contents/data/brands.json";
pub const RAW_URL: &str = "https://raw.githubusercontent.com/HanYunChenLuo/xiaoman/main/data/brands.json";
const MAX_SCHEMA: i64 = 1;

fn int_value(x: &Value) -> Option<i64> {
    match x {
        Value::Number(n) => n.as_i64().or_else(|| n.as_f64().map(|f| f as i64)),
        Value::String(s) => s.trim().parse::<i64>().ok(),
        Value::Bool(b) => Some(if *b { 1 } else { 0 }),
        _ => None,
    }
}

fn i64_of(v: &Value, key: &str) -> i64 {
    v.get(key).and_then(int_value).unwrap_or(0)
}

pub fn api_url() -> &'static str {
    API_URL
}

pub fn raw_url() -> &'static str {
    RAW_URL
}

/// 校验数据格式/App 版本兼容性。
/// 返回 `{ok, version, versionName, error}`；`error` 为可直接展示的中文。
pub fn validate(data: &Value, app_version_code: i64) -> Value {
    let schema = data
        .get("schema")
        .and_then(int_value)
        .unwrap_or(MAX_SCHEMA);
    let version = i64_of(data, "dataVersion");
    let version_name = data
        .get("dataVersionName")
        .and_then(Value::as_str)
        .map(str::to_string)
        .unwrap_or_else(|| version.to_string());

    let error = if schema > MAX_SCHEMA {
        "数据格式版本过高"
    } else {
        let sup = data.get("supportedApp");
        let min = sup.map(|s| i64_of(s, "minVersionCode")).unwrap_or(0);
        let max = sup.map(|s| i64_of(s, "maxVersionCode")).unwrap_or(0);
        if app_version_code < min || (max > 0 && app_version_code > max) {
            "当前版本不支持该数据"
        } else {
            ""
        }
    };

    json!({
        "ok": error.is_empty(),
        "version": version,
        "versionName": version_name,
        "error": error,
    })
}

/// 解析品牌数据为跨端共用的 book。
///
/// 输出：
/// - `brands`：原始分类表（Android 品牌选择器直接使用元组格式）
/// - `categories` / `byId`：对象格式（Web 使用）
/// - `models`：品牌 id → 型号分组
/// - `aliases`：`[aliasLower, name, id, latin?1:0]`，按别名长度倒序
/// - `version` / `versionName`
pub fn parse(data: &Value) -> Value {
    // 兼容旧格式：没有 brands 字段时，数据本身即分类表。
    let brands = data
        .get("brands")
        .cloned()
        .unwrap_or_else(|| data.clone());

    let mut models = Map::new();
    let mut by_id = Map::new();
    let mut categories = Map::new();
    let mut aliases: Vec<Vec<Value>> = Vec::new();

    if let Some(source) = brands.as_object() {
        for (cat, list) in source {
            let mut out_list = Vec::new();
            for item in list.as_array().map(Vec::as_slice).unwrap_or(&[]) {
                let Some(tuple) = item.as_array() else { continue };
                let name = tuple
                    .get(0)
                    .and_then(Value::as_str)
                    .unwrap_or("")
                    .to_string();
                let id = tuple
                    .get(1)
                    .and_then(Value::as_str)
                    .unwrap_or("")
                    .to_string();
                let brand_aliases = tuple
                    .get(2)
                    .and_then(Value::as_array)
                    .cloned()
                    .unwrap_or_default();
                let groups = tuple
                    .get(3)
                    .cloned()
                    .unwrap_or_else(|| json!([]));

                if !id.is_empty() {
                    models.insert(id.clone(), groups.clone());
                }
                for alias in &brand_aliases {
                    let Some(alias) = alias.as_str() else { continue };
                    if alias.is_empty() {
                        continue;
                    }
                    let lower = alias.to_lowercase();
                    let latin = lower.chars().any(|c| c.is_ascii_alphabetic());
                    aliases.push(vec![
                        json!(lower),
                        json!(name),
                        json!(id),
                        json!(if latin { 1 } else { 0 }),
                    ]);
                }

                let brand = json!({
                    "name": name,
                    "id": id,
                    "aliases": brand_aliases,
                    "groups": groups,
                });
                if !id.is_empty() {
                    by_id.insert(id.clone(), brand.clone());
                }
                out_list.push(brand);
            }
            categories.insert(cat.clone(), Value::Array(out_list));
        }
    }

    // 与旧实现一致：长别名优先，长度相同保持数据顺序（稳定排序）。
    aliases.sort_by(|a, b| {
        let len = |v: &Vec<Value>| v[0].as_str().unwrap_or("").chars().count();
        len(b).cmp(&len(a))
    });
    let aliases: Vec<Value> = aliases.into_iter().map(Value::Array).collect();

    json!({
        "version": i64_of(data, "dataVersion"),
        "versionName": data
            .get("dataVersionName")
            .and_then(Value::as_str)
            .map(str::to_string)
            .unwrap_or_else(|| i64_of(data, "dataVersion").to_string()),
        "brands": brands,
        "categories": Value::Object(categories),
        "byId": Value::Object(by_id),
        "models": Value::Object(models),
        "aliases": aliases,
    })
}

/// 别名匹配（`aliases` 为 [`parse`] 输出的 `aliases`）。
/// latin 别名要求词边界；中文别名要求长度 ≥ 2。
pub fn match_brand(text: &str, aliases: &Value) -> Value {
    let lower = text.trim().to_lowercase();
    if lower.is_empty() {
        return Value::Null;
    }
    for item in aliases.as_array().map(Vec::as_slice).unwrap_or(&[]) {
        let Some(tuple) = item.as_array() else { continue };
        let alias = tuple.get(0).and_then(Value::as_str).unwrap_or("");
        if alias.is_empty() {
            continue;
        }
        let latin = tuple.get(3).and_then(Value::as_i64).unwrap_or(0) != 0;
        let hit = if latin {
            latin_word(&lower, alias)
        } else {
            alias.chars().count() >= 2 && lower.contains(alias)
        };
        if hit {
            return json!({
                "name": tuple.get(1).and_then(Value::as_str).unwrap_or(""),
                "id": tuple.get(2).and_then(Value::as_str).unwrap_or(""),
            });
        }
    }
    Value::Null
}

fn is_word_byte(c: char) -> bool {
    c.is_ascii_alphanumeric()
}

/// 等价于正则 `(?<![a-z0-9])needle(?![a-z0-9])`。
fn latin_word(haystack: &str, needle: &str) -> bool {
    let hay: Vec<char> = haystack.chars().collect();
    let needle: Vec<char> = needle.chars().collect();
    if needle.is_empty() || needle.len() > hay.len() {
        return false;
    }
    let mut i = 0;
    while i + needle.len() <= hay.len() {
        if hay[i..i + needle.len()] == needle[..] {
            let before_ok = i == 0 || !is_word_byte(hay[i - 1]);
            let after_ok = i + needle.len() == hay.len() || !is_word_byte(hay[i + needle.len()]);
            if before_ok && after_ok {
                return true;
            }
        }
        i += 1;
    }
    false
}

/// 型号/名称 → 图标 key（`phone` / `laptop` / … / `default`）。
/// 长关键词优先，长度相同按表内顺序；与 Android 旧实现一致。
pub fn icon_key(text: &str) -> &'static str {
    let lower = text.to_lowercase();
    if lower.trim().is_empty() {
        return "default";
    }
    let mut pairs: Vec<(&str, &str)> = ICON_RULES
        .iter()
        .flat_map(|(key, words)| words.iter().map(move |w| (*w, *key)))
        .collect();
    pairs.sort_by(|a, b| b.0.chars().count().cmp(&a.0.chars().count()));
    for (word, key) in pairs {
        let latin = word.chars().any(|c| c.is_ascii_alphabetic());
        let hit = if latin {
            latin_word(&lower, word)
        } else {
            word.chars().count() >= 2 && lower.contains(word)
        };
        if hit {
            return key;
        }
    }
    "default"
}

const ICON_RULES: &[(&str, &[&str])] = &[
    (
        "phone",
        &[
            "iphone", "galaxy", "pixel", "mate", "redmi", "ipad", "平板", "matepad", "手机", "小米",
            "oppo", "vivo", "一加", "oneplus", "荣耀", "honor", "magic",
        ],
    ),
    (
        "laptop",
        &[
            "macbook", "笔记本", "thinkpad", "小新", "matebook", "rog", "拯救者", "天选", "xps",
            "轻薄本", "游戏本",
        ],
    ),
    (
        "desktop",
        &["mac mini", "mac studio", "imac", "台式机", "台式", "主机", "组装机", "一体机"],
    ),
    (
        "game",
        &["switch", "ns2", "ns", "ps5", "ps4", "playstation", "xbox", "游戏机"],
    ),
    (
        "drone",
        &[
            "dji", "pocket", "osmo", "动作相机", "运动相机", "摄像机", "mavic", "无人机", "drone",
        ],
    ),
    ("camera", &["相机", "camera", "单反", "微单", "镜头", "gopro"]),
    (
        "headphone",
        &["airpods", "耳机", "headphone", "音箱", "音响", "speaker"],
    ),
    ("watch", &["apple watch", "手表", "手环", "watch"]),
    ("tv", &["电视", "tv", "显示器", "monitor"]),
    (
        "car",
        &[
            "汽车", "理想", "特斯拉", "比亚迪", "蔚来", "小鹏", "宝马", "奔驰", "奥迪", "大众",
            "丰田", "本田", "日产", "保时捷", "沃尔沃", "极星", "马自达",
        ],
    ),
    (
        "bike",
        &["自行车", "公路车", "山地车", "单车", "bike", "giant", "捷安特", "trek"],
    ),
    ("moto", &["摩托", "motorcycle"]),
    ("scooter", &["滑板车", "电动滑板", "scooter"]),
    ("plane", &["飞机", "airplane"]),
];

#[cfg(test)]
mod tests {
    use super::*;

    fn sample() -> Value {
        json!({
            "schema": 1,
            "dataVersion": 20261120,
            "dataVersionName": "2026.11.20",
            "supportedApp": { "minVersionCode": 1, "maxVersionCode": 0 },
            "brands": {
                "digital": [
                    ["苹果", "apple", ["Apple", "苹果"], [["手机", ["iPhone 15", "iPhone 16"]]]],
                    ["华为", "huawei", ["Huawei", "华为"], [["手机", ["Mate 60"]]]],
                ],
                "car": [
                    ["理想", "lixiang", ["Li Auto", "理想"], []]
                ]
            }
        })
    }

    #[test]
    fn validates_schema_and_app_version() {
        let data = sample();
        let ok = validate(&data, 1);
        assert_eq!(ok["ok"], true);
        assert_eq!(ok["version"], 20261120);
        assert_eq!(ok["versionName"], "2026.11.20");

        let mut high = data.clone();
        high["schema"] = json!(2);
        assert_eq!(validate(&high, 1)["error"], "数据格式版本过高");

        // 字符串形式的 schema 也要按数字处理（旧端 optInt/Number 会转换）。
        let mut high_text = data.clone();
        high_text["schema"] = json!("2");
        assert_eq!(validate(&high_text, 1)["error"], "数据格式版本过高");

        let mut future = data.clone();
        future["supportedApp"] = json!({ "minVersionCode": 5 });
        assert_eq!(validate(&future, 1)["error"], "当前版本不支持该数据");

        let mut capped = data.clone();
        capped["supportedApp"] = json!({ "minVersionCode": 1, "maxVersionCode": 2 });
        assert_eq!(validate(&capped, 3)["ok"], false);
        assert_eq!(validate(&capped, 2)["ok"], true);
    }

    #[test]
    fn parses_aliases_models_and_categories() {
        let book = parse(&sample());
        assert_eq!(book["version"], 20261120);
        assert_eq!(book["versionName"], "2026.11.20");
        assert_eq!(book["categories"]["digital"].as_array().unwrap().len(), 2);
        assert_eq!(book["categories"]["digital"][0]["name"], "苹果");
        assert_eq!(book["byId"]["huawei"]["name"], "华为");
        assert_eq!(book["models"]["apple"][0][0], "手机");
        assert_eq!(book["brands"]["digital"][0][0], "苹果");

        // 别名按长度倒序：apple(5) / huawei(6) / 苹果 / 华为 / li auto(7) / 理想
        let aliases = book["aliases"].as_array().unwrap();
        let first = aliases[0].as_array().unwrap();
        assert_eq!(first[0], "li auto");
        assert_eq!(first[3], 1);
        let zh = aliases
            .iter()
            .find(|a| a[0] == "苹果")
            .unwrap()
            .as_array()
            .unwrap();
        assert_eq!(zh[2], "apple");
        assert_eq!(zh[3], 0);
    }

    #[test]
    fn matches_latin_and_cjk_with_rules() {
        let book = parse(&sample());
        let aliases = &book["aliases"];

        assert_eq!(match_brand("Apple iPhone 15 Pro", aliases)["id"], "apple");
        assert_eq!(match_brand("我的华为手机", aliases)["id"], "huawei");
        assert_eq!(match_brand("Li Auto L9", aliases)["id"], "lixiang");
        // latin 词边界：pineapple 不应命中 apple
        assert!(match_brand("pineapple juice", aliases).is_null());
        // 中文别名要求长度 ≥ 2
        assert!(match_brand("理", aliases).is_null());
        assert!(match_brand("", aliases).is_null());
    }

    #[test]
    fn icon_key_prefers_longer_keywords() {
        assert_eq!(icon_key("iPhone 15"), "phone");
        assert_eq!(icon_key("MacBook Pro"), "laptop");
        assert_eq!(icon_key("Mac mini"), "desktop");
        assert_eq!(icon_key("DJI Pocket 3"), "drone");
        assert_eq!(icon_key("运动相机"), "drone");
        assert_eq!(icon_key("理想 L9"), "car");
        assert_eq!(icon_key("公路车"), "bike");
        assert_eq!(icon_key("神秘物品"), "default");
        assert_eq!(icon_key(""), "default");
    }

    #[test]
    fn urls_are_stable() {
        assert!(api_url().starts_with("https://api.github.com/"));
        assert!(raw_url().starts_with("https://raw.githubusercontent.com/"));
    }
}
