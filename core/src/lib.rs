//! 小满核心库：数据计算、备份编解码，后续加入品牌匹配与云同步。
//! Android 通过 JNI，Web 通过 wasm-bindgen，桌面端直接链接。

pub mod backup;
pub mod calc;
pub mod cloud;
pub mod update;

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
