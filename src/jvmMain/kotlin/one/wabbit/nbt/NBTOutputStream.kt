package one.wabbit.nbt

import java.io.Closeable
import java.io.IOException
import java.io.OutputStream
import java.nio.ByteOrder
import kotlinx.io.asSink
import kotlinx.io.buffered

/**
 * This class writes **NBT**, or **Named Binary Tag** `Tag` objects to an underlying `OutputStream`.
 *
 * The NBT format was created by Markus Persson, and the specification may be found at
 * [http://www.minecraft.net/docs/NBT.txt](http://www.minecraft.net/docs/NBT.txt).
 */
class NBTOutputStream
/**
 * Creates a new `NBTOutputStream`, which will write data to the specified underlying output stream.
 *
 * @param os The output stream.
 * @throws IOException if an I/O error occurs.
 */
@Throws(IOException::class)
constructor(
    os: OutputStream,
    byteOrder: ByteOrder = ByteOrder.BIG_ENDIAN,
) : Closeable {
    private val sink = os.asSink().buffered()
    private val writer = NbtWriter(sink, byteOrder.toNbtByteOrder())

    /**
     * Writes a tag.
     *
     * @param tag The tag to write.
     * @throws IOException if an I/O error occurs.
     */
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
