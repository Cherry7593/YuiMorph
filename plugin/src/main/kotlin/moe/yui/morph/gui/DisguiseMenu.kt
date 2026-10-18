package moe.yui.morph.gui

import moe.yui.morph.YuiMorphPlugin
import moe.yui.morph.disguise.DisguiseId
import moe.yui.morph.i18n.ph
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.Material
import org.bukkit.entity.EntityType
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryDragEvent
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.InventoryHolder
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.SkullMeta

/**
 * 箱子界面的伪装选择（给没有装客户端 Mod 的玩家用）。
 *
 * 布局：前 5 行是伪装，最后一行是「上一页 · 当前伪装/解除 · 下一页」。
 * 所有操作都在玩家自己的区域线程（界面事件本来就在这里触发）。
 */
class DisguiseMenu private constructor(
    private val plugin: YuiMorphPlugin,
    private val player: Player,
    private val entries: List<DisguiseId>,
) : InventoryHolder {
    private var page = 0
    private val pages = maxOf(1, (entries.size + PAGE_SIZE - 1) / PAGE_SIZE)
    private val inv: Inventory = plugin.server.createInventory(this, SIZE, plugin.messages.get("gui.title"))

    override fun getInventory(): Inventory = inv

    private fun render() {
        inv.clear()
        val current = plugin.disguises.current(player)?.disguise
        entries.drop(page * PAGE_SIZE).take(PAGE_SIZE).forEachIndexed { slot, id ->
            inv.setItem(slot, icon(id, selected = id == current))
        }
        val msg = plugin.messages
        val pageInfo = arrayOf(ph("page", (page + 1).toString()), ph("pages", pages.toString()))
        if (page > 0) inv.setItem(SLOT_PREV, button(Material.ARROW, msg.get("gui.prev", *pageInfo)))
        if (page < pages - 1) inv.setItem(SLOT_NEXT, button(Material.ARROW, msg.get("gui.next", *pageInfo)))
        inv.setItem(
            SLOT_CENTER,
            if (current != null) button(Material.BARRIER, msg.get("gui.unmorph", ph("disguise", current.displayName())))
            else button(Material.GRAY_STAINED_GLASS_PANE, msg.get("gui.not-disguised")),
        )
        if (entries.isEmpty()) inv.setItem(22, button(Material.PAPER, msg.get("gui.empty")))
    }

    private fun click(slot: Int) {
        when (slot) {
            SLOT_PREV -> if (page > 0) { page--; render() }
            SLOT_NEXT -> if (page < pages - 1) { page++; render() }
            SLOT_CENTER -> if (plugin.disguises.current(player) != null) {
                player.closeInventory()
                plugin.disguises.unmorph(player)
            }
            in 0 until PAGE_SIZE -> {
                val id = entries.getOrNull(page * PAGE_SIZE + slot) ?: return
                player.closeInventory()
                plugin.disguises.morph(player, id)
            }
        }
    }

    private fun icon(id: DisguiseId, selected: Boolean): ItemStack {
        val item = when (id) {
            is DisguiseId.Mob -> ItemStack(iconMaterial(id.type))
            is DisguiseId.Player -> ItemStack(Material.PLAYER_HEAD).apply {
                // 只给名字，客户端会自己解析皮肤，不需要服务端联网
                editMeta(SkullMeta::class.java) { it.playerProfile = plugin.server.createProfile(id.name) }
            }
        }
        item.editMeta { meta ->
            meta.displayName(id.displayName().decoration(TextDecoration.ITALIC, false))
            val lore = mutableListOf(plugin.messages.get("gui.entry-id", ph("id", id.toString())))
            if (selected) lore += plugin.messages.get("gui.entry-current")
            meta.lore(lore.map { it.decoration(TextDecoration.ITALIC, false) })
            if (selected) meta.setEnchantmentGlintOverride(true)
        }
        return item
    }

    private fun button(material: Material, name: Component) = ItemStack(material).apply {
        editMeta { it.displayName(name.decoration(TextDecoration.ITALIC, false)) }
    }

    /** 点击和拖动都要取消，防止玩家把图标拿走。 */
    class MenuListener : Listener {
        @EventHandler(ignoreCancelled = true)
        fun onClick(event: InventoryClickEvent) {
            val menu = event.view.topInventory.holder as? DisguiseMenu ?: return
            event.isCancelled = true
            if (event.clickedInventory == event.view.topInventory) menu.click(event.slot)
        }

        @EventHandler(ignoreCancelled = true)
        fun onDrag(event: InventoryDragEvent) {
            if (event.view.topInventory.holder is DisguiseMenu) event.isCancelled = true
        }
    }

    companion object {
        private const val SIZE = 54
        private const val PAGE_SIZE = 45
        private const val SLOT_PREV = 45
        private const val SLOT_CENTER = 49
        private const val SLOT_NEXT = 53

        /** 打开界面。必须在玩家区域线程调用。 */
        fun open(plugin: YuiMorphPlugin, player: Player) {
            val entries = plugin.disguises.available(player)
            DisguiseMenu(plugin, player, entries).apply { render(); player.openInventory(inv) }
        }

        /** 刷怪蛋作为图标；没有刷怪蛋的实体用一些合适的物品代替。 */
        private fun iconMaterial(type: EntityType): Material =
            Material.matchMaterial("${type.key.key}_spawn_egg")
                ?: FALLBACK_ICONS[type]
                ?: Material.NAME_TAG

        private val FALLBACK_ICONS = mapOf(
            EntityType.ARMOR_STAND to Material.ARMOR_STAND,
            EntityType.MANNEQUIN to Material.ARMOR_STAND,
            EntityType.GIANT to Material.ZOMBIE_HEAD,
            EntityType.ILLUSIONER to Material.BOW,
        )
    }
}
