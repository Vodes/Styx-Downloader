import kotlinx.serialization.decodeFromString
import moe.styx.common.json
import moe.styx.downloader.utils.MediaInfo
import kotlin.test.*

class MediaInspectionTests {
    @Test
    fun inspectionMetadataPreservesDatabaseAndLegacyNamingFields() {
        val media = json.decodeFromString<MediaInfo>("""{"track":[
            {"@type":"video","Format":"HEVC","Width":"1920","Height":"1080","BitDepth":"10"},
            {"@type":"audio","Format":"AAC","Language":"ja"},
            {"@type":"audio","Format":"EAC-3","Language":"de-DE"},
            {"@type":"audio","Format":"Opus","Language":"en-US"},
            {"@type":"text","Language":"deu"}
        ]}""")
        assertEquals("HEVC", media.videoCodec())
        assertEquals(10, media.videoBitDepth())
        assertEquals("1920x1080", media.videoResolution())
        assertTrue(media.hasGermanDub())
        assertTrue(media.hasEnglishDub())
        assertTrue(media.hasGermanSub())
        assertEquals("AAC", media.tracks.single { it.language == "ja" }.format)
    }

    @Test
    fun absentLanguagesAreNotReportedAsDubsOrSubtitles() {
        val media = json.decodeFromString<MediaInfo>("""{"track":[{"@type":"video"},{"@type":"audio","Language":"ja"}]}""")
        assertFalse(media.hasGermanDub())
        assertFalse(media.hasEnglishDub())
        assertFalse(media.hasGermanSub())
    }
}
