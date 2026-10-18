package moe.yui.morph.command

import com.mojang.brigadier.arguments.StringArgumentType
import io.papermc.paper.command.brigadier.Commands
import moe.yui.morph.Permissions
import moe.yui.morph.YuiMorphPlugin
import moe.yui.morph.i18n.ph
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.JoinConfiguration
import net.kyori.adventure.text.event.ClickEvent
import net.kyori.adventure.text.event.HoverEvent
import org.bukkit.entity.Player

/** 伪装动作：`/ym action [动作]`。不带参数列出可点击的动作。 */
class ActionCommands(plugin: YuiMorphPlugin) : CommandModule(plugin) {

    override fun nodes(): List<Sub> = listOf(action())

    override fun help() = listOf(HelpEntry("action [动作]", "help.action", Permissions.ACTION))

    private fun action(): Sub = literal("action")
        .requires { it.sender.hasPermission(Permissions.ACTION) }
        .executes { ctx -> onPlayerThread(ctx, ::list) }
        .then(
            Commands.argument("action", StringArgumentType.word())
                .suggests(suggestions { ctx -> playerOf(ctx)?.let(manager::current)?.let(plugin.actions::names).orEmpty() })
                .executes { ctx ->
                    val name = StringArgumentType.getString(ctx, "action")
                    onPlayerThread(ctx) { plugin.actions.play(it, name) }
                },
        )

    private fun list(player: Player) {
        val session = manager.current(player) ?: return msg.send(player, "action.not-disguised")
        val names = plugin.actions.names(session)
        val disguise = ph("disguise", session.disguise.displayName())
        if (names.isEmpty()) return msg.send(player, "action.none", disguise)

        msg.send(player, "action.header", disguise)
        val buttons = names.map { name ->
            val shown = msg.raw("action.names.$name").takeUnless { it == "action.names.$name" } ?: name
            msg.get("action.entry", ph("name", shown))
                .clickEvent(ClickEvent.runCommand("/${RootCommand.ALIAS} action $name"))
                .hoverEvent(HoverEvent.showText(msg.get("action.hover", ph("action", name))))
        }
        player.sendMessage(Component.join(JoinConfiguration.spaces(), buttons))
    }
}
