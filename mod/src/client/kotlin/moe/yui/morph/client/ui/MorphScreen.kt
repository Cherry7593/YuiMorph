package moe.yui.morph.client.ui

import moe.yui.morph.client.MorphClientState
import moe.yui.morph.client.storage.ClientStorage
import moe.yui.morph.client.storage.Preset
import moe.yui.morph.protocol.C2SMorph
import moe.yui.morph.protocol.C2SPlayAction
import moe.yui.morph.protocol.C2SUnmorph
import moe.yui.morph.protocol.PropertySpec
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component
import kotlin.math.max
import kotlin.math.sign

/**
 * 伪装选择界面。
 *
 * ```
 * ┌ 标题 · 当前伪装 ─────────────────────────── [搜索] ┐
 * │ [全部][生物][玩家][预设]                            │
 * │ ┌──┐┌──┐┌──┐┌──┐        │   大预览（跟随鼠标）     │
 * │ │模│└──┘└──┘└──┘  网格  │   属性：◀ 值 ▶           │
 * │ └──┘ …（滚轮翻页）      │   [伪装][重置][存预设]    │
 * │                         │   [解除] 动作…            │
 * └─────────────────────────────────────────────────────┘
 * ```
 * 左侧网格每格都是实时模型；右侧预览的外观由服务器计算（和别人看到的一致），修改属性立即刷新。
 */
class MorphScreen : Screen(Component.translatable("yuimorph.screen.select.title")) {
    private enum class Tab(val key: String) { ALL("all"), MOBS("mobs"), PLAYERS("players"), PRESETS("presets") }

    private data class Tile(
        val id: String,
        val label: Component,
        val recent: Boolean = false,
        val preset: Int? = null,
        val properties: Map<String, String>? = null,
    )

    /** 右侧的一行属性控件，标签和值在渲染时画。 */
    private data class Row(val spec: PropertySpec, val y: Int)

    private var tab = Tab.ALL
    private var filter = ""
    private var scrollRow = 0
    private var tiles: List<Tile> = emptyList()
    private var selectedPreset: Int? = null
    private var search: EditBox? = null
    private val rows = mutableListOf<Row>()
    private val textBoxes = HashMap<String, EditBox>()

    /** 构建控件时的状态，变化时才重建（只改属性值不重建，否则正在输入的文本框会丢焦点）。 */
    private var builtFor: Any? = null
    private var seenData: Any? = null

    private var pendingText: Pair<String, String>? = null
    private var pendingTicks = 0
    private var flash: Component? = null
    private var flashTicks = 0

    // ---------------- 布局 ----------------

    private var gridX0 = 0
    private var gridX1 = 0
    private var gridY0 = 0
    private var gridY1 = 0
    private var panelX0 = 0
    private var panelX1 = 0
    private var panelY0 = 0
    private var panelY1 = 0
    private var previewY1 = 0

    private val columns get() = max(1, (gridX1 - gridX0 + GAP) / (TILE_W + GAP))
    private val visibleRows get() = max(1, (gridY1 - gridY0 + GAP) / (TILE_H + GAP))
    private val maxScroll get() = max(0, (tiles.size + columns - 1) / columns - visibleRows)

    private fun layout() {
        gridX0 = MARGIN
        gridY0 = 50
        gridY1 = height - MARGIN
        panelX1 = width - MARGIN
        // 右侧面板约占 40%，至少 170 像素
        panelX0 = (width - max(170, (width * 0.4).toInt())).coerceAtLeast(MARGIN + TILE_W + 20)
        gridX1 = panelX0 - 10
        panelY0 = gridY0
        panelY1 = height - MARGIN
        previewY1 = panelY0 + ((panelY1 - panelY0) * 0.46).toInt().coerceIn(70, 190)
    }

    // ---------------- 构建控件 ----------------

