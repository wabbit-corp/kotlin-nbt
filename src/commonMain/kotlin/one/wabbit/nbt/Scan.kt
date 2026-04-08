package one.wabbit.nbt

import kotlinx.io.Buffer
import kotlinx.io.Source
import kotlinx.io.readByteArray
import kotlinx.io.readIntLe
import kotlinx.io.readLongLe
import kotlinx.io.readShortLe
import kotlinx.io.write

enum class NbtScanByteOrder {
    BIG_ENDIAN,
    LITTLE_ENDIAN,
}

data class NbtScanOptions(
    val maxDepth: Int = 512,
    val maxBytesRead: Long = 512L * 1024L * 1024L,
    val maxStringBytes: Long = 32L * 1024L * 1024L,
    val maxByteArrayBytes: Long = 256L * 1024L * 1024L,
    val maxIntArrayBytes: Long = 256L * 1024L * 1024L,
    val maxLongArrayBytes: Long = 256L * 1024L * 1024L,
) {
    init {
        require(maxDepth >= 0) { "maxDepth must be non-negative." }
        require(maxBytesRead >= 0) { "maxBytesRead must be non-negative." }
        require(maxStringBytes >= 0) { "maxStringBytes must be non-negative." }
        require(maxByteArrayBytes >= 0) { "maxByteArrayBytes must be non-negative." }
        require(maxIntArrayBytes >= 0) { "maxIntArrayBytes must be non-negative." }
        require(maxLongArrayBytes >= 0) { "maxLongArrayBytes must be non-negative." }
    }
}

open class NbtScanException(
    message: String,
    cause: Throwable? = null,
) : IllegalArgumentException(message, cause)

class NbtScanQuotaException(
    message: String,
) : NbtScanException(message)

interface NbtScanInput {
    val options: NbtScanOptions
    val bytesRead: Long

    fun readNamedTagHeader(): Pair<TagType<out Tag>, String>

    fun readTagTypeCode(): TagType<out Tag>

    fun readStringPayload(): String

    fun readBytePayload(): Byte

    fun readShortPayload(): Short

    fun readIntPayload(): Int

    fun readLongPayload(): Long

    fun readFloatPayload(): Float

    fun readDoublePayload(): Double

    fun readListHeader(): Pair<TagType<out Tag>, Int>

    fun readByteArrayPayload(): ByteArray

    fun readIntArrayPayload(): IntArray

    fun readLongArrayPayload(): LongArray

    fun readIntLikePayload(type: TagType<out Tag>): Long

    fun skipPayload(
        type: TagType<out Tag>,
        depth: Int = 0,
    )

    fun scanCompoundEntries(
        depth: Int = 0,
        visitor: (String, TagType<out Tag>) -> Unit,
    )
}

class NbtCompoundSelection internal constructor(
    internal val fields: Map<String, NbtSelectedField>,
)

sealed interface NbtSelectedField {
    data object IntLike : NbtSelectedField

    data object String : NbtSelectedField

    data class Compound(
        val selection: NbtCompoundSelection,
    ) : NbtSelectedField

    data class CompoundList(
        val selection: NbtCompoundSelection,
    ) : NbtSelectedField
}

class NbtSelectionBuilder {
    private val fields = LinkedHashMap<String, NbtSelectedField>()

    fun intLike(name: String) {
        fields[name] = NbtSelectedField.IntLike
    }

    fun string(name: String) {
        fields[name] = NbtSelectedField.String
    }

    fun compound(
        name: String,
        block: NbtSelectionBuilder.() -> Unit,
    ) {
        fields[name] = NbtSelectedField.Compound(nbtSelection(block))
    }

    fun listOfCompounds(
        name: String,
        block: NbtSelectionBuilder.() -> Unit,
    ) {
        fields[name] = NbtSelectedField.CompoundList(nbtSelection(block))
    }

    internal fun build(): NbtCompoundSelection = NbtCompoundSelection(fields.toMap())
}

fun nbtSelection(block: NbtSelectionBuilder.() -> Unit): NbtCompoundSelection =
    NbtSelectionBuilder().apply(block).build()

sealed interface NbtSelectedValue

data class NbtSelectedIntLike(
    val value: Long,
) : NbtSelectedValue

data class NbtSelectedString(
    val value: String,
) : NbtSelectedValue

