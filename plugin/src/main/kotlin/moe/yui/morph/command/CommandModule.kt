package moe.yui.morph.command

import com.mojang.brigadier.Command
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.suggestion.SuggestionProvider
import com.mojang.brigadier.suggestion.SuggestionsBuilder
import com.mojang.brigadier.tree.LiteralCommandNode
import io.papermc.paper.command.brigadier.CommandSourceStack
import io.papermc.paper.command.brigadier.Commands
import io.papermc.paper.command.brigadier.argument.resolvers.selector.PlayerSelectorArgumentResolver
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents
import moe.yui.morph.YuiMorphPlugin
import moe.yui.morph.disguise.DisguiseId
import moe.yui.morph.i18n.ph
import moe.yui.morph.util.runOnRegion
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

typealias Ctx = CommandContext<CommandSourceStack>
typealias Node = LiteralCommandNode<CommandSourceStack>

/** 一条要注册的命令。 */
data class CommandSpec(val node: Node, val description: String, val aliases: List<String> = emptyList())

/**
 * 命令模块的公共工具。
 *
 * Folia 下命令执行线程不确定（玩家命令在玩家线程，控制台在全局线程），
 * 所以涉及玩家状态的操作统一用 `runOnRegion` 切到目标玩家线程。
 */
abstract class CommandModule(protected val plugin: YuiMorphPlugin) {
    protected val manager get() = plugin.disguises
    protected val msg get() = plugin.messages

    abstract fun commands(): List<CommandSpec>

    /** 命令的执行者：`execute as` 时是被指定的实体，否则是发送者。 */
    protected fun playerOf(ctx: Ctx): Player? = ctx.source.executor as? Player ?: ctx.source.sender as? Player

    protected inline fun withPlayer(ctx: Ctx, block: (Player) -> Unit): Int {
        val player = playerOf(ctx) ?: run {
            msg.send(ctx.source.sender, "morph.players-only")
            return 0
        }
        block(player)
        return Command.SINGLE_SUCCESS
    }

    /** 在执行者自己的区域线程运行。 */
    protected fun onPlayerThread(ctx: Ctx, block: (Player) -> Unit): Int = withPlayer(ctx) { p -> p.runOnRegion(plugin) { block(p) } }

    protected fun forEachTarget(ctx: Ctx, name: String = "targets", action: (Player) -> Unit): Int {
        val targets = ctx.getArgument(name, PlayerSelectorArgumentResolver::class.java).resolve(ctx.source)
        if (targets.isEmpty()) {
            msg.send(ctx.source.sender, "admin.no-target")
            return 0
        }
        targets.forEach { t -> t.runOnRegion(plugin) { action(t) } }
        return targets.size
    }

    protected fun parseOrReport(sender: CommandSender, raw: String): DisguiseId? =
        DisguiseId.parse(raw) ?: run {
            msg.send(sender, "morph.invalid-id", ph("id", raw))
            null
        }

    /** 补全：同时匹配完整 ID 和省略命名空间的写法。 */
    protected fun suggest(builder: SuggestionsBuilder, candidates: Iterable<String>) {
        val input = builder.remainingLowerCase
        candidates.asSequence()
            .filter { it.lowercase().startsWith(input) || it.substringAfter(':').lowercase().startsWith(input) }
            .take(100)
            .forEach(builder::suggest)
    }

    protected fun suggestions(candidates: (Ctx) -> Iterable<String>) = SuggestionProvider<CommandSourceStack> { ctx, builder ->
        suggest(builder, candidates(ctx))
        builder.buildFuture()
    }

    /** 所有生物（管理员用）。 */
    protected val allMobSuggestions = suggestions { DisguiseId.allMobs().map { it.toString() } }

    companion object {
        fun registerAll(plugin: YuiMorphPlugin, modules: List<CommandModule>) {
            plugin.lifecycleManager.registerEventHandler(LifecycleEvents.COMMANDS) { event ->
                modules.flatMap { it.commands() }.forEach { event.registrar().register(it.node, it.description, it.aliases) }
            }
        }

        fun literal(name: String) = Commands.literal(name)
    }
}
