package moe.yui.morph.command

import com.mojang.brigadier.Command
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.builder.ArgumentBuilder
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.suggestion.SuggestionProvider
import com.mojang.brigadier.tree.LiteralCommandNode
import io.papermc.paper.command.brigadier.CommandSourceStack
import io.papermc.paper.command.brigadier.Commands
import io.papermc.paper.command.brigadier.argument.ArgumentTypes
import io.papermc.paper.command.brigadier.argument.resolvers.selector.PlayerSelectorArgumentResolver
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents
import moe.yui.morph.Permissions
import moe.yui.morph.YuiMorphPlugin
import moe.yui.morph.disguise.DisguiseId
import moe.yui.morph.i18n.ph
import moe.yui.morph.util.runOnRegion
import org.bukkit.entity.Player

private typealias Ctx = CommandContext<CommandSourceStack>

/**
 * 命令注册。
 *
 * Folia 下命令执行线程不确定（玩家命令在玩家线程，控制台在全局线程），
 * 所以涉及玩家状态的操作统一用 `runOnRegion` 切到目标玩家线程。
 */
class MorphCommands(private val plugin: YuiMorphPlugin) {
    private val manager get() = plugin.disguises
    private val msg get() = plugin.messages

    fun register() {
        plugin.lifecycleManager.registerEventHandler(LifecycleEvents.COMMANDS) { event ->
            val r = event.registrar()
            r.register(morph(), "伪装成生物或玩家")
            r.register(unmorph(), "解除伪装")
            r.register(admin(), "YuiMorph 管理命令", listOf("ym"))
        }
    }

    // ---------------- /morph [伪装] ----------------

    private fun morph(): LiteralCommandNode<CommandSourceStack> = Commands.literal("morph")
        .requires { it.sender.hasPermission(Permissions.MORPH) }
        .executes { ctx -> withPlayer(ctx) { listUnlocked(it) } }
        .then(
            Commands.argument("disguise", StringArgumentType.greedyString())
                .suggests(availableSuggestions)
                .executes { ctx ->
                    withPlayer(ctx) { player ->
                        val raw = StringArgumentType.getString(ctx, "disguise")
                        val id = parseOrReport(player, raw) ?: return@withPlayer
                        player.runOnRegion(plugin) { manager.morph(player, id) }
                    }
                },
        )
        .build()

    private fun unmorph(): LiteralCommandNode<CommandSourceStack> = Commands.literal("unmorph")
        .requires { it.sender.hasPermission(Permissions.UNMORPH) }
        .executes { ctx -> withPlayer(ctx) { p -> p.runOnRegion(plugin) { manager.unmorph(p) } } }
        .build()

    private fun listUnlocked(player: Player) {
        val list = manager.available(player)
        if (list.isEmpty()) {
            msg.send(player, "list.empty")
            return
        }
        msg.send(player, "list.header", ph("count", list.size.toString()))
        list.forEach { player.sendMessage(msg.get("list.entry", ph("disguise", it.displayName()), ph("id", it.toString()))) }
    }

    // ---------------- /yuimorph ----------------

    private fun admin(): LiteralCommandNode<CommandSourceStack> = Commands.literal("yuimorph")
        .requires { it.sender.hasPermission(Permissions.ADMIN) }
        .then(Commands.literal("reload").executes { ctx ->
            plugin.reload()
            msg.send(ctx.source.sender, "admin.reloaded")
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
        .then(Commands.literal("unmorph").then(Commands.argument("targets", ArgumentTypes.players()).executes { ctx ->
            forEachTarget(ctx) { target ->
                if (manager.unmorph(target)) msg.send(ctx.source.sender, "admin.force-unmorphed", ph("player", target.name))
            }
        }))
        .build()

    /** `<sub> <targets> <disguise>`，[action] 在每个目标玩家的线程执行。 */
    private fun targetsWithDisguise(
        sub: String,
        action: (sender: org.bukkit.command.CommandSender, target: Player, id: DisguiseId) -> Unit,
    ): ArgumentBuilder<CommandSourceStack, *> = Commands.literal(sub).then(
        Commands.argument("targets", ArgumentTypes.players()).then(
            Commands.argument("disguise", StringArgumentType.greedyString())
                .suggests(allSuggestions)
                .executes { ctx ->
                    val sender = ctx.source.sender
                    val id = parseOrReport(sender, StringArgumentType.getString(ctx, "disguise")) ?: return@executes 0
                    forEachTarget(ctx) { target -> action(sender, target, id) }
                },
        ),
    )

    // ---------------- 工具 ----------------

    private fun forEachTarget(ctx: Ctx, action: (Player) -> Unit): Int {
        val targets = ctx.getArgument("targets", PlayerSelectorArgumentResolver::class.java).resolve(ctx.source)
        if (targets.isEmpty()) {
            msg.send(ctx.source.sender, "admin.no-target")
            return 0
        }
        targets.forEach { t -> t.runOnRegion(plugin) { action(t) } }
        return targets.size
    }

    private inline fun withPlayer(ctx: Ctx, block: (Player) -> Unit): Int {
        val player = ctx.source.executor as? Player ?: ctx.source.sender as? Player
        if (player == null) {
            msg.send(ctx.source.sender, "morph.players-only")
            return 0
        }
        block(player)
        return Command.SINGLE_SUCCESS
    }

    private fun parseOrReport(sender: org.bukkit.command.CommandSender, raw: String): DisguiseId? =
        DisguiseId.parse(raw) ?: run {
            msg.send(sender, "morph.invalid-id", ph("id", raw))
            null
        }

    /** 补全：玩家可用的伪装。 */
    private val availableSuggestions = SuggestionProvider<CommandSourceStack> { ctx, builder ->
        val player = ctx.source.executor as? Player ?: ctx.source.sender as? Player
        if (player != null) suggest(builder.remainingLowerCase, manager.available(player).map { it.toString() }, builder::suggest)
        builder.buildFuture()
    }

    /** 补全：所有生物（管理员用）。 */
    private val allSuggestions = SuggestionProvider<CommandSourceStack> { _, builder ->
        suggest(builder.remainingLowerCase, DisguiseId.allMobs().map { it.toString() }, builder::suggest)
        builder.buildFuture()
    }

    /** 同时匹配完整 ID 和省略 `minecraft:` 的写法。 */
    private inline fun suggest(input: String, ids: List<String>, add: (String) -> Unit) {
        ids.filter { it.lowercase().startsWith(input) || it.substringAfter(':').lowercase().startsWith(input) }
            .take(100)
            .forEach(add)
    }
}
