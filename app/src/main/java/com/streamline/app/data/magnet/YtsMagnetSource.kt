package com.streamline.app.data.magnet

import com.streamline.app.core.util.await
import com.streamline.app.core.util.readTextBounded
import com.streamline.app.domain.model.MagnetPage
import com.streamline.app.domain.repository.MagnetCatalogException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

/**
 * Reads the public YTS index. Mirrors are tried in order so one blocked domain does not take the
 * archive down; the working mirror is remembered for the rest of the session.
 */
class YtsMagnetSource(
    private val client: OkHttpClient,
    private val hosts: List<String> = DEFAULT_HOSTS,
) : MagnetSourceProvider {

    @Volatile
    private var preferredHost: String? = null

    override suspend fun browse(query: String, page: Int): MagnetPage = withContext(Dispatchers.IO) {
        val orderedHosts = buildList {
            preferredHost?.let(::add)
            hosts.filterNot { it == preferredHost }.forEach(::add)
        }
        var lastError: Throwable? = null
        for (host in orderedHosts) {
            try {
                val payload = fetch(host, query, page)
                val result = YtsResponseParser.parse(payload, page)
                preferredHost = host
                return@withContext result
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                lastError = error
            }
        }
        throw MagnetCatalogException(
            message = "YTS isn't answering right now. Check your connection and try again.",
            cause = lastError,
        )
    }

    private suspend fun fetch(host: String, query: String, page: Int): String {
        val url = "https://$host/api/v2/list_movies.json".toHttpUrl().newBuilder()
            .addQueryParameter("limit", YtsResponseParser.PAGE_SIZE.toString())
            .addQueryParameter("page", page.coerceAtLeast(1).toString())
            .addQueryParameter("sort_by", "date_added")
            .addQueryParameter("order_by", "desc")
            .apply {
                if (query.isNotBlank()) addQueryParameter("query_term", query.trim())
            }
            .build()
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .build()

        client.await(request).use { response ->
            if (!response.isSuccessful) {
                throw IOException("YTS responded with HTTP ${response.code}.")
            }
            val payload = response.body?.readTextBounded().orEmpty()
            if (payload.isBlank()) throw IOException("YTS returned an empty payload.")
            if (payload.trimStart().startsWith("<")) {
                throw IOException("YTS returned a web page instead of JSON.")
            }
            return payload
        }
    }

    private companion object {
        val DEFAULT_HOSTS = listOf("yts.rs", "yts.mx", "yts.lt", "yts.gg", "yts.am", "yts.ag")
    }
}
