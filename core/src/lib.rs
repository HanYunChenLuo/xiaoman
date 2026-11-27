//! 小满核心库：计算/趋势、备份编解码、状态规范化、云备份协议与 Google OAuth。
//! Android 通过 JNI，Web 通过 wasm-bindgen，桌面端直接链接。

pub mod backup;
pub mod brands;
pub mod calc;
pub mod cloud;
pub mod oauth;
pub mod state;
pub mod update;
mod util;

pub const VERSION: &str = "0.1.0";

pub fn core_version() -> &'static str {
    VERSION
}

#[cfg(target_os = "android")]
mod android {
    use crate::calc;
    use jni::objects::{JByteArray, JClass, JString};
    use jni::sys::{jbyteArray, jdouble, jint, jstring};
    use jni::JNIEnv;
    use serde_json::{from_str, Value};

    fn take_string(env: &mut JNIEnv, s: &JString) -> Result<String, ()> {
        env.get_string(s).map(|v| v.into()).map_err(|e| {
            let _ = env.throw_new("java/lang/Exception", e.to_string());
        })
    }

    fn parse(s: &str) -> Value {
        from_str(s).unwrap_or(Value::Object(Default::default()))
    }

    fn throw_null<T>(env: &mut JNIEnv, msg: impl ToString) -> T
    where
        T: From<*mut jni::sys::_jobject>,
    {
        let _ = env.throw_new("java/lang/Exception", msg.to_string());
        T::from(std::ptr::null_mut())
    }

    #[no_mangle]
    pub extern "system" fn Java_com_xiaoman_app_NativeCore_version(
        env: JNIEnv,
        _class: JClass,
    ) -> jstring {
        env.new_string(crate::core_version())
            .expect("version string")
            .into_raw()
    }

    #[no_mangle]
    pub extern "system" fn Java_com_xiaoman_app_NativeCore_decodeIas(
        mut env: JNIEnv,
        _class: JClass,
        arr: JByteArray,
    ) -> jstring {
        let bytes = match env.convert_byte_array(&arr) {
            Ok(b) => b,
            Err(e) => {
                let _ = env.throw_new("java/lang/Exception", e.to_string());
                return std::ptr::null_mut();
            }
        };
        match crate::backup::decode_ias(&bytes) {
            Ok(s) => env.new_string(s).unwrap().into_raw(),
            Err(e) => {
                let _ = env.throw_new("java/lang/Exception", e);
                std::ptr::null_mut()
            }
        }
    }

    #[no_mangle]
    pub extern "system" fn Java_com_xiaoman_app_NativeCore_encodeIas(
        mut env: JNIEnv,
        _class: JClass,
        payload: JString,
    ) -> jbyteArray {
        let text = match take_string(&mut env, &payload) {
            Ok(s) => s,
            Err(_) => return std::ptr::null_mut(),
        };
        match crate::backup::encode_ias(&text) {
            Ok(bytes) => match env.byte_array_from_slice(&bytes) {
                Ok(a) => a.into_raw(),
                Err(e) => {
                    let _ = env.throw_new("java/lang/Exception", e.to_string());
                    std::ptr::null_mut()
                }
            },
            Err(e) => {
                let _ = env.throw_new("java/lang/Exception", e);
                std::ptr::null_mut()
            }
        }
    }

    // ---------- .ias 语义映射（core/src/state.rs） ----------

    fn json_arg(env: &mut JNIEnv, s: &JString) -> Result<Value, String> {
        let text = take_string(env, s).map_err(|_| "无效参数".to_string())?;
        from_str(&text).map_err(|e| e.to_string())
    }

    fn ret_err(env: &mut JNIEnv, msg: &str) -> jstring {
        let _ = env.throw_new("java/lang/Exception", msg);
        std::ptr::null_mut()
    }

    #[no_mangle]
    pub extern "system" fn Java_com_xiaoman_app_NativeCore_backupExport(
        mut env: JNIEnv,
        _class: JClass,
        state: JString,
        images: JString,
        platform: JString,
        now_ms: jni::sys::jlong,
        tz_offset_ms: jni::sys::jlong,
    ) -> jstring {
        let state = match json_arg(&mut env, &state) {
            Ok(v) => v,
            Err(e) => return ret_err(&mut env, &e),
        };
        let images = match json_arg(&mut env, &images) {
            Ok(v) => v,
            Err(e) => return ret_err(&mut env, &e),
        };
        let Ok(platform) = take_string(&mut env, &platform) else {
            return ret_err(&mut env, "无效参数");
        };
        match crate::state::to_payload(&state, &images, &platform, now_ms, tz_offset_ms) {
            Ok(v) => ret_string(&mut env, v.to_string()),
            Err(e) => ret_err(&mut env, &e),
        }
    }

