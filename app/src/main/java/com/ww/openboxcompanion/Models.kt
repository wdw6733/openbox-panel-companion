package com.ww.openboxcompanion

/**
 * One saved panel server. The password is stored encrypted by [ServerStore]
 * and is only ever sent to this server's own panel login endpoint.
 */
data class Server(
    val id: String,
    val name: String,
    val scheme: String,
    val host: String,
    val port: Int,
    val password: String
) {
    fun baseUrl(): String = "$scheme://$host:$port"
    fun displayUrl(): String = baseUrl()
}

data class HealthResult(
    val rawBody: String,
    val httpCode: Int,
    val latencyMs: Long
)

data class ReleaseAsset(
    val name: String,
    val downloadUrl: String,
    val sizeBytes: Long
)

data class ReleaseInfo(
    val tag: String,
    val name: String,
    val publishedAt: String,
    val htmlUrl: String,
    val assets: List<ReleaseAsset>
)

data class CommandItem(
    val title: String,
    val body: String
)

/** Result wrapper so network failures carry a readable message, never a crash. */
sealed class ApiResult<out T> {
    data class Success<T>(val data: T, val httpCode: Int, val latencyMs: Long) : ApiResult<T>()
    data class Failure(val message: String, val httpCode: Int? = null) : ApiResult<Nothing>()
}
