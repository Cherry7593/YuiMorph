package moe.yui.morph.action

import com.github.retrooper.packetevents.protocol.entity.data.EntityData
import com.github.retrooper.packetevents.protocol.entity.data.EntityDataTypes
import moe.yui.morph.YuiMorphPlugin
import moe.yui.morph.disguise.DisguiseManager
import moe.yui.morph.disguise.DisguiseSession
import moe.yui.morph.disguise.property.MetaValue
import moe.yui.morph.i18n.ph
import moe.yui.morph.render.EntitySnapshot
import moe.yui.morph.render.PeMetadata
import org.bukkit.NamespacedKey
import org.bukkit.Registry
import org.bukkit.SoundCategory
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerMoveEvent
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * 执行伪装动作：按步骤修改元数据、播放声音、等待。
 *
 * 每次开始新动作都会换一个令牌，旧动作还没跑完的延迟步骤发现令牌不对就停止。
 * 所有方法都必须在玩家所在区域线程调用（延迟步骤也通过玩家的调度器回到该线程）。
 */
class ActionManager(private val plugin: YuiMorphPlugin) : Listener {
    private class Running(val token: Long, val def: ActionDef, val stopping: Boolean = false)

    private val running = ConcurrentHashMap<UUID, Running>()
    private val tokens = AtomicLong()
    private val msg get() = plugin.messages

    fun names(session: DisguiseSession): List<String> = ActionDefs.names(DisguiseManager.typeKey(session.disguise))

    /** 播放动作；`stop` 停止当前的持续动作。 */
    fun play(player: Player, name: String) {
        val session = plugin.disguises.current(player) ?: return msg.send(player, "action.not-disguised")
        if (name.equals(ActionDefs.STOP, ignoreCase = true)) {
            if (!stop(player)) msg.send(player, "action.nothing-to-stop")
            return
        }
        val def = ActionDefs.find(DisguiseManager.typeKey(session.disguise), name)
            ?: return msg.send(player, "action.unknown", ph("action", name))

        // 先结束正在进行的动作（不播放它的结束步骤，直接恢复）
        interrupt(player)
        val run = Running(tokens.incrementAndGet(), def)
        running[player.uniqueId] = run
        execute(player, run, def.steps, 0) { if (!def.persistent) finish(player, run) }
    }

    /** 停止持续动作并执行它的结束步骤。没有正在进行的动作时返回 false。 */
    fun stop(player: Player): Boolean {
        val current = running[player.uniqueId] ?: return false
        if (current.stopping) return true
        val stopping = Running(tokens.incrementAndGet(), current.def, stopping = true)
        running[player.uniqueId] = stopping
        execute(player, stopping, current.def.stopSteps, 0) { finish(player, stopping) }
        return true
    }

    /** 立即中断并恢复字段（换动作时）。 */
    private fun interrupt(player: Player) {
        val current = running.remove(player.uniqueId) ?: return
        resetTargets(player, current.def)
    }

    /** 伪装被替换或解除时调用：只丢弃状态，新的会话本来就没有动作覆盖值。 */
    fun forget(player: Player) {
        running.remove(player.uniqueId)
    }

    private fun finish(player: Player, run: Running) {
        running.remove(player.uniqueId, run)
    }

    private fun execute(player: Player, run: Running, steps: List<ActionStep>, from: Int, done: () -> Unit) {
        for (i in from until steps.size) {
            // 期间换了动作、停止或解除伪装
            if (running[player.uniqueId] !== run || plugin.disguises.current(player) == null) return
            when (val step = steps[i]) {
                is ActionStep.Set -> apply(player, mapOf(step.target to step.value))
                is ActionStep.Sound -> playSound(player, step)
                is ActionStep.Reset -> resetTargets(player, run.def)
                is ActionStep.Wait -> {
                    player.scheduler.runDelayed(plugin, { execute(player, run, steps, i + 1, done) }, null, step.ticks.coerceAtLeast(1))
                    return
                }
            }
        }
        if (running[player.uniqueId] === run) done()
    }

    // ---------------- 元数据 ----------------

    /** 写入动作覆盖值并通知观察者。 */
    private fun apply(player: Player, values: Map<ActionTarget, Any>) {
        val session = plugin.disguises.current(player) ?: return
        val data = values.mapNotNull { (target, value) -> toEntityData(session, target, value) }
        if (data.isEmpty()) return
        val indices = data.mapTo(HashSet()) { it.index }
        val merged = session.actionMetadata.filter { it.index !in indices } + data
        plugin.disguises.sessions.put(session.copy(actionMetadata = merged))
        plugin.renderer.sendMetadata(player, data)
    }

    /** 恢复动作改过的字段：有属性覆盖时用属性值，否则用默认值（姿态用玩家实时姿态）。 */
    private fun resetTargets(player: Player, def: ActionDef) {
        val session = plugin.disguises.current(player) ?: return
        val restored = def.targets.mapNotNull { target ->
            val index = indexOf(session, target) ?: return@mapNotNull null
            session.metadata.firstOrNull { it.index == index }
                ?: if (target.default == null) EntityData(index, EntityDataTypes.ENTITY_POSE, EntitySnapshot.pose(player.pose))
                else toEntityData(session, target, target.default)
        }
        val indices = restored.mapTo(HashSet()) { it.index }
        plugin.disguises.sessions.put(session.copy(actionMetadata = session.actionMetadata.filter { it.index !in indices }))
        plugin.renderer.sendMetadata(player, restored)
    }

    private fun indexOf(session: DisguiseSession, target: ActionTarget): Int? =
        plugin.layouts.indexOf(DisguiseManager.typeKey(session.disguise), target.field)

    private fun toEntityData(session: DisguiseSession, target: ActionTarget, value: Any): EntityData<*>? {
        val index = indexOf(session, target) ?: return null
        return PeMetadata.toEntityData(MetaValue(index, target.kind, value))
    }

    private fun playSound(player: Player, step: ActionStep.Sound) {
        val sound = Registry.SOUNDS.get(NamespacedKey.minecraft(step.key)) ?: return
        player.world.playSound(player.location, sound, SoundCategory.NEUTRAL, step.volume, 1f)
    }

    // ---------------- 移动取消 ----------------

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onMove(event: PlayerMoveEvent) {
        val run = running[event.player.uniqueId] ?: return
        if (!run.def.persistent || !run.def.cancelOnMove || run.stopping) return
        val from = event.from
        val to = event.to
        // 只看位置变化，转头不算
        val dx = to.x - from.x
        val dy = to.y - from.y
        val dz = to.z - from.z
        if (dx * dx + dy * dy + dz * dz > MOVE_THRESHOLD_SQ) stop(event.player)
    }

    private companion object {
        const val MOVE_THRESHOLD_SQ = 0.01 * 0.01
    }
}
