package moe.styx.downloader.utils

object RegexCollection {
    val fixPattern = "(?<whole>(?<ep>(?:S\\d+)?[E ]\\d+)(?<version>v\\d))".toRegex(RegexOption.IGNORE_CASE)
    val singleLetterWithDot = "\\.[A-Za-z]\\.".toRegex()
    val torrentHrefRegex = "href=\"(?<url>https?:\\/\\/[^ \"<>]+?\\.torrent)\"".toRegex(RegexOption.IGNORE_CASE)
    val generalURLRegex = "https?:\\/\\/.+".toRegex(RegexOption.IGNORE_CASE)
    val specialEpisodeRegex = "(?:(SP\\d)|(?:(?:E| )(\\d+\\.\\d)))".toRegex()

    val xdccAnnounceRegex = "\\/msg (?<user>.+?) xdcc send #?(?<num>\\d+)".toRegex(RegexOption.IGNORE_CASE)
    val repackRegex = "(?:\\.| )REPACK(?<num>\\d+)?(?:\\.| )".toRegex(RegexOption.IGNORE_CASE)

    val ftpConnectionStringRegex =
        "(?<connection>ftpe?s?):\\/\\/(?:(?<user>.+):(?<pass>.+)@)?(?<host>(?:[a-zA-Z0-9]+\\.?)+)(?::(?<port>\\d+))?(?<path>\\/.+)?"
            .toRegex(RegexOption.IGNORE_CASE)
}

object TokenRegex {
    val groupToken = "%(?:release_group|group)(?<b>_b)?(?<p>_p)?%".toRegex(RegexOption.IGNORE_CASE)
    val regularEpisodeToken = "%(?:episode_number|ep)(?<e>_e)?%".toRegex(RegexOption.IGNORE_CASE)
    val absoluteEpisodeToken = "%(?:episode_number_a|ep_a)%".toRegex(RegexOption.IGNORE_CASE)
    val offsetEpisodeToken =
        "%(?:episode_number_o_|episode_number_off_|episode_number_offset_|ep_o_)(?<offset>-?\\d+(?:\\.\\d+)?)%".toRegex(RegexOption.IGNORE_CASE)
}

fun MatchResult.wantsBrackets() = this.groupValues.anyEquals("_b")

fun MatchResult.wantsParentheses() = this.groupValues.anyEquals("_p")

fun MatchResult.wantsEPrefix() = this.groupValues.anyEquals("_e")