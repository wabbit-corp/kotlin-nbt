package one.wabbit.nbt

import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract
import kotlin.reflect.KClass

object NBTPackage {
    fun forceLoad() = Unit
}

sealed class TagType<T : Tag>(
    val code: kotlin.Int,
    val clazz: KClass<T>,
    val name: kotlin.String,
) {
    data object End : TagType<EndTag>(0, EndTag::class, "TAG_End")

    data object Byte : TagType<ByteTag>(1, ByteTag::class, "TAG_Byte")

    data object Short : TagType<ShortTag>(2, ShortTag::class, "TAG_Short")

    data object Int : TagType<IntTag>(3, IntTag::class, "TAG_Int")

    data object Long : TagType<LongTag>(4, LongTag::class, "TAG_Long")

    data object Float : TagType<FloatTag>(5, FloatTag::class, "TAG_Float")

    data object Double : TagType<DoubleTag>(6, DoubleTag::class, "TAG_Double")

    data object ByteArray : TagType<ByteArrayTag>(7, ByteArrayTag::class, "TAG_Byte_Array")

    data object String : TagType<StringTag>(8, StringTag::class, "TAG_String")

    data object List : TagType<ListTag<*>>(9, ListTag::class, "TAG_List")

    data object Compound : TagType<CompoundTag>(10, CompoundTag::class, "TAG_Compound")

    data object IntArray : TagType<IntArrayTag>(11, IntArrayTag::class, "TAG_Int_Array")

    data object LongArray : TagType<LongArrayTag>(12, LongArrayTag::class, "TAG_Long_Array")

    companion object {
        val all: kotlin.collections.List<TagType<out Tag>> by lazy {
            listOf(
                End,
                Byte,
                Short,
                Int,
                Long,
                Float,
                Double,
                ByteArray,
                String,
                List,
                Compound,
                IntArray,
                LongArray,
            ).also { entries ->
                for (index in entries.indices) {
                    check(entries[index].code == index) {
                        "TagType code mismatch: ${entries[index].code} != $index"
                    }
                }
            }
        }

        private val codeToTagType: Map<kotlin.Int, TagType<out Tag>> by lazy { all.associateBy { it.code } }
        private val nameToTagType: Map<kotlin.String, TagType<out Tag>> by lazy { all.associateBy { it.name } }
        private val kclassToTagType: Map<KClass<out Tag>, TagType<out Tag>> by lazy {
            all.associateBy { it.clazz }
        }

        fun fromCode(code: kotlin.Int): TagType<out Tag> =
            codeToTagType[code] ?: throw IllegalArgumentException("Invalid tag code ($code)")

        fun fromCode(code: kotlin.Byte): TagType<out Tag> =
            codeToTagType[code.toInt()] ?: throw IllegalArgumentException("Invalid tag code ($code)")

        fun fromName(name: kotlin.String): TagType<out Tag> =
            nameToTagType[name] ?: throw IllegalArgumentException("Invalid tag name ($name)")

        @Suppress("UNCHECKED_CAST")
        fun <T : Tag> fromClass(clazz: KClass<T>): TagType<T> =
            (kclassToTagType[clazz] as? TagType<T>)
                ?: throw IllegalArgumentException("Invalid tag class ($clazz)")
    }
}

sealed class Tag {
    abstract val type: TagType<out Tag>

    @OptIn(ExperimentalContracts::class)
    fun <T : Tag> isListOf(cls: TagType<T>): Boolean {
        contract { returns(true) implies (this@Tag is ListTag<*>) }

        if (this !is ListTag<*>) {
            return false
        }
        if (this.elementType == TagType.End && this.value.isEmpty()) {
            return true
        }
        return this.elementType == cls
    }

    @OptIn(ExperimentalContracts::class)
    inline fun <reified T : Tag> asListOf(): ListTag<T>? {
        contract { returnsNotNull() implies (this@Tag is ListTag<*>) }

        if (this !is ListTag<*>) {
            return null
        }
        if (this.elementType == TagType.End && this.value.isEmpty()) {
            @Suppress("UNCHECKED_CAST")
            return this as ListTag<T>
        }
        if (this.elementType.clazz == T::class || T::class == Tag::class) {
            @Suppress("UNCHECKED_CAST")
            return this as ListTag<T>
        }
        return null
    }

