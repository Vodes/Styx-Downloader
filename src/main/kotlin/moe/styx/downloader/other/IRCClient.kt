package moe.styx.downloader.other

import io.ktor.client.request.*
import io.ktor.client.statement.*
import kotlinx.coroutines.runBlocking
import moe.styx.downloader.capitalize
import moe.styx.downloader.httpClient
import org.jibble.pircbot.DccFileTransfer
import org.jibble.pircbot.PircBot
import kotlin.random.Random

class IRCClient : PircBot() {

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
        
    }


    override fun onFinger(sourceNick: String?, sourceLogin: String?, sourceHostname: String?, target: String?) {
        // Why does this exist?
    }

    override fun onIncomingFileTransfer(transfer: DccFileTransfer?) {
        super.onIncomingFileTransfer(transfer)
    }

    override fun onFileTransferFinished(transfer: DccFileTransfer?, e: Exception?) {
        super.onFileTransferFinished(transfer, e)
    }

    override fun onMessage(channel: String?, sender: String?, login: String?, hostname: String?, message: String?) {
        super.onMessage(channel, sender, login, hostname, message)
    }

}