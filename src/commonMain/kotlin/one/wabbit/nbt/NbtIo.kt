package one.wabbit.nbt

import kotlinx.io.Sink
import kotlinx.io.Source
import kotlinx.io.readByteArray
import kotlinx.io.readIntLe
import kotlinx.io.readLongLe
import kotlinx.io.readShortLe
import kotlinx.io.writeIntLe
import kotlinx.io.writeLongLe
import kotlinx.io.writeShortLe

internal enum class NbtByteOrder {
    BIG_ENDIAN,
    LITTLE_ENDIAN,
}

internal class NbtReader(
    private val source: Source,
    private val byteOrder: NbtByteOrder = NbtByteOrder.BIG_ENDIAN,
) {
    fun readNamedTag(): NamedTag = readNamedTag(0)

    fun readNamedTagHeader(): Pair<TagType<out Tag>, String> {
        val type = readTagTypeCode()
        val name =
            if (type != TagType.End) {
                readStringPayload()
            } else {
                ""
            }
        return type to name
    }

    fun readRawTag(): Tag {
        return readTagPayload(readTagTypeCode(), 0)
    }

    fun readTagTypeCode(): TagType<out Tag> {
        val rawType = readUnsignedByte()
        require(rawType in TagType.all.indices) { "Illegal tag type $rawType." }
        return TagType.all[rawType]
    }

    fun readStringPayload(): String {
        val nameLength = readShort().toInt() and 0xFFFF
        return source.readByteArray(nameLength).decodeToString()
    }

    fun readListHeader(): Pair<TagType<out Tag>, Int> = readTagTypeCode() to readInt()

    fun readByteArrayPayload(): ByteArray {
        val length = readInt()
        return source.readByteArray(length)
    }

    fun readIntArrayPayload(): IntArray {
        val length = readInt()
        return IntArray(length) { readInt() }
    }

    fun readLongArrayPayload(): LongArray {
        val length = readInt()
        return LongArray(length) { readLong() }
    }

    fun readIntLikePayload(type: TagType<out Tag>): Long =
        when (type) {
            TagType.Byte -> source.readByte().toLong()
            TagType.Short -> readShort().toLong()
            TagType.Int -> readInt().toLong()
            TagType.Long -> readLong()
            else -> throw IllegalArgumentException("Expected integer-like tag type, but found ${type.name}.")
        }

    fun skipPayload(type: TagType<out Tag>) {
        when (type) {
            TagType.End -> Unit
            TagType.Byte -> source.skip(1)
            TagType.Short -> source.skip(2)
            TagType.Int -> source.skip(4)
            TagType.Long -> source.skip(8)
            TagType.Float -> source.skip(4)
            TagType.Double -> source.skip(8)
            TagType.ByteArray -> {
                val length = readInt()
                source.skip(length.toLong())
            }
            TagType.String -> {
                val length = readShort().toInt() and 0xFFFF
                source.skip(length.toLong())
            }
            TagType.List -> {
                val (childType, length) = readListHeader()
                repeat(length) {
                    skipPayload(childType)
                }
            }
            TagType.Compound -> {
                while (true) {
                    val childType = readTagTypeCode()
                    if (childType == TagType.End) {
                        break
                    }
                    readStringPayload()
                    skipPayload(childType)
                }
            }
            TagType.IntArray -> {
                val length = readInt()
                source.skip(length.toLong() * 4L)
            }
            TagType.LongArray -> {
                val length = readInt()
                source.skip(length.toLong() * 8L)
            }
        }
    }

    private fun readNamedTag(depth: Int): NamedTag {
        val (type, name) = readNamedTagHeader()

        return NamedTag(name, readTagPayload(type, depth))
    }

    private fun <T : Tag> readTagPayload(type: TagType<T>, depth: Int): T {
        val result: Tag =
            when (type) {
                TagType.End ->
                    if (depth == 0) {
                        throw IllegalArgumentException(
                            "TAG_End found without a TAG_Compound/TAG_List tag preceding it."
                        )
                    } else {
                        EndTag
                    }

                TagType.Byte -> ByteTag(source.readByte())
                TagType.Short -> ShortTag(readShort())
                TagType.Int -> IntTag(readInt())
                TagType.Long -> LongTag(readLong())
                TagType.Float -> FloatTag(Float.fromBits(readInt()))
                TagType.Double -> DoubleTag(Double.fromBits(readLong()))
                TagType.ByteArray -> {
                    ByteArrayTag(readByteArrayPayload())
                }
                TagType.String -> StringTag(readStringPayload())
                TagType.List -> {
                    val (childType, length) = readListHeader()
                    val tagList = mutableListOf<Tag>()
                    repeat(length) {
                        val tag = readTagPayload(childType, depth + 1)
                        require(tag !is EndTag) { "An actual TAG_End was not expected here." }
                        tagList.add(tag)
                    }

                    @Suppress("UNCHECKED_CAST")
                    ListTag(childType as TagType<Tag>, tagList)
                }
                TagType.Compound -> {
                    val tagMap = mutableMapOf<String, Tag>()
                    while (true) {
                        val namedTag = readNamedTag(depth + 1)
                        val tag = namedTag.tag
                        if (tag is EndTag) {
                            break
                        }
                        tagMap[namedTag.name] = tag
                    }
                    CompoundTag(tagMap)
                }
                TagType.IntArray -> IntArrayTag(readIntArrayPayload())
                TagType.LongArray -> LongArrayTag(readLongArrayPayload())
            }

        @Suppress("UNCHECKED_CAST")
        return result as T
    }

    private fun readUnsignedByte(): Int = source.readByte().toInt() and 0xFF

    private fun readShort(): Short =
        when (byteOrder) {
            NbtByteOrder.BIG_ENDIAN -> source.readShort()
            NbtByteOrder.LITTLE_ENDIAN -> source.readShortLe()
        }

    private fun readInt(): Int =
        when (byteOrder) {
            NbtByteOrder.BIG_ENDIAN -> source.readInt()
            NbtByteOrder.LITTLE_ENDIAN -> source.readIntLe()
        }

    private fun readLong(): Long =
        when (byteOrder) {
            NbtByteOrder.BIG_ENDIAN -> source.readLong()
            NbtByteOrder.LITTLE_ENDIAN -> source.readLongLe()
        }
}

