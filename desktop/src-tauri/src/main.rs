//! 小满桌面端外壳：加载 Web UI，并为桌面端提供 Google 云备份能力。
//!
//! 云备份协议（URL/方法/头部、响应解析、错误文案）来自共享的 `xiaoman-core`；
//! 桌面端额外负责：
//! - 系统浏览器 + 127.0.0.1 loopback 的 Google OAuth（PKCE，无需 client_secret）
//! - 原生 HTTP（避免 WebView 的 CORS 与自定义 scheme 限制）

#![cfg_attr(not(debug_assertions), windows_subsystem = "windows")]

use std::collections::HashMap;
use std::path::PathBuf;
use std::time::{Duration, SystemTime, UNIX_EPOCH};

use aes_gcm::aead::{Aead, KeyInit};
use aes_gcm::{Aes256Gcm, Nonce};
use serde_json::{json, Value};
use sha2::{Digest, Sha256};
use tauri::ipc::Response;
use tauri::menu::{Menu, MenuItem};
use tauri::tray::{MouseButton, MouseButtonState, TrayIconBuilder, TrayIconEvent};
use tauri::{Manager, WindowEvent};
use tokio::io::{AsyncReadExt, AsyncWriteExt};
use tokio::net::TcpListener;
use xiaoman_core::{cloud, oauth};

const LOOPBACK_PORT: u16 = 41888;
const LOGIN_TIMEOUT: Duration = Duration::from_secs(180);
/// 仅用于混淆内置凭据文件；密钥随程序分发，不构成机密性保证。
const OAUTH_FILE_KEY_SEED: &str = "xiaoman-desktop/oauth-file/v1/5b0f6c1e";

fn oauth_file_key() -> [u8; 32] {
    Sha256::digest(OAUTH_FILE_KEY_SEED.as_bytes()).into()
}

/// 依次尝试资源目录、可执行文件目录、源码目录（开发时）。
fn credential_paths(app: &tauri::AppHandle) -> Vec<PathBuf> {
    let mut paths = Vec::new();
    if let Ok(dir) = app.path().resource_dir() {
        paths.push(dir.join("oauth.enc"));
    }
    if let Ok(exe) = std::env::current_exe() {
        if let Some(dir) = exe.parent() {
            paths.push(dir.join("oauth.enc"));
        }
    }
    paths.push(PathBuf::from(env!("CARGO_MANIFEST_DIR")).join("oauth.enc"));
    paths
}

/// 解密凭据文件内容；格式：12 字节 nonce + AES-256-GCM(ciphertext+tag)。
fn decrypt_credentials(data: &[u8]) -> Result<Option<(String, String)>, String> {
    if data.len() < 28 {
        return Err("凭据文件格式无效".to_string());
    }
    let cipher = Aes256Gcm::new_from_slice(&oauth_file_key()).map_err(|_| "初始化解密失败".to_string())?;
    let nonce = Nonce::from_slice(&data[..12]);
    let plain = cipher
        .decrypt(nonce, &data[12..])
        .map_err(|_| "凭据文件解密失败".to_string())?;
    let value: Value = serde_json::from_slice(&plain).map_err(|e| format!("凭据内容无效：{e}"))?;
    let id = value
        .get("clientId")
        .and_then(Value::as_str)
        .unwrap_or("")
        .trim()
        .to_string();
    let secret = value
        .get("clientSecret")
        .and_then(Value::as_str)
        .unwrap_or("")
        .trim()
        .to_string();
    if id.is_empty() {
        return Ok(None);
    }
    Ok(Some((id, secret)))
}

/// 读取并解密内置的 OAuth 凭据；文件不存在时返回 None（云同步不可用）。
fn load_credentials(app: &tauri::AppHandle) -> Result<Option<(String, String)>, String> {
    for path in credential_paths(app) {
        if !path.exists() {
            continue;
        }
        let data = std::fs::read(&path).map_err(|e| format!("读取凭据文件失败：{e}"))?;
        return decrypt_credentials(&data);
    }
    Ok(None)
}

/// 前端启动时查询：空对象表示当前构建没有内置云同步凭据。
#[tauri::command]
fn oauth_defaults(app: tauri::AppHandle) -> Value {
    match load_credentials(&app) {
        Ok(Some((client_id, client_secret))) => {
            json!({ "clientId": client_id, "clientSecret": client_secret })
        }
        _ => json!({}),
    }
}

fn now_ms() -> u64 {
    SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .map(|d| d.as_millis() as u64)
        .unwrap_or(0)
}

