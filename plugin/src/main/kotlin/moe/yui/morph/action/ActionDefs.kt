package moe.yui.morph.action

import moe.yui.morph.disguise.property.DataKind

/**
 * 动作修改的字段。[field] 是实体布局里的字段名，[default] 是动作结束后恢复的值；
 * `default = null` 表示恢复成玩家当前的实时值（只用于姿态）。
 */
data class ActionTarget(val field: String, val kind: DataKind, val default: Any?) {
    companion object {
        /** 所有实体通用的姿态字段。 */
        val POSE = ActionTarget("Entity.DATA_POSE", DataKind.POSE, null)
    }
}

sealed interface ActionStep {
    data class Set(val target: ActionTarget, val value: Any) : ActionStep

    /** 在伪装者位置播放声音，所有人都能听到。 */
    data class Sound(val key: String, val volume: Float = 1f) : ActionStep

    data class Wait(val ticks: Long) : ActionStep

    /** 把本动作改过的字段全部恢复为默认值。 */
    data object Reset : ActionStep
}

/**
 * 一个动作。
 * - [persistent]：执行完后保持状态（例如坐下），直到被停止、换动作、或者 [cancelOnMove] 时玩家移动
 * - [stopSteps]：停止持续动作时执行的步骤（例如犰狳先播放展开动画再恢复）；为空时直接 [ActionStep.Reset]
 */
class ActionDef(
    val name: String,
    val steps: List<ActionStep>,
    val persistent: Boolean = false,
    val cancelOnMove: Boolean = false,
    val stopSteps: List<ActionStep> = listOf(ActionStep.Reset),
) {
    /** 本动作会修改的字段。 */
    val targets: Set<ActionTarget> = (steps + stopSteps).filterIsInstance<ActionStep.Set>().mapTo(LinkedHashSet()) { it.target }
}

/** 各伪装可用的动作（参考 FeatherMorph，字段和时长核对自 26.3）。 */
object ActionDefs {
    const val STOP = "stop"

    private fun set(field: String, kind: DataKind, default: Any, value: Any) = ActionStep.Set(ActionTarget(field, kind, default), value)
    private fun pose(value: String) = ActionStep.Set(ActionTarget.POSE, value)
    private fun sound(key: String, volume: Float = 1f) = ActionStep.Sound(key, volume)
    private fun wait(ticks: Long) = ActionStep.Wait(ticks)
    private val reset = ActionStep.Reset

    /** 坐下 / 躺下这类：保持不动，移动时取消。 */
    private fun rest(name: String, vararg steps: ActionStep) = ActionDef(name, steps.toList(), persistent = true, cancelOnMove = true)

    private fun tameableSit() = rest("sit", set("TamableAnimal.DATA_FLAGS_ID", DataKind.BYTE, 0.toByte(), 0x01.toByte()))

