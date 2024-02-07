package moe.styx.downloader.utils

import moe.styx.types.*

infix fun DownloadableOption.parentIn(list: List<DownloaderTarget>): DownloaderTarget {
    return list.find { it.options.find { opt -> opt == this } != null }!!
}

/**
 * We only want to get each RSS feed once, so we map it to Feed and Options that use the feed
 */
fun List<DownloaderTarget>.getRSSOptions(): Map<String, List<DownloadableOption>> {
    val options = this.flatMap { it.options }.filter { !it.sourcePath.isNullOrBlank() && it.source == SourceType.TORRENT }
    return options.groupBy { it.sourcePath!! }
}

fun List<DownloaderTarget>.getFTPOptions(): List<DownloadableOption> {
    val options = this.flatMap { it.options }
    return options.filter { !it.sourcePath.isNullOrBlank() && it.source == SourceType.FTP }
}

fun ProcessingOptions.needsMuxtools(): Boolean {
    return arrayOf(
        this.keepAudioOfPrevious,
        this.keepVideoOfPrevious,
        this.keepBetterAudio,
        this.keepSubsOfPrevious,
        this.keepNonEnglish,
        this.removeNewSubs,
        this.manualSubSync != 0L,
        this.manualAudioSync != 0L,
        this.restyleSubs,
        this.tppSubs,
        this.sushiSubs
    ).any { it }
}

fun Image.getURL(): String {
    return if (hasWEBP?.toBoolean() == true) {
        "https://i.styx.moe/$GUID.webp"
    } else if (hasJPG?.toBoolean() == true) {
        "https://i.styx.moe/$GUID.jpg"
    } else if (hasPNG?.toBoolean() == true) {
        "https://i.styx.moe/$GUID.png"
    } else {
        return externalURL as String
    }
}