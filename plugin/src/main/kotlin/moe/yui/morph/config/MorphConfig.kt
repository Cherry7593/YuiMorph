package moe.yui.morph.config

import moe.yui.morph.disguise.DisguiseId
import org.bukkit.configuration.file.FileConfiguration

data class BoundingBoxConfig(
    val enabled: Boolean = true,
    val checkSpace: Boolean = true,
    val minScale: Double = 0.25,
    val maxScale: Double = 2.0,
)

/** 伪装后最大生命值的处理方式。 */
enum class HealthMode(val key: String) {
    /** 不改变玩家生命值。 */
    NONE("none"),

    /** 跟随伪装生物，但不超过玩家本来的最大生命值。 */
    CAPPED("capped"),

    /** 完全跟随伪装生物。 */
    UNLIMITED("unlimited"),
    ;

    companion object {
        /** 无法识别的值按 [NONE] 处理。 */
        fun parse(value: String?): HealthMode =
            entries.firstOrNull { it.key.equals(value?.trim(), ignoreCase = true) } ?: NONE
    }
}

data class MobAggroConfig(
    val enabled: Boolean = true,
    /** 玩家主动攻击某只怪物后，它在多少秒内仍会主动追击。 */
    val provokeSeconds: Int = 30,
    val bruteIgnoresDisguise: Boolean = true,
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
    val healthMode: HealthMode = HealthMode.NONE,
    val mobAggro: MobAggroConfig = MobAggroConfig(),
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
            healthMode = HealthMode.parse(config.getString("health-scale.mode")),
            mobAggro = MobAggroConfig(
                enabled = config.getBoolean("mob-aggro.enabled", true),
                provokeSeconds = config.getInt("mob-aggro.provoke-seconds", 30).coerceIn(0, 3600),
                bruteIgnoresDisguise = config.getBoolean("mob-aggro.piglin-brute-ignore-disguise", true),
            ),
            ambientSoundFrequency = config.getDouble("ambient-sound-frequency", 1.0).coerceIn(0.0, 6.0),
            restoreOnJoin = config.getBoolean("restore-on-join", true),
            unmorphOnDeath = config.getBoolean("unmorph-on-death", false),
            forcedDisguise = config.getString("forced-disguise")?.trim()?.takeIf { it.isNotEmpty() },
            clientModEnabled = config.getBoolean("client-mod.enabled", true),
        )
    }
}