fn dec(s: &str) -> String {
    let bytes = s.as_bytes();
    let mut out = Vec::with_capacity(bytes.len());
    let mut i = 0;
    while i < bytes.len() {
        match bytes[i] {
            b'+' => {
                out.push(b' ');
                i += 1;
            }
            b'%' if i + 2 < bytes.len() => {
                let hex = std::str::from_utf8(&bytes[i + 1..i + 3]).unwrap_or("");
                match u8::from_str_radix(hex, 16) {
                    Ok(v) => {
                        out.push(v);
                        i += 3;
                    }
                    Err(_) => {
                        out.push(bytes[i]);
                        i += 1;
                    }
                }
            }
            c => {
                out.push(c);
                i += 1;
            }
        }
    }
    String::from_utf8_lossy(&out).into_owned()
}

fn random_string(len: usize) -> String {
    use rand::Rng;
    const CHARS: &[u8] = b"ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-._~";
    let mut rng = rand::thread_rng();
    (0..len).map(|_| CHARS[rng.gen_range(0..CHARS.len())] as char).collect()
}

/// 等待 loopback 回调，返回 (code, state)。
async fn wait_for_code(listener: TcpListener) -> Result<(String, String), String> {
    let (mut socket, _) = tokio::time::timeout(LOGIN_TIMEOUT, listener.accept())
        .await
        .map_err(|_| "等待 Google 授权超时".to_string())?
        .map_err(|e| format!("本地回调失败：{e}"))?;
    let mut buf = vec![0u8; 8192];
    let n = socket.read(&mut buf).await.map_err(|e| format!("读取回调失败：{e}"))?;
    let request = String::from_utf8_lossy(&buf[..n]);
    let path = request
        .lines()
        .next()
        .and_then(|line| line.split_whitespace().nth(1))
        .unwrap_or("");
    let query = path.split_once('?').map(|(_, q)| q).unwrap_or("");
    let params: HashMap<String, String> = query
        .split('&')
        .filter_map(|pair| pair.split_once('='))
        .map(|(k, v)| (dec(k), dec(v)))
        .collect();
    eprintln!(
        "[xiaoman-desktop] callback params: {:?}",
        params.keys().collect::<Vec<_>>()
    );

    let ok = params.contains_key("code");
    let (status, message) = if ok {
        ("200 OK", "登录成功，可以关闭此页面返回小满。")
    } else {
        ("400 Bad Request", "未获取到授权码，请回到小满重试。")
    };
    let body = format!(
        "<!doctype html><meta charset=\"utf-8\"><title>小满</title><body style=\"font-family:system-ui;display:flex;height:100vh;align-items:center;justify-content:center;background:#f4f4f4\"><p style=\"font-size:18px\">{message}</p></body>"
    );
    let resp = format!(
        "HTTP/1.1 {status}\r\nContent-Type: text/html; charset=utf-8\r\nContent-Length: {}\r\nConnection: close\r\n\r\n{body}",
        body.len()
    );
    let _ = socket.write_all(resp.as_bytes()).await;

    if let Some(err) = params.get("error") {
        return Err(format!("Google 授权失败：{err}"));
    }
    let code = params.get("code").cloned().ok_or("未获取到授权码")?;
    let state = params.get("state").cloned().unwrap_or_default();
    Ok((code, state))
}

/// 执行 `core/src/oauth.rs` 描述的 OAuth 请求。
async fn send_oauth(req: &oauth::OAuthRequest) -> Result<(i32, String), String> {
    let method = reqwest::Method::from_bytes(req.method.as_bytes())
        .map_err(|e| format!("请求方法无效：{e}"))?;
    let mut builder = http_client().request(method, &req.url);
    for (key, value) in &req.headers {
        builder = builder.header(key, value);
    }
    if let Some(body) = &req.body {
        builder = builder.body(body.clone());
    }
    let res = builder.send().await.map_err(|e| format!("网络请求失败：{e}"))?;
    let status = res.status().as_u16() as i32;
    let text = res.text().await.map_err(|e| format!("读取响应失败：{e}"))?;
    Ok((status, text))
}

