package moe.yui.morph.effect

import io.papermc.paper.threadedregions.scheduler.ScheduledTask
import moe.yui.morph.YuiMorphPlugin
import moe.yui.morph.disguise.DisguiseId
import moe.yui.morph.disguise.DisguiseManager
import moe.yui.morph.disguise.DisguiseSession
import org.bukkit.GameMode
import org.bukkit.NamespacedKey
import org.bukkit.Registry
import org.bukkit.Sound
import org.bukkit.SoundCategory
import org.bukkit.attribute.Attribute
import org.bukkit.attribute.AttributeModifier
import org.bukkit.entity.Player
import org.bukkit.util.BoundingBox
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.random.Random

/**
 * 伪装带来的玩家本体变化：碰撞箱（scale 属性）、最大生命值、环境音效。
 *
 * 属性修改都是 transient（不写入存档），所以服务器崩溃也不会残留。
 * 所有方法都必须在玩家所在区域线程调用。
 */
class DisguiseEffects(private val plugin: YuiMorphPlugin, private val traits: EntityTraits) {
    private val soundTasks = ConcurrentHashMap<UUID, ScheduledTask>()
    private val config get() = plugin.morphConfig

    private fun traitsOf(session: DisguiseSession): Traits? =
        if (session.disguise is DisguiseId.Player) null else traits[DisguiseManager.typeKey(session.disguise)]

    /** 应用（或刷新）伪装效果，可重复调用。 */
    fun apply(player: Player, session: DisguiseSession) {
        val t = traitsOf(session)
        val typeKey = DisguiseManager.typeKey(session.disguise)

        val scale = if (t != null && config.boundingBox.enabled) {
            EffectMath.scale(typeKey, t, session.properties, config.boundingBox.minScale, config.boundingBox.maxScale)
        } else null
        setModifier(player, Attribute.SCALE, SCALE_KEY, scale?.let { it - 1.0 }, AttributeModifier.Operation.MULTIPLY_SCALAR_1)

        val health = if (t != null && config.healthScale.enabled) EffectMath.maxHealth(t, config.healthScale.maxHealth) else null
        setMaxHealth(player, health)

        startSounds(player, session)
    }

    /** 恢复玩家本体。 */
    fun clear(player: Player) {
        soundTasks.remove(player.uniqueId)?.cancel()
        setModifier(player, Attribute.SCALE, SCALE_KEY, null, AttributeModifier.Operation.MULTIPLY_SCALAR_1)
        setMaxHealth(player, null)
    }

    /**
     * 变身前检查空间：伪装后的碰撞箱会不会卡进方块。
     * 只检查变大的情况，变小总是安全的。
     */
    fun hasSpaceFor(player: Player, id: DisguiseId, properties: Map<String, String>): Boolean {
        if (!config.boundingBox.enabled || !config.boundingBox.checkSpace || id is DisguiseId.Player) return true
        val typeKey = DisguiseManager.typeKey(id)
        val t = traits[typeKey] ?: return true
        val scale = EffectMath.scale(typeKey, t, properties, config.boundingBox.minScale, config.boundingBox.maxScale)
        if (scale <= (player.getAttribute(Attribute.SCALE)?.value ?: 1.0)) return true

        val loc = player.location
        val halfWidth = EffectMath.PLAYER_WIDTH * scale / 2
        // 底部抬高一点，避免把脚下的方块算进去
        val box = BoundingBox(
            loc.x - halfWidth, loc.y + 0.01, loc.z - halfWidth,
            loc.x + halfWidth, loc.y + EffectMath.PLAYER_HEIGHT * scale, loc.z + halfWidth,
        )
        val world = player.world
        for (x in floor(box.minX).toInt() until ceil(box.maxX).toInt())
            for (y in floor(box.minY).toInt() until ceil(box.maxY).toInt())
                for (z in floor(box.minZ).toInt() until ceil(box.maxZ).toInt()) {
                    val block = world.getBlockAt(x, y, z)
                    if (!block.isPassable && block.boundingBox.overlaps(box)) return false
                }
        return true
    }

    // ---------------- 属性 ----------------

    private fun setModifier(player: Player, attribute: Attribute, key: NamespacedKey, amount: Double?, op: AttributeModifier.Operation) {
        val instance = player.getAttribute(attribute) ?: return
        instance.removeModifier(key)
        if (amount != null && amount != 0.0) instance.addTransientModifier(AttributeModifier(key, amount, op))
    }

    /** 修改最大生命值，并按比例换算当前血量（避免变成小鸡后血量溢出、变回来后只剩几颗心）。 */
    private fun setMaxHealth(player: Player, target: Double?) {
        val instance = player.getAttribute(Attribute.MAX_HEALTH) ?: return
        val oldMax = instance.value
        instance.removeModifier(HEALTH_KEY)
        if (target != null) {
            val amount = target - instance.value
            if (amount != 0.0) instance.addTransientModifier(AttributeModifier(HEALTH_KEY, amount, AttributeModifier.Operation.ADD_NUMBER))
        }
        val newMax = instance.value
        if (newMax != oldMax && !player.isDead) player.health = EffectMath.rescaleHealth(player.health, oldMax, newMax)
    }

    // ---------------- 环境音效 ----------------

    private fun startSounds(player: Player, session: DisguiseSession) {
        soundTasks.remove(player.uniqueId)?.cancel()
        val frequency = config.ambientSoundFrequency
        val id = session.disguise as? DisguiseId.Mob ?: return
        if (frequency <= 0) return
        val sound = ambientSound(id) ?: return
        val baby = session.properties["baby"] == "true"

        // 原版生物平均约 6 秒叫一次：每秒判定一次，概率 1/6 × 频率
        val task = player.scheduler.runAtFixedRate(plugin, { _ ->
            if (player.gameMode == GameMode.SPECTATOR || player.isDead || player.isInvisible) return@runAtFixedRate
            if (Random.nextDouble() >= frequency / 6.0) return@runAtFixedRate
            val pitch = (if (baby) 1.5f else 1.0f) + (Random.nextFloat() - Random.nextFloat()) * 0.2f
            player.world.playSound(player.location, sound, SoundCategory.NEUTRAL, 1f, pitch)
        }, null, 20L, 20L)
        if (task != null) soundTasks[player.uniqueId] = task
    }

    private fun ambientSound(id: DisguiseId.Mob): Sound? =
        Registry.SOUNDS.get(NamespacedKey.minecraft("entity.${id.type.key.key}.ambient"))

    companion object {
        val SCALE_KEY = NamespacedKey("yuimorph", "disguise_scale")
        val HEALTH_KEY = NamespacedKey("yuimorph", "disguise_health")
    }
}
