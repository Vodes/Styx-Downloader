package moe.styx.downloader.other

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

val test = """
    {
      "content": "test",
      "embeds": [
        {
          "title": "New episode",
          "description": "test\n\n[Watch]()",
          "color": 9886511,
          "author": {
            "name": "Styx",
            "url": "",
            "icon_url": ""
          },
          "thumbnail": {
            "url": ""
          }
        }
      ]
    }
""".trimIndent()

fun test() {
    val embed = JsonObject(
        mapOf(
            "title" to JsonPrimitive(""),
            "description" to JsonPrimitive(""),

            )
    )
    val body = JsonObject(
        mapOf(
            "content" to JsonPrimitive("test content"),
            "embeds" to JsonArray(listOf(embed))
        )
    )
    println(body)
}