package com.xiaoman.app

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import org.json.JSONArray
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
 * 云端备份：GitHub Gist（设备码 OAuth）与 Google Drive 应用数据文件夹（PKCE）。
 * 云文件内容为 .ias 备份的 Base64 文本 / 原始字节。
 */
class CloudSync(private val ctx: Context) {
    private val prefs = ctx.getSharedPreferences("xiaoman_cloud", Context.MODE_PRIVATE)

    @Volatile private var ghCancel = false

    companion object {
        private const val GIST_DESC = "小满云备份"
        private const val GIST_FILE = "xiaoman_backup.ias.b64"
        private const val DRIVE_FILE = "xiaoman_backup.ias"
        private const val REDIRECT = "http://127.0.0.1"
    }

    // ---------- 配置 ----------
    fun githubClientId(): String {
        val custom = prefs.getString("gh_client_id", "") ?: ""
        return if (custom.isNotEmpty()) custom else BuildConfig.GITHUB_CLIENT_ID
    }

    fun googleClientId(): String {
        val custom = prefs.getString("g_client_id", "") ?: ""
        return if (custom.isNotEmpty()) custom else BuildConfig.GOOGLE_CLIENT_ID
    }

    fun setGithubClientId(id: String) {
        prefs.edit().putString("gh_client_id", id.trim()).apply()
    }

    fun setGoogleClientId(id: String) {
        prefs.edit().putString("g_client_id", id.trim()).apply()
    }

    fun githubLogin(): String = prefs.getString("gh_login", "") ?: ""

    fun googleEmail(): String = prefs.getString("g_email", "") ?: ""

    fun githubLoggedIn(): Boolean = prefs.getString("gh_token", "")?.isNotEmpty() == true

    fun googleLoggedIn(): Boolean =
        prefs.getString("g_refresh", "")?.isNotEmpty() == true || prefs.getString("g_token", "")?.isNotEmpty() == true

    fun logoutGithub() {
        ghCancel = true
        prefs.edit().remove("gh_token").remove("gh_login").remove("gh_gist").apply()
    }

    fun logoutGoogle() {
        clearPending()
        prefs.edit().remove("g_token").remove("g_refresh").remove("g_expiry").remove("g_email").remove("g_file").apply()
    }

    fun lastSync(provider: String): String = prefs.getString("last_$provider", "") ?: ""

