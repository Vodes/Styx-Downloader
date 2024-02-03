package moe.styx.downloader.utils

import kotlinx.datetime.LocalDateTime
import moe.styx.types.padString
import java.math.RoundingMode
import java.text.DecimalFormat
import java.util.*
import kotlin.math.floor

val isWindows = System.getProperty("os.name").contains("win", true)

fun String.capitalize(): String {
    return this.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
}

private val small = DecimalFormat("#.#")
private val big = DecimalFormat("#.##")

fun Long.readableSize(useBinary: Boolean = false): String {
    val units = if (useBinary) listOf("B", "KiB", "MiB", "GiB", "TiB") else listOf("B", "KB", "MB", "GB", "TB")
    val divisor = if (useBinary) 1024 else 1000
    var steps = 0
    var current = this.toDouble()
    while (floor((current / divisor)) > 0) {
        current = (current / divisor)
        steps++;
    }
    small.roundingMode = RoundingMode.CEILING.also { big.roundingMode = it }
    return "${(if (steps > 2) big else small).format(current)} ${units[steps]}"
}

fun LocalDateTime.formattedStr(): String {
    return "${this.year}-${this.monthNumber.padString()}-${this.dayOfMonth.padString()} " +
            "${this.hour.padString()}:${this.minute.padString()}:${this.second.padString()}"
}

/**
 * Removes characters from a string that might be invalid for a file on your system.
 *
 * ":" to " -" is really just a stylistic choice.
 * For example: `Frieren: Beyond Journey’s End - S01E21`
 * to `Frieren - Beyond Journey’s End - S01E21`
 */
fun String.toFileSystemCompliantName(): String {
    return if (isWindows) {
        this.replace(":", " -").replaceAll("", "<", ">", "\"", "/", "\\", "|", "?", "*")
    } else {
        this.replace(":", " -").replace("/", "")
    }
}

fun String.replaceAll(replacement: String, vararg values: String, ignoreCase: Boolean = true): String {
    var new = this
    values.forEach {
        new = new.replace(it, replacement, ignoreCase)
    }
    return new
}

fun String?.equalsAny(vararg values: String, ignoreCase: Boolean = true): Boolean {
    if (this == null)
        return false
    for (value in values) {
        if (this.equals(value, ignoreCase))
            return true
    }
    return false
}

fun String.containsAny(vararg values: String, ignoreCase: Boolean = true): Boolean {
    for (value in values) {
        if (this.contains(value, ignoreCase))
            return true
    }
    return false
}

fun List<String>.anyEquals(value: String, ignoreCase: Boolean = true): Boolean {
    for (element in this) {
        if (element.equals(value, ignoreCase))
            return true
    }
    return false
}


fun List<String>.containsIgnoreCase(value: String): Boolean {
    for (element in this) {
        if (element.contains(value, true))
            return true
    }
    return false
}