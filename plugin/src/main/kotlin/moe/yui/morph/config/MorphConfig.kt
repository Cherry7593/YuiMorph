package moe.yui.morph.config

import moe.yui.morph.disguise.DisguiseId
import org.bukkit.configuration.file.FileConfiguration

data class BoundingBoxConfig(
    val enabled: Boolean = true,
    val checkSpace: Boolean = true,
    val minScale: Double = 0.25,
    val maxScale: Double = 2.0,
)

data class HealthScaleConfig(
    val enabled: Boolean = true,
    val maxHealth: Double = 60.0,
)

/** 不可变配置快照，重载时整体替换，可以在任意线程安全读取。 */
data class MorphConfig(
    val language: String,
    val unlockOnKill: Boolean,
    val bannedDisguises: Set<String>,
    val disabledWorlds: Set<String>,
    val skinCacheHours: Long = 24,
    val requestTimeoutSeconds: Int = 60,
    val boundingBox: BoundingBoxConfig = BoundingBoxConfig(),
    val healthScale: HealthScaleConfig = HealthScaleConfig(),
    /** 每秒触发环境音效的概率系数，1.0 约等于原版生物的频率，0 关闭。 */
    val ambientSoundFrequency: Double = 1.0,
    val restoreOnJoin: Boolean = true,
    val unmorphOnDeath: Boolean = false,
    /** 强制伪装的 ID 字符串；null 表示不启用。 */
    val forcedDisguise: String? = null,
    /** 是否接受客户端 Mod 的增强功能。 */
    val clientModEnabled: Boolean = true,
) {
    fun isBanned(id: DisguiseId): Boolean =
        id.toString() in bannedDisguises || "${id.namespace}:@all" in bannedDisguises

    fun isWorldDisabled(worldName: String): Boolean = worldName in disabledWorlds

    companion object {
        fun load(config: FileConfiguration) = MorphConfig(
            language = config.getString("language", "zh_cn")!!,
            unlockOnKill = config.getBoolean("unlock-on-kill", true),
            bannedDisguises = config.getStringList("banned-disguises").map { it.trim() }.toSet(),
            disabledWorlds = config.getStringList("disabled-worlds").toSet(),
            skinCacheHours = config.getLong("skin-cache-hours", 24).coerceAtLeast(0),
            requestTimeoutSeconds = config.getInt("request-timeout-seconds", 60).coerceIn(5, 3600),
            boundingBox = BoundingBoxConfig(
                enabled = config.getBoolean("bounding-box.enabled", true),
                checkSpace = config.getBoolean("bounding-box.check-space", true),
                // scale 属性的合法范围是 0.0625 ~ 16
                minScale = config.getDouble("bounding-box.min-scale", 0.25).coerceIn(0.0625, 16.0),
                maxScale = config.getDouble("bounding-box.max-scale", 2.0).coerceIn(0.0625, 16.0),
            ),
            healthScale = HealthScaleConfig(
                enabled = config.getBoolean("health-scale.enabled", true),
                maxHealth = config.getDouble("health-scale.max-health", 60.0).coerceIn(1.0, 1024.0),
            ),
            ambientSoundFrequency = config.getDouble("ambient-sound-frequency", 1.0).coerceIn(0.0, 6.0),
            restoreOnJoin = config.getBoolean("restore-on-join", true),
            unmorphOnDeath = config.getBoolean("unmorph-on-death", false),
            forcedDisguise = config.getString("forced-disguise")?.trim()?.takeIf { it.isNotEmpty() },
            clientModEnabled = config.getBoolean("client-mod.enabled", true),
        )
    }
}
