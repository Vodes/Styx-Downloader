package moe.styx.downloader.other

import kotlinx.serialization.encodeToString
import moe.styx.db.getEntries
import moe.styx.db.getMedia
import moe.styx.downloader.Main
import moe.styx.downloader.getDBClient
import moe.styx.downloader.parsing.AnitomyResults
import moe.styx.downloader.parsing.parseEpisodeAndVersion
import moe.styx.downloader.parsing.parseMetadata
import moe.styx.downloader.utils.*
import moe.styx.downloader.utils.Log
import moe.styx.types.*
import java.io.File
import java.text.DecimalFormat

private val epFormat = DecimalFormat("0.#")

fun handleFile(file: File, target: DownloaderTarget, option: DownloadableOption) {
    val anitomyResults = parseMetadata(file.name)
    val (episodeWithOffset, version) = anitomyResults.parseEpisodeAndVersion(option.episodeOffset) ?: return
    val media = getDBClient().executeGet { getMedia(mapOf("GUID" to target.mediaID)) }.firstOrNull() ?: return
    var outname = (if (option.overrideNamingTemplate.isNullOrBlank()) target.namingTemplate else option.overrideNamingTemplate)!!
        .fillTokens(media, option, anitomyResults).toFileSystemCompliantName()
    if (!outname.endsWith(".mkv", true))
        outname = "$outname.mkv"
    val title = (if (option.overrideTitleTemplate.isNullOrBlank()) target.titleTemplate else option.overrideTitleTemplate)!!
        .fillTokens(media, option, anitomyResults)
    val filledDir = target.outputDir.fillTokens(media, option, anitomyResults)
    val outDir = File(File(filledDir).parentFile, File(filledDir).name.toFileSystemCompliantName())
    outDir.mkdirs()
    val muxDir = File(Main.appDir, "Muxing")
    muxDir.mkdirs()

    val output = File(outDir, outname)
    val previous = getDBClient().executeGet { getEntries(mapOf("mediaID" to media.GUID)) }
        .find { it.entryNumber.toDoubleOrNull() == episodeWithOffset.toDoubleOrNull() }
    if (option.processingOptions != null) {
        val logFile = File(muxDir, "Mux Log - ${Log.getFormattedTime().replace(":", "-")}.txt")
        val commands = getBaseCommand(option.processingOptions!!)
        commands.add("-o=${output.absolutePath}")

        if (arrayOf(
                option.processingOptions!!.keepAudioOfPrevious,
                option.processingOptions!!.keepVideoOfPrevious,
                option.processingOptions!!.keepBetterAudio,
                option.processingOptions!!.keepSubsOfPrevious,
                option.processingOptions!!.keepNonEnglish,
                option.processingOptions!!.removeNewSubs,
                option.processingOptions!!.manualSubSync != 0L,
                option.processingOptions!!.manualAudioSync != 0L
            ).any { it }
        ) {
            if (previous == null || !File(previous.filePath).exists()) {
                Log.w("FileHandler for File: ${file.name}") { "No processing applied due to missing a previous entry." }
                return
            }
            commands.add(previous.filePath)
            logFile.writeText("Input 1: ${File(previous.filePath).name}\nInput2: ${file.name}\n")
        } else
            logFile.writeText("Input 1: ${file.name}\n")

        logFile.writeText(
            logFile.readText() +
                    "ProcessingOptions:\n${Main.toml.encodeToString(option.processingOptions!!)}\n\n" +
                    "---------- Muxtools Log below ----------\n\n"
        )
        commands.add(file.absolutePath)
        ProcessBuilder(commands)
            .redirectOutput(ProcessBuilder.Redirect.appendTo(logFile))
            .redirectError(ProcessBuilder.Redirect.appendTo(logFile))
            .directory(muxDir).start().waitFor()
    } else {
        file.renameTo(output)
    }
    output.setTitleAndFixMeta(title)

    //TODO: Add to database, delete old file, add mediainfo to database, delete
}

fun String.fillTokens(
    media: Media,
    option: DownloadableOption,
    anitomyResults: AnitomyResults
): String {
    var filled = this.trim()
    val (episode, _) = anitomyResults.parseEpisodeAndVersion(option.episodeOffset)!!

    filled = filled.replaceAll(media.name, "%name%")
    filled = filled.replaceAll(media.nameEN ?: "", "%en%", "%english%").trim()
    filled = filled.replaceAll(media.nameJP ?: "", "%rom%", "%romaji%").trim()
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
            groupMatch.forEach { filled = filled.replace(it.groups[0]!!.value, "").trim() }
        } else {
            groupMatch.forEach {
                val toReplace = it.groups[0]!!.value
                filled = if (it.wantsBrackets()) filled.replace(toReplace, "[$group]")
                else if (it.wantsParentheses()) filled.replace(toReplace, "($group)")
                else filled.replace(toReplace, group)
            }
        }
    }
    return filled.trim()
}

private fun File.setTitleAndFixMeta(title: String): Boolean {
    val exe = getExecutableFromPath("mkvpropedit")
    if (exe == null) {
        Log.w { "Could not find mkvpropedit in path!" }
        return false
    }
    val commands = listOf(
        exe.absolutePath,
        this.absolutePath,
        "--add-track-statistics-tags",
        "--set",
        "writing-application=Styx Muxing Service v69.0.0 ('Sneedmode') 64-bit @Vodes",
        "--edit",
        "info",
        "--set",
        "title=$title"
    )
    return ProcessBuilder(commands).redirectError(ProcessBuilder.Redirect.INHERIT).redirectOutput(ProcessBuilder.Redirect.DISCARD).start()
        .waitFor() == 0
}

private fun getBaseCommand(processingOptions: ProcessingOptions): MutableList<String> {
    val commands = mutableListOf(getExecutableFromPath("python")!!.absolutePath, "-m", "muxtools_styx", "-v")

    if (processingOptions.restyleSubs)
        commands.add("--restyle-subs")
    if (processingOptions.keepBetterAudio)
        commands.add("--best-audio")
    if (processingOptions.keepVideoOfPrevious)
        commands.add("--keep-video")
    if (processingOptions.keepAudioOfPrevious)
        commands.add("--keep-audio")
    if (processingOptions.keepSubsOfPrevious)
        commands.add("--keep-subs")
    if (processingOptions.removeNewSubs)
        commands.add("--discard-new-subs")
    if (processingOptions.keepNonEnglish)
        commands.add("--keep-non-english")
    if (processingOptions.tppSubs)
        commands.add("-tpp")
    if (processingOptions.sushiSubs)
        commands.add("-sushi")
    if (processingOptions.manualAudioSync != 0L)
        commands.add("--audio-sync=${processingOptions.manualAudioSync}")
    if (processingOptions.manualSubSync != 0L)
        commands.add("--sub-sync=${processingOptions.manualSubSync}")

    return commands
}