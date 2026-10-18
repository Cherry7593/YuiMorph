package moe.yui.morph.client.ui

import moe.yui.morph.client.MorphClientState
import moe.yui.morph.protocol.C2SPlayAction
import net.minecraft.client.KeyMapping
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * 动作轮盘：按住动作键打开，鼠标划向某个动作，松开按键（或点击）即播放。
 * 鼠标停在中心附近时松开 = 取消。
 */
class ActionWheelScreen(private val key: KeyMapping) : Screen(Component.translatable("yuimorph.screen.actions.title")) {
    private val actions = MorphClientState.self.actions
    private var hovered: Int? = null

    private val centerX get() = width / 2
    private val centerY get() = height / 2

    /** 鼠标方向对应的扇区；第一个动作在正上方，顺时针排列。 */
    private fun sectorAt(mx: Double, my: Double): Int? {
        if (actions.isEmpty()) return null
        val dx = mx - centerX
        val dy = my - centerY
        if (hypot(dx, dy) < DEAD_ZONE) return null
        val angle = atan2(dy, dx) + PI / 2 // 以正上方为 0
        val step = 2 * PI / actions.size
        return ((angle / step).roundToInt()).mod(actions.size)
    }

    private fun choose() {
        hovered?.let { MorphClientState.send(C2SPlayAction(actions[it])) }
        onClose()
    }

    override fun keyReleased(event: KeyEvent): Boolean {
        if (key.matches(event)) {
            choose()
            return true
        }
        return super.keyReleased(event)
    }

    override fun mouseClicked(event: MouseButtonEvent, doubleClick: Boolean): Boolean {
        hovered = sectorAt(event.x(), event.y())
        choose()
        return true
    }

    override fun extractRenderState(g: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
        super.extractRenderState(g, mouseX, mouseY, partialTick)
        hovered = sectorAt(mouseX.toDouble(), mouseY.toDouble())

        val name = MorphClientState.currentName() ?: title
        g.centeredText(font, name, centerX, centerY - 4, WHITE)
        if (actions.isEmpty()) {
            g.centeredText(font, Component.translatable("yuimorph.screen.actions.empty"), centerX, centerY + 10, GRAY)
            return
        }

        val step = 2 * PI / actions.size
        actions.forEachIndexed { i, action ->
            val angle = i * step - PI / 2
            val x = centerX + (cos(angle) * RADIUS).toInt()
            val y = centerY + (sin(angle) * RADIUS).toInt()
            val label = Labels.action(action)
            val w = font.width(label) + 12
            val selected = i == hovered
            g.fill(x - w / 2, y - 9, x + w / 2, y + 9, if (selected) 0xC0FF9ECF.toInt() else 0x90000000.toInt())
            g.centeredText(font, label, x, y - 4, if (selected) BLACK else WHITE)
        }
        g.centeredText(font, Component.translatable("yuimorph.screen.actions.wheel_hint"), centerX, centerY + RADIUS + 22, GRAY)
    }

    override fun isPauseScreen(): Boolean = false

    private companion object {
        const val RADIUS = 72
        const val DEAD_ZONE = 22.0
        const val WHITE = 0xFFFFFFFF.toInt()
        const val GRAY = 0xFFAAAAAA.toInt()
        const val BLACK = 0xFF000000.toInt()
    }
}