    #[no_mangle]
    pub extern "system" fn Java_com_xiaoman_app_NativeCore_backupImport(
        mut env: JNIEnv,
        _class: JClass,
        payload: JString,
        now_ms: jni::sys::jlong,
        tz_offset_ms: jni::sys::jlong,
    ) -> jstring {
        let payload = match json_arg(&mut env, &payload) {
            Ok(v) => v,
            Err(e) => return ret_err(&mut env, &e),
        };
        match crate::state::from_backup(&payload, now_ms, tz_offset_ms) {
            Ok(v) => ret_string(&mut env, v.to_string()),
            Err(e) => ret_err(&mut env, &e),
        }
    }

    #[no_mangle]
    pub extern "system" fn Java_com_xiaoman_app_NativeCore_backupMerge(
        mut env: JNIEnv,
        _class: JClass,
        local: JString,
        incoming: JString,
        replace: jni::sys::jboolean,
        prefer_incoming: jni::sys::jboolean,
    ) -> jstring {
        let local = match json_arg(&mut env, &local) {
            Ok(v) => v,
            Err(e) => return ret_err(&mut env, &e),
        };
        let incoming = match json_arg(&mut env, &incoming) {
            Ok(v) => v,
            Err(e) => return ret_err(&mut env, &e),
        };
        match crate::state::merge(&local, &incoming, replace != 0, prefer_incoming != 0) {
            Ok(v) => ret_string(&mut env, v.to_string()),
            Err(e) => ret_err(&mut env, &e),
        }
    }

    #[no_mangle]
    pub extern "system" fn Java_com_xiaoman_app_NativeCore_backupTimeMs(
        mut env: JNIEnv,
        _class: JClass,
        payload: JString,
    ) -> jni::sys::jlong {
        let payload = match json_arg(&mut env, &payload) {
            Ok(v) => v,
            Err(e) => {
                let _ = env.throw_new("java/lang/Exception", e);
                return 0;
            }
        };
        crate::state::backup_time_ms(&payload)
    }

    #[no_mangle]
    pub extern "system" fn Java_com_xiaoman_app_NativeCore_stateNormalize(
        mut env: JNIEnv,
        _class: JClass,
        state: JString,
        defaults: JString,
    ) -> jstring {
        let state = match json_arg(&mut env, &state) {
            Ok(v) => v,
            Err(e) => return ret_err(&mut env, &e),
        };
        let defaults = match json_arg(&mut env, &defaults) {
            Ok(v) => v,
            Err(e) => return ret_err(&mut env, &e),
        };
        ret_string(&mut env, crate::state::normalize(&state, &defaults).to_string())
    }

    #[no_mangle]
    pub extern "system" fn Java_com_xiaoman_app_NativeCore_todayString(
        mut env: JNIEnv,
        _class: JClass,
        now_ms: jni::sys::jlong,
        tz_offset_ms: jni::sys::jlong,
    ) -> jstring {
        ret_string(&mut env, calc::today_string(now_ms, tz_offset_ms))
    }

    #[no_mangle]
    pub extern "system" fn Java_com_xiaoman_app_NativeCore_trendSeries(
        mut env: JNIEnv,
        _class: JClass,
        assets: JString,
        days: jni::sys::jint,
        now_ms: jni::sys::jlong,
        tz_offset_ms: jni::sys::jlong,
    ) -> jstring {
        let Ok(assets) = take_string(&mut env, &assets) else {
            return std::ptr::null_mut();
        };
        let values = calc::trend_series(&parse(&assets), days, now_ms, tz_offset_ms);
        ret_string(&mut env, to_json_string(&values))
    }

    #[no_mangle]
    pub extern "system" fn Java_com_xiaoman_app_NativeCore_categoryDistribution(
        mut env: JNIEnv,
        _class: JClass,
        assets: JString,
        categories: JString,
    ) -> jstring {
        let Ok(assets) = take_string(&mut env, &assets) else {
            return std::ptr::null_mut();
        };
        let Ok(categories) = take_string(&mut env, &categories) else {
            return std::ptr::null_mut();
        };
        let out = calc::category_distribution(&parse(&assets), &parse(&categories));
        ret_string(&mut env, out.to_string())
    }

    #[no_mangle]
    pub extern "system" fn Java_com_xiaoman_app_NativeCore_incomeTotal(
        mut env: JNIEnv,
        _class: JClass,
        asset: JString,
    ) -> jdouble {
        let Ok(asset) = take_string(&mut env, &asset) else { return 0.0 };
        calc::income_total(&parse(&asset))
    }

    #[no_mangle]
    pub extern "system" fn Java_com_xiaoman_app_NativeCore_latestPrice(
        mut env: JNIEnv,
        _class: JClass,
        asset: JString,
    ) -> jstring {
        let Ok(asset) = take_string(&mut env, &asset) else {
            return std::ptr::null_mut();
        };
        ret_string(&mut env, calc::latest_price(&parse(&asset)).to_string())
    }

