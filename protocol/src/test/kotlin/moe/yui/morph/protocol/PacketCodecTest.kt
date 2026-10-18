package moe.yui.morph.protocol

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.Test

class PacketCodecTest {
    @Test
    fun `C2S 包往返编解码`() {
        listOf(
            C2SHello(Protocol.VERSION, "0.1.0"),
            C2SMorph("minecraft:pig"),
            C2SMorph("minecraft:sheep", mapOf("color" to "pink")),
            C2SPreview(7, "minecraft:cat", null),
            C2SPreview(8, "player:Notch", mapOf("skin" to "jeb_")),
            C2SUnmorph,
            C2SPlayAction("sit"),
        ).forEach { assertEquals(it, PacketCodec.decodeC2S(PacketCodec.encode(it))) }
    }

    @Test
    fun `S2C 包往返编解码`() {
        listOf(
            S2CHello(Protocol.VERSION, true, "0.1.0"),
            S2CUnlockedSync(true, listOf("minecraft:pig", "player:中文名"), emptyList()),
            S2CSelfDisguise("minecraft:sheep", null, mapOf("color" to "pink"), listOf("sit", "stop"), -1001),
            S2CSelfDisguise("player:Notch", "Notch", emptyMap(), listOf("lay"), -1002),
            S2CSelfDisguise.NONE,
            S2CPreview(
                7, "minecraft:sheep", -2, mapOf("color" to "pink"),
                listOf(PropertySpec("color", listOf("white", "pink"), 32), PropertySpec("name", null, 32)),
                listOf("eat"),
            ),
        ).forEach { assertEquals(it, PacketCodec.decodeS2C(PacketCodec.encode(it))) }
    }

    @Test
    fun `拒绝非法数据`() {
        assertThrows<ProtocolException> { PacketCodec.decodeC2S(byteArrayOf()) }
        assertThrows<ProtocolException> { PacketCodec.decodeC2S(byteArrayOf(99)) }
        // 字符串长度声明远大于实际数据
        assertThrows<ProtocolException> { PacketCodec.decodeC2S(byteArrayOf(1, 0x7F)) }
        // 多余尾部数据
        assertThrows<ProtocolException> { PacketCodec.decodeC2S(byteArrayOf(2, 0)) }
    }

    @Test
    fun `VarInt 边界值`() {
        listOf(0, 1, 127, 128, 255, 25565, Int.MAX_VALUE, -1, Int.MIN_VALUE).forEach {
            assertEquals(it, PacketReader(PacketWriter().varInt(it).toByteArray()).varInt())
        }
    }
}
