package moe.yui.morph.client.debug

import moe.yui.morph.client.MorphClientState
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.Entity
import org.slf4j.LoggerFactory

/**
 * 临时诊断：定位「伪装后第一人称疾跑，松开按键仍缓慢漂移」。
 *
 * 检测条件：伪装中（有代理实体）、没有移动输入、在地面上，水平速度仍然连续 20 tick 大于阈值。
 * 检测到后：
 *   1. 记录一份现场快照（玩家、代理实体、附近实体）
 *   2. 下一 tick 记录所有修改本地玩家速度的调用栈（去重计数）
 * 结果写进客户端 latest.log，前缀「YuiMorph-诊断」。平时只做一次布尔判断，没有额外开销。
 */
object DriftDiagnostics {
    private val logger = LoggerFactory.getLogger("YuiMorph-诊断")

    private var anomalyTicks = 0
    private var cooldown = 0

    /** 只在记录调用栈的那一 tick 为 true。Mixin 先检查这个标志再做任何事。 */
    @JvmStatic
    @Volatile
    var tracing = false
        private set

    private val traces = LinkedHashMap<String, Int>()

    /** 由 Mixin 调用：本地玩家的速度被修改。 */
    @JvmStatic
    fun onVelocityChange(entity: Entity, kind: String, x: Double, y: Double, z: Double) {
        if (!tracing || entity !== Minecraft.getInstance().player) return
        val frames = Throwable().stackTrace.drop(2).take(16).joinToString("\n      at ") { "${it.className}.${it.methodName}:${it.lineNumber}" }
        val key = "$kind(%.4f, %.4f, %.4f)\n      at %s".format(x, y, z, frames)
        traces.merge(key, 1, Int::plus)
    }

    fun tick(mc: Minecraft) {
        val player = mc.player ?: return
        if (tracing) {
            tracing = false
            logger.warn("—— 下一 tick 修改本地玩家速度的调用（共 {} 种）——", traces.size)
            traces.forEach { (k, n) -> logger.warn("  ×{} {}", n, k) }
            traces.clear()
            player.sendOverlayMessage(Component.literal("[YuiMorph] 已记录漂移诊断，请把客户端 latest.log 发给开发者"))
            cooldown = 200
            return
        }
        if (cooldown > 0) {
            cooldown--
            return
        }
        val proxy = MorphClientState.proxy()
        if (proxy == null) {
            anomalyTicks = 0
            return
        }

        val move = player.input.moveVector
        val keys = player.input.keyPresses
        val noInput = move.x == 0f && move.y == 0f && !keys.jump()
        val speed = player.deltaMovement.horizontalDistance()
        anomalyTicks = if (noInput && player.onGround() && speed > 0.01) anomalyTicks + 1 else 0
        if (anomalyTicks < 20) return

        anomalyTicks = 0
        logger.warn("检测到漂移：连续 20 tick 无输入但水平速度 > 0.01")
        logger.warn(
            "  玩家 id={} pos=({}, {}, {}) old=({}, {}, {}) delta={} onGround={} sprinting={} passenger={} vehicle={}",
            player.id, player.x, player.y, player.z, player.xo, player.yo, player.zo, player.deltaMovement,
            player.onGround(), player.isSprinting, player.isPassenger, player.vehicle,
        )
        logger.warn(
            "  代理 id={} type={} removed={} pos=({}, {}, {}) old=({}, {}, {}) delta={} tickCount={}",
            proxy.id, proxy.type, proxy.isRemoved, proxy.x, proxy.y, proxy.z, proxy.xo, proxy.yo, proxy.zo,
            proxy.deltaMovement, proxy.tickCount,
        )
        logger.warn("  相机 detached={} 伪装={}", mc.gameRenderer.mainCamera().isDetached, MorphClientState.self)
        mc.level?.entitiesForRendering()?.forEach { e ->
            if (e !== player && e.distanceToSqr(player) < 9.0) {
                logger.warn("  附近实体 id={} type={} pos=({}, {}, {}) dist={}", e.id, e.type, e.x, e.y, e.z, Math.sqrt(e.distanceToSqr(player)))
            }
        }
        tracing = true
    }
}
