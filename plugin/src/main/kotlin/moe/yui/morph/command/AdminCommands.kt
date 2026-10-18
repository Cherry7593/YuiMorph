package moe.yui.morph.command

import com.mojang.brigadier.Command
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.builder.ArgumentBuilder
import io.papermc.paper.command.brigadier.CommandSourceStack
import io.papermc.paper.command.brigadier.Commands
import io.papermc.paper.command.brigadier.argument.ArgumentTypes
import moe.yui.morph.Permissions
import moe.yui.morph.YuiMorphPlugin
import moe.yui.morph.disguise.DisguiseId
import moe.yui.morph.i18n.ph
import moe.yui.morph.util.runAsync
import moe.yui.morph.util.runOnRegion
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.JoinConfiguration
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

/**
 * 管理命令 `/yuimorph`（别名 `/ym`）：
 * reload / status / grant / revoke / force / unmorph / query / queryall / lookup / ban / unban / bans
 */
class AdminCommands(plugin: YuiMorphPlugin) : CommandModule(plugin) {

    override fun commands() = listOf(CommandSpec(admin(), "YuiMorph 管理命令", listOf("ym")))

    private fun admin(): Node = literal("yuimorph")
        .requires { it.sender.hasPermission(Permissions.ADMIN) }
        .then(literal("reload").executes { ctx ->
            plugin.reload()
            msg.send(ctx.source.sender, "admin.reloaded")
            Command.SINGLE_SUCCESS
        })
        .then(literal("status").executes { ctx ->
            msg.send(
                ctx.source.sender, "admin.status",
                ph("version", plugin.pluginMeta.version),
                ph("disguised", manager.sessions.all().size.toString()),
                ph("banned", plugin.morphConfig.bannedDisguises.size.toString()),
            )
            Command.SINGLE_SUCCESS
        })
        .then(targetsWithDisguise("grant") { sender, target, id ->
            val key = if (manager.unlock(target, id)) "admin.granted" else "admin.already-granted"
            msg.send(sender, key, ph("player", target.name), ph("disguise", id.displayName()))
        })
        .then(targetsWithDisguise("revoke") { sender, target, id ->
            val key = if (manager.revoke(target, id)) "admin.revoked" else "admin.not-granted"
            msg.send(sender, key, ph("player", target.name), ph("disguise", id.displayName()))
        })
        .then(targetsWithDisguise("force") { sender, target, id ->
            if (manager.morph(target, id, force = true)) {
                msg.send(sender, "admin.forced", ph("player", target.name), ph("disguise", id.displayName()))
            }
        })
        .then(withTargets("unmorph") { sender, target ->
            if (manager.unmorph(target, force = true)) msg.send(sender, "admin.force-unmorphed", ph("player", target.name))
        })
        .then(withTargets("query") { sender, target -> query(sender, target) })
        .then(literal("queryall").executes { ctx -> queryAll(ctx.source.sender); Command.SINGLE_SUCCESS })
        .then(withTargets("lookup") { sender, target -> lookup(sender, target) })
        .then(ban("ban", true))
        .then(ban("unban", false))
        .then(
            literal("import").then(
                literal("feathermorph")
                    .executes { ctx -> importFeatherMorph(ctx.source.sender, apply = false) }
                    .then(literal("confirm").executes { ctx -> importFeatherMorph(ctx.source.sender, apply = true) }),
            ),
        )
        .then(literal("bans").executes { ctx ->
            val bans = plugin.morphConfig.bannedDisguises.sorted()
            if (bans.isEmpty()) msg.send(ctx.source.sender, "admin.bans-empty")
            else msg.send(ctx.source.sender, "admin.bans", ph("list", bans.joinToString(", ")))
            Command.SINGLE_SUCCESS
        })
        .build()

    // ---------------- 查询 ----------------

    private fun query(sender: CommandSender, target: Player) {
        val session = manager.current(target)
        if (session == null) {
            msg.send(sender, "admin.query-none", ph("player", target.name))
            return
        }
        val props = session.properties.entries.joinToString(", ") { "${it.key}=${it.value}" }.ifEmpty { "-" }
        msg.send(
            sender, "admin.query",
            ph("player", target.name), ph("disguise", session.disguise.displayName()),
            ph("id", session.disguise.toString()), ph("properties", props),
        )
    }

    private fun queryAll(sender: CommandSender) {
        val sessions = manager.sessions.all().toList()
        if (sessions.isEmpty()) return msg.send(sender, "admin.queryall-empty")
        msg.send(sender, "admin.queryall-header", ph("count", sessions.size.toString()))
        sessions.forEach { s ->
            val name = plugin.server.getPlayer(s.playerId)?.name ?: s.playerId.toString()
            sender.sendMessage(msg.get("admin.queryall-entry", ph("player", name), ph("disguise", s.disguise.displayName()), ph("id", s.disguise.toString())))
        }
    }

