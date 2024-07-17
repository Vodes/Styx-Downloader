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
import moe.styx.common.http.httpClient
import moe.styx.common.util.launchGlobal
import moe.styx.db.tables.*
import moe.styx.downloader.dbClient
import moe.styx.downloader.downloaderConfig
import moe.styx.downloader.utils.Log
import moe.styx.downloader.utils.getTargetTime
import moe.styx.downloader.utils.getURL
import org.javacord.api.DiscordApi
import org.javacord.api.DiscordApiBuilder
import org.javacord.api.entity.message.embed.EmbedBuilder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.selectAll
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
    bot = DiscordApiBuilder().setToken(downloaderConfig.discordBot.token).setAllIntents().login().join()

    if (downloaderConfig.discordBot.scheduleMessage.isBlank())
        return

    launchGlobal {
        while (true) {
            val mediaList = dbClient.transaction { MediaTable.query { selectAll().toList() } }
            val scheduleDays = dbClient.transaction { MediaScheduleTable.query { selectAll().toList() } }.sortedBy { it.getTargetTime().dayOfWeek }
                .groupBy { it.getTargetTime().dayOfWeek }
            runCatching {
                val embed = EmbedBuilder().setTitle("Anime-Chart")
                scheduleDays.forEach { (_, schedules) ->
                    for (schedule in schedules.sortedBy { it.getTargetTime() }) {
                        val media = mediaList.find { it.GUID eqI schedule.mediaID }
                        if (media == null)
                            continue
                        val latestEntry = dbClient.transaction { MediaEntryTable.query { selectAll().where { mediaID eq media.GUID }.toList() } }
                            .maxBy { it.entryNumber.toDoubleOrNull() ?: 0.0 }
                        val latestEntryNum = latestEntry.entryNumber.toDoubleOrNull()
                        val target = schedule.getTargetTime()
                        val instant = target.atZone(ZoneId.systemDefault()).toInstant()
                        val unix = instant.epochSecond
                        val isToday = target.dayOfWeek == LocalDateTime.now().dayOfWeek

                        val episode = if (latestEntryNum != null) {
                            val next = latestEntryNum.roundToInt() + 1
                            val nextFormatted = next.padString(2)
                            " - $nextFormatted${if (schedule.finalEpisodeCount != 0 && next >= schedule.finalEpisodeCount) " (Final EP)" else ""}"
                        } else ""
                        embed.addField(
                            "${if (isToday) "> " else ""}${media.name}$episode",
                            "<t:$unix:R> (<t:$unix:d> <t:$unix:t>)${if (schedule.isEstimated.toBoolean()) " (Estimated)" else ""}"
                        )
                    }
                }
                val message = bot.getMessageByLink(downloaderConfig.discordBot.scheduleMessage).get().join()
                message.edit("", embed)
            }.onFailure { it.printStackTrace() }

            delay(3.toDuration(DurationUnit.MINUTES))
        }
    }
}

fun notifyDiscord(entry: MediaEntry, media: Media) {
    if (!downloaderConfig.discordBot.isValid())
        return
    if (media.thumbID.isNullOrBlank())
        return
    val thumb = dbClient.transaction { ImageTable.query { selectAll().where { GUID eq media.thumbID!! }.toList() } }.firstOrNull() ?: return
    val users = dbClient.transaction { UserTable.query { selectAll().toList() } }
    checkAndRemoveSchedule(media, entry)

    runCatching {
        val server = bot.getServerById(downloaderConfig.discordBot.announceServer).getOrNull() ?: return@runCatching
        val channel = server.getTextChannelById(downloaderConfig.discordBot.announceChannel).getOrNull() ?: return@runCatching
        val pingRole = server.getRoleById(downloaderConfig.discordBot.pingRole).getOrNull()
        val dmRole = server.getRoleById(downloaderConfig.discordBot.dmRole).getOrNull()

        if (!channel.canYouWrite()) {
            Log.e { "Discord announce channel is not writeable!" }
            return@runCatching
        }

        val userFavs = dbClient.transaction { FavouriteTable.query { selectAll().toList() } }
            .filter { it.mediaID eqI media.GUID }
            .associateWith { fav -> users.find { it.GUID eqI fav.userID } }
            .filterValues { it != null }
            .mapValues { runCatching { bot.getUserById(it.value!!.discordID).get() }.getOrNull() }
            .filterValues { it != null }

        val embed = EmbedBuilder()
            .setAuthor("Styx", downloaderConfig.siteBaseUrl, "${downloaderConfig.imageBaseUrl}/website/icon.png")
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
    }
}

private fun checkAndRemoveSchedule(media: Media, entry: MediaEntry) {
    val schedule = dbClient.transaction { MediaScheduleTable.query { selectAll().where { mediaID eq media.GUID }.toList() } }.firstOrNull() ?: return
    if (schedule.finalEpisodeCount <= 0)
        return
    val episode = entry.entryNumber.toDoubleOrNull() ?: return
    if (episode.roundToInt() >= schedule.finalEpisodeCount)
        dbClient.transaction { MediaScheduleTable.deleteWhere { mediaID eq media.GUID } }
}