    #[no_mangle]
    pub extern "system" fn Java_com_xiaoman_app_NativeCore_statusText(
        mut env: JNIEnv,
        _class: JClass,
        status: JString,
    ) -> jstring {
        let Ok(status) = take_string(&mut env, &status) else {
            return std::ptr::null_mut();
        };
        ret_string(&mut env, calc::status_text(&status).to_string())
    }

    #[no_mangle]
    pub extern "system" fn Java_com_xiaoman_app_NativeCore_displayName(
        mut env: JNIEnv,
        _class: JClass,
        asset: JString,
    ) -> jstring {
        let Ok(asset) = take_string(&mut env, &asset) else {
            return std::ptr::null_mut();
        };
        ret_string(&mut env, calc::display_name(&parse(&asset)))
    }

    #[no_mangle]
    pub extern "system" fn Java_com_xiaoman_app_NativeCore_newId(
        mut env: JNIEnv,
        _class: JClass,
        prefix: JString,
        now_ms: jni::sys::jlong,
        entropy: jni::sys::jlong,
    ) -> jstring {
        let Ok(prefix) = take_string(&mut env, &prefix) else {
            return std::ptr::null_mut();
        };
        ret_string(&mut env, calc::new_id(&prefix, now_ms, entropy))
    }

    #[no_mangle]
    pub extern "system" fn Java_com_xiaoman_app_NativeCore_daysBetween(
        mut env: JNIEnv,
        _class: JClass,
        from: JString,
        to: JString,
    ) -> jint {
        let Ok(a) = take_string(&mut env, &from) else { return 1 };
        let Ok(b) = take_string(&mut env, &to) else { return 1 };
        calc::days_between(&a, &b)
    }

    #[no_mangle]
    pub extern "system" fn Java_com_xiaoman_app_NativeCore_ageDays(
        mut env: JNIEnv,
        _class: JClass,
        asset: JString,
        today: JString,
    ) -> jint {
        let Ok(a) = take_string(&mut env, &asset) else { return 1 };
        let Ok(t) = take_string(&mut env, &today) else { return 1 };
        calc::age_days(&parse(&a), &t)
    }

    #[no_mangle]
    pub extern "system" fn Java_com_xiaoman_app_NativeCore_usageCount(
        mut env: JNIEnv,
        _class: JClass,
        asset: JString,
    ) -> jint {
        let Ok(a) = take_string(&mut env, &asset) else { return 1 };
        calc::usage_count(&parse(&a))
    }

    #[no_mangle]
    pub extern "system" fn Java_com_xiaoman_app_NativeCore_netCost(
        mut env: JNIEnv,
        _class: JClass,
        asset: JString,
    ) -> jdouble {
        let Ok(a) = take_string(&mut env, &asset) else { return 0.0 };
        calc::net_cost(&parse(&a))
    }

    #[no_mangle]
    pub extern "system" fn Java_com_xiaoman_app_NativeCore_dailyCost(
        mut env: JNIEnv,
        _class: JClass,
        asset: JString,
        now_ms: jni::sys::jlong,
        tz_offset_ms: jni::sys::jlong,
    ) -> jdouble {
        let Ok(a) = take_string(&mut env, &asset) else { return 0.0 };
        calc::daily_cost(&parse(&a), now_ms, tz_offset_ms)
    }

    #[no_mangle]
    pub extern "system" fn Java_com_xiaoman_app_NativeCore_totalOfKind(
        mut env: JNIEnv,
        _class: JClass,
        assets: JString,
        kind: JString,
    ) -> jdouble {
        let Ok(a) = take_string(&mut env, &assets) else { return 0.0 };
        let Ok(k) = take_string(&mut env, &kind) else { return 0.0 };
        calc::total(&parse(&a), &k)
    }

    #[no_mangle]
    pub extern "system" fn Java_com_xiaoman_app_NativeCore_dailyOfKind(
        mut env: JNIEnv,
        _class: JClass,
        assets: JString,
        kind: JString,
        now_ms: jni::sys::jlong,
        tz_offset_ms: jni::sys::jlong,
    ) -> jdouble {
        let Ok(a) = take_string(&mut env, &assets) else { return 0.0 };
        let Ok(k) = take_string(&mut env, &kind) else { return 0.0 };
        calc::daily(&parse(&a), &k, now_ms, tz_offset_ms)
    }

    #[no_mangle]
    pub extern "system" fn Java_com_xiaoman_app_NativeCore_countOfKind(
        mut env: JNIEnv,
        _class: JClass,
        assets: JString,
        kind: JString,
        status: JString,
    ) -> jint {
        let Ok(a) = take_string(&mut env, &assets) else { return 0 };
        let Ok(k) = take_string(&mut env, &kind) else { return 0 };
        let Ok(s) = take_string(&mut env, &status) else { return 0 };
        calc::count(&parse(&a), &k, &s)
    }

    // ---------- 云备份协议 ----------

