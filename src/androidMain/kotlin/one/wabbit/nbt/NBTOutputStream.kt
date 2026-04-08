package one.wabbit.nbt

import java.io.Closeable
import java.io.IOException
import java.io.OutputStream
import java.nio.ByteOrder
import kotlinx.io.asSink
import kotlinx.io.buffered

class NBTOutputStream @Throws(IOException::class) constructor(
    output: OutputStream,
    byteOrder: ByteOrder = ByteOrder.BIG_ENDIAN,
) : Closeable {
    private val sink = output.asSink().buffered()
    private val writer = NbtWriter(sink, byteOrder.toNbtByteOrder())

    @Throws(IOException::class)
    fun writeNamedTag(name: String, tag: Tag) {
        writer.writeNamedTag(name, tag)
    }

    @Throws(IOException::class)
    fun writeRawTag(tag: Tag) {
        writer.writeRawTag(tag)
    }

    @Throws(IOException::class)
    override fun close() {
        sink.close()
    }
}

private fun ByteOrder.toNbtByteOrder(): NbtByteOrder =
    if (this == ByteOrder.LITTLE_ENDIAN) {
        NbtByteOrder.LITTLE_ENDIAN
    } else {
        NbtByteOrder.BIG_ENDIAN
    }
