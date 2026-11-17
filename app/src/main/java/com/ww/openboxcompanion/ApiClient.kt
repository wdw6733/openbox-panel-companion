package com.ww.openboxcompanion

import android.webkit.CookieManager
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * HTTP client for the user's own Open-Box panel.
 *
 * Only endpoints that appear in the public Open-Box repository
 * (release-verify harness and docs) are used: /api/health,
 * /api/auth/login and /api/openbox/{subscriptions, groups, profile,
 * service/status, service/core/*}. Authentication is the normal
 * password login; the session cookie is kept in memory only.
 *
 * All methods are blocking and must be called off the main thread.
 * Nothing here logs passwords or request bodies.
 */
object ApiClient {

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    private val cookieJar = MemoryCookieJar()

    private val client: OkHttpClient = OkHttpClient.Builder()
        .cookieJar(cookieJar)
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .build()

    private val githubClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .build()

    fun clearCookies() {
        cookieJar.clear()
    }

    // ---------- health ----------

    fun healthCheck(baseUrl: String): ApiResult<HealthResult> {
        val url = buildUrl(baseUrl, "api", "health")
            ?: return ApiResult.Failure("面板地址不正确：$baseUrl")
        val started = System.currentTimeMillis()
        return try {
            val request = Request.Builder().url(url).get().build()
            client.newCall(request).execute().use { response ->
                val latency = System.currentTimeMillis() - started
                val body = (response.body?.string() ?: "").take(2000)
                if (response.isSuccessful) {
                    ApiResult.Success(HealthResult(body, response.code, latency), response.code, latency)
                } else {
                    ApiResult.Failure("健康检查返回 HTTP ${response.code}：$body", response.code)
                }
            }
        } catch (e: Exception) {
            ApiResult.Failure("健康检查失败：${safeMessage(e)}")
        }
    }

    // ---------- auth ----------

    fun login(server: Server): ApiResult<Unit> {
        val url = buildUrl(server.baseUrl(), "api", "auth", "login")
            ?: return ApiResult.Failure("面板地址不正确：${server.baseUrl()}")
        if (server.password.isEmpty()) {
            return ApiResult.Failure("这台服务器没有保存密码，请先在编辑页填写面板密码。")
        }
        cookieJar.clearHost(url.host)
        val bodyJson = JSONObject().put("password", server.password)
        val started = System.currentTimeMillis()
        return try {
            val request = Request.Builder()
                .url(url)
                .post(bodyJson.toString().toRequestBody(jsonMediaType))
                .header("Accept", "application/json")
                .build()
            client.newCall(request).execute().use { response ->
                val latency = System.currentTimeMillis() - started
                val body = (response.body?.string() ?: "").take(500)
                if (response.isSuccessful) {
                    ApiResult.Success(Unit, response.code, latency)
                } else if (response.code == 401 || response.code == 403) {
                    ApiResult.Failure("密码不正确或会话被拒绝（HTTP ${response.code}）。请确认面板密码，或先在面板网页里登录一次。", response.code)
                } else {
                    ApiResult.Failure("登录返回 HTTP ${response.code}：$body", response.code)
                }
            }
        } catch (e: Exception) {
            ApiResult.Failure("登录失败：${safeMessage(e)}")
        }
    }

    // ---------- generic panel API with one login retry ----------

    private fun callOpenBox(
        server: Server,
        method: String,
        segments: List<String>,
        bodyJson: JSONObject?,
        allowRetry: Boolean = true
    ): ApiResult<JSONObject> {
        val url = buildUrl(server.baseUrl(), *segments.toTypedArray())
            ?: return ApiResult.Failure("面板地址不正确：${server.baseUrl()}")
        val started = System.currentTimeMillis()
        try {
            val builder = Request.Builder().url(url).header("Accept", "application/json")
            when (method) {
                "GET" -> builder.get()
                "POST" -> builder.post((bodyJson ?: JSONObject()).toString().toRequestBody(jsonMediaType))
                "PUT" -> builder.put((bodyJson ?: JSONObject()).toString().toRequestBody(jsonMediaType))
                "PATCH" -> builder.patch((bodyJson ?: JSONObject()).toString().toRequestBody(jsonMediaType))
                else -> builder.get()
            }
            client.newCall(builder.build()).execute().use { response ->
                val latency = System.currentTimeMillis() - started
                val body = response.body?.string() ?: ""
                if (response.isSuccessful) {
                    val parsed = parseJsonObject(body)
                        ?: return ApiResult.Failure("面板返回的内容不是 JSON：${body.take(300)}", response.code)
                    return ApiResult.Success(parsed, response.code, latency)
                }
                if ((response.code == 401 || response.code == 403) && allowRetry) {
                    val loginResult = login(server)
                    if (loginResult is ApiResult.Success) {
                        return callOpenBox(server, method, segments, bodyJson, allowRetry = false)
                    }
                    return ApiResult.Failure("需要登录：${(loginResult as ApiResult.Failure).message}", response.code)
                }
                return ApiResult.Failure(
                    "面板返回 HTTP ${response.code}：${body.take(500)}",
                    response.code
                )
            }
        } catch (e: Exception) {
            return ApiResult.Failure("请求失败：${safeMessage(e)}")
        }
    }

    // ---------- service ----------

    fun getStatus(server: Server): ApiResult<JSONObject> {
        return callOpenBox(server, "GET", listOf("api", "openbox", "service", "status"), null)
    }

    fun coreAction(server: Server, action: String): ApiResult<JSONObject> {
        if (action != "start" && action != "stop" && action != "restart") {
            return ApiResult.Failure("未知的内核操作：$action")
        }
        return callOpenBox(
            server,
            "POST",
            listOf("api", "openbox", "service", "core", action),
            JSONObject()
        )
    }

    // ---------- subscriptions ----------

    fun getSubscriptions(server: Server): ApiResult<JSONObject> {
        return callOpenBox(server, "GET", listOf("api", "openbox", "subscriptions"), null)
    }

    fun addSubscription(server: Server, name: String, content: String): ApiResult<JSONObject> {
        val renameOptions = JSONObject()
            .put("enabled", false)
            .put("excludeKeywords", JSONArray())
        val body = JSONObject()
            .put("name", name)
            .put("content", content)
            .put("renameOptions", renameOptions)
        return callOpenBox(server, "POST", listOf("api", "openbox", "subscriptions"), body)
    }

    fun updateSubscriptionContent(server: Server, id: String, content: String): ApiResult<JSONObject> {
        val body = JSONObject().put("content", content)
        return callOpenBox(server, "PATCH", listOf("api", "openbox", "subscriptions", id), body)
    }

    // ---------- groups ----------

    fun getGroups(server: Server): ApiResult<JSONObject> {
        return callOpenBox(server, "GET", listOf("api", "openbox", "groups"), null)
    }

    fun updateGroups(server: Server, groups: JSONArray): ApiResult<JSONObject> {
        val body = JSONObject().put("groups", groups)
        return callOpenBox(server, "PUT", listOf("api", "openbox", "groups"), body)
    }

    // ---------- profile ----------

    fun getProfile(server: Server): ApiResult<JSONObject> {
        return callOpenBox(server, "GET", listOf("api", "openbox", "profile"), null)
    }

    fun updateProfile(server: Server, partial: JSONObject): ApiResult<JSONObject> {
        return callOpenBox(server, "PUT", listOf("api", "openbox", "profile"), partial)
    }

    // ---------- GitHub releases (public, read-only) ----------

    fun fetchReleases(): ApiResult<List<ReleaseInfo>> {
        val url = "https://api.github.com/repos/liandu2024/Open-Box/releases?per_page=5"
            .toHttpUrlOrNull() ?: return ApiResult.Failure("GitHub API 地址解析失败。")
        val started = System.currentTimeMillis()
        return try {
            val request = Request.Builder()
                .url(url)
                .get()
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "panel-companion/0.1.0")
                .build()
            githubClient.newCall(request).execute().use { response ->
                val latency = System.currentTimeMillis() - started
                val body = response.body?.string() ?: ""
                if (!response.isSuccessful) {
                    return ApiResult.Failure("GitHub 返回 HTTP ${response.code}：${body.take(300)}", response.code)
                }
                val array = try {
                    JSONArray(body)
                } catch (e: Exception) {
                    return ApiResult.Failure("GitHub 返回的内容无法解析。")
                }
                val releases = mutableListOf<ReleaseInfo>()
                for (i in 0 until array.length()) {
                    val obj = array.optJSONObject(i) ?: continue
                    val assets = mutableListOf<ReleaseAsset>()
                    val assetsArray = obj.optJSONArray("assets")
                    if (assetsArray != null) {
                        for (j in 0 until assetsArray.length()) {
                            val asset = assetsArray.optJSONObject(j) ?: continue
                            assets.add(
                                ReleaseAsset(
                                    name = asset.optString("name"),
                                    downloadUrl = asset.optString("browser_download_url"),
                                    sizeBytes = asset.optLong("size", 0L)
                                )
                            )
                        }
                    }
                    releases.add(
                        ReleaseInfo(
                            tag = obj.optString("tag_name"),
                            name = obj.optString("name"),
                            publishedAt = obj.optString("published_at"),
                            htmlUrl = obj.optString("html_url"),
                            assets = assets
                        )
                    )
                }
                ApiResult.Success(releases, response.code, latency)
            }
        } catch (e: Exception) {
            ApiResult.Failure("检查版本失败：${safeMessage(e)}")
        }
    }

    // ---------- WebView cookie sync (best effort) ----------

    /**
     * Copies the in-memory session cookies for [server] into the WebView
     * cookie store so the embedded panel page may already be logged in.
     * Returns how many cookies were copied; 0 means the user will need
     * to log in inside the WebView once.
     */
    fun syncCookiesToWebView(server: Server): Int {
        return try {
            val url = buildUrl(server.baseUrl()) ?: return 0
            val cookies = cookieJar.loadForRequest(url)
            if (cookies.isEmpty()) return 0
            val manager = CookieManager.getInstance()
            manager.setAcceptCookie(true)
            for (cookie in cookies) {
                manager.setCookie(server.baseUrl(), "${cookie.name}=${cookie.value}")
            }
            manager.flush()
            cookies.size
        } catch (e: Exception) {
            0
        }
    }

    // ---------- helpers ----------

    private fun buildUrl(base: String, vararg segments: String): HttpUrl? {
        val parsed = base.trimEnd('/').toHttpUrlOrNull() ?: return null
        val builder = parsed.newBuilder()
        for (segment in segments) {
            builder.addPathSegment(segment)
        }
        return builder.build()
    }

    private fun parseJsonObject(body: String): JSONObject? {
        val trimmed = body.trim()
        if (trimmed.isEmpty()) return JSONObject()
        return try {
            JSONObject(trimmed)
        } catch (e: Exception) {
            null
        }
    }

    private fun safeMessage(e: Exception): String {
        val message = e.message ?: e.javaClass.simpleName
        return message.take(300)
    }

    private class MemoryCookieJar : CookieJar {
        private val store = ConcurrentHashMap<String, MutableList<Cookie>>()

        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
            val list = store.getOrPut(url.host) { mutableListOf() }
            synchronized(list) {
                for (cookie in cookies) {
                    list.removeAll { it.name == cookie.name && it.path == cookie.path }
                    list.add(cookie)
                }
            }
        }

        override fun loadForRequest(url: HttpUrl): List<Cookie> {
            val list = store[url.host] ?: return emptyList()
            val now = System.currentTimeMillis()
            synchronized(list) {
                return list.filter { !it.persistent || it.expiresAt > now }.toList()
            }
        }

        fun clear() {
            store.clear()
        }

        fun clearHost(host: String) {
            store.remove(host)
        }
    }
}
