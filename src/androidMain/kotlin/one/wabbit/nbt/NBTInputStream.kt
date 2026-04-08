package one.wabbit.nbt

import java.io.Closeable
import java.io.IOException
import java.io.InputStream
import java.nio.ByteOrder
import kotlinx.io.asSource
import kotlinx.io.buffered

class NBTInputStream @Throws(IOException::class) constructor(
    stream: InputStream,
    byteOrder: ByteOrder = ByteOrder.BIG_ENDIAN,
) : Closeable {
    private val source = stream.asSource().buffered()
    private val reader = NbtReader(source, byteOrder.toNbtByteOrder())

    @Throws(IOException::class)
    fun readNamedTag(): NamedTag = reader.readNamedTag()

    @Throws(IOException::class)
    fun readNamedTagHeader(): Pair<TagType<out Tag>, String> = reader.readNamedTagHeader()

    @Throws(IOException::class)
    fun readTagTypeCode(): TagType<out Tag> = reader.readTagTypeCode()

    @Throws(IOException::class)
    fun readStringPayload(): String = reader.readStringPayload()

    @Throws(IOException::class)
    fun readListHeader(): Pair<TagType<out Tag>, Int> = reader.readListHeader()

    @Throws(IOException::class)
    fun readByteArrayPayload(): ByteArray = reader.readByteArrayPayload()

    @Throws(IOException::class)
    fun readIntArrayPayload(): IntArray = reader.readIntArrayPayload()

    @Throws(IOException::class)
    fun readLongArrayPayload(): LongArray = reader.readLongArrayPayload()

    @Throws(IOException::class)
    fun readIntLikePayload(type: TagType<out Tag>): Long = reader.readIntLikePayload(type)

    @Throws(IOException::class)
    fun skipPayload(type: TagType<out Tag>) {
        reader.skipPayload(type)
    }

    @Throws(IOException::class)
    fun readRawTag(): Tag = reader.readRawTag()

    @Throws(IOException::class)
    override fun close() {
        source.close()
    }
}

private fun ByteOrder.toNbtByteOrder(): NbtByteOrder =
    if (this == ByteOrder.LITTLE_ENDIAN) {
        NbtByteOrder.LITTLE_ENDIAN
    } else {
        NbtByteOrder.BIG_ENDIAN
    }
