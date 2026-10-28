package com.airgesture.control

import java.io.InputStream
import java.io.ByteArrayOutputStream
import java.io.PushbackInputStream
import java.nio.ByteBuffer
import java.util.zip.ZipInputStream

internal object HandModelBundle {
    fun extract(input: InputStream): ByteBuffer = PushbackInputStream(input, 4).use { source ->
        val header = ByteArray(4)
        var read = 0
        while (read < header.size) {
            val count = source.read(header, read, header.size - read)
            require(count > 0) { "Incomplete model bundle" }
            read += count
        }
        // MediaPipe's pinned .task archive has a two-byte prefix before ZIP.
        val offset = when {
            header[0] == 80.toByte() && header[1] == 75.toByte() -> 0
            header[0] == 0.toByte() && header[1] == 0.toByte() &&
                header[2] == 80.toByte() && header[3] == 75.toByte() -> 2
            else -> throw IllegalArgumentException("Invalid model bundle header")
        }
        source.unread(header, offset, header.size - offset)
        ZipInputStream(source).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (entry.name != "hand_landmarker.task") continue
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (true) {
                    val count = zip.read(buffer)
                    if (count < 0) break
                    require(output.size().toLong() + count <= 8_373_440L) { "Oversized hand submodel" }
                    output.write(buffer, 0, count)
                }
                require(output.size() > 0) { "Empty hand submodel" }
                return ByteBuffer.allocateDirect(output.size()).apply { put(output.toByteArray()); rewind() }
            }
        }
        throw IllegalArgumentException("Hand submodel missing from pinned bundle")
    }
}
