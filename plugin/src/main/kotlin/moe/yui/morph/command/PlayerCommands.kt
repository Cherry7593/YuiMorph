package moe.yui.morph.command

import com.mojang.brigadier.arguments.StringArgumentType
import io.papermc.paper.command.brigadier.Commands
import moe.yui.morph.Permissions
import moe.yui.morph.YuiMorphPlugin
import moe.yui.morph.disguise.DisguiseManager
import moe.yui.morph.gui.DisguiseMenu
import moe.yui.morph.i18n.ph
import org.bukkit.entity.Player

/** 玩家命令：`/ym morph <伪装>`、`/ym unmorph`、`/ym property [属性] [值]`。 */
class PlayerCommands(plugin: YuiMorphPlugin) : CommandModule(plugin) {

    override fun nodes(): List<Sub> = listOf(morph(), unmorph(), property())

    override fun help() = listOf(
        HelpEntry("", "help.open-menu", Permissions.MORPH),
        HelpEntry("morph <伪装>", "help.morph", Permissions.MORPH),
        HelpEntry("unmorph", "help.unmorph", Permissions.UNMORPH),
        HelpEntry("property [属性] [值]", "help.property", Permissions.PROPERTY),
    )

    // ---------------- morph <伪装>（不带参数打开选择界面） ----------------

    private fun morph(): Sub = literal("morph")
        .requires { it.sender.hasPermission(Permissions.MORPH) }
        .executes { ctx -> onPlayerThread(ctx) { DisguiseMenu.open(plugin, it) } }
        .then(
            Commands.argument("disguise", StringArgumentType.greedyString())
                .suggests(suggestions { ctx -> playerOf(ctx)?.let(manager::available).orEmpty().map { it.toString() } })
                .executes { ctx ->
                    val raw = StringArgumentType.getString(ctx, "disguise")
                    onPlayerThread(ctx) { p -> parseOrReport(p, raw)?.let { manager.morph(p, it) } }
                },
        )

    private fun unmorph(): Sub = literal("unmorph")
        .requires { it.sender.hasPermission(Permissions.UNMORPH) }
        .executes { ctx -> onPlayerThread(ctx) { manager.unmorph(it) } }

    // ---------------- property [属性] [值] ----------------

    private fun property(): Sub = literal("property")
        .requires { it.sender.hasPermission(Permissions.PROPERTY) }
        .executes { ctx -> withPlayer(ctx, ::listProperties) }
        .then(
            Commands.argument("key", StringArgumentType.word())
                .suggests(suggestions { ctx -> currentKeys(playerOf(ctx)) })
                // 只写属性名：重置为默认
                .executes { ctx ->
                    val key = StringArgumentType.getString(ctx, "key")
                    onPlayerThread(ctx) { manager.setProperty(it, key, null) }
                }
                .then(
                    Commands.argument("value", StringArgumentType.greedyString())
                        .suggests(suggestions { ctx ->
                            val session = playerOf(ctx)?.let(manager::current) ?: return@suggestions emptyList()
                            val key = plugin.properties.key(DisguiseManager.typeKey(session.disguise), StringArgumentType.getString(ctx, "key"))
                            key?.let(plugin.properties::values).orEmpty()
                        })
                        .executes { ctx ->
                            val key = StringArgumentType.getString(ctx, "key")
                            val value = StringArgumentType.getString(ctx, "value")
                            onPlayerThread(ctx) { manager.setProperty(it, key, value) }
                        },
                ),
        )

    private fun currentKeys(player: Player?): List<String> {
        val session = player?.let(manager::current) ?: return emptyList()
        return plugin.properties.keys(DisguiseManager.typeKey(session.disguise)).map { it.name }
    }

    private fun listProperties(player: Player) {
        val session = manager.current(player) ?: return msg.send(player, "property.not-disguised")
        val keys = plugin.properties.keys(DisguiseManager.typeKey(session.disguise))
        val name = ph("disguise", session.disguise.displayName())
        if (keys.isEmpty()) return msg.send(player, "property.none", name)

        msg.send(player, "property.header", name)
        keys.forEach { key ->
            val current = session.properties[key.name] ?: msg.raw("property.default")
            val values = plugin.properties.values(key)?.joinToString(" / ") ?: msg.raw("property.free-text")
            player.sendMessage(msg.get("property.entry", ph("key", key.name), ph("value", current), ph("values", values)))
        }
    }
}
