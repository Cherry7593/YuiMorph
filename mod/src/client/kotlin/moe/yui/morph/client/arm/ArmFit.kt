package moe.yui.morph.client.arm

import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.math.Axis
import net.minecraft.client.model.geom.ModelPart
import net.minecraft.world.entity.HumanoidArm
import org.joml.Quaternionf
import org.joml.Vector3f
import java.util.WeakHashMap

/**
 * 把任意部件摆到玩家手臂的位置。
 *
 * 玩家右臂（粗手臂）：枢轴 (-5, 2, 0)，方块相对枢轴 x∈[-3,1] y∈[-2,10] z∈[-2,2]（像素，y 向下）。
 * 先算出部件（含它自己的枢轴、旋转和子部件）在初始姿势下的包围盒，等比缩放到手臂长度（宽度和放大倍数有上下限），
 * 再把包围盒的「底面中心」（末端）对到手的位置——第一人称只看得到手臂的下半截，
 * 太短的部件（幼体的腿）对齐肩膀的话会完全在画面外。
 */
object ArmFit {
    private const val PX = 1f / 16f

    /** 目标长度：玩家手臂 12 像素。 */
    private const val TARGET_LENGTH = 12 * PX

    /** 缩放后最粗 / 最细（像素）：猪腿这类又短又粗的不至于糊满屏幕，铁傀儡这类细长的不至于变成竹竿。 */
    private const val MAX_WIDTH = 6 * PX
    private const val MIN_WIDTH = 2.5f * PX

    /** 最多放大两倍：悦灵、幼体这类小部件保持小巧。 */
    private const val MAX_SCALE = 2f

    /** 拟合结果：部件包围盒的锚点（底面中心）和缩放。 */
    private class Fit(val anchor: Vector3f, val scale: Float)

    /** 按部件和左右手缓存（左手的预旋转是镜像的）。部件随模型一起在资源重载后被回收。 */
    private val cache = WeakHashMap<ModelPart, Array<Fit?>>()
    private val EMPTY = Fit(Vector3f(), 0f)

    /**
     * 把 [poseStack] 从原版「准备画手臂」的状态变换到「直接提交 [part] 即可」的状态。
     * 部件没有任何方块时返回 false。调用前部件应该已经重置到初始姿势。
     */
    fun apply(poseStack: PoseStack, part: ModelPart, arm: HumanoidArm, profile: ArmProfile?): Boolean {
        val left = arm == HumanoidArm.LEFT
        val sign = if (left) -1f else 1f
        val rotation = profile?.rotation?.let { r -> rotation(r, left) }

        val slots = cache.getOrPut(part) { arrayOfNulls(2) }
        val index = if (left) 1 else 0
        val fit = slots[index] ?: (compute(part, rotation) ?: EMPTY).also { slots[index] = it }
        if (fit === EMPTY) return false

        // 和 AvatarRenderer.renderHand 一样：肩膀枢轴 + 手臂向外张开 0.1 弧度
        poseStack.translate(sign * -5 * PX, 2 * PX, 0f)
        poseStack.rotate(Axis.ZP, sign * 0.1f)
        profile?.offset?.let { poseStack.translate(sign * it.x * PX, it.y * PX, it.z * PX) }
        // 部件底面中心 → 手臂底面中心 (x = -1, y = 10, z = 0)
        poseStack.translate(sign * -1 * PX, 10 * PX, 0f)
        val s = fit.scale * (profile?.scale ?: 1f)
        poseStack.scale(s, s, s)
        poseStack.translate(-fit.anchor.x, -fit.anchor.y, -fit.anchor.z)
        rotation?.let { poseStack.rotate(it) }
        return true
    }

    private fun compute(part: ModelPart, rotation: Quaternionf?): Fit? {
        val min = Vector3f(Float.POSITIVE_INFINITY)
        val max = Vector3f(Float.NEGATIVE_INFINITY)
        val pose = PoseStack()
        rotation?.let { pose.rotate(it) }
        part.getExtentsForGui(pose) { v ->
            min.min(v)
            max.max(v)
        }
        if (min.x > max.x) return null

        val length = max.y - min.y
        val width = maxOf(max.x - min.x, max.z - min.z)
        if (length <= 1e-4f && width <= 1e-4f) return null

        var scale = if (length > 1e-4f) TARGET_LENGTH / length else 1f
        if (width * scale > MAX_WIDTH) scale = MAX_WIDTH / width
        if (width > 1e-4f && width * scale < MIN_WIDTH) scale = MIN_WIDTH / width
        scale = scale.coerceAtMost(MAX_SCALE)
        return Fit(Vector3f((min.x + max.x) / 2, max.y, (min.z + max.z) / 2), scale)
    }

    /** 角度 → 四元数，顺序和 ModelPart 一致；左手绕 Y、Z 的角度取反（沿 X 轴镜像）。 */
    private fun rotation(degrees: Vector3f, left: Boolean): Quaternionf {
        val sign = if (left) -1f else 1f
        return Quaternionf().rotationZYX(
            Math.toRadians(sign * degrees.z.toDouble()).toFloat(),
            Math.toRadians(sign * degrees.y.toDouble()).toFloat(),
            Math.toRadians(degrees.x.toDouble()).toFloat(),
        )
    }
}
