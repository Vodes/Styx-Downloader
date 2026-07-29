package moe.styx.downloader.parsing

import moe.styx.common.extension.eqI
import moe.styx.common.extension.equalsAny
import moe.styx.downloader.utils.Log
import moe.styx.downloader.utils.RegexCollection
import pw.vodes.anitomy.Element
import pw.vodes.anitomy.ElementKind
import java.text.DecimalFormat

import pw.vodes.anitomy.parse as anitomyParse

typealias AnitomyResults = List<Element>

val AnitomyResults.season: String?
    get() = this.find { it.kind == ElementKind.SEASON }?.value

val AnitomyResults.title: String?
    get() = this.find { it.kind == ElementKind.TITLE }?.value

val AnitomyResults.episode: String?
    get() = this.find { it.kind == ElementKind.EPISODE }?.value

val AnitomyResults.version: String?
    get() = this.find { it.kind == ElementKind.RELEASE_VERSION }?.value

val AnitomyResults.group: String?
    get() = this.find { it.kind == ElementKind.RELEASE_GROUP }?.value

val AnitomyResults.isLikelyFtpRelease: Boolean
    get() {
        return if(!group.isNullOrBlank())
            group.equalsAny("JapDub", "GerJapDub", "JapGerDub", "E-AC-3", "EAC3", "EAC-3", "GerEngSub", "GerSub", "EngSub")
        else
            this
                .filter { it.kind in arrayOf(ElementKind.AUDIO_TERM, ElementKind.VIDEO_TERM, ElementKind.SUBTITLES, ElementKind.OTHER, ElementKind.RELEASE_INFORMATION) }
                .any { it.value.equalsAny("JapDub", "EngSub", "GerSub", "GerJapDub", "JapGerDub", "GerEngSub") }
    }


fun parseMetadata(toParse: String): AnitomyResults {
    var adjusted = toParse

    val repackMatch = RegexCollection.repackRegex.find(adjusted)
    if (repackMatch != null)
        adjusted =
            adjusted.replace(repackMatch.groups[0]!!.value, repackMatch.groups[1]?.let { ".V${it.value.toIntOrNull()?.plus(1) ?: 2}." } ?: ".V2.")

    adjusted = adjusted.replaceFirst(RegexCollection.crc32Regex, "")
    if (adjusted.contains("NanDesuKa", true)) {
        val oldFixMatch = RegexCollection.oldNandesukaFixRegex.find(adjusted)
        if (oldFixMatch != null) {
            adjusted = adjusted.replaceFirst(oldFixMatch.groups["site"]!!.value, "")
        }
    }

    val result = anitomyParse(adjusted).toMutableList()

    // Sometimes it doesn't seem to parse the season at all.
    val episode = result.episode
    if (episode == null) {
        val zeroMatch = RegexCollection.seasonZeroRegex.find(adjusted)
        if (zeroMatch != null) {
            result.add(Element(ElementKind.EPISODE, zeroMatch.groups["ep"]!!.value, -1))
            result.removeIf { it.kind == ElementKind.SEASON }
            result.add(Element(ElementKind.SEASON, "00", -1))
            Log.w { "Had to 'manually' parse Season 0 episode for: $toParse" }
        }
    }
    return result
}

fun List<Element>.parseEpisodeAndVersion(offset: Int?, originalName: String? = null): Pair<String, Int>? {
    var episode = this.episode ?: return null
    val version = this.version
    if (offset != null && offset != 0) {
        var episodeDouble = episode.toDoubleOrNull() ?: return null
        val format = DecimalFormat("0.#")
        episodeDouble += offset
        if (episodeDouble >= 0) {
            episode = if (episodeDouble < 10) "0${format.format(episodeDouble)}" else format.format(episodeDouble)
        } else {
            Log.w("ParseEpisode for $originalName") { "Could not apply episode offset because resulting number would be <0!" }
        }
    }
    return episode to (version?.toIntOrNull() ?: 0)
}

fun parseEpisodeAndVersion(toParse: String, offset: Int?): Pair<String, Int>? {
    val parsed = parseMetadata(toParse)
    return parsed.parseEpisodeAndVersion(offset)
}