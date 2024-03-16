package moe.styx.downloader.other

import de.androidpit.colorthief.ColorThief
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.utils.io.jvm.javaio.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import moe.styx.common.data.Media
import moe.styx.common.data.MediaEntry
import moe.styx.common.extension.eqI
import moe.styx.common.extension.padString
import moe.styx.common.extension.toBoolean
import moe.styx.common.util.launchGlobal
import moe.styx.db.*
import moe.styx.downloader.Main
import moe.styx.downloader.getDBClient
import moe.styx.downloader.httpClient
import moe.styx.downloader.utils.Log
import moe.styx.downloader.utils.getTargetTime
import moe.styx.downloader.utils.getURL
import org.javacord.api.DiscordApi
import org.javacord.api.DiscordApiBuilder
import org.javacord.api.entity.message.embed.EmbedBuilder
import java.awt.Color
import java.time.LocalDateTime
import java.time.ZoneId
import javax.imageio.ImageIO
import kotlin.jvm.optionals.getOrNull
import kotlin.math.roundToInt
import kotlin.time.DurationUnit
import kotlin.time.toDuration

private lateinit var bot: DiscordApi

fun startBot() {
    bot = DiscordApiBuilder().setToken(Main.config.discordBot.token).setAllIntents().login().join()

    if (Main.config.discordBot.scheduleMessage.isBlank())
        return

    launchGlobal {
        while (true) {
            val dbClient = getDBClient()
            val mediaList = dbClient.getMedia()
            val scheduleDays = dbClient.getSchedules().sortedBy { it.getTargetTime().dayOfWeek }.groupBy { it.getTargetTime().dayOfWeek }
            runCatching {
                val embed = EmbedBuilder().setTitle("Anime-Chart")
                scheduleDays.forEach { (_, schedules) ->
                    for (schedule in schedules.sortedBy { it.getTargetTime() }) {
                        val media = mediaList.find { it.GUID eqI schedule.mediaID }
                        if (media == null)
                            continue
                        val latestEntry = dbClient.getEntries(mapOf("mediaID" to media.GUID)).maxBy { it.entryNumber.toDoubleOrNull() ?: 0.0 }
                        val latestEntryNum = latestEntry.entryNumber.toDoubleOrNull()
                        val target = schedule.getTargetTime()
                        val instant = target.atZone(ZoneId.systemDefault()).toInstant()
                        val unix = instant.epochSecond
                        val isToday = target.dayOfWeek == LocalDateTime.now().dayOfWeek

                        val episode = if (latestEntryNum != null) {
                            val next = latestEntryNum.roundToInt() + 1
                            val nextFormatted = next.padString(2)
                            " - $nextFormatted${if (schedule.finalEpisodeCount != 0 && next >= schedule.finalEpisodeCount) "(Final EP)" else ""}"
                        } else ""
                        embed.addField(
                            "${if (isToday) "> " else ""}${media!!.name}$episode",
                            "<t:$unix:R> (<t:$unix:d> <t:$unix:t>)${if (schedule.isEstimated.toBoolean()) " (Estimated)" else ""}"
                        )
                    }
                }
                val message = bot.getMessageByLink(Main.config.discordBot.scheduleMessage).get().join()
                message.edit("", embed)
            }.onFailure { it.printStackTrace() }

            dbClient.closeConnection()
            delay(3.toDuration(DurationUnit.MINUTES))
        }
    }
}

fun notifyDiscord(entry: MediaEntry, media: Media) {
    if (!Main.config.discordBot.isValid())
        return
    if (media.thumbID.isNullOrBlank())
        return
    val dbClient = getDBClient()
    val thumb = dbClient.getImages(mapOf("GUID" to media.thumbID!!)).firstOrNull() ?: return
    val users = dbClient.getUsers()

    runCatching {
        val server = bot.getServerById(Main.config.discordBot.announceServer).getOrNull() ?: return@runCatching
        val channel = server.getTextChannelById(Main.config.discordBot.announceChannel).getOrNull() ?: return@runCatching
        val pingRole = server.getRoleById(Main.config.discordBot.pingRole).getOrNull()
        val dmRole = server.getRoleById(Main.config.discordBot.dmRole).getOrNull()

        if (!channel.canYouWrite()) {
            Log.e { "Discord announce channel is not writeable!" }
            return@runCatching
        }

        val userFavs = dbClient.getFavourites().filter { it.mediaID eqI media.GUID }
            .associateWith { fav -> users.find { it.GUID eqI fav.userID } }
            .filterValues { it != null }
            .mapValues { runCatching { bot.getUserById(it.value!!.discordID).get() }.getOrNull() }
            .filterValues { it != null }

        val embed = EmbedBuilder()
            .setAuthor("Styx", Main.config.siteBaseUrl, "${Main.config.imageBaseUrl}/website/icon.png")
            .setThumbnail(thumb.getURL())
            .setTitle("New episode")
            .setDescription("${media.name} - ${entry.entryNumber}")

        runBlocking {
            val response = runBlocking { httpClient.get(thumb.getURL()) }
            if (response.status.isSuccess()) {
                val inputStream = response.bodyAsChannel().toInputStream()
                runCatching {
                    val read = ImageIO.read(inputStream)
                    val color = ColorThief.getColor(read)
                    embed.setColor(Color(color[0], color[1], color[2]))
                }
                runCatching { inputStream.close() }
            }
        }

        if (pingRole != null && userFavs.isNotEmpty()) {
            val filtered = userFavs.filterValues { pingRole.hasUser(it) }
            if (filtered.isNotEmpty()) {
                channel.sendMessage(filtered.values.joinToString(" ") { it!!.mentionTag }, embed).join()
            } else {
                channel.sendMessage(embed).join()
            }
        } else {
            channel.sendMessage(embed).join()
        }

        if (dmRole != null) {
            val filtered = userFavs.filterValues { dmRole.hasUser(it) }
            filtered.values.forEach {
                runCatching { it?.sendMessage("`${media.name} - ${entry.entryNumber}` has been added!")?.join() }
            }
        }
    }.also {
        dbClient.closeConnection()
    }
}