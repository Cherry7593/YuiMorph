package moe.yui.morph.client

import io.papermc.paper.threadedregions.scheduler.ScheduledTask
import moe.yui.morph.Permissions
import moe.yui.morph.YuiMorphPlugin
import moe.yui.morph.disguise.DisguiseId
import moe.yui.morph.disguise.DisguiseManager
import moe.yui.morph.disguise.DisguiseSession
import moe.yui.morph.action.ActionDefs
import moe.yui.morph.render.PeMetadata
import moe.yui.morph.i18n.ph
import moe.yui.morph.protocol.C2SHello
import moe.yui.morph.protocol.C2SMorph
import moe.yui.morph.protocol.C2SPlayAction
import moe.yui.morph.protocol.C2SPreview
import moe.yui.morph.protocol.C2SRequestSkins
import moe.yui.morph.protocol.C2SUnmorph
import moe.yui.morph.protocol.PacketCodec
import moe.yui.morph.protocol.Protocol
import moe.yui.morph.protocol.ProtocolException
import moe.yui.morph.protocol.S2CHello
import moe.yui.morph.protocol.PropertySpec
import moe.yui.morph.protocol.S2CPacket
import moe.yui.morph.protocol.S2CPreview
import moe.yui.morph.protocol.S2CSkin
import moe.yui.morph.protocol.SkinTexture
import moe.yui.morph.disguise.property.PropertyDefs
import moe.yui.morph.protocol.S2CSelfDisguise
import moe.yui.morph.protocol.S2CUnlockedSync
import moe.yui.morph.util.runOnRegion
import org.bukkit.entity.Player
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.messaging.PluginMessageListener
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.logging.Level

/**
 * 和客户端 Mod 的通信（插件消息频道 [Protocol.CHANNEL]）。
 *
 * 客户端进服后发送 [C2SHello]，版本一致时服务端回复接受，并同步可用伪装和自身伪装状态。
 * 没有装 Mod 的玩家完全不受影响。所有对玩家的操作都切到玩家的区域线程执行。
 */
class ClientBridge(private val plugin: YuiMorphPlugin) : PluginMessageListener {
    private class ClientState(val modVersion: String) {
        var equipmentTask: ScheduledTask? = null
        var lastEquipment: List<ItemStack?> = emptyList()
    }

    private val clients = ConcurrentHashMap<UUID, ClientState>()

    fun register() {
        val messenger = plugin.server.messenger
        messenger.registerIncomingPluginChannel(plugin, Protocol.CHANNEL, this)
        messenger.registerOutgoingPluginChannel(plugin, Protocol.CHANNEL)
    }

    fun unregister() {
        plugin.server.messenger.unregisterIncomingPluginChannel(plugin, Protocol.CHANNEL, this)
        plugin.server.messenger.unregisterOutgoingPluginChannel(plugin, Protocol.CHANNEL)
    }

    fun isModded(player: Player): Boolean = clients.containsKey(player.uniqueId)

    override fun onPluginMessageReceived(channel: String, player: Player, message: ByteArray) {
        if (channel != Protocol.CHANNEL) return
        val packet = try {
            PacketCodec.decodeC2S(message)
        } catch (e: ProtocolException) {
            plugin.logger.log(Level.FINE, "来自 ${player.name} 的非法客户端数据：${e.message}")
            return
        }
        player.runOnRegion(plugin) {
            when (packet) {
                is C2SHello -> handshake(player, packet)
                // 握手前的请求一律忽略
                else -> if (isModded(player)) when (packet) {
                    is C2SMorph -> DisguiseId.parse(packet.disguiseId)?.let { plugin.disguises.morph(player, it, properties = packet.properties) }
                        ?: plugin.messages.send(player, "morph.invalid-id", ph("id", packet.disguiseId))
                    is C2SPreview -> preview(player, packet)
                    is C2SRequestSkins -> sendSkins(player, packet.names)
                    C2SUnmorph -> plugin.disguises.unmorph(player)
                    is C2SPlayAction -> plugin.actions.play(player, packet.action)
                    is C2SHello -> Unit
                }
            }
        }
    }

    private fun handshake(player: Player, hello: C2SHello) {
        val accepted = plugin.morphConfig.clientModEnabled && hello.protocolVersion == Protocol.VERSION
        send(player, S2CHello(Protocol.VERSION, accepted, plugin.pluginMeta.version), force = true)
        if (!accepted) {
            plugin.logger.info("${player.name} 的客户端 Mod（${hello.modVersion}，协议 ${hello.protocolVersion}）未启用增强")
            return
        }
        val state = ClientState(hello.modVersion)
        clients[player.uniqueId] = state
        plugin.renderer.enableProxy(player)
        syncAvailable(player)
        val current = plugin.disguises.current(player)
        if (current != null) plugin.renderer.respawnProxy(player, current)
        onDisguiseChanged(player, current)

        // 代理实体的装备：服务器不会给本人发自己的装备包，定期检查变化后补发
        state.equipmentTask = player.scheduler.runAtFixedRate(plugin, { _ ->
            if (plugin.disguises.current(player) == null) return@runAtFixedRate
            val now = EQUIPMENT_SLOTS.map { player.equipment.getItem(it).takeUnless(ItemStack::isEmpty) }
            if (now != state.lastEquipment) {
                state.lastEquipment = now
                plugin.renderer.sendProxyEquipment(player)
            }
        }, null, 10L, 10L)
    }

