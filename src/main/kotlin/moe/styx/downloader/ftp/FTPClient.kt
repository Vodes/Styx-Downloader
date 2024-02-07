package moe.styx.downloader.ftp

import moe.styx.downloader.utils.Log
import moe.styx.downloader.utils.RegexCollection
import org.apache.commons.net.ftp.FTP
import org.apache.commons.net.ftp.FTPClientConfig
import java.io.File

import org.apache.commons.net.ftp.FTPClient as FTPC
import org.apache.commons.net.ftp.FTPSClient as FTPSC

class FTPClient(
    private val host: String,
    private val port: Int,
    private val user: String?,
    private val pass: String?,
    ftps: Boolean = true,
    explicit: Boolean = false,
    private val defaultWorkdir: String? = null
) {
    companion object {
        fun fromConnectionString(connectionString: String): FTPClient {
            val match = RegexCollection.ftpConnectionStringRegex.matchEntire(connectionString)
            if (match == null) {
                val exception = Exception("Failed to parse connection string!")
                Log.e("FTPClient from connection string: $connectionString", exception, printStack = false)
                throw exception
            }

            return FTPClient(
                match.groups["host"]!!.value,
                match.groups["port"]?.value?.toIntOrNull() ?: 21,
                match.groups["user"]?.value,
                match.groups["pass"]?.value,
                match.groups["connection"]!!.value.contains("s", true),
                match.groups["connection"]!!.value.contains("es", true),
                match.groups["path"]?.value
            )
        }
    }

    private val client: FTPC = if (ftps) FTPSC(!explicit) else FTPC()

    fun connect(): FTPClient? {
        val config = FTPClientConfig()
        config.serverTimeZoneId = "Europe/Berlin" //TODO: Make the user able to configure that somehow
        client.controlEncoding = "UTF-8"
        client.configure(config)

        try {
            client.connect(host, port)
            client.enterLocalPassiveMode()
            if (!user.isNullOrBlank() && !pass.isNullOrBlank()) {
                if (!client.login(user, pass))
                    Log.e("FTPClient at: $host") { "Failed to login!" }
            }
            if (client is FTPSC) {
                client.execPBSZ(0)
                client.execPROT("P")
            }
            client.setFileType(FTP.BINARY_FILE_TYPE)
            if (!defaultWorkdir.isNullOrBlank() && !client.changeWorkingDirectory(defaultWorkdir))
                Log.e("FTPClient at: $host") { "Could not change into work directory!" }
            return this
        } catch (ex: Exception) {
            Log.e("FTPClient at: $host", ex) { "Some other error occurred connecting to the FTP Server!" }
        }
        return null
    }

    fun disconnect() = client.disconnect()
    fun listDirectories() = client.listDirectories()
    fun listDirectories(path: String) = client.listDirectories(path)
    fun listFiles() = client.listFiles()
    fun listFiles(path: String) = client.listFiles(path)
    fun printWorkingDirectory() = client.printWorkingDirectory()

    fun downloadFile(remote: String, target: File, targetSize: Long) {
        val out = LimitedOutputStream(target, targetSize)
        client.retrieveFile(remote, out)
        out.close()
        Log.i("FTPClient at: $host") { "Downloaded file: ${target.name}" }
    }
}