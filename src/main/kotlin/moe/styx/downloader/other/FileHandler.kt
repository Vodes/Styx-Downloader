package moe.styx.downloader.other

import kotlinx.datetime.Clock
import kotlinx.serialization.encodeToString
import moe.styx.common.data.*
import moe.styx.common.data.MediaInfo
import moe.styx.common.extension.*
import moe.styx.db.*
import moe.styx.downloader.Main
import moe.styx.downloader.getDBClient
import moe.styx.downloader.other.MetadataFetcher.addEntry
import moe.styx.downloader.parsing.AnitomyResults
import moe.styx.downloader.parsing.parseEpisodeAndVersion
import moe.styx.downloader.parsing.parseMetadata
import moe.styx.downloader.utils.*
import moe.styx.downloader.utils.Log
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.text.DecimalFormat
import java.util.*

private val epFormat = DecimalFormat("0.#")

fun handleFile(file: File, target: DownloaderTarget, option: DownloadableOption): Boolean {
    val anitomyResults = parseMetadata(file.name)
    val (episodeWithOffset, version) = anitomyResults.parseEpisodeAndVersion(option.episodeOffset) ?: return false
    val media = getDBClient().executeGet { getMedia(mapOf("GUID" to target.mediaID)) }.firstOrNull() ?: return false
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
    if (option.processingOptions != null && option.processingOptions!!.needsMuxtools()) {
        val logFile = File(muxDir, "Mux Log - ${Log.getFormattedTime().replace(":", "-")}.txt")
        val commands = getBaseCommand(option.processingOptions!!)
        commands.add("-o=${File(muxDir, output.name)}")

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
                return false
            }
            commands.add(previous.filePath)
            logFile.writeText("Input 1: ${File(previous.filePath).name}\nInput 2: ${file.name}\n")
        } else
            logFile.writeText("Input 1: ${file.name}\n")

        logFile.writeText(
            logFile.readText() +
                    "ProcessingOptions:\n${Main.toml.encodeToString(option.processingOptions!!)}\n\n" +
                    "---------- Muxtools Log below ----------\n\n"
        )
        commands.add(file.absolutePath)
        val result = ProcessBuilder(commands)
            .redirectOutput(ProcessBuilder.Redirect.appendTo(logFile))
            .redirectError(ProcessBuilder.Redirect.appendTo(logFile))
            .directory(muxDir).start().waitFor()
        val muxedFile = File(muxDir, output.name)
        if (result == 0 && muxedFile.exists()) {
            Files.move(muxedFile.toPath(), output.toPath(), StandardCopyOption.REPLACE_EXISTING)
            file.delete()
        } else
            return false
    } else {
        Files.move(file.toPath(), output.toPath(), StandardCopyOption.REPLACE_EXISTING)
    }
    output.setTitleAndFixMeta(title)

    if (option.processingOptions != null && option.processingOptions!!.fixTagging) {
        val commands = listOf(
            getExecutableFromPath("python")!!.absolutePath,
            "-m",
            "muxtools_styx",
            "-o=${output.absolutePath}",
            "--fix-tagging",
            output.absolutePath
        )
        ProcessBuilder(commands).redirectOutput(ProcessBuilder.Redirect.DISCARD).start().waitFor()
    }

    if (previous != null) {
        val previousFile = File(previous.filePath)
        if (previousFile.exists() && previousFile != output)
            previousFile.delete()
    }

    getDBClient().executeAndClose {
        val time = Clock.System.now().epochSeconds
        val entry = previous?.copy(filePath = output.absolutePath, fileSize = output.length(), originalName = file.name)
            ?: MediaEntry(
                UUID.randomUUID().toString().uppercase(),
                media.GUID,
                time,
                episodeWithOffset,
                null,
                null,
                null,
                null,
                null,
                output.absolutePath,
                output.length(),
                file.name
            )

        if (!save(entry)) {
            Log.e("FileHandler for file: ${output.name}") { "Could not add entry to database!" }
            return@executeAndClose
        }

        val mediaInfoResult = output.getMediaInfo()
        if (mediaInfoResult != null) {
            save(
                MediaInfo(
                    entry.GUID,
                    mediaInfoResult.videoCodec(),
                    mediaInfoResult.videoBitDepth(),
                    mediaInfoResult.videoResolution(),
                    mediaInfoResult.hasEnglishDub().toInt(),
                    mediaInfoResult.hasGermanDub().toInt(),
                    mediaInfoResult.hasGermanSub().toInt()
                )
            )
        }

        if (option.addToLegacyDatabase) {
            getDBClient("Styx-Library").executeAndClose Legacy@{
                val anime = getLegacyAnime(mapOf("GUID" to media.GUID)).firstOrNull() ?: return@Legacy
                val legacyEP = getLegacyEpisodes(mapOf("Name" to anime.name)).find { it.ep.toDoubleOrNull() == entry.entryNumber.toDoubleOrNull() }
                val convertedSize = String.format("%.2f", entry.fileSize.toDouble() / (1024 * 1024)).toDouble()
                if (legacyEP != null)
                    save(
                        legacyEP.copy(
                            filePath = entry.filePath,
                            fileSize = convertedSize,
                            prevName = entry.originalName
                        )
                    )
                else {
                    save(
                        LegacyEpisodeInfo(
                            UUID.randomUUID().toString().uppercase(),
                            entry.GUID,
                            time.toDateString(),
                            anime.name,
                            entry.entryNumber,
                            entry.originalName,
                            null,
                            null,
                            null,
                            null,
                            entry.filePath,
                            convertedSize
                        )
                    )
                }
            }
        }

        if (previous == null) {
            notifyDiscord(entry, media)
            addEntry(entry)
        }

        Main.updateEntryChanges()
    }
    return true
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

    var group = anitomyResults.find { it.category.toString() eqI "kElementReleaseGroup" }?.value
    if (group != null && group.containsAny("JapDub", "GerJapDub", "E-AC-3", "EAC3", "EAC-3", "GerEngSub", "GerSub", "EngSub")) {
        group = "GerFTP"
    }
    if (option.processingOptions?.needsMuxtools() == true) {
        group = if (group.isNullOrBlank()) "Styx" else "$group-Styx"
    }
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