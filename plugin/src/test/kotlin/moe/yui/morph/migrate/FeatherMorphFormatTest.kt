package moe.yui.morph.migrate

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.util.UUID

class FeatherMorphFormatTest {
    private val uuid = "11973f51-ff8f-4e40-87dc-14ef63dee3a3"

    @Test
    fun `解析玩家数据`() {
        val json = """{"playerName":"Zvbj_","unlockedDisguiseIdentifiers":["minecraft:pig","player:Notch","minecraft:pig"],"showDisguiseToSelf":false}"""
        val p = FeatherMorphFormat.parsePlayer("$uuid.json", json)!!
        assertEquals(UUID.fromString(uuid), p.uuid)
        assertEquals("Zvbj_", p.name)
        assertEquals(listOf("minecraft:pig", "player:Notch"), p.unlocked)
    }

    @Test
    fun `丢弃不合法的 ID`() {
        val json = """{"unlockedDisguiseIdentifiers":["minecraft:pig","ld:custom","player:这不是名字","minecraft:Bad Id",""]}"""
        assertEquals(listOf("minecraft:pig"), FeatherMorphFormat.parsePlayer("$uuid.json", json)!!.unlocked)
    }

    @Test
    fun `跳过非玩家数据文件`() {
        // 生产环境 playerdata 目录里有一个 package_version.txt，内容是数字 1
        assertNull(FeatherMorphFormat.parsePlayer("package_version.txt", "1"))
        assertNull(FeatherMorphFormat.parsePlayer("not-a-uuid.json", "{}"))
        assertNull(FeatherMorphFormat.parsePlayer("$uuid.json", "1"))
        assertNull(FeatherMorphFormat.parsePlayer("$uuid.json", "{broken"))
    }

    @Test
    fun `解析下线伪装`() {
        val (id, props) = FeatherMorphFormat.parseSaved("""{"disguiseIdentifier":"minecraft:cat","properties":{"cat/variant":"minecraft:red"}}""")!!
        assertEquals("minecraft:cat", id)
        assertEquals(mapOf("cat/variant" to "minecraft:red"), props)
        assertNull(FeatherMorphFormat.parseSaved("""{"properties":{}}"""))
    }

    @Test
    fun `属性映射覆盖生产环境出现过的键`() {
        assertEquals(mapOf("variant" to "ragdoll"), FeatherMorphFormat.mapProperties(mapOf("cat/variant" to "minecraft:ragdoll")))
        assertEquals(
            mapOf("variant" to "dark_brown", "markings" to "white_field"),
            FeatherMorphFormat.mapProperties(mapOf("horse/color" to "dark_brown", "horse/style" to "whitefield")),
        )
        assertEquals(mapOf("size" to "4"), FeatherMorphFormat.mapProperties(mapOf("slime_magma/size" to "4")))
        assertEquals(mapOf("powered" to "false"), FeatherMorphFormat.mapProperties(mapOf("creeper/charged" to "false")))
        assertEquals(mapOf("variant" to "red"), FeatherMorphFormat.mapProperties(mapOf("mooshroom/variant" to "red")))
        // 不迁移的属性
        assertEquals(emptyMap<String, String>(), FeatherMorphFormat.mapProperties(mapOf("player/skin" to "eyJ0ZXh0dXJl...", "armor_stand/show_arms" to "true")))
    }
}
