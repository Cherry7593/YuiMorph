package moe.yui.morph.client.net

import moe.yui.morph.protocol.Protocol
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.Identifier

/**
 * 插件消息频道上的原始字节。编解码交给 `protocol` 模块，这里只负责搬运。
 * 服务端（Bukkit 插件消息）收发的也是同样的原始字节。
 */
class MorphPayload(val data: ByteArray) : CustomPacketPayload {
    override fun type(): CustomPacketPayload.Type<MorphPayload> = TYPE

    companion object {
        val TYPE: CustomPacketPayload.Type<MorphPayload> = CustomPacketPayload.Type(Identifier.parse(Protocol.CHANNEL))

        val CODEC: StreamCodec<RegistryFriendlyByteBuf, MorphPayload> = StreamCodec.of(
            { buf, payload -> buf.writeBytes(payload.data) },
            { buf ->
                val size = buf.readableBytes()
                require(size <= Protocol.MAX_PACKET_BYTES) { "YuiMorph 数据包过大：$size 字节" }
                MorphPayload(ByteArray(size).also(buf::readBytes))
            },
        )
    }
}
