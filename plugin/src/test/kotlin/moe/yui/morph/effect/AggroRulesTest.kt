package moe.yui.morph.effect

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AggroRulesTest {
    private fun targets(source: String, disguise: String, witherFriend: Boolean = false) =
        AggroRules.shouldTarget("minecraft:$source", "minecraft:$disguise", witherFriend)

    @Test
    fun `僵尸攻击村民和铁傀儡，不攻击同类`() {
        for (zombie in listOf("zombie", "husk", "zombie_villager", "drowned")) {
            assertTrue(targets(zombie, "villager"), zombie)
            assertTrue(targets(zombie, "wandering_trader"), zombie)
            assertTrue(targets(zombie, "iron_golem"), zombie)
            assertTrue(targets(zombie, "turtle"), zombie)
            assertFalse(targets(zombie, "zombie"), zombie)
            assertFalse(targets(zombie, "pig"), zombie)
        }
        assertTrue(targets("drowned", "axolotl"))
        assertFalse(targets("zombie", "axolotl"))
    }

    @Test
    fun `雪傀儡不会被主动攻击（和 FeatherMorph 不同）`() {
        for (source in listOf("zombie", "skeleton", "pillager", "wither_skeleton")) {
            assertFalse(targets(source, "snow_golem"), source)
        }
    }

    @Test
    fun `骷髅类`() {
        for (skeleton in listOf("skeleton", "stray", "bogged", "parched", "wither_skeleton")) {
            assertTrue(targets(skeleton, "iron_golem"), skeleton)
            assertFalse(targets(skeleton, "villager"), skeleton)
        }
        assertTrue(targets("wither_skeleton", "piglin"))
        assertTrue(targets("wither_skeleton", "piglin_brute"))
        assertFalse(targets("skeleton", "piglin"))
    }

    @Test
    fun `灾厄村民攻击村民`() {
        for (raider in listOf("pillager", "vindicator", "evoker", "illusioner", "ravager")) {
            assertTrue(targets(raider, "villager"), raider)
            assertTrue(targets(raider, "iron_golem"), raider)
            assertFalse(targets(raider, "pillager"), raider)
        }
        assertFalse(targets("witch", "villager"))
    }

    @Test
    fun `凋灵攻击亡灵以外的生物（和 FeatherMorph 相反）`() {
        assertTrue(targets("wither", "pig"))
        assertTrue(targets("wither", "villager"))
        assertFalse(targets("wither", "zombie", witherFriend = true))
        assertFalse(targets("wither", "armor_stand"))
    }

    @Test
    fun `其他敌对生物只攻击玩家`() {
        for (source in listOf("creeper", "blaze", "phantom", "spider", "enderman", "guardian", "piglin", "hoglin")) {
            assertFalse(targets(source, "pig"), source)
        }
        assertTrue(targets("spider", "iron_golem"))
        assertTrue(targets("enderman", "endermite"))
        assertTrue(targets("guardian", "squid"))
        assertTrue(targets("piglin", "wither_skeleton"))
        assertTrue(targets("zoglin", "pig"))
        assertFalse(targets("zoglin", "creeper"))
    }

    @Test
    fun `表外生物交给原版`() {
        for (source in listOf("warden", "wolf", "bee", "zombified_piglin", "iron_golem", "polar_bear")) {
            assertFalse(AggroRules.handles("minecraft:$source"), source)
            assertTrue(targets(source, "pig"), source)
        }
        assertTrue(AggroRules.handles("minecraft:wither"))
        assertTrue(AggroRules.handles("minecraft:piglin_brute"))
    }
}
