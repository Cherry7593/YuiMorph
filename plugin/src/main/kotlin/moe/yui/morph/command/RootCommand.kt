package moe.yui.morph.command

import com.mojang.brigadier.Command
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents
import moe.yui.morph.Permissions
import moe.yui.morph.YuiMorphPlugin
import moe.yui.morph.gui.DisguiseMenu
import moe.yui.morph.i18n.ph
import moe.yui.morph.util.runOnRegion
import net.kyori.adventure.text.event.ClickEvent
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

/**
 * 唯一的入口命令 `/yuimorph`（别名 `/ym`），所有功能都是它的子命令：
 * - 玩家：`morph` `unmorph` `property` `action` `request` `help`；不带参数打开选择界面
 * - 管理：`admin ...`
 *
 * 没有权限的子命令不会出现在补全里（Brigadier 的 requires）。
 */
class RootCommand(private val plugin: YuiMorphPlugin, private val modules: List<CommandModule>) {
    private val msg get() = plugin.messages

    fun register() {
        plugin.lifecycleManager.registerEventHandler(LifecycleEvents.COMMANDS) { event ->
            event.registrar().register(build(), "YuiMorph 伪装", listOf(ALIAS))
        }
    }

    private fun build(): Node {
        val root = CommandModule.literal(NAME)
            .executes { ctx ->
                val sender = ctx.source.sender
                val player = ctx.source.executor as? Player ?: sender as? Player
                if (player != null && player.hasPermission(Permissions.MORPH)) {
                    player.runOnRegion(plugin) { DisguiseMenu.open(plugin, player) }
                } else {
                    sendHelp(sender)
                }
                Command.SINGLE_SUCCESS
            }
            .then(CommandModule.literal("help").executes { ctx -> sendHelp(ctx.source.sender); Command.SINGLE_SUCCESS })
        modules.flatMap { it.nodes() }.forEach { root.then(it) }
        return root.build()
    }

    /** 只列出发送者有权限的命令，点击可以把命令填进聊天框。 */
    private fun sendHelp(sender: CommandSender) {
        val entries = modules.flatMap { it.help() }.filter { it.permission == null || sender.hasPermission(it.permission) }
        msg.send(sender, "help.header", ph("version", plugin.pluginMeta.version))
        entries.forEach { e ->
            val usage = if (e.usage.isEmpty()) "/$ALIAS" else "/$ALIAS ${e.usage}"
            val line = msg.get("help.entry", ph("usage", usage), ph("description", msg.get(e.descKey)))
                .clickEvent(ClickEvent.suggestCommand(usage.substringBefore(" <").substringBefore(" [")))
            sender.sendMessage(line)
        }
    }

    companion object {
        const val NAME = "yuimorph"
        const val ALIAS = "ym"
    }
}
