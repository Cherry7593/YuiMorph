package moe.yui.morph.client

import net.minecraft.client.Minecraft

/**
 * 每个客户端 tick 结束时（玩家已经移动完）手动 tick 代理实体。
 *
 * 代理实体不随世界 tick（见 ClientLevelMixin），否则 `setOldPosAndRot()` 会在我们移动它之前执行，
 * 位移永远是 0，腿不会动。这里的顺序是：
 *   1. `commonTick()`：记录旧位置（= 上一 tick 的玩家位置）
 *   2. 复制玩家当前的位置和朝向
 *   3. `tick()`：按位移计算走路动画，并推进生物自己的动画（跳舞、吼叫等）
 *   4. 再次覆盖朝向，避免生物自己的转身逻辑和玩家不一致
 * 这样代理实体的插值、走路动画和玩家完全同步。
 */
object ProxyTicker {
    /** 正在由我们手动 tick 代理实体。其他路径 tick 代理实体时会被 Mixin 拦下。 */
    @JvmStatic
    var isManualTicking = false
        private set

    fun tick(mc: Minecraft) {
        val player = mc.player ?: return
        val proxy = MorphClientState.proxy() ?: return
        if (proxy.isRemoved) return

        proxy.commonTick()
        proxy.setPos(player.x, player.y, player.z)
        proxy.setOnGround(player.onGround())
        // 复制速度只影响外观（披风摆动、鞘翅等），代理实体的物理已经由 Mixin 完全隔离
        proxy.deltaMovement = player.deltaMovement
        proxy.yRot = player.yRot
        proxy.xRot = player.xRot
        proxy.yHeadRot = player.yHeadRot

        isManualTicking = true
        try {
            proxy.tick()
        } finally {
            isManualTicking = false
        }
        // tick 中如果有任何逻辑改动了位置，强制贴回玩家
        proxy.setPos(player.x, player.y, player.z)

        proxy.yRotO = player.yRotO
        proxy.xRotO = player.xRotO
        proxy.yBodyRot = player.yBodyRot
        proxy.yBodyRotO = player.yBodyRotO
        proxy.yHeadRot = player.yHeadRot
        proxy.yHeadRotO = player.yHeadRotO
    }
}
