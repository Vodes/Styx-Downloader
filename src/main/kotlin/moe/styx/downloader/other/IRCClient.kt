package moe.styx.downloader.other

import io.ktor.client.request.*
import io.ktor.client.statement.*
import kotlinx.coroutines.runBlocking
import moe.styx.common.data.SourceType
import moe.styx.common.extension.capitalize
import moe.styx.common.extension.eqI
import moe.styx.common.http.httpClient
import moe.styx.db.tables.DownloaderTargetsTable
import moe.styx.downloader.Main
import moe.styx.downloader.dbClient
import moe.styx.downloader.episodeWanted
import moe.styx.downloader.parsing.ParseResult
import moe.styx.downloader.utils.Log
import moe.styx.downloader.utils.RegexCollection
import org.jetbrains.exposed.sql.selectAll
import org.jibble.pircbot.DccFileTransfer
import org.jibble.pircbot.PircBot
import java.io.File
import kotlin.random.Random

class IRCClient(private val server: String, private val channels: List<String>) : PircBot() {
    private val logSource = "IRCClient for server - $server"
    private val wordList = mutableListOf<String>()

    init {
        runBlocking {
            val response = httpClient.get("https://raw.githubusercontent.com/openethereum/wordlist/master/res/wordlist.txt")
            wordList.addAll(response.bodyAsText().lines())
        }
    }

    fun start() {
        val generatedName = wordList[Random.nextInt(wordList.size)].capitalize() + wordList[Random.nextInt(wordList.size)].capitalize() + "${
            Random.nextInt(
                100,
                9999
            )
        }"
        this.name = generatedName
        this.userName = generatedName
        this.realName = generatedName
        this.version = "1"
        runCatching {
            this.connect(server.split(":")[0], server.split(":").getOrNull(1)?.toIntOrNull() ?: 6667)
            Log.i { "IRC Client connected." }
        }
    }

    override fun onConnect() {
        super.onConnect()
        channels.forEach {
            this.joinChannel(it)
        }
    }


    override fun onFinger(sourceNick: String?, sourceLogin: String?, sourceHostname: String?, target: String?) {
        // Why does this exist?
    }

    override fun onIncomingFileTransfer(transfer: DccFileTransfer) {
        if (Main.config.ircConfig.whitelistedXDCCBots.find { it eqI transfer.nick } == null) {
            Log.d(logSource) { "Blocked incoming transfer from unknown user '${transfer.nick}'" }
            transfer.close()
            return
        }
        val targets = dbClient.transaction { DownloaderTargetsTable.query { selectAll().toList() } }
        val parseResult = targets.episodeWanted(transfer.file.name)
        if (parseResult !is ParseResult.OK || parseResult.option.source != SourceType.XDCC) {
            Log.d(logSource) { "Blocked invalid incoming transfer from user '${transfer.nick}'" }
            transfer.close()
            return
        }

        val tempDir = File(Main.appDir, "Temp-XDCC-Downloads")
        tempDir.mkdirs()
        Log.i(logSource) { "Downloading File '${transfer.file.name}' from '${transfer.nick}'" }
        transfer.receive(File(tempDir, transfer.file.name).also {
            if (it.exists())
                it.delete()
        }, false)
    }

    override fun onFileTransferFinished(transfer: DccFileTransfer, e: Exception?) {
        if (e != null) {
            Log.e("IRCClient for server - $server", e) { "Failed to download file: ${transfer.file.name}" }
            if (transfer.file.exists())
                transfer.file.delete()
            return
        }
        val targets = dbClient.transaction { DownloaderTargetsTable.query { selectAll().toList() } }
        val parseResult = targets.episodeWanted(transfer.file.name)
        if (parseResult !is ParseResult.OK || parseResult.option.source != SourceType.XDCC) {
            Log.d(logSource) { "Somehow ended up with unwanted file: ${transfer.file.name}" }
            return
        }
        Log.i(logSource) { "Downloaded file: ${transfer.file.name}" }
        // TODO: Perhaps add a delay to avoid parsing/moving just barely unfinished downloads
        handleFile(transfer.file, parseResult.target, parseResult.option)
    }

    override fun onPrivateMessage(sender: String, login: String, hostname: String, message: String) {
        super.onPrivateMessage(sender, login, hostname, message)
        if (Main.config.ircConfig.whitelistedXDCCBots.find { it eqI sender } == null) {
            return
        }
        handleMessage(message, sender)
    }

    override fun onMessage(channel: String, sender: String, login: String, hostname: String, message: String) {
        super.onMessage(channel, sender, login, hostname, message)
        if (Main.config.ircConfig.whitelistedXDCCBots.find { it eqI sender } == null) {
            return
        }
        handleMessage(message, sender)
    }

    private fun handleMessage(message: String, sender: String) {
        val announceMatch = RegexCollection.xdccAnnounceRegex.find(message) ?: return
        val targets = dbClient.transaction { DownloaderTargetsTable.query { selectAll().toList() } }
        val parseResult = targets.episodeWanted(message)
        if (parseResult !is ParseResult.OK || parseResult.option.source != SourceType.XDCC) {
            return
        }
        Log.d(logSource) { "Found valid announce by user '$sender'" }
        this.sendMessage(announceMatch.groups["user"]!!.value, "xdcc send #${announceMatch.groups["num"]?.value}")
    }
}