class NbtSelectedCompound internal constructor(
    private val fields: Map<String, NbtSelectedValue>,
) : NbtSelectedValue {
    operator fun get(name: String): NbtSelectedValue? = fields[name]

    fun intLike(name: String): Long? = (fields[name] as? NbtSelectedIntLike)?.value

    fun string(name: String): String? = (fields[name] as? NbtSelectedString)?.value

    fun compound(name: String): NbtSelectedCompound? = fields[name] as? NbtSelectedCompound

    fun compounds(name: String): List<NbtSelectedCompound> =
        (fields[name] as? NbtSelectedCompoundList)?.items ?: emptyList()

    fun asMap(): Map<String, NbtSelectedValue> = fields
}

data class NbtSelectedCompoundList(
    val items: List<NbtSelectedCompound>,
) : NbtSelectedValue

object NBTScan {
    fun <T> scanNamed(
        data: ByteArray,
        byteOrder: NbtScanByteOrder = NbtScanByteOrder.BIG_ENDIAN,
        options: NbtScanOptions = NbtScanOptions(),
        block: NbtScanInput.() -> T,
    ): T {
        val buffer = Buffer()
        buffer.write(data)
        return scanNamed(buffer, byteOrder, options, block)
    }

    fun <T> scanNamed(
        source: Source,
        byteOrder: NbtScanByteOrder = NbtScanByteOrder.BIG_ENDIAN,
        options: NbtScanOptions = NbtScanOptions(),
        block: NbtScanInput.() -> T,
    ): T = NbtScanInputImpl(source, byteOrder, options).block()

    fun selectNamed(
        data: ByteArray,
        selection: NbtCompoundSelection,
        byteOrder: NbtScanByteOrder = NbtScanByteOrder.BIG_ENDIAN,
        options: NbtScanOptions = NbtScanOptions(),
    ): NbtSelectedCompound {
        val buffer = Buffer()
        buffer.write(data)
        return selectNamed(buffer, selection, byteOrder, options)
    }

    fun selectNamed(
        source: Source,
        selection: NbtCompoundSelection,
        byteOrder: NbtScanByteOrder = NbtScanByteOrder.BIG_ENDIAN,
        options: NbtScanOptions = NbtScanOptions(),
    ): NbtSelectedCompound =
        scanNamed(source, byteOrder, options) {
            val (rootType, _) = readNamedTagHeader()
            require(rootType == TagType.Compound) { "Selective scan expects a TAG_Compound root." }
            extractCompound(this, selection, depth = 0)
        }
}