async fn exchange_code(
    client_id: &str,
    client_secret: &str,
    code: &str,
    verifier: &str,
    redirect: &str,
) -> Result<Value, String> {
    let req = oauth::token_request(client_id, client_secret, code, verifier, redirect);
    let (status, body) = send_oauth(&req).await?;
    let token = oauth::parse_token(status, &body);
    if !token.get("ok").and_then(Value::as_bool).unwrap_or(false) {
        let msg = token
            .get("error")
            .and_then(Value::as_str)
            .unwrap_or("换取令牌失败")
            .to_string();
        eprintln!("[xiaoman-desktop] token error {status}: {msg}");
        if msg.contains("client_secret is missing") {
            return Err(
                "该客户端需要密钥：请在「数据同步 → 桌面端 Client Secret」中填写 Google Console 的客户端密钥"
                    .to_string(),
            );
        }
        return Err(msg);
    }
    Ok(token)
}

async fn fetch_email(access_token: &str) -> String {
    let req = oauth::userinfo_request(access_token);
    match send_oauth(&req).await {
        Ok((status, body)) => oauth::parse_email(status, &body),
        Err(_) => String::new(),
    }
}

/// 系统浏览器完成 Google 授权（PKCE + loopback）。Desktop 客户端仍需 client_secret。
#[tauri::command]
async fn google_login(
    app: tauri::AppHandle,
    client_id: String,
    client_secret: String,
) -> Result<Value, String> {
    let (client_id, client_secret) = if client_id.trim().is_empty() {
        load_credentials(&app)?.ok_or("当前构建未内置云同步凭据")?
    } else {
        (client_id.trim().to_string(), client_secret.trim().to_string())
    };
    if client_id.is_empty() {
        return Err("请先填写 OAuth Client ID".to_string());
    }
    let redirect = format!("http://127.0.0.1:{LOOPBACK_PORT}");
    let verifier = random_string(64);
    let challenge = oauth::pkce_challenge(&verifier);
    let state = random_string(24);
    let auth_url = oauth::auth_url(
        &client_id,
        &redirect,
        &challenge,
        &state,
        oauth::SCOPE,
        "select_account",
    );

    let listener = TcpListener::bind(("127.0.0.1", LOOPBACK_PORT))
        .await
        .map_err(|e| format!("无法监听本地端口 {LOOPBACK_PORT}：{e}"))?;
    eprintln!(
        "[xiaoman-desktop] google_login client={}… redirect={redirect}",
        &client_id[..client_id.len().min(24)]
    );
    match open::that(&auth_url) {
        Ok(()) => eprintln!("[xiaoman-desktop] opened system browser"),
        Err(e) => {
            eprintln!("[xiaoman-desktop] open browser failed: {e}");
            return Err(format!("无法打开浏览器：{e}"));
        }
    }
    let (code, got_state) = wait_for_code(listener).await?;
    eprintln!("[xiaoman-desktop] loopback callback received");
    if got_state != state {
        return Err("授权状态校验失败，请重试".to_string());
    }
    let token = exchange_code(&client_id, client_secret.trim(), &code, &verifier, &redirect).await?;
    eprintln!("[xiaoman-desktop] token exchange ok");
    if let Some(scope) = token.get("scope").and_then(Value::as_str) {
        // Google 正常会返回 scope；缺失时不要误报成“未授予 Drive 权限”。
        if !scope.is_empty() && !scope.contains("drive.appdata") {
            return Err(
                "未授予 Drive 权限：Google 授权页的 Drive 权限需要手动勾选后才能继续".to_string(),
            );
        }
    }
    let access = token
        .get("accessToken")
        .and_then(Value::as_str)
        .unwrap_or("")
        .to_string();
    if access.is_empty() {
        return Err("Google 未返回访问令牌".to_string());
    }
    let email = fetch_email(&access).await;
    let expires_in = token.get("expiresIn").and_then(Value::as_i64).unwrap_or(3600);
    Ok(json!({
        "email": email,
        "accessToken": access,
        "refreshToken": token.get("refreshToken").and_then(Value::as_str).unwrap_or(""),
        "expiresAt": now_ms() + expires_in.max(0) as u64 * 1000
    }))
}