    fn to_json_string<T: serde::Serialize>(value: &T) -> String {
        serde_json::to_string(value).unwrap_or_else(|_| "{}".to_string())
    }

    fn ret_string(env: &mut JNIEnv, text: String) -> jstring {
        match env.new_string(text) {
            Ok(s) => s.into_raw(),
            Err(e) => {
                let _ = env.throw_new("java/lang/Exception", e.to_string());
                std::ptr::null_mut()
            }
        }
    }

    #[no_mangle]
    pub extern "system" fn Java_com_xiaoman_app_NativeCore_cloudBackupName(
        mut env: JNIEnv,
        _class: JClass,
    ) -> jstring {
        ret_string(&mut env, crate::cloud::backup_name().to_string())
    }

    #[no_mangle]
    pub extern "system" fn Java_com_xiaoman_app_NativeCore_cloudFindRequest(
        mut env: JNIEnv,
        _class: JClass,
        token: JString,
    ) -> jstring {
        let Ok(token) = take_string(&mut env, &token) else { return std::ptr::null_mut() };
        let req = crate::cloud::find_request(&token);
        ret_string(&mut env, to_json_string(&req))
    }

    #[no_mangle]
    pub extern "system" fn Java_com_xiaoman_app_NativeCore_cloudCreateRequest(
        mut env: JNIEnv,
        _class: JClass,
        token: JString,
    ) -> jstring {
        let Ok(token) = take_string(&mut env, &token) else { return std::ptr::null_mut() };
        let req = crate::cloud::create_request(&token);
        ret_string(&mut env, to_json_string(&req))
    }

    #[no_mangle]
    pub extern "system" fn Java_com_xiaoman_app_NativeCore_cloudUploadRequest(
        mut env: JNIEnv,
        _class: JClass,
        token: JString,
        file_id: JString,
    ) -> jstring {
        let Ok(token) = take_string(&mut env, &token) else { return std::ptr::null_mut() };
        let Ok(id) = take_string(&mut env, &file_id) else { return std::ptr::null_mut() };
        let req = crate::cloud::upload_request(&token, &id);
        ret_string(&mut env, to_json_string(&req))
    }

    #[no_mangle]
    pub extern "system" fn Java_com_xiaoman_app_NativeCore_cloudDownloadRequest(
        mut env: JNIEnv,
        _class: JClass,
        token: JString,
        file_id: JString,
    ) -> jstring {
        let Ok(token) = take_string(&mut env, &token) else { return std::ptr::null_mut() };
        let Ok(id) = take_string(&mut env, &file_id) else { return std::ptr::null_mut() };
        let req = crate::cloud::download_request(&token, &id);
        ret_string(&mut env, to_json_string(&req))
    }

    #[no_mangle]
    pub extern "system" fn Java_com_xiaoman_app_NativeCore_cloudParseFileList(
        mut env: JNIEnv,
        _class: JClass,
        status: jint,
        body: JString,
    ) -> jstring {
        let Ok(body) = take_string(&mut env, &body) else { return std::ptr::null_mut() };
        let out = crate::cloud::parse_file_list(status, &body);
        ret_string(&mut env, out.to_string())
    }

    #[no_mangle]
    pub extern "system" fn Java_com_xiaoman_app_NativeCore_cloudParseFile(
        mut env: JNIEnv,
        _class: JClass,
        status: jint,
        body: JString,
    ) -> jstring {
        let Ok(body) = take_string(&mut env, &body) else { return std::ptr::null_mut() };
        let out = crate::cloud::parse_file(status, &body);
        ret_string(&mut env, out.to_string())
    }

    #[no_mangle]
    pub extern "system" fn Java_com_xiaoman_app_NativeCore_cloudTimeMs(
        mut env: JNIEnv,
        _class: JClass,
        iso: JString,
    ) -> jni::sys::jlong {
        let Ok(iso) = take_string(&mut env, &iso) else { return 0 };
        crate::cloud::time_ms(&iso)
    }

    #[no_mangle]
    pub extern "system" fn Java_com_xiaoman_app_NativeCore_cloudConflict(
        mut env: JNIEnv,
        _class: JClass,
        remote_iso: JString,
        local_ms: jni::sys::jlong,
        last_remote_ms: jni::sys::jlong,
    ) -> jstring {
        let Ok(iso) = take_string(&mut env, &remote_iso) else {
            return std::ptr::null_mut();
        };
        let out = crate::cloud::conflict(&iso, local_ms, last_remote_ms);
        ret_string(&mut env, out.to_string())
    }

    // ---------- 品牌/型号/图标（core/src/brands.rs） ----------

    #[no_mangle]
    pub extern "system" fn Java_com_xiaoman_app_NativeCore_brandsParse(
        mut env: JNIEnv,
        _class: JClass,
        json: JString,
    ) -> jstring {
        let Ok(json) = take_string(&mut env, &json) else {
            return std::ptr::null_mut();
        };
        ret_string(&mut env, crate::brands::parse(&parse(&json)).to_string())
    }

