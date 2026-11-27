package com.xiaoman.app

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.ServerSocket
import java.net.URL
import java.security.SecureRandom
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 云端备份：Google Drive 应用数据文件夹（PKCE）。
 * 云端文件内容为 .ias 备份的原始字节。
 */
class CloudSync(private val ctx: Context) {
    private val prefs = ctx.getSharedPreferences("xiaoman_cloud", Context.MODE_PRIVATE)

    private fun getSecret(key: String): String {
        val legacy = prefs.getString(key, "") ?: ""
        if (legacy.isNotEmpty()) {
            if (SecretStore.put(prefs, key, legacy)) prefs.edit().remove(key).apply()
            return legacy
        }
        return SecretStore.get(prefs, key)
    }

    private fun putSecret(key: String, value: String) {
        if (SecretStore.put(prefs, key, value)) prefs.edit().remove(key).apply()
        else prefs.edit().putString(key, value).apply()
    }

    companion object {
        private const val REDIRECT = "http://127.0.0.1"
        private const val SCOPE = "openid email https://www.googleapis.com/auth/drive.appdata"

        /** Drive 返回的 ISO 8601 时间（UTC）转换为毫秒；失败返回 0。 */
        @JvmStatic
        fun driveTimeMs(iso: String): Long = NativeCore.cloudTimeMs(iso)
    }

    // ---------- 配置 ----------
    /** 仅返回真正区别于构建内置值的自定义 Client ID；与内置值相同视为未自定义。 */
    fun customGoogleClientId(): String {
        val custom = prefs.getString("g_client_id", "") ?: ""
        return if (custom.isEmpty() || custom == BuildConfig.GOOGLE_CLIENT_ID) "" else custom
    }

    fun googleClientId(): String {
        val custom = customGoogleClientId()
        return if (custom.isNotEmpty()) custom else BuildConfig.GOOGLE_CLIENT_ID
    }

    fun setGoogleClientId(id: String) {
        prefs.edit().putString("g_client_id", id.trim()).apply()
    }

    fun googleEmail(): String = prefs.getString("g_email", "") ?: ""

    fun googleLoggedIn(): Boolean =
        getSecret("g_refresh").isNotEmpty() || getSecret("g_token").isNotEmpty()

    fun logoutGoogle() {
        clearPending()
        SecretStore.clear(prefs, "g_token")
        SecretStore.clear(prefs, "g_refresh")
        prefs.edit().remove("g_expiry").remove("g_email").remove("g_file").remove("g_remote_ms").apply()
    }

    /**
     * 我们最近一次上传后，服务端返回的云端文件修改时间（毫秒）。
     * 用来区分“云端是别人传的新备份”和“云端仍是我们上次传的那份”。
     */
    fun lastRemoteMs(): Long = prefs.getLong("g_remote_ms", 0L)

    private fun rememberRemote(file: JSONObject?) {
        if (file == null) return
        val ms = driveTimeMs(file.optString("modifiedTime"))
        if (ms > 0) prefs.edit().putLong("g_remote_ms", ms).apply()
    }

    fun lastSync(provider: String): String = prefs.getString("last_$provider", "") ?: ""

