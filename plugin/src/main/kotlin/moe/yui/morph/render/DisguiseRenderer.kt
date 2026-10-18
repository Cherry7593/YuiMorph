package moe.yui.morph.render

import com.github.retrooper.packetevents.PacketEvents
import com.github.retrooper.packetevents.event.PacketListenerAbstract
import com.github.retrooper.packetevents.event.PacketListenerPriority
import com.github.retrooper.packetevents.event.PacketSendEvent
import com.github.retrooper.packetevents.protocol.entity.data.EntityData
import com.github.retrooper.packetevents.protocol.entity.type.EntityTypes
import com.github.retrooper.packetevents.protocol.packettype.PacketType
import com.github.retrooper.packetevents.protocol.player.Equipment
import com.github.retrooper.packetevents.protocol.player.EquipmentSlot as PeSlot
import com.github.retrooper.packetevents.util.Vector3d
import com.github.retrooper.packetevents.wrapper.PacketWrapper
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerDestroyEntities
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityEquipment
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityMetadata
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSetPassengers
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSpawnEntity
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerUpdateAttributes
import io.github.retrooper.packetevents.util.SpigotConversionUtil
import moe.yui.morph.disguise.DisguiseSession
import moe.yui.morph.disguise.SessionRegistry
import org.bukkit.entity.Player
import org.bukkit.inventory.EquipmentSlot
import java.util.Optional
import java.util.logging.Level
import java.util.logging.Logger

/**
 * 服务端渲染：改写发给**其他玩家**的数据包，把伪装中的玩家显示成伪装实体。
 *
 * 伪装实体沿用玩家的 entity id，所以移动、转头、动画等包不需要处理。
 * 监听器运行在 Netty 线程，只读取 [SessionRegistry]，不访问世界状态。
 */