    #[no_mangle]
    pub extern "system" fn Java_com_xiaoman_app_NativeCore_brandsMatch(
        mut env: JNIEnv,
        _class: JClass,
        text: JString,
        aliases: JString,
    ) -> jstring {
        let Ok(text) = take_string(&mut env, &text) else {
            return std::ptr::null_mut();
        };
        let Ok(aliases) = take_string(&mut env, &aliases) else {
            return std::ptr::null_mut();
        };
        let out = crate::brands::match_brand(&text, &parse(&aliases));
        ret_string(&mut env, out.to_string())
    }

    #[no_mangle]
    pub extern "system" fn Java_com_xiaoman_app_NativeCore_brandIconKey(
        mut env: JNIEnv,
        _class: JClass,
        text: JString,
    ) -> jstring {
        let Ok(text) = take_string(&mut env, &text) else {
            return std::ptr::null_mut();
        };
        ret_string(&mut env, crate::brands::icon_key(&text).to_string())
    }

    #[no_mangle]
    pub extern "system" fn Java_com_xiaoman_app_NativeCore_brandsValidate(
        mut env: JNIEnv,
        _class: JClass,
        json: JString,
        app_version_code: jint,
    ) -> jstring {
        let Ok(json) = take_string(&mut env, &json) else {
            return std::ptr::null_mut();
        };
        let out = crate::brands::validate(&parse(&json), app_version_code as i64);
        ret_string(&mut env, out.to_string())
    }

    #[no_mangle]
    pub extern "system" fn Java_com_xiaoman_app_NativeCore_brandsApiUrl(
        mut env: JNIEnv,
        _class: JClass,
    ) -> jstring {
        ret_string(&mut env, crate::brands::api_url().to_string())
    }

    #[no_mangle]
    pub extern "system" fn Java_com_xiaoman_app_NativeCore_brandsRawUrl(
        mut env: JNIEnv,
        _class: JClass,
    ) -> jstring {
        ret_string(&mut env, crate::brands::raw_url().to_string())
    }

    // ---------- Google OAuth（core/src/oauth.rs） ----------

    #[no_mangle]
    pub extern "system" fn Java_com_xiaoman_app_NativeCore_oauthPkceChallenge(
        mut env: JNIEnv,
        _class: JClass,
        verifier: JString,
    ) -> jstring {
        let Ok(verifier) = take_string(&mut env, &verifier) else {
            return std::ptr::null_mut();
        };
        ret_string(&mut env, crate::oauth::pkce_challenge(&verifier))
    }

    #[no_mangle]
    pub extern "system" fn Java_com_xiaoman_app_NativeCore_oauthAuthUrl(
        mut env: JNIEnv,
        _class: JClass,
        client_id: JString,
        redirect_uri: JString,
        challenge: JString,
        state: JString,
        scope: JString,
        prompt: JString,
    ) -> jstring {
        let (Ok(client_id), Ok(redirect_uri), Ok(challenge), Ok(state), Ok(scope), Ok(prompt)) = (
            take_string(&mut env, &client_id),
            take_string(&mut env, &redirect_uri),
            take_string(&mut env, &challenge),
            take_string(&mut env, &state),
            take_string(&mut env, &scope),
            take_string(&mut env, &prompt),
        ) else {
            return std::ptr::null_mut();
        };
        ret_string(
            &mut env,
            crate::oauth::auth_url(&client_id, &redirect_uri, &challenge, &state, &scope, &prompt),
        )
    }

    #[no_mangle]
    pub extern "system" fn Java_com_xiaoman_app_NativeCore_oauthTokenRequest(
        mut env: JNIEnv,
        _class: JClass,
        client_id: JString,
        client_secret: JString,
        code: JString,
        verifier: JString,
        redirect_uri: JString,
    ) -> jstring {
        let (Ok(client_id), Ok(client_secret), Ok(code), Ok(verifier), Ok(redirect_uri)) = (
            take_string(&mut env, &client_id),
            take_string(&mut env, &client_secret),
            take_string(&mut env, &code),
            take_string(&mut env, &verifier),
            take_string(&mut env, &redirect_uri),
        ) else {
            return std::ptr::null_mut();
        };
        let req = crate::oauth::token_request(&client_id, &client_secret, &code, &verifier, &redirect_uri);
        ret_string(&mut env, to_json_string(&req))
    }

    #[no_mangle]
    pub extern "system" fn Java_com_xiaoman_app_NativeCore_oauthRefreshRequest(
        mut env: JNIEnv,
        _class: JClass,
        client_id: JString,
        client_secret: JString,
        refresh_token: JString,
    ) -> jstring {
        let (Ok(client_id), Ok(client_secret), Ok(refresh_token)) = (
            take_string(&mut env, &client_id),
            take_string(&mut env, &client_secret),
            take_string(&mut env, &refresh_token),
        ) else {
            return std::ptr::null_mut();
        };
        let req = crate::oauth::refresh_request(&client_id, &client_secret, &refresh_token);
        ret_string(&mut env, to_json_string(&req))
    }

