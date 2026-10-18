package moe.yui.morph.command

import com.mojang.brigadier.arguments.StringArgumentType
import io.papermc.paper.command.brigadier.Commands
import io.papermc.paper.command.brigadier.argument.ArgumentTypes
import io.papermc.paper.command.brigadier.argument.resolvers.selector.PlayerSelectorArgumentResolver
import moe.yui.morph.Permissions
import moe.yui.morph.YuiMorphPlugin

/** 交换请求：`/ym request send|accept|deny <玩家>`。 */
class RequestCommands(plugin: YuiMorphPlugin) : CommandModule(plugin) {
    private val requests get() = plugin.requests

    override fun nodes(): List<Sub> = listOf(request())

    override fun help() = listOf(
        HelpEntry("request send <玩家>", "help.request-send", Permissions.REQUEST_SEND),
        HelpEntry("request accept <玩家>", "help.request-accept", Permissions.REQUEST_ACCEPT),
        HelpEntry("request deny <玩家>", "help.request-deny", Permissions.REQUEST_DENY),
    )

    private fun request(): Sub = literal("request")
        .then(
            literal("send")
                .requires { it.sender.hasPermission(Permissions.REQUEST_SEND) }
                .then(Commands.argument("player", ArgumentTypes.player()).executes { ctx ->
                    withPlayer(ctx) { from ->
                        val to = ctx.getArgument("player", PlayerSelectorArgumentResolver::class.java)
                            .resolve(ctx.source).firstOrNull() ?: return@withPlayer msg.send(from, "admin.no-target")
                        requests.send(from, to)
                    }
                }),
        )
        .then(respond("accept", Permissions.REQUEST_ACCEPT) { p, name -> requests.accept(p, name) })
        .then(respond("deny", Permissions.REQUEST_DENY) { p, name -> requests.deny(p, name) })

    private fun respond(sub: String, permission: String, action: (org.bukkit.entity.Player, String) -> Unit) =
        literal(sub)
            .requires { it.sender.hasPermission(permission) }
            .then(
                Commands.argument("player", StringArgumentType.word())
                    .suggests(suggestions { ctx -> playerOf(ctx)?.let { requests.pendingFor(it.uniqueId) }.orEmpty() })
                    .executes { ctx ->
                        val name = StringArgumentType.getString(ctx, "player")
                        onPlayerThread(ctx) { action(it, name) }
                    },
            )
}
