package moe.yui.morph.client.gui

import moe.yui.morph.client.MorphClientState
import moe.yui.morph.protocol.C2SPlayAction
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.screens.Screen
import net.minecraft.locale.Language
import net.minecraft.network.chat.Component

/** 当前伪装的动作列表，点击播放。 */
class ActionScreen : Screen(Component.translatable("yuimorph.screen.actions.title")) {

    override fun init() {
        val actions = MorphClientState.self.actions
        val columns = 3
        val buttonWidth = 100
        val left = width / 2 - (columns * (buttonWidth + 4) - 4) / 2
        actions.forEachIndexed { i, action ->
            val x = left + (i % columns) * (buttonWidth + 4)
            val y = height / 2 - 30 + (i / columns) * 24
            addRenderableWidget(Button.builder(actionName(action)) { play(action) }.bounds(x, y, buttonWidth, 20).build())
        }
        addRenderableWidget(Button.builder(Component.translatable("gui.done")) { onClose() }.bounds(width / 2 - 50, height - 28, 100, 20).build())
    }

    private fun play(action: String) {
        MorphClientState.send(C2SPlayAction(action))
        onClose()
    }

    override fun extractRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick)
        val name = MorphClientState.currentName()
        val header = if (name != null) Component.translatable("yuimorph.screen.actions.header", name) else title
        graphics.centeredText(font, header, width / 2, height / 2 - 52, 0xFFFFFFFF.toInt())
        if (MorphClientState.self.actions.isEmpty()) {
            graphics.centeredText(font, Component.translatable("yuimorph.screen.actions.empty"), width / 2, height / 2, 0xFFAAAAAA.toInt())
        }
    }

    override fun isPauseScreen(): Boolean = false

    companion object {
        /** 有翻译时显示翻译，否则显示动作 ID。 */
        fun actionName(action: String): Component {
            val key = "yuimorph.action.$action"
            return if (Language.getInstance().has(key)) Component.translatable(key) else Component.literal(action)
        }
    }
}
