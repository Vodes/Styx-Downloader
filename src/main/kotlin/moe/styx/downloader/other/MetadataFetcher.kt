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
import moe.styx.common.extension.eqI
import moe.styx.common.json
import moe.styx.common.util.launchGlobal
import moe.styx.db.tables.ChangesTable
import moe.styx.db.tables.MediaEntryTable
import moe.styx.db.tables.MediaTable
import moe.styx.downloader.dbClient
import moe.styx.downloader.downloaderConfig
import moe.styx.downloader.getAppDir
import moe.styx.downloader.utils.Log
import moe.styx.downloader.utils.getRemoteEpisodes
import org.jetbrains.exposed.sql.selectAll
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
        if (downloaderConfig.tmdbToken.isBlank())
            return@launchGlobal
        Log.i { "Starting Metadatafetcher" }
        if (entryFile.exists()) {
            val parsed = runCatching { json.decodeFromString<List<EntryMetadataUpdate>>(entryFile.readText()) }.onFailure {
                Log.w("MetadataFetcher") { "Failed to parse queued-metadata-updates." }
            }.getOrNull()
            entries = (parsed ?: mutableListOf()).toMutableList()
        }
        delay(1.toDuration(DurationUnit.MINUTES))
        while (true) {
            if (entries.isEmpty()) {
                delay(5000)
                continue
            }
            entries.toList().forEachIndexed { index, updateEntry ->
                val now = currentUnixSeconds()

                val entry =
                    dbClient.transaction { MediaEntryTable.query { selectAll().where { GUID eq updateEntry.entryID }.toList() }.firstOrNull() }
                        ?: return@forEachIndexed
                val media = dbClient.transaction { MediaTable.query { selectAll().where { GUID eq entry.mediaID }.toList() }.firstOrNull() }
                    ?: return@forEachIndexed

                if (updateEntry.added < (now - 133200)) {
                    Log.i { "Removing '${media.name} - ${entry.entryNumber}' from metadata auto fetch queue." }
                    entries.removeIf { it.entryID eqI updateEntry.entryID }
                    return@forEachIndexed
                }

                if (updateEntry.lastCheck > (now - 21600))
                    return@forEachIndexed

                runCatching {
                    updateMetadataForEntry(entry, media)
                    entries.set(index, updateEntry.copy(lastCheck = now))
                }.onFailure { Log.e(exception = it) { "Failed to fetch metadata for '${media.name} - ${entry.entryNumber}'" } }
                delay(1.toDuration(DurationUnit.MINUTES))
            }
            save()
            delay(30.toDuration(DurationUnit.MINUTES))
        }
    }
}

fun updateMetadataForEntry(entry: MediaEntry, media: Media) {
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
    dbClient.transaction { MediaEntryTable.upsertItem(newEntry) }
    if (listOf(
            newEntry.nameEN?.equals(entry.nameEN),
            newEntry.nameDE?.equals(entry.nameDE),
            newEntry.synopsisEN?.equals(entry.synopsisEN),
            newEntry.synopsisDE?.equals(entry.synopsisDE)
        ).any { it == false }
    ) {
        Log.i { "Updated entry metadata for '${media.name} - ${entry.entryNumber}'" }
        runCatching { dbClient.transaction { ChangesTable.setToNow(false, true) } }
    }
}