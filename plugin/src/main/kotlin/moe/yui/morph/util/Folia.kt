package moe.yui.morph.util

import org.bukkit.entity.Entity
import org.bukkit.plugin.Plugin

/**
 * Folia 调度工具。
 *
 * 规则：读写实体状态必须在实体所在区域线程执行；文件 IO 走异步调度器。
 */

/** 在实体所在区域线程执行；实体已失效（例如玩家下线）时不执行。 */
fun Entity.runOnRegion(plugin: Plugin, block: () -> Unit) {
    scheduler.run(plugin, { block() }, null)
}

/** 在异步线程执行（不得访问世界和实体）。 */
fun Plugin.runAsync(block: () -> Unit) {
    server.asyncScheduler.runNow(this) { block() }
}
