package moe.styx.downloader.utils

import moe.styx.downloader.Main

fun resolveTemplate(input: String): Pair<String, String> {
    var inputProxy = input
    var templatedURI = input
    for ((name, uri) in Main.config.torrentConfig.feedTemplates) {
        if (input.contains("%$name%", true)) {
            templatedURI = uri
            inputProxy = inputProxy.replace("%$name%", "", true)
            break
        }
    }
    return templatedURI to inputProxy.trim()
}