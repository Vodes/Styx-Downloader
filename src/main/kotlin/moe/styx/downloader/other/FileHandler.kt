package moe.styx.downloader.other

import moe.styx.db.getMedia
import moe.styx.downloader.getDBClient
import moe.styx.downloader.parsing.AnitomyResults
import moe.styx.downloader.parsing.parseEpisodeAndVersion
import moe.styx.downloader.parsing.parseMetadata
import moe.styx.downloader.utils.*
import moe.styx.types.DownloadableOption
import moe.styx.types.DownloaderTarget
import moe.styx.types.eqI
import java.io.File
import java.text.DecimalFormat

private val epFormat = DecimalFormat("0.#")

fun handleFile(file: File, target: DownloaderTarget, option: DownloadableOption) {
    val anitomyResults = parseMetadata(file.name)
    val (episodeWithOffset, version) = anitomyResults.parseEpisodeAndVersion(option.episodeOffset) ?: return
    val outname = (if (option.overrideNamingTemplate.isNullOrBlank()) target.namingTemplate else option.overrideNamingTemplate)!!
        .fillTokens(target, option, anitomyResults)
    val outDir = target.outputDir.fillTokens(target, option, anitomyResults)
}

fun String.fillTokens(
    target: DownloaderTarget,
    option: DownloadableOption,
    anitomyResults: AnitomyResults
): String {
    var filled = this
    val media = getDBClient().executeGet { getMedia(mapOf("GUID" to target.mediaID)) }.first()
    val (episode, _) = anitomyResults.parseEpisodeAndVersion(option.episodeOffset)!!

    filled = filled.replaceAll(media.name, "%name%")
    filled = filled.replaceAll(media.nameEN ?: "", "%en%", "%english%")
    filled = filled.replaceAll(media.nameJP ?: "", "%rom%", "%romaji%")
    filled = filled.replace(
        TokenRegex.absoluteEpisodeToken,
        anitomyResults.find { it.category.toString() eqI "kElementEpisodeNumber" }?.value ?: ""
    )

    val episodeMatch = TokenRegex.regularEpisodeToken.findAll(filled).toList()
    if (episodeMatch.isNotEmpty()) {
        episodeMatch.forEach {
            val toReplace = it.groups[0]!!.value
            filled = if (it.wantsEPrefix()) filled.replace(toReplace, "E$episode") else filled.replace(toReplace, episode)
        }
    }

    val offsetEpisodeMatch = TokenRegex.offsetEpisodeToken.findAll(filled).toList()
    if (offsetEpisodeMatch.isNotEmpty()) {
        offsetEpisodeMatch.forEach {
            val toReplace = it.groups[0]!!.value
            val episodeDouble = episode.toDouble()
            val offset = it.groups["offset"]!!.value.toDouble()
            filled = filled.replace(toReplace, epFormat.format(episodeDouble + offset).padStart(2, '0'))
        }
    }

    val group = anitomyResults.find { it.category.toString() eqI "kElementReleaseGroup" }?.value
    val groupMatch = TokenRegex.groupToken.findAll(filled).toList()
    if (groupMatch.isNotEmpty()) {
        if (group.isNullOrBlank()) {
            groupMatch.forEach { filled = filled.replace(it.groups[0]!!.value, "") }
        } else {
            groupMatch.forEach {
                val toReplace = it.groups[0]!!.value
                filled = if (it.wantsBrackets()) filled.replace(toReplace, "[$group]")
                else if (it.wantsParentheses()) filled.replace(toReplace, "($group)")
                else filled.replace(toReplace, group)
            }
        }
    }

    return filled
}