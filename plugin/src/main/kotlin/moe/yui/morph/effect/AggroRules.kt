package moe.yui.morph.effect

/**
 * 怪物仇恨规则：伪装成生物后，怪物只会主动攻击原版里它本来就会攻击的生物。
 *
 * 只覆盖会主动索敌玩家的敌对怪物；表外的生物（中立生物、驯服的狼、监守者等）一律交给原版处理，
 * 避免打断它们被激怒后的追击。关系依据 26.3 各怪物的 `NearestAttackableTargetGoal` 目标类整理，
 * 设计见 `docs/MOB_AGGRO.md`。
 *
 * 所有键都是完整的实体 ID（例如 `minecraft:zombie`）。纯函数，便于测试。
 */
object AggroRules {
    private fun keys(vararg names: String): Set<String> = names.mapTo(HashSet()) { "minecraft:$it" }

    private val VILLAGERS = keys("villager", "wandering_trader")
    private val IRON_GOLEM = keys("iron_golem")
    private val TURTLE = keys("turtle")

    private val ZOMBIE_TARGETS = VILLAGERS + IRON_GOLEM + TURTLE
    private val SKELETON_TARGETS = IRON_GOLEM + TURTLE
    private val RAIDER_TARGETS = VILLAGERS + IRON_GOLEM

    /** 来源怪物 → 会主动攻击的伪装（不含玩家伪装，玩家伪装始终按原版）。 */
    private val TARGETS: Map<String, Set<String>> = buildMap {
        fun put(sources: Set<String>, targets: Set<String>) = sources.forEach { put(it, targets) }

        put(keys("zombie", "husk", "zombie_villager"), ZOMBIE_TARGETS)
        put(keys("drowned"), ZOMBIE_TARGETS + keys("axolotl"))
        put(keys("skeleton", "stray", "bogged", "parched"), SKELETON_TARGETS)
        put(keys("wither_skeleton"), SKELETON_TARGETS + keys("piglin", "piglin_brute"))
        put(keys("spider", "cave_spider", "slime", "magma_cube"), IRON_GOLEM)
        put(keys("pillager", "vindicator", "evoker", "illusioner", "ravager"), RAIDER_TARGETS)
        put(keys("guardian", "elder_guardian"), keys("squid", "glow_squid", "axolotl"))
        // 猪灵因金子而激怒不处理：伪装期间不会因此被攻击；猪灵蛮兵是否接管由配置决定
        put(keys("piglin", "piglin_brute"), keys("wither_skeleton", "wither"))
        put(keys("enderman"), keys("endermite"))
        // 只会主动攻击玩家
        put(
            keys(
                "creeper", "blaze", "ghast", "phantom", "shulker", "silverfish", "endermite",
                "witch", "vex", "breeze", "hoglin", "creaking",
            ),
            emptySet(),
        )
    }

    const val WITHER = "minecraft:wither"
    const val ZOGLIN = "minecraft:zoglin"
    const val PIGLIN_BRUTE = "minecraft:piglin_brute"

    /** 僵尸疣猪兽不会攻击的生物（沿用 FeatherMorph）。 */
    private val ZOGLIN_IGNORES = keys("creeper", "ghast", "zoglin")

    /** 这种怪物的主动索敌是否由本规则接管。 */
    fun handles(source: String): Boolean = source in TARGETS || source == WITHER || source == ZOGLIN

    /**
     * 怪物 [source] 是否会主动攻击伪装成 [disguise] 的玩家。
     * [witherFriend] 表示伪装生物是否属于 `#wither_friends`（亡灵），只有凋灵会用到。
     * 不由本规则接管的怪物返回 true。
     */
    fun shouldTarget(source: String, disguise: String, witherFriend: Boolean = false): Boolean = when (source) {
        // 原版凋灵攻击不属于 #wither_friends 且可被攻击的生物（盔甲架不可被攻击）
        WITHER -> !witherFriend && disguise != "minecraft:armor_stand"
        ZOGLIN -> disguise !in ZOGLIN_IGNORES
        else -> TARGETS[source]?.contains(disguise) ?: true
    }
}
