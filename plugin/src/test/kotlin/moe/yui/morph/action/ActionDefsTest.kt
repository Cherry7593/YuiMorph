package moe.yui.morph.action

import moe.yui.morph.disguise.property.DisguiseProperties
import moe.yui.morph.disguise.property.EntityLayouts
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ActionDefsTest {
    private val layouts = javaClass.classLoader.getResourceAsStream("entity-layouts.tsv")!!.use(EntityLayouts::load)

    @Test
    fun `动作修改的字段在对应实体上都存在`() {
        for (type in ActionDefs.types) {
            for (def in ActionDefs.forType(type)) {
                for (target in def.targets) {
                    assertNotNull(layouts.indexOf(type, target.field), "$type 的动作 ${def.name} 引用了不存在的字段 ${target.field}")
                }
            }
        }
    }

    @Test
    fun `动作字段不与属性字段冲突`() {
        val props = DisguiseProperties(layouts) { listOf("x") }
        for (type in ActionDefs.types) {
            val propIndices = props.metadata(type, props.keys(type).associate { k -> k.name to (props.values(k)?.first() ?: "名字") })
                .map { it.index }.toSet()
            for (def in ActionDefs.forType(type)) {
                for (target in def.targets) {
                    val index = layouts.indexOf(type, target.field)!!
                    assertTrue(index !in propIndices, "$type 的动作 ${def.name} 与属性共用索引 $index")
                }
            }
        }
    }

    @Test
    fun `有持续动作时附带 stop`() {
        assertEquals(listOf("sit", "sleep", "stop"), ActionDefs.names("minecraft:fox"))
        // 只有一次性动作时不需要 stop
        assertEquals(listOf("sniff", "dig"), ActionDefs.names("minecraft:sniffer"))
        assertEquals(emptyList<String>(), ActionDefs.names("minecraft:pig"))
    }

    @Test
    fun `坐下类动作移动时取消，爬行不取消`() {
        assertTrue(ActionDefs.find("minecraft:cat", "sit")!!.cancelOnMove)
        assertTrue(!ActionDefs.find("minecraft:player", "crawl")!!.cancelOnMove)
    }
}
