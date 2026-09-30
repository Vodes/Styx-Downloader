import kotlinx.serialization.encodeToString
import moe.styx.common.data.ProcessingOptions
import moe.styx.common.json
import moe.styx.downloader.other.*
import moe.styx.downloader.utils.needsMuxtools
import java.io.File
import java.nio.file.Files
import kotlin.test.*

class ProcessingTests {
    @Test
    fun missingDonorSkipsOnlyDependentOperationsAndDoesNotChangeSavedOptions() {
        val saved = ProcessingOptions(keepVideoOfPrevious = true, fillAudioOfPrevious = true,
            keepSubsOfPrevious = true, keepSubsMissingLanguages = true, keepNonEnglish = true,
            removeNewSubs = true, manualAudioSync = 50, manualSubSync = -100,
            keepBetterAudio = true, restyleSubs = true, normalizeTrackNames = true)
        val effective = saved.effectiveProcessing(false)
        assertEquals(8, effective.skipped.size)
        assertFalse(effective.options.fillAudioOfPrevious)
        assertFalse(effective.options.removeNewSubs)
        assertEquals(0L, effective.options.manualAudioSync)
        assertEquals(0L, effective.options.manualSubSync)
        assertTrue(effective.options.keepBetterAudio)
        assertTrue(effective.options.restyleSubs)
        assertTrue(effective.options.normalizeTrackNames)
        assertTrue(effective.options.removeUnnecessary)
        assertTrue(effective.options.fixTagging)
        assertTrue(saved.fillAudioOfPrevious)
        assertTrue(saved.removeNewSubs)
        assertEquals(saved, saved.effectiveProcessing(true).options)
    }

    @Test
    fun conflictingAudioPoliciesAreRejectedEvenWithoutDonor() {
        for (donor in listOf(false, true)) assertFailsWith<IllegalArgumentException> {
            ProcessingOptions(keepAudioOfPrevious = true, fillAudioOfPrevious = true).effectiveProcessing(donor)
        }
    }

    @Test
    fun filteringAndMetadataTriggerProcessingWhileSushiDoesNot() {
        val neutral = ProcessingOptions(removeUnnecessary = false, fixTagging = false)
        assertFalse(neutral.needsMuxtools())
        assertFalse(neutral.copy(sushiSubs = true).needsMuxtools())
        assertTrue(neutral.copy(removeUnnecessary = true).needsMuxtools())
        assertTrue(neutral.copy(fixTagging = true).needsMuxtools())
        assertTrue(neutral.copy(normalizeTrackNames = true).needsMuxtools())
        assertTrue(neutral.copy(fillAudioOfPrevious = true).needsMuxtools())
    }

    @Test
    fun waitForPreviousRequiresAnActualFileRegardlessOfProcessingOptions() {
        assertFailsWith<IllegalArgumentException> { prepareProcessing(null, false, true) }
        assertFailsWith<IllegalArgumentException> { prepareProcessing(ProcessingOptions(), false, true) }
        assertNotNull(prepareProcessing(ProcessingOptions(), true, true))
        assertNotNull(prepareProcessing(ProcessingOptions(), false, false))
        assertNull(prepareProcessing(null, false, false))
    }

    private val targetFolder = "Temppal Item no Chikara (Overgeared) [GerJapDub,GerEngSub,ADN]"
    private val donorFolder = "Temppal Item no Chikara (Overgeared) [GerJapDub,GerEngSub,CR]"

    private fun command(options: ProcessingOptions, donor: File? = File("previous file.mkv")) =
        buildProcessingCommand(File("python"), File("new file.mkv"), donor, options, File("stage"),
            "%english% - ${'$'}res${'$'}.mkv", "Show - 03", "03", "Show", supplementalInfo = targetFolder, donorSupplementalInfo = donorFolder)

