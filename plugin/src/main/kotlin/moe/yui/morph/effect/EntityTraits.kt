package moe.yui.morph.effect

import java.io.InputStream

/** 原版尺寸和默认最大生命值。 */
data class Traits(val width: Double, val height: Double, val maxHealth: Double)

/** 来自 `entity-traits.tsv`（由 `scripts/gen-entity-layouts.py` 从服务端字节码生成）。 */
class EntityTraits private constructor(private val traits: Map<String, Traits>) {
    operator fun get(typeKey: String): Traits? = traits[typeKey]

    val size: Int get() = traits.size

    companion object {
        fun load(input: InputStream): EntityTraits = EntityTraits(
            input.bufferedReader(Charsets.UTF_8).useLines { lines ->
                lines.filter { it.isNotBlank() && !it.startsWith("#") }.associate { line ->
                    val c = line.split('\t')
                    c[0] to Traits(c[1].toDouble(), c[2].toDouble(), c[3].toDouble())
                }
            },
        )
    }
}

/** 碰撞箱和血量的计算规则（纯函数，便于测试）。 */
object EffectMath {
    const val PLAYER_HEIGHT = 1.8
    const val PLAYER_WIDTH = 0.6

    /** 幼体的尺寸系数（原版 AgeableMob 的 baby 缩放）。 */
    private const val BABY_FACTOR = 0.5

    /**
     * 伪装后的玩家缩放比例：按高度对齐，这样能不能钻过缝隙和原生物一致。
     * 史莱姆 / 岩浆怪的尺寸还要乘以 `size` 属性。
     */
    fun scale(typeKey: String, traits: Traits, properties: Map<String, String>, min: Double, max: Double): Double {
        var height = traits.height
        if (properties["baby"] == "true") height *= BABY_FACTOR
        if (typeKey in CUBE_MOBS) height *= properties["size"]?.toIntOrNull()?.coerceAtLeast(1) ?: 1
        return (height / PLAYER_HEIGHT).coerceIn(min, max)
    }

    /** 尺寸由 size 属性决定的生物（表里是 size=1 时的尺寸）。 */
    private val CUBE_MOBS = setOf("minecraft:slime", "minecraft:magma_cube")

    /** 伪装后的最大生命值：生物的默认值，不超过 [cap]。 */
    fun maxHealth(traits: Traits, cap: Double): Double = traits.maxHealth.coerceIn(1.0, cap)

    /** 最大生命值变化时按比例换算当前血量，至少保留半颗心避免直接死亡。 */
    fun rescaleHealth(health: Double, oldMax: Double, newMax: Double): Double {
        if (health <= 0 || oldMax <= 0) return health
        return (health / oldMax * newMax).coerceIn(0.5, newMax)
    }
}
