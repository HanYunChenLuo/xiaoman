package com.xiaoman.app

/** Rust 核心库的 JNI 入口。 */
object NativeCore {
    init {
        System.loadLibrary("xiaoman_core")
    }

    @JvmStatic external fun version(): String
}