    private fun markSync(provider: String) {
        val t = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date())
        prefs.edit().putString("last_$provider", t).apply()
    }

    fun hasGithubBackup(): Boolean = prefs.getString("gh_gist", "")?.isNotEmpty() == true
    fun hasGoogleBackup(): Boolean = prefs.getString("g_file", "")?.isNotEmpty() == true

    // ---------- GitHub 设备码登录 ----------
    fun startGithubLogin(
        activity: Activity,
        onCode: (userCode: String, url: String) -> Unit,
        onDone: (login: String) -> Unit,
        onError: (String) -> Unit
    ) {
        val clientId = githubClientId()
        if (clientId.isEmpty()) {
            onError("请先配置 GitHub Client ID")
            return
        }
        ghCancel = false
        Thread {
            try {
                val codeRes = postForm(
                    "https://github.com/login/device/code",
                    mapOf("client_id" to clientId, "scope" to "gist"),
                    json = true
                )
                val deviceCode = codeRes.optString("device_code")
                val userCode = codeRes.optString("user_code")
                val url = codeRes.optString("verification_uri", "https://github.com/login/device")
                var interval = Math.max(5, codeRes.optInt("interval", 5))
                val expires = System.currentTimeMillis() + codeRes.optLong("expires_in", 900) * 1000
                if (deviceCode.isEmpty()) throw Exception(codeRes.optString("error_description", "设备码获取失败"))
                activity.runOnUiThread { onCode(userCode, url) }

                while (!ghCancel && System.currentTimeMillis() < expires) {
                    Thread.sleep(interval * 1000L)
                    val tokenRes = postForm(
                        "https://github.com/login/oauth/access_token",
                        mapOf(
                            "client_id" to clientId,
                            "device_code" to deviceCode,
                            "grant_type" to "urn:ietf:params:oauth:grant-type:device_code"
                        ),
                        json = true
                    )
                    val token = tokenRes.optString("access_token")
                    if (token.isNotEmpty()) {
                        val user = httpJson(
                            "https://api.github.com/user", "GET", null,
                            mapOf("Authorization" to "Bearer $token", "Accept" to "application/vnd.github+json")
                        )
                        val login = user.optString("login")
                        prefs.edit().putString("gh_token", token).putString("gh_login", login).apply()
                        activity.runOnUiThread { onDone(login.ifEmpty { "GitHub" }) }
                        return@Thread
                    }
                    when (tokenRes.optString("error")) {
                        "authorization_pending" -> {}
                        "slow_down" -> interval += 5
                        "expired_token" -> throw Exception("授权已过期，请重试")
                        "access_denied" -> throw Exception("已取消授权")
                        else -> if (tokenRes.has("error")) {
                            throw Exception(tokenRes.optString("error_description", tokenRes.optString("error")))
                        }
                    }
                }
            } catch (e: Exception) {
                if (!ghCancel) activity.runOnUiThread { onError(e.message ?: "GitHub 登录失败") }
            }
        }.start()
    }

    // ---------- GitHub Gist 备份 ----------
    fun githubUpload(bytes: ByteArray, onDone: () -> Unit, onError: (String) -> Unit) {
        val token = prefs.getString("gh_token", "") ?: ""
        if (token.isEmpty()) {
            onError("请先登录 GitHub")
            return
        }
        Thread {
            try {
                val b64 = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
                val desc = GIST_DESC + " · " + SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date())
                var id = prefs.getString("gh_gist", "") ?: ""
                val payload = JSONObject()
                    .put("description", desc)
                    .put("public", false)
                    .put("files", JSONObject().put(GIST_FILE, JSONObject().put("content", b64)))
                if (id.isEmpty()) id = findGist(token)
                if (id.isNotEmpty()) {
                    try {
                        httpJson("https://api.github.com/gists/$id", "PATCH", payload.toString(), auth(token))
                    } catch (_: Exception) {
                        id = ""
                    }
                }
                if (id.isEmpty()) {
                    val created = httpJson("https://api.github.com/gists", "POST", payload.toString(), auth(token))
                    id = created.optString("id")
                }
                if (id.isEmpty()) throw Exception("创建 Gist 失败")
                prefs.edit().putString("gh_gist", id).apply()
                markSync("github")
                onDone()
            } catch (e: Exception) {
                onError(e.message ?: "上传失败")
            }
        }.start()
    }

    fun githubDownload(onDone: (ByteArray) -> Unit, onError: (String) -> Unit) {
        val token = prefs.getString("gh_token", "") ?: ""
        if (token.isEmpty()) {
            onError("请先登录 GitHub")
            return
        }
        Thread {
            try {
                var id = prefs.getString("gh_gist", "") ?: ""
                if (id.isEmpty()) id = findGist(token)
                if (id.isEmpty()) throw Exception("云端没有备份")
                val gist = httpJson("https://api.github.com/gists/$id", "GET", null, auth(token))
                val file = gist.optJSONObject("files")?.optJSONObject(GIST_FILE)
                    ?: throw Exception("备份文件不存在")
                var b64 = file.optString("content")
                if (file.optBoolean("truncated", false)) {
                    val raw = file.optString("raw_url")
                    b64 = String(httpBytes(raw, auth(token)), Charsets.UTF_8)
                }
                val bytes = android.util.Base64.decode(b64, android.util.Base64.DEFAULT)
                onDone(bytes)
            } catch (e: Exception) {
                onError(e.message ?: "下载失败")
            }
        }.start()
    }

    private fun findGist(token: String): String {
        val arr = JSONArray(
            String(
                httpBytes(
                    "https://api.github.com/gists?per_page=100",
                    mapOf("Authorization" to "Bearer $token", "Accept" to "application/vnd.github+json")
                ),
                Charsets.UTF_8
            )
        )
        for (i in 0 until arr.length()) {
            val g = arr.optJSONObject(i) ?: continue
            if (g.optString("description").startsWith(GIST_DESC)) {
                val id = g.optString("id")
                prefs.edit().putString("gh_gist", id).apply()
                return id
            }
        }
        return ""
    }

    private fun auth(token: String) = mapOf(
        "Authorization" to "Bearer $token",
        "Accept" to "application/vnd.github+json"
    )

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
        prefs.edit()
            .putString("g_token", access)
            .putString("g_refresh", refresh)
            .putLong("g_expiry", expires)
            .putString("g_email", email)
            .apply()
        return email.ifEmpty { "Google" }
    }

    // ---------- Google Drive ----------
    fun googleUpload(bytes: ByteArray, onDone: () -> Unit, onError: (String) -> Unit) {
        Thread {
            try {
                val token = googleToken()
                var id = prefs.getString("g_file", "") ?: ""
                if (id.isEmpty()) id = findDriveFile(token)
                if (id.isNotEmpty()) {
                    rawRequest(
                        "https://www.googleapis.com/upload/drive/v3/files/$id?uploadType=media",
                        "PATCH", bytes, "application/octet-stream",
                        mapOf("Authorization" to "Bearer $token")
                    )
                } else {
                    val boundary = "xiaoman" + System.currentTimeMillis()
                    val meta = JSONObject().put("name", DRIVE_FILE).put("parents", JSONArray().put("appDataFolder")).toString()
                    val bos = ByteArrayOutputStream()
                    bos.write(("--$boundary\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n$meta\r\n").toByteArray())
                    bos.write(("--$boundary\r\nContent-Type: application/octet-stream\r\n\r\n").toByteArray())
                    bos.write(bytes)
                    bos.write(("\r\n--$boundary--\r\n").toByteArray())
                    val res = rawRequest(
                        "https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart",
                        "POST", bos.toByteArray(), "multipart/related; boundary=$boundary",
                        mapOf("Authorization" to "Bearer $token")
                    )
                    id = JSONObject(String(res, Charsets.UTF_8)).optString("id")
                }
                if (id.isEmpty()) throw Exception("上传到 Drive 失败")
                prefs.edit().putString("g_file", id).apply()
                markSync("google")
                onDone()
            } catch (e: Exception) {
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
                val bytes = httpBytes(
                    "https://www.googleapis.com/drive/v3/files/$id?alt=media",
                    mapOf("Authorization" to "Bearer $token")
                )
                onDone(bytes)
            } catch (e: Exception) {
                onError(e.message ?: "下载失败")
            }
        }.start()
    }

    private fun findDriveFile(token: String): String {
        val q = URLEncoder.encode("name='$DRIVE_FILE'", "UTF-8")
        val res = httpJson(
            "https://www.googleapis.com/drive/v3/files?spaces=appDataFolder&q=$q&fields=files(id,name)",
            "GET", null, mapOf("Authorization" to "Bearer $token")
        )
        val files = res.optJSONArray("files") ?: return ""
        if (files.length() == 0) return ""
        val id = files.optJSONObject(0)?.optString("id") ?: ""
        if (id.isNotEmpty()) prefs.edit().putString("g_file", id).apply()
        return id
    }

    private fun googleToken(): String {
        val access = prefs.getString("g_token", "") ?: ""
        val expiry = prefs.getLong("g_expiry", 0)
        if (access.isNotEmpty() && System.currentTimeMillis() < expiry - 60_000) return access
        val refresh = prefs.getString("g_refresh", "") ?: ""
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
        prefs.edit()
            .putString("g_token", token)
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

    private fun rawRequest(url: String, method: String, body: ByteArray, contentType: String, headers: Map<String, String>): ByteArray =
        httpBytes(url, headers, method, body, contentType)

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
