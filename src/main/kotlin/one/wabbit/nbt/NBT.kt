package one.wabbit.nbt

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.*
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract
import kotlin.reflect.KClass

object NBTPackage {
    /**
     * Forces the class loader to load a predefined set of classes by accessing their names.
     *
     * This method initializes specified classes by referencing their class names to ensure
     * that they are loaded into the JVM. This is commonly used to trigger static initializers
     * or to ensure the availability of certain classes at runtime.
     *
     * The classes being loaded pertain to various `Tag` implementations, which represent
     * different types of NBT elements and associated I/O utilities.
     */
    fun forceLoad() {
        val classes = arrayOf(
                TagType::class.java,
                EndTag::class.java,
                ByteTag::class.java,
                ShortTag::class.java,
                IntTag::class.java,
                LongTag::class.java,
                FloatTag::class.java,
                DoubleTag::class.java,
                ByteArrayTag::class.java,
                StringTag::class.java,
                ListTag::class.java,
                CompoundTag::class.java,
                IntArrayTag::class.java,
                NBTOutputStream::class.java,
                NBTInputStream::class.java
        )
        for (c in classes) {
            c.name
        }
    }
}

/**
 * A class which holds constant values.
 */
sealed class TagType<T : Tag>(val code: kotlin.Int, val clazz: KClass<T>, val name: kotlin.String) {
    data object End        : TagType<EndTag>(0, EndTag::class, "TAG_End")
    data object Byte       : TagType<ByteTag>(1, ByteTag::class, "TAG_Byte")
    data object Short      : TagType<ShortTag>(2, ShortTag::class, "TAG_Short")
    data object Int        : TagType<IntTag>(3, IntTag::class, "TAG_Int")
    data object Long       : TagType<LongTag>(4, LongTag::class, "TAG_Long")
    data object Float      : TagType<FloatTag>(5, FloatTag::class, "TAG_Float")
    data object Double     : TagType<DoubleTag>(6, DoubleTag::class, "TAG_Double")
    data object ByteArray : TagType<ByteArrayTag>(7, ByteArrayTag::class, "TAG_Byte_Array")
    data object String     : TagType<StringTag>(8, StringTag::class, "TAG_String")
    data object List       : TagType<ListTag<*>>(9, ListTag::class, "TAG_List")
    data object Compound   : TagType<CompoundTag>(10, CompoundTag::class, "TAG_Compound")
    data object IntArray  : TagType<IntArrayTag>(11, IntArrayTag::class, "TAG_Int_Array")

    companion object {
        val all = listOf(
            End, Byte, Short, Int, Long, Float, Double,
            ByteArray, String, List, Compound, IntArray,
        )

        init {
            for (i in all.indices) {
                check(all[i].code == i) { "TagType code mismatch: ${all[i].code} != $i" }
            }
        }

        private val codeToTagType = all.associateBy { it.code }
        private val nameToTagType = all.associateBy { it.name }
        private val kclazzToTagType = all.associateBy { it.clazz }

        @JvmStatic fun fromCode(code: kotlin.Int) : TagType<out Tag> =
            codeToTagType[code] ?: throw IllegalArgumentException("Invalid tag code ($code)")
        @JvmStatic fun fromCode(code: kotlin.Byte) : TagType<out Tag> =
            codeToTagType[code.toInt()] ?: throw IllegalArgumentException("Invalid tag code ($code)")
        @JvmStatic fun fromName(name: kotlin.String) : TagType<out Tag> =
            nameToTagType[name] ?: throw IllegalArgumentException("Invalid tag name ($name)")

        @Suppress("UNCHECKED_CAST")
        @JvmStatic fun <T : Tag> fromClass(clazz: KClass<T>) : TagType<T> =
            (kclazzToTagType[clazz] as? TagType<T>) ?: throw IllegalArgumentException("Invalid tag class ($clazz)")
    }
}

/**
 * A sealed class representing a generic NBT (Named Binary Tag) structure.
 * This is used to serialize and deserialize hierarchical data. Subclasses
 * of `Tag` represent specific data types, such as numbers, lists, strings,
 * or compound structures.
 */
sealed class Tag {
    abstract val type: TagType<out Tag>

