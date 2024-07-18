package moe.styx.downloader.utils

import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.buildJsonObject
import moe.styx.common.json

inline fun <reified T> HttpRequestBuilder.setGenericJsonBody(body: T) {
    this.contentType(ContentType.Application.Json)
    this.setBody(json.encodeToString(body))
}

inline fun HttpRequestBuilder.setGenericJsonBody(builder: JsonObjectBuilder.() -> Unit) {
    val jsonObj = buildJsonObject(builder)
    this.contentType(ContentType.Application.Json)
    this.setBody(json.encodeToString(jsonObj))
}

fun extractFilename(contentDisposition: String?): String {
    return runCatching {
        val startIndex = contentDisposition!!.indexOf("filename=")
        val startQuoteIndex = contentDisposition.indexOf('"', startIndex)
        val endQuoteIndex = contentDisposition.indexOf('"', startQuoteIndex + 1)
        contentDisposition.substring(startQuoteIndex + 1, endQuoteIndex)
    }.getOrNull() ?: ""
}