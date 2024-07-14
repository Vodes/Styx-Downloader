package moe.styx.downloader.utils

import moe.styx.common.data.*
import moe.styx.common.data.tmdb.TmdbEpisode
import moe.styx.common.extension.toBoolean
import moe.styx.downloader.Main
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

infix fun DownloadableOption.parentIn(list: List<DownloaderTarget>): DownloaderTarget {
    return list.find { it.options.find { opt -> opt == this } != null }!!
}

/**
 * We only want to get each RSS feed once, so we map it to Feed and Options that use the feed
 */
fun List<DownloaderTarget>.getRSSOptions(type: SourceType): Map<String, List<DownloadableOption>> {
    val options = this.flatMap { it.options }.filter { !it.sourcePath.isNullOrBlank() && it.source == type }
    return options.groupBy { it.sourcePath!! }
}

fun List<DownloaderTarget>.getFTPOptions(): List<DownloadableOption> {
    val options = this.flatMap { it.options }
    return options.filter { !it.sourcePath.isNullOrBlank() && it.source == SourceType.FTP }
}

fun ScheduleWeekday.dayOfWeek(): DayOfWeek {
    return when (this) {
        ScheduleWeekday.MONDAY -> DayOfWeek.MONDAY
        ScheduleWeekday.TUESDAY -> DayOfWeek.TUESDAY
        ScheduleWeekday.WEDNESDAY -> DayOfWeek.WEDNESDAY
        ScheduleWeekday.THURSDAY -> DayOfWeek.THURSDAY
        ScheduleWeekday.FRIDAY -> DayOfWeek.FRIDAY
        ScheduleWeekday.SATURDAY -> DayOfWeek.SATURDAY
        else -> DayOfWeek.SUNDAY
    }
}

fun MediaSchedule.getTargetTime(): LocalDateTime {
    val nowTime = LocalDateTime.now(ZoneId.of("Europe/Berlin"))
    val now = LocalDate.now(ZoneId.of("Europe/Berlin"))
    val adjusted = now.atTime(this.hour, this.minute)

    val target = if (this.day.dayOfWeek() == now.dayOfWeek && nowTime.isBefore(adjusted))
        adjusted
    else
        adjusted.with(TemporalAdjusters.next(this.day.dayOfWeek()))

    return target.atZone(ZoneId.of("Europe/Berlin")).withZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime()
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
        "${Main.config.imageBaseUrl}/$GUID.webp"
    } else if (hasJPG?.toBoolean() == true) {
        "${Main.config.imageBaseUrl}/$GUID.jpg"
    } else if (hasPNG?.toBoolean() == true) {
        "${Main.config.imageBaseUrl}/$GUID.png"
    } else {
        return externalURL as String
    }
}

fun TMDBMapping.getRemoteEpisodes(message: (content: String) -> Unit = {}): Pair<List<TmdbEpisode>, List<TmdbEpisode>> {
    val empty = emptyList<TmdbEpisode>() to emptyList<TmdbEpisode>()
    if (remoteID <= 0)
        message("No valid ID was found!").also { return empty }

    if (orderType != null && !orderID.isNullOrBlank()) {
        val order = getTmdbOrder(orderID!!)
        if (order == null)
            message("No episode order was found!").also { return empty }
        val group = order!!.groups.find { it.order == seasonEntry }
        if (group == null)
            message("Could not find season $seasonEntry in the episode group!").also { return empty }

        val otherOrder = getTmdbOrder(orderID!!, "de-DE")
        val otherGroup = otherOrder?.groups?.find { it.order == seasonEntry }
        return group!!.episodes to (otherGroup?.episodes ?: emptyList())
    }
    val season = getTmdbSeason(remoteID, seasonEntry, "en-US")
    if (season == null)
        message("Could not get season $seasonEntry for $remoteID!").also { return empty }

    val other = getTmdbSeason(remoteID, seasonEntry, "de-DE")
    if (other == null)
        message("Could not get season $seasonEntry for $remoteID!").also { return empty }

    return season!!.episodes to other!!.episodes
}