    override fun init() {
        layout()
        val box = search ?: EditBox(font, 0, 0, 140, 16, Component.translatable("yuimorph.screen.select.search")).also {
            it.setHint(Component.translatable("yuimorph.screen.select.search"))
            it.setResponder { text -> filter = text; scrollRow = 0; refreshTiles() }
        }
        box.setX(width - MARGIN - 140)
        box.setY(8)
        search = box
        addRenderableWidget(box)

        Tab.entries.forEachIndexed { i, t ->
            addRenderableWidget(
                Button.builder(Component.translatable("yuimorph.screen.select.tab.${t.key}")) { switchTab(t) }
                    .bounds(MARGIN + i * (TAB_W + 2), 28, TAB_W, 16).build(),
            ).active = tab != t
        }

        refreshTiles()
        buildPanel()
        builtFor = buildKey()
    }

    private fun buildKey() = listOf(
        PreviewController.selectedId, PreviewController.specs, PreviewController.actions,
        MorphClientState.self.disguiseId, selectedPreset, tab, MorphClientState.accepted,
    )

    private fun rebuild() {
        val focusedKey = textBoxes.entries.firstOrNull { it.value.isFocused }?.key
        clearWidgets()
        rows.clear()
        textBoxes.clear()
        init()
        // 重建后恢复正在输入的文本框焦点
        focusedKey?.let { key -> textBoxes[key]?.let { setFocused(it); it.moveCursorToEnd(false) } }
    }

    private fun buildPanel() {
        val id = PreviewController.selectedId ?: return
        val panelW = panelX1 - panelX0
        val buttonsTop = panelY1 - 42
        var y = previewY1 + 6

        for (spec in PreviewController.specs) {
            if (y + 18 > buttonsTop - 4) break
            val values = spec.values
            if (values == null) {
                val text = EditBox(font, panelX0 + LABEL_W, y, panelW - LABEL_W, 18, Labels.property(spec.key))
                text.setMaxLength(spec.maxLength)
                text.setValue(PreviewController.properties[spec.key] ?: "")
                text.setResponder { v -> pendingText = spec.key to v; pendingTicks = TEXT_DEBOUNCE_TICKS }
                textBoxes[spec.key] = text
                addRenderableWidget(text)
            } else {
                addRenderableWidget(Button.builder(Component.literal("◀")) { cycle(spec, -1) }.bounds(panelX0 + LABEL_W, y, 18, 18).build())
                addRenderableWidget(Button.builder(Component.literal("▶")) { cycle(spec, 1) }.bounds(panelX1 - 18, y, 18, 18).build())
            }
            rows += Row(spec, y)
            y += 22
        }

        // 第一行：伪装 / 重置 / 预设
        val third = (panelW - 4) / 3
        addRenderableWidget(Button.builder(Component.translatable("yuimorph.screen.select.morph")) { morph(id) }
            .bounds(panelX0, buttonsTop, third, 20).build()).active = MorphClientState.accepted
        addRenderableWidget(Button.builder(Component.translatable("yuimorph.screen.select.reset")) { PreviewController.reset() }
            .bounds(panelX0 + third + 2, buttonsTop, third, 20).build()).active = PreviewController.specs.isNotEmpty()
        val presetIndex = selectedPreset
        if (tab == Tab.PRESETS && presetIndex != null) {
            addRenderableWidget(Button.builder(Component.translatable("yuimorph.screen.select.delete_preset")) { deletePreset(presetIndex) }
                .bounds(panelX0 + (third + 2) * 2, buttonsTop, third, 20).build())
        } else {
            addRenderableWidget(Button.builder(Component.translatable("yuimorph.screen.select.save_preset")) { savePreset(id) }
                .bounds(panelX0 + (third + 2) * 2, buttonsTop, third, 20).build())
        }

        // 第二行：解除伪装 + 当前伪装的动作
        val row2 = buttonsTop + 22
        val unmorphW = 60
        addRenderableWidget(Button.builder(Component.translatable("yuimorph.screen.select.unmorph")) { unmorph() }
            .bounds(panelX0, row2, unmorphW, 20).build()).active = MorphClientState.self.disguiseId != null
        if (id == MorphClientState.self.disguiseId) {
            var x = panelX0 + unmorphW + 4
            for (action in PreviewController.actions) {
                val label = Labels.action(action)
                val w = font.width(label) + 10
                if (x + w > panelX1) break
                addRenderableWidget(Button.builder(label) { MorphClientState.send(C2SPlayAction(action)) }.bounds(x, row2, w, 20).build())
                x += w + 2
            }
        }
    }