    /**
     * Checks whether the current tag is a `ListTag` containing elements of the specified type.
     *
     * @param cls The class type to check against the elements of the `ListTag`.
     * @return `true` if the tag is a `ListTag` with elements of the specified type or if
     *         it matches the criteria for an empty `ListTag` with an `EndTag` element type;
     *         otherwise, `false`.
     */
    @OptIn(ExperimentalContracts::class)
    fun <T : Tag> isListOf(cls: TagType<T>): Boolean {
        contract {
            returns(true) implies (this@Tag is ListTag<*>)
        }

        if (this !is ListTag<*>)
            return false
        if (this.elementType == TagType.End && this.value.size == 0)
            return true
        if (this.elementType == cls)
            return true
        return false
    }

    /**
     * Converts the current tag to a `ListTag` of the specified reified type `T`, if applicable.
     *
     * This function checks if the current tag is a `ListTag` containing elements of type `T` or satisfies
     * the criteria for an empty `ListTag` with an `EndTag` as the element type. If these conditions are met,
     * it returns the tag as a `ListTag<T>`. Otherwise, it returns null.
     *
     * @return A `ListTag<T>` if the current tag matches the conditions for being a list of type `T`, or null otherwise.
     */
    @OptIn(ExperimentalContracts::class)
    inline fun <reified T : Tag> asListOf(): ListTag<T>? {
        contract {
            returnsNotNull() implies (this@Tag is ListTag<*>)
        }

        if (this !is ListTag<*>)
            return null
        if (this.elementType == TagType.End && this.value.size == 0)
            return this as ListTag<T>
        if (this.elementType.clazz == T::class)
            return this as ListTag<T>
        if (T::class == Tag::class)
            return this as ListTag<T>
        return null
    }

    /**
     * Converts the current tag to a byte array representation, including the provided name.
     *
     * @param name The name associated with the tag to include in the byte array.
     * @return A `ByteArray` containing the serialized representation of the tag with the given name.
     */
    fun toByteArray(name: String): ByteArray {
        val bos = ByteArrayOutputStream()
        val nbtos = NBTOutputStream(bos)
        nbtos.writeNamedTag(name, this)
        nbtos.close()
        return bos.toByteArray()
    }

    /**
     * Converts the current tag to its raw byte array representation.
     *
     * @return A `ByteArray` containing the serialized raw representation of the tag.
     */
    fun toRawByteArray(): ByteArray {
        val bos = ByteArrayOutputStream()
        val nbtos = NBTOutputStream(bos)
        nbtos.writeRawTag(this)
        nbtos.close()
        return bos.toByteArray()
    }

    /**
     * Converts the current tag to its SNBT (Stringified Named Binary Tag) representation.
     *
     * @return A string containing the SNBT representation of the tag.
     */
    fun toSNBT(): String {
        val sb = StringBuilder()
        toSNBT(sb)
        return sb.toString()
    }

    /**
     * Appends the SNBT (String Named Binary Tag) representation of the current tag to the provided `StringBuilder`.
     *
     * @param sb A `StringBuilder` instance to which the serialized SNBT string will be appended.
     */
    fun toSNBT(sb: StringBuilder) {
        when (this) {
            is CompoundTag -> {
                sb.append("{")
                val it = value.iterator()
                var first = true
                while (it.hasNext()) {
                    if (!first) sb.append(", ")
                    first = false

                    val (key, value) = it.next()
                    sb.append(key).append(": ")
                    value.toSNBT(sb)
                }
                sb.append("}")
            }

            is ListTag<*> -> {
                sb.append("[")
                val it = value.iterator()
                var first = true
                while (it.hasNext()) {
                    if (!first) sb.append(", ")
                    first = false
                    it.next().toSNBT(sb)
                }
                sb.append("]")
            }

            is ByteArrayTag -> {
                sb.append("[B;")
                for (i in value.indices) {
                    if (i != 0) sb.append(", ")
                    sb.append(value[i])
                }
                sb.append("]")
            }

            is IntArrayTag -> {
                sb.append("[I;")
                for (i in value.indices) {
                    if (i != 0) sb.append(", ")
                    sb.append(value[i])
                }
                sb.append("]")
            }

            is StringTag -> {
                sb.append('"')
                for (c in value) {
                    when (c) {
                        '\\' -> sb.append("\\\\")
                        '"' -> sb.append("\\\"")
                        else -> sb.append(c)
                    }
                }
                sb.append('"')
            }

            is ByteTag -> sb.append(value).append("b")
            is ShortTag -> sb.append(value).append("s")
            is IntTag -> sb.append(value)
            is LongTag -> sb.append(value).append("l")
            is FloatTag -> sb.append(value).append("f")
            is DoubleTag -> sb.append(value).append("d")
            EndTag -> sb.append("END")
        }
    }

