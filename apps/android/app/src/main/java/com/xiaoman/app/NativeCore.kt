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

    // 云备份协议（core/src/cloud.rs）：返回请求 JSON / 解析结果 JSON
    @JvmStatic external fun cloudBackupName(): String

    @JvmStatic external fun cloudFindRequest(token: String): String

    @JvmStatic external fun cloudCreateRequest(token: String): String

    @JvmStatic external fun cloudUploadRequest(token: String, fileId: String): String

    @JvmStatic external fun cloudDownloadRequest(token: String, fileId: String): String

    @JvmStatic external fun cloudParseFileList(status: Int, body: String): String

    @JvmStatic external fun cloudParseFile(status: Int, body: String): String

    // 更新检查协议（core/src/update.rs）
    @JvmStatic external fun updateApiUrl(): String

    @JvmStatic external fun updateReleasePage(): String

    @JvmStatic external fun updateParseRelease(status: Int, body: String): String

    @JvmStatic external fun updateIsNewer(current: String, latest: String): Boolean

    @JvmStatic external fun updatePickAsset(assetsJson: String, suffix: String): String
}
