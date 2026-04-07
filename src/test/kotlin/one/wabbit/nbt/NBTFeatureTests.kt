package one.wabbit.nbt

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull

class NBTFeatureTests {
    private fun resourceBytes(path: String): ByteArray =
        checkNotNull(javaClass.getResourceAsStream(path)) { "Missing test resource: $path" }.use {
            it.readAllBytes()
        }

    @Test
    fun longArrayNamedRoundTripIsSupported() {
        val tag =
            compoundTagOf(
                "palette" to longArrayTagOf(longArrayOf(1L, -2L, 3L)),
                "label" to stringTagOf("packed"),
            )

        val decoded = Tag.fromByteArray(tag.toByteArray("root"))

        assertEquals(tag, decoded)
    }

    @Test
    fun explicitCompressionRoundTripsForNamedAndRawPayloads() {
        val tag =
            compoundTagOf(
                "name" to stringTagOf("compressed"),
                "values" to intArrayTagOf(intArrayOf(7, 8, 9)),
                "packed" to longArrayTagOf(longArrayOf(5L, 6L)),
            )

        val gzipNamed = tag.toCompressedByteArray("root", Compression.GZIP)
        val zlibNamed = tag.toCompressedByteArray("root", Compression.ZLIB)
        val gzipRaw = tag.toCompressedRawByteArray(Compression.GZIP)

        assertEquals(tag, Tag.fromCompressedByteArray(Compression.GZIP, gzipNamed))
        assertEquals(tag, Tag.fromCompressedByteArray(Compression.ZLIB, zlibNamed))
        assertEquals(tag, Tag.fromCompressedRawByteArray(Compression.GZIP, gzipRaw))
    }

    @Test
    fun autoDetectionHandlesUncompressedGzipAndZlibNamedPayloads() {
        val tag =
            compoundTagOf(
                "name" to stringTagOf("auto"),
                "flag" to byteTagOf(1),
                "payload" to longArrayTagOf(longArrayOf(9L, -2L)),
            )

        assertEquals(tag, Tag.fromByteArrayAuto(tag.toByteArray("root")))
        assertEquals(tag, Tag.fromByteArrayAuto(tag.toCompressedByteArray("root", Compression.GZIP)))
        assertEquals(tag, Tag.fromByteArrayAuto(tag.toCompressedByteArray("root", Compression.ZLIB)))
    }

    @Test
    fun snbtParserRoundTripsEmittedSnbt() {
        val tag =
            compoundTagOf(
                "name" to stringTagOf("birch"),
                "height" to intTagOf(12),
                "message" to stringTagOf("say \"hi\" \\\\ wave\n"),
                "payload" to byteArrayTagOf(byteArrayOf(0, -1)),
                "ints" to intArrayTagOf(intArrayOf(1, 2)),
                "packed" to longArrayTagOf(longArrayOf(9L, -2L)),
            )

        val parsed = Tag.fromSNBT(tag.toSNBT())

        assertEquals(tag, parsed)
    }

    @Test
    fun snbtParserHandlesTypedArraysNumericFormsAndRelaxedStrings() {
        val parsed =
            Tag.fromSNBT(
                """{single:'hi', escaped:"line\n\tindent", hex:0x10, binary:-0b11, decimal:1.5, bytes:[B;1B, -1B], ints:[I;1, 2], longs:[L;3L, -4L]}""",
            )

        val compound = assertIs<CompoundTag>(parsed)

        assertEquals("hi", compound.getString("single"))
        assertEquals("line\n\tindent", compound.getString("escaped"))
        assertEquals(16, assertIs<IntTag>(compound["hex"]).value)
        assertEquals(-3, assertIs<IntTag>(compound["binary"]).value)
        assertEquals(1.5, assertIs<DoubleTag>(compound["decimal"]).value)
        assertEquals(byteArrayTagOf(byteArrayOf(1, -1)), compound["bytes"])
        assertEquals(intArrayTagOf(intArrayOf(1, 2)), compound["ints"])
        assertEquals(longArrayTagOf(longArrayOf(3L, -4L)), compound["longs"])
    }

    @Test
    fun snbtParserAcceptsRelaxedTokensWithNestedBraces() {
        val parsed =
            Tag.fromSNBT(
                """{Command:tellraw @p {text:'Nothing happened, sorry! :)',color:gray}, Time:1}""",
            )

        val compound = assertIs<CompoundTag>(parsed)

        assertEquals(
            "tellraw @p {text:'Nothing happened, sorry! :)',color:gray}",
            compound.getString("Command"),
        )
        assertEquals(1, assertIs<IntTag>(compound["Time"]).value)
    }

    @Test
    fun gzipHelloWorldFixtureAutoDetectsAndParsesExpectedContent() {
        val parsed = Tag.fromByteArrayAuto(resourceBytes("/fixtures/hello_world_gzip.nbt"))
        val compound = assertIs<CompoundTag>(parsed)

        assertEquals("Bananrama", compound.getString("name"))
    }

    @Test
    fun classicSchematicFixtureAutoDetectsAndExposesExpectedMetadata() {
        val parsed = Tag.fromByteArrayAuto(resourceBytes("/fixtures/simple.schematic"))
        val compound = assertIs<CompoundTag>(parsed)

        assertEquals(4, assertIs<ShortTag>(assertNotNull(compound["Width"])).value.toInt())
        assertEquals(4, assertIs<ShortTag>(assertNotNull(compound["Height"])).value.toInt())
        assertEquals(4, assertIs<ShortTag>(assertNotNull(compound["Length"])).value.toInt())
        assertEquals("Alpha", compound.getString("Materials"))
        assertIs<ByteArrayTag>(assertNotNull(compound["Blocks"]))
        assertIs<ByteArrayTag>(assertNotNull(compound["Data"]))
    }
}
