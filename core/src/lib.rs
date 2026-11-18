//! 小满核心库：数据模型、备份编解码、品牌匹配与云同步逻辑。
//! Android 通过 JNI 调用，Web 通过 wasm-bindgen 调用，桌面端直接链接。

pub const VERSION: &str = "0.1.0";

pub fn core_version() -> &'static str {
    VERSION
}

#[cfg(target_os = "android")]
mod android {
    use jni::objects::JClass;
    use jni::sys::jstring;
    use jni::JNIEnv;

    #[no_mangle]
    pub extern "system" fn Java_com_xiaoman_app_NativeCore_version(
        env: JNIEnv,
        _class: JClass,
    ) -> jstring {
        env.new_string(crate::core_version())
            .expect("version string")
            .into_raw()
    }
}

#[cfg(target_arch = "wasm32")]
mod wasm {
    use wasm_bindgen::prelude::*;

    #[wasm_bindgen]
    pub fn version() -> String {
        crate::core_version().to_string()
    }
}