    // ---------------- 数据 ----------------

    private fun refreshTiles() {
        val keyword = filter.trim().lowercase()
        fun matches(t: Tile) = keyword.isEmpty() || t.id.lowercase().contains(keyword) || t.label.string.lowercase().contains(keyword)

        tiles = if (tab == Tab.PRESETS) {
            ClientStorage.presets().mapIndexed { i, p -> Tile(p.id, Component.literal(p.label), preset = i, properties = p.properties) }
                .filter(::matches)
        } else {
            val base = MorphClientState.available
                .filter { tab == Tab.ALL || (tab == Tab.PLAYERS) == it.startsWith(PreviewEntities.PLAYER_PREFIX) }
                .map { Tile(it, MorphClientState.displayName(it)) }
                .filter(::matches)
                .sortedBy { it.label.string }
            if (tab == Tab.ALL && keyword.isEmpty()) {
                // 最近使用的排在最前面
                val recent = ClientStorage.recent().mapNotNull { r -> base.firstOrNull { it.id == r }?.copy(recent = true) }
                recent + base.filter { t -> recent.none { it.id == t.id } }
            } else {
                base
            }
        }
        scrollRow = scrollRow.coerceIn(0, maxScroll)
        // 玩家缩略图的皮肤向服务器要
        SkinCache.request(tiles.filter { it.id.startsWith(PreviewEntities.PLAYER_PREFIX) }.map { it.id.removePrefix(PreviewEntities.PLAYER_PREFIX) })
    }

    private fun switchTab(t: Tab) {
        tab = t
        scrollRow = 0
        selectedPreset = null
        rebuild()
    }

    private fun select(tile: Tile) {
        selectedPreset = tile.preset
        PreviewController.select(tile.id, tile.properties)
        rebuild()
    }

    private fun cycle(spec: PropertySpec, direction: Int) {
        // 「默认」放在选项最前面
        val options = listOf<String?>(null) + spec.values.orEmpty()
        val current = options.indexOf(PreviewController.properties[spec.key]).coerceAtLeast(0)
        PreviewController.set(spec.key, options[(current + direction).mod(options.size)])
    }

    private fun morph(id: String) {
        MorphClientState.send(C2SMorph(id, PreviewController.properties))
        onClose()
    }

    private fun unmorph() {
        MorphClientState.send(C2SUnmorph)
        onClose()
    }

    private fun savePreset(id: String) {
        val props = PreviewController.properties
        val summary = props.entries.joinToString(" ") { (k, v) -> Labels.value(k, v).string }
        val label = MorphClientState.displayName(id).string + if (summary.isNotEmpty()) " · $summary" else ""
        val saved = ClientStorage.addPreset(Preset(id, props, label))
        showFlash(Component.translatable(if (saved) "yuimorph.screen.select.preset_saved" else "yuimorph.screen.select.preset_exists"))
    }

    private fun deletePreset(index: Int) {
        ClientStorage.removePreset(index)
        selectedPreset = null
        PreviewController.clear()
        refreshTiles()
        rebuild()
    }

    private fun showFlash(text: Component) {
        flash = text
        flashTicks = 40
    }

