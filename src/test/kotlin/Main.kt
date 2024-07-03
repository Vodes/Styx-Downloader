import moe.styx.downloader.parsing.*
import moe.styx.downloader.torrent.transmission.TransmissionClient
import kotlin.test.assertEquals
import kotlin.test.assertTrue


fun main() {
    testParser()
    testTransmission()
}

private fun testParser() {
    var filename = "[SubsPlus+] Oshi no Ko - S02E01v2 (NF WEB 1080p AVC AAC) [E01A6580].mkv"
    parseMetadata(filename).assertResult("SubsPlus+", "01", "02", "Oshi no Ko", "2")

    filename = "The.Misfit.of.Demon.King.Academy.S02E23.1080p.CR.WEB-DL.AAC2.0.H.264-NanDesuKa.mkv"
    parseMetadata(filename).assertResult("NanDesuKa", "23", "02", "The Misfit of Demon King Academy")

    filename = "Invincible.2021.S02E01.A.LESSON.FOR.YOUR.NEXT.LIFE.1080p.AMZN.WEB-DL.DDP5.1.H.264-FLUX.mkv"
    parseMetadata(filename).assertResult("FLUX", "01", "02", "Invincible 2021")

    filename = "Tensei shitara Slime Datta Ken - S01E01 (BD 1080p HEVC) [Vodes].mkv"
    parseMetadata(filename).assertResult("Vodes", "01", "01", "Tensei shitara Slime Datta Ken")

    filename = "Whisper.Me.a.Love.Song.S01E08.1080p.WEBRip.DDP2.0.x265-smol.mkv"
    parseMetadata(filename).assertResult("smol", "08", "01", "Whisper Me a Love Song")

    filename = "Given.S01E10.1080p.BluRay.Opus2.0.x265-smol.mkv"
    parseMetadata(filename).assertResult("smol", "10", "01", "Given")

    // Below here are worst case scenarios
    filename =
        "KONOSUBA.-Gods.blessing.on.this.wonderful.world!.S03E11.Gods.Blessings.for.These.Unchanging.Days!.1080p.CR.WEB-DL.DUAL.AAC2.0.H.264.MSubs-ToonsHub.mkv"
    parseMetadata(filename).assertResult("ToonsHub", "11", "03", "KONOSUBA -Gods blessing on this wonderful world!")

    filename = "Yubisaki to Renren - S01E09 - DUAL 480p WEB x264 -NanDesuKa (CR).mkv"
    parseMetadata(filename).assertResult("NanDesuKa", "09", "01", "Yubisaki to Renren")
}

private fun AnitomyResults.assertResult(
    group: String?,
    episode: String?,
    season: String?,
    title: String?,
    version: String? = null
) {
    assertEquals(group, this.group)
    assertEquals(episode, this.episode)
    assertEquals(season, this.season)
    assertTrue { this.title.equals(title) || this.title?.replace(" ", ".").equals(title?.replace(" ", ".")) }
    assertEquals(version, this.version)
}

private fun testTransmission() {
    val client = TransmissionClient(
        System.getenv("TRANSMISSION_URL"),
        System.getenv("TRANSMISSION_USER"),
        System.getenv("TRANSMISSION_PASS")
    )
    println("Auth: ${client.authenticate()}")
    println("Torrents: ${client.listTorrents()}")
    val t = client.addTorrentByURL("https://linuxmint.com/torrents/linuxmint-21-cinnamon-64bit.iso.torrent", "/downloads")
    if (t != null) {
        println("Torrent added: ${t.name}")
        println("Deleted: ${client.deleteTorrent(t.hash)}")
    }
}