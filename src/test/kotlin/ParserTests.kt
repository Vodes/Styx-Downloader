import moe.styx.downloader.parsing.*
import kotlin.test.assertEquals
import kotlin.test.assertTrue

object ParserTests {

    fun testParser() {
        parseMetadata("The Age of Cosmos Exploration - S01E12 - 1080p WEB H.264 -NanDesuKa (B-Global).mkv").assertResult(
            "NanDesuKa",
            "12",
            "01",
            "The Age of Cosmos Exploration",
            null
        )

        parseMetadata("[SubsPlus+] Oshi no Ko - S02E01v2 (NF WEB 1080p AVC AAC) [E01A6580].mkv").assertResult(
            "SubsPlus+",
            "01",
            "02",
            "Oshi no Ko",
            "2"
        )

        parseMetadata("The.Misfit.of.Demon.King.Academy.S02E23.1080p.CR.WEB-DL.AAC2.0.H.264-NanDesuKa.mkv").assertResult(
            "NanDesuKa",
            "23",
            "02",
            "The Misfit of Demon King Academy"
        )

        parseMetadata("Invincible.2021.S02E01.A.LESSON.FOR.YOUR.NEXT.LIFE.1080p.AMZN.WEB-DL.DDP5.1.H.264-FLUX.mkv").assertResult(
            "FLUX",
            "01",
            "02",
            "Invincible 2021"
        )

        parseMetadata("Tensei shitara Slime Datta Ken - S01E01 (BD 1080p HEVC) [Vodes].mkv").assertResult(
            "Vodes",
            "01",
            "01",
            "Tensei shitara Slime Datta Ken"
        )

        parseMetadata("Whisper.Me.a.Love.Song.S01E08.1080p.WEBRip.DDP2.0.x265-smol.mkv").assertResult(
            "smol",
            "08",
            "01",
            "Whisper Me a Love Song"
        )

        parseMetadata("Given.S01E10.1080p.BluRay.Opus2.0.x265-smol.mkv").assertResult(
            "smol",
            "10",
            "01",
            "Given"
        )

        parseMetadata("[HatSubs] One Piece 1088.5 (WEB 1080p) [BAACCC99].mkv").assertResult(
            "HatSubs",
            "1088.5",
            null,
            "One Piece"
        )

        // Below here are worst case scenarios
        parseMetadata("KONOSUBA.-Gods.blessing.on.this.wonderful.world!.S03E11.Gods.Blessings.for.These.Unchanging.Days!.1080p.CR.WEB-DL.DUAL.AAC2.0.H.264.MSubs-ToonsHub.mkv").assertResult(
            "ToonsHub",
            "11",
            "03",
            "KONOSUBA -Gods blessing on this wonderful world!"
        )

        parseMetadata("Yubisaki to Renren - S01E09 - DUAL 480p WEB x264 -NanDesuKa (CR).mkv").assertResult(
            "NanDesuKa",
            "09",
            "01",
            "Yubisaki to Renren"
        )

        parseMetadata("[SubsPlus+] 2.5 Dimensional Seduction - S01E01 (CR WEB 1080p AVC EAC3) | 2.5 Jigen no Ririsa").assertResult(
            "SubsPlus+",
            "01",
            "01",
            "2.5 Dimensional Seduction"
        )

        parseMetadata("[SubsPlus+] 2.5 Dimensional Seduction - S01E01 (CR WEB 1080p AVC EAC3).mkv").assertResult(
            "SubsPlus+",
            "01",
            "01",
            "2.5 Dimensional Seduction"
        )

        parseMetadata("[SubsPlease] NieR Automata Ver1.1a - 13 (1080p) [DF36D5E3].mkv").assertResult(
            "SubsPlease",
            "13",
            null,
            "NieR Automata Ver1.1a"
        )

        parseMetadata("2.5 Jigen no Ririsa E01 [1080p][AAC][JapDub][GerSub][Web-DL].mkv").assertResult(
            "JapDub",
            "01",
            "01",
            "2.5 Jigen no Ririsa"
        )

        parseMetadata("NieRAutomata Ver 1.1a S2E01 [1080p][AAC][JapDub][GerEngSub][Web-DL].mkv").assertResult(
            "JapDub",
            "01",
            "2",
            "NieRAutomata Ver 1.1a"
        )
        parseMetadata("CITY.THE.ANIMATION.S01E02.2.1080p.AMZN.WEB-DL.MULTi.DDP2.0.H.264-VARYG.mkv").assertResult(
            "VARYG",
            "02",
            "01",
            "CITY THE ANIMATION"
        )
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
}