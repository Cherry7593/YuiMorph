package moe.yui.morph.effect

import moe.yui.morph.config.HealthMode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class EffectMathTest {
    private val traits = javaClass.classLoader.getResourceAsStream("entity-traits.tsv")!!.use(EntityTraits::load)

    private fun scale(type: String, props: Map<String, String> = emptyMap()) =
        EffectMath.scale("minecraft:$type", traits["minecraft:$type"]!!, props, 0.25, 2.0)

    @Test
    fun `特性表与原版数值一致`() {
        assertEquals(Traits(0.9, 0.9, 10.0), traits["minecraft:pig"])
        assertEquals(Traits(0.4, 0.7, 4.0), traits["minecraft:chicken"])
        assertEquals(100.0, traits["minecraft:iron_golem"]!!.maxHealth)
        assertEquals(500.0, traits["minecraft:warden"]!!.maxHealth)
        assertNotNull(traits["minecraft:player"])
    }

    @Test
    fun `按高度对齐缩放`() {
        // 猪 0.9 高 → 玩家缩到一半，可以钻进 1 格高的缝
        assertEquals(0.5, scale("pig"), 1e-9)
        assertEquals(1.5, scale("iron_golem"), 1e-9)
    }

    @Test
    fun `缩放受上下限约束`() {
        assertEquals(2.0, scale("ghast"))
        assertEquals(0.25, scale("silverfish"))
    }

    @Test
    fun `幼体和史莱姆尺寸`() {
        assertEquals(0.25, scale("pig", mapOf("baby" to "true")), 1e-9)
        assertEquals(0.52 * 4 / 1.8, scale("slime", mapOf("size" to "4")), 1e-9)
        // size 只对史莱姆类生效
        assertEquals(0.5, scale("pig", mapOf("size" to "4")), 1e-9)
    }

    @Test
    fun `血量模式`() {
        val warden = traits["minecraft:warden"]!!
        val chicken = traits["minecraft:chicken"]!!
        // none：不修改
        assertNull(EffectMath.maxHealth(warden, HealthMode.NONE, 20.0))
        assertNull(EffectMath.maxHealth(chicken, HealthMode.NONE, 20.0))
        // capped：可以变少，不能超过玩家本来的上限
        assertEquals(4.0, EffectMath.maxHealth(chicken, HealthMode.CAPPED, 20.0))
        assertEquals(20.0, EffectMath.maxHealth(warden, HealthMode.CAPPED, 20.0))
        assertEquals(40.0, EffectMath.maxHealth(warden, HealthMode.CAPPED, 40.0))
        // unlimited：完全跟随生物
        assertEquals(500.0, EffectMath.maxHealth(warden, HealthMode.UNLIMITED, 20.0))
        assertEquals(4.0, EffectMath.maxHealth(chicken, HealthMode.UNLIMITED, 20.0))
    }

    @Test
    fun `血量比例换算`() {
        // 满血 20 → 变成小鸡后满血 4 → 变回来满血 20
        assertEquals(4.0, EffectMath.rescaleHealth(20.0, 20.0, 4.0))
        assertEquals(20.0, EffectMath.rescaleHealth(4.0, 4.0, 20.0))
        // 不会因为换算直接死亡
        assertEquals(0.5, EffectMath.rescaleHealth(0.1, 20.0, 4.0))
        // 已死亡不处理
        assertEquals(0.0, EffectMath.rescaleHealth(0.0, 20.0, 4.0))
    }
}
