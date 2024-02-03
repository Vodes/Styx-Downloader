package moe.styx.downloader.utils

import com.github.ajalt.mordant.rendering.TextColors
import com.github.ajalt.mordant.terminal.Terminal
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import moe.styx.downloader.Main

object Log {

    private val terminal = Terminal()

    fun getFormattedTime() = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).formattedStr()

    fun i(source: String? = null, message: () -> String) {
        printMsg(message(), "I", source, prefixColor = TextColors.gray)
    }

    fun e(source: String? = null, exception: Throwable? = null, printStack: Boolean = true, message: () -> String = { "" }) {
        printMsg(message(), "E", source, exception, printStack, prefixColor = TextColors.brightRed)
    }

    fun d(source: String? = null, message: () -> String) {
        if (!Main.config.debug)
            return
        printMsg(message(), "D", source, prefixColor = TextColors.brightGreen)
    }

    fun w(source: String? = null, exception: Throwable? = null, message: () -> String) {
        printMsg(message(), "W", source, exception, false, prefixColor = TextColors.brightYellow)
    }

    private fun printMsg(
        message: String,
        prefix: String,
        source: String? = null,
        exception: Throwable? = null,
        printStack: Boolean = true,
        prefixColor: TextColors? = null
    ) {
        val fallback = if (message.isBlank() && exception != null) "Exception: ${exception.message}" else message
        var msg = if (prefixColor == null)
            "${getFormattedTime()} - [$prefix] - $fallback"
        else {
            val pre = prefixColor("${getFormattedTime()} - [$prefix]")
            "$pre - $fallback"
        }
        if (!source.isNullOrBlank())
            msg += "\n  at: $source"
        if (exception != null && message.isBlank())
            msg += "\n  Exception: ${exception.message}"

        terminal.println(msg, stderr = prefix == "E")

        if (printStack)
            exception?.printStackTrace()
    }
}