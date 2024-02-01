package moe.styx.downloader.utils

import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.buildJsonObject
import moe.styx.types.json

inline fun <reified T> HttpRequestBuilder.setGenericJsonBody(body: T) {
    this.contentType(ContentType.Application.Json)
    this.setBody(json.encodeToString(body))
}

inline fun HttpRequestBuilder.setGenericJsonBody(builder: JsonObjectBuilder.() -> Unit) {
    val jsonObj = buildJsonObject(builder)
    this.contentType(ContentType.Application.Json)
    this.setBody(json.encodeToString(jsonObj))
}