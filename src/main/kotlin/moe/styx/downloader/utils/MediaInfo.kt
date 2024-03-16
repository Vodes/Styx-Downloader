package moe.styx.downloader.utils

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject
import moe.styx.common.extension.eqI
import moe.styx.common.extension.equalsAny
import moe.styx.common.isWindows
import moe.styx.common.json
import java.io.File
import java.util.concurrent.TimeUnit

@Serializable
data class MediaInfo(
    @SerialName("@ref")
    val ref: String? = null,
    @SerialName("track")
    val tracks: List<Track>
) {
    fun hasGermanDub() = tracks.filter { it.type eqI "audio" }.find { it.language.equalsAny("de", "ger") } != null
    fun hasGermanSub() = tracks.filter { it.type eqI "text" }.find { it.language.equalsAny("de", "ger") } != null
    fun hasEnglishDub() = tracks.filter { it.type eqI "audio" }.find { it.language.equalsAny("en", "eng") } != null
    fun videoBitDepth() = tracks.find { it.type eqI "video" }?.bitDepth?.toIntOrNull() ?: 8
    fun videoResolution() = tracks.find { it.type eqI "video" }?.let { "${it.width ?: "1920"}x${it.height ?: "1080"}" } ?: "1920x1080"
    fun videoCodec() = tracks.find { it.type eqI "video" }?.format ?: "AVC"
}

@Serializable
@SerialName("track")
data class Track(
    @SerialName("@type")
    val type: String,
    @SerialName("StreamOrder")
    val streamOrder: String? = null,
    @SerialName("@typeorder")
    val typeOrder: String? = null,
    @SerialName("ID")
    val id: String? = null,
    @SerialName("Format")
    val format: String? = null,
    @SerialName("CodecID")
    val codecID: String? = null,
    @SerialName("Width")
    val width: String? = null,
    @SerialName("Height")
    val height: String? = null,
    @SerialName("BitDepth")
    val bitDepth: String? = null,
    @SerialName("Language")
    val language: String? = null,
    @SerialName("Default")
    val default: String? = null,
    @SerialName("Forced")
    val forced: String? = null,
    @SerialName("Title")
    val title: String? = null
)

fun File.getMediaInfo(): MediaInfo? {
    val mediainfoExecutable = getExecutableFromPath("mediainfo") ?: return null

    val process: Process = if (isWindows) {
        val command = "\"${mediainfoExecutable.absolutePath}\" --Output=JSON \"${this.absolutePath}\""
        ProcessBuilder(command)
            .redirectOutput(ProcessBuilder.Redirect.PIPE)
            .redirectError(ProcessBuilder.Redirect.PIPE).start()
    } else {
        ProcessBuilder(listOf(mediainfoExecutable.absolutePath, "--Output=JSON", this.absolutePath)).directory(mediainfoExecutable.parentFile)
            .redirectOutput(ProcessBuilder.Redirect.PIPE)
            .redirectError(ProcessBuilder.Redirect.PIPE).start()
    }

    process.waitFor(3, TimeUnit.SECONDS)

    try {
        val output = process.inputStream.bufferedReader().readText()
        val jsonObj = json.decodeFromString<JsonObject>(output)
        return json.decodeFromJsonElement(jsonObj["media"]!!.jsonObject)
    } catch (ex: Exception) {
        ex.printStackTrace()
    }
    return null
}

fun getExecutableFromPath(name: String): File? {
    val pathDirs = System.getenv("PATH").split(File.pathSeparator)
        .map { File(it) }.filter { it.exists() && it.isDirectory }

    for (files in pathDirs.map { it.listFiles() }) {
        val target = files?.find { it.nameWithoutExtension eqI name }
        if (target != null)
            return target
    }
    return null
}