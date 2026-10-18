package moe.yui.morph.render

import com.destroystokyo.paper.ClientOption
import com.github.retrooper.packetevents.protocol.entity.data.EntityData
import com.github.retrooper.packetevents.protocol.entity.data.EntityDataTypes
import com.github.retrooper.packetevents.protocol.entity.pose.EntityPose
import com.github.retrooper.packetevents.protocol.player.HumanoidArm
import org.bukkit.entity.Player
import org.bukkit.entity.Pose
import org.bukkit.inventory.MainHand

/**
 * 实体元数据索引（核对自 26.3 服务端 `EntityDataAccessor` 的定义顺序）。
 *
 * - Entity：0..7
 * - LivingEntity：8..14
 * - Avatar（玩家 / 假人）：15..16，Player：17..20
 * - Mob：15 起，之后是各生物自己的字段
 */
object EntityIndex {
    const val SHARED_FLAGS = 0
    const val AIR_SUPPLY = 1
    const val SILENT = 4
    const val NO_GRAVITY = 5
    const val POSE = 6
    const val TICKS_FROZEN = 7
    const val HEALTH = 9

    /** LivingEntity 的最后一个字段。这个索引及之前的字段所有生物通用，可以直接转发。 */
    const val LAST_COMMON = 14

    const val AVATAR_MAIN_HAND = 15
    const val AVATAR_SKIN_PARTS = 16
}

/** 根据玩家当前状态构造元数据，用于主动重新生成实体（变身 / 解除）。必须在玩家区域线程调用。 */
object EntitySnapshot {
    fun common(player: Player): MutableList<EntityData<*>> {
        var flags = 0
        if (player.fireTicks > 0 || player.isVisualFire) flags = flags or 0x01
        if (player.isSneaking) flags = flags or 0x02
        if (player.isSprinting) flags = flags or 0x08
        if (player.isSwimming) flags = flags or 0x10
        if (player.isInvisible) flags = flags or 0x20
        if (player.isGlowing) flags = flags or 0x40
        if (player.isGliding) flags = flags or 0x80

        return mutableListOf(
            EntityData(EntityIndex.SHARED_FLAGS, EntityDataTypes.BYTE, flags.toByte()),
            EntityData(EntityIndex.AIR_SUPPLY, EntityDataTypes.INT, player.remainingAir),
            EntityData(EntityIndex.SILENT, EntityDataTypes.BOOLEAN, player.isSilent),
            EntityData(EntityIndex.NO_GRAVITY, EntityDataTypes.BOOLEAN, !player.hasGravity()),
            EntityData(EntityIndex.POSE, EntityDataTypes.ENTITY_POSE, pose(player.pose)),
            EntityData(EntityIndex.TICKS_FROZEN, EntityDataTypes.INT, player.freezeTicks),
        )
    }

    /** 恢复玩家本体时需要的完整元数据（包含血量、主手、皮肤图层）。 */
    fun realPlayer(player: Player): List<EntityData<*>> = common(player).apply {
        add(EntityData(EntityIndex.HEALTH, EntityDataTypes.FLOAT, player.health.toFloat()))
        val arm = if (player.mainHand == MainHand.LEFT) HumanoidArm.LEFT else HumanoidArm.RIGHT
        add(EntityData(EntityIndex.AVATAR_MAIN_HAND, EntityDataTypes.HUMANOID_ARM, arm))
        val skinParts = player.getClientOption(ClientOption.SKIN_PARTS).raw
        add(EntityData(EntityIndex.AVATAR_SKIN_PARTS, EntityDataTypes.BYTE, skinParts.toByte()))
    }

    fun pose(pose: Pose): EntityPose = when (pose) {
        Pose.SNEAKING -> EntityPose.CROUCHING
        else -> runCatching { EntityPose.valueOf(pose.name) }.getOrDefault(EntityPose.STANDING)
    }
}
