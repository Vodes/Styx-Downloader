package moe.styx.downloader.utils

import moe.styx.common.data.*
import moe.styx.common.extension.toBoolean
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
fun List<DownloaderTarget>.getRSSOptions(): Map<String, List<DownloadableOption>> {
    val options = this.flatMap { it.options }.filter { !it.sourcePath.isNullOrBlank() && it.source == SourceType.TORRENT }
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
        "https://i.styx.moe/$GUID.webp"
    } else if (hasJPG?.toBoolean() == true) {
        "https://i.styx.moe/$GUID.jpg"
    } else if (hasPNG?.toBoolean() == true) {
        "https://i.styx.moe/$GUID.png"
    } else {
        return externalURL as String
    }
}