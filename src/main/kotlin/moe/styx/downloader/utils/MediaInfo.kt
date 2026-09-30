package moe.styx.downloader.utils

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import moe.styx.common.extension.eqI
import moe.styx.common.extension.equalsAny
import moe.styx.common.json
import java.io.File

@Serializable
data class MediaInfo(
    @SerialName("@ref")
    val ref: String? = null,
    @SerialName("track")
    val tracks: List<Track>
) {
    fun hasGermanDub() = tracks.filter { it.type eqI "audio" }.find { it.matchesLanguage("de", "ger", "deu") } != null
    fun hasGermanSub() = tracks.filter { it.type eqI "text" }.find { it.matchesLanguage("de", "ger", "deu") } != null
    fun hasEnglishDub() = tracks.filter { it.type eqI "audio" }.find { it.matchesLanguage("en", "eng") } != null
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

internal fun Track.matchesLanguage(vararg languages: String): Boolean =
    language?.substringBefore('-').equalsAny(*languages)

internal val mediaInspectionScript = """
    import json, sys
    from muxtools import ParsedFile, TrackType

    media = ParsedFile.from_file(sys.argv[1])
    kinds = {TrackType.VIDEO: "video", TrackType.AUDIO: "audio", TrackType.SUB: "text"}
    codecs = {"h264": "AVC", "hevc": "HEVC", "mpeg2video": "MPEG Video", "av1": "AV1", "vp9": "VP9"}
    tracks = []
    for track in media.tracks:
        if track.type not in kinds:
            continue
        audio_format = track.get_audio_format()
        codec = audio_format.display_name if audio_format else codecs.get(track.codec_name, track.codec_name.upper())
        raw = track.raw_ffprobe
        tracks.append({
            "@type": kinds[track.type], "Format": codec,
            "Width": str(raw.width) if raw.width else None,
            "Height": str(raw.height) if raw.height else None,
            "BitDepth": str(track.bit_depth) if track.bit_depth else None,
            "Language": track.sanitized_lang.to_tag(), "Title": track.title,
            "Default": "Yes" if track.is_default else "No",
            "Forced": "Yes" if track.is_forced else "No",
        })
    print(json.dumps({"@ref": sys.argv[1], "track": tracks}, separators=(",", ":")))
""".trimIndent()

fun File.getMediaInfo(): MediaInfo? {
    val python = getExecutableFromPath("python") ?: return null
    return runCatching {
        json.decodeFromString<MediaInfo>(runPythonQuery(python, mediaInspectionScript, absolutePath))
    }.onFailure {
        Log.w("Media inspection: $name") { it.message ?: "Could not inspect media with muxtools" }
    }.getOrNull()
}

fun File.inspectMedia(): String {
    val python = getExecutableFromPath("python") ?: error("Could not find python or python3 in PATH")
    return runPythonCommand(python, listOf("-m", "muxtools_styx", "inspect", absolutePath))
        .also { check(it.isNotBlank()) { "Media inspection returned no output" } }
}
