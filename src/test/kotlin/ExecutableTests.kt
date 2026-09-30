import kotlinx.serialization.encodeToString
import moe.styx.common.json
import moe.styx.downloader.utils.*
import java.io.File
import java.nio.file.Files
import kotlin.test.*

class ExecutableTests {
    @Test
    fun pathTakesPrecedenceAndManagedFallbackUsesPython3() {
        val direct = File("/direct/ffmpeg")
        val python = File("/direct/python3")
        val managed = File("/managed/ffmpeg")
        assertEquals(direct, resolveExecutable("ffmpeg", { if (it == "ffmpeg") direct else null }, { _, _ -> error("Unexpected fallback") }))
        assertEquals(managed, resolveExecutable("ffmpeg", { if (it == "python3") python else null }, { name, interpreter ->
            assertEquals("ffmpeg", name)
            assertEquals(python, interpreter)
            managed
        }))
        assertEquals(python, resolveExecutable("python", { if (it == "python3") python else null }, { _, _ -> error("Recursive Python lookup") }))
        assertNull(resolveExecutable("ffmpeg", { null }, { _, _ -> error("No interpreter") }))
    }

    @Test
    fun pathLookupRejectsDirectoriesNonExecutablesAndUnrelatedExtensions() {
        val root = Files.createTempDirectory("styx-path-test").toFile()
        try {
            File(root, "ffmpeg").mkdir()
            assertNull(findPathExecutable("ffmpeg", root.absolutePath))
            File(root, "ffmpeg").delete()
            File(root, "ffmpeg.txt").writeText("not executable")
            assertNull(findPathExecutable("ffmpeg", root.absolutePath))
            val executable = File(root, "ffmpeg").apply { writeText("fixture"); setExecutable(true) }
            assertEquals(executable, findPathExecutable("ffmpeg", root.absolutePath))
            if (!System.getProperty("os.name").startsWith("Windows")) {
                executable.setExecutable(false)
                assertNull(findPathExecutable("ffmpeg", root.absolutePath))
            }
            assertNull(findPathExecutable("ffmpeg", null))
        } finally { root.deleteRecursively() }
    }

    @Test
    fun managedLookupAcceptsSpacesAndHandlesMissingOrFailedQueries() {
        if (System.getProperty("os.name").startsWith("Windows")) return
        val root = Files.createTempDirectory("styx managed lookup ").toFile()
        try {
            val tool = File(root, "managed tool").apply { writeText("fixture"); setExecutable(true) }
            val python = File(root, "fake python").apply {
                writeText("#!/bin/sh\nprintf '%s\\n' 'diagnostics' '${json.encodeToString(tool.absolutePath)}'\n")
                setExecutable(true)
            }
            assertEquals(tool, findManagedExecutable("ffmpeg", python))
            tool.delete()
            assertNull(findManagedExecutable("ffmpeg", python))
            python.writeText("#!/bin/sh\necho null\n")
            assertNull(findManagedExecutable("ffmpeg", python))
            python.writeText("#!/bin/sh\necho error >&2\nexit 1\n")
            assertNull(findManagedExecutable("ffmpeg", python))
            assertFailsWith<IllegalStateException> { runPythonQuery(python, "ignored", "ignored") }
            python.writeText("#!/bin/sh\nexec sleep 10\n")
            assertFailsWith<IllegalStateException> { runPythonQuery(python, "ignored", "ignored", timeoutSeconds = 0) }
        } finally { root.deleteRecursively() }
    }

    @Test
    fun readableOutputKeepsAllLines() {
        if (System.getProperty("os.name").startsWith("Windows")) return
        val root = Files.createTempDirectory("styx-readable-inspection").toFile()
        try {
            val python = File(root, "python").apply {
                writeText("#!/bin/sh\nprintf 'Video tracks:\\n  AVC\\nAudio tracks:\\n  Japanese\\n'\n")
                setExecutable(true)
            }
            assertEquals("Video tracks:\n  AVC\nAudio tracks:\n  Japanese", runPythonCommand(python, listOf("-m", "muxtools_styx", "inspect", "file with spaces.mkv")))
        } finally { root.deleteRecursively() }
    }
}