    private fun lookup(sender: CommandSender, target: Player) {
        val unlocked = plugin.store[target.uniqueId]?.unlocked.orEmpty().sorted()
        if (unlocked.isEmpty()) return msg.send(sender, "admin.lookup-empty", ph("player", target.name))
        val names = unlocked.map { raw -> DisguiseId.parse(raw)?.displayName() ?: Component.text(raw) }
        msg.send(
            sender, "admin.lookup",
            ph("player", target.name), ph("count", unlocked.size.toString()),
            ph("list", Component.join(JoinConfiguration.commas(true), names)),
        )
    }

    // ---------------- 数据导入 ----------------

    /** 不带 confirm 只预览；导入在异步线程执行，完成后把结果发给执行者。 */
    private fun importFeatherMorph(sender: CommandSender, apply: Boolean): Int {
        val importer = plugin.importer
        if (!importer.hasSource()) {
            msg.send(sender, "admin.import.no-source", ph("path", importer.sourceDir.path))
            return 0
        }
        msg.send(sender, if (apply) "admin.import.started" else "admin.import.previewing")
        plugin.runAsync {
            val result = try {
                importer.run(apply)
            } catch (e: Exception) {
                msg.send(sender, "admin.import.failed", ph("error", e.toString()))
                return@runAsync
            }
            if (result == null) return@runAsync msg.send(sender, "admin.import.busy")
            val placeholders = arrayOf(
                ph("files", result.files.toString()), ph("players", result.players.toString()),
                ph("skipped", result.skippedFiles.toString()), ph("changed", result.changedPlayers.toString()),
                ph("unlocks", result.addedUnlocks.toString()), ph("restores", result.restoredDisguises.toString()),
                ph("props", result.importedProperties.toString()), ph("backup", result.backup?.name ?: "-"),
            )
            msg.send(sender, if (apply) "admin.import.done" else "admin.import.preview", *placeholders)
        }
        return Command.SINGLE_SUCCESS
    }

    // ---------------- 封禁 ----------------

    /** 封禁 / 解封。参数可以是具体 ID，也可以是 `minecraft:@all`、`player:@all`。 */
    private fun ban(sub: String, banned: Boolean): ArgumentBuilder<CommandSourceStack, *> = literal(sub).then(
        Commands.argument("disguise", StringArgumentType.greedyString())
            .suggests(suggestions {
                if (banned) DisguiseId.allMobs().map { it.toString() } + CATEGORY_WILDCARDS
                else plugin.morphConfig.bannedDisguises
            })
            .executes { ctx ->
                val sender = ctx.source.sender
                val raw = StringArgumentType.getString(ctx, "disguise").trim()
                val entry = if (raw in CATEGORY_WILDCARDS) raw else parseOrReport(sender, raw)?.toString() ?: return@executes 0

                if (!plugin.setBanned(entry, banned)) {
                    msg.send(sender, if (banned) "admin.already-banned" else "admin.not-banned", ph("id", entry))
                    return@executes 0
                }
                msg.send(sender, if (banned) "admin.banned" else "admin.unbanned", ph("id", entry))
                if (banned) kickFromBanned()
                plugin.client.syncAvailableToAll()
                Command.SINGLE_SUCCESS
            },
    )

    /** 封禁后，正在使用被封禁伪装的玩家立即解除伪装。 */
    private fun kickFromBanned() {
        manager.sessions.all().filter { plugin.morphConfig.isBanned(it.disguise) }.forEach { s ->
            val player = plugin.server.getPlayer(s.playerId) ?: return@forEach
            player.runOnRegion(plugin) {
                if (manager.current(player)?.let { plugin.morphConfig.isBanned(it.disguise) } == true) {
                    manager.unmorph(player, silent = true, force = true)
                    msg.send(player, "morph.banned-removed", ph("disguise", s.disguise.displayName()))
                }
            }
        }
    }

    // ---------------- 参数模板 ----------------

    /** `<sub> <targets>`，[action] 在每个目标玩家的线程执行。 */
    private fun withTargets(sub: String, action: (CommandSender, Player) -> Unit): ArgumentBuilder<CommandSourceStack, *> =
        literal(sub).then(
            Commands.argument("targets", ArgumentTypes.players()).executes { ctx ->
                val sender = ctx.source.sender
                forEachTarget(ctx) { action(sender, it) }
            },
        )

    /** `<sub> <targets> <disguise>`，[action] 在每个目标玩家的线程执行。 */
    private fun targetsWithDisguise(
        sub: String,
        action: (CommandSender, Player, DisguiseId) -> Unit,
    ): ArgumentBuilder<CommandSourceStack, *> = literal(sub).then(
        Commands.argument("targets", ArgumentTypes.players()).then(
            Commands.argument("disguise", StringArgumentType.greedyString())
                .suggests(allMobSuggestions)
                .executes { ctx ->
                    val sender = ctx.source.sender
                    val id = parseOrReport(sender, StringArgumentType.getString(ctx, "disguise")) ?: return@executes 0
                    forEachTarget(ctx) { action(sender, it, id) }
                },
        ),
    )

    private companion object {
        val CATEGORY_WILDCARDS = listOf("minecraft:@all", "player:@all")
    }
}
