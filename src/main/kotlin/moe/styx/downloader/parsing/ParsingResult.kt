package moe.styx.downloader.parsing

import moe.styx.common.data.DownloadableOption
import moe.styx.common.data.DownloaderTarget

enum class ParseDenyReason {
    NoOptionMatched,
    InvalidEpisodeNumber,
    BetterVersionPresent,
    SameVersionPresent,
    WaitingForPreviousOption,
    PostIsTooOld,
    FileIsTooNew,
    IsSpecialOrFiller,
    DatabaseConnectionFailed,

    NZBNotFound,
    TorrentNotFound
}

sealed class ParseResult {
    data class OK(val target: DownloaderTarget, val option: DownloadableOption, val parentDir: String? = null) : ParseResult()
    data class FAILED(val parseFailReason: ParseDenyReason, val reasonMessage: String = "") : ParseResult()
    data class DENIED(val parseFailReason: ParseDenyReason, val reasonMessage: String = "") : ParseResult()
}