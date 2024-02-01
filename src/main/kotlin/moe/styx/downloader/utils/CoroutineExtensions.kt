package moe.styx.downloader.utils

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

fun launchThreaded(run: suspend CoroutineScope.() -> Unit): Pair<Job, CoroutineScope> {
    val job = Job()
    val scope = CoroutineScope(job)
    scope.launch {
        run()
    }
    return Pair(job, scope)
}