    private val byType: Map<String, List<ActionDef>> = mapOf(
        "minecraft:fox" to listOf(
            rest("sit", set("Fox.DATA_FLAGS_ID", DataKind.BYTE, 0.toByte(), 0x01.toByte())),
            rest("sleep", set("Fox.DATA_FLAGS_ID", DataKind.BYTE, 0.toByte(), 0x20.toByte())),
        ),
        "minecraft:cat" to listOf(
            tameableSit(),
            rest("lie", set("Cat.IS_LYING", DataKind.BOOLEAN, false, true)),
        ),
        "minecraft:wolf" to listOf(tameableSit()),
        "minecraft:parrot" to listOf(tameableSit()),
        "minecraft:panda" to listOf(
            rest("sit", set("Panda.DATA_ID_FLAGS", DataKind.BYTE, 0.toByte(), 0x08.toByte())),
            rest("lie", set("Panda.DATA_ID_FLAGS", DataKind.BYTE, 0.toByte(), 0x10.toByte())),
        ),
        "minecraft:shulker" to listOf(
            ActionDef(
                "peek",
                listOf(
                    set("Shulker.DATA_PEEK_ID", DataKind.BYTE, 0.toByte(), 30.toByte()), sound("entity.shulker.open"),
                    wait(40), sound("entity.shulker.close"), reset,
                ),
            ),
            ActionDef(
                "open",
                listOf(
                    set("Shulker.DATA_PEEK_ID", DataKind.BYTE, 0.toByte(), 100.toByte()), sound("entity.shulker.open"),
                    wait(100), sound("entity.shulker.close"), reset,
                ),
            ),
        ),
        "minecraft:allay" to listOf(
            ActionDef("dance", listOf(set("Allay.DATA_DANCING", DataKind.BOOLEAN, false, true)), persistent = true),
        ),
        "minecraft:piglin" to listOf(
            ActionDef(
                "dance",
                listOf(set("Piglin.DATA_IS_DANCING", DataKind.BOOLEAN, false, true), sound("entity.piglin.celebrate")),
                persistent = true,
            ),
        ),
        "minecraft:armadillo" to run {
            fun state(v: String) = set("Armadillo.ARMADILLO_STATE", DataKind.ARMADILLO_STATE, "IDLE", v)
            listOf(
                ActionDef(
                    "roll",
                    listOf(state("ROLLING"), sound("entity.armadillo.roll"), wait(10), state("SCARED"), sound("entity.armadillo.land")),
                    persistent = true,
                    cancelOnMove = true,
                    stopSteps = listOf(state("UNROLLING"), sound("entity.armadillo.unroll_start"), wait(30), sound("entity.armadillo.unroll_finish"), reset),
                ),
            )
        },
        "minecraft:sniffer" to run {
            fun state(v: String) = set("Sniffer.DATA_STATE", DataKind.SNIFFER_STATE, "IDLING", v)
            listOf(
                ActionDef("sniff", listOf(state("SNIFFING"), sound("entity.sniffer.sniffing"), wait(40), reset)),
                ActionDef("dig", listOf(state("DIGGING"), sound("entity.sniffer.digging"), wait(120), reset)),
            )
        },
        "minecraft:frog" to listOf(
            ActionDef("eat", listOf(pose("USING_TONGUE"), sound("entity.frog.eat"), wait(10), reset)),
            ActionDef("croak", listOf(pose("CROAKING"), sound("entity.frog.ambient"), wait(60), reset)),
        ),
        "minecraft:warden" to listOf(
            // 原版 Roar 行为：吼叫动画开始 25 tick 后才出声，共 84 tick
            ActionDef("roar", listOf(pose("ROARING"), wait(25), sound("entity.warden.roar", 3f), wait(59), reset)),
            ActionDef("sniff", listOf(pose("SNIFFING"), sound("entity.warden.sniff", 5f), wait(34), reset)),
        ),
        "minecraft:pufferfish" to run {
            fun puff(v: Int) = set("Pufferfish.PUFF_STATE", DataKind.INT, 0, v)
            listOf(
                ActionDef(
                    "inflate",
                    listOf(puff(1), sound("entity.puffer_fish.blow_up"), wait(5), puff(2)),
                    persistent = true,
                    stopSteps = listOf(sound("entity.puffer_fish.blow_out"), reset),
                ),
            )
        },
        "minecraft:creaking" to listOf(
            ActionDef(
                "activate",
                listOf(set("Creaking.IS_ACTIVE", DataKind.BOOLEAN, false, true), sound("entity.creaking.activate")),
                persistent = true,
                stopSteps = listOf(sound("entity.creaking.freeze"), reset),
            ),
        ),
        "minecraft:player" to listOf(
            rest("lay", pose("SLEEPING")),
            ActionDef("crawl", listOf(pose("SWIMMING")), persistent = true),
        ),
    )

    /** 该伪装可用的动作；有持续动作时自动附带 [STOP]。 */
    fun forType(typeKey: String): List<ActionDef> = byType[typeKey].orEmpty()

    fun names(typeKey: String): List<String> {
        val defs = forType(typeKey)
        return if (defs.any { it.persistent }) defs.map { it.name } + STOP else defs.map { it.name }
    }

    fun find(typeKey: String, name: String): ActionDef? = forType(typeKey).firstOrNull { it.name == name.lowercase() }

    /** 有定义的实体类型（测试用）。 */
    val types: Set<String> get() = byType.keys
}
