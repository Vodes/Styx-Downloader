package moe.styx.downloader.ftp

import kotlinx.coroutines.delay
import moe.styx.downloader.utils.Log
import moe.styx.downloader.utils.launchThreaded
import moe.styx.downloader.utils.readableSize
import java.io.File
import java.io.FileOutputStream
import kotlin.time.DurationUnit
import kotlin.time.toDuration

class LimitedOutputStream(private val file: File, private val targetSize: Long, private val rateLimit: Double = 0.0) : FileOutputStream(file) {
    private var done = false
    private var firstWrite = true
    private var wait = false

    private var prevSize: Long = 0
    private var currentSize: Long = 0

    override fun close() {
        done = true
        super.close()
    }

    //TODO: Figure out some smart way to ratelimit without adding another dependency
    override fun write(b: ByteArray, off: Int, len: Int) {
        currentSize += len
        if (firstWrite) {
            launchThreaded {
                while (!done) {
                    val diff = currentSize - prevSize
                    val speed = (diff / 3).readableSize()
//                    if (diff / 3 > rateLimit)
//                        wait = true
                    Log.d { "${file.name}: ${currentSize.readableSize()} / ${targetSize.readableSize()} | $speed/s" }
                    prevSize = currentSize
                    delay(3.toDuration(DurationUnit.SECONDS))
                }
            }
            firstWrite = false
        }
//        if (wait)
//            Thread.sleep(1000).also { wait = false }
        super.write(b, off, len)
    }
}