private class NbtScanInputImpl(
    private val source: Source,
    private val byteOrder: NbtScanByteOrder,
    override val options: NbtScanOptions,
) : NbtScanInput {
    override var bytesRead: Long = 0
        private set

    override fun readNamedTagHeader(): Pair<TagType<out Tag>, String> {
        val type = readTagTypeCode()
        val name =
            if (type != TagType.End) {
                readStringPayload()
            } else {
                ""
            }
        return type to name
    }

    override fun readTagTypeCode(): TagType<out Tag> {
        val rawType = readUnsignedByte()
        require(rawType in TagType.all.indices) { "Illegal tag type $rawType." }
        return TagType.all[rawType]
    }

    override fun readStringPayload(): String {
        val byteCount = readUnsignedShort()
        enforcePayloadBudget(byteCount.toLong(), options.maxStringBytes, "string payload")
        val bytes = readExactByteArray(byteCount)
        return bytes.decodeToString()
    }

    override fun readBytePayload(): Byte {
        reserveBytes(1, "byte payload")
        val result = source.readByte()
        bytesRead += 1
        return result
    }

    override fun readShortPayload(): Short {
        reserveBytes(2, "short payload")
        val result =
            when (byteOrder) {
                NbtScanByteOrder.BIG_ENDIAN -> source.readShort()
                NbtScanByteOrder.LITTLE_ENDIAN -> source.readShortLe()
            }
        bytesRead += 2
        return result
    }

    override fun readIntPayload(): Int {
        reserveBytes(4, "int payload")
        val result =
            when (byteOrder) {
                NbtScanByteOrder.BIG_ENDIAN -> source.readInt()
                NbtScanByteOrder.LITTLE_ENDIAN -> source.readIntLe()
            }
        bytesRead += 4
        return result
    }

    override fun readLongPayload(): Long {
        reserveBytes(8, "long payload")
        val result =
            when (byteOrder) {
                NbtScanByteOrder.BIG_ENDIAN -> source.readLong()
                NbtScanByteOrder.LITTLE_ENDIAN -> source.readLongLe()
            }
        bytesRead += 8
        return result
    }

    override fun readFloatPayload(): Float = Float.fromBits(readIntPayload())

    override fun readDoublePayload(): Double = Double.fromBits(readLongPayload())

    override fun readListHeader(): Pair<TagType<out Tag>, Int> = readTagTypeCode() to readIntPayload()

    override fun readByteArrayPayload(): ByteArray {
        val byteCount = readIntPayload()
        require(byteCount >= 0) { "Negative byte array length $byteCount." }
        enforcePayloadBudget(byteCount.toLong(), options.maxByteArrayBytes, "byte array payload")
        return readExactByteArray(byteCount)
    }

    override fun readIntArrayPayload(): IntArray {
        val length = readIntPayload()
        require(length >= 0) { "Negative int array length $length." }
        val byteCount = length.toLong() * 4L
        enforcePayloadBudget(byteCount, options.maxIntArrayBytes, "int array payload")
        return IntArray(length) { readIntPayload() }
    }

    override fun readLongArrayPayload(): LongArray {
        val length = readIntPayload()
        require(length >= 0) { "Negative long array length $length." }
        val byteCount = length.toLong() * 8L
        enforcePayloadBudget(byteCount, options.maxLongArrayBytes, "long array payload")
        return LongArray(length) { readLongPayload() }
    }

    override fun readIntLikePayload(type: TagType<out Tag>): Long =
        when (type) {
            TagType.Byte -> readBytePayload().toLong()
            TagType.Short -> readShortPayload().toLong()
            TagType.Int -> readIntPayload().toLong()
            TagType.Long -> readLongPayload()
            else -> throw IllegalArgumentException("Expected integer-like tag type, but found ${type.name}.")
        }

    override fun skipPayload(
        type: TagType<out Tag>,
        depth: Int,
    ) {
        when (type) {
            TagType.End -> Unit
            TagType.Byte -> skipBytes(1, "byte payload")
            TagType.Short -> skipBytes(2, "short payload")
            TagType.Int -> skipBytes(4, "int payload")
            TagType.Long -> skipBytes(8, "long payload")
            TagType.Float -> skipBytes(4, "float payload")
            TagType.Double -> skipBytes(8, "double payload")
            TagType.ByteArray -> {
                val length = readIntPayload()
                require(length >= 0) { "Negative byte array length $length." }
                val byteCount = length.toLong()
                enforcePayloadBudget(byteCount, options.maxByteArrayBytes, "byte array payload")
                skipBytes(byteCount, "byte array payload")
            }
            TagType.String -> {
                val byteCount = readUnsignedShort().toLong()
                enforcePayloadBudget(byteCount, options.maxStringBytes, "string payload")
                skipBytes(byteCount, "string payload")
            }
            TagType.List -> {
                requireDepth(depth)
                val (childType, length) = readListHeader()
                require(length >= 0) { "Negative list length $length." }
                repeat(length) {
                    skipPayload(childType, depth + 1)
                }
            }
            TagType.Compound -> {
                requireDepth(depth)
                while (true) {
                    val childType = readTagTypeCode()
                    if (childType == TagType.End) {
                        break
                    }
                    readStringPayload()
                    skipPayload(childType, depth + 1)
                }
            }
            TagType.IntArray -> {
                val length = readIntPayload()
                require(length >= 0) { "Negative int array length $length." }
                val byteCount = length.toLong() * 4L
                enforcePayloadBudget(byteCount, options.maxIntArrayBytes, "int array payload")
                skipBytes(byteCount, "int array payload")
            }
            TagType.LongArray -> {
                val length = readIntPayload()
                require(length >= 0) { "Negative long array length $length." }
                val byteCount = length.toLong() * 8L
                enforcePayloadBudget(byteCount, options.maxLongArrayBytes, "long array payload")
                skipBytes(byteCount, "long array payload")
            }
        }
    }

    override fun scanCompoundEntries(
        depth: Int,
        visitor: (String, TagType<out Tag>) -> Unit,
    ) {
        requireDepth(depth)
        while (true) {
            val tagType = readTagTypeCode()
            if (tagType == TagType.End) {
                return
            }
            val name = readStringPayload()
            visitor(name, tagType)
        }
    }

    private fun readUnsignedByte(): Int = readBytePayload().toInt() and 0xFF

    private fun readUnsignedShort(): Int = readShortPayload().toInt() and 0xFFFF

    private fun readExactByteArray(length: Int): ByteArray {
        reserveBytes(length.toLong(), "byte sequence")
        val bytes = source.readByteArray(length)
        bytesRead += length.toLong()
        return bytes
    }

    private fun skipBytes(
        byteCount: Long,
        context: String,
    ) {
        reserveBytes(byteCount, context)
        source.skip(byteCount)
        bytesRead += byteCount
    }

    private fun reserveBytes(
        byteCount: Long,
        context: String,
    ) {
        require(byteCount >= 0) { "Negative byte count $byteCount for $context." }
        if (bytesRead + byteCount > options.maxBytesRead) {
            throw NbtScanQuotaException(
                "NBT scan exceeded maxBytesRead ${options.maxBytesRead} while reading $context.",
            )
        }
    }

    private fun enforcePayloadBudget(
        byteCount: Long,
        maxBytes: Long,
        context: String,
    ) {
        if (byteCount > maxBytes) {
            throw NbtScanQuotaException(
                "NBT scan exceeded $context budget $maxBytes bytes with payload size $byteCount.",
            )
        }
    }

    private fun requireDepth(depth: Int) {
        if (depth > options.maxDepth) {
            throw NbtScanQuotaException(
                "NBT scan exceeded maxDepth ${options.maxDepth} at depth $depth.",
            )
        }
    }
}

