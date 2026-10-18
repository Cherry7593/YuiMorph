package moe.yui.morph.client

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement
import net.minecraft.client.DeltaTracker
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.network.chat.Component

/** 左上角显示当前伪装；没有伪装或打开了调试界面时不显示。 */
object StatusHud : HudElement {
    override fun extractRenderState(graphics: GuiGraphicsExtractor, deltaTracker: DeltaTracker) {
        val mc = Minecraft.getInstance()
        val name = MorphClientState.currentName() ?: return
        if (mc.debugOverlay.showDebugScreen()) return
        val text = Component.translatable("yuimorph.hud.disguised", name)
        graphics.fill(2, 2, 6 + mc.font.width(text), 14, 0x80000000.toInt())
        graphics.text(mc.font, text, 4, 4, 0xFFFFFFFF.toInt())
    }
}
