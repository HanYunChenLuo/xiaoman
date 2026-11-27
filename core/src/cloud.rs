//! 云备份协议：Google Drive appDataFolder 的请求构造与响应解析。
//!
//! 各端只负责 OAuth 授权、token 存储和真正执行 HTTP；请求的 URL/方法/
//! 头部、文件命名与错误映射统一在这里，保证 Android / Web / 桌面行为一致。
//!
//! 约定（与 Android 旧实现、Web 端一致的云端契约）：
//! - 云端文件固定为 appDataFolder 下的 `xiaoman_backup.ias`
//! - 文件内容是 Rust `backup::encode_ias` 产出的原始字节
//! - 取消授权/权限问题以中文错误串返回，由各端直接展示

use serde::{Deserialize, Serialize};
use serde_json::{json, Value};

use crate::util::percent_encode;

/// 云端备份文件名，Android 与 Web 共用。
pub const BACKUP_NAME: &str = "xiaoman_backup.ias";
const API_ROOT: &str = "https://www.googleapis.com";
const FILE_FIELDS: &str = "id,name,modifiedTime,size";

/// 平台需要执行的一次 HTTP 请求。
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct CloudRequest {
    pub method: String,
    pub url: String,
    pub headers: std::collections::BTreeMap<String, String>,
    /// 仅创建文件时使用；上传字节由平台附加，不放这里。
    #[serde(skip_serializing_if = "Option::is_none")]
    pub body: Option<String>,
}

/// 云端文件元数据。
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct CloudFile {
    pub id: String,
    #[serde(default)]
    pub name: Option<String>,
    #[serde(default, rename = "modifiedTime")]
    pub modified_time: Option<String>,
    #[serde(default)]
    pub size: Option<String>,
}

pub fn backup_name() -> &'static str {
    BACKUP_NAME
}

fn request(method: &str, url: String, token: &str) -> CloudRequest {
    let mut headers = std::collections::BTreeMap::new();
    headers.insert("Authorization".to_string(), format!("Bearer {token}"));
    CloudRequest {
        method: method.to_string(),
        url,
        headers,
        body: None,
    }
}

fn with_header(mut req: CloudRequest, key: &str, value: &str) -> CloudRequest {
    req.headers.insert(key.to_string(), value.to_string());
    req
}

/// 查找云端备份（按修改时间倒序取第一条）。
pub fn find_request(token: &str) -> CloudRequest {
    let q = format!("name='{}' and trashed=false", BACKUP_NAME);
    let url = format!(
        "{API_ROOT}/drive/v3/files?spaces=appDataFolder&q={}&fields={}&orderBy={}&pageSize=5",
        percent_encode(&q),
        percent_encode(&format!("files({FILE_FIELDS})")),
        percent_encode("modifiedTime desc")
    );
    request("GET", url, token)
}

/// 创建空文件（元数据），随后用 [`upload_request`] 写入内容。
pub fn create_request(token: &str) -> CloudRequest {
    let url = format!(
        "{API_ROOT}/drive/v3/files?fields={}",
        percent_encode(FILE_FIELDS)
    );
    let mut req = with_header(request("POST", url, token), "Content-Type", "application/json");
    req.body = Some(
        json!({ "name": BACKUP_NAME, "parents": ["appDataFolder"] }).to_string(),
    );
    req
}

/// 用 `uploadType=media` 写入/覆盖备份字节。
pub fn upload_request(token: &str, file_id: &str) -> CloudRequest {
    let url = format!(
        "{API_ROOT}/upload/drive/v3/files/{}?uploadType=media&fields={}",
        percent_encode(file_id),
        percent_encode(FILE_FIELDS)
    );
    with_header(request("PATCH", url, token), "Content-Type", "application/octet-stream")
}

/// 下载备份字节（`alt=media`）。
pub fn download_request(token: &str, file_id: &str) -> CloudRequest {
    let url = format!(
        "{API_ROOT}/drive/v3/files/{}?alt=media",
        percent_encode(file_id)
    );
    request("GET", url, token)
}

/// Drive 返回的 ISO 8601 时间（UTC）→ 毫秒；空串或格式异常返回 0。
pub fn time_ms(iso: &str) -> i64 {
    let iso = iso.trim();
    if iso.is_empty() {
        return 0;
    }
    chrono::DateTime::parse_from_rfc3339(iso)
        .map(|dt| dt.timestamp_millis())
        .unwrap_or(0)
}

/// 上传前冲突判断：云端比本地新、且不等于我们上次上传后看到的那份时才算“云端较新”。
/// 返回 `{newer, remoteMs}`，`remoteMs` 供提示文案显示。
pub fn conflict(remote_iso: &str, local_ms: i64, last_remote_ms: i64) -> Value {
    let remote_ms = time_ms(remote_iso);
    json!({
        "newer": remote_ms > 0 && remote_ms > local_ms && remote_ms > last_remote_ms,
        "remoteMs": remote_ms,
    })
}

fn error_json(status: i32, body: &str) -> Value {
    let message = match status {
        401 => "Google 登录已过期，请重新登录".to_string(),
        403 => "没有云端访问权限，请在授权时允许访问 Drive".to_string(),
        404 => "云端备份不存在".to_string(),
        0 => "网络请求失败".to_string(),
        _ => {
            let detail = serde_json::from_str::<Value>(body)
                .ok()
                .and_then(|v| v.pointer("/error/message").and_then(|m| m.as_str()).map(str::to_string))
                .unwrap_or_default();
            if detail.is_empty() {
                format!("云端请求失败（HTTP {status}）")
            } else {
                format!("云端请求失败（HTTP {status}）：{detail}")
            }
        }
    };
    json!({
        "ok": false,
        "status": status,
        "relogin": status == 401,
        "error": message
    })
}