private fun extractCompound(
    reader: NbtScanInput,
    selection: NbtCompoundSelection,
    depth: Int,
): NbtSelectedCompound {
    if (depth > reader.options.maxDepth) {
        throw NbtScanQuotaException(
            "NBT scan exceeded maxDepth ${reader.options.maxDepth} at depth $depth.",
        )
    }

    val fields = LinkedHashMap<String, NbtSelectedValue>()
    reader.scanCompoundEntries(depth) { name, tagType ->
        when (val requested = selection.fields[name]) {
            null -> reader.skipPayload(tagType, depth + 1)

            NbtSelectedField.IntLike -> {
                val value =
                    when (tagType) {
                        TagType.Byte,
                        TagType.Short,
                        TagType.Int,
                        TagType.Long,
                        -> reader.readIntLikePayload(tagType)

                        else -> {
                            reader.skipPayload(tagType, depth + 1)
                            null
                        }
                    }

                if (value != null) {
                    fields[name] = NbtSelectedIntLike(value)
                }
            }

            NbtSelectedField.String -> {
                if (tagType == TagType.String) {
                    fields[name] = NbtSelectedString(reader.readStringPayload())
                } else {
                    reader.skipPayload(tagType, depth + 1)
                }
            }

            is NbtSelectedField.Compound -> {
                if (tagType == TagType.Compound) {
                    fields[name] = extractCompound(reader, requested.selection, depth + 1)
                } else {
                    reader.skipPayload(tagType, depth + 1)
                }
            }

            is NbtSelectedField.CompoundList -> {
                if (tagType == TagType.List) {
                    val (childType, length) = reader.readListHeader()
                    require(length >= 0) { "Negative list length $length." }
                    if (childType == TagType.Compound || (childType == TagType.End && length == 0)) {
                        val items = ArrayList<NbtSelectedCompound>(length)
                        repeat(length) {
                            items.add(extractCompound(reader, requested.selection, depth + 1))
                        }
                        fields[name] = NbtSelectedCompoundList(items)
                    } else {
                        skipListItems(reader, childType, length, depth + 1)
                    }
                } else {
                    reader.skipPayload(tagType, depth + 1)
                }
            }
        }
    }
    return NbtSelectedCompound(fields)
}

private fun skipListItems(
    reader: NbtScanInput,
    childType: TagType<out Tag>,
    length: Int,
    depth: Int,
) {
    repeat(length) {
        reader.skipPayload(childType, depth + 1)
    }
}
