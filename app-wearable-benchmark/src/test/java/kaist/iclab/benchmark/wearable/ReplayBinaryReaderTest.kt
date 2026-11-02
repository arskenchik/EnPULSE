package kaist.iclab.benchmark.wearable

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

class ReplayBinaryReaderTest {
    @Test
    fun readsHeaderAndLittleEndianRecord() {
        val file = File.createTempFile("replay", ".bin")
        try {
            val buffer = ByteBuffer.allocate(32 + 8 + 2 * 2 * 4).order(ByteOrder.LITTLE_ENDIAN)
            buffer.put("ICLRPLY1".toByteArray(Charsets.US_ASCII))
            buffer.putInt(1)
            buffer.putInt(2)
            buffer.putInt(2)
            buffer.putInt(1)
            buffer.putLong(1)
            buffer.putInt(42)
            buffer.putInt(7)
            floatArrayOf(1f, 2f, 3f, 4f).forEach(buffer::putFloat)
            file.writeBytes(buffer.array())

            ReplayBinaryReader(file).use { reader ->
                assertEquals(2, reader.header.rows)
                assertEquals(2, reader.header.channels)
                assertEquals(1, reader.header.count)
                val window = reader.next()!!
                assertEquals(42, window.windowId)
                assertEquals(7, window.label)
                assertArrayEquals(floatArrayOf(1f, 2f, 3f, 4f), window.values, 0f)
                assertEquals(null, reader.next())
            }
        } finally {
            file.delete()
        }
    }
}
