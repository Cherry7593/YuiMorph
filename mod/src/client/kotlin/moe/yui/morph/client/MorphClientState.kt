package moe.yui.morph.client

import moe.yui.morph.client.net.MorphPayload
import moe.yui.morph.protocol.C2SPacket
import moe.yui.morph.protocol.PacketCodec
import moe.yui.morph.protocol.S2CHello
import moe.yui.morph.protocol.S2CPacket
import moe.yui.morph.protocol.S2CSelfDisguise
import moe.yui.morph.protocol.S2CUnlockedSync
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.minecraft.client.Minecraft
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import org.slf4j.LoggerFactory

/**
 * 客户端状态：握手结果、可用伪装、自身伪装（含代理实体）。
 * 只在客户端主线程读写。
 */
object MorphClientState {
    private val logger = LoggerFactory.getLogger("YuiMorph")

    /** 服务器装了 YuiMorph 并接受了本客户端。 */
    var accepted = false
        private set

    var available: List<String> = emptyList()
        private set

    var self: S2CSelfDisguise = S2CSelfDisguise.NONE
        private set

    fun reset() {
        accepted = false
        available = emptyList()
        self = S2CSelfDisguise.NONE
    }

    fun handle(packet: S2CPacket) {
        when (packet) {
            is S2CHello -> {
                accepted = packet.accepted
                logger.info("YuiMorph 服务端 {}：{}", packet.serverVersion, if (packet.accepted) "已启用客户端增强" else "未启用客户端增强")
            }
            is S2CUnlockedSync -> available = if (packet.full) packet.added else (available - packet.removed.toSet() + packet.added).distinct()
            is S2CSelfDisguise -> self = packet
        }
    }

    fun send(packet: C2SPacket) {
        ClientPlayNetworking.send(MorphPayload(PacketCodec.encode(packet)))
    }

    // ---------------- 代理实体 ----------------

    /** 服务器只发给本人的代理实体（伪装外观）。还没收到实体时返回 null。 */
    fun proxy(): LivingEntity? {
        val id = self.proxyEntityId
        if (id == S2CSelfDisguise.NO_PROXY || self.disguiseId == null) return null
        return Minecraft.getInstance().level?.getEntity(id) as? LivingEntity
    }

    /** 由 Mixin 调用：这个实体是不是本客户端的代理实体。 */
    @JvmStatic
    fun isProxy(entity: Entity): Boolean {
        val id = self.proxyEntityId
        return id != S2CSelfDisguise.NO_PROXY && entity.id == id && entity.level() is ClientLevel
    }

    /** 由 Mixin 调用：包装实体查询条件，排除代理实体。没有代理时原样返回，不增加开销。 */
    @JvmStatic
    fun <T> excludeProxy(predicate: java.util.function.Predicate<T>): java.util.function.Predicate<T> {
        if (self.proxyEntityId == S2CSelfDisguise.NO_PROXY) return predicate
        return java.util.function.Predicate { e -> !(e is Entity && isProxy(e)) && predicate.test(e) }
    }

    /**
     * 由 Mixin 调用：是否跳过渲染。
     * - 第三人称：隐藏自己的玩家模型（由代理实体代替）
     * - 第一人称：隐藏代理实体，否则会挡住视线
     */
    @JvmStatic
    fun shouldHide(entity: Entity): Boolean {
        val mc = Minecraft.getInstance()
        if (entity === mc.player) return proxy() != null
        if (isProxy(entity)) return !mc.gameRenderer.mainCamera().isDetached
        return false
    }

    // ---------------- 显示名 ----------------

    fun displayName(id: String): Component {
        if (id.startsWith("player:")) return Component.literal(id.removePrefix("player:"))
        val key = Identifier.tryParse(id) ?: return Component.literal(id)
        return BuiltInRegistries.ENTITY_TYPE.getOptional(key).map { it.description }.orElse(Component.literal(id))
    }

    fun currentName(): Component? = self.disguiseId?.let { id -> self.displayName?.let(Component::literal) ?: displayName(id) }
}
