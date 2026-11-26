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
import java.net.URLEncoder
import java.security.MessageDigest
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
    }

    // ---------- 配置 ----------
    fun googleClientId(): String {
        val custom = prefs.getString("g_client_id", "") ?: ""
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
        prefs.edit().remove("g_expiry").remove("g_email").remove("g_file").apply()
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
                val challenge = base64Url(sha256(verifier.toByteArray(Charsets.US_ASCII)))

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
        "https://accounts.google.com/o/oauth2/v2/auth?" + form(mapOf(
            "client_id" to clientId,
            "redirect_uri" to redirect,
            "response_type" to "code",
            "scope" to "openid email https://www.googleapis.com/auth/drive.appdata",
            "code_challenge" to challenge,
            "code_challenge_method" to "S256",
            "access_type" to "offline",
            "include_granted_scopes" to "true",
            "prompt" to "consent"
        ))

    private fun exchangeGoogleCode(clientId: String, code: String, verifier: String, redirect: String): String {
        val tokenRes = postFormJson(
            "https://oauth2.googleapis.com/token",
            mapOf(
                "client_id" to clientId,
                "code" to code,
                "code_verifier" to verifier,
                "redirect_uri" to redirect,
                "grant_type" to "authorization_code"
            )
        )
        android.util.Log.i("XiaomanCloud", "google token scopes=" + tokenRes.optString("scope"))
        val access = tokenRes.optString("access_token")
        val refresh = tokenRes.optString("refresh_token")
        val expires = System.currentTimeMillis() + tokenRes.optLong("expires_in", 3600) * 1000
        if (access.isEmpty()) throw Exception(tokenRes.optString("error_description", "获取令牌失败"))
        val email = try {
            val ui = httpJson(
                "https://openidconnect.googleapis.com/v1/userinfo", "GET", null,
                mapOf("Authorization" to "Bearer $access")
            )
            ui.optString("email")
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
                cloudResult(NativeCore.cloudParseFile(up.code, up.text))
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

    private fun findDriveFile(token: String): String {
        val res = execute(cloudReq(NativeCore.cloudFindRequest(token)))
        val parsed = cloudResult(NativeCore.cloudParseFileList(res.code, res.text))
        val id = parsed.optJSONObject("file")?.optString("id").orEmpty()
        if (id.isNotEmpty()) prefs.edit().putString("g_file", id).apply()
        return id
    }

    private fun googleToken(): String {
        val access = getSecret("g_token")
        val expiry = prefs.getLong("g_expiry", 0)
        if (access.isNotEmpty() && System.currentTimeMillis() < expiry - 60_000) return access
        val refresh = getSecret("g_refresh")
        if (refresh.isEmpty()) throw Exception("请先登录 Google")
        val res = postFormJson(
            "https://oauth2.googleapis.com/token",
            mapOf(
                "client_id" to googleClientId(),
                "refresh_token" to refresh,
                "grant_type" to "refresh_token"
            )
        )
        val token = res.optString("access_token")
        if (token.isEmpty()) throw Exception(res.optString("error_description", "刷新令牌失败"))
        putSecret("g_token", token)
        prefs.edit()
            .putLong("g_expiry", System.currentTimeMillis() + res.optLong("expires_in", 3600) * 1000)
            .apply()
        return token
    }

    // ---------- 网络工具 ----------
    private fun postForm(url: String, params: Map<String, String>, json: Boolean): JSONObject {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.connectTimeout = 15000
        conn.readTimeout = 15000
        conn.doOutput = true
        conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
        conn.setRequestProperty("Accept", if (json) "application/json" else "text/plain")
        conn.setRequestProperty("User-Agent", "Xiaoman/1.0")
        val body = form(params).toByteArray(Charsets.UTF_8)
        conn.outputStream.use { it.write(body) }
        val code = conn.responseCode
        val text = readText(if (code in 200..299) conn.inputStream else conn.errorStream)
        if (code !in 200..299 && text.isEmpty()) throw Exception("HTTP $code")
        return if (text.trimStart().startsWith("{")) JSONObject(text)
        else JSONObject(formToJson(text))
    }

    private fun postFormJson(url: String, params: Map<String, String>): JSONObject = postForm(url, params, true)

    private fun formToJson(text: String): String {
        val obj = JSONObject()
        for (pair in text.split("&")) {
            val idx = pair.indexOf('=')
            if (idx <= 0) continue
            obj.put(
                java.net.URLDecoder.decode(pair.substring(0, idx), "UTF-8"),
                java.net.URLDecoder.decode(pair.substring(idx + 1), "UTF-8")
            )
        }
        return obj.toString()
    }

    private fun httpJson(url: String, method: String, body: String?, headers: Map<String, String>): JSONObject {
        val bytes = httpBytes(url, headers, method, body?.toByteArray(Charsets.UTF_8), "application/json")
        return JSONObject(String(bytes, Charsets.UTF_8))
    }

    private fun httpBytes(url: String, headers: Map<String, String>, method: String = "GET", body: ByteArray? = null, contentType: String? = null): ByteArray {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.requestMethod = method
        conn.connectTimeout = 15000
        conn.readTimeout = 20000
        conn.instanceFollowRedirects = true
        for ((k, v) in headers) conn.setRequestProperty(k, v)
        if (body != null) {
            conn.doOutput = true
            contentType?.let { conn.setRequestProperty("Content-Type", it) }
            conn.outputStream.use { it.write(body) }
        }
        val code = conn.responseCode
        val data = readBytes(if (code in 200..299) conn.inputStream else conn.errorStream)
        if (code !in 200..299) {
            throw Exception("HTTP $code " + String(data, Charsets.UTF_8).take(200))
        }
        return data
    }

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

    private fun readText(input: java.io.InputStream?): String = String(readBytes(input), Charsets.UTF_8)

    private fun form(params: Map<String, String>): String =
        params.entries.joinToString("&") { (k, v) ->
            URLEncoder.encode(k, "UTF-8") + "=" + URLEncoder.encode(v, "UTF-8")
        }

    private fun randomPkce(): String {
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-._~"
        val rnd = SecureRandom()
        return (1..64).map { chars[rnd.nextInt(chars.length)] }.joinToString("")
    }

    private fun sha256(data: ByteArray): ByteArray =
        MessageDigest.getInstance("SHA-256").digest(data)

    private fun base64Url(data: ByteArray): String =
        android.util.Base64.encodeToString(data, android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP or android.util.Base64.NO_PADDING)
}
