package moe.styx.downloader

import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.buildJsonObject
import moe.styx.types.*
import net.peanuuutz.tomlkt.Toml
import java.math.RoundingMode
import java.text.DecimalFormat
import java.util.*
import kotlin.math.floor

val toml = Toml {
    ignoreUnknownKeys = true
    explicitNulls = true
}

inline fun <reified T> HttpRequestBuilder.setGenericJsonBody(body: T) {
    this.contentType(ContentType.Application.Json)
    this.setBody(json.encodeToString(body))
}

inline fun HttpRequestBuilder.setGenericJsonBody(builder: JsonObjectBuilder.() -> Unit) {
    val jsonObj = buildJsonObject(builder)
    this.contentType(ContentType.Application.Json)
    this.setBody(json.encodeToString(jsonObj))
}

fun String.capitalize(): String {
    return this.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
}

private val small = DecimalFormat("#.#")
private val big = DecimalFormat("#.##")

fun Long.readableSize(useBinary: Boolean = false): String {
    val units = if (useBinary) listOf("B", "KiB", "MiB", "GiB", "TiB") else listOf("B", "KB", "MB", "GB", "TB")
    val divisor = if (useBinary) 1024 else 1000
    var steps = 0
    var current = this.toDouble()
    while (floor((current / divisor)) > 0) {
        current = (current / divisor)
        steps++;
    }
    small.roundingMode = RoundingMode.CEILING.also { big.roundingMode = it }
    return "${(if (steps > 2) big else small).format(current)} ${units[steps]}"
}

fun launchThreaded(run: suspend CoroutineScope.() -> Unit): Pair<Job, CoroutineScope> {
    val job = Job()
    val scope = CoroutineScope(job)
    scope.launch {
        run()
    }
    return Pair(job, scope)
}

infix fun DownloadableOption.parentIn(list: List<DownloaderTarget>): DownloaderTarget {
    return list.find { it.options.find { opt -> opt == this } != null }!!
}

/**
 * We only want to get each RSS feed once, so we map it to Feed and Options that use the feed
 */
fun List<DownloaderTarget>.getRSSOptions(): Map<String, List<DownloadableOption>> {
    val options = this.flatMap { it.options }.filter { !it.sourcePath.isNullOrBlank() && it.source == SourceType.TORRENT }
    return options.groupBy { it.sourcePath!! }
}

fun List<DownloaderTarget>.getFTPOptions(): List<DownloadableOption> {
    val options = this.flatMap { it.options }
    return options.filter { !it.sourcePath.isNullOrBlank() && it.source == SourceType.FTP }
}

fun LocalDateTime.formattedStr(): String {
    return "${this.year}-${this.monthNumber.padString()}-${this.dayOfMonth.padString()} " +
            "${this.hour.padString()}:${this.minute.padString()}:${this.second.padString()}"
}