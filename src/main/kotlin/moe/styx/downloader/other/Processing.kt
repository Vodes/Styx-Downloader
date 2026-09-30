package moe.styx.downloader.other

import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import moe.styx.common.data.ProcessingOptions
import moe.styx.common.json
import java.io.File

internal data class EffectiveProcessing(val options: ProcessingOptions, val skipped: List<String>)

internal fun prepareProcessing(options: ProcessingOptions?, hasDonor: Boolean, waitForPrevious: Boolean): EffectiveProcessing? {
    require(!waitForPrevious || hasDonor) { "Previous file is missing; waiting for it before import" }
    return options?.effectiveProcessing(hasDonor)
}

internal fun ProcessingOptions.effectiveProcessing(hasDonor: Boolean): EffectiveProcessing {
    require(!(keepAudioOfPrevious && fillAudioOfPrevious)) { "Keep Audio and Fill Audio cannot be combined" }
    if (hasDonor) return EffectiveProcessing(this, emptyList())
    val skipped = buildList {
        if (keepVideoOfPrevious) add("keep video")
        if (keepAudioOfPrevious) add("keep audio")
        if (fillAudioOfPrevious) add("fill audio")
        if (keepSubsOfPrevious) add("keep subtitles")
        if (keepSubsMissingLanguages) add("keep missing-language subtitles")
        if (keepNonEnglish) add("keep non-English subtitles")
        if (removeNewSubs) add("discard new subtitles")
        if (manualAudioSync != 0L) add("audio delay")
        if (manualSubSync != 0L) add("subtitle delay")
    }
    return EffectiveProcessing(copy(
        keepVideoOfPrevious = false, keepAudioOfPrevious = false, fillAudioOfPrevious = false,
        keepSubsOfPrevious = false, keepSubsMissingLanguages = false, keepNonEnglish = false,
        removeNewSubs = false, manualAudioSync = 0, manualSubSync = 0,
    ), skipped)
}

internal fun ProcessingOptions.usesDonor(): Boolean = keepVideoOfPrevious || keepAudioOfPrevious || fillAudioOfPrevious ||
    keepSubsOfPrevious || keepSubsMissingLanguages || keepNonEnglish || removeNewSubs ||
    manualAudioSync != 0L || manualSubSync != 0L || keepBetterAudio

internal fun hasMuxtoolsTokens(template: String): Boolean = Regex("\\$[^$]+\\$").containsMatchIn(template)

internal fun buildProcessingCommand(
    python: File, target: File, donor: File?, options: ProcessingOptions,
    stage: File, name: String, title: String, episode: String, show: String,
    supplementalInfo: String? = null, donorSupplementalInfo: String? = null,
): List<String> = buildList {
    addAll(listOf(python.absolutePath, "-m", "muxtools_styx", "--json", "mux", target.absolutePath,
        "--out-dir", stage.absolutePath, "--out-name", name, "--mkv-title", title,
        "--episode", episode, "--show-name", show))
    if (donor != null && options.usesDonor()) addAll(listOf("--donor", donor.absolutePath))
    if (!supplementalInfo.isNullOrBlank()) addAll(listOf("--supplemental-info", supplementalInfo))
    if (donor != null && options.usesDonor() && !donorSupplementalInfo.isNullOrBlank())
        addAll(listOf("--donor-supplemental-info", donorSupplementalInfo))
    val flags = listOf(
        options.keepVideoOfPrevious to "--keep-video", options.keepAudioOfPrevious to "--keep-audio",
        options.fillAudioOfPrevious to "--fill-audio", options.keepBetterAudio to "--best-audio",
        options.keepSubsOfPrevious to "--keep-subs", options.keepSubsMissingLanguages to "--keep-subs-missing-languages",
        options.keepNonEnglish to "--keep-non-english", options.removeNewSubs to "--discard-new-subs",
        options.removeUnnecessary to "--remove-unnecessary", options.restyleSubs to "--restyle-subs",
        options.fixTagging to "--fix-tags", options.normalizeTrackNames to "--normalize-track-names",
        options.tppSubs to "--tpp",
    )
    flags.filter { it.first }.forEach { add(it.second) }
    for ((flag, languages) in listOf("--audio-language" to options.audioLanguages,
        "--sub-language" to options.subLanguages, "--restyle-language" to options.restyleLanguages)) {
        languages.split(",").map { it.trim() }.filter { it.isNotEmpty() }.forEach { addAll(listOf(flag, it)) }
    }
    if (options.manualAudioSync != 0L) add("--audio-sync=${options.manualAudioSync}")
    if (options.manualSubSync != 0L) add("--sub-sync=${options.manualSubSync}")
}

@Serializable
private data class MuxResult(val output: String)

internal fun readProcessingOutput(stdout: String, stage: File): File {
    // Some muxtools versions emit diagnostics on stdout before the final CLI result.
    val result = stdout.lineSequence().lastOrNull { it.isNotBlank() } ?: error("muxtools-styx returned no result")
    val output = File(json.decodeFromString<MuxResult>(result).output)
    require(output.isAbsolute && output.isFile) { "muxtools-styx did not return an existing absolute output file" }
    require(output.canonicalFile.parentFile == stage.canonicalFile && output.extension.equals("mkv", true)) {
        "muxtools-styx returned an output outside its staging directory or a non-MKV file"
    }
    return output
}

internal fun runProcessingCommand(commands: List<String>, stage: File, logFile: File): File {
    val stdoutFile = File(stage, "result.json")
    val exitCode = ProcessBuilder(commands).directory(stage)
        .redirectOutput(stdoutFile).redirectError(ProcessBuilder.Redirect.appendTo(logFile)).start().waitFor()
    val stdout = stdoutFile.readText()
    logFile.appendText(stdout)
    check(exitCode == 0) { "muxtools-styx exited with code $exitCode; see ${logFile.absolutePath}" }
    return readProcessingOutput(stdout, stage)
}
