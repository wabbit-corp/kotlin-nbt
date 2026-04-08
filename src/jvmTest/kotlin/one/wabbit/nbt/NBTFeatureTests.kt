package one.wabbit.nbt

import java.io.ByteArrayInputStream
import java.nio.ByteOrder
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

    @Test
    fun littleEndianInputStreamRemainsSupported() {
        val littleEndianRawInt = byteArrayOf(3, 4, 0, 0, 0)

        val parsed =
            NBTInputStream(ByteArrayInputStream(littleEndianRawInt), ByteOrder.LITTLE_ENDIAN).use {
                it.readRawTag()
            }

        assertEquals(IntTag(4), parsed)
    }
}