    companion object {
        /**
         * Converts a given byte array to a `Tag` object by deserializing the data.
         *
         * @param data The byte array containing the serialized NBT data.
         * @return The `Tag` object deserialized from the input byte array.
         */
        fun fromByteArray(data: ByteArray): Tag {
            val bis = ByteArrayInputStream(data)
            val nbtis = NBTInputStream(bis)
            val tag = nbtis.readNamedTag().tag
            nbtis.close()
            return tag
        }

        /**
         * Converts a raw byte array into a `Tag` object by deserializing the data.
         *
         * @param data The byte array containing the raw serialized data to be converted into a `Tag`.
         * @return A `Tag` object representing the deserialized data.
         */
        fun fromRawByteArray(data: ByteArray): Tag {
            val bis = ByteArrayInputStream(data)
            val nbtis = NBTInputStream(bis)
            val tag = nbtis.readRawTag()
            nbtis.close()
            return tag
        }
    }
}


/**
 * Represents a named NBT (Named Binary Tag) entry.
 *
 * A `NamedTag` is composed of a name and an associated `Tag` object.
 * This is used for storing and organizing hierarchical data within NBT structures,
 * where each data entry is identified by a unique name.
 *
 * @property name The name assigned to this NBT tag, used as an identifier.
 * @property tag The associated `Tag` object, representing the data value of this entry.
 */
data class NamedTag(val name: String, val tag: Tag)

/**
 * Represents the "end" tag in the Named Binary Tag (NBT) format.
 *
 * The EndTag signifies the termination of a compound or list tag in the NBT structure.
 * It is primarily used as a marker and does not contain any associated value, serving
 * as a delimiter for the hierarchical structure of NBT data.
 */
object EndTag : Tag() {
    override val type: TagType<EndTag>
        get() = TagType.End
    override fun toString(): String = "TAG_End"
}

/**
 * A data class representing a `Byte` type NBT.
 *
 * @property value The byte value stored in this tag.
 */
data class ByteTag(var value: Byte) : Tag() {
    override val type: TagType<out Tag>
        get() = TagType.Byte
    override fun toString(): String = "TAG_Byte($value)"
}

/**
 * Represents a tag storing a `Short` type NBT.
 *
 * @property value The `Short` value stored in this tag.
 */
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
        for (b in value) {
            hex.append(String.format("%02X", b)).append(' ')
        }
        return "TAG_Byte_Array($hex)"
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as ByteArrayTag
        if (!value.contentEquals(other.value)) return false
        return true
    }

    override fun hashCode(): Int = value.contentHashCode()
}

/**
 * The `TAG_Int_Array` tag.
 */
data class IntArrayTag(var value: IntArray) : Tag() {
    override val type: TagType<out Tag>
        get() = TagType.IntArray

    override fun toString(): String {
        val hex = StringBuilder()
        for (b in value) {
            val hexDigits = Integer.toHexString(b).uppercase(Locale.ENGLISH)
            if (hexDigits.length == 1) {
                hex.append("0")
            }
            hex.append(hexDigits).append(" ")
        }
        return "TAG_Int_Array($hex)"
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as IntArrayTag
        if (!value.contentEquals(other.value)) return false
        return true
    }

    override fun hashCode(): Int = value.contentHashCode()
}


fun byteTagOf(value: Byte): ByteTag = ByteTag(value)
fun shortTagOf(value: Short): ShortTag =
    ShortTag(value)
fun intTagOf(value: Int): IntTag = IntTag(value)
fun longTagOf(value: Long): LongTag = LongTag(value)
fun floatTagOf(value: Float): FloatTag =
    FloatTag(value)
fun doubleTagOf(value: Double): DoubleTag =
    DoubleTag(value)

fun stringTagOf(value: String): StringTag =
    StringTag(value)

fun byteArrayTagOf(value: ByteArray): ByteArrayTag =
    ByteArrayTag(value)
fun intArrayTagOf(value: IntArray): IntArrayTag =
    IntArrayTag(value)

