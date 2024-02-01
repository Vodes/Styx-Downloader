package moe.styx.downloader

import com.dgtlrepublic.anitomyj.AnitomyJ
import moe.styx.db.StyxDBClient
import moe.styx.db.getEntries
import moe.styx.downloader.parsing.ParseDenyReason
import moe.styx.downloader.parsing.ParseResult
import moe.styx.types.DownloadableOption
import moe.styx.types.DownloaderTarget
import moe.styx.types.eqI
import java.text.DecimalFormat
import kotlin.math.abs

fun getDBClient(database: String = "Styx2"): StyxDBClient {
    return StyxDBClient(
        "com.mysql.cj.jdbc.Driver",
        "jdbc:mysql://${Main.config.dbConfig.ip}/$database?" +
                "useUnicode=true&useJDBCCompliantTimezoneShift=true&useLegacyDatetimeCode=false&serverTimezone=Europe/Berlin",
        Main.config.dbConfig.user,
        Main.config.dbConfig.pass
    )
}

fun DownloadableOption.episodeWanted(toMatch: String, parent: DownloaderTarget, rss: Boolean = false): ParseResult {
    if (!this.matches(toMatch, rss)) {
        return ParseResult.FAILED(ParseDenyReason.NoOptionMatched)
    }
    val (episode, version) = parseEpisodeAndVersion(toMatch, episodeOffset)
        ?: return ParseResult.FAILED(ParseDenyReason.InvalidEpisodeNumber)
    // Check if we already have the episode in the database
    val dbEpisode = getDBClient().executeGet { getEntries(mapOf("mediaID" to parent.mediaID)) }
        .find { it.entryNumber.toDoubleOrNull() == episode.toDoubleOrNull() }

    // Check what "option" the episode in the database corresponds to (if same and not a different version return false)
    val existingOptionVal = listOf(parent).matchesAny(dbEpisode?.originalName, false)?.second?.priority ?: -1
    if (dbEpisode != null && existingOptionVal == priority) {
        val existingData = parseEpisodeAndVersion(dbEpisode.originalName ?: "", episodeOffset)
        if (existingData != null && version <= existingData.second)
            return ParseResult.DENIED(ParseDenyReason.SameVersionPresent)
    }
    // Return false if we already have a better version of this episode
    if (priority < existingOptionVal)
        return ParseResult.DENIED(ParseDenyReason.BetterVersionPresent)
    // Return false if a "worse" version is required to exist (for muxing purposes perhaps) but doesn't yet
    if ((abs(existingOptionVal - priority) > 2 || existingOptionVal < 0) && waitForPrevious)
        return ParseResult.DENIED(ParseDenyReason.WaitingForPreviousOption)

    // Yay we need this file
    return ParseResult.OK(parent, this)
}

fun List<DownloaderTarget>.episodeWanted(toMatch: String, rss: Boolean = false): ParseResult {
    val (target, option) = matchesAny(toMatch, rss) ?: return ParseResult.FAILED(ParseDenyReason.NoOptionMatched)
    return option.episodeWanted(toMatch, target, rss)
}

fun parseEpisodeAndVersion(toParse: String, offset: Int?): Pair<String, Int>? {
    // Space out stuff like S01E01v2 to be S01E01 v2 (because Anitomy bad)
    val fixPattern = "(?<whole>(?<ep>(?:S\\d+)?[E ]\\d+)(?<version>v\\d))".toRegex(RegexOption.IGNORE_CASE)
    var toParse = toParse
    val match = fixPattern.matchEntire(toParse)
    if (match != null) {
        toParse = toParse.replace(
            match.groups["whole"]!!.value,
            "%s %s".format(match.groups["ep"]!!.value, match.groups["version"]!!.value)
        )
    }
    // Other misc fixes that kinda fuck with anitomy
    // Single letter parts in scene naming, for example Invincible.2021.S02E01.A.LESSON.FOR.YOUR.NEXT.LIFE.1080p.AMZN.WEB-DL.DDP5.1.H.264-FLUX.mkv
    toParse = toParse.replaceFirst("\\.[A-Za-z]\\.".toRegex(), "")

    val parsed = AnitomyJ.parse(toParse)
    var episode = parsed.find { it.category.toString() eqI "kElementEpisodeNumber" }?.value ?: return null
    val version = parsed.find { it.category.toString() eqI "kElementReleaseVersion" }
    if (offset != null && offset != 0) {
        var episodeDouble = episode.toDoubleOrNull() ?: return null
        val format = DecimalFormat("0.#")
        episodeDouble += offset
        if (episodeDouble > 0) {
            episode = if (episodeDouble < 10) "0${format.format(episodeDouble)}" else format.format(episodeDouble)
        } else
            Log.w("ParseEpisode for $toParse") { "Could not apply episode offset because resulting number would be <0!" }
    }
    return episode to (version?.value?.toIntOrNull() ?: 0)
}

fun List<DownloaderTarget>.matchesAny(toMatch: String?, rss: Boolean = false): Pair<DownloaderTarget, DownloadableOption>? {
    if (toMatch == null)
        return null
    for (target in this) {
        val match = target.options.find { it.matches(toMatch, rss) }
        if (match != null)
            return target to match
    }
    return null
}

fun DownloadableOption.matches(toMatch: String, rss: Boolean = false): Boolean {
    var regex = this.fileRegex.toRegex(RegexOption.IGNORE_CASE)
    if (rss) {
        regex = if (!this.rssRegex.isNullOrBlank()) this.rssRegex!!.toRegex(RegexOption.IGNORE_CASE) else
            this.fileRegex.replace("\\.mkv", "").replace(".mkv", "").toRegex(RegexOption.IGNORE_CASE)
    }
    return regex.matches(if (!toMatch.endsWith(".mkv") && !rss) "$toMatch.mkv" else toMatch)
}