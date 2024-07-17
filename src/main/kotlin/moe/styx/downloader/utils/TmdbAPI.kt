package moe.styx.downloader.utils

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.runBlocking
import moe.styx.common.data.tmdb.TmdbEpisodeOrder
import moe.styx.common.data.tmdb.TmdbMeta
import moe.styx.common.data.tmdb.TmdbSeason
import moe.styx.common.http.httpClient
import moe.styx.common.json
import moe.styx.downloader.downloaderConfig

suspend inline fun <reified T> genericTmdbGet(url: String): T? {
    val response = httpClient.get(url) {
        accept(ContentType.Application.Json)
        bearerAuth(downloaderConfig.tmdbToken)
        userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/119.0.0.0 Safari/537.36")
    }

    if (response.status == HttpStatusCode.OK)
        return json.decodeFromString<T>(response.bodyAsText())

    return null
}

fun getTmdbMetadata(id: Int, tv: Boolean = true, languageCode: String = "en-US", season: Int? = null): TmdbMeta? = runBlocking {
    var url = "https://api.themoviedb.org/3/${if (tv) "tv" else "movie"}/$id"
    if (tv && season != null) {
        url += "/season/$season"
    }
    return@runBlocking genericTmdbGet("$url?language=$languageCode")
}

fun getTmdbSeason(id: Int, season: Int, languageCode: String = "en-US"): TmdbSeason? = runBlocking {
    return@runBlocking genericTmdbGet("https://api.themoviedb.org/3/tv/$id/season/$season?language=$languageCode")
}

fun getTmdbOrder(id: String, languageCode: String = "en-US"): TmdbEpisodeOrder? = runBlocking {
    return@runBlocking genericTmdbGet("https://api.themoviedb.org/3/tv/episode_group/$id?language=$languageCode")
}