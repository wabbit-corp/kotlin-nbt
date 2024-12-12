package one.wabbit.nbt

import one.wabbit.io.EndianInputStream
import java.io.Closeable
import java.io.IOException
import java.io.InputStream
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets

/**
 * This class reads **NBT**, or **Named Binary Tag**
 * streams, and produces an object graph of subclasses of the `Tag`
 * object.
 *
 *
 * The NBT format was created by Markus Persson, and the specification may be
 * found at [http://www.minecraft.net/docs/NBT.txt](http://www.minecraft.net/docs/NBT.txt).
 */
class NBTInputStream
/**
 * Creates a new `NBTInputStream`, which will source its data
 * from the specified input stream.
 *
 * @param `is` the input stream
 * @throws IOException if an I/O error occurs
 */
@Throws(IOException::class)
constructor(stream: InputStream, byteOrder: ByteOrder = ByteOrder.BIG_ENDIAN) : Closeable {
    private val stream: EndianInputStream = EndianInputStream(stream, byteOrder)

    /**
     * Reads an NBT tag from the stream.
     *
     * @return The tag that was read.
     * @throws IOException if an I/O error occurs.
     */
    @Throws(IOException::class)
    fun readNamedTag(): NamedTag = readNamedTag(0)

    /**
     * Reads an NBT from the stream.
     *
     * @param depth the depth of this tag
     * @return The tag that was read.
     * @throws IOException if an I/O error occurs.
     */
    @Throws(IOException::class)
    private fun readNamedTag(depth: Int): NamedTag {
        val rawType = stream.readByte().toInt() and 0xFF
        if (rawType > TagType.all.size)
            throw IOException("Illegal tag type ${rawType}.")

        val type = TagType.all[rawType]

        val name: String = if (type != TagType.End) {
            val nameLength = stream.readShort().toInt() and 0xFFFF
            val nameBytes = ByteArray(nameLength)
            stream.readFully(nameBytes)
            String(nameBytes, StandardCharsets.UTF_8)
        } else {
            ""
        }

        return NamedTag(name, readTagPayload(type, depth))
    }

    /**
     * Reads the payload of a tag given the type.
     *
     * @param type the type
     * @param depth the depth
     * @return the tag
     * @throws IOException if an I/O error occurs.
     */
    @Throws(IOException::class)
    private fun <T : Tag> readTagPayload(type: TagType<T>, depth: Int): T {
        val result: Tag = when (type) {
            TagType.End -> if (depth == 0) {
                throw IOException(
                    "TAG_End found without a TAG_Compound/TAG_List tag preceding it."
                )
            } else EndTag

            TagType.Byte -> ByteTag(stream.readByte())
            TagType.Short -> ShortTag(stream.readShort())
            TagType.Int -> IntTag(stream.readInt())
            TagType.Long -> LongTag(stream.readLong())
            TagType.Float -> FloatTag(stream.readFloat())
            TagType.Double -> DoubleTag(stream.readDouble())
            TagType.ByteArray -> {
                val length = stream.readInt()
                val bytes = ByteArray(length)
                stream.readFully(bytes)
                ByteArrayTag(bytes)
            }
            TagType.String -> {
                val length = stream.readShort().toInt()
                val bytes = ByteArray(length)
                stream.readFully(bytes)
                StringTag(String(bytes, StandardCharsets.UTF_8))
            }
            TagType.List -> {
                val rawType = stream.readByte().toInt() and 0xFF
                if (rawType > TagType.all.size)
                    throw IOException("Illegal tag type $rawType.")

                val childType = TagType.all[rawType]
                val length = stream.readInt()

                val tagList = mutableListOf<Tag>() // length
                for (i in 0 until length) {
                    val tag = readTagPayload(childType, depth + 1)
                    if (tag is EndTag) {
                        throw IOException("An actual TAG_End was not expected here.")
                    }
                    tagList.add(tag)
                }

                @Suppress("UNCHECKED_CAST")
                (ListTag(
        childType as TagType<Tag>,
        tagList
    ))
            }
            TagType.Compound -> {
                val tagMap = mutableMapOf<String, Tag>()
                while (true) {
                    val namedTag = readNamedTag(depth + 1)
                    val tag = namedTag.tag
                    if (tag is EndTag) break
                    tagMap[namedTag.name] = tag
                }
                CompoundTag(tagMap)
            }
            TagType.IntArray -> {
                val length = stream.readInt()
                val data = IntArray(length)
                for (i in 0 until length) {
                    data[i] = stream.readInt()
                }
                IntArrayTag(data)
            }
        }

        @Suppress("UNCHECKED_CAST")
        return result as T
    }

    fun readRawTag(): Tag {
        val rawType = stream.readByte().toInt() and 0xFF
        if (rawType > TagType.all.size)
            throw IOException("Illegal tag type ${rawType}.")

        val type = TagType.all[rawType]
        return readTagPayload(type, 0)
    }

    @Throws(IOException::class)
    override fun close() {
        stream.close()
    }
}
