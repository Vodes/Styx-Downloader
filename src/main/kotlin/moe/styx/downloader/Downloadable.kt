package moe.styx.downloader

import moe.styx.common.data.*
import moe.styx.common.extension.equalsAny
import moe.styx.db.tables.MediaEntryTable
import moe.styx.downloader.parsing.ParseDenyReason
import moe.styx.downloader.parsing.ParseResult
import moe.styx.downloader.parsing.parseEpisodeAndVersion
import moe.styx.downloader.utils.RegexCollection
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll
import java.io.File
import kotlin.math.abs

val specialNumbers = listOf("2.0", "5.1", "7.1", "6.0", "5.0")

data class ExistingDownloadState(
    val optionPriority: Int = -1,
    val version: Int? = null,
    val fileExists: Boolean = false,
)

fun DownloadableOption.episodeWanted(
    toMatch: String,
    parentDir: String?,
    parent: DownloaderTarget,
    rss: Boolean = false
): ParseResult {
    if (!this.matches(toMatch, parentDir, rss)) {
        return ParseResult.FAILED(ParseDenyReason.NoOptionMatched)
    }
    // Ignore stuff like "One Piece - Egghead SP1" or "One Piece E1078.5"
    if (downloaderConfig.ignoreSpecialsAndPoint5) {
        val matches = RegexCollection.specialEpisodeRegex.findAll(toMatch)
        for (match in matches) {
            val group = match.groups["num"]?.value ?: continue
            if (!group.trim().equalsAny(specialNumbers)) {
                return ParseResult.DENIED(ParseDenyReason.IsSpecialOrFiller)
            }
        }
    }

    val (episode, version) = parseEpisodeAndVersion(toMatch, episodeOffset)
        ?: return ParseResult.FAILED(ParseDenyReason.InvalidEpisodeNumber)

    // Another specials check, just to be safe
    if (downloaderConfig.ignoreSpecialsAndPoint5 && episode.toIntOrNull() == null)
        return ParseResult.DENIED(ParseDenyReason.IsSpecialOrFiller)

    // Check if we already have the episode in the database
    val dbEpisode = runCatching {
        dbClient.transaction {
            MediaEntryTable.query { selectAll().where { mediaID eq parent.mediaID }.toList() }
        }.find {
            it.entryNumber.toDoubleOrNull() == episode.toDoubleOrNull()
        }
    }.onFailure {
        it.printStackTrace()
        return ParseResult.DENIED(ParseDenyReason.DatabaseConnectionFailed)
    }.getOrNull()

    val existingState = ExistingDownloadState(
        listOf(parent).getExistingOptionPriority(dbEpisode?.originalName, dbEpisode?.originalParentFolder),
        dbEpisode?.originalName?.let { parseEpisodeAndVersion(it, episodeOffset)?.second },
        dbEpisode?.let { File(it.filePath).exists() } == true
    )
    return getDownloadDecision(version, parent, parentDir, existingState)
}

fun List<DownloaderTarget>.episodeWanted(toMatch: String, parentDir: String?, rss: Boolean = false): ParseResult {
    val (target, option) = matchesAny(toMatch, parentDir, rss)
        ?: return ParseResult.FAILED(ParseDenyReason.NoOptionMatched)
    return option.episodeWanted(toMatch, parentDir, target, rss)
}

fun List<DownloaderTarget>.matchesAny(
    toMatch: String?,
    parentDir: String?,
    rss: Boolean = false
): Pair<DownloaderTarget, DownloadableOption>? {
    if (toMatch == null)
        return null
    for (target in this) {
        val match = target.options.find { it.matches(toMatch, parentDir, rss) }
        if (match != null)
            return target to match
    }
    return null
}

fun List<DownloaderTarget>.getExistingOptionPriority(toMatch: String?, parentDir: String?): Int {
    return matchesAny(toMatch, parentDir, false)?.second?.priority ?: -1
}

fun DownloadableOption.getDownloadDecision(
    version: Int,
    parent: DownloaderTarget,
    parentDir: String?,
    existingState: ExistingDownloadState = ExistingDownloadState()
): ParseResult {
    if (existingState.optionPriority == priority && existingState.version != null && version <= existingState.version && existingState.fileExists)
        return ParseResult.DENIED(ParseDenyReason.SameVersionPresent)
    if (priority < existingState.optionPriority)
        return ParseResult.DENIED(ParseDenyReason.BetterVersionPresent)

    val diff = abs(existingState.optionPriority - priority)
    if ((diff > 1 || existingState.optionPriority < 0) && waitForPrevious)
        return ParseResult.DENIED(ParseDenyReason.WaitingForPreviousOption)

    return ParseResult.OK(parent, this, parentDir)
}

fun DownloadableOption.matches(toMatch: String, parentDir: String?, rss: Boolean = false): Boolean {
    val sanitized = toMatch.replace(RegexCollection.repackRegex, "")
    val nameMatches = if (useTokens) matchesTokens(sanitized, rss) else matchesRegex(sanitized, rss)
    return nameMatches && matchesParentFolder(parentDir)
}

fun DownloadableOption.matchesRegex(toMatch: String, rss: Boolean = false): Boolean {
    var regex = this.fileRegex.toRegex(RegexOption.IGNORE_CASE)
    var nameMatches =
        regex.find(if (!(toMatch.endsWith(".mkv") || toMatch.endsWith(".mka")) && !rss) "$toMatch.mkv" else toMatch) != null
    if (rss) {
        regex = if (!this.rssRegex.isNullOrBlank()) this.rssRegex!!.toRegex(RegexOption.IGNORE_CASE) else
            this.fileRegex.replace("\\.mkv", "").replace(".mkv", "").toRegex(RegexOption.IGNORE_CASE)
        nameMatches = nameMatches || regex.find(toMatch) != null
    }
    return nameMatches
}

fun DownloadableOption.matchesTokens(toMatch: String, rss: Boolean = false): Boolean {
    val activeGroups = tokenGroups.filter { it.matchesTarget(rss) }
    if (activeGroups.isEmpty())
        return false

    return activeGroups.all { it.matchesTokenGroup(toMatch) }
}

fun DownloadableOption.matchesParentFolder(parentDir: String?): Boolean {
    return if (this.ignoreParentFolder || this.source != SourceType.FTP || parentDir == null)
        true
    else
        this.sourcePath?.contains(parentDir, true) == true
}

fun TokenGroup.matchesTarget(rss: Boolean): Boolean {
    return target == TokenTarget.BOTH || (rss && target == TokenTarget.RSS) || (!rss && target == TokenTarget.FILE)
}

fun TokenGroup.matchesTokenGroup(toMatch: String): Boolean {
    if (tokens.isEmpty())
        return false

    val matches = tokens.map { token ->
        when (method) {
            TokenMatchMethod.CONTAINS -> toMatch.contains(token, true)
            TokenMatchMethod.REGEX -> runCatching {
                token.toRegex(RegexOption.IGNORE_CASE).find(toMatch) != null
            }.onFailure { it.printStackTrace() }.getOrDefault(false)
        }
    }

    return when (matchType) {
        TokenMatchType.ALL -> matches.all { it }
        TokenMatchType.ANY -> matches.any { it }
        TokenMatchType.NONE -> matches.none { it }
    }
}