class DisguiseRenderer(
    private val sessions: SessionRegistry,
    private val logger: Logger,
) : PacketListenerAbstract(PacketListenerPriority.HIGH) {

    fun register() {
        PacketEvents.getAPI().eventManager.registerListener(this)
    }

    fun unregister() {
        PacketEvents.getAPI().eventManager.unregisterListener(this)
    }

    // ---------------- 被动改写：服务器正常发出的包 ----------------

    override fun onPacketSend(event: PacketSendEvent) {
        if (sessions.isEmpty) return
        try {
            when (event.packetType) {
                PacketType.Play.Server.SPAWN_ENTITY -> onSpawn(event)
                PacketType.Play.Server.ENTITY_METADATA -> onMetadata(event)
                PacketType.Play.Server.UPDATE_ATTRIBUTES -> onAttributes(event)
            }
        } catch (e: Exception) {
            logger.log(Level.WARNING, "改写数据包失败：${event.packetType}", e)
        }
    }

    /** 找到这个包对应的伪装会话；发给伪装者自己的包不处理。 */
    private fun sessionFor(event: PacketSendEvent, entityId: Int): DisguiseSession? {
        val session = sessions.byEntityId(entityId) ?: return null
        return if (event.user.uuid == session.playerId) null else session
    }

    private fun onSpawn(event: PacketSendEvent) {
        val packet = WrapperPlayServerSpawnEntity(event)
        val session = sessionFor(event, packet.entityId) ?: return
        packet.entityType = session.entityType
        event.markForReEncode(true)
    }

    private fun onMetadata(event: PacketSendEvent) {
        val packet = WrapperPlayServerEntityMetadata(event)
        val session = sessionFor(event, packet.entityId) ?: return
        val filtered = filterMetadata(packet.entityMetadata)
        if (filtered.isEmpty()) {
            event.isCancelled = true
        } else if (filtered.size != packet.entityMetadata.size) {
            packet.entityMetadata = filtered
            event.markForReEncode(true)
        }
    }

    /** 伪装实体没有玩家的属性，客户端收到未知属性会刷警告，直接丢弃。 */
    private fun onAttributes(event: PacketSendEvent) {
        val entityId = WrapperPlayServerUpdateAttributes(event).entityId
        if (sessionFor(event, entityId) != null) event.isCancelled = true
    }

    /**
     * 只保留所有生物通用的字段（索引 0..14）。
     * 血量只在 ≤ 0 时转发（用于死亡动画），否则伪装实体会按玩家血量显示受损外观。
     */
    private fun filterMetadata(data: List<EntityData<*>>): List<EntityData<*>> = data.filter {
        when {
            it.index > EntityIndex.LAST_COMMON -> false
            it.index == EntityIndex.HEALTH -> (it.value as? Float ?: 1f) <= 0f
            else -> true
        }
    }

    // ---------------- 主动刷新：变身 / 解除时重新生成实体 ----------------

    /**
     * 对当前所有观察者重新生成该玩家的实体（伪装实体或玩家本体）。
     * 必须在玩家所在区域线程调用。
     */
    fun refresh(player: Player, session: DisguiseSession?) {
        val viewers = player.trackedBy
        if (viewers.isEmpty()) return

        val packets = buildList<PacketWrapper<*>> {
            add(WrapperPlayServerDestroyEntities(player.entityId))
            addAll(spawnPackets(player, session))
        }
        val pm = PacketEvents.getAPI().playerManager
        for (viewer in viewers) {
            // 假人插件（FakePlayer 等）的玩家没有真实连接，PacketEvents 没有接管，跳过
            if (pm.getUser(viewer) == null) continue
            try {
                // 用 silently 发送，避免被自己的监听器再处理一遍
                packets.forEach { pm.sendPacketSilently(viewer, it) }
            } catch (e: Exception) {
                logger.log(Level.FINE, "向 ${viewer.name} 发送伪装刷新失败", e)
            }
        }
    }

    private fun spawnPackets(player: Player, session: DisguiseSession?): List<PacketWrapper<*>> {
        val loc = player.location
        val vel = player.velocity
        val type = session?.entityType ?: EntityTypes.PLAYER
        val metadata = if (session == null) EntitySnapshot.realPlayer(player) else EntitySnapshot.common(player)

        return buildList {
            add(
                WrapperPlayServerSpawnEntity(
                    player.entityId, Optional.of(player.uniqueId), type,
                    Vector3d(loc.x, loc.y, loc.z), loc.pitch, loc.yaw, loc.yaw, 0,
                    Optional.of(Vector3d(vel.x, vel.y, vel.z)),
                ),
            )
            add(WrapperPlayServerEntityMetadata(player.entityId, metadata))
            add(WrapperPlayServerEntityEquipment(player.entityId, equipment(player)))
            // 实体被销毁后客户端会丢失乘坐关系，需要补发
            player.vehicle?.let { v -> add(WrapperPlayServerSetPassengers(v.entityId, v.passengers.map { it.entityId }.toIntArray())) }
            if (player.passengers.isNotEmpty()) {
                add(WrapperPlayServerSetPassengers(player.entityId, player.passengers.map { it.entityId }.toIntArray()))
            }
        }
    }

    private fun equipment(player: Player): List<Equipment> {
        val eq = player.equipment
        return SLOTS.map { (bukkit, pe) -> Equipment(pe, SpigotConversionUtil.fromBukkitItemStack(eq.getItem(bukkit))) }
    }

    private companion object {
        val SLOTS = listOf(
            EquipmentSlot.HAND to PeSlot.MAIN_HAND,
            EquipmentSlot.OFF_HAND to PeSlot.OFF_HAND,
            EquipmentSlot.FEET to PeSlot.BOOTS,
            EquipmentSlot.LEGS to PeSlot.LEGGINGS,
            EquipmentSlot.CHEST to PeSlot.CHEST_PLATE,
            EquipmentSlot.HEAD to PeSlot.HELMET,
        )
    }
}
