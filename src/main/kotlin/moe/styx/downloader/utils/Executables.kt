package moe.styx.downloader.utils

import kotlinx.serialization.decodeFromString
import moe.styx.common.isWindows
import moe.styx.common.json
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

private val managedExecutables = ConcurrentHashMap<String, File>()

fun getExecutableFromPath(name: String): File? = resolveExecutable(name, managedLookup = { tool, python ->
    managedExecutables[tool]?.takeIf { it.isFile && it.canExecute() }
        ?: findManagedExecutable(tool, python)?.also { managedExecutables[tool] = it }
})

internal fun resolveExecutable(
    name: String,
    pathLookup: (String) -> File? = { findPathExecutable(it) },
    managedLookup: (String, File) -> File? = ::findManagedExecutable,
): File? {
    pathLookup(name)?.let { return it }
    if (name == "python") return pathLookup("python3")
    if (name == "python3") return null
    val python = pathLookup("python") ?: pathLookup("python3") ?: return null
    return managedLookup(name, python)
}

internal fun findPathExecutable(name: String, path: String? = System.getenv("PATH")): File? {
    val names = if (isWindows) listOf(name, "$name.exe", "$name.cmd", "$name.bat") else listOf(name)
    for (directory in path.orEmpty().split(File.pathSeparator).filter { it.isNotBlank() }) {
        for (candidate in names) {
            val executable = File(directory, candidate)
            if (executable.isFile && executable.canExecute()) return executable.absoluteFile
        }
    }
    return null
}

internal fun findManagedExecutable(name: String, python: File): File? = runCatching {
    val script = "import json, sys; from muxtools import get_executable; print(json.dumps(get_executable(sys.argv[1], can_error=False)))"
    val output = runPythonQuery(python, script, name)
    json.decodeFromString<String?>(output)?.let { File(it) }?.takeIf { it.isFile && it.canExecute() }?.absoluteFile
}.getOrNull()

internal fun runPythonQuery(python: File, script: String, argument: String, timeoutSeconds: Long = 30): String =
    runPythonCommand(python, listOf("-c", script, argument), timeoutSeconds)
        .lineSequence().lastOrNull { it.isNotBlank() } ?: error("Python query returned no result")

internal fun runPythonCommand(python: File, arguments: List<String>, timeoutSeconds: Long = 30): String {
    val stdout = File.createTempFile("styx-python-", ".out")
    val stderr = File.createTempFile("styx-python-", ".err")
    var process: Process? = null
    try {
        process = ProcessBuilder(listOf(python.absolutePath) + arguments)
            .redirectOutput(stdout).redirectError(stderr).start()
        check(process.waitFor(timeoutSeconds, TimeUnit.SECONDS)) { "Python query timed out" }
        check(process.exitValue() == 0) { "Python query failed: ${stderr.readText().takeLast(2000)}" }
        return stdout.readText().trimEnd()
    } finally {
        if (process?.isAlive == true) {
            process.destroyForcibly()
            process.waitFor(5, TimeUnit.SECONDS)
        }
        stdout.delete()
        stderr.delete()
    }
}