    private fun markSync(provider: String) {
        val t = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date())
        prefs.edit().putString("last_$provider", t).apply()
    }


    // ---------- Google 登录（PKCE + loopback） ----------
    private var pendingVerifier: String? = null
    private var pendingRedirect: String? = null
    private var pendingClientId: String? = null
    private var pendingDone: ((String) -> Unit)? = null
    private var pendingError: ((String) -> Unit)? = null

    fun handleGoogleUri(activity: Activity, uri: Uri): Boolean {
        if (uri.scheme != BuildConfig.GOOGLE_REDIRECT_SCHEME) return false
        val verifier = pendingVerifier ?: return false
        val redirect = pendingRedirect ?: return false
        val clientId = pendingClientId ?: return false
        val code = uri.getQueryParameter("code") ?: ""
        val error = uri.getQueryParameter("error") ?: ""
        val done = pendingDone
        val fail = pendingError
        clearPending()
        Thread {
            try {
                if (code.isEmpty()) {
                    throw Exception(if (error.isNotEmpty()) "授权失败：$error" else "未收到授权码")
                }
                val email = exchangeGoogleCode(clientId, code, verifier, redirect)
                activity.runOnUiThread { done?.invoke(email) }
            } catch (e: Exception) {
                android.util.Log.w("XiaomanCloud", "google redirect failed", e)
                activity.runOnUiThread { fail?.invoke(e.message ?: "Google 登录失败") }
            }
        }.start()
        return true
    }

    private fun clearPending() {
        pendingVerifier = null
        pendingRedirect = null
        pendingClientId = null
        pendingDone = null
        pendingError = null
    }

    fun startGoogleLogin(activity: Activity, onStatus: (String) -> Unit, onDone: (String) -> Unit, onError: (String) -> Unit) {
        val clientId = googleClientId()
        if (clientId.isEmpty()) {
            onError("请先配置 Google Client ID")
            return
        }
        Thread {
            try {
                val verifier = randomPkce()
                val challenge = NativeCore.oauthPkceChallenge(verifier)

                // 构建时配置了 Google Client ID 时使用自定义 Scheme 直接跳回 App。
                if (BuildConfig.GOOGLE_CLIENT_ID.isNotEmpty() && clientId == BuildConfig.GOOGLE_CLIENT_ID) {
                    val redirect = BuildConfig.GOOGLE_REDIRECT_SCHEME + ":/oauth2redirect"
                    pendingVerifier = verifier
                    pendingRedirect = redirect
                    pendingClientId = clientId
                    pendingDone = onDone
                    pendingError = onError
                    val url = authUrl(clientId, redirect, challenge)
                    activity.runOnUiThread {
                        onStatus("请在浏览器中完成 Google 授权…")
                        activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                    }
                    return@Thread
                }

                // 运行时填写的 Client ID：回退到 127.0.0.1 loopback（桌面应用类型客户端）。
                val server = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))
                server.soTimeout = 180_000
                val redirect = "$REDIRECT:${server.localPort}/"
                val url = authUrl(clientId, redirect, challenge)
                activity.runOnUiThread {
                    onStatus("请在浏览器中完成 Google 授权…")
                    activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                }
                val authCode = server.accept().use { socket ->
                    val input = socket.getInputStream().bufferedReader()
                    val requestLine = input.readLine() ?: ""
                    val target = requestLine.split(" ").getOrNull(1) ?: ""
                    val code = Uri.parse("http://x$target").getQueryParameter("code") ?: ""
                    val error = Uri.parse("http://x$target").getQueryParameter("error") ?: ""
                    val body = if (code.isNotEmpty()) {
                        "<html><body style='font-family:sans-serif;padding:40px'><h2>登录成功</h2><p>请返回小满。</p></body></html>"
                    } else {
                        "<html><body style='font-family:sans-serif;padding:40px'><h2>授权失败</h2><p>$error</p></body></html>"
                    }
                    val out = socket.getOutputStream()
                    out.write(("HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=utf-8\r\nConnection: close\r\n\r\n$body").toByteArray())
                    out.flush()
                    when {
                        code.isNotEmpty() -> code
                        error.isNotEmpty() -> throw Exception("授权失败：$error")
                        else -> throw Exception("未收到授权码")
                    }
                }
                server.close()
                val email = exchangeGoogleCode(clientId, authCode, verifier, redirect)
                activity.runOnUiThread { onDone(email) }
            } catch (e: Exception) {
                android.util.Log.w("XiaomanCloud", "google login failed", e)
                activity.runOnUiThread { onError(e.message ?: "Google 登录失败") }
            }
        }.start()
    }

    private fun authUrl(clientId: String, redirect: String, challenge: String): String =
        NativeCore.oauthAuthUrl(clientId, redirect, challenge, "", SCOPE, "consent")

    private fun exchangeGoogleCode(clientId: String, code: String, verifier: String, redirect: String): String {
        val tokenRes = execute(cloudReq(NativeCore.oauthTokenRequest(clientId, "", code, verifier, redirect)))
        val token = JSONObject(NativeCore.oauthParseToken(tokenRes.code, tokenRes.text))
        android.util.Log.i("XiaomanCloud", "google token scopes=" + token.optString("scope"))
        if (!token.optBoolean("ok")) throw Exception(token.optString("error", "获取令牌失败"))
        val access = token.optString("accessToken")
        val refresh = token.optString("refreshToken")
        val expires = System.currentTimeMillis() + token.optLong("expiresIn", 3600) * 1000
        val email = try {
            val ui = execute(cloudReq(NativeCore.oauthUserinfoRequest(access)))
            NativeCore.oauthParseEmail(ui.code, ui.text)
        } catch (_: Exception) {
            ""
        }
        putSecret("g_token", access)
        putSecret("g_refresh", refresh)
        prefs.edit()
            .putLong("g_expiry", expires)
            .putString("g_email", email)
            .apply()
        return email.ifEmpty { "Google" }
    }

    // ---------- Google Drive ----------
    // 云端协议（文件名、URL、错误文案）由 Rust core 提供，与 Web 端一致。

    private data class CloudReq(
        val method: String,
        val url: String,
        val headers: Map<String, String>,
        val body: ByteArray?
    )

    private data class HttpResponse(val code: Int, val bytes: ByteArray, val text: String)

    private fun cloudReq(json: String): CloudReq {
        val obj = JSONObject(json)
        val headers = LinkedHashMap<String, String>()
        obj.optJSONObject("headers")?.let { h ->
            for (key in h.keys()) headers[key] = h.optString(key)
        }
        val body = obj.optString("body", "").takeIf { obj.has("body") && it.isNotEmpty() }
        return CloudReq(
            obj.optString("method", "GET"),
            obj.optString("url"),
            headers,
            body?.toByteArray(Charsets.UTF_8)
        )
    }

    private fun execute(req: CloudReq, body: ByteArray? = null): HttpResponse =
        httpRaw(req.url, req.method, body ?: req.body, req.headers)

    private fun httpRaw(url: String, method: String, body: ByteArray?, headers: Map<String, String>): HttpResponse {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.requestMethod = method
        conn.connectTimeout = 15000
        conn.readTimeout = 30000
        for ((k, v) in headers) conn.setRequestProperty(k, v)
        if (body != null) {
            conn.doOutput = true
            conn.outputStream.use { it.write(body) }
        }
        val code = conn.responseCode
        val data = readBytes(if (code in 200..299) conn.inputStream else conn.errorStream)
        return HttpResponse(code, data, String(data, Charsets.UTF_8))
    }

    /** 解析 core 返回的 {ok,file,error,status}；失败时抛出中文错误。 */
    private fun cloudResult(json: String): JSONObject {
        val obj = JSONObject(json)
        if (!obj.optBoolean("ok")) {
            if (obj.optInt("status", 0) == 404) prefs.edit().remove("g_file").apply()
            throw Exception(obj.optString("error", "云端请求失败"))
        }
        return obj
    }

    fun googleUpload(bytes: ByteArray, onDone: () -> Unit, onError: (String) -> Unit) {
        Thread {
            try {
                val token = googleToken()
                var id = prefs.getString("g_file", "") ?: ""
                if (id.isEmpty()) id = findDriveFile(token)
                if (id.isEmpty()) {
                    val created = execute(cloudReq(NativeCore.cloudCreateRequest(token)))
                    val parsed = cloudResult(NativeCore.cloudParseFile(created.code, created.text))
                    id = parsed.optJSONObject("file")?.optString("id").orEmpty()
                    if (id.isEmpty()) throw Exception("上传到 Drive 失败")
                    prefs.edit().putString("g_file", id).apply()
                }
                val up = execute(cloudReq(NativeCore.cloudUploadRequest(token, id)), bytes)
                val parsed = cloudResult(NativeCore.cloudParseFile(up.code, up.text))
                rememberRemote(parsed.optJSONObject("file"))
                markSync("google")
                onDone()
            } catch (e: Exception) {
                android.util.Log.w("XiaomanCloud", "upload failed", e)
                onError(e.message ?: "上传失败")
            }
        }.start()
    }

    fun googleDownload(onDone: (ByteArray) -> Unit, onError: (String) -> Unit) {
        Thread {
            try {
                val token = googleToken()
                var id = prefs.getString("g_file", "") ?: ""
                if (id.isEmpty()) id = findDriveFile(token)
                if (id.isEmpty()) throw Exception("云端没有备份")
                val req = cloudReq(NativeCore.cloudDownloadRequest(token, id))
                val res = execute(req)
                if (res.code !in 200..299) {
                    cloudResult(NativeCore.cloudParseFile(res.code, res.text))
                    throw Exception("下载失败")
                }
                onDone(res.bytes)
            } catch (e: Exception) {
                android.util.Log.w("XiaomanCloud", "download failed", e)
                onError(e.message ?: "下载失败")
            }
        }.start()
    }

    /** 云端备份的文件信息（含 modifiedTime/size）；没有备份时返回 null。 */
    fun googleFileInfo(onDone: (JSONObject?) -> Unit, onError: (String) -> Unit) {
        Thread {
            try {
                // 只读取，不更新“我们见过的云端时间”，否则冲突检测会失效。
                onDone(findDriveFileInfo(googleToken()))
            } catch (e: Exception) {
                android.util.Log.w("XiaomanCloud", "file info failed", e)
                onError(e.message ?: "读取云端备份信息失败")
            }
        }.start()
    }

    private fun findDriveFile(token: String): String = findDriveFileInfo(token)?.optString("id").orEmpty()

    private fun findDriveFileInfo(token: String): JSONObject? {
        val res = execute(cloudReq(NativeCore.cloudFindRequest(token)))
        val parsed = cloudResult(NativeCore.cloudParseFileList(res.code, res.text))
        val file = parsed.optJSONObject("file")
        val id = file?.optString("id").orEmpty()
        if (id.isEmpty()) return null
        prefs.edit().putString("g_file", id).apply()
        return file
    }

    private fun googleToken(): String {
        val access = getSecret("g_token")
        val expiry = prefs.getLong("g_expiry", 0)
        if (access.isNotEmpty() && System.currentTimeMillis() < expiry - 60_000) return access
        val refresh = getSecret("g_refresh")
        if (refresh.isEmpty()) throw Exception("请先登录 Google")
        val res = execute(cloudReq(NativeCore.oauthRefreshRequest(googleClientId(), "", refresh)))
        val parsed = JSONObject(NativeCore.oauthParseToken(res.code, res.text))
        val token = parsed.optString("accessToken")
        if (!parsed.optBoolean("ok") || token.isEmpty()) {
            if (parsed.optBoolean("relogin")) {
                logoutGoogle()
                throw Exception("Google 登录已过期，请在「设置 → 数据同步」重新登录")
            }
            throw Exception(parsed.optString("error", "刷新令牌失败"))
        }
        putSecret("g_token", token)
        prefs.edit()
            .putLong("g_expiry", System.currentTimeMillis() + parsed.optLong("expiresIn", 3600) * 1000)
            .apply()
        return token
    }

    // ---------- 网络工具 ----------
    private fun readBytes(input: java.io.InputStream?): ByteArray {
        if (input == null) return ByteArray(0)
        input.use {
            val bos = ByteArrayOutputStream()
            val buf = ByteArray(8192)
            var n: Int
            while (it.read(buf).also { n = it } > 0) bos.write(buf, 0, n)
            return bos.toByteArray()
        }
    }

    private fun randomPkce(): String {
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-._~"
        val rnd = SecureRandom()
        return (1..64).map { chars[rnd.nextInt(chars.length)] }.joinToString("")
    }
}
