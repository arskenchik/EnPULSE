package kaist.iclab.benchmark.wearable

import java.io.Closeable
import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

data class ReplayHeader(
    val version: Int,
    val rows: Int,
    val channels: Int,
    val dtypeCode: Int,
    val count: Long,
)

data class ReplayWindow(val windowId: Int, val label: Int, val values: FloatArray)

/** Reads the little-endian ICLRPLY1 format produced by prepare_samosa_replay.py. */
class ReplayBinaryReader(file: File) : Closeable {
    private val stream = FileInputStream(file)
    private val channel = stream.channel
    val header: ReplayHeader
    private var windowsRead = 0L

    init {
        val bytes = ByteBuffer.allocate(32).order(ByteOrder.LITTLE_ENDIAN)
        readFully(bytes)
        bytes.flip()
        val magicBytes = ByteArray(8)
        bytes.get(magicBytes)
        require(String(magicBytes, Charsets.US_ASCII) == "ICLRPLY1") { "Invalid replay magic" }
        header = ReplayHeader(bytes.int, bytes.int, bytes.int, bytes.int, bytes.long)
        require(header.version == 1) { "Unsupported replay version ${header.version}" }
        require(header.dtypeCode == 1) { "Only float32 replay input is supported" }
    }

    fun next(): ReplayWindow? {
        if (windowsRead >= header.count) return null
        val bytes = ByteBuffer.allocate(8 + header.rows * header.channels * 4)
            .order(ByteOrder.LITTLE_ENDIAN)
        readFully(bytes)
        bytes.flip()
        val id = bytes.int
        val label = bytes.int
        val values = FloatArray(header.rows * header.channels)
        bytes.asFloatBuffer().get(values)
        windowsRead++
        return ReplayWindow(id, label, values)
    }

    private fun readFully(buffer: ByteBuffer) {
        while (buffer.hasRemaining()) {
            check(channel.read(buffer) >= 0) { "Unexpected end of replay file" }
        }
    }

    override fun close() = stream.close()
}
