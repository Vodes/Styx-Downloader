package moe.styx.downloader.utils

import moe.styx.common.extension.anyEquals

object RegexCollection {
    val fixPattern = "(?<whole>(?<ep>(?:S\\d+)?[E ]\\d+)(?<version>v\\d))".toRegex(RegexOption.IGNORE_CASE)
    val semiFixPattern = "\\w+ E(?<ep>\\d+) \\[".toRegex()
    val singleLetterWithDot = "\\d(\\.[A-Za-z]\\.)(?!264|265|2\\.0)".toRegex()
    val torrentHrefRegex = "href=\"(?<url>https?:\\/\\/[^ \"<>]+?\\.torrent)\"".toRegex(RegexOption.IGNORE_CASE)
    val generalURLRegex = "https?:\\/\\/.+".toRegex(RegexOption.IGNORE_CASE)
    val specialEpisodeRegex = "(?:(SP\\d)|((?:(?:E| ?- ?)(?<num>\\d+\\.\\d(?: |\\.|\$)))))".toRegex()
    val sxxExxWithNumberEPTitleRegex = "(?<entire>(?<realEpisode>S\\d{1,2}E\\d{1,4})(?:\\.| )\\d{1,2})[ |\\.]".toRegex()
    val crc32Regex = " ?(?:\\[|\\(|\\.)[0-F]{8}(?:\\]|\\)|\\.) ?".toRegex()
    val seasonZeroRegex = "(?: |\\.)S00E(?<ep>\\d+)(?: |\\.)".toRegex()
    val p2pGroupRegex = ".+-(?<name>\\w+)\$".toRegex()

    val oldNandesukaFixRegex = ".+(-(?<name>\\w+)(?<site> \\((?:CR|NF|AMZN|HIDIVE|HIDI|ADN|DSNP|HULU|B-Global)\\)))".toRegex()

    val xdccAnnounceRegex = "\\/msg (?<user>.+?) xdcc send #?(?<num>\\d+)".toRegex(RegexOption.IGNORE_CASE)
    val repackRegex = "(?:\\.| )REPACK(?<num>\\d+)?(?:\\.| )".toRegex(RegexOption.IGNORE_CASE)
    val stupidKeyRegex = "(?<sep>\\?|&)(?<key>(?:api|apikey|r|passkey|user)=\\w+&?)".toRegex(RegexOption.IGNORE_CASE)

    val ftpConnectionStringRegex =
        "(?<connection>ftpe?s?):\\/\\/(?:(?<user>.+):(?<pass>.+)@)?(?<host>(?:[a-zA-Z0-9]+\\.?)+)(?::(?<port>\\d+))?(?<path>\\/.+)?"
            .toRegex(RegexOption.IGNORE_CASE)
    val channelURLRegex = "channels\\/(?<serverID>\\d+)\\/(?<channelID>\\d+)".toRegex(RegexOption.IGNORE_CASE)
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