fn malformed(status: i32) -> Value {
    json!({
        "ok": false,
        "status": status,
        "relogin": false,
        "error": "云端响应格式异常"
    })
}

/// 解析 `files.list` 响应，返回 `{ok, file}`（无文件时 `file` 为 null）。
pub fn parse_file_list(status: i32, body: &str) -> Value {
    if !(200..300).contains(&status) {
        return error_json(status, body);
    }
    let Ok(v) = serde_json::from_str::<Value>(body) else {
        return malformed(status);
    };
    let Some(files) = v.get("files").and_then(|f| f.as_array()) else {
        return malformed(status);
    };
    let file = files
        .iter()
        .find_map(|f| serde_json::from_value::<CloudFile>(f.clone()).ok())
        .filter(|f| !f.id.is_empty());
    json!({ "ok": true, "file": file })
}

/// 解析创建/上传响应，返回 `{ok, file}`。
pub fn parse_file(status: i32, body: &str) -> Value {
    if !(200..300).contains(&status) {
        return error_json(status, body);
    }
    match serde_json::from_str::<CloudFile>(body) {
        Ok(f) if !f.id.is_empty() => json!({ "ok": true, "file": f }),
        _ => malformed(status),
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn find_url_encodes_query() {
        let req = find_request("tok");
        assert_eq!(req.method, "GET");
        assert_eq!(req.headers.get("Authorization").map(String::as_str), Some("Bearer tok"));
        assert!(req.url.starts_with("https://www.googleapis.com/drive/v3/files?"));
        assert!(req.url.contains("spaces=appDataFolder"));
        assert!(req.url.contains("xiaoman_backup.ias"));
        assert!(req.url.contains("trashed%3Dfalse"));
        assert!(!req.url.contains(' '));
    }

    #[test]
    fn create_request_has_metadata() {
        let req = create_request("tok");
        assert_eq!(req.method, "POST");
        assert_eq!(req.headers.get("Content-Type").map(String::as_str), Some("application/json"));
        let body: Value = serde_json::from_str(req.body.as_deref().unwrap()).unwrap();
        assert_eq!(body["name"], BACKUP_NAME);
        assert_eq!(body["parents"][0], "appDataFolder");
    }

    #[test]
    fn upload_and_download_paths() {
        let up = upload_request("tok", "abc");
        assert_eq!(up.method, "PATCH");
        assert!(up.url.contains("/upload/drive/v3/files/abc?uploadType=media"));
        let down = download_request("tok", "abc");
        assert_eq!(down.method, "GET");
        assert!(down.url.ends_with("/drive/v3/files/abc?alt=media"));
    }

    #[test]
    fn file_id_is_encoded() {
        let req = download_request("tok", "a/b c");
        assert!(req.url.ends_with("/drive/v3/files/a%2Fb%20c?alt=media"));
    }

    #[test]
    fn parse_list_returns_first_file() {
        let body = r#"{"files":[{"id":"f1","name":"xiaoman_backup.ias","modifiedTime":"2026-11-19T08:00:00Z","size":"12"}]}"#;
        let v = parse_file_list(200, body);
        assert_eq!(v["ok"], true);
        assert_eq!(v["file"]["id"], "f1");
        assert_eq!(v["file"]["modifiedTime"], "2026-11-19T08:00:00Z");
    }

    #[test]
    fn parse_list_empty() {
        let v = parse_file_list(200, r#"{"files":[]}"#);
        assert_eq!(v["ok"], true);
        assert!(v["file"].is_null());
    }

    #[test]
    fn parse_errors() {
        let v = parse_file_list(401, "{}");
        assert_eq!(v["ok"], false);
        assert_eq!(v["relogin"], true);
        assert_eq!(v["error"], "Google 登录已过期，请重新登录");

        let v = parse_file(403, "");
        assert_eq!(v["error"], "没有云端访问权限，请在授权时允许访问 Drive");

        let v = parse_file(500, r#"{"error":{"message":"boom"}}"#);
        assert_eq!(v["error"], "云端请求失败（HTTP 500）：boom");
    }

    #[test]
    fn parse_malformed_success() {
        let v = parse_file(200, "not json");
        assert_eq!(v["ok"], false);
        assert_eq!(v["error"], "云端响应格式异常");
    }

    #[test]
    fn parses_drive_time() {
        assert_eq!(time_ms("2026-11-19T08:00:00Z"), 1_795_075_200_000);
        assert_eq!(time_ms("2026-11-19T08:00:00.123Z"), 1_795_075_200_123);
        assert_eq!(time_ms("2026-11-19T16:00:00+08:00"), 1_795_075_200_000);
        assert_eq!(time_ms(""), 0);
        assert_eq!(time_ms("not a time"), 0);
    }

    #[test]
    fn conflict_only_when_remote_is_newer() {
        let remote = "2026-11-19T08:00:00Z";
        let remote_ms = 1_795_075_200_000;

        let same = conflict(remote, remote_ms, 0);
        assert_eq!(same["newer"], false);
        assert_eq!(same["remoteMs"], remote_ms);

        let newer = conflict(remote, remote_ms - 1000, 0);
        assert_eq!(newer["newer"], true);

        // 我们上次已经见过这个云端时间：不再提示。
        let seen = conflict(remote, remote_ms - 1000, remote_ms);
        assert_eq!(seen["newer"], false);

        // 本地比云端新：不提示。
        let local_newer = conflict(remote, remote_ms + 1000, 0);
        assert_eq!(local_newer["newer"], false);

        // 拿不到云端时间：不提示。
        let unknown = conflict("", 0, 0);
        assert_eq!(unknown["newer"], false);
        assert_eq!(unknown["remoteMs"], 0);
    }
}
