import moe.styx.downloader.loadConfig

fun main() {
    loadConfig()
    ParserTests.testParser()
    RSSTest.testRSSFeed()
    TransmissionTests.testTransmission()
}