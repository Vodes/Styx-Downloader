import moe.styx.downloader.loadConfig
import moe.styx.downloader.utils.removeKeysFromURL
import kotlin.test.assertEquals

fun main() {
    loadConfig()
    assertEquals(
        "https://animetosho.org/api?t=tvsearch",
        removeKeysFromURL("https://animetosho.org/api?t=tvsearch&apikey=xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx"),
        "Didn't remove api keys properly!"
    )
    assertEquals(
        "https://animetosho.org/api?t=tvsearch",
        removeKeysFromURL("https://animetosho.org/api?user=test&t=tvsearch&apikey=xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx"),
        "Didn't remove api keys properly!"
    )
    ParserTests.testParser()
    RSSTest.testRSSFeed()
    SabTests.testSabnzbd()
    TransmissionTests.testTransmission()
}