    #[no_mangle]
    pub extern "system" fn Java_com_xiaoman_app_NativeCore_oauthParseToken(
        mut env: JNIEnv,
        _class: JClass,
        status: jint,
        body: JString,
    ) -> jstring {
        let Ok(body) = take_string(&mut env, &body) else {
            return std::ptr::null_mut();
        };
        ret_string(&mut env, crate::oauth::parse_token(status, &body).to_string())
    }

    #[no_mangle]
    pub extern "system" fn Java_com_xiaoman_app_NativeCore_oauthUserinfoRequest(
        mut env: JNIEnv,
        _class: JClass,
        token: JString,
    ) -> jstring {
        let Ok(token) = take_string(&mut env, &token) else {
            return std::ptr::null_mut();
        };
        ret_string(&mut env, to_json_string(&crate::oauth::userinfo_request(&token)))
    }

    #[no_mangle]
    pub extern "system" fn Java_com_xiaoman_app_NativeCore_oauthParseEmail(
        mut env: JNIEnv,
        _class: JClass,
        status: jint,
        body: JString,
    ) -> jstring {
        let Ok(body) = take_string(&mut env, &body) else {
            return std::ptr::null_mut();
        };
        ret_string(&mut env, crate::oauth::parse_email(status, &body))
    }

    // ---------- 更新检查协议 ----------

    #[no_mangle]
    pub extern "system" fn Java_com_xiaoman_app_NativeCore_updateApiUrl(
        mut env: JNIEnv,
        _class: JClass,
    ) -> jstring {
        ret_string(&mut env, crate::update::release_api_url())
    }

    #[no_mangle]
    pub extern "system" fn Java_com_xiaoman_app_NativeCore_updateReleasePage(
        mut env: JNIEnv,
        _class: JClass,
    ) -> jstring {
        ret_string(&mut env, crate::update::release_page_url())
    }

    #[no_mangle]
    pub extern "system" fn Java_com_xiaoman_app_NativeCore_updateParseRelease(
        mut env: JNIEnv,
        _class: JClass,
        status: jint,
        body: JString,
    ) -> jstring {
        let Ok(body) = take_string(&mut env, &body) else { return std::ptr::null_mut() };
        let out = crate::update::parse_release(status, &body);
        ret_string(&mut env, out.to_string())
    }

    #[no_mangle]
    pub extern "system" fn Java_com_xiaoman_app_NativeCore_updateIsNewer(
        mut env: JNIEnv,
        _class: JClass,
        current: JString,
        latest: JString,
    ) -> jni::sys::jboolean {
        let Ok(cur) = take_string(&mut env, &current) else { return 0 };
        let Ok(new) = take_string(&mut env, &latest) else { return 0 };
        crate::update::is_newer(&cur, &new) as jni::sys::jboolean
    }

    #[no_mangle]
    pub extern "system" fn Java_com_xiaoman_app_NativeCore_updatePickAsset(
        mut env: JNIEnv,
        _class: JClass,
        assets: JString,
        suffix: JString,
    ) -> jstring {
        let Ok(a) = take_string(&mut env, &assets) else { return std::ptr::null_mut() };
        let Ok(s) = take_string(&mut env, &suffix) else { return std::ptr::null_mut() };
        ret_string(&mut env, crate::update::pick_asset(&a, &s).to_string())
    }
}

#[cfg(target_arch = "wasm32")]
mod wasm {
    use wasm_bindgen::prelude::*;

    #[wasm_bindgen]
    pub fn version() -> String {
        crate::core_version().to_string()
    }

    #[wasm_bindgen]
    pub fn decode_ias(bytes: &[u8]) -> Result<String, JsValue> {
        crate::backup::decode_ias(bytes).map_err(|e| JsValue::from_str(&e))
    }

    #[wasm_bindgen]
    pub fn encode_ias(payload: &str) -> Result<Vec<u8>, JsValue> {
        crate::backup::encode_ias(payload).map_err(|e| JsValue::from_str(&e))
    }

    // ---------- .ias 语义映射（core/src/state.rs） ----------

    fn js_json(text: &str) -> Result<serde_json::Value, JsValue> {
        serde_json::from_str(text).map_err(|e| JsValue::from_str(&e.to_string()))
    }

    #[wasm_bindgen]
    pub fn backup_export(
        state: &str,
        images: &str,
        platform: &str,
        now_ms: f64,
        tz_offset_ms: f64,
    ) -> Result<String, JsValue> {
        let state = js_json(state)?;
        let images = js_json(images)?;
        crate::state::to_payload(&state, &images, platform, now_ms as i64, tz_offset_ms as i64)
            .map(|v| v.to_string())
            .map_err(|e| JsValue::from_str(&e))
    }

