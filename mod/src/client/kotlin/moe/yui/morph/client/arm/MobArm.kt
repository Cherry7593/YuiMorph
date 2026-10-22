package moe.yui.morph.client.arm

import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.Minecraft
import net.minecraft.client.model.Model
import net.minecraft.client.model.geom.ModelPart
import net.minecraft.client.renderer.SubmitNodeCollector
import net.minecraft.client.renderer.entity.EntityRenderer
import net.minecraft.client.renderer.entity.LivingEntityRenderer
import net.minecraft.client.renderer.entity.state.EntityRenderState
import net.minecraft.client.renderer.state.level.CameraRenderState
import net.minecraft.client.renderer.texture.OverlayTexture
import net.minecraft.world.entity.HumanoidArm
import java.util.WeakHashMap
import java.util.function.Function

/**
 * 生物伪装的第一人称手臂。
 *
 * 1. 捕获：让伪装的 renderer 照常 submit 一遍，交给 [CapturingCollector]。
 *    这样拿到的模型就是它真正会用的（26.x 的变种模型、幼体模型都是在 submit 里才选的），
 *    贴图、染色、图层（羊毛、职业服、外层）也都是原版决定的
 * 2. 选部件：手调表 → 名字（right_arm → right_front_leg → …）→ 没有
 * 3. 只把这个部件画出来：主体模型和每个有同名部件的图层模型各画一次，位置由 [ArmFit] 计算
 */
object MobArm {
    private val camera = CameraRenderState()

    /** 同一帧左右手共用一次捕获（同一个 render state 对象 = 同一帧）。 */
    private var capturedFor: EntityRenderState? = null
    private var captured: Capture? = null

    private class Capture(val base: Model<*>?, val entries: List<CapturingCollector.Entry>)

    /** 画出来了返回 true；这种生物没有能当手臂的部件时返回 false。 */
    fun submit(state: EntityRenderState, arm: HumanoidArm, poseStack: PoseStack, collector: SubmitNodeCollector): Boolean {
        val profile = ArmProfiles[state.entityType]
        if (profile?.none == true) return false

        val capture = capture(state)
        val base = capture.base ?: return false
        val name = partName(base, profile, arm) ?: return false
        val basePart = lookup(base).apply(name) ?: return false
        resetPose(basePart, profile?.straighten == true)

        poseStack.pushPose()
        try {
            if (!ArmFit.apply(poseStack, basePart, arm, profile)) return false
            for (entry in capture.entries) {
                val part = if (entry.model === base) basePart else lookup(entry.model).apply(name) ?: continue
                if (part !== basePart) {
                    // 图层模型的初始姿势不一定和主体一致（例如流浪者的衣服），原版也是让图层跟着主体的姿势走
                    resetPose(part, profile?.straighten == true)
                    part.loadPose(basePart.storePose())
                }
                collector.submitModelPart(part, poseStack, entry.renderType, state.lightCoords, OverlayTexture.NO_OVERLAY, entry.uvMapping, entry.tint)
            }
        } finally {
            poseStack.popPose()
        }
        return true
    }

    private fun capture(state: EntityRenderState): Capture {
        if (capturedFor === state) captured?.let { return it }
        @Suppress("UNCHECKED_CAST")
        val renderer = Minecraft.getInstance().entityRenderDispatcher.getRenderer(state) as EntityRenderer<*, EntityRenderState>
        val collector = CapturingCollector()
        renderer.submit(state, PoseStack(), collector, camera)
        // LivingEntityRenderer 在 submit 里把 model 切换成了这次实际用的（变种 / 幼体）
        val base = (renderer as? LivingEntityRenderer<*, *, *>)?.model ?: collector.entries.firstOrNull()?.model()
        return Capture(base, collector.entries).also {
            capturedFor = state
            captured = it
        }
    }

    // ---------------- 选部件 ----------------

    private val lookups = WeakHashMap<Model<*>, Function<String, ModelPart?>>()

    private fun lookup(model: Model<*>): Function<String, ModelPart?> = lookups.getOrPut(model) { model.root().createPartLookup() }

    /** 按优先级排列的候选部件名（右手），左手把 right 换成 left。 */
    private val CANDIDATES = listOf("right_arm", "right_front_leg", "right_leg", "right_hind_leg")

    private fun partName(model: Model<*>, profile: ArmProfile?, arm: HumanoidArm): String? {
        val lookup = lookup(model)
        val left = arm == HumanoidArm.LEFT
        profile?.part?.let { right ->
            if (!left) return right.takeIf { lookup.apply(it) != null }
            val mirrored = right.replace("right", "left")
            return if (lookup.apply(mirrored) != null) mirrored else right.takeIf { lookup.apply(it) != null }
        }
        // 先找这一侧，没有的话用另一侧的（例如只有一条的部件）
        val sides = if (left) listOf("left", "right") else listOf("right")
        for (side in sides) {
            for (candidate in CANDIDATES) {
                val name = candidate.replace("right", side)
                if (lookup.apply(name) != null) return name
            }
        }
        return null
    }

    /**
     * 和原版玩家手一样不跟随动作（例如僵尸平举），回到模型定义的初始姿势。
     * [straighten] 时再去掉自带的旋转（初始姿势就是斜的、横的部件）。
     */
    private fun resetPose(part: ModelPart, straighten: Boolean) {
        for (p in part.allParts) {
            p.resetPose()
            if (straighten) p.setRotation(0f, 0f, 0f)
        }
        part.visible = true
    }

    /** 换世界 / 断开时释放引用。 */
    fun clear() {
        capturedFor = null
        captured = null
    }
}
