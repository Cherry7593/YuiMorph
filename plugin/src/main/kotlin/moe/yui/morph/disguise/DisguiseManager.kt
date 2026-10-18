package moe.yui.morph.disguise

import com.github.retrooper.packetevents.protocol.entity.type.EntityTypes
import io.github.retrooper.packetevents.util.SpigotConversionUtil
import moe.yui.morph.Permissions
import moe.yui.morph.YuiMorphPlugin
import moe.yui.morph.disguise.property.DisguiseProperties
import moe.yui.morph.disguise.property.PropertyDefs
import moe.yui.morph.i18n.ph
import moe.yui.morph.render.PeMetadata
import moe.yui.morph.skin.ResolvedSkin
import moe.yui.morph.util.runOnRegion
import org.bukkit.entity.Player
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * 伪装的增删、属性和解锁管理。
 *
 * 所有 `morph` / `unmorph` / `setProperty` 调用都必须在目标玩家的区域线程执行（使用 `player.runOnRegion`）。
 */
class DisguiseManager(private val plugin: YuiMorphPlugin) {
    val sessions = SessionRegistry()

    /** 玩家伪装需要异步获取皮肤；记录每个玩家最新一次请求，过期的结果直接丢弃。 */
    private val requestSeq = AtomicLong()
    private val pending = ConcurrentHashMap<UUID, Long>()

    private val msg get() = plugin.messages
    private val props get() = plugin.properties

    fun current(player: Player): DisguiseSession? = sessions[player.uniqueId]

    /** 配置了强制伪装，并且玩家没有绕过权限。 */
    fun isForced(player: Player): Boolean =
        plugin.morphConfig.forcedDisguise != null && !player.hasPermission(Permissions.FORCED_BYPASS)

    fun isUnlocked(player: Player, id: DisguiseId): Boolean {
        val unlocked = plugin.store[player.uniqueId]?.unlocked ?: return false
        val text = id.toString()
        // 玩家名不区分大小写
        return if (id is DisguiseId.Player) unlocked.any { it.equals(text, ignoreCase = true) } else text in unlocked
    }

    /** 玩家可以选择的伪装：已解锁的，或者拥有 [Permissions.MORPH_ANY] 时的全部生物。 */
    fun available(player: Player): List<DisguiseId> {
        val unlocked = plugin.store[player.uniqueId]?.unlocked.orEmpty().mapNotNull(DisguiseId::parse)
        val all = if (player.hasPermission(Permissions.MORPH_ANY)) DisguiseId.allMobs() + unlocked else unlocked
        return all.distinct().filter { !plugin.morphConfig.isBanned(it) && Permissions.canMorphAs(player, it) }
    }

    /**
     * 变身。[force] 为 true 时跳过解锁、权限和封禁检查（管理员强制）。
     * 返回 true 表示已开始变身（玩家伪装在皮肤获取完成后才生效）；失败时已经给玩家发送了原因。
     */
    fun morph(player: Player, id: DisguiseId, force: Boolean = false): Boolean {
        val name = ph("disguise", id.displayName())

        if (!force) {
            val reason = when {
                isForced(player) -> "morph.forced"
                plugin.morphConfig.isWorldDisabled(player.world.name) -> "morph.world-disabled"
                plugin.morphConfig.isBanned(id) -> "morph.banned"
                !Permissions.canMorphAs(player, id) -> "morph.no-permission"
                !isUnlocked(player, id) && !player.hasPermission(Permissions.MORPH_ANY) -> "morph.not-unlocked"
                else -> null
            }
            if (reason != null) {
                msg.send(player, reason, name)
                return false
            }
        }

        if (current(player)?.disguise == id) {
            msg.send(player, "morph.already", name)
            return false
        }

        val saved = savedProperties(player, id)
        if (!plugin.effects.hasSpaceFor(player, id, props.retain(typeKey(id), saved))) {
            msg.send(player, "morph.no-space", name)
            return false
        }
        when (id) {
            is DisguiseId.Mob -> {
                pending.remove(player.uniqueId)
                activate(player, buildSession(player, id, saved, null))
                msg.send(player, "morph.success", name)
            }
            is DisguiseId.Player -> morphAsPlayer(player, id, saved)
        }
        return true
    }

