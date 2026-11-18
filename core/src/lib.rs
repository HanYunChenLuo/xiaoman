//! 小满核心库：数据计算、备份编解码，后续加入品牌匹配与云同步。
//! Android 通过 JNI，Web 通过 wasm-bindgen，桌面端直接链接。

pub mod backup;
pub mod calc;

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
}