    fun toByteArray(name: String): ByteArray = NBTSerialization.toByteArray(name, this)

    fun toRawByteArray(): ByteArray = NBTSerialization.toRawByteArray(this)

    fun toCompressedByteArray(name: String, compression: Compression): ByteArray =
        NBTSerialization.toCompressedByteArray(compression, name, this)

    fun toCompressedRawByteArray(compression: Compression): ByteArray =
        NBTSerialization.toCompressedRawByteArray(compression, this)

    fun toSNBT(): String {
        val builder = StringBuilder()
        toSNBT(builder)
        return builder.toString()
    }

    fun toSNBT(builder: StringBuilder) {
        when (this) {
            is CompoundTag -> {
                builder.append("{")
                val iterator = value.iterator()
                var first = true
                while (iterator.hasNext()) {
                    if (!first) {
                        builder.append(", ")
                    }
                    first = false
                    val entry = iterator.next()
                    builder.append(entry.key).append(": ")
                    entry.value.toSNBT(builder)
                }
                builder.append("}")
            }

            is ListTag<*> -> {
                builder.append("[")
                val iterator = value.iterator()
                var first = true
                while (iterator.hasNext()) {
                    if (!first) {
                        builder.append(", ")
                    }
                    first = false
                    iterator.next().toSNBT(builder)
                }
                builder.append("]")
            }

            is ByteArrayTag -> {
                builder.append("[B;")
                for (index in value.indices) {
                    if (index != 0) {
                        builder.append(", ")
                    }
                    builder.append(value[index].toInt()).append("B")
                }
                builder.append("]")
            }

            is IntArrayTag -> {
                builder.append("[I;")
                for (index in value.indices) {
                    if (index != 0) {
                        builder.append(", ")
                    }
                    builder.append(value[index])
                }
                builder.append("]")
            }

            is LongArrayTag -> {
                builder.append("[L;")
                for (index in value.indices) {
                    if (index != 0) {
                        builder.append(", ")
                    }
                    builder.append(value[index]).append("L")
                }
                builder.append("]")
            }

            is StringTag -> {
                builder.append('"')
                for (char in value) {
                    when (char) {
                        '\\' -> builder.append("\\\\")
                        '"' -> builder.append("\\\"")
                        '\b' -> builder.append("\\b")
                        '\t' -> builder.append("\\t")
                        '\n' -> builder.append("\\n")
                        '\u000C' -> builder.append("\\f")
                        '\r' -> builder.append("\\r")
                        else -> builder.append(char)
                    }
                }
                builder.append('"')
            }

            is ByteTag -> builder.append(value).append("b")
            is ShortTag -> builder.append(value).append("s")
            is IntTag -> builder.append(value)
            is LongTag -> builder.append(value).append("l")
            is FloatTag -> builder.append(value).append("f")
            is DoubleTag -> builder.append(value).append("d")
            EndTag -> builder.append("END")
        }
    }

    companion object {
        fun fromByteArray(data: ByteArray): Tag = NBTSerialization.fromByteArray(data)

        fun fromRawByteArray(data: ByteArray): Tag = NBTSerialization.fromRawByteArray(data)

        fun fromCompressedByteArray(compression: Compression, data: ByteArray): Tag =
            NBTSerialization.fromCompressedByteArray(compression, data)

        fun fromCompressedRawByteArray(compression: Compression, data: ByteArray): Tag =
            NBTSerialization.fromCompressedRawByteArray(compression, data)

        fun fromByteArrayAuto(data: ByteArray): Tag = NBTSerialization.fromByteArrayAuto(data)

        fun fromSNBT(text: String): Tag = SNBT.parse(text)
    }
}

data class NamedTag(val name: String, val tag: Tag)

object EndTag : Tag() {
    override val type: TagType<EndTag>
        get() = TagType.End

    override fun toString(): String = "TAG_End"
}

data class ByteTag(var value: Byte) : Tag() {
    override val type: TagType<out Tag>
        get() = TagType.Byte

