package moe.yui.morph.request

import moe.yui.morph.YuiMorphPlugin
import moe.yui.morph.command.RootCommand
import moe.yui.morph.disguise.DisguiseId
import moe.yui.morph.i18n.ph
import moe.yui.morph.util.runOnRegion
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.ClickEvent
import org.bukkit.entity.Player
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * 玩家间的伪装交换请求：A 向 B 发送请求，B 接受后双方互相解锁对方的玩家伪装。
 *
 * 请求只存在内存里，超时或任一方下线后失效。任意线程可调用；
 * 涉及某个玩家的操作（解锁、发消息）会切到该玩家的区域线程执行。
 */
class RequestManager(private val plugin: YuiMorphPlugin) {
    private data class Key(val from: UUID, val to: UUID)
    private data class Request(val fromName: String, val toName: String, val expiresAt: Long)

    private val requests = ConcurrentHashMap<Key, Request>()
    private val msg get() = plugin.messages

    private fun now() = System.currentTimeMillis()

    fun send(from: Player, to: Player) {
        when {
            from == to -> return msg.send(from, "request.self")
            plugin.disguises.isUnlocked(from, DisguiseId.Player(to.name)) &&
                plugin.disguises.isUnlocked(to, DisguiseId.Player(from.name)) ->
                return msg.send(from, "request.already-exchanged", ph("player", to.name))
        }
        val key = Key(from.uniqueId, to.uniqueId)
        val existing = requests[key]
        if (existing != null && existing.expiresAt > now()) return msg.send(from, "request.pending", ph("player", to.name))

        val timeout = plugin.morphConfig.requestTimeoutSeconds
        requests[key] = Request(from.name, to.name, now() + timeout * 1000L)
        msg.send(from, "request.sent", ph("player", to.name), ph("seconds", timeout.toString()))

        val buttons = msg.get("request.accept-button").clickEvent(ClickEvent.runCommand("/${RootCommand.ALIAS} request accept ${from.name}"))
            .append(Component.space())
            .append(msg.get("request.deny-button").clickEvent(ClickEvent.runCommand("/${RootCommand.ALIAS} request deny ${from.name}")))
        to.runOnRegion(plugin) {
            msg.send(to, "request.received", ph("player", from.name), ph("seconds", timeout.toString()))
            to.sendMessage(buttons)
        }
    }

    /** [target] 接受来自 [fromName] 的请求。 */
    fun accept(target: Player, fromName: String) {
        val (key, request) = take(target, fromName) ?: return
        val from = plugin.server.getPlayer(key.from)
        if (from == null) return msg.send(target, "request.offline", ph("player", request.fromName))

        // 各自在自己的线程解锁对方
        target.runOnRegion(plugin) { plugin.disguises.unlock(target, DisguiseId.Player(from.name)) }
        from.runOnRegion(plugin) {
            plugin.disguises.unlock(from, DisguiseId.Player(target.name))
            msg.send(from, "request.accepted-by", ph("player", target.name))
        }
        msg.send(target, "request.accepted", ph("player", from.name))
    }

    fun deny(target: Player, fromName: String) {
        val (key, request) = take(target, fromName) ?: return
        msg.send(target, "request.denied", ph("player", request.fromName))
        plugin.server.getPlayer(key.from)?.let { from ->
            from.runOnRegion(plugin) { msg.send(from, "request.denied-by", ph("player", target.name)) }
        }
    }

    /** 取出并删除请求；不存在或已过期时提示玩家并返回 null。 */
    private fun take(target: Player, fromName: String): Pair<Key, Request>? {
        val entry = requests.entries.firstOrNull { (k, r) -> k.to == target.uniqueId && r.fromName.equals(fromName, true) }
        if (entry == null || !requests.remove(entry.key, entry.value) || entry.value.expiresAt <= now()) {
            msg.send(target, "request.not-found", ph("player", fromName))
            return null
        }
        return entry.key to entry.value
    }

    /** 发给 [target] 的有效请求的发送者名字（用于补全）。 */
    fun pendingFor(target: UUID): List<String> {
        val t = now()
        return requests.entries.filter { (k, r) -> k.to == target && r.expiresAt > t }.map { it.value.fromName }
    }

    /** 玩家下线时清理与其相关的请求。 */
    fun onQuit(uuid: UUID) {
        requests.keys.removeIf { it.from == uuid || it.to == uuid }
    }

    /** 定期清理过期请求（由异步定时任务调用）。 */
    fun purgeExpired() {
        val t = now()
        requests.values.removeIf { it.expiresAt <= t }
    }
}
