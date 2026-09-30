package moe.styx.downloader.other

import kotlinx.serialization.encodeToString
import moe.styx.common.config.UnifiedConfig
import moe.styx.common.data.*
import moe.styx.common.data.MediaInfo
import moe.styx.common.extension.*
import moe.styx.common.toml
import moe.styx.common.util.launchGlobal
import moe.styx.db.tables.ChangesTable
import moe.styx.db.tables.MediaEntryTable
import moe.styx.db.tables.MediaInfoTable
import moe.styx.db.tables.MediaTable
import moe.styx.downloader.dbClient
import moe.styx.downloader.other.MetadataFetcher.addEntry
import moe.styx.downloader.parsing.AnitomyResults
import moe.styx.downloader.parsing.group
import moe.styx.downloader.parsing.isLikelyFtpRelease
import moe.styx.downloader.parsing.parseEpisodeAndVersion
import moe.styx.downloader.parsing.parseMetadata
import moe.styx.downloader.parsing.season
import moe.styx.downloader.utils.*
import moe.styx.downloader.utils.Log
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.text.DecimalFormat
import java.util.*
import kotlin.time.Clock

private val epFormat = DecimalFormat("0.#")

fun handleFile(file: File, parentDir: String?, target: DownloaderTarget, option: DownloadableOption): Boolean {
    val anitomyResults = parseMetadata(file.name)
    val (episodeWithOffset, version) = anitomyResults.parseEpisodeAndVersion(option.episodeOffset, file.name) ?: return false
    val media = dbClient.transaction { MediaTable.query { selectAll().where { GUID eq target.mediaID }.toList() } }.firstOrNull() ?: return false
    var outname = (if (option.overrideNamingTemplate.isNullOrBlank()) target.namingTemplate else option.overrideNamingTemplate)!!
        .fillTokens(media, option, anitomyResults, file.name).toFileSystemCompliantName()
    if (!outname.endsWith(".mkv", true))
        outname = "$outname.mkv"
    val title = (if (option.overrideTitleTemplate.isNullOrBlank()) target.titleTemplate else option.overrideTitleTemplate)!!
        .fillTokens(media, option, anitomyResults, file.name)
    val filledDir = target.outputDir.fillTokens(media, option, anitomyResults, file.name)
    val outDir = File(File(filledDir).parentFile, File(filledDir).name.toFileSystemCompliantName())
    outDir.mkdirs()
    val muxDir = File(UnifiedConfig.configFile.parentFile, "Muxing")
    muxDir.mkdirs()

    var output = File(outDir, outname)
    val previous = dbClient.transaction { MediaEntryTable.query { selectAll().where { mediaID eq media.GUID }.toList() } }
        .find { it.entryNumber.toDoubleOrNull() == episodeWithOffset.toDoubleOrNull() }
    val donor = previous?.let { File(it.filePath) }?.takeIf { it.isFile }
    val effective = runCatching {
        prepareProcessing(option.processingOptions, donor != null, option.waitForPrevious)
    }.getOrElse {
        Log.e("FileHandler for File: ${file.name}") { it.message ?: "Invalid processing options" }
        return false
    }
    if (effective?.skipped?.isNotEmpty() == true)
        Log.w("FileHandler for File: ${file.name}") { "No previous file; skipping: ${effective.skipped.joinToString()}" }
    val processing = effective?.options
    val needsProcessing = processing?.needsMuxtools() == true || hasMuxtoolsTokens(outname) || hasMuxtoolsTokens(title)
    if (needsProcessing) {
        val logFile = File(muxDir, "Mux Log - ${UUID.randomUUID()}.txt")
        val stage = Files.createTempDirectory(muxDir.toPath(), "mux-").toFile()
        val result = runCatching {
            val python = getExecutableFromPath("python") ?: error("Could not find python or python3 in PATH")
            val commands = buildProcessingCommand(
                python, file, donor, processing ?: ProcessingOptions(removeUnnecessary = false, fixTagging = false),
                stage, outname, title, episodeWithOffset, media.nameEN ?: media.name,
                supplementalInfo = parentDir,
                donorSupplementalInfo = previous?.originalParentFolder,
            )
            logFile.writeText("Target: ${file.absolutePath}\nDonor: ${donor?.absolutePath}\n" +
                "ProcessingOptions:\n${processing?.let { toml.encodeToString(it) }}\n\n")
            val muxedFile = runProcessingCommand(commands, stage, logFile)
            output = File(outDir, muxedFile.name.toFileSystemCompliantName())
            Files.move(muxedFile.toPath(), output.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
        if (result.isFailure) {
            Log.e("FileHandler for File: ${file.name}") { "${result.exceptionOrNull()?.message}; log: ${logFile.absolutePath}" }
            return false
        }
        stage.deleteRecursively()
        file.delete()
    } else {
        Files.move(file.toPath(), output.toPath(), StandardCopyOption.REPLACE_EXISTING)
    }
    output.setTitleAndFixMeta(if (needsProcessing) null else title)

    val mediaInfoResult = output.getMediaInfo()

    if (output.name.containsAny("%jp%", "%res%")) {
        val resolution = mediaInfoResult?.tracks?.find { it.type eqI "video" }?.let { it.height ?: "1080" } ?: "1080"
        var jpCodec = mediaInfoResult?.tracks?.find { it.type eqI "audio" && it.matchesLanguage("ja", "jpn") }?.format ?: "AAC"
        if (parentDir?.contains("ADN") == true) {
            jpCodec = "qAAC"
        }
        val newName = output.name.replace("%res%", "${resolution}p", true).replace("%jp%", jpCodec, true)
        val newFile = File(output.parentFile, newName)
        if (output.renameTo(newFile))
            output = newFile
        else {
            Log.e("Source: ${output.name}") { "Could not rename file to '${newFile.name}'" }
        }
    }

    if (previous != null) {
        val previousFile = File(previous.filePath)
        if (previousFile.exists() && previousFile != output)
            previousFile.delete()
    }

    val time = Clock.System.now().epochSeconds
    val entry = previous?.copy(filePath = output.absolutePath, fileSize = output.length(), originalName = file.name, originalParentFolder = parentDir)
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
            file.name,
            parentDir
        )

    val result = dbClient.transaction { MediaEntryTable.upsertItem(entry).insertedCount.toBoolean() }
    if (!result) {
        Log.e("FileHandler for file: ${output.name}") { "Could not add entry to database!" }
        return false
    }
    dbClient.transaction {
        if (mediaInfoResult != null) {
            MediaInfoTable.upsertItem(
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
    }
    if (previous == null) {
        notifyDiscord(entry, media)
        addEntry(entry)
        launchGlobal {
            runCatching { updateMetadataForEntry(entry, media) }
        }
    }

    dbClient.transaction { ChangesTable.setToNow(false, true) }
    return true
}

fun String.fillTokens(
    media: Media,
    option: DownloadableOption,
    anitomyResults: AnitomyResults,
    originalName: String
): String {
    var filled = this.trim()
    val (episode, _) = anitomyResults.parseEpisodeAndVersion(option.episodeOffset, originalName)!!

    filled = filled.replaceAll(media.name, "%name%")
    filled = filled.replaceAll(media.nameEN ?: "", "%en%", "%english%").trim()
    filled = filled.replaceAll(media.nameJP ?: "", "%rom%", "%romaji%").trim()
    filled = filled.replace(
        TokenRegex.absoluteEpisodeToken,
        anitomyResults.season ?: ""
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

    var group = anitomyResults.group
    if(anitomyResults.isLikelyFtpRelease)
        group = "GerFTP"

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

private fun File.setTitleAndFixMeta(title: String?): Boolean {
    val exe = getExecutableFromPath("mkvpropedit")
    if (exe == null) {
        Log.w { "Could not find mkvpropedit in PATH or muxtools managed binaries!" }
        return false
    }
    val commands = mutableListOf(
        exe.absolutePath,
        this.absolutePath,
        "--add-track-statistics-tags",
        "--set",
        "writing-application=Styx Muxing Service v69.0.0 ('Sneedmode') 64-bit @Vodes"
    )
    if (title != null) commands.addAll(listOf("--edit", "info", "--set", "title=$title"))
    return ProcessBuilder(commands).redirectError(ProcessBuilder.Redirect.INHERIT).redirectOutput(ProcessBuilder.Redirect.DISCARD).start()
        .waitFor() == 0
}

