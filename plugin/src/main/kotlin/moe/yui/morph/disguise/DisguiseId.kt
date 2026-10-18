package moe.yui.morph.disguise

import net.kyori.adventure.text.Component
import org.bukkit.NamespacedKey
import org.bukkit.Registry
import org.bukkit.entity.EntityType

/**
 * 伪装标识。字符串形式：
 * - 生物：`minecraft:pig`（解析时可以省略 `minecraft:`）
 * - 玩家：`player:Notch`
 */
sealed interface DisguiseId {
    val namespace: String

    /** 用于权限节点的后半段，例如 `minecraft.pig`、`player.Notch`。 */
    val permissionSuffix: String

    fun displayName(): Component

    data class Mob(val type: EntityType) : DisguiseId {
        override val namespace: String get() = type.key.namespace
        override val permissionSuffix: String get() = "${type.key.namespace}.${type.key.key}"
        override fun displayName(): Component = Component.translatable(type.translationKey())
        override fun toString(): String = type.key.asString()
    }

    data class Player(val name: String) : DisguiseId {
        override val namespace: String get() = NAMESPACE
        override val permissionSuffix: String get() = "player.$name"
        override fun displayName(): Component = Component.text(name)
        override fun toString(): String = "$NAMESPACE:$name"

        companion object {
            const val NAMESPACE = "player"
        }
    }

    companion object {
        private val PLAYER_NAME = Regex("^[A-Za-z0-9_]{1,16}$")

        /** 解析失败或该实体不能用于伪装时返回 null。 */
        fun parse(raw: String): DisguiseId? {
            val text = raw.trim()
            if (text.startsWith("${Player.NAMESPACE}:")) {
                val name = text.substring(Player.NAMESPACE.length + 1)
                return if (PLAYER_NAME.matches(name)) Player(name) else null
            }
            val key = NamespacedKey.fromString(text.lowercase()) ?: return null
            val type = Registry.ENTITY_TYPE.get(key) ?: return null
            return of(type)
        }

        fun of(type: EntityType): Mob? = if (isDisguisable(type)) Mob(type) else null

        /** 可以伪装的实体：除玩家外所有可生成的生物。 */
        fun isDisguisable(type: EntityType): Boolean =
            type != EntityType.PLAYER && type.isAlive && type.isSpawnable

        /** 所有可伪装的生物，用于补全和管理员。 */
        fun allMobs(): List<Mob> = Registry.ENTITY_TYPE.filter(::isDisguisable).map(::Mob).sortedBy { it.toString() }
    }
}
