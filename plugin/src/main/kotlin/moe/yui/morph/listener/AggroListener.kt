package moe.yui.morph.listener

import moe.yui.morph.YuiMorphPlugin
import moe.yui.morph.disguise.DisguiseId
import moe.yui.morph.effect.AggroRules
import org.bukkit.Tag
import org.bukkit.entity.Mob
import org.bukkit.entity.Player
import org.bukkit.entity.Projectile
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.entity.EntityDeathEvent
import org.bukkit.event.entity.EntityTargetEvent
import org.bukkit.event.entity.EntityTargetEvent.TargetReason
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * 怪物仇恨：取消怪物对伪装玩家「不该发生」的主动索敌，规则见 [AggroRules]。
 *
 * 只取消事件，不修改生物 AI。变身前已经在追你的怪物会继续追（事件只在换目标时触发）。
 * Folia 下事件在怪物所在区域线程触发；这里只读会话表、读写并发的挑衅表，不改世界。
 */
class AggroListener(private val plugin: YuiMorphPlugin) : Listener {
    private val config get() = plugin.morphConfig.mobAggro

    /** 挑衅记录：怪物 UUID → 打过它的玩家和过期时间。 */
    private data class Provoke(val player: UUID, val expiresAt: Long)

    private val provoked = ConcurrentHashMap<UUID, Provoke>()

    @EventHandler(ignoreCancelled = true)
    fun onTarget(event: EntityTargetEvent) {
        val player = event.target as? Player ?: return
        val mob = event.entity
        if (!config.enabled || event.reason in PASS_REASONS) return

        val source = mob.type.key.asString()
        if (!AggroRules.handles(source)) return
        if (source == AggroRules.PIGLIN_BRUTE && config.bruteIgnoresDisguise) return

        val disguise = (plugin.disguises.current(player)?.disguise as? DisguiseId.Mob)?.type ?: return
        if (isProvoked(mob.uniqueId, player.uniqueId)) return

        val witherFriend = source == AggroRules.WITHER && Tag.ENTITY_TYPES_WITHER_FRIENDS.isTagged(disguise)
        if (!AggroRules.shouldTarget(source, disguise.key.asString(), witherFriend)) event.isCancelled = true
    }

    /** 玩家（或玩家射出的弹射物）打了怪物：一段时间内允许它主动追击这名玩家。 */
    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    fun onDamage(event: EntityDamageByEntityEvent) {
        val mob = event.entity as? Mob ?: return
        if (!config.enabled || config.provokeSeconds <= 0) return
        val damager = event.damager
        val player = damager as? Player ?: (damager as? Projectile)?.shooter as? Player ?: return
        if (!AggroRules.handles(mob.type.key.asString())) return
        provoked[mob.uniqueId] = Provoke(player.uniqueId, System.currentTimeMillis() + config.provokeSeconds * 1000L)
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun onDeath(event: EntityDeathEvent) {
        provoked.remove(event.entity.uniqueId)
    }

    private fun isProvoked(mob: UUID, player: UUID): Boolean {
        val record = provoked[mob] ?: return false
        if (record.expiresAt <= System.currentTimeMillis()) {
            provoked.remove(mob, record)
            return false
        }
        return record.player == player
    }

    /** 定期清理过期记录（怪物被卸载时不会触发死亡事件）。 */
    fun purgeExpired() {
        val now = System.currentTimeMillis()
        provoked.entries.removeIf { it.value.expiresAt <= now }
    }

    companion object {
        /** 反击、协助、主人指令和插件设置的目标一律放行（同 FeatherMorph）。 */
        private val PASS_REASONS = setOf(
            TargetReason.TARGET_ATTACKED_ENTITY,
            TargetReason.TARGET_ATTACKED_NEARBY_ENTITY,
            TargetReason.REINFORCEMENT_TARGET,
            TargetReason.FOLLOW_LEADER,
            TargetReason.DEFEND_VILLAGE,
            TargetReason.TARGET_ATTACKED_OWNER,
            TargetReason.OWNER_ATTACKED_TARGET,
            TargetReason.CUSTOM,
            TargetReason.UNKNOWN,
        )
    }
}
