package moe.yui.morph.listener

import com.destroystokyo.paper.event.player.PlayerPostRespawnEvent
import moe.yui.morph.Permissions
import moe.yui.morph.YuiMorphPlugin
import moe.yui.morph.disguise.DisguiseId
import moe.yui.morph.util.runAsync
import moe.yui.morph.util.runOnRegion
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDeathEvent
import org.bukkit.event.entity.PlayerDeathEvent
import org.bukkit.event.player.AsyncPlayerPreLoginEvent
import org.bukkit.event.player.PlayerChangedWorldEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent

class PlayerListener(private val plugin: YuiMorphPlugin) : Listener {
    private val manager get() = plugin.disguises

    /** 登录前在异步线程加载数据，进服时数据已就绪。 */
    @EventHandler(priority = EventPriority.MONITOR)
    fun onPreLogin(event: AsyncPlayerPreLoginEvent) {
        if (event.loginResult == AsyncPlayerPreLoginEvent.Result.ALLOWED) plugin.store.load(event.uniqueId)
    }

    /** 兜底：插件在玩家在线时启用，或者预登录事件被其他插件拦截后又放行。 */
    @EventHandler
    fun onJoin(event: PlayerJoinEvent) {
        val uuid = event.player.uniqueId
        if (plugin.store[uuid] == null) plugin.runAsync { plugin.store.load(uuid) }
        // 补发其他人的玩家伪装档案，否则看不到他们的伪装
        plugin.renderer.sendProfilesTo(event.player)
        restoreOrForce(event.player)
    }

    /** 进服时：强制伪装优先；否则恢复下线前的伪装（仍然检查解锁和权限）。 */
    private fun restoreOrForce(player: Player) {
        val data = plugin.store[player.uniqueId]
        val saved = data?.current?.let(DisguiseId::parse)
        data?.current = null
        val forced = plugin.morphConfig.forcedDisguise?.let(DisguiseId::parse)

        player.runOnRegion(plugin) {
            when {
                forced != null && manager.isForced(player) -> manager.morph(player, forced, force = true)
                saved != null && plugin.morphConfig.restoreOnJoin -> manager.morph(player, saved)
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun onQuit(event: PlayerQuitEvent) {
        plugin.store[event.player.uniqueId]?.current = manager.current(event.player)?.disguise?.toString()
        manager.onQuit(event.player)
        plugin.requests.onQuit(event.player.uniqueId)
        plugin.sounds.onQuit(event.player.uniqueId)
        plugin.client.onQuit(event.player.uniqueId)
        plugin.store.unload(event.player.uniqueId)
    }

    /** 重生后玩家属性会重建，重新应用碰撞箱和血量。 */
    @EventHandler
    fun onRespawn(event: PlayerPostRespawnEvent) {
        val player = event.player
        manager.syncEntityId(player)
        manager.current(player)?.let {
            plugin.effects.apply(player, it)
            // 重生后客户端世界重建，代理实体需要重新生成
            plugin.renderer.respawnProxy(player, it)
        }
    }

    /** 死亡时解除伪装（强制伪装不受影响）。 */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onPlayerDeath(event: PlayerDeathEvent) {
        val player = event.player
        if (plugin.morphConfig.unmorphOnDeath && manager.current(player) != null && manager.unmorph(player, silent = true)) {
            plugin.messages.send(player, "morph.death-removed")
        }
    }

    @EventHandler
    fun onChangeWorld(event: PlayerChangedWorldEvent) {
        val player = event.player
        manager.syncEntityId(player)
        manager.current(player)?.let { plugin.renderer.respawnProxy(player, it) }
        if (manager.current(player) != null && plugin.morphConfig.isWorldDisabled(player.world.name)) {
            manager.unmorph(player, silent = true, force = true)
            plugin.messages.send(player, "morph.world-disabled-removed")
        }
    }

    /** 击杀解锁。事件在死者所在区域线程触发，解锁操作切到击杀者的线程执行。 */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onDeath(event: EntityDeathEvent) {
        if (!plugin.morphConfig.unlockOnKill) return
        val victim = event.entity
        val killer = victim.killer ?: return
        if (killer == victim || NO_GRANT_TAG in victim.scoreboardTags) return

        val id = when (victim) {
            is Player -> DisguiseId.Player(victim.name)
            else -> DisguiseId.of(victim.type) ?: return
        }
        killer.runOnRegion(plugin) {
            if (killer.hasPermission(Permissions.ACQUIRE)) manager.unlock(killer, id)
        }
    }

    private companion object {
        const val NO_GRANT_TAG = "yuimorph_nogrant"
    }
}
