//! Google OAuth 授权流程：PKCE、授权 URL、令牌请求与响应解析。
//!
//! Android 与桌面端共用这里的 URL/表单/错误映射；各端只负责浏览器/回环、
//! token 存储和真正执行 HTTP。Web 使用 GIS Token 模型，不走这段代码。

use std::collections::BTreeMap;

use base64::Engine as _;
use serde::{Deserialize, Serialize};
use serde_json::{json, Value};
use sha2::{Digest, Sha256};

use crate::util::percent_encode;

pub const AUTH_URL: &str = "https://accounts.google.com/o/oauth2/v2/auth";
pub const TOKEN_URL: &str = "https://oauth2.googleapis.com/token";
pub const USERINFO_URL: &str = "https://openidconnect.googleapis.com/v1/userinfo";
pub const SCOPE: &str = "openid email profile https://www.googleapis.com/auth/drive.appdata";

/// 平台需要执行的一次 HTTP 请求（与 `cloud::CloudRequest` 同形）。
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct OAuthRequest {
    pub method: String,
    pub url: String,
    pub headers: BTreeMap<String, String>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub body: Option<String>,
}

fn form(params: &[(&str, &str)]) -> String {
    params
        .iter()
        .map(|(k, v)| format!("{}={}", percent_encode(k), percent_encode(v)))
        .collect::<Vec<_>>()
        .join("&")
}

fn post_form(params: &[(&str, &str)]) -> OAuthRequest {
    let mut headers = BTreeMap::new();
    headers.insert(
        "Content-Type".to_string(),
        "application/x-www-form-urlencoded".to_string(),
    );
    headers.insert("Accept".to_string(), "application/json".to_string());
    OAuthRequest {
        method: "POST".to_string(),
        url: TOKEN_URL.to_string(),
        headers,
        body: Some(form(params)),
    }
}

/// PKCE S256：`base64url(sha256(verifier))`，无填充。
pub fn pkce_challenge(verifier: &str) -> String {
    let digest = Sha256::digest(verifier.as_bytes());
    base64::engine::general_purpose::URL_SAFE_NO_PAD.encode(digest)
}

/// 浏览器授权 URL。`state` / `prompt` 为空时省略对应参数。
pub fn auth_url(
    client_id: &str,
    redirect_uri: &str,
    challenge: &str,
    state: &str,
    scope: &str,
    prompt: &str,
) -> String {
    let mut params = vec![
        ("client_id", client_id),
        ("redirect_uri", redirect_uri),
        ("response_type", "code"),
        ("scope", scope),
        ("code_challenge", challenge),
        ("code_challenge_method", "S256"),
        ("access_type", "offline"),
        ("include_granted_scopes", "true"),
    ];
    if !prompt.is_empty() {
        params.push(("prompt", prompt));
    }
    if !state.is_empty() {
        params.push(("state", state));
    }
    format!("{AUTH_URL}?{}", form(&params))
}

/// 授权码换令牌；`client_secret` 为空时不提交（Android 公开客户端）。
pub fn token_request(
    client_id: &str,
    client_secret: &str,
    code: &str,
    verifier: &str,
    redirect_uri: &str,
) -> OAuthRequest {
    let mut params = vec![
        ("client_id", client_id),
        ("code", code),
        ("code_verifier", verifier),
        ("redirect_uri", redirect_uri),
        ("grant_type", "authorization_code"),
    ];
    if !client_secret.is_empty() {
        params.push(("client_secret", client_secret));
    }
    post_form(&params)
}

/// refresh token 续期。
pub fn refresh_request(client_id: &str, client_secret: &str, refresh_token: &str) -> OAuthRequest {
    let mut params = vec![
        ("client_id", client_id),
        ("refresh_token", refresh_token),
        ("grant_type", "refresh_token"),
    ];
    if !client_secret.is_empty() {
        params.push(("client_secret", client_secret));
    }
    post_form(&params)
}

/// 读取账号邮箱。
pub fn userinfo_request(access_token: &str) -> OAuthRequest {
    let mut headers = BTreeMap::new();
    headers.insert(
        "Authorization".to_string(),
        format!("Bearer {access_token}"),
    );
    OAuthRequest {
        method: "GET".to_string(),
        url: USERINFO_URL.to_string(),
        headers,
        body: None,
    }
}

fn as_i64(v: Option<&Value>) -> i64 {
    match v {
        Some(Value::Number(n)) => n
            .as_i64()
            .or_else(|| n.as_f64().map(|f| f as i64))
            .unwrap_or(0),
        Some(Value::String(s)) => s
            .trim()
            .parse::<i64>()
            .ok()
            .or_else(|| s.trim().parse::<f64>().ok().map(|f| f as i64))
            .unwrap_or(0),
        _ => 0,
    }
}

