package moe.yui.morph.client.arm

import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.EntityTypes
import org.joml.Vector3f

/**
 * 某种生物的第一人称手臂怎么画。不在表里的生物按部件名字自动找，再自动拟合位置。
 * 所有数值都按**右手**写，左手自动镜像。
 *
 * @property part 用作手臂的部件名（右手）。左手把名字里的 right 换成 left，模型里没有时两只手用同一个部件。
 *                为 null 时按名字自动找。
 * @property none 这种生物没有能当手臂的部件，不画。
 * @property straighten 去掉部件（和子部件）自带的旋转，用于初始姿势就是斜的部件（蜘蛛腿）。
 * @property rotation 拟合之前先把部件转一下（角度，按 Z→Y→X 顺序，和 ModelPart 一致），
 *                    用于默认姿势是横着、斜着的部件。拟合按转完后的包围盒计算。
 * @property offset 拟合之后的平移（像素，玩家手臂坐标：x 向外为负、y 向下、z 向前为负）。
 * @property scale 拟合之后再乘的缩放。
 */
data class ArmProfile(
    val part: String? = null,
    val none: Boolean = false,
    val straighten: Boolean = false,
    val rotation: Vector3f? = null,
    val offset: Vector3f? = null,
    val scale: Float = 1f,
)

/**
 * 自动选部件 + 自动拟合搞不定的生物。调整后用手臂画廊检查：
 * `./gradlew :mod:runClientGameTest -ParmGallery=spider,frog`（见 FirstPersonArmGameTest）。
 */
object ArmProfiles {
    private val profiles: Map<EntityType<*>, ArmProfile> = buildMap {
        // 翼臂比爪子更像手
        put(EntityTypes.CHICKEN, ArmProfile(part = "right_wing"))
        put(EntityTypes.PARROT, ArmProfile(part = "right_wing"))
        // 腿是零厚度的面片，当手臂很怪
        put(EntityTypes.BEE, ArmProfile(none = true))
        put(EntityTypes.AXOLOTL, ArmProfile(none = true))
        // 没有左右之分的部件，两只手用同一个
        put(EntityTypes.BLAZE, ArmProfile(part = "part0"))
        put(EntityTypes.BREEZE, ArmProfile(part = "rod_1"))
        put(EntityTypes.GHAST, ArmProfile(part = "tentacle0"))
        put(EntityTypes.HAPPY_GHAST, ArmProfile(part = "tentacle0"))
        put(EntityTypes.SQUID, ArmProfile(part = "tentacle0"))
        put(EntityTypes.GLOW_SQUID, ArmProfile(part = "tentacle0"))
        // 初始姿势是横着的：去掉自带旋转后沿 -X（右）伸出，绕 Z 转 -90° 变成沿 +Y（向下）
        val sideways = ArmProfile(straighten = true, rotation = Vector3f(0f, 0f, -90f))
        put(EntityTypes.SPIDER, sideways)
        put(EntityTypes.CAVE_SPIDER, sideways)
        put(EntityTypes.TURTLE, sideways)
        // 雪傀儡的手臂去掉旋转后沿 +X 伸出
        put(EntityTypes.SNOW_GOLEM, ArmProfile(straighten = true, rotation = Vector3f(0f, 0f, 90f)))
        // 青蛙的前肢沿 -Z（向前）伸出，绕 X 转 90° 变成向下
        put(EntityTypes.FROG, ArmProfile(rotation = Vector3f(90f, 0f, 0f)))
        // 末影龙：用小腿 + 爪子，整条腿太长
        put(EntityTypes.ENDER_DRAGON, ArmProfile(part = "right_front_leg_tip", straighten = true))
        // 铁傀儡的手臂很长，按长度缩小后太细
        put(EntityTypes.IRON_GOLEM, ArmProfile(scale = 1.5f))
    }

    operator fun get(type: EntityType<*>): ArmProfile? = profiles[type]
}
