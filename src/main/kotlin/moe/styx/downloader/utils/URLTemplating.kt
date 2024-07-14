package moe.styx.downloader.utils

import moe.styx.downloader.Main

fun resolveTemplate(input: String): Pair<String, String> {
    var inputProxy = input
    var templatedURI = input
    for ((name, uri) in Main.config.rssConfig.feedTemplates) {
        if (input.contains("%$name%", true)) {
            templatedURI = uri
            inputProxy = inputProxy.replace("%$name%", "", true)
            break
        }
    }
    return templatedURI to inputProxy.trim()
}

fun removeKeysFromURL(url: String): String {
    var cleaned = url
    var match: MatchResult?

    while (RegexCollection.stupidKeyRegex.find(cleaned).also { match = it } != null) {
        if (match!!.groups["sep"]?.value == "?") {
            match!!.groups["key"]?.value?.let {
                cleaned = cleaned.replace(it, "", true)
            }
        } else
            cleaned = cleaned.replace(match!!.groups[0]!!.value, "", true)
    }
    return cleaned
}