    override fun tick() {
        PreviewEntities.tick(PreviewController.entity)
        // 服务器同步了可用列表 / 自身伪装：刷新网格
        val data = MorphClientState.available to MorphClientState.self.disguiseId
        if (data != seenData) {
            seenData = data
            refreshTiles()
        }
        if (buildKey() != builtFor) rebuild()

        if (pendingTicks > 0 && --pendingTicks == 0) {
            pendingText?.let { (key, value) -> PreviewController.set(key, value.trim().ifEmpty { null }) }
            pendingText = null
        }
        if (flashTicks > 0 && --flashTicks == 0) flash = null
    }

    // ---------------- 输入 ----------------

    private fun tileAt(mx: Double, my: Double): Tile? {
        if (mx < gridX0 || mx >= gridX1 || my < gridY0 || my >= gridY1) return null
        val col = ((mx - gridX0) / (TILE_W + GAP)).toInt()
        val row = ((my - gridY0) / (TILE_H + GAP)).toInt()
        if (col >= columns || row >= visibleRows) return null
        // 落在格子间隙里不算
        if ((mx - gridX0) % (TILE_W + GAP) >= TILE_W || (my - gridY0) % (TILE_H + GAP) >= TILE_H) return null
        return tiles.getOrNull((scrollRow + row) * columns + col)
    }

    override fun mouseClicked(event: MouseButtonEvent, doubleClick: Boolean): Boolean {
        if (super.mouseClicked(event, doubleClick)) return true
        val tile = tileAt(event.x(), event.y()) ?: return false
        if (doubleClick && tile.id == PreviewController.selectedId) {
            morph(tile.id)
        } else {
            select(tile)
        }
        return true
    }

    override fun mouseScrolled(x: Double, y: Double, horizontal: Double, vertical: Double): Boolean {
        if (x in gridX0.toDouble()..gridX1.toDouble() && y in gridY0.toDouble()..gridY1.toDouble()) {
            scrollRow = (scrollRow - vertical.sign.toInt()).coerceIn(0, maxScroll)
            return true
        }
        return super.mouseScrolled(x, y, horizontal, vertical)
    }

    // ---------------- 渲染 ----------------

    override fun extractRenderState(g: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
        super.extractRenderState(g, mouseX, mouseY, partialTick)
        g.text(font, title, MARGIN, 12, WHITE)
        val current = MorphClientState.currentName() ?: Component.translatable("yuimorph.screen.select.none")
        g.text(font, Component.translatable("yuimorph.screen.select.current", current), MARGIN + font.width(title) + 12, 12, GRAY)
        drawGrid(g, mouseX, mouseY)
        drawPanel(g, mouseX, mouseY)
    }

    private fun drawGrid(g: GuiGraphicsExtractor, mouseX: Int, mouseY: Int) {
        if (!MorphClientState.accepted) {
            g.centeredText(font, Component.translatable("yuimorph.message.server_unsupported"), (gridX0 + gridX1) / 2, (gridY0 + gridY1) / 2, GRAY)
            return
        }
        if (tiles.isEmpty()) {
            val key = if (tab == Tab.PRESETS) "yuimorph.screen.select.no_presets" else "yuimorph.screen.select.empty"
            g.centeredText(font, Component.translatable(key), (gridX0 + gridX1) / 2, (gridY0 + gridY1) / 2, GRAY)
            return
        }
        val cols = columns
        val selfId = MorphClientState.self.disguiseId
        tiles.drop(scrollRow * cols).take(cols * visibleRows).forEachIndexed { i, tile ->
            val x = gridX0 + (i % cols) * (TILE_W + GAP)
            val y = gridY0 + (i / cols) * (TILE_H + GAP)
            val hovered = mouseX in x until x + TILE_W && mouseY in y until y + TILE_H
            val selected = tile.id == PreviewController.selectedId && (tab != Tab.PRESETS || tile.preset == selectedPreset)
            g.fill(x, y, x + TILE_W, y + TILE_H, if (hovered) 0x50FFFFFF else 0x38000000)
            if (selected) frame(g, x, y, x + TILE_W, y + TILE_H, PINK)

            PreviewEntities.thumbnail(tile.id)?.let { e ->
                // 缩略图正对玩家：视线点放在格子中心
                PreviewEntities.draw(g, tile.id, e, x + 2, y + 2, x + TILE_W - 2, y + TILE_H - 12, x + TILE_W / 2f, y + TILE_H / 2f - 8)
            }
            val name = font.plainSubstrByWidth(tile.label.string, TILE_W - 4)
            g.centeredText(font, name, x + TILE_W / 2, y + TILE_H - 10, if (selected) PINK else WHITE)
            if (tile.id == selfId && tab != Tab.PRESETS) g.text(font, "✔", x + 3, y + 2, PINK)
            if (tile.recent) g.text(font, "•", x + TILE_W - 7, y + 1, GRAY)
        }

        // 滚动条
        val totalRows = (tiles.size + cols - 1) / cols
        if (totalRows > visibleRows) {
            val trackH = gridY1 - gridY0
            val barH = max(12, trackH * visibleRows / totalRows)
            val barY = gridY0 + (trackH - barH) * scrollRow / max(1, maxScroll)
            g.fill(gridX1 + 2, gridY0, gridX1 + 4, gridY1, 0x30FFFFFF)
            g.fill(gridX1 + 2, barY, gridX1 + 4, barY + barH, 0xA0FFFFFF.toInt())
        }
    }

