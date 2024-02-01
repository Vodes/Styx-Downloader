package moe.styx.downloader.parsing

import moe.styx.types.DownloadableOption
import moe.styx.types.DownloaderTarget

enum class ParseDenyReason {
    NoOptionMatched,
    InvalidEpisodeNumber,
    BetterVersionPresent,
    SameVersionPresent,
    WaitingForPreviousOption
}

sealed class ParseResult {
    data class OK(val target: DownloaderTarget, val option: DownloadableOption) : ParseResult()
    data class FAILED(val parseFailReason: ParseDenyReason, val reasonMessage: String = "") : ParseResult()
    data class DENIED(val parseFailReason: ParseDenyReason, val reasonMessage: String = "") : ParseResult()
}