    @Test
    fun commandUsesCurrentFlagsAndSeparateLanguageArguments() {
        val args = command(ProcessingOptions(fillAudioOfPrevious = true, keepSubsMissingLanguages = true,
            restyleSubs = true, normalizeTrackNames = true, tppSubs = true, sushiSubs = true,
            audioLanguages = " ja, , en, ", manualSubSync = -50))
        assertEquals(listOf("-m", "muxtools_styx", "--json", "mux"), args.subList(1, 5))
        for (flag in listOf("--donor", "--fill-audio", "--keep-subs-missing-languages", "--fix-tags",
            "--normalize-track-names", "--remove-unnecessary", "--restyle-subs", "--tpp",
            "--sub-sync=-50", "--supplemental-info", "--donor-supplemental-info")) assertTrue(flag in args, flag)
        assertEquals(targetFolder, args[args.indexOf("--supplemental-info") + 1])
        assertEquals(donorFolder, args[args.indexOf("--donor-supplemental-info") + 1])
        assertFalse("--keep-audio" in args)
        assertFalse(args.any { it.contains("sushi") })
        assertEquals(listOf("ja", "en"), args.indices.filter { args[it] == "--audio-language" }.map { args[it + 1] })
        assertTrue("%english% - ${'$'}res${'$'}.mkv" in args)
        assertTrue("--keep-audio" in command(ProcessingOptions(keepAudioOfPrevious = true)))
    }

    @Test
    fun bestAudioWorksWithoutDonorAndUsesAvailableDonor() {
        val options = ProcessingOptions(keepBetterAudio = true)
        assertTrue("--donor" in command(options))
        assertFalse("--donor" in command(options, null))
        assertFalse("--donor-supplemental-info" in command(options, null))
        assertTrue("--best-audio" in command(options, null))
    }

    @Test
    fun detectsMixedMuxtoolsTokens() {
        assertTrue(hasMuxtoolsTokens("%english% - ${'$'}ep${'$'} [${'$'}res${'$'}]"))
        assertFalse(hasMuxtoolsTokens("%english% - %ep%"))
    }

    @Test
    fun acceptsResolvedOutputAndRejectsInvalidResults() {
        val stage = Files.createTempDirectory("styx processing test ").toFile()
        try {
            val output = File(stage, "Resolved show 03.mkv").apply { writeText("fixture") }
            val resultJson = json.encodeToString(mapOf("output" to output.absolutePath))
            assertEquals(output, readProcessingOutput(resultJson, stage))
            assertEquals(output, readProcessingOutput("[23:48:57] DEBUG muxtools | Mux: Done\n$resultJson\n", stage))
            assertFails { readProcessingOutput("$resultJson\ninvalid-final-result", stage) }
            for (result in listOf("invalid", "{}", "{\"output\":\"relative.mkv\"}",
                json.encodeToString(mapOf("output" to File(stage, "missing.mkv").absolutePath)),
                json.encodeToString(mapOf("output" to File(stage, "result.json").apply { writeText("{}")} .absolutePath)))) {
                assertFails { readProcessingOutput(result, stage) }
            }
        } finally { stage.deleteRecursively() }
    }

    @Test
    fun cliFailurePreservesInputFiles() {
        val stage = Files.createTempDirectory("styx processing failure").toFile()
        try {
            val target = File(stage, "target.mkv").apply { writeText("target") }
            val donor = File(stage, "donor.mkv").apply { writeText("donor") }
            if (System.getProperty("os.name").startsWith("Windows")) return
            assertFailsWith<IllegalStateException> {
                runProcessingCommand(listOf("sh", "-c", "echo failure >&2; exit 1"), stage, File(stage, "log"))
            }
            assertFails { runProcessingCommand(listOf("sh", "-c", "echo invalid-json"), stage, File(stage, "log")) }
            assertFails { runProcessingCommand(listOf("sh", "-c", "echo '{}'; exit 0"), stage, File(stage, "log")) }
            assertEquals("target", target.readText())
            assertEquals("donor", donor.readText())
        } finally { stage.deleteRecursively() }
    }
}
