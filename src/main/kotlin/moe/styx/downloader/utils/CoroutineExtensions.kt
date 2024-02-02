package moe.styx.downloader.utils

import kotlinx.coroutines.*

fun launchThreaded(run: suspend CoroutineScope.() -> Unit): CoroutineScope {
    val scope = CoroutineScope(Dispatchers.IO)
    scope.launch {
        run()
    }
    return scope
}

@OptIn(DelicateCoroutinesApi::class)
fun launchGlobal(run: suspend CoroutineScope.() -> Unit) {
    GlobalScope.launch {
        run()
    }
}
