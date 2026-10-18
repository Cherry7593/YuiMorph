package moe.yui.morph.config

import moe.yui.morph.disguise.DisguiseId
import org.bukkit.configuration.file.FileConfiguration

/** 不可变配置快照，重载时整体替换，可以在任意线程安全读取。 */
data class MorphConfig(
    val language: String,
    val unlockOnKill: Boolean,
    val bannedDisguises: Set<String>,
    val disabledWorlds: Set<String>,
    val skinCacheHours: Long = 24,
    val requestTimeoutSeconds: Int = 60,
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
        )
    }
}
