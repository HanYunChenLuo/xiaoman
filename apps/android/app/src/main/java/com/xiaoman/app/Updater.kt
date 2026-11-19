package com.xiaoman.app

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * 应用内更新：GitHub Releases 检查 → 自动下载 APK → 拉起系统安装。
 *
 * 版本比较、发布信息解析和资产筛选在 Rust core（update.rs），与桌面端共用。
 * Android 不允许静默安装，最后一步必须由系统安装器确认（同签名即可覆盖升级）。
 */
class Updater(private val activity: Activity) {

    data class Info(val version: String, val notes: String, val url: String, val size: Long)

    fun check(onResult: (Info?) -> Unit, onError: (String) -> Unit) {
        Thread {
            try {
                val conn = URL(NativeCore.updateApiUrl()).openConnection() as HttpURLConnection
                conn.connectTimeout = 15000
                conn.readTimeout = 20000
                conn.setRequestProperty("Accept", "application/vnd.github+json")
                conn.setRequestProperty("User-Agent", "Xiaoman-Android/${BuildConfig.VERSION_NAME}")
                val code = conn.responseCode
                val stream = if (code in 200..299) conn.inputStream else conn.errorStream
                val body = stream?.bufferedReader()?.use { it.readText() } ?: ""
                val parsed = JSONObject(NativeCore.updateParseRelease(code, body))
                if (!parsed.optBoolean("ok")) {
                    throw Exception(parsed.optString("error", "检查更新失败"))
                }
                val latest = parsed.optString("version")
                if (!NativeCore.updateIsNewer(BuildConfig.VERSION_NAME, latest)) {
                    activity.runOnUiThread { onResult(null) }
                    return@Thread
                }
                val assets = parsed.optJSONArray("assets")?.toString() ?: "[]"
                val picked = JSONObject(NativeCore.updatePickAsset(assets, ".apk"))
                if (!picked.optBoolean("found")) throw Exception("该版本未提供 APK 安装包")
                val asset = picked.getJSONObject("asset")
                val info = Info(
                    latest,
                    parsed.optString("notes", ""),
                    asset.optString("url"),
                    asset.optLong("size", 0L)
                )
                activity.runOnUiThread { onResult(info) }
            } catch (e: Exception) {
                activity.runOnUiThread { onError(e.message ?: "检查更新失败") }
            }
        }.start()
    }

    /** 下载完成后回调 `onReady(apk)`（主线程），由调用方关闭进度提示后再 [install]。 */
    fun download(info: Info, onProgress: (Int) -> Unit, onReady: (File) -> Unit, onError: (String) -> Unit) {
        Thread {
            try {
                val dir = File(activity.cacheDir, "update").apply { mkdirs() }
                val apk = File(dir, "xiaoman-${info.version}.apk")
                if (apk.exists() && apk.length() > 0 && info.size > 0 && apk.length() == info.size) {
                    activity.runOnUiThread { onReady(apk) }
                    return@Thread
                }
                val conn = URL(info.url).openConnection() as HttpURLConnection
                conn.connectTimeout = 15000
                conn.readTimeout = 60000
                conn.instanceFollowRedirects = true
                conn.setRequestProperty("User-Agent", "Xiaoman-Android/${BuildConfig.VERSION_NAME}")
                val total = conn.contentLengthLong
                conn.inputStream.use { input ->
                    apk.outputStream().use { out ->
                        val buf = ByteArray(64 * 1024)
                        var read = 0L
                        var n: Int
                        while (input.read(buf).also { n = it } > 0) {
                            out.write(buf, 0, n)
                            read += n
                            if (total > 0) {
                                val pct = (read * 100 / total).toInt()
                                activity.runOnUiThread { onProgress(pct) }
                            }
                        }
                    }
                }
                if (apk.length() <= 0L) throw Exception("下载失败")
                activity.runOnUiThread { onReady(apk) }
            } catch (e: Exception) {
                activity.runOnUiThread { onError(e.message ?: "下载失败") }
            }
        }.start()
    }

    fun install(apk: File) {
        if (Build.VERSION.SDK_INT >= 26 && !activity.packageManager.canRequestPackageInstalls()) {
            Toast.makeText(activity, "请允许「小满」安装应用后再点检查更新", Toast.LENGTH_LONG).show()
            try {
                activity.startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:${activity.packageName}")
                    )
                )
            } catch (_: Exception) {
                Toast.makeText(activity, "无法打开安装权限设置", Toast.LENGTH_LONG).show()
            }
            return
        }
        try {
            val uri = FileProvider.getUriForFile(activity, "${activity.packageName}.files", apk)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            activity.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(activity, "无法启动安装器：${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}
