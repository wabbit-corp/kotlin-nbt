package one.wabbit.nbt

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class NBTScanTests {
    @Test
    fun publicScannerCanWalkNamedCompoundAndSkipUnusedSubtrees() {
        val tag =
            compoundTagOf(
                "DataVersion" to intTagOf(3700),
                "Status" to stringTagOf("minecraft:full"),
                "Sections" to
                    compoundTagOf(
                        "Ignored" to byteArrayTagOf(ByteArray(16) { it.toByte() }),
                    ),
            )

        val visited = ArrayList<String>()
        val bytesRead =
            NBTScan.scanNamed(tag.toByteArray("root")) {
                val (rootType, rootName) = readNamedTagHeader()
                assertEquals(TagType.Compound, rootType)
                assertEquals("root", rootName)

                scanCompoundEntries(depth = 0) { name, tagType ->
                    visited += "$name:${tagType.name}"
                    skipPayload(tagType, depth = 1)
                }

                bytesRead
            }

        assertEquals(
            listOf(
                "DataVersion:TAG_Int",
                "Status:TAG_String",
                "Sections:TAG_Compound",
            ),
            visited,
        )
        assertTrue(bytesRead > 0L)
    }

    @Test
    fun selectorExtractsChunkStyleTopLevelFieldsAndSectionYValues() {
        val chunk =
            compoundTagOf(
                "DataVersion" to intTagOf(3700),
                "Status" to stringTagOf("minecraft:full"),
                "xPos" to intTagOf(4),
                "zPos" to intTagOf(-2),
                "sections" to
                    listTagOf(
                        compoundTagOf(
                            "Y" to byteTagOf((-1).toByte()),
                            "block_states" to
                                compoundTagOf(
                                    "palette" to listTagOf(compoundTagOf("Name" to stringTagOf("minecraft:stone"))),
                                ),
                        ),
                        compoundTagOf(
                            "Y" to byteTagOf(0),
                            "SkyLight" to byteArrayTagOf(ByteArray(4) { 0x0F.toByte() }),
                        ),
                    ),
                "block_entities" to
                    listTagOf(
                        compoundTagOf(
                            "id" to stringTagOf("minecraft:barrel"),
                        ),
                    ),
            )

        val selection =
            nbtSelection {
                intLike("DataVersion")
                string("Status")
                intLike("xPos")
                intLike("zPos")
                listOfCompounds("sections") {
                    intLike("Y")
                }
            }

        val selected = NBTScan.selectNamed(chunk.toByteArray("chunk"), selection)

        assertEquals(3700L, selected.intLike("DataVersion"))
        assertEquals("minecraft:full", selected.string("Status"))
        assertEquals(4L, selected.intLike("xPos"))
        assertEquals(-2L, selected.intLike("zPos"))
        assertEquals(listOf(-1L, 0L), selected.compounds("sections").mapNotNull { it.intLike("Y") })
    }

    @Test
    fun selectorEnforcesStringAndByteBudgetsWhileSkipping() {
        val tag =
            compoundTagOf(
                "blob" to byteArrayTagOf(ByteArray(64) { 7 }),
                "Status" to stringTagOf("minecraft:full"),
            )

        val selection =
            nbtSelection {
                string("Status")
            }

        assertFailsWith<NbtScanQuotaException> {
            NBTScan.selectNamed(
                tag.toByteArray("root"),
                selection = selection,
                options =
                    NbtScanOptions(
                        maxBytesRead = 48,
                        maxByteArrayBytes = 1024,
                    ),
            )
        }

        assertFailsWith<NbtScanQuotaException> {
            NBTScan.selectNamed(
                tag.toByteArray("root"),
                selection = selection,
                options =
                    NbtScanOptions(
                        maxStringBytes = 4,
                    ),
            )
        }
    }

    @Test
    fun selectorEnforcesDepthBudgetForNestedCompounds() {
        val tag =
            compoundTagOf(
                "Level" to
                    compoundTagOf(
                        "sections" to
                            listTagOf(
                                compoundTagOf(
                                    "Y" to byteTagOf(0),
                                ),
                            ),
                    ),
            )

        val selection =
            nbtSelection {
                compound("Level") {
                    listOfCompounds("sections") {
                        intLike("Y")
                    }
                }
            }

        assertFailsWith<NbtScanQuotaException> {
            NBTScan.selectNamed(
                tag.toByteArray("root"),
                selection = selection,
                options =
                    NbtScanOptions(
                        maxDepth = 1,
                    ),
            )
        }
    }
}
