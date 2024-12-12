package one.wabbit.nbt

import java.io.Closeable
import java.io.DataOutputStream
import java.io.IOException
import java.io.OutputStream
import java.nio.charset.StandardCharsets

/**
 * This class writes **NBT**, or **Named Binary Tag**
 * `Tag` objects to an underlying `OutputStream`.
 *
 *
 * The NBT format was created by Markus Persson, and the specification may be
 * found at [http://www.minecraft.net/docs/NBT.txt](http://www.minecraft.net/docs/NBT.txt).
 */
class NBTOutputStream
/**
 * Creates a new `NBTOutputStream`, which will write data to the
 * specified underlying output stream.
 *
 * @param os The output stream.
 * @throws IOException if an I/O error occurs.
 */
@Throws(IOException::class)
constructor(os: OutputStream) : Closeable {
    /**
     * The output stream.
     */
    private val os: DataOutputStream = DataOutputStream(os)

    /**
     * Writes a tag.
     *
     * @param tag
     * The tag to write.
     * @throws IOException
     * if an I/O error occurs.
     */
    @Throws(IOException::class)
    fun writeNamedTag(name: String, tag: Tag) {
        val type = tag.type
        val nameBytes = name.toByteArray(StandardCharsets.UTF_8)

        os.writeByte(type.code)
        os.writeShort(nameBytes.size)
        os.write(nameBytes)

        if (type == TagType.End) {
            throw IOException("Named TAG_End not permitted.")
        }

        writeTagPayload(tag)
    }

    /**
     * Writes tag payload.
     *
     * @param tag
     * The tag.
     * @throws IOException
     * if an I/O error occurs.
     */
    @Throws(IOException::class)
    private fun writeTagPayload(tag: Tag) {
        val type = tag.type
        when (type) {
            TagType.End -> writeEndTagPayload(tag as EndTag)
            TagType.Byte -> writeByteTagPayload(tag as ByteTag)
            TagType.Short -> writeShortTagPayload(tag as ShortTag)
            TagType.Int -> writeIntTagPayload(tag as IntTag)
            TagType.Long -> writeLongTagPayload(tag as LongTag)
            TagType.Float -> writeFloatTagPayload(tag as FloatTag)
            TagType.Double -> writeDoubleTagPayload(tag as DoubleTag)
            TagType.ByteArray -> writeByteArrayTagPayload(tag as ByteArrayTag)
            TagType.String -> writeStringTagPayload(tag as StringTag)
            TagType.List -> writeListTagPayload(tag as ListTag<*>)
            TagType.Compound -> writeCompoundTagPayload(tag as CompoundTag)
            TagType.IntArray -> writeIntArrayTagPayload(tag as IntArrayTag)
        }
    }

    @Throws(IOException::class)
    fun writeRawTag(tag: Tag) {
        os.writeByte(tag.type.code)
        writeTagPayload(tag)
    }

    /**
     * Writes a `TAG_Byte` tag.
     *
     * @param tag
     * The tag.
     * @throws IOException
     * if an I/O error occurs.
     */
    @Throws(IOException::class)
    private fun writeByteTagPayload(tag: ByteTag) {
        os.writeByte(tag.value.toInt())
    }

    /**
     * Writes a `TAG_Byte_Array` tag.
     *
     * @param tag
     * The tag.
     * @throws IOException
     * if an I/O error occurs.
     */
    @Throws(IOException::class)
    private fun writeByteArrayTagPayload(tag: ByteArrayTag) {
        val bytes = tag.value
        os.writeInt(bytes.size)
        os.write(bytes)
    }

    /**
     * Writes a `TAG_Compound` tag.
     *
     * @param tag
     * The tag.
     * @throws IOException
     * if an I/O error occurs.
     */
    @Throws(IOException::class)
    private fun writeCompoundTagPayload(tag: CompoundTag) {
        for ((key, value) in tag.value) {
            writeNamedTag(key, value)
        }
        os.writeByte(0.toByte().toInt()) // end tag - better way?
    }

    /**
     * Writes a `TAG_List` tag.
     *
     * @param tag
     * The tag.
     * @throws IOException
     * if an I/O error occurs.
     */
    @Throws(IOException::class)
    private fun <T : Tag> writeListTagPayload(tag: ListTag<T>) {
        val elementType = tag.elementType
        val tags = tag.value
        val size = tags.size

        os.writeByte(elementType.code)
        os.writeInt(size)
        for (t in tags) {
            writeTagPayload(t)
        }
    }

    /**
     * Writes a `TAG_String` tag.
     *
     * @param tag
     * The tag.
     * @throws IOException
     * if an I/O error occurs.
     */
    @Throws(IOException::class)
    private fun writeStringTagPayload(tag: StringTag) {
        val bytes = tag.value.toByteArray(StandardCharsets.UTF_8)
        os.writeShort(bytes.size)
        os.write(bytes)
    }

    /**
     * Writes a `TAG_Double` tag.
     *
     * @param tag
     * The tag.
     * @throws IOException
     * if an I/O error occurs.
     */
    @Throws(IOException::class)
    private fun writeDoubleTagPayload(tag: DoubleTag) {
        os.writeDouble(tag.value)
    }

    /**
     * Writes a `TAG_Float` tag.
     *
     * @param tag
     * The tag.
     * @throws IOException
     * if an I/O error occurs.
     */
    @Throws(IOException::class)
    private fun writeFloatTagPayload(tag: FloatTag) {
        os.writeFloat(tag.value)
    }

    /**
     * Writes a `TAG_Long` tag.
     *
     * @param tag
     * The tag.
     * @throws IOException
     * if an I/O error occurs.
     */
    @Throws(IOException::class)
    private fun writeLongTagPayload(tag: LongTag) {
        os.writeLong(tag.value)
    }

    /**
     * Writes a `TAG_Int` tag.
     *
     * @param tag
     * The tag.
     * @throws IOException
     * if an I/O error occurs.
     */
    @Throws(IOException::class)
    private fun writeIntTagPayload(tag: IntTag) {
        os.writeInt(tag.value)
    }

    /**
     * Writes a `TAG_Short` tag.
     *
     * @param tag
     * The tag.
     * @throws IOException
     * if an I/O error occurs.
     */
    @Throws(IOException::class)
    private fun writeShortTagPayload(tag: ShortTag) {
        os.writeShort(tag.value.toInt())
    }

    /**
     * Writes a `TAG_Empty` tag.
     *
     * @param tag the tag
     */
    private fun writeEndTagPayload(@Suppress("UNUSED_PARAMETER") tag: EndTag) {
        /* empty */
    }

    @Throws(IOException::class)
    private fun writeIntArrayTagPayload(tag: IntArrayTag) {
        val data = tag.value
        os.writeInt(data.size)
        for (aData in data) {
            os.writeInt(aData)
        }
    }

    @Throws(IOException::class)
    override fun close() {
        os.close()
    }
}
