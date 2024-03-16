package moe.styx.downloader.other

import kotlinx.coroutines.delay
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import moe.styx.common.data.Media
import moe.styx.common.data.MediaEntry
import moe.styx.common.data.TMDBMapping
import moe.styx.common.data.tmdb.decodeMapping
import moe.styx.common.data.tmdb.getMappingForEpisode
import moe.styx.common.data.tmdb.orEmpty
import moe.styx.common.extension.currentUnixSeconds
import moe.styx.common.json
import moe.styx.common.util.launchGlobal
import moe.styx.db.StyxDBClient
import moe.styx.db.getEntries
import moe.styx.db.getMedia
import moe.styx.db.save
import moe.styx.downloader.Main
import moe.styx.downloader.getAppDir
import moe.styx.downloader.getDBClient
import moe.styx.downloader.utils.Log
import moe.styx.downloader.utils.getRemoteEpisodes
import java.io.File
import kotlin.time.DurationUnit
import kotlin.time.toDuration

@Serializable
data class EntryMetadataUpdate(val entryID: String, val added: Long, var lastCheck: Long)

object MetadataFetcher {
    private var entries = mutableListOf<EntryMetadataUpdate>()
    private val entryFile by lazy {
        File(getAppDir(), "queued-metadata-updates.json")
    }

    fun addEntry(entry: MediaEntry) {
        val now = currentUnixSeconds()
        entries.add(EntryMetadataUpdate(entry.GUID, now, now))
        save()
    }

    private fun save() = entryFile.writeText(json.encodeToString(entries))

    fun start() = launchGlobal {
        if (Main.config.tmdbToken.isBlank())
            return@launchGlobal
        if (entryFile.exists()) {
            entries =
                (runCatching { json.decodeFromString<List<EntryMetadataUpdate>>(entryFile.readText()) }.getOrNull()
                    ?: mutableListOf()).toMutableList()
        }
        delay(1.toDuration(DurationUnit.MINUTES))
        while (true) {
            if (entries.isEmpty()) {
                delay(5000)
                continue
            }
            getDBClient().executeAndClose {
                val now = currentUnixSeconds()
                for (updateEntry in entries) {
                    if (updateEntry.lastCheck > (now - 21600))
                        continue

                    val entry = getEntries(mapOf("GUID" to updateEntry.entryID)).firstOrNull() ?: continue
                    val media = getMedia(mapOf("GUID" to entry.mediaID)).firstOrNull() ?: continue
                    runCatching {
                        updateMetadataForEntry(entry, media, this)
                        updateEntry.lastCheck = now
                    }.onFailure { Log.e(exception = it) { "Failed to fetch metadata for '${media.name} - ${entry.entryNumber}'" } }
                    if (updateEntry.added < now - 86400) {
                        Log.i { "Removing this entry from metadata auto fetch queue." }
                        entries.remove(updateEntry)
                    }
                    delay(1.toDuration(DurationUnit.MINUTES))
                }
                save()
            }
            delay(30.toDuration(DurationUnit.MINUTES))
        }
    }
}

fun updateMetadataForEntry(entry: MediaEntry, media: Media, dbClient: StyxDBClient) {
    val tmdbMapping = media.decodeMapping().orEmpty().getMappingForEpisode(entry.entryNumber)?.let { it as TMDBMapping } ?: return
    val (metaEN, metaDE) = tmdbMapping.getRemoteEpisodes { Log.e("MetadataFetcher") { it } }
    val number = entry.entryNumber.toDouble() + tmdbMapping.offset
    val epMetaEN = metaEN.find { (if (it.order != null) it.order!! + 1 else it.episodeNumber) == number.toInt() }
    val epMetaDE = metaDE.find { (if (it.order != null) it.order!! + 1 else it.episodeNumber) == number.toInt() }
    if (epMetaEN == null)
        return
    val nameEN = epMetaEN.filteredName()
    val nameDE = epMetaDE?.filteredName() ?: ""
    val newEntry = entry.copy(
        nameEN = nameEN.ifBlank { entry.nameEN },
        nameDE = nameDE.ifBlank { entry.nameDE },
        synopsisEN = epMetaEN.overview.ifBlank { entry.synopsisEN },
        synopsisDE = (epMetaDE?.overview ?: "").ifBlank { entry.synopsisDE },
    )
    dbClient.save(newEntry)
    Log.i { "Updated entry metadata for '${media.name} - ${entry.entryNumber}'" }
}