    #[wasm_bindgen]
    pub fn backup_import(payload: &str, now_ms: f64, tz_offset_ms: f64) -> Result<String, JsValue> {
        let payload = js_json(payload)?;
        crate::state::from_backup(&payload, now_ms as i64, tz_offset_ms as i64)
            .map(|v| v.to_string())
            .map_err(|e| JsValue::from_str(&e))
    }

    #[wasm_bindgen]
    pub fn backup_merge(
        local: &str,
        incoming: &str,
        replace: bool,
        prefer_incoming: bool,
    ) -> Result<String, JsValue> {
        let local = js_json(local)?;
        let incoming = js_json(incoming)?;
        crate::state::merge(&local, &incoming, replace, prefer_incoming)
            .map(|v| v.to_string())
            .map_err(|e| JsValue::from_str(&e))
    }

    #[wasm_bindgen]
    pub fn backup_time_ms(payload: &str) -> f64 {
        js_json(payload)
            .map(|v| crate::state::backup_time_ms(&v) as f64)
            .unwrap_or(0.0)
    }

    #[wasm_bindgen]
    pub fn days_between(from: &str, to: &str) -> i32 {
        crate::calc::days_between(from, to)
    }

    fn parse(s: &str) -> serde_json::Value {
        serde_json::from_str(s).unwrap_or(serde_json::Value::Null)
    }

    #[wasm_bindgen]
    pub fn age_days(asset_json: &str, today: &str) -> i32 {
        crate::calc::age_days(&parse(asset_json), today)
    }

    #[wasm_bindgen]
    pub fn usage_count(asset_json: &str) -> i32 {
        crate::calc::usage_count(&parse(asset_json))
    }

    #[wasm_bindgen]
    pub fn net_cost(asset_json: &str) -> f64 {
        crate::calc::net_cost(&parse(asset_json))
    }

    #[wasm_bindgen]
    pub fn daily_cost(asset_json: &str, now_ms: f64, tz_offset_ms: f64) -> f64 {
        crate::calc::daily_cost(&parse(asset_json), now_ms as i64, tz_offset_ms as i64)
    }

    #[wasm_bindgen]
    pub fn total_of_kind(assets_json: &str, kind: &str) -> f64 {
        crate::calc::total(&parse(assets_json), kind)
    }

    #[wasm_bindgen]
    pub fn daily_of_kind(assets_json: &str, kind: &str, now_ms: f64, tz_offset_ms: f64) -> f64 {
        crate::calc::daily(&parse(assets_json), kind, now_ms as i64, tz_offset_ms as i64)
    }

    #[wasm_bindgen]
    pub fn count_of_kind(assets_json: &str, kind: &str, status: &str) -> i32 {
        crate::calc::count(&parse(assets_json), kind, status)
    }

    #[wasm_bindgen]
    pub fn today_string(now_ms: f64, tz_offset_ms: f64) -> String {
        crate::calc::today_string(now_ms as i64, tz_offset_ms as i64)
    }

    #[wasm_bindgen]
    pub fn trend_series(assets_json: &str, days: i32, now_ms: f64, tz_offset_ms: f64) -> String {
        let values = crate::calc::trend_series(
            &parse(assets_json),
            days,
            now_ms as i64,
            tz_offset_ms as i64,
        );
        serde_json::to_string(&values).unwrap_or_else(|_| "[]".to_string())
    }

    #[wasm_bindgen]
    pub fn category_distribution(assets_json: &str, categories_json: &str) -> String {
        crate::calc::category_distribution(&parse(assets_json), &parse(categories_json)).to_string()
    }

    #[wasm_bindgen]
    pub fn income_total(asset_json: &str) -> f64 {
        crate::calc::income_total(&parse(asset_json))
    }

    #[wasm_bindgen]
    pub fn latest_price(asset_json: &str) -> String {
        crate::calc::latest_price(&parse(asset_json)).to_string()
    }

    #[wasm_bindgen]
    pub fn status_text(status: &str) -> String {
        crate::calc::status_text(status).to_string()
    }

    #[wasm_bindgen]
    pub fn display_name(asset_json: &str) -> String {
        crate::calc::display_name(&parse(asset_json))
    }

    #[wasm_bindgen]
    pub fn new_id(prefix: &str, now_ms: f64, entropy: f64) -> String {
        crate::calc::new_id(prefix, now_ms as i64, entropy as i64)
    }

    #[wasm_bindgen]
    pub fn state_normalize(state_json: &str, defaults_json: &str) -> Result<String, JsValue> {
        let state = js_json(state_json)?;
        let defaults = js_json(defaults_json)?;
        Ok(crate::state::normalize(&state, &defaults).to_string())
    }

    // ---------- 云备份协议 ----------

    fn json_of<T: serde::Serialize>(value: &T) -> String {
        serde_json::to_string(value).unwrap_or_else(|_| "{}".to_string())
    }

