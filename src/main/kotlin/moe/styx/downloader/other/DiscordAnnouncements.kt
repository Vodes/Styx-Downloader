package moe.styx.downloader.other

import de.androidpit.colorthief.ColorThief
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.utils.io.jvm.javaio.*
import kotlinx.coroutines.runBlocking
import moe.styx.common.data.Media
import moe.styx.common.data.MediaEntry
import moe.styx.common.extension.eqI
import moe.styx.db.getFavourites
import moe.styx.db.getImages
import moe.styx.db.getUsers
import moe.styx.downloader.Main
import moe.styx.downloader.getDBClient
import moe.styx.downloader.httpClient
import moe.styx.downloader.utils.Log
import moe.styx.downloader.utils.getURL
import org.javacord.api.DiscordApiBuilder
import org.javacord.api.entity.message.embed.EmbedBuilder
import java.awt.Color
import javax.imageio.ImageIO
import kotlin.jvm.optionals.getOrNull

fun notifyDiscord(entry: MediaEntry, media: Media) {
    if (!Main.config.discordBot.isValid())
        return
    if (media.thumbID.isNullOrBlank())
        return
    val dbClient = getDBClient()
    val thumb = dbClient.getImages(mapOf("GUID" to media.thumbID!!)).firstOrNull() ?: return
    val users = dbClient.getUsers()

    val bot = DiscordApiBuilder().setToken(Main.config.discordBot.token).setAllIntents().login().join()

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
            .setAuthor("Styx", "https://beta.styx.moe", "https://i.styx.moe/website/icon.png")
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
                it?.sendMessage("`${media.name} - ${entry.entryNumber}` has been added!")?.join()
            }
        }
    }.also {
        bot.disconnect()
        dbClient.closeConnection()
    }
}