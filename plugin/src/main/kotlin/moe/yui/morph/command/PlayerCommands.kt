package moe.yui.morph.command

import com.mojang.brigadier.arguments.StringArgumentType
import io.papermc.paper.command.brigadier.Commands
import moe.yui.morph.Permissions
import moe.yui.morph.YuiMorphPlugin
import moe.yui.morph.disguise.DisguiseManager
import moe.yui.morph.gui.DisguiseMenu
import moe.yui.morph.i18n.ph
import org.bukkit.entity.Player

/** 玩家命令：`/morph`、`/unmorph`、`/morphprop`。 */
class PlayerCommands(plugin: YuiMorphPlugin) : CommandModule(plugin) {

    override fun commands() = listOf(
        CommandSpec(morph(), "伪装成生物或玩家；不带参数打开选择界面"),
        CommandSpec(unmorph(), "解除伪装"),
        CommandSpec(morphProp(), "设置当前伪装的属性", listOf("mprop")),
    )

    // ---------------- /morph [伪装] ----------------

    private fun morph(): Node = literal("morph")
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
        .build()

    private fun unmorph(): Node = literal("unmorph")
        .requires { it.sender.hasPermission(Permissions.UNMORPH) }
        .executes { ctx -> onPlayerThread(ctx) { manager.unmorph(it) } }
        .build()

    // ---------------- /morphprop [属性] [值] ----------------

    private fun morphProp(): Node = literal("morphprop")
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
        .build()

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
