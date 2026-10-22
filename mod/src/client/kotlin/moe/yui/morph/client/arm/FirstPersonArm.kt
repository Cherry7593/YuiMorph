package moe.yui.morph.client.arm

import com.mojang.blaze3d.vertex.PoseStack
import moe.yui.morph.client.MorphClientState
import moe.yui.morph.client.storage.ClientSettings
import net.minecraft.client.DeltaTracker
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.SubmitNodeCollector
import net.minecraft.client.renderer.entity.state.AvatarRenderState
import net.minecraft.client.renderer.state.level.PlayerRenderState
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.HumanoidArm
import org.slf4j.LoggerFactory

/**
 * 第一人称伪装手臂。设计见 docs/FIRST_PERSON_ARM.md。
 *
 * - 提取阶段（[extract]）：把代理实体的 render state 挂到本地玩家的 [PlayerRenderState] 上
 * - 提交阶段（[render]）：原版要画玩家手的地方（空主手、拿地图）换成伪装的手
 *   - 玩家伪装：伪装的 AvatarRenderer + 伪装皮肤（粗 / 细手臂自动选）
 *   - 生物伪装：[MobArm]
 */
object FirstPersonArm {
    private val logger = LoggerFactory.getLogger("YuiMorph")

    /** 测试用：代替代理实体作为手臂来源。 */
    @JvmStatic
    var sourceOverride: Entity? = null

    /** 画手臂出过错的生物，之后当作没有手臂（避免每帧刷错误）。 */
    private val broken = HashSet<EntityType<*>>()

    @JvmStatic
    fun extract(state: PlayerRenderState, deltaTracker: DeltaTracker) {
        val holder = state as ProxyStateHolder
        holder.yuimorph_setProxyState(null)
        if (!ClientSettings.firstPersonArm || !state.hasPlayer) return
        val mc = Minecraft.getInstance()
        if (!mc.options.cameraType.isFirstPerson) return
        val level = mc.level ?: return
        val source = sourceOverride ?: MorphClientState.proxy() ?: return
        if (source.isRemoved) return
        val partialTick = deltaTracker.getGameTimeDeltaPartialTick(!level.tickRateManager().isEntityFrozen(source))
        holder.yuimorph_setProxyState(mc.entityRenderDispatcher.extractEntity(source, partialTick))
    }

    /** 由 Mixin 调用。返回 true 表示已经处理（画了伪装的手，或者按设置不画），false 表示照常画玩家的手。 */
    @JvmStatic
    fun render(playerState: PlayerRenderState, arm: HumanoidArm, poseStack: PoseStack, collector: SubmitNodeCollector, light: Int): Boolean {
        val state = (playerState as ProxyStateHolder).yuimorph_getProxyState() ?: return false

        if (state is AvatarRenderState) {
            val renderer = Minecraft.getInstance().entityRenderDispatcher.getRenderer(state)
            val skin = state.skin.body().texturePath()
            if (arm == HumanoidArm.RIGHT) renderer.renderRightHand(poseStack, collector, light, skin, state.showRightSleeve)
            else renderer.renderLeftHand(poseStack, collector, light, skin, state.showLeftSleeve)
            return true
        }

        val type = state.entityType
        if (type !in broken) {
            try {
                if (MobArm.submit(state, arm, poseStack, collector)) return true
            } catch (e: Exception) {
                broken += type
                logger.warn("无法画 {} 的第一人称手臂，之后不再尝试", EntityType.getKey(type), e)
            }
        }
        return ClientSettings.noArmFallback == ClientSettings.NoArmFallback.HIDE
    }

    fun clear() {
        MobArm.clear()
    }
}