/// 解析令牌响应，返回 `{ok, accessToken, refreshToken, expiresIn, scope}` 或
/// `{ok:false, status, relogin, error, errorCode}`。
///
/// `relogin` 为 true 表示 refresh token 已过期/被吊销，应清除本地令牌并提示重新登录。
pub fn parse_token(status: i32, body: &str) -> Value {
    let value: Value = serde_json::from_str(body).unwrap_or(Value::Null);
    let access = value
        .get("access_token")
        .and_then(Value::as_str)
        .unwrap_or("")
        .to_string();
    if (200..300).contains(&status) && !access.is_empty() {
        let expires = as_i64(value.get("expires_in"));
        return json!({
            "ok": true,
            "accessToken": access,
            "refreshToken": value.get("refresh_token").and_then(Value::as_str).unwrap_or(""),
            "expiresIn": if expires > 0 { expires } else { 3600 },
            "scope": value.get("scope").and_then(Value::as_str).unwrap_or(""),
        });
    }

    let error_code = value
        .get("error")
        .and_then(Value::as_str)
        .unwrap_or("")
        .to_string();
    let description = value
        .get("error_description")
        .and_then(Value::as_str)
        .unwrap_or("")
        .to_string();
    let lower = description.to_ascii_lowercase();
    let relogin = error_code == "invalid_grant"
        || lower.contains("expired")
        || lower.contains("revoked");
    let message = if !description.is_empty() {
        description
    } else if !error_code.is_empty() {
        error_code
    } else if (200..300).contains(&status) {
        "获取令牌失败".to_string()
    } else {
        format!("令牌请求失败（HTTP {status}）")
    };
    json!({
        "ok": false,
        "status": status,
        "relogin": relogin,
        "error": message,
        "errorCode": value.get("error").and_then(Value::as_str).unwrap_or(""),
    })
}

/// 解析 userinfo 响应；失败返回空串。
pub fn parse_email(status: i32, body: &str) -> String {
    if !(200..300).contains(&status) {
        return String::new();
    }
    serde_json::from_str::<Value>(body)
        .ok()
        .and_then(|v| v.get("email").and_then(Value::as_str).map(str::to_string))
        .unwrap_or_default()
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn pkce_matches_rfc7636_vector() {
        let verifier = "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk";
        assert_eq!(
            pkce_challenge(verifier),
            "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM"
        );
    }

    #[test]
    fn auth_url_encodes_and_omits_empty_state() {
        let url = auth_url(
            "id.apps.googleusercontent.com",
            "http://127.0.0.1:41888",
            "chal",
            "",
            SCOPE,
            "select_account",
        );
        assert!(url.starts_with(AUTH_URL));
        assert!(url.contains("code_challenge=chal"));
        assert!(url.contains("code_challenge_method=S256"));
        assert!(url.contains("redirect_uri=http%3A%2F%2F127.0.0.1%3A41888"));
        assert!(url.contains("scope=openid%20email%20profile%20https%3A%2F%2Fwww.googleapis.com%2Fauth%2Fdrive.appdata"));
        assert!(url.contains("prompt=select_account"));
        assert!(!url.contains("state="));

        let with_state = auth_url("id", "scheme:/oauth2redirect", "c", "st", "s", "consent");
        assert!(with_state.contains("state=st"));
        assert!(with_state.contains("prompt=consent"));
    }

    #[test]
    fn token_requests_include_optional_secret() {
        let public = token_request("cid", "", "code", "ver", "http://127.0.0.1/x");
        assert_eq!(public.method, "POST");
        assert_eq!(public.headers.get("Content-Type").map(String::as_str), Some("application/x-www-form-urlencoded"));
        let body = public.body.as_deref().unwrap();
        assert!(body.contains("grant_type=authorization_code"));
        assert!(body.contains("code=code"));
        assert!(body.contains("code_verifier=ver"));
        assert!(!body.contains("client_secret"));

        let confidential = token_request("cid", "sec ret", "code", "ver", "http://x");
        assert!(confidential.body.as_deref().unwrap().contains("client_secret=sec%20ret"));

        let refresh = refresh_request("cid", "", "rf");
        let body = refresh.body.as_deref().unwrap();
        assert!(body.contains("grant_type=refresh_token"));
        assert!(body.contains("refresh_token=rf"));
        assert!(!body.contains("client_secret"));
    }

    #[test]
    fn userinfo_request_has_bearer() {
        let req = userinfo_request("tok");
        assert_eq!(req.method, "GET");
        assert_eq!(req.url, USERINFO_URL);
        assert_eq!(req.headers.get("Authorization").map(String::as_str), Some("Bearer tok"));
        assert!(req.body.is_none());
    }

    #[test]
    fn parse_token_success_and_errors() {
        let ok = parse_token(
            200,
            r#"{"access_token":"a","refresh_token":"r","expires_in":3599,"scope":"openid"}"#,
        );
        assert_eq!(ok["ok"], true);
        assert_eq!(ok["accessToken"], "a");
        assert_eq!(ok["refreshToken"], "r");
        assert_eq!(ok["expiresIn"], 3599);
        assert_eq!(ok["scope"], "openid");

        let string_expiry = parse_token(200, r#"{"access_token":"a","expires_in":"120"}"#);
        assert_eq!(string_expiry["expiresIn"], 120);

        let default_expiry = parse_token(200, r#"{"access_token":"a"}"#);
        assert_eq!(default_expiry["expiresIn"], 3600);

        let expired = parse_token(
            400,
            r#"{"error":"invalid_grant","error_description":"Token has been expired or revoked."}"#,
        );
        assert_eq!(expired["ok"], false);
        assert_eq!(expired["relogin"], true);
        assert_eq!(expired["error"], "Token has been expired or revoked.");

        let denied = parse_token(400, r#"{"error":"access_denied"}"#);
        assert_eq!(denied["relogin"], false);
        assert_eq!(denied["error"], "access_denied");

        let no_json = parse_token(500, "oops");
        assert_eq!(no_json["ok"], false);
        assert_eq!(no_json["error"], "令牌请求失败（HTTP 500）");
    }

    #[test]
    fn parse_email_reads_userinfo() {
        assert_eq!(parse_email(200, r#"{"email":"a@b.c"}"#), "a@b.c");
        assert_eq!(parse_email(401, r#"{"email":"a@b.c"}"#), "");
        assert_eq!(parse_email(200, "no"), "");
    }
}