    override fun toString(): String = "TAG_Byte($value)"
}

data class ShortTag(var value: Short) : Tag() {
    override val type: TagType<out Tag>
        get() = TagType.Short

    override fun toString(): String = "TAG_Short($value)"
}

data class IntTag(var value: Int) : Tag() {
    override val type: TagType<out Tag>
        get() = TagType.Int

    override fun toString(): String = "TAG_Int($value)"
}

data class LongTag(var value: Long) : Tag() {
    override val type: TagType<out Tag>
        get() = TagType.Long

    override fun toString(): String = "TAG_Long($value)"
}

data class FloatTag(var value: Float) : Tag() {
    override val type: TagType<out Tag>
        get() = TagType.Float

    override fun toString(): String = "TAG_Float($value)"
}

data class DoubleTag(var value: Double) : Tag() {
    override val type: TagType<out Tag>
        get() = TagType.Double

    override fun toString(): String = "TAG_Double($value)"
}

data class StringTag(var value: String) : Tag() {
    override val type: TagType<out Tag>
        get() = TagType.String

    override fun toString(): String = "TAG_String($value)"
}

data class ByteArrayTag(var value: ByteArray) : Tag() {
    override val type: TagType<out Tag>
        get() = TagType.ByteArray

    override fun toString(): String {
        val hex = StringBuilder()
        for (byte in value) {
            hex.appendByteAsHex(byte).append(' ')
        }
        return "TAG_Byte_Array($hex)"
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }
        if (other !is ByteArrayTag) {
            return false
        }
        return value.contentEquals(other.value)
    }

    override fun hashCode(): Int = value.contentHashCode()
}

data class IntArrayTag(var value: IntArray) : Tag() {
    override val type: TagType<out Tag>
        get() = TagType.IntArray

    override fun toString(): String {
        val hex = StringBuilder()
        for (intValue in value) {
            val hexDigits = intValue.toUInt().toString(16).uppercase()
            if (hexDigits.length == 1) {
                hex.append('0')
            }
            hex.append(hexDigits).append(' ')
        }
        return "TAG_Int_Array($hex)"
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }
        if (other !is IntArrayTag) {
            return false
        }
        return value.contentEquals(other.value)
    }

    override fun hashCode(): Int = value.contentHashCode()
}

data class LongArrayTag(var value: LongArray) : Tag() {
    override val type: TagType<out Tag>
        get() = TagType.LongArray

    override fun toString(): String {
        val hex = StringBuilder()
        for (longValue in value) {
            val hexDigits = longValue.toULong().toString(16).uppercase()
            if (hexDigits.length == 1) {
                hex.append('0')
            }
            hex.append(hexDigits).append(' ')
        }
        return "TAG_Long_Array($hex)"
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }
        if (other !is LongArrayTag) {
            return false
        }
        return value.contentEquals(other.value)
    }

    override fun hashCode(): Int = value.contentHashCode()
}

fun byteTagOf(value: Byte): ByteTag = ByteTag(value)

fun shortTagOf(value: Short): ShortTag = ShortTag(value)

fun intTagOf(value: Int): IntTag = IntTag(value)

fun longTagOf(value: Long): LongTag = LongTag(value)

fun floatTagOf(value: Float): FloatTag = FloatTag(value)

fun doubleTagOf(value: Double): DoubleTag = DoubleTag(value)

fun stringTagOf(value: String): StringTag = StringTag(value)

fun byteArrayTagOf(value: ByteArray): ByteArrayTag = ByteArrayTag(value)

fun intArrayTagOf(value: IntArray): IntArrayTag = IntArrayTag(value)

fun longArrayTagOf(value: LongArray): LongArrayTag = LongArrayTag(value)

class ListTag<T : Tag>(val elementType: TagType<T>, val value: MutableList<T>) : Tag() {
    init {
        if (elementType == TagType.End) {
            require(value.isEmpty()) { "EndTag lists cannot contain elements" }
        }
    }

    override val type: TagType<out Tag>
        get() = TagType.List