internal class NbtWriter(
    private val sink: Sink,
    private val byteOrder: NbtByteOrder = NbtByteOrder.BIG_ENDIAN,
) {
    fun writeNamedTag(name: String, tag: Tag) {
        val type = tag.type
        val nameBytes = name.encodeToByteArray()

        sink.writeByte(type.code.toByte())
        writeShort(nameBytes.size.toShort())
        sink.write(nameBytes)

        require(type != TagType.End) { "Named TAG_End not permitted." }
        writeTagPayload(tag)
    }

    fun writeRawTag(tag: Tag) {
        sink.writeByte(tag.type.code.toByte())
        writeTagPayload(tag)
    }

    private fun writeTagPayload(tag: Tag) {
        when (tag) {
            EndTag -> Unit
            is ByteTag -> sink.writeByte(tag.value)
            is ShortTag -> writeShort(tag.value)
            is IntTag -> writeInt(tag.value)
            is LongTag -> writeLong(tag.value)
            is FloatTag -> writeInt(tag.value.toBits())
            is DoubleTag -> writeLong(tag.value.toBits())
            is ByteArrayTag -> {
                writeInt(tag.value.size)
                sink.write(tag.value)
            }
            is StringTag -> {
                val bytes = tag.value.encodeToByteArray()
                writeShort(bytes.size.toShort())
                sink.write(bytes)
            }
            is ListTag<*> -> {
                sink.writeByte(tag.elementType.code.toByte())
                writeInt(tag.value.size)
                for (entry in tag.value) {
                    writeTagPayload(entry)
                }
            }
            is CompoundTag -> {
                for ((key, value) in tag.value) {
                    writeNamedTag(key, value)
                }
                sink.writeByte(0)
            }
            is IntArrayTag -> {
                writeInt(tag.value.size)
                for (entry in tag.value) {
                    writeInt(entry)
                }
            }
            is LongArrayTag -> {
                writeInt(tag.value.size)
                for (entry in tag.value) {
                    writeLong(entry)
                }
            }
        }
    }

    private fun writeShort(value: Short) {
        when (byteOrder) {
            NbtByteOrder.BIG_ENDIAN -> sink.writeShort(value)
            NbtByteOrder.LITTLE_ENDIAN -> sink.writeShortLe(value)
        }
    }

    private fun writeInt(value: Int) {
        when (byteOrder) {
            NbtByteOrder.BIG_ENDIAN -> sink.writeInt(value)
            NbtByteOrder.LITTLE_ENDIAN -> sink.writeIntLe(value)
        }
    }

    private fun writeLong(value: Long) {
        when (byteOrder) {
            NbtByteOrder.BIG_ENDIAN -> sink.writeLong(value)
            NbtByteOrder.LITTLE_ENDIAN -> sink.writeLongLe(value)
        }
    }
}
