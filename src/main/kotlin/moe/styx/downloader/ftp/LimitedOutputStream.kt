package moe.styx.downloader.ftp

import com.google.common.util.concurrent.RateLimiter
import kotlinx.coroutines.delay
import moe.styx.downloader.utils.Log
import moe.styx.downloader.utils.launchThreaded
import moe.styx.downloader.utils.readableSize
import java.io.File
import java.io.FileOutputStream
import kotlin.time.DurationUnit
import kotlin.time.toDuration

class LimitedOutputStream(private val file: File, private val targetSize: Long, rateLimit: Double = 52000000.0) : FileOutputStream(file) {
    private var done = false
    private var firstWrite = true

    private var prevSize: Long = 0
    private var currentSize: Long = 0
    private val rateLimiter = RateLimiter.create(rateLimit)

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
                    val speed = (diff / 8).readableSize()
                    Log.d { "${file.name}: ${currentSize.readableSize()} / ${targetSize.readableSize()} | $speed/s" }
                    prevSize = currentSize
                    delay(8.toDuration(DurationUnit.SECONDS))
                }
            }
            firstWrite = false
        }
        rateLimiter.acquire(len)
        super.write(b, off, len)
    }
}