package moe.styx.downloader.utils

object RegexCollection {
    val fixPattern = "(?<whole>(?<ep>(?:S\\d+)?[E ]\\d+)(?<version>v\\d))".toRegex(RegexOption.IGNORE_CASE)
    val singleLetterWithDot = "\\.[A-Za-z]\\.".toRegex()
    val torrentHrefRegex = "href=\"(?<url>https?:\\/\\/[^ \"<>]+?\\.torrent)\"".toRegex(RegexOption.IGNORE_CASE)
    val generalURLRegex = "https?:\\/\\/.+".toRegex(RegexOption.IGNORE_CASE)
    
    val ftpConnectionStringRegex =
        "(?<connection>ftpe?s?):\\/\\/(?:(?<user>.+):(?<pass>.+)@)?(?<host>(?:[a-zA-Z0-9]+\\.?)+)(?::(?<port>\\d+))?(?<path>\\/.+)?"
            .toRegex(RegexOption.IGNORE_CASE)
}