    // ---------------- 预览 ----------------

    /**
     * 计算预览外观：和真正伪装用同一套属性规则，所以预览和别人看到的完全一致。
     * 只能预览自己可以使用的伪装；属性按权限过滤。必须在玩家所在区域线程调用。
     */
    private fun preview(player: Player, request: C2SPreview) {
        val id = DisguiseId.parse(request.disguiseId) ?: return
        val manager = plugin.disguises
        // 已解锁（或可用）的伪装；拥有 morph.any 时可以预览任意玩家
        val usable = id in manager.available(player) || (id is DisguiseId.Player && player.hasPermission(Permissions.MORPH_ANY))
        if (!usable) return
        val properties = request.properties?.let { manager.allowedProperties(player, id, it) } ?: manager.savedProperties(player, id)
        val specs = manager.allowedKeys(player, id).map { key ->
            PropertySpec(key.name, plugin.properties.values(key), key.maxLength)
        }
        val typeKey = DisguiseManager.typeKey(id)
        val reply = { skin: SkinTexture? ->
            send(player, S2CPreview(request.requestId, id.toString(), PREVIEW_ENTITY_ID, properties, specs, ActionDefs.names(typeKey), skin))
            // 元数据紧跟在预览信息之后发出，客户端按顺序处理
            val metadata = plugin.properties.metadata(typeKey, properties).mapNotNull(PeMetadata::toEntityData)
            plugin.renderer.sendPreviewMetadata(player, PREVIEW_ENTITY_ID, metadata)
        }
        if (id !is DisguiseId.Player) return reply(null)

        // 玩家伪装：带上服务器拿到的签名皮肤（客户端不一定能访问 Mojang）
        val skinName = properties[PropertyDefs.SKIN] ?: id.name
        plugin.skins.resolve(skinName).whenComplete { result, _ ->
            player.runOnRegion(plugin) { reply(result?.skin?.let { SkinTexture(it.value, it.signature) }) }
        }
    }

    /** 界面缩略图要用的玩家皮肤：逐个获取，拿到一个回复一个。 */
    private fun sendSkins(player: Player, names: List<String>) {
        names.asSequence().filter(PLAYER_NAME::matches).distinct().take(C2SRequestSkins.MAX_NAMES).forEach { name ->
            plugin.skins.resolve(name).whenComplete { result, _ ->
                val skin = result?.skin?.let { SkinTexture(it.value, it.signature) }
                player.runOnRegion(plugin) { send(player, S2CSkin(name, skin)) }
            }
        }
    }

    // ---------------- 状态推送 ----------------

    /** 伪装变化（变身、换属性、解除）。必须在玩家所在区域线程调用。 */
    fun onDisguiseChanged(player: Player, session: DisguiseSession?) {
        if (!isModded(player)) return
        clients[player.uniqueId]?.lastEquipment = emptyList()
        send(player, selfDisguise(session))
    }

    /** 可用伪装列表变化（解锁、移除、封禁）。 */
    fun syncAvailable(player: Player) {
        if (!isModded(player)) return
        send(player, S2CUnlockedSync(true, plugin.disguises.available(player).map { it.toString() }, emptyList()))
    }

    fun syncAvailableToAll() {
        clients.keys.mapNotNull(plugin.server::getPlayer).forEach { p -> p.runOnRegion(plugin) { syncAvailable(p) } }
    }

    private fun selfDisguise(session: DisguiseSession?): S2CSelfDisguise {
        if (session == null) return S2CSelfDisguise.NONE
        return S2CSelfDisguise(
            disguiseId = session.disguise.toString(),
            displayName = (session.disguise as? DisguiseId.Player)?.name,
            properties = session.properties,
            actions = plugin.actions.names(session),
            proxyEntityId = plugin.renderer.proxyId(session.playerId) ?: S2CSelfDisguise.NO_PROXY,
        )
    }

    private fun send(player: Player, packet: S2CPacket, force: Boolean = false) {
        if (!force && !isModded(player)) return
        player.sendPluginMessage(plugin, Protocol.CHANNEL, PacketCodec.encode(packet))
    }

    fun onQuit(uuid: UUID) {
        clients.remove(uuid)?.equipmentTask?.cancel()
        plugin.renderer.disableProxy(uuid)
    }

    private companion object {
        /** 预览实体只存在于客户端的界面里，用一个不会和真实实体冲突的负数 ID。 */
        const val PREVIEW_ENTITY_ID = -2
        val PLAYER_NAME = Regex("^[A-Za-z0-9_]{1,16}$")

        val EQUIPMENT_SLOTS = listOf(
            EquipmentSlot.HAND, EquipmentSlot.OFF_HAND, EquipmentSlot.FEET,
            EquipmentSlot.LEGS, EquipmentSlot.CHEST, EquipmentSlot.HEAD,
        )
    }
}
