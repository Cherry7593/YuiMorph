package moe.yui.morph.gametest

import com.google.gson.JsonParser
import moe.yui.morph.client.ui.PreviewEntities
import moe.yui.morph.client.ui.SkinCache
import moe.yui.morph.protocol.SkinTexture
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import org.slf4j.LoggerFactory

/**
 * 预览渲染的自动化测试：建一个单人世界，用和选择界面完全相同的代码画缩略图，然后截图。
 * 用来区分「我们的代码画不出来」还是「玩家客户端上其他 Mod 的冲突」。
 */
class PreviewRenderGameTest : FabricClientGameTest {
    private val logger = LoggerFactory.getLogger("YuiMorph-GameTest")

    override fun runTest(context: ClientGameTestContext) {
        javaClass.classLoader.getResourceAsStream("yuimorph-gametest-notch.json")?.use { input ->
            val obj = JsonParser.parseString(input.reader().readText()).asJsonObject
            val signature = obj.get("signature")?.takeIf { !it.isJsonNull }?.asString
            context.runOnClient<RuntimeException> { SkinCache.put("Notch", SkinTexture(obj.get("value").asString, signature)) }
        }

        context.worldBuilder().create().use { _ ->
            context.waitTicks(40)
            context.setScreen { PreviewTestScreen() }
            // 等皮肤下载、披风物理稳定
            context.waitTicks(100)
            val path = context.takeScreenshot("yuimorph-preview")
            logger.info("预览截图：{}", path)
        }
    }

    private class PreviewTestScreen : Screen(Component.literal("YuiMorph preview test")) {
        private val ids = listOf(
            "minecraft:zombie", "minecraft:zombie_villager", "minecraft:zombified_piglin", "minecraft:zoglin",
            "minecraft:husk", "minecraft:drowned", "minecraft:skeleton", "minecraft:hoglin",
            "minecraft:polar_bear", "minecraft:pig", "minecraft:zombie_horse", "minecraft:villager",
            "player:Notch", "player:YuiMorphNoSkin",
        )

        override fun tick() {
            PreviewEntities.tick(null)
        }

        override fun extractRenderState(g: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
            super.extractRenderState(g, mouseX, mouseY, partialTick)
            val cols = 7
            ids.forEachIndexed { i, id ->
                val x = 10 + (i % cols) * 64
                val y = 10 + (i / cols) * 84
                g.fill(x, y, x + 60, y + 80, 0x40000000)
                val entity = PreviewEntities.thumbnail(id)
                if (entity == null) {
                    g.text(font, "NULL", x + 4, y + 30, 0xFFFF5555.toInt())
                } else {
                    PreviewEntities.draw(g, id, entity, x + 2, y + 2, x + 58, y + 66, x + 30f - 80f, y + 30f)
                }
                g.text(font, id.substringAfter(':').take(10), x + 2, y + 70, 0xFFFFFFFF.toInt())
            }
        }
    }
}
