package com.xiaoman.app

/** Rust 核心库的 JNI 入口。 */
object NativeCore {
    init {
        System.loadLibrary("xiaoman_core")
    }

    @JvmStatic external fun version(): String

    @JvmStatic external fun decodeIas(bytes: ByteArray): String

    @JvmStatic external fun encodeIas(payload: String): ByteArray

    @JvmStatic external fun daysBetween(from: String, to: String): Int

    @JvmStatic external fun ageDays(assetJson: String, today: String): Int

    @JvmStatic external fun usageCount(assetJson: String): Int

    @JvmStatic external fun netCost(assetJson: String): Double

    @JvmStatic external fun dailyCost(assetJson: String, nowMs: Long, tzOffsetMs: Long): Double

    @JvmStatic external fun totalOfKind(assetsJson: String, kind: String): Double

    @JvmStatic external fun dailyOfKind(assetsJson: String, kind: String, nowMs: Long, tzOffsetMs: Long): Double

    @JvmStatic external fun countOfKind(assetsJson: String, kind: String, status: String): Int
}
