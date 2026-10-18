package moe.yui.morph.disguise

import io.github.retrooper.packetevents.util.SpigotConversionUtil
import moe.yui.morph.Permissions
import moe.yui.morph.YuiMorphPlugin
import moe.yui.morph.i18n.ph
import org.bukkit.entity.Player

/**
 * 伪装的增删和解锁管理。
 *
 * 所有 `morph` / `unmorph` 调用都必须在目标玩家的区域线程执行（使用 `player.runOnRegion`）。
 */
class DisguiseManager(private val plugin: YuiMorphPlugin) {
    val sessions = SessionRegistry()

    fun current(player: Player): DisguiseSession? = sessions[player.uniqueId]

    fun isUnlocked(player: Player, id: DisguiseId): Boolean =
        plugin.store[player.uniqueId]?.unlocked?.contains(id.toString()) == true

    /** 玩家可以选择的伪装：已解锁的，或者拥有 [Permissions.MORPH_ANY] 时的全部生物。 */
    fun available(player: Player): List<DisguiseId> {
        val unlocked = plugin.store[player.uniqueId]?.unlocked.orEmpty().mapNotNull(DisguiseId::parse)
        val all = if (player.hasPermission(Permissions.MORPH_ANY)) DisguiseId.allMobs() + unlocked else unlocked
        return all.distinct().filter { !plugin.morphConfig.isBanned(it) && Permissions.canMorphAs(player, it) }
    }

    /**
     * 变身。[force] 为 true 时跳过解锁、权限和封禁检查（管理员强制）。
     * 成功返回 true；失败时已经给玩家发送了原因。
     */
    fun morph(player: Player, id: DisguiseId, force: Boolean = false): Boolean {
        val msg = plugin.messages
        val name = ph("disguise", id.displayName())

        if (!force) {
            val reason = when {
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

        val entityType = when (id) {
            is DisguiseId.Mob -> SpigotConversionUtil.fromBukkitEntityType(id.type)
            is DisguiseId.Player -> {
                // TODO(M2): 伪装成玩家
                msg.send(player, "morph.player-not-supported")
                return false
            }
        }

        val session = DisguiseSession(player.uniqueId, player.entityId, id, entityType)
        sessions.put(session)
        plugin.renderer.refresh(player, session)
        msg.send(player, "morph.success", name)
        return true
    }

    /** 解除伪装。没有伪装时返回 false。 */
    fun unmorph(player: Player, silent: Boolean = false): Boolean {
        sessions.remove(player.uniqueId) ?: run {
            if (!silent) plugin.messages.send(player, "morph.not-disguised")
            return false
        }
        plugin.renderer.refresh(player, null)
        if (!silent) plugin.messages.send(player, "morph.unmorph")
        return true
    }

    /** 解锁伪装并保存。已经解锁过返回 false。 */
    fun unlock(player: Player, id: DisguiseId, notify: Boolean = true): Boolean {
        val data = plugin.store[player.uniqueId] ?: return false
        if (!data.unlocked.add(id.toString())) return false
        plugin.store.save(player.uniqueId)
        if (notify) plugin.messages.send(player, "unlock.new", ph("disguise", id.displayName()))
        return true
    }

    fun revoke(player: Player, id: DisguiseId): Boolean {
        val data = plugin.store[player.uniqueId] ?: return false
        if (!data.unlocked.remove(id.toString())) return false
        plugin.store.save(player.uniqueId)
        if (current(player)?.disguise == id) unmorph(player)
        return true
    }

    /** 玩家下线：直接丢弃会话（不需要发包，客户端会收到玩家移除）。 */
    fun onQuit(player: Player) {
        sessions.remove(player.uniqueId)
    }

    /** 玩家重生 / 换世界后 entity id 可能变化，重新登记。 */
    fun syncEntityId(player: Player) {
        val session = current(player) ?: return
        if (session.entityId != player.entityId) sessions.put(session.copy(entityId = player.entityId))
    }
}