/// access token 过期后用 refresh token 续期。
#[tauri::command]
async fn google_refresh(
    app: tauri::AppHandle,
    client_id: String,
    client_secret: String,
    refresh_token: String,
) -> Result<Value, String> {
    let (client_id, mut client_secret) = if client_id.trim().is_empty() {
        load_credentials(&app)?.ok_or("当前构建未内置云同步凭据")?
    } else {
        (client_id.trim().to_string(), client_secret.trim().to_string())
    };
    if client_secret.trim().is_empty() {
        if let Some((_, secret)) = load_credentials(&app)? {
            client_secret = secret;
        }
    }
    let req = oauth::refresh_request(&client_id, client_secret.trim(), &refresh_token);
    let (status, body) = send_oauth(&req).await?;
    let token = oauth::parse_token(status, &body);
    if !token.get("ok").and_then(Value::as_bool).unwrap_or(false) {
        return Err(token
            .get("error")
            .and_then(Value::as_str)
            .unwrap_or("刷新令牌失败")
            .to_string());
    }
    let access = token
        .get("accessToken")
        .and_then(Value::as_str)
        .unwrap_or("")
        .to_string();
    if access.is_empty() {
        return Err("Google 未返回访问令牌".to_string());
    }
    let expires_in = token.get("expiresIn").and_then(Value::as_i64).unwrap_or(3600);
    Ok(json!({
        "accessToken": access,
        "expiresAt": now_ms() + expires_in.max(0) as u64 * 1000
    }))
}

fn http_client() -> reqwest::Client {
    reqwest::Client::builder()
        .timeout(Duration::from_secs(60))
        .build()
        .unwrap_or_default()
}

async fn send(
    req: &cloud::CloudRequest,
    body: Option<Vec<u8>>,
) -> Result<(i32, Vec<u8>), String> {
    let method =
        reqwest::Method::from_bytes(req.method.as_bytes()).map_err(|e| format!("请求方法无效：{e}"))?;
    let mut builder = http_client().request(method, &req.url);
    for (key, value) in &req.headers {
        builder = builder.header(key, value);
    }
    if let Some(bytes) = body {
        builder = builder.body(bytes);
    }
    let res = builder.send().await.map_err(|e| format!("网络请求失败：{e}"))?;
    let status = res.status().as_u16() as i32;
    let bytes = res.bytes().await.map_err(|e| format!("读取响应失败：{e}"))?;
    Ok((status, bytes.to_vec()))
}

async fn find_file(token: &str) -> Result<Value, String> {
    let (status, bytes) = send(&cloud::find_request(token), None).await?;
    Ok(cloud::parse_file_list(status, &String::from_utf8_lossy(&bytes)))
}

/// 云端备份元数据：`{ok, file}` 或 `{ok:false, error, status}`。
#[tauri::command]
async fn cloud_file_info(access_token: String) -> Result<String, String> {
    Ok(find_file(&access_token).await?.to_string())
}

/// 上传 .ias 字节，返回与 Web 端一致的解析结果 JSON。
#[tauri::command]
async fn cloud_upload(access_token: String, bytes: Vec<u8>) -> Result<String, String> {
    let found = find_file(&access_token).await?;
    if !found.get("ok").and_then(Value::as_bool).unwrap_or(false) {
        return Ok(found.to_string());
    }
    let mut file_id = found
        .get("file")
        .and_then(|f| f.get("id"))
        .and_then(Value::as_str)
        .unwrap_or("")
        .to_string();

    if file_id.is_empty() {
        let create = cloud::create_request(&access_token);
        let (status, body) = send(&create, create.body.as_ref().map(|b| b.as_bytes().to_vec())).await?;
        let parsed = cloud::parse_file(status, &String::from_utf8_lossy(&body));
        if !parsed.get("ok").and_then(Value::as_bool).unwrap_or(false) {
            return Ok(parsed.to_string());
        }
        file_id = parsed
            .get("file")
            .and_then(|f| f.get("id"))
            .and_then(Value::as_str)
            .unwrap_or("")
            .to_string();
        if file_id.is_empty() {
            return Err("上传到 Drive 失败".to_string());
        }
    }

    let upload = cloud::upload_request(&access_token, &file_id);
    let (status, body) = send(&upload, Some(bytes)).await?;
    Ok(cloud::parse_file(status, &String::from_utf8_lossy(&body)).to_string())
}

/// 下载备份原始字节。
#[tauri::command]
async fn cloud_download(access_token: String, file_id: String) -> Result<Response, String> {
    let (status, bytes) = send(&cloud::download_request(&access_token, &file_id), None).await?;
    if !(200..300).contains(&status) {
        let parsed = cloud::parse_file(status, &String::from_utf8_lossy(&bytes));
        return Err(parsed
            .get("error")
            .and_then(Value::as_str)
            .unwrap_or("下载失败")
            .to_string());
    }
    Ok(Response::new(bytes))
}

/// 从菜单栏/托盘恢复主窗口。
fn show_window(app: &tauri::AppHandle) {
    if let Some(window) = app.get_webview_window("main") {
        let _ = window.show();
        let _ = window.set_focus();
    }
}

