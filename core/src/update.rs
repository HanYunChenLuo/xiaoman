//! 更新检查协议：GitHub Releases 的请求/解析与版本比较。
//!
//! Android、Web/桌面共用同一套规则；各端只负责真正发起 HTTP、
//! 下载安装包与调用系统安装流程。

use serde_json::{json, Value};

pub const REPO: &str = "HanYunChenLuo/xiaoman";

pub fn release_api_url() -> String {
    format!("https://api.github.com/repos/{REPO}/releases/latest")
}

pub fn release_page_url() -> String {
    format!("https://github.com/{REPO}/releases")
}

fn version_parts(v: &str) -> Vec<u64> {
    v.trim()
        .trim_start_matches('v')
        .split(|c: char| !c.is_ascii_digit())
        .filter(|s| !s.is_empty())
        .map(|s| s.parse().unwrap_or(0))
        .collect()
}

/// `latest` 是否比 `current` 新（按数字段比较，忽略 v 前缀与预发布后缀）。
pub fn is_newer(current: &str, latest: &str) -> bool {
    let a = version_parts(current);
    let b = version_parts(latest);
    for i in 0..a.len().max(b.len()) {
        let x = a.get(i).copied().unwrap_or(0);
        let y = b.get(i).copied().unwrap_or(0);
        if y > x {
            return true;
        }
        if y < x {
            return false;
        }
    }
    false
}

/// 解析 `releases/latest` 响应：
/// `{ok, version, tag, notes, page, assets:[{name,url,size}]}`
/// 或 `{ok:false, status, error}`。
pub fn parse_release(status: i32, body: &str) -> Value {
    if status == 404 {
        return json!({ "ok": false, "status": 404, "error": "暂无发布版本" });
    }
    if !(200..300).contains(&status) {
        return json!({
            "ok": false,
            "status": status,
            "error": format!("检查更新失败（HTTP {status}）")
        });
    }
    let Ok(v) = serde_json::from_str::<Value>(body) else {
        return json!({ "ok": false, "status": status, "error": "更新信息格式异常" });
    };
    let tag = v.get("tag_name").and_then(Value::as_str).unwrap_or("").to_string();
    let version = tag.trim_start_matches('v').to_string();
    if version.is_empty() {
        return json!({ "ok": false, "status": status, "error": "更新信息缺少版本号" });
    }
    let assets: Vec<Value> = v
        .get("assets")
        .and_then(Value::as_array)
        .map(|list| {
            list.iter()
                .filter_map(|a| {
                    let name = a.get("name").and_then(Value::as_str)?;
                    let url = a.get("browser_download_url").and_then(Value::as_str)?;
                    Some(json!({
                        "name": name,
                        "url": url,
                        "size": a.get("size").and_then(Value::as_u64).unwrap_or(0),
                    }))
                })
                .collect()
        })
        .unwrap_or_default();
    json!({
        "ok": true,
        "version": version,
        "tag": tag,
        "notes": v.get("body").and_then(Value::as_str).unwrap_or(""),
        "page": v.get("html_url").and_then(Value::as_str).unwrap_or(""),
        "assets": assets,
    })
}

/// 从资产列表里挑第一个以 `suffix` 结尾的文件（如 `.apk`、`.dmg`）。
pub fn pick_asset(assets_json: &str, suffix: &str) -> Value {
    let suffix = suffix.to_ascii_lowercase();
    let Ok(v) = serde_json::from_str::<Value>(assets_json) else {
        return json!({ "found": false });
    };
    let list = v
        .get("assets")
        .and_then(Value::as_array)
        .or_else(|| v.as_array())
        .cloned()
        .unwrap_or_default();
    for item in list {
        let name = item.get("name").and_then(Value::as_str).unwrap_or("");
        if name.to_ascii_lowercase().ends_with(&suffix) {
            return json!({ "found": true, "asset": item });
        }
    }
    json!({ "found": false })
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn compares_versions() {
        assert!(is_newer("1.0.0", "1.0.1"));
        assert!(is_newer("1.0.0", "v1.1"));
        assert!(is_newer("1.9", "1.10"));
        assert!(!is_newer("1.0.1", "1.0.1"));
        assert!(!is_newer("2.0", "1.9.9"));
        assert!(!is_newer("1.0.0", "1.0.0-beta"));
    }

    #[test]
    fn parses_release() {
        let body = r#"{
          "tag_name": "v1.2.0",
          "body": "fixes",
          "html_url": "https://example/rel",
          "assets": [
            {"name":"latest.json","browser_download_url":"https://x/latest.json","size":10},
            {"name":"xiaoman-android.apk","browser_download_url":"https://x/a.apk","size":20}
          ]
        }"#;
        let v = parse_release(200, body);
        assert_eq!(v["ok"], true);
        assert_eq!(v["version"], "1.2.0");
        assert_eq!(v["assets"].as_array().unwrap().len(), 2);
    }

    #[test]
    fn handles_missing_release() {
        assert_eq!(parse_release(404, "{}")["error"], "暂无发布版本");
        assert_eq!(parse_release(500, "")["ok"], false);
    }

    #[test]
    fn picks_asset_by_suffix() {
        let assets = r#"[{"name":"latest.json","url":"u1"},{"name":"xiaoman.apk","url":"u2"}]"#;
        let v = pick_asset(assets, ".APK");
        assert_eq!(v["found"], true);
        assert_eq!(v["asset"]["name"], "xiaoman.apk");
        assert_eq!(pick_asset(assets, ".dmg")["found"], false);
    }
}
