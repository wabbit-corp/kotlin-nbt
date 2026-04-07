package one.wabbit.nbt

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.DeflaterOutputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import java.util.zip.InflaterInputStream

enum class Compression {
    NONE,
    GZIP,
    ZLIB,
}

object NBTSerialization {
    @JvmStatic
    fun toByteArray(name: String, tag: Tag): ByteArray {
        val bos = ByteArrayOutputStream()
        NBTOutputStream(bos).use { it.writeNamedTag(name, tag) }
        return bos.toByteArray()
    }

    @JvmStatic
    fun toCompressedByteArray(compression: Compression, name: String, tag: Tag): ByteArray =
        withOutputStream(compression) { output ->
            NBTOutputStream(output).use { it.writeNamedTag(name, tag) }
        }

    @JvmStatic
    fun toRawByteArray(tag: Tag): ByteArray {
        val bos = ByteArrayOutputStream()
        NBTOutputStream(bos).use { it.writeRawTag(tag) }
        return bos.toByteArray()
    }

    @JvmStatic
    fun toCompressedRawByteArray(compression: Compression, tag: Tag): ByteArray =
        withOutputStream(compression) { output ->
            NBTOutputStream(output).use { it.writeRawTag(tag) }
        }

    @JvmStatic
    fun fromByteArray(data: ByteArray): Tag = readNamed(ByteArrayInputStream(data))

    @JvmStatic
    fun fromCompressedByteArray(compression: Compression, data: ByteArray): Tag =
        openInputStream(compression, data).use(::readNamed)

    @JvmStatic
    fun fromRawByteArray(data: ByteArray): Tag = readRaw(ByteArrayInputStream(data))

    @JvmStatic
    fun fromCompressedRawByteArray(compression: Compression, data: ByteArray): Tag =
        openInputStream(compression, data).use(::readRaw)

    @JvmStatic
    fun fromByteArrayAuto(data: ByteArray): Tag {
        val candidates =
            listOf(detectCompression(data), Compression.NONE, Compression.GZIP, Compression.ZLIB)
                .distinct()

        var lastError: Exception? = null
        for (compression in candidates) {
            try {
                return if (compression == Compression.NONE) {
                    fromByteArray(data)
                } else {
                    fromCompressedByteArray(compression, data)
                }
            } catch (ex: Exception) {
                lastError = ex
            }
        }

        throw IllegalArgumentException(
            "Unable to auto-detect a supported named NBT encoding.",
            lastError,
        )
    }

    @JvmStatic
    fun detectCompression(data: ByteArray): Compression =
        when {
            data.size >= 2 && data[0] == 0x1F.toByte() && data[1] == 0x8B.toByte() -> Compression.GZIP
            data.size >= 2 &&
                data[0] == 0x78.toByte() &&
                data[1] in
                    byteArrayOf(
                        0x01.toByte(),
                        0x5E.toByte(),
                        0x9C.toByte(),
                        0xDA.toByte(),
                    ) -> Compression.ZLIB
            else -> Compression.NONE
        }

    private fun withOutputStream(compression: Compression, action: (OutputStream) -> Unit): ByteArray {
        val bos = ByteArrayOutputStream()
        when (compression) {
            Compression.NONE -> action(bos)
            Compression.GZIP -> GZIPOutputStream(bos).use(action)
            Compression.ZLIB -> DeflaterOutputStream(bos).use(action)
        }
        return bos.toByteArray()
    }

    private fun openInputStream(compression: Compression, data: ByteArray): InputStream {
        val input = ByteArrayInputStream(data)
        return when (compression) {
            Compression.NONE -> input
            Compression.GZIP -> GZIPInputStream(input)
            Compression.ZLIB -> InflaterInputStream(input)
        }
    }

    private fun readNamed(input: InputStream): Tag =
        NBTInputStream(input).use { reader ->
            val tag = reader.readNamedTag().tag
            ensureFullyConsumed(input)
            tag
        }

    private fun readRaw(input: InputStream): Tag =
        NBTInputStream(input).use { reader ->
            val tag = reader.readRawTag()
            ensureFullyConsumed(input)
            tag
        }

    private fun ensureFullyConsumed(input: InputStream) {
        if (input.read() != -1) {
            throw IllegalArgumentException("Trailing bytes found after decoding NBT payload.")
        }
    }
}