    /** 玩家伪装：先获取皮肤（可以用 `skin` 属性换成别人的皮肤），再回到玩家线程生效。 */
    private fun morphAsPlayer(player: Player, id: DisguiseId.Player, properties: Map<String, String>) {
        val token = requestSeq.incrementAndGet()
        pending[player.uniqueId] = token
        val skinName = properties[PropertyDefs.SKIN] ?: id.name
        msg.send(player, "morph.fetching-skin", ph("name", skinName))

        plugin.skins.resolve(skinName).whenComplete { result, _ ->
            player.runOnRegion(plugin) {
                // 期间玩家又变身成别的或解除了伪装，丢弃这次结果
                if (!pending.remove(player.uniqueId, token)) return@runOnRegion
                val skin = result ?: ResolvedSkin(skinName, null)
                if (skin.skin == null) msg.send(player, "morph.skin-failed", ph("name", skinName))

                // 用 Mojang 返回的正确大小写作为显示名
                val shown = if (skinName.equals(id.name, ignoreCase = true)) DisguiseId.Player(skin.name) else id
                val profile = DisguiseProfile(virtualUuid(player.uniqueId, shown.name, skinName), shown.name, skin.skin)
                activate(player, buildSession(player, shown, properties, profile))
                msg.send(player, "morph.success", ph("disguise", shown.displayName()))
            }
        }
    }

    private fun buildSession(
        player: Player,
        id: DisguiseId,
        properties: Map<String, String>,
        profile: DisguiseProfile?,
    ): DisguiseSession {
        val entityType = when (id) {
            is DisguiseId.Mob -> SpigotConversionUtil.fromBukkitEntityType(id.type)
            is DisguiseId.Player -> EntityTypes.PLAYER
        }
        val typeKey = typeKey(id)
        val valid = props.retain(typeKey, properties)
        return DisguiseSession(
            playerId = player.uniqueId,
            entityId = player.entityId,
            disguise = id,
            entityType = entityType,
            properties = valid,
            metadata = props.metadata(typeKey, valid).mapNotNull(PeMetadata::toEntityData),
            profile = profile,
        )
    }

    private fun activate(player: Player, session: DisguiseSession) {
        val old = current(player)
        sessions.put(session)
        plugin.renderer.apply(player, old, session)
        plugin.effects.apply(player, session)
    }

    /** 解除伪装。没有伪装时返回 false。[force] 为 true 时无视强制伪装（管理员、死亡等）。 */
    fun unmorph(player: Player, silent: Boolean = false, force: Boolean = false): Boolean {
        if (!force && isForced(player)) {
            if (!silent) msg.send(player, "morph.forced")
            return false
        }
        val hadPending = pending.remove(player.uniqueId) != null
        val old = sessions.remove(player.uniqueId)
        if (old == null) {
            if (!silent && !hadPending) msg.send(player, "morph.not-disguised")
            return hadPending
        }
        plugin.renderer.apply(player, old, null)
        plugin.effects.clear(player)
        if (!silent) msg.send(player, "morph.unmorph")
        return true
    }

    // ---------------- 属性 ----------------

