package moe.styx.downloader.parsing

import com.dgtlrepublic.anitomyj.AnitomyJ
import com.dgtlrepublic.anitomyj.Element
import moe.styx.common.extension.eqI
import moe.styx.downloader.utils.Log
import moe.styx.downloader.utils.RegexCollection
import java.text.DecimalFormat

typealias AnitomyResults = List<Element>

val AnitomyResults.season: String?
    get() = this.find { it.category == Element.ElementCategory.kElementAnimeSeason }?.value

val AnitomyResults.title: String?
    get() = this.find { it.category == Element.ElementCategory.kElementAnimeTitle }?.value

val AnitomyResults.episode: String?
    get() = this.find { it.category == Element.ElementCategory.kElementEpisodeNumber }?.value

val AnitomyResults.version: String?
    get() = this.find { it.category == Element.ElementCategory.kElementReleaseVersion }?.value

val AnitomyResults.group: String?
    get() = this.find { it.category == Element.ElementCategory.kElementReleaseGroup }?.value

val AnitomyResults.filename: String?
    get() = this.find { it.category == Element.ElementCategory.kElementFileName }?.value


fun parseMetadata(toParse: String): AnitomyResults {
    var adjusted = toParse

    val repackMatch = RegexCollection.repackRegex.find(adjusted)
    if (repackMatch != null)
        adjusted =
            adjusted.replace(repackMatch.groups[0]!!.value, repackMatch.groups[1]?.let { ".V${it.value.toIntOrNull()?.plus(1) ?: 2}." } ?: ".V2.")

    var match = RegexCollection.fixPattern.find(adjusted)
    if (match != null) {
        // Space out stuff like S01E01v2 to be S01E01 v2 (because Anitomy bad)
        adjusted = adjusted.replace(
            match.groups["whole"]!!.value,
            "%s %s".format(match.groups["ep"]!!.value, match.groups["version"]!!.value)
        )
    }
    match = RegexCollection.semiFixPattern.find(adjusted)
    if (match != null) {
        // Add S01 to standalone Exx
        val episode = match.groups["ep"]!!.value
        adjusted = adjusted.replaceFirst("E$episode", "S01E$episode")
    }

    // Other misc fixes that kinda fuck with anitomy
    // Single letter parts in scene naming, for example Invincible.2021.S02E01.A.LESSON.FOR.YOUR.NEXT.LIFE.1080p.AMZN.WEB-DL.DDP5.1.H.264-FLUX.mkv
    val singleLetterMatch = RegexCollection.singleLetterWithDot.find(adjusted)
    adjusted = if (singleLetterMatch != null) adjusted.replaceFirst(singleLetterMatch.groups[1]!!.value, ".") else adjusted
    adjusted = adjusted.replaceFirst(RegexCollection.crc32Regex, "")
    if (adjusted.contains("NanDesuKa", true)) {
        val oldFixMatch = RegexCollection.oldNandesukaFixRegex.find(adjusted)
        if (oldFixMatch != null) {
            adjusted = adjusted.replaceFirst(oldFixMatch.groups["site"]!!.value, "")
        }
    }

    val result = AnitomyJ.parse(adjusted).toMutableList()

    // Sometimes it doesn't seem to parse the season at all.
    val episode = result.episode
    if (episode == null) {
        val zeroMatch = RegexCollection.seasonZeroRegex.find(adjusted)
        if (zeroMatch != null) {
            result.add(Element(Element.ElementCategory.kElementEpisodeNumber, zeroMatch.groups["ep"]!!.value))
            result.removeIf { it.category == Element.ElementCategory.kElementAnimeSeason }
            result.add(Element(Element.ElementCategory.kElementAnimeSeason, "00"))
            Log.w { "Had to 'manually' parse Season 0 episode for: $toParse" }
        }
    }
    if (result.group.isNullOrBlank() && !result.filename.isNullOrBlank()) {
        var tempName = result.filename!!
        result.filter { it.category != Element.ElementCategory.kElementEpisodeTitle && it.category != Element.ElementCategory.kElementFileName }
            .sortedByDescending { it.value.length }
            .forEach {
                val valueAsNumber = it.value.toDoubleOrNull()
                if (valueAsNumber != null) {
                    tempName = tempName.replaceFirst("S${it.value}", "", true)
                    tempName = tempName.replaceFirst("E${it.value}", "", true)
                    tempName = tempName.replaceFirst("v${it.value}", "", true)
                } else {
                    tempName = tempName.replace(it.value, "", true)
                    tempName = tempName.replace(it.value.replace(" ", ".", true), "", true)
                }
            }
        val groupMatch = RegexCollection.p2pGroupRegex.matchEntire(tempName)
        if (groupMatch != null) {
            result.add(Element(Element.ElementCategory.kElementReleaseGroup, groupMatch.groups["name"]!!.value))
        }
    }
    return result
}

fun List<Element>.parseEpisodeAndVersion(offset: Int?): Pair<String, Int>? {
    var episode = this.episode ?: return null
    val version = this.version
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
    return episode to (version?.toIntOrNull() ?: 0)
}

fun parseEpisodeAndVersion(toParse: String, offset: Int?): Pair<String, Int>? {
    val parsed = parseMetadata(toParse)
    return parsed.parseEpisodeAndVersion(offset)
}