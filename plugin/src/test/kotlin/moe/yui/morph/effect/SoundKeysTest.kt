package moe.yui.morph.effect

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SoundKeysTest {
    @Test
    fun `普通生物按 entity_id_hurt 命名`() {
        assertEquals("entity.pig.hurt", SoundKeys.hurt("pig", emptyMap()))
        assertEquals("entity.zombie.death", SoundKeys.death("zombie", emptyMap()))
    }

    @Test
    fun `特殊命名和史莱姆尺寸`() {
        assertEquals("entity.armor_stand.hit", SoundKeys.hurt("armor_stand", emptyMap()))
        assertEquals("entity.armor_stand.break", SoundKeys.death("armor_stand", emptyMap()))
        // 史莱姆默认尺寸 1，用小史莱姆的音效
        assertEquals("entity.slime.hurt_small", SoundKeys.hurt("slime", emptyMap()))
        assertEquals("entity.magma_cube.death", SoundKeys.death("magma_cube", mapOf("size" to "4")))
    }

    @Test
    fun `幼体音调更高`() {
        assertEquals(1.5f, SoundKeys.pitchMultiplier(mapOf("baby" to "true")))
        assertEquals(1.0f, SoundKeys.pitchMultiplier(emptyMap()))
    }
}