    /**
     * 设置（[value] 非 null）或重置当前伪装的属性，并保存为该伪装的默认属性。
     * 校验失败时已经给玩家发送了原因。
     */
    fun setProperty(player: Player, key: String, value: String?): Boolean {
        val session = current(player) ?: run {
            msg.send(player, "property.not-disguised")
            return false
        }
        val typeKey = typeKey(session.disguise)
        val propKey = props.key(typeKey, key) ?: run {
            msg.send(player, "property.unknown-key", ph("key", key))
            return false
        }
        if (propKey.permission != null && !player.hasPermission(propKey.permission)) {
            msg.send(player, "property.no-permission", ph("key", propKey.name))
            return false
        }

        val updated = session.properties.toMutableMap()
        if (value == null) {
            updated.remove(propKey.name)
        } else {
            val normalized = props.normalize(propKey, value) ?: run {
                msg.send(player, "property.invalid-value", ph("key", propKey.name), ph("value", value))
                return false
            }
            updated[propKey.name] = normalized
        }
        saveProperties(player, session.disguise, updated)

        if (propKey.name == PropertyDefs.SKIN) {
            // 换皮肤需要重新获取档案
            morphAsPlayer(player, session.disguise as DisguiseId.Player, updated)
        } else {
            activate(player, buildSession(player, session.disguise, updated, session.profile))
        }
        val key2 = if (value == null) "property.reset" else "property.set"
        msg.send(player, key2, ph("key", propKey.name), ph("value", updated[propKey.name] ?: ""))
        return true
    }

    private fun propertyStoreKey(id: DisguiseId) = if (id is DisguiseId.Player) id.toString().lowercase() else id.toString()

    private fun savedProperties(player: Player, id: DisguiseId): Map<String, String> =
        plugin.store[player.uniqueId]?.properties?.get(propertyStoreKey(id)).orEmpty()

    private fun saveProperties(player: Player, id: DisguiseId, properties: Map<String, String>) {
        val data = plugin.store[player.uniqueId] ?: return
        val key = propertyStoreKey(id)
        if (properties.isEmpty()) data.properties.remove(key) else data.properties[key] = properties.toMap()
        plugin.store.save(player.uniqueId)
    }

    // ---------------- 解锁 ----------------

    /** 解锁伪装并保存。已经解锁过返回 false。 */
    fun unlock(player: Player, id: DisguiseId, notify: Boolean = true): Boolean {
        val data = plugin.store[player.uniqueId] ?: return false
        if (isUnlocked(player, id)) return false
        data.unlocked.add(id.toString())
        plugin.store.save(player.uniqueId)
        if (notify) msg.send(player, "unlock.new", ph("disguise", id.displayName()))
        return true
    }

    fun revoke(player: Player, id: DisguiseId): Boolean {
        val data = plugin.store[player.uniqueId] ?: return false
        val text = id.toString()
        if (!data.unlocked.removeIf { it.equals(text, ignoreCase = id is DisguiseId.Player) }) return false
        plugin.store.save(player.uniqueId)
        if (current(player)?.disguise == id) unmorph(player, force = true)
        return true
    }

    // ---------------- 生命周期 ----------------

    /** 玩家下线：丢弃会话；玩家伪装的虚拟档案需要从其他客户端移除。 */
    fun onQuit(player: Player) {
        pending.remove(player.uniqueId)
        // 在玩家存档之前恢复血量上限，避免下次进服时血量被截断
        plugin.effects.clear(player)
        sessions.remove(player.uniqueId)?.profile?.let(plugin.renderer::broadcastProfileRemoval)
    }

    /** 玩家重生 / 换世界后 entity id 可能变化，重新登记。 */
    fun syncEntityId(player: Player) {
        val session = current(player) ?: return
        if (session.entityId != player.entityId) sessions.put(session.copy(entityId = player.entityId))
    }

    companion object {
        fun typeKey(id: DisguiseId): String = when (id) {
            is DisguiseId.Mob -> id.type.key.asString()
            is DisguiseId.Player -> DisguiseProperties.PLAYER
        }

        /** 虚拟 UUID：由伪装者、伪装名和皮肤来源决定。换皮肤会得到新 UUID，客户端才会重新加载皮肤。 */
        fun virtualUuid(owner: UUID, name: String, skinName: String): UUID =
            UUID.nameUUIDFromBytes("yuimorph:$owner:${name.lowercase()}:${skinName.lowercase()}".toByteArray())
    }
}