    override fun toString(): String {
        val builder = StringBuilder()
        builder
            .append("TAG_List")
            .append(": ")
            .append(value.size)
            .append(" entries of type ")
            .append(elementType.name)
            .append("\r\n{\r\n")
        for (tag in value) {
            builder.append("   ").append(tag.toString().replace("\r\n", "\r\n   ")).append("\r\n")
        }
        builder.append("}")
        return builder.toString()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }
        if (other !is ListTag<*>) {
            return false
        }
        return value == other.value && elementType == other.elementType
    }

    override fun hashCode(): Int {
        var result = value.hashCode()
        result = 31 * result + elementType.hashCode()
        return result
    }
}

inline fun <reified T : Tag> listTagOf(vararg values: T): ListTag<T> =
    ListTag(TagType.fromClass(T::class), values.toMutableList())

inline fun <reified T : Tag> listTagOf(values: Collection<T>): ListTag<T> =
    ListTag(TagType.fromClass(T::class), values.toMutableList())

data class CompoundTag(var value: MutableMap<String, Tag>) : Tag() {
    override val type: TagType<out Tag>
        get() = TagType.Compound

    operator fun get(key: String): Tag? = value[key]

    operator fun set(key: String, tag: Tag) {
        value[key] = tag
    }

    operator fun contains(key: String): Boolean = value.containsKey(key)

    fun containsKey(key: String): Boolean = value.containsKey(key)

    fun getByteArray(key: String): ByteArray? = (value[key] as? ByteArrayTag)?.value

    fun getIntArray(key: String): IntArray? = (value[key] as? IntArrayTag)?.value

    fun getLongArray(key: String): LongArray? = (value[key] as? LongArrayTag)?.value

    fun getByte(key: String): Byte? = (value[key] as? ByteTag)?.value

    fun getShort(key: String): Short? = (value[key] as? ShortTag)?.value

    fun getInt(key: String): Int? = (value[key] as? IntTag)?.value

    fun getLong(key: String): Long? = (value[key] as? LongTag)?.value

    fun getFloat(key: String): Float? = (value[key] as? FloatTag)?.value

    fun getDouble(key: String): Double? = (value[key] as? DoubleTag)?.value

    fun getString(key: String): String? = (value[key] as? StringTag)?.value

    fun getMap(key: String): Map<String, Tag>? = (value[key] as? CompoundTag)?.value

    fun <T : Tag> getList(key: String, clazz: KClass<T>): List<T>? {
        val list = value[key] as? ListTag<*> ?: return null
        val expectedType = TagType.fromClass(clazz)
        @Suppress("UNCHECKED_CAST")
        return if (list.elementType == expectedType) {
            list.value as List<T>
        } else {
            null
        }
    }

    inline fun <reified T : Tag> getList(key: String): List<T>? = getList(key, T::class)

    override fun toString(): String {
        val builder = StringBuilder()
        builder.append("TAG_Compound").append(": ").append(value.size).append(" entries\r\n{\r\n")
        for ((key, entryValue) in value) {
            builder
                .append("   ")
                .append(key)
                .append("  :  ")
                .append(entryValue.toString().replace("\r\n", "\r\n   "))
                .append("\r\n")
        }
        builder.append("}")
        return builder.toString()
    }

    companion object {
        fun empty(): CompoundTag = CompoundTag(mutableMapOf())
    }
}

fun compoundTagOf(vararg values: Pair<String, Tag>): CompoundTag {
    val result = CompoundTag(mutableMapOf())
    for (index in 0..<values.size) {
        val entry = values[index]
        result.value[entry.first] = entry.second
    }
    return result
}

fun compoundTagOf(values: Collection<Pair<String, Tag>>): CompoundTag {
    val result = CompoundTag(mutableMapOf())
    for (entry in values) {
        result.value[entry.first] = entry.second
    }
    return result
}

private val uppercaseHexDigits = "0123456789ABCDEF"

private fun StringBuilder.appendByteAsHex(value: Byte): StringBuilder {
    val unsignedValue = value.toInt() and 0xFF
    append(uppercaseHexDigits[unsignedValue ushr 4])
    append(uppercaseHexDigits[unsignedValue and 0x0F])
    return this
}