/// 关闭窗口时隐藏到菜单栏，而不是退出（Dock 图标保持不动）。
fn hide_to_tray(window: &tauri::Window) {
    let _ = window.hide();
}

/// 云备份账户（access/refresh token）存 macOS 钥匙串，不落 localStorage。
const KEYCHAIN_SERVICE: &str = "com.xiaoman.desktop";
const KEYCHAIN_ACCOUNT: &str = "google-cloud";

fn keychain_entry() -> Result<keyring::Entry, String> {
    keyring::Entry::new(KEYCHAIN_SERVICE, KEYCHAIN_ACCOUNT)
        .map_err(|e| format!("打开钥匙串失败：{e}"))
}

#[tauri::command]
fn cloud_account_load() -> Result<String, String> {
    match keychain_entry()?.get_password() {
        Ok(value) => Ok(value),
        Err(keyring::Error::NoEntry) => Ok(String::new()),
        Err(e) => Err(format!("读取钥匙串失败：{e}")),
    }
}

#[tauri::command]
fn cloud_account_save(json: String) -> Result<(), String> {
    keychain_entry()?
        .set_password(&json)
        .map_err(|e| format!("写入钥匙串失败：{e}"))
}

#[tauri::command]
fn cloud_account_clear() -> Result<(), String> {
    match keychain_entry()?.delete_credential() {
        Ok(()) => Ok(()),
        Err(keyring::Error::NoEntry) => Ok(()),
        Err(e) => Err(format!("清除钥匙串失败：{e}")),
    }
}

/// 下载安装完成后重启应用（由 updater 前端流程调用）。
#[tauri::command]
fn restart_app(app: tauri::AppHandle) {
    app.restart();
}

fn main() {
    tauri::Builder::default()
        .plugin(tauri_plugin_updater::Builder::new().build())
        .setup(|app| {
            let show = MenuItem::with_id(app, "show", "打开小满", true, None::<&str>)?;
            let quit = MenuItem::with_id(app, "quit", "退出", true, None::<&str>)?;
            let menu = Menu::with_items(app, &[&show, &quit])?;
            let icon = tauri::image::Image::from_bytes(include_bytes!("../icons/tray.png"))?;
            TrayIconBuilder::new()
                .icon(icon)
                .icon_as_template(true)
                .menu(&menu)
                .show_menu_on_left_click(false)
                .on_menu_event(|app, event| match event.id.as_ref() {
                    "show" => show_window(app),
                    "quit" => app.exit(0),
                    _ => {}
                })
                .on_tray_icon_event(|tray, event| {
                    if let TrayIconEvent::Click {
                        button: MouseButton::Left,
                        button_state: MouseButtonState::Up,
                        ..
                    } = event
                    {
                        show_window(tray.app_handle());
                    }
                })
                .build(app)?;
            Ok(())
        })
        .on_window_event(|window, event| {
            if let WindowEvent::CloseRequested { api, .. } = event {
                api.prevent_close();
                hide_to_tray(window);
            }
        })
        .invoke_handler(tauri::generate_handler![
            restart_app,
            cloud_account_load,
            cloud_account_save,
            cloud_account_clear,
            oauth_defaults,
            google_login,
            google_refresh,
            cloud_file_info,
            cloud_upload,
            cloud_download
        ])
        .build(tauri::generate_context!())
        .expect("error while building 小满")
        .run(|app, event| {
            // 关闭窗口后点 Dock 图标：macOS 会触发 Reopen。
            #[cfg(target_os = "macos")]
            if let tauri::RunEvent::Reopen {
                has_visible_windows, ..
            } = event
            {
                if !has_visible_windows {
                    show_window(app);
                }
            }
            #[cfg(not(target_os = "macos"))]
            let _ = (app, event);
        });
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn seeded_credentials_decrypt() {
        let path = PathBuf::from(env!("CARGO_MANIFEST_DIR")).join("oauth.enc");
        if !path.exists() {
            return; // 仓库不含凭据文件，跳过
        }
        let data = std::fs::read(path).unwrap();
        let (id, secret) = decrypt_credentials(&data).unwrap().expect("credentials present");
        assert!(id.contains("apps.googleusercontent.com"));
        assert!(!secret.is_empty());
    }

    #[test]
    fn rejects_short_file() {
        assert!(decrypt_credentials(&[0u8; 8]).is_err());
    }
}