/**
 * Represents a tag that holds a list of other tags of the same type.
 *
 * This class is designed to encapsulate a list of `Tag` objects and validate that all elements
 * in the list conform to the specified type. It throws warnings for invalid or overly generic
 * tag definitions during initialization.
 *
 * @param T the type of tags contained within the list, which must extend the `Tag` class
 * @property value a mutable list of tags conforming to the specified type
 * @throws IllegalStateException when the list is heterogeneous or does not conform to the specified type
 */
class ListTag<T : Tag>(val elementType: TagType<T>, val value: MutableList<T>) : Tag() {
    init {
        if (elementType == TagType.End) {
            require(value.isEmpty()) { "EndTag lists cannot contain elements" }
        }
    }

    override val type: TagType<out Tag>
        get() = TagType.List

    override fun toString(): String {
        val bldr = StringBuilder()
        bldr.append("TAG_List").append(": ").append(value.size).append(" entries of type ")
                .append(elementType.name).append("\r\n{\r\n")
        for (t in value) {
            bldr.append("   ").append(t.toString().replace("\r\n".toRegex(), "\r\n   ")).append("\r\n")
        }
        bldr.append("}")
        return bldr.toString()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ListTag<*>

        if (value != other.value) return false
        if (elementType != other.elementType) return false

        return true
    }

    override fun hashCode(): Int {
        var result = value.hashCode()
        result = 31 * result + elementType.hashCode()
        return result
    }

    companion object {
//        fun make(rawType: KClass<out Tag>, values: MutableList<Tag>): ListTag<Tag> {
//            var broken = false
//
//            fun reportNonFatalError(msg: String) {
//                System.err.println(msg)
//                Throwable().printStackTrace(System.err)
//            }
//
//            if (rawType == Tag::class) {
//                reportNonFatalError("Overly generic list tag")
//                broken = true
//            }
//
//            if (!values.all { rawType.isAssignableFrom(it.type) }) {
//                reportNonFatalError("invalid list tag")
//                broken = true
//            }
//
//            if (broken) {
//                val classes = TagType.all.map { TagType.getClassFromTagType(it) }
//                val bestType = classes.find { cls ->
//                    value.all { cls.java.isAssignableFrom(it.javaClass) }
//                }
//
//                if (bestType == null) {
//                    reportNonFatalError("Heterogeneous list tag")
//                    elementType = rawType
//                } else {
//                    System.err.println("bestType = $bestType")
//                    val classesStr = value.map{ "$it:${it.javaClass.simpleName}"}
//                    System.err.println("classes = $classesStr")
//                    elementType = bestType as Class<T>
//                }
//            } else {
//                elementType = rawType
//            }
//        }
    }
}

/**
 * Creates a `ListTag` containing the specified `Tag` elements.
 *
 * @param values The `Tag` elements to be included in the created `ListTag`.
 * @return A `ListTag` instance containing the provided `Tag` elements.
 */
inline fun <reified T : Tag> listTagOf(vararg values: T): ListTag<T> =
    ListTag(TagType.fromClass(T::class), values.toMutableList())

/**
 * Creates a `ListTag` of the specified type `T` from the provided collection of tags.
 *
 * @param values The `Tag` elements to be included in the created `ListTag`.
 * @return A `ListTag` instance containing the provided `Tag` elements.
 */
inline fun <reified T : Tag> listTagOf(values: Collection<T>): ListTag<T> =
    ListTag(TagType.fromClass(T::class), values.toMutableList())

/**
 * Represents a compound tag that is a collection of key-value pairs where the key is a string,
 * and the value is a tag. Provides various utility methods to interact with and retrieve
 * values from the compound tag.
 *
 * @constructor Creates a CompoundTag with the specified mutable map of values.
 * @property value A mutable map storing the key-value pairs, where the key is a string
 * and the value is a tag.
 */
data class CompoundTag(var value: MutableMap<String, Tag>) : Tag() {
    override val type: TagType<out Tag>
        get() = TagType.Compound

    /**
     * Retrieves the tag associated with the specified key from the compound tag.
     *
     * @param key The key for which the corresponding tag is to be retrieved.
     * @return The `Tag` associated with the given key, or `null` if the key does not exist.
     */
    operator fun get(key: String): Tag? = value[key]

