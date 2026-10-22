package moe.yui.morph.config

import moe.yui.morph.disguise.DisguiseId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MorphConfigTest {
    private fun config(vararg banned: String) = MorphConfig("zh_cn", true, banned.toSet(), emptySet())

    @Test
    fun `按完整 ID 封禁`() {
        val c = config("player:Notch")
        assertTrue(c.isBanned(DisguiseId.Player("Notch")))
        assertFalse(c.isBanned(DisguiseId.Player("jeb_")))
    }

    @Test
    fun `血量模式解析`() {
        assertEquals(HealthMode.NONE, HealthMode.parse(null))
        assertEquals(HealthMode.NONE, HealthMode.parse("bogus"))
        assertEquals(HealthMode.CAPPED, HealthMode.parse("capped"))
        assertEquals(HealthMode.UNLIMITED, HealthMode.parse(" Unlimited "))
        assertEquals(HealthMode.NONE, config().healthMode)
    }

    @Test
    fun `按分类封禁`() {
        val c = config("player:@all")
        assertTrue(c.isBanned(DisguiseId.Player("anyone")))
    }
}
