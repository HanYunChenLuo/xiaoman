package com.xiaoman.app

/** Rust 核心库的 JNI 入口。 */
object NativeCore {
    init {
        System.loadLibrary("xiaoman_core")
    }

    @JvmStatic external fun version(): String

    @JvmStatic external fun decodeIas(bytes: ByteArray): String

    @JvmStatic external fun encodeIas(payload: String): ByteArray

    // .ias 语义映射（core/src/state.rs）：规范状态 JSON
    @JvmStatic external fun backupExport(stateJson: String, imagesJson: String, platform: String, nowMs: Long, tzOffsetMs: Long): String

    @JvmStatic external fun backupImport(payloadJson: String, nowMs: Long, tzOffsetMs: Long): String

    @JvmStatic external fun backupMerge(localJson: String, incomingJson: String, replace: Boolean, preferIncoming: Boolean): String

    @JvmStatic external fun backupTimeMs(payloadJson: String): Long

    @JvmStatic external fun daysBetween(from: String, to: String): Int

    @JvmStatic external fun ageDays(assetJson: String, today: String): Int

    @JvmStatic external fun usageCount(assetJson: String): Int

    @JvmStatic external fun netCost(assetJson: String): Double

    @JvmStatic external fun dailyCost(assetJson: String, nowMs: Long, tzOffsetMs: Long): Double

    @JvmStatic external fun totalOfKind(assetsJson: String, kind: String): Double

    @JvmStatic external fun dailyOfKind(assetsJson: String, kind: String, nowMs: Long, tzOffsetMs: Long): Double

    @JvmStatic external fun countOfKind(assetsJson: String, kind: String, status: String): Int

    // 趋势 / 分布 / 日期 / 状态规范化
    @JvmStatic external fun todayString(nowMs: Long, tzOffsetMs: Long): String

    @JvmStatic external fun trendSeries(assetsJson: String, days: Int, nowMs: Long, tzOffsetMs: Long): String

    @JvmStatic external fun categoryDistribution(assetsJson: String, categoriesJson: String): String

    @JvmStatic external fun incomeTotal(assetJson: String): Double

    @JvmStatic external fun latestPrice(assetJson: String): String

    @JvmStatic external fun statusText(status: String): String

    @JvmStatic external fun displayName(assetJson: String): String

    @JvmStatic external fun newId(prefix: String, nowMs: Long, entropy: Long): String

    @JvmStatic external fun stateNormalize(stateJson: String, defaultsJson: String): String

    // 云备份协议（core/src/cloud.rs）：返回请求 JSON / 解析结果 JSON
    @JvmStatic external fun cloudBackupName(): String

    @JvmStatic external fun cloudFindRequest(token: String): String

    @JvmStatic external fun cloudCreateRequest(token: String): String

    @JvmStatic external fun cloudUploadRequest(token: String, fileId: String): String

    @JvmStatic external fun cloudDownloadRequest(token: String, fileId: String): String

    @JvmStatic external fun cloudParseFileList(status: Int, body: String): String

    @JvmStatic external fun cloudParseFile(status: Int, body: String): String

    @JvmStatic external fun cloudTimeMs(iso: String): Long

    @JvmStatic external fun cloudConflict(remoteIso: String, localMs: Long, lastRemoteMs: Long): String

    // 品牌/型号/图标（core/src/brands.rs）
    @JvmStatic external fun brandsParse(json: String): String

    @JvmStatic external fun brandsMatch(text: String, aliasesJson: String): String

    @JvmStatic external fun brandIconKey(text: String): String

    @JvmStatic external fun brandsValidate(json: String, appVersionCode: Int): String

    @JvmStatic external fun brandsApiUrl(): String

    @JvmStatic external fun brandsRawUrl(): String

    // Google OAuth（core/src/oauth.rs）
    @JvmStatic external fun oauthPkceChallenge(verifier: String): String

    @JvmStatic external fun oauthAuthUrl(clientId: String, redirectUri: String, challenge: String, state: String, scope: String, prompt: String): String

    @JvmStatic external fun oauthTokenRequest(clientId: String, clientSecret: String, code: String, verifier: String, redirectUri: String): String

    @JvmStatic external fun oauthRefreshRequest(clientId: String, clientSecret: String, refreshToken: String): String

    @JvmStatic external fun oauthParseToken(status: Int, body: String): String

    @JvmStatic external fun oauthUserinfoRequest(token: String): String

    @JvmStatic external fun oauthParseEmail(status: Int, body: String): String

    // 更新检查协议（core/src/update.rs）
    @JvmStatic external fun updateApiUrl(): String

    @JvmStatic external fun updateReleasePage(): String

    @JvmStatic external fun updateParseRelease(status: Int, body: String): String

    @JvmStatic external fun updateIsNewer(current: String, latest: String): Boolean

    @JvmStatic external fun updatePickAsset(assetsJson: String, suffix: String): String
}