    /**
     * Associates the specified key with the provided `Tag` in the compound tag.
     *
     * @param key The key to associate with the `Tag`.
     * @param tag The `Tag` to be stored in the compound tag.
     */
    operator fun set(key: String, tag: Tag) {
        value[key] = tag
    }

    /**
     * Checks if the compound tag contains a tag associated with the specified key.
     *
     * @param key The key to check for the presence of a corresponding tag.
     * @return `true` if a tag is associated with the given key in the compound tag, `false` otherwise.
     */
    operator fun contains(key: String): Boolean =
        value.containsKey(key)

    fun containsKey(key: String): Boolean = value.containsKey(key)

    /**
     * Retrieves the value associated with the specified key as a `ByteArray`.
     *
     * @param key The key for which the corresponding `ByteArray` value is to be retrieved.
     * @return The `ByteArray` associated with the given key, or `null` if no `ByteArray` is found for the key.
     */
    fun getByteArray(key: String): ByteArray? = (value[key] as? ByteArrayTag)?.value

    /**
     * Retrieves the value associated with the specified key as an `IntArray`.
     *
     * @param key The key for which the corresponding `IntArray` value is to be retrieved.
     * @return The `IntArray` associated with the given key, or `null` if no `IntArray` is found for the key.
     */
    fun getIntArray(key: String): IntArray? = (value[key] as? IntArrayTag)?.value

    fun getByte(key: String): Byte? = (value[key] as? ByteTag)?.value
    fun getShort(key: String): Short? = (value[key] as? ShortTag)?.value
    fun getLong(key: String): Long? = (value[key] as? LongTag)?.value
    fun getFloat(key: String): Float? = (value[key] as? FloatTag)?.value
    fun getDouble(key: String): Double? = (value[key] as? DoubleTag)?.value
    fun getString(key: String): String? = (value[key] as? StringTag)?.value

    fun getMap(key: String): Map<String, Tag>? = (value[key] as? CompoundTag)?.value

    /**
     * Retrieves a list of tags of the specified type associated with the given key from the compound tag.
     *
     * @param key The key for which the corresponding list of tags is to be retrieved.
     * @param clazz The class type of the tags expected in the list.
     * @return The list of tags of the specified type associated with the given key, or `null` if no list of the matching type exists for the key.
     */
    fun <T : Tag> getList(key: String, clazz: Class<T>): List<T>? {
        val list = value[key] as? ListTag<*>
        @Suppress("UNCHECKED_CAST")
        return if (list?.elementType == clazz) (list.value as List<T>)
        else null
    }

    override fun toString(): String {
        val bldr = StringBuilder()
        bldr.append("TAG_Compound").append(": ").append(value.size).append(" entries\r\n{\r\n")
        for ((key, value1) in value) {
            bldr.append("   ").append(key).append("  :  ")
                    .append(value1.toString().replace("\r\n".toRegex(), "\r\n   ")).append("\r\n")
        }
        bldr.append("}")
        return bldr.toString()
    }

    companion object {
        fun empty(): CompoundTag =
            CompoundTag(mutableMapOf())
    }
}

/**
 * Creates a `CompoundTag` from a variable number of key-value pairs representing tags.
 * This function initializes and populates a `CompoundTag` with the given key-value pairs,
 * where each key is a string and each value is a `Tag`.
 *
 * @param values The key-value pairs to be added to the `CompoundTag`.
 *               Each pair consists of a string key and a `Tag` value.
 * @return A `CompoundTag` containing the provided key-value pairs.
 */
fun compoundTagOf(vararg values: Pair<String, Tag>): CompoundTag {
    val result = CompoundTag(mutableMapOf())
    for (index in 0..<values.size) {
        val entry = values[index]
        result.value[entry.first] = entry.second
    }
    return result
}

/**
 * Creates a `CompoundTag` by combining the given collection of key-value pairs.
 *
 * @param values A collection of pairs where the first element is a `String`
 *               representing the key, and the second element is a `Tag`
 *               representing the value to be included in the `CompoundTag`.
 *
 * @return A `CompoundTag` containing the provided key-value pairs.
 */
fun compoundTagOf(values: Collection<Pair<String, Tag>>): CompoundTag {
    val result = CompoundTag(mutableMapOf())
    for (entry in values) {
        result.value[entry.first] = entry.second
    }
    return result
}

