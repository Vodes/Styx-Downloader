package moe.styx.downloader.other

import moe.styx.db.getFavourites
import moe.styx.db.getImages
import moe.styx.db.getUsers
import moe.styx.downloader.Main
import moe.styx.downloader.getDBClient
import moe.styx.downloader.utils.Log
import moe.styx.downloader.utils.getURL
import moe.styx.types.Media
import moe.styx.types.MediaEntry
import moe.styx.types.eqI
import org.javacord.api.DiscordApiBuilder
import org.javacord.api.entity.message.embed.EmbedBuilder
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
            .setAuthor("Styx", "https://beta.styx.moe", "https://beta.styx.moe/icons/icon.ico")
            .setThumbnail(thumb.getURL())
            .setTitle("New episode")
            .setDescription("${media.name} - ${entry.entryNumber}")

        if (pingRole != null && userFavs.isNotEmpty()) {
            val filtered = userFavs.filterValues { pingRole.hasUser(it) }
            if (filtered.isNotEmpty()) {
                channel.sendMessage(filtered.values.joinToString(" ") { it!!.mentionTag }, embed)
            } else {
                channel.sendMessage(embed)
            }
        } else {
            channel.sendMessage(embed)
        }

        if (dmRole != null) {
            val filtered = userFavs.filterValues { dmRole.hasUser(it) }
            filtered.values.forEach {
                it?.sendMessage("`${media.name} - ${entry.entryNumber}` has been added!")
            }
        }
    }.also {
        bot.disconnect()
        dbClient.closeConnection()
    }
}