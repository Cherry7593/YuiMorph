package moe.yui.morph.effect

import com.github.retrooper.packetevents.event.PacketSendEvent
import com.github.retrooper.packetevents.protocol.sound.SoundCategory
import com.github.retrooper.packetevents.protocol.sound.Sounds
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerDamageEvent
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerHurtAnimation
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntitySoundEffect
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSoundEffect
import moe.yui.morph.YuiMorphPlugin
import moe.yui.morph.disguise.DisguiseId
import moe.yui.morph.disguise.DisguiseSession
import org.bukkit.NamespacedKey
import org.bukkit.Registry
import org.bukkit.Sound
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.entity.PlayerDeathEvent
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs
import kotlin.random.Random

/** 生物伪装的音效键规则（纯函数，便于测试）。 */
object SoundKeys {
    /** 命名不符合 `entity.<id>.hurt / death` 规则的生物。 */
    private val SPECIAL = mapOf(
        "armor_stand" to ("entity.armor_stand.hit" to "entity.armor_stand.break"),
    )

    /** 尺寸为 1 时使用 `_small` 音效的生物。 */
    private val CUBE_MOBS = setOf("slime", "magma_cube")

    fun hurt(entityKey: String, properties: Map<String, String>): String =
        SPECIAL[entityKey]?.first ?: "entity.$entityKey.hurt${smallSuffix(entityKey, properties)}"

    fun death(entityKey: String, properties: Map<String, String>): String =
        SPECIAL[entityKey]?.second ?: "entity.$entityKey.death${smallSuffix(entityKey, properties)}"

    private fun smallSuffix(entityKey: String, properties: Map<String, String>): String =
        if (entityKey in CUBE_MOBS && (properties["size"]?.toIntOrNull() ?: 1) <= 1) "_small" else ""

    /** 原版生物音调：幼体高 0.5。 */
    fun pitchMultiplier(properties: Map<String, String>): Float = if (properties["baby"] == "true") 1.5f else 1.0f
}

/**
 * 把伪装中玩家的受击、死亡音效换成伪装生物的音效。
 *
 * - 死亡：Paper 的 [PlayerDeathEvent] 可以直接改音效。
 * - 受击（旁观者听到的）：没有 API。服务器以「只有坐标的声音包」广播 `entity.player.hurt*`，
 *   并且**排除受伤者本人**。在受伤事件里记下位置，数据包层把该位置的玩家受击音效**改写**成生物音效。
 *   改写而不是取消再另播：原版不播受击音效的情况（盾牌格挡、无敌帧）也不会多出声音，音量音调也保持原样。
 * - 受击（伪装者自己听到的）：不是服务器发的声音，而是客户端收到「伤害事件包」后在本地播放玩家受击音效。
 *   所以拦截发给伪装者本人、关于他自己的伤害事件包，改发「受伤动画包」（保留受伤闪红和镜头晃动，不带声音）
 *   和生物受击音效。
 */
class DisguiseSounds(private val plugin: YuiMorphPlugin) : Listener {
    private data class Mark(val x: Double, val y: Double, val z: Double, val sound: String, val pitch: Float, val expiresAt: Long)

    private val marks = ConcurrentHashMap<UUID, Mark>()

    /** 服务器发给伪装者本人的最近一次受伤方向（来自 indicateDamage 的受伤动画包）。 */
    private val lastHurtYaw = ConcurrentHashMap<UUID, Float>()

    /** 音效在注册表中存在才使用，否则保留玩家原音效，避免发出客户端不认识的声音。 */
    private fun resolve(key: String): Sound? = Registry.SOUNDS.get(NamespacedKey.minecraft(key))

