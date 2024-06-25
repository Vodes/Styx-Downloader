package moe.styx.downloader.parsing

import com.dgtlrepublic.anitomyj.AnitomyJ
import com.dgtlrepublic.anitomyj.Element
import moe.styx.common.extension.eqI
import moe.styx.downloader.utils.Log
import moe.styx.downloader.utils.RegexCollection
import java.text.DecimalFormat

typealias AnitomyResults = List<Element>

fun parseMetadata(toParse: String): AnitomyResults {
    var adjusted = toParse

    val repackMatch = RegexCollection.repackRegex.find(adjusted)
    if (repackMatch != null)
        adjusted =
            adjusted.replace(repackMatch.groups[0]!!.value, repackMatch.groups[1]?.let { ".V${it.value.toIntOrNull()?.plus(1) ?: 2}." } ?: ".V2.")

    val match = RegexCollection.fixPattern.matchEntire(adjusted)
    if (match != null) {
        // Space out stuff like S01E01v2 to be S01E01 v2 (because Anitomy bad)
        adjusted = adjusted.replace(
            match.groups["whole"]!!.value,
            "%s %s".format(match.groups["ep"]!!.value, match.groups["version"]!!.value)
        )
    }
    // Other misc fixes that kinda fuck with anitomy
    // Single letter parts in scene naming, for example Invincible.2021.S02E01.A.LESSON.FOR.YOUR.NEXT.LIFE.1080p.AMZN.WEB-DL.DDP5.1.H.264-FLUX.mkv
    adjusted = adjusted.replaceFirst(RegexCollection.singleLetterWithDot, " ")
    adjusted = adjusted.replaceFirst(RegexCollection.crc32Regex, "")

    val result = AnitomyJ.parse(adjusted).toMutableList()

    // Sometimes it doesn't seem to parse the season at all.
    val episode = result.find { it.category.toString() eqI "kElementEpisodeNumber" }?.value
    if (episode == null) {
        val zeroMatch = RegexCollection.seasonZeroRegex.find(adjusted)
        if (zeroMatch != null) {
            result.add(Element(Element.ElementCategory.kElementEpisodeNumber, zeroMatch.groups["ep"]!!.value))
            result.removeIf { it.category == Element.ElementCategory.kElementAnimeSeason }
            result.add(Element(Element.ElementCategory.kElementAnimeSeason, "00"))
            Log.w { "Had to 'manually' parse Season 0 episode for: $toParse" }
        }
    }
    return result
}

fun List<Element>.parseEpisodeAndVersion(offset: Int?): Pair<String, Int>? {
    var episode = this.find { it.category.toString() eqI "kElementEpisodeNumber" }?.value ?: return null
    val version = this.find { it.category.toString() eqI "kElementReleaseVersion" }
    if (offset != null && offset != 0) {
        var episodeDouble = episode.toDoubleOrNull() ?: return null
        val format = DecimalFormat("0.#")
        episodeDouble += offset
        if (episodeDouble >= 0) {
            episode = if (episodeDouble < 10) "0${format.format(episodeDouble)}" else format.format(episodeDouble)
        } else {
            val fileName = this.find { it.category.toString() eqI "kElementFileName" }?.value
            Log.w("ParseEpisode for $fileName") { "Could not apply episode offset because resulting number would be <0!" }
        }
    }
    return episode to (version?.value?.toIntOrNull() ?: 0)
}

fun parseEpisodeAndVersion(toParse: String, offset: Int?): Pair<String, Int>? {
    val parsed = parseMetadata(toParse)
    return parsed.parseEpisodeAndVersion(offset)
}