    private fun drawPanel(g: GuiGraphicsExtractor, mouseX: Int, mouseY: Int) {
        g.fill(panelX0, panelY0, panelX1, previewY1, 0x38000000)
        val id = PreviewController.selectedId
        val entity = PreviewController.entity
        if (id == null) {
            g.centeredText(font, Component.translatable("yuimorph.screen.select.hint"), (panelX0 + panelX1) / 2, (panelY0 + previewY1) / 2, GRAY)
            return
        }
        g.centeredText(font, MorphClientState.displayName(id), (panelX0 + panelX1) / 2, panelY0 + 4, WHITE)
        if (entity != null) {
            PreviewEntities.draw(g, id, entity, panelX0, panelY0 + 14, panelX1, previewY1 - 2, mouseX.toFloat(), mouseY.toFloat())
        }

        for (row in rows) {
            g.text(font, Labels.property(row.spec.key), panelX0, row.y + 5, GRAY)
            if (row.spec.values != null) {
                val value = Labels.value(row.spec.key, PreviewController.properties[row.spec.key])
                val center = (panelX0 + LABEL_W + 18 + panelX1 - 18) / 2
                g.centeredText(font, value, center, row.y + 5, WHITE)
            }
        }
        if (PreviewController.specs.isEmpty() && MorphClientState.accepted) {
            g.text(font, Component.translatable("yuimorph.screen.select.no_properties"), panelX0, previewY1 + 10, GRAY)
        }
        flash?.let { g.centeredText(font, it, (panelX0 + panelX1) / 2, panelY1 - 54, PINK) }
    }

    private fun frame(g: GuiGraphicsExtractor, x0: Int, y0: Int, x1: Int, y1: Int, color: Int) {
        g.fill(x0, y0, x1, y0 + 1, color)
        g.fill(x0, y1 - 1, x1, y1, color)
        g.fill(x0, y0, x0 + 1, y1, color)
        g.fill(x1 - 1, y0, x1, y1, color)
    }

    override fun onClose() {
        PreviewController.clear()
        super.onClose()
    }

    override fun isPauseScreen(): Boolean = false

    private companion object {
        const val MARGIN = 10
        const val TILE_W = 54
        const val TILE_H = 64
        const val GAP = 4
        const val TAB_W = 44
        const val LABEL_W = 46
        const val TEXT_DEBOUNCE_TICKS = 8

        const val WHITE = 0xFFFFFFFF.toInt()
        const val GRAY = 0xFFAAAAAA.toInt()
        const val PINK = 0xFFFF9ECF.toInt()
    }
}
