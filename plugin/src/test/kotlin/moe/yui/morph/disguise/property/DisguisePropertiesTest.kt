package moe.yui.morph.disguise.property

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DisguisePropertiesTest {
    private val layouts = javaClass.classLoader.getResourceAsStream("entity-layouts.tsv")!!.use(EntityLayouts::load)
    /** 测试用注册表：不依赖 PacketEvents 运行时。 */
    private val props = DisguiseProperties(layouts) { source ->
        when (source) {
            RegistrySource.CAT_VARIANT -> listOf("black", "tabby")
            RegistrySource.VILLAGER_TYPE -> listOf("plains", "desert")
            RegistrySource.VILLAGER_PROFESSION -> listOf("none", "farmer")
            else -> listOf("temperate", "warm", "cold")
        }
    }

    private fun keys(type: String) = props.keys("minecraft:$type").map { it.name }.toSet()

    @Test
    fun `布局索引与 26_3 字节码一致`() {
        assertEquals(18, layouts.indexOf("minecraft:sheep", "Sheep.DATA_WOOL_ID"))
        // 26.3 中史莱姆继承 AgeableMob，size 不再是 16
        assertEquals(18, layouts.indexOf("minecraft:slime", "AbstractCubeMob.ID_SIZE"))
        assertEquals(17, layouts.indexOf("minecraft:piglin", "Piglin.DATA_BABY_ID"))
        assertNull(layouts.indexOf("minecraft:piglin_brute", "Piglin.DATA_BABY_ID"))
        assertEquals(21, layouts.fieldCount("minecraft:player"))
    }

    @Test
    fun `按实体解析可用属性`() {
        assertEquals(setOf("name", "baby", "color", "sheared"), keys("sheep"))
        assertTrue("baby" in keys("piglin"))
        assertFalse("baby" in keys("piglin_brute"))
        assertEquals(setOf("name", "baby", "variant"), keys("cat"))
        assertEquals(setOf("name", "baby", "variant", "markings"), keys("horse"))
        // 玩家伪装没有自定义名字，但可以换皮肤
        assertEquals(setOf("skin"), keys("player"))
    }

    @Test
    fun `编码羊的颜色和剪毛状态`() {
        val data = props.metadata("minecraft:sheep", mapOf("color" to "pink", "sheared" to "true")).single()
        assertEquals(18, data.index)
        assertEquals((6 or 0x10).toByte(), data.value)
    }

    @Test
    fun `玩家伪装默认显示全部皮肤图层`() {
        val data = props.metadata("minecraft:player", emptyMap()).single()
        assertEquals(16, data.index)
        assertEquals(0x7F.toByte(), data.value)
    }

    @Test
    fun `注册表变种可以解析`() {
        val variant = props.key("minecraft:cat", "variant")!!
        assertEquals(listOf("black", "tabby"), props.values(variant))
        val meta = props.metadata("minecraft:cat", mapOf("variant" to "black")).single()
        assertEquals(DataKind.CAT_VARIANT, meta.kind)
        assertEquals("black", meta.value)
        assertEquals(20, meta.index)
        assertNull(props.normalize(variant, "not_a_cat"))
    }

    @Test
    fun `丢弃不适用或不合法的属性`() {
        val kept = props.retain("minecraft:pig", mapOf("color" to "red", "baby" to "true", "variant" to "not_a_variant"))
        assertEquals(mapOf("baby" to "true"), kept)
    }

    @Test
    fun `所有实体设置任何属性都不会发出越界索引`() {
        val types = javaClass.classLoader.getResourceAsStream("entity-layouts.tsv")!!.bufferedReader().useLines { lines ->
            lines.filter { it.startsWith("minecraft:") }.map { it.substringBefore('\t') }.toList()
        }
        for (type in types) {
            val count = layouts.fieldCount(type)!!
            for (key in props.keys(type)) {
                val samples = props.values(key) ?: listOf("测试名字")
                for (value in samples) {
                    props.metadata(type, mapOf(key.name to value)).forEach {
                        assertTrue(it.index < count, "$type.${key.name}=$value 发出了越界索引 ${it.index}（共 $count 个字段）")
                    }
                }
            }
        }
    }
}