    private fun mobOf(player: Player): Pair<DisguiseSession, String>? {
        val session = plugin.disguises.current(player) ?: return null
        val mob = session.disguise as? DisguiseId.Mob ?: return null
        return session to mob.type.key.key
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onDamage(event: EntityDamageEvent) {
        val player = event.entity as? Player ?: return
        val (session, key) = mobOf(player) ?: return
        val sound = SoundKeys.hurt(key, session.properties).takeIf { resolve(it) != null } ?: return
        val loc = player.location
        marks[player.uniqueId] = Mark(
            loc.x, loc.y, loc.z, sound, SoundKeys.pitchMultiplier(session.properties),
            System.currentTimeMillis() + MARK_TTL_MS,
        )
    }

    /** 在 MONITOR 之前执行：死亡时解除伪装（unmorph-on-death）发生在 MONITOR。 */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onDeath(event: PlayerDeathEvent) {
        val (session, key) = mobOf(event.player) ?: return
        val sound = resolve(SoundKeys.death(key, session.properties)) ?: return
        event.deathSound = sound
        event.deathSoundPitch = event.deathSoundPitch * SoundKeys.pitchMultiplier(session.properties)
    }

    /** 由数据包监听器调用（Netty 线程）。同一个声音会分别发给每个旁观者，所以标记在过期前一直有效。 */
    fun rewrite(event: PacketSendEvent) {
        if (marks.isEmpty()) return
        val packet = WrapperPlayServerSoundEffect(event)
        if (!packet.sound.soundId.key.startsWith(PLAYER_HURT)) return

        val pos = packet.effectPosition
        val x = pos.x / 8.0
        val y = pos.y / 8.0
        val z = pos.z / 8.0
        val now = System.currentTimeMillis()
        val mark = marks.values.firstOrNull {
            it.expiresAt > now && abs(it.x - x) <= TOLERANCE && abs(it.y - y) <= TOLERANCE && abs(it.z - z) <= TOLERANCE
        } ?: return

        packet.sound = Sounds.getByNameOrCreate("minecraft:${mark.sound}")
        packet.pitch = packet.pitch * mark.pitch
        event.markForReEncode(true)
    }

    /** 绑定实体的声音包：直接按 entity id 找伪装会话，不需要坐标匹配。 */
    fun rewriteEntity(event: PacketSendEvent) {
        val packet = WrapperPlayServerEntitySoundEffect(event)
        if (!packet.sound.soundId.key.startsWith(PLAYER_HURT)) return
        val session = plugin.disguises.sessions.byEntityId(packet.entityId) ?: return
        val mob = session.disguise as? DisguiseId.Mob ?: return
        val sound = SoundKeys.hurt(mob.type.key.key, session.properties).takeIf { resolve(it) != null } ?: return
        packet.sound = Sounds.getByNameOrCreate("minecraft:$sound")
        packet.pitch = packet.pitch * SoundKeys.pitchMultiplier(session.properties)
        event.markForReEncode(true)
    }

    /** 发给伪装者本人、关于他自己的包对应的会话；其他情况返回 null。 */
    private fun selfSession(event: PacketSendEvent, entityId: Int): DisguiseSession? =
        plugin.disguises.sessions.byEntityId(entityId)?.takeIf { it.playerId == event.user.uuid }

    /** 记下受伤方向，替换伤害事件时用它重发受伤动画，镜头倾斜方向保持正确。 */
    fun onHurtAnimation(event: PacketSendEvent) {
        val packet = WrapperPlayServerHurtAnimation(event)
        val session = selfSession(event, packet.entityId) ?: return
        lastHurtYaw[session.playerId] = packet.yaw
    }

    /** 伪装者本人收到关于自己的伤害事件：换成无声的受伤动画 + 生物受击音效。 */
    fun onDamageEvent(event: PacketSendEvent) {
        val packet = WrapperPlayServerDamageEvent(event)
        val session = selfSession(event, packet.entityId) ?: return
        val mob = session.disguise as? DisguiseId.Mob ?: return
        // 没有对应的生物音效时保留原版行为
        val sound = SoundKeys.hurt(mob.type.key.key, session.properties).takeIf { resolve(it) != null } ?: return

        event.isCancelled = true
        val user = event.user
        val yaw = lastHurtYaw[session.playerId] ?: 0f
        // 原版 getVoicePitch：1 ± 0.2 的随机浮动
        val pitch = ((Random.nextFloat() - Random.nextFloat()) * 0.2f + 1f) * SoundKeys.pitchMultiplier(session.properties)
        user.sendPacketSilently(WrapperPlayServerHurtAnimation(packet.entityId, yaw))
        user.sendPacketSilently(
            WrapperPlayServerEntitySoundEffect(Sounds.getByNameOrCreate("minecraft:$sound"), SoundCategory.PLAYER, packet.entityId, 1f, pitch),
        )
    }

    /** 定期清理过期标记（异步定时任务）。 */
    fun purgeExpired() {
        val now = System.currentTimeMillis()
        marks.values.removeIf { it.expiresAt <= now }
    }

    fun onQuit(uuid: UUID) {
        marks.remove(uuid)
        lastHurtYaw.remove(uuid)
    }

    private companion object {
        const val PLAYER_HURT = "entity.player.hurt"
        const val MARK_TTL_MS = 500L

        /** 声音包坐标精度是 1/8 格，留一点余量。 */
        const val TOLERANCE = 0.25
    }
}
