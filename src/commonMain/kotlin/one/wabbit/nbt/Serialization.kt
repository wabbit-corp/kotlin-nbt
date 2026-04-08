package one.wabbit.nbt

import kotlinx.io.Buffer
import kotlinx.io.readByteArray
import kotlinx.io.write
import one.wabbit.compression.Compression as CompressionCodec
import one.wabbit.compression.CompressionFormat as CompressionCodecFormat

enum class Compression {
    NONE,
    GZIP,
    ZLIB,
}

object NBTSerialization {
    fun toByteArray(name: String, tag: Tag): ByteArray {
        val buffer = Buffer()
        NbtWriter(buffer).writeNamedTag(name, tag)
        return buffer.readByteArray()
    }

    fun toCompressedByteArray(compression: Compression, name: String, tag: Tag): ByteArray =
        compress(compression, toByteArray(name, tag))

    fun toRawByteArray(tag: Tag): ByteArray {
        val buffer = Buffer()
        NbtWriter(buffer).writeRawTag(tag)
        return buffer.readByteArray()
    }

    fun toCompressedRawByteArray(compression: Compression, tag: Tag): ByteArray =
        compress(compression, toRawByteArray(tag))

    fun fromByteArray(data: ByteArray): Tag {
        val buffer = Buffer()
        buffer.write(data)
        val tag = NbtReader(buffer).readNamedTag().tag
        ensureFullyConsumed(buffer)
        return tag
    }

    fun fromCompressedByteArray(compression: Compression, data: ByteArray): Tag =
        fromByteArray(decompress(compression, data))

    fun fromRawByteArray(data: ByteArray): Tag {
        val buffer = Buffer()
        buffer.write(data)
        val tag = NbtReader(buffer).readRawTag()
        ensureFullyConsumed(buffer)
        return tag
    }

    fun fromCompressedRawByteArray(compression: Compression, data: ByteArray): Tag =
        fromRawByteArray(decompress(compression, data))

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

    fun detectCompression(data: ByteArray): Compression =
        when {
            data.size >= 2 && data[0] == 0x1F.toByte() && data[1] == 0x8B.toByte() -> Compression.GZIP
            data.size >= 2 &&
                data[0] == 0x78.toByte() &&
                data[1] in byteArrayOf(0x01.toByte(), 0x5E.toByte(), 0x9C.toByte(), 0xDA.toByte()) ->
                Compression.ZLIB
            else -> Compression.NONE
        }

    private fun compress(compression: Compression, data: ByteArray): ByteArray =
        when (compression) {
            Compression.NONE -> data
            else -> CompressionCodec.compress(compression.toCodecFormat(), data)
        }

    private fun decompress(compression: Compression, data: ByteArray): ByteArray =
        when (compression) {
            Compression.NONE -> data
            else -> CompressionCodec.decompress(compression.toCodecFormat(), data)
        }

    private fun ensureFullyConsumed(buffer: Buffer) {
        require(buffer.exhausted()) { "Trailing bytes found after decoding NBT payload." }
    }

    private fun Compression.toCodecFormat(): CompressionCodecFormat =
        when (this) {
            Compression.NONE -> error("Compression.NONE does not map to a codec format")
            Compression.GZIP -> CompressionCodecFormat.GZIP
            Compression.ZLIB -> CompressionCodecFormat.ZLIB
        }
}