    #[wasm_bindgen]
    pub fn cloud_backup_name() -> String {
        crate::cloud::backup_name().to_string()
    }

    #[wasm_bindgen]
    pub fn cloud_find_request(token: &str) -> String {
        json_of(&crate::cloud::find_request(token))
    }

    #[wasm_bindgen]
    pub fn cloud_create_request(token: &str) -> String {
        json_of(&crate::cloud::create_request(token))
    }

    #[wasm_bindgen]
    pub fn cloud_upload_request(token: &str, file_id: &str) -> String {
        json_of(&crate::cloud::upload_request(token, file_id))
    }

    #[wasm_bindgen]
    pub fn cloud_download_request(token: &str, file_id: &str) -> String {
        json_of(&crate::cloud::download_request(token, file_id))
    }

    #[wasm_bindgen]
    pub fn cloud_parse_file_list(status: i32, body: &str) -> String {
        crate::cloud::parse_file_list(status, body).to_string()
    }

    #[wasm_bindgen]
    pub fn cloud_parse_file(status: i32, body: &str) -> String {
        crate::cloud::parse_file(status, body).to_string()
    }

    #[wasm_bindgen]
    pub fn cloud_time_ms(iso: &str) -> f64 {
        crate::cloud::time_ms(iso) as f64
    }

    #[wasm_bindgen]
    pub fn cloud_conflict(remote_iso: &str, local_ms: f64, last_remote_ms: f64) -> String {
        crate::cloud::conflict(remote_iso, local_ms as i64, last_remote_ms as i64).to_string()
    }

    // ---------- 品牌/型号/图标 ----------

    #[wasm_bindgen]
    pub fn brands_parse(json: &str) -> String {
        crate::brands::parse(&parse(json)).to_string()
    }

    #[wasm_bindgen]
    pub fn brands_match(text: &str, aliases_json: &str) -> String {
        crate::brands::match_brand(text, &parse(aliases_json)).to_string()
    }

    #[wasm_bindgen]
    pub fn brand_icon_key(text: &str) -> String {
        crate::brands::icon_key(text).to_string()
    }

    #[wasm_bindgen]
    pub fn brands_validate(json: &str, app_version_code: i32) -> String {
        crate::brands::validate(&parse(json), app_version_code as i64).to_string()
    }

    #[wasm_bindgen]
    pub fn brands_api_url() -> String {
        crate::brands::api_url().to_string()
    }

    #[wasm_bindgen]
    pub fn brands_raw_url() -> String {
        crate::brands::raw_url().to_string()
    }

    // ---------- Google OAuth ----------

    #[wasm_bindgen]
    pub fn oauth_pkce_challenge(verifier: &str) -> String {
        crate::oauth::pkce_challenge(verifier)
    }

    #[wasm_bindgen]
    pub fn oauth_auth_url(
        client_id: &str,
        redirect_uri: &str,
        challenge: &str,
        state: &str,
        scope: &str,
        prompt: &str,
    ) -> String {
        crate::oauth::auth_url(client_id, redirect_uri, challenge, state, scope, prompt)
    }

    #[wasm_bindgen]
    pub fn oauth_token_request(
        client_id: &str,
        client_secret: &str,
        code: &str,
        verifier: &str,
        redirect_uri: &str,
    ) -> String {
        json_of(&crate::oauth::token_request(
            client_id,
            client_secret,
            code,
            verifier,
            redirect_uri,
        ))
    }

    #[wasm_bindgen]
    pub fn oauth_refresh_request(
        client_id: &str,
        client_secret: &str,
        refresh_token: &str,
    ) -> String {
        json_of(&crate::oauth::refresh_request(client_id, client_secret, refresh_token))
    }

    #[wasm_bindgen]
    pub fn oauth_parse_token(status: i32, body: &str) -> String {
        crate::oauth::parse_token(status, body).to_string()
    }

    #[wasm_bindgen]
    pub fn oauth_userinfo_request(token: &str) -> String {
        json_of(&crate::oauth::userinfo_request(token))
    }

    #[wasm_bindgen]
    pub fn oauth_parse_email(status: i32, body: &str) -> String {
        crate::oauth::parse_email(status, body)
    }

    // ---------- 更新检查协议 ----------

    #[wasm_bindgen]
    pub fn update_api_url() -> String {
        crate::update::release_api_url()
    }

    #[wasm_bindgen]
    pub fn update_release_page() -> String {
        crate::update::release_page_url()
    }

    #[wasm_bindgen]
    pub fn update_parse_release(status: i32, body: &str) -> String {
        crate::update::parse_release(status, body).to_string()
    }

    #[wasm_bindgen]
    pub fn update_is_newer(current: &str, latest: &str) -> bool {
        crate::update::is_newer(current, latest)
    }

    #[wasm_bindgen]
    pub fn update_pick_asset(assets_json: &str, suffix: &str) -> String {
        crate::update::pick_asset(assets_json, suffix).to_string()
    }
}
