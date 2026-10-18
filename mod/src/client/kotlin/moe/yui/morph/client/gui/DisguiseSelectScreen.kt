package moe.yui.morph.client.gui

import moe.yui.morph.client.MorphClientState
import moe.yui.morph.protocol.C2SMorph
import moe.yui.morph.protocol.C2SUnmorph
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.screens.Screen
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.SpawnEggItem

/**
 * 原生伪装选择界面：搜索框 + 分页的伪装按钮（带刷怪蛋 / 玩家头图标）+ 解除伪装。
 * 列表来自服务器同步的可用伪装，点击后发送变身请求，服务器仍然会做权限和解锁检查。
 */
class DisguiseSelectScreen : Screen(Component.translatable("yuimorph.screen.select.title")) {
    private var page = 0
    private var filter = ""
    private lateinit var search: EditBox

    /** 当前页按钮对应的伪装和图标（用于在按钮上画图标）。 */
    private val icons = mutableListOf<Pair<Button, ItemStack>>()

    private val columns get() = ((width - MARGIN * 2) / (BUTTON_WIDTH + GAP)).coerceIn(1, 4)
    private val rows get() = ((height - TOP - BOTTOM) / (BUTTON_HEIGHT + GAP)).coerceAtLeast(1)
    private val pageSize get() = columns * rows

    private fun entries(): List<String> {
        val keyword = filter.trim().lowercase()
        return MorphClientState.available
            .map { it to MorphClientState.displayName(it).string }
            .filter { (id, name) -> keyword.isEmpty() || id.lowercase().contains(keyword) || name.lowercase().contains(keyword) }
            .sortedBy { it.second }
            .map { it.first }
    }

    override fun init() {
        search = EditBox(font, width / 2 - 100, 22, 200, 18, Component.translatable("yuimorph.screen.select.search"))
        search.setHint(Component.translatable("yuimorph.screen.select.search"))
        search.value = filter
        search.setResponder { filter = it; page = 0; rebuild() }
        rebuild()
    }

    /** 重建按钮（翻页、搜索、同步更新时调用）。 */
    private fun rebuild() {
        clearWidgets()
        icons.clear()
        addRenderableWidget(search)
        setInitialFocus(search)

        val all = entries()
        val pages = maxOf(1, (all.size + pageSize - 1) / pageSize)
        page = page.coerceIn(0, pages - 1)
        val current = MorphClientState.self.disguiseId
        val gridWidth = columns * BUTTON_WIDTH + (columns - 1) * GAP
        val left = (width - gridWidth) / 2

        all.drop(page * pageSize).take(pageSize).forEachIndexed { i, id ->
            val x = left + (i % columns) * (BUTTON_WIDTH + GAP)
            val y = TOP + (i / columns) * (BUTTON_HEIGHT + GAP)
            val name = MorphClientState.displayName(id)
            val label = if (id == current) Component.literal("✔ ").append(name) else name
            val button = Button.builder(label) { morph(id) }.bounds(x, y, BUTTON_WIDTH, BUTTON_HEIGHT).build()
            addRenderableWidget(button)
            icons += button to icon(id)
        }

        val bottom = height - 28
        addRenderableWidget(Button.builder(Component.literal("<")) { page--; rebuild() }.bounds(width / 2 - 154, bottom, 20, 20).build())
            .active = page > 0
        addRenderableWidget(
            Button.builder(Component.translatable("yuimorph.screen.select.unmorph")) { unmorph() }.bounds(width / 2 - 130, bottom, 128, 20).build(),
        ).active = current != null
        addRenderableWidget(Button.builder(Component.translatable("gui.done")) { onClose() }.bounds(width / 2 + 2, bottom, 128, 20).build())
        addRenderableWidget(Button.builder(Component.literal(">")) { page++; rebuild() }.bounds(width / 2 + 134, bottom, 20, 20).build())
            .active = page < pages - 1
    }

    private fun icon(id: String): ItemStack {
        if (id.startsWith("player:")) return ItemStack(Items.PLAYER_HEAD)
        val type = Identifier.tryParse(id)?.let { BuiltInRegistries.ENTITY_TYPE.getOptional(it).orElse(null) }
        return type?.let { SpawnEggItem.byId(it).map { egg -> ItemStack(egg.value()) }.orElse(null) } ?: ItemStack(Items.NAME_TAG)
    }

    private fun morph(id: String) {
        MorphClientState.send(C2SMorph(id))
        onClose()
    }

    private fun unmorph() {
        MorphClientState.send(C2SUnmorph)
        onClose()
    }

    /** 服务器同步了新的可用列表时刷新。 */
    private var seen = MorphClientState.available to MorphClientState.self

    override fun tick() {
        val now = MorphClientState.available to MorphClientState.self
        if (now != seen) {
            seen = now
            rebuild()
        }
    }

    override fun extractRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick)
        graphics.centeredText(font, title, width / 2, 8, 0xFFFFFFFF.toInt())
        icons.forEach { (button, stack) -> graphics.item(stack, button.x + 2, button.y + 2) }
        if (icons.isEmpty()) {
            graphics.centeredText(font, Component.translatable("yuimorph.screen.select.empty"), width / 2, height / 2, 0xFFAAAAAA.toInt())
        }
    }

    override fun isPauseScreen(): Boolean = false

    private companion object {
        const val BUTTON_WIDTH = 120
        const val BUTTON_HEIGHT = 20
        const val GAP = 4
        const val MARGIN = 12
        const val TOP = 48
        const val BOTTOM = 40
    }
}
