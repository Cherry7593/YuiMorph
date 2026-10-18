package moe.yui.morph.migrate

import moe.yui.morph.YuiMorphPlugin
import moe.yui.morph.disguise.DisguiseId
import moe.yui.morph.disguise.DisguiseManager
import moe.yui.morph.storage.PlayerData
import moe.yui.morph.util.runOnRegion
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.atomic.AtomicBoolean
import java.util.logging.Level

/**
 * 从 FeatherMorph 导入玩家数据（只读取 FeatherMorph，不修改它）。
 *
 * 合并而不是覆盖：解锁取并集；下线伪装只在 YuiMorph 没有记录时写入；属性只在该伪装没有属性时写入。
 * 所以重复导入是安全的，玩家已经在 YuiMorph 里解锁的东西也不会丢。
 */
class FeatherMorphImporter(private val plugin: YuiMorphPlugin) {
    data class Result(
        val files: Int,
        val players: Int,
        val skippedFiles: Int,
        val changedPlayers: Int,
        val addedUnlocks: Int,
        val restoredDisguises: Int,
        val importedProperties: Int,
        val backup: File?,
    )

    private val running = AtomicBoolean(false)

    val sourceDir: File get() = File(plugin.dataFolder.parentFile, "FeatherMorph")

    fun hasSource(): Boolean = File(sourceDir, "playerdata").isDirectory

    /**
     * 执行导入（[apply] 为 false 时只预览，不写入任何东西）。阻塞，只能在异步线程调用。
     * 已经有导入在进行时返回 null。
     */
    fun run(apply: Boolean): Result? {
        if (!running.compareAndSet(false, true)) return null
        try {
            val (players, skipped, files) = readSource()
            val backup = if (apply) plugin.store.backup(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))) else null

            var changed = 0
            var unlocks = 0
            var restores = 0
            var props = 0
            for (fm in players) {
                val merge = { data: PlayerData ->
                    val r = merge(data, fm)
                    unlocks += r.unlocks
                    if (r.restored) restores++
                    if (r.properties) props++
                    if (r.changed) changed++
                    r.changed
                }
                if (apply) {
                    plugin.store.update(fm.uuid, merge)
                } else {
                    // 预览：在数据副本上合并，只统计不写入
                    merge(plugin.store.peek(fm.uuid))
                }
            }

            if (apply) {
                // 在线玩家：刷新客户端 Mod 里的可用伪装列表
                plugin.server.onlinePlayers.forEach { p -> p.runOnRegion(plugin) { plugin.client.syncAvailable(p) } }
                plugin.logger.info(
                    "FeatherMorph 数据导入完成：${players.size} 名玩家，$changed 名有变化，新增解锁 $unlocks 条，" +
                        "下线伪装 $restores 个，属性 $props 组。导入前的数据备份在 ${backup?.name}",
                )
            }
            return Result(files, players.size, skipped, changed, unlocks, restores, props, backup)
        } catch (e: Exception) {
            plugin.logger.log(Level.SEVERE, "导入 FeatherMorph 数据失败", e)
            throw e
        } finally {
            running.set(false)
        }
    }

    private fun readSource(): Triple<List<FeatherMorphPlayer>, Int, Int> {
        val playerFiles = File(sourceDir, "playerdata").listFiles().orEmpty().filter { it.isFile }
        var skipped = 0
        val saved = File(sourceDir, "saved_disguises").listFiles().orEmpty()
            .filter { it.isFile && it.name.endsWith(".json") }
            .mapNotNull { f ->
                val uuid = runCatching { java.util.UUID.fromString(f.name.removeSuffix(".json")) }.getOrNull() ?: return@mapNotNull null
                FeatherMorphFormat.parseSaved(f.readText(Charsets.UTF_8))?.let { uuid to it }
            }.toMap()

        val players = playerFiles.mapNotNull { f ->
            val parsed = runCatching { FeatherMorphFormat.parsePlayer(f.name, f.readText(Charsets.UTF_8)) }.getOrNull()
            if (parsed == null) skipped++
            parsed?.let { p -> saved[p.uuid]?.let { (id, pr) -> p.copy(savedDisguise = id, savedProperties = pr) } ?: p }
        }
        return Triple(players, skipped, playerFiles.size)
    }

    private data class MergeResult(val unlocks: Int, val restored: Boolean, val properties: Boolean) {
        val changed get() = unlocks > 0 || restored || properties
    }

    /** 把一个 FeatherMorph 玩家合并进 YuiMorph 数据。 */
    private fun merge(data: PlayerData, fm: FeatherMorphPlayer): MergeResult {
        var added = 0
        for (id in fm.unlocked) {
            val isPlayer = id.startsWith("player:")
            val exists = if (isPlayer) data.unlocked.any { it.equals(id, ignoreCase = true) } else id in data.unlocked
            if (!exists && data.unlocked.add(id)) added++
        }

        var restored = false
        var propsImported = false
        val saved = fm.savedDisguise?.let(DisguiseId::parse)
        if (saved != null) {
            if (data.current == null) {
                data.current = saved.toString()
                restored = true
            }
            val key = if (saved is DisguiseId.Player) saved.toString().lowercase() else saved.toString()
            val mapped = plugin.properties.retain(DisguiseManager.typeKey(saved), FeatherMorphFormat.mapProperties(fm.savedProperties))
            if (mapped.isNotEmpty() && data.properties[key].isNullOrEmpty()) {
                data.properties[key] = mapped
                propsImported = true
            }
        }
        return MergeResult(added, restored, propsImported)
    }
}
