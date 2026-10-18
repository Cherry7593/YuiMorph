package moe.yui.morph.storage

import com.google.gson.GsonBuilder
import com.google.gson.JsonParseException
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.logging.Level
import java.util.logging.Logger

/** 单个玩家的数据，所有字段线程安全。 */
class PlayerData(unlocked: Collection<String>, properties: Map<String, Map<String, String>>, current: String?) {
    /** 已解锁的伪装 ID（字符串形式，未知 ID 也保留，防止版本回退时丢数据）。 */
    val unlocked: MutableSet<String> = ConcurrentHashMap.newKeySet<String>().apply { addAll(unlocked) }

    /** 每个伪装上次使用的属性：伪装 ID -> (属性键 -> 值)。内层 Map 不可变，修改时整体替换。 */
    val properties: MutableMap<String, Map<String, String>> = ConcurrentHashMap(properties)

    /** 下线时的伪装 ID，进服恢复后清空。 */
    @Volatile
    var current: String? = current
}

/** 磁盘格式。字段名就是 JSON 键，改名前要考虑兼容旧数据。Gson 可能写入 null，所以字段可空。 */
private class PlayerDataFile(
    var version: Int = 1,
    var unlocked: List<String>? = null,
    var properties: Map<String, Map<String, String>>? = null,
    var current: String? = null,
)

/**
 * 每个玩家一个 JSON 文件：`playerdata/<uuid>.json`。
 *
 * - 加载：在玩家登录前（异步线程，可阻塞）调用 [load]
 * - 保存：修改后调用 [save]，写入在单线程 IO 执行器上串行执行，先写临时文件再原子替换
 */
class PlayerDataStore(dataFolder: File, private val logger: Logger) {
    private val dir = File(dataFolder, "playerdata").apply { mkdirs() }
    private val gson = GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create()
    private val cache = ConcurrentHashMap<UUID, PlayerData>()
    private val io: ExecutorService = Executors.newSingleThreadExecutor { Thread(it, "YuiMorph-IO").apply { isDaemon = true } }

    /** 已加载的玩家数据。未加载（不在线）返回 null。 */
    operator fun get(uuid: UUID): PlayerData? = cache[uuid]

    /** 阻塞读取磁盘并缓存。只能在异步线程调用。 */
    fun load(uuid: UUID): PlayerData = cache.computeIfAbsent(uuid) { read(it).let { f -> PlayerData(f.unlocked.orEmpty(), f.properties.orEmpty(), f.current) } }

    private fun read(uuid: UUID): PlayerDataFile {
        val file = file(uuid)
        if (!file.exists()) return PlayerDataFile()
        return try {
            gson.fromJson(file.readText(Charsets.UTF_8), PlayerDataFile::class.java) ?: PlayerDataFile()
        } catch (e: JsonParseException) {
            // 损坏的文件改名备份，避免下一次保存时覆盖掉
            val broken = File(dir, "$uuid.json.broken-${System.currentTimeMillis()}")
            file.renameTo(broken)
            logger.log(Level.SEVERE, "玩家数据损坏，已备份为 ${broken.name}", e)
            PlayerDataFile()
        }
    }

    /** 异步保存玩家当前数据。 */
    fun save(uuid: UUID) {
        val data = cache[uuid] ?: return
        val snapshot = PlayerDataFile(unlocked = data.unlocked.sorted(), properties = data.properties.toSortedMap(), current = data.current)
        io.execute { write(uuid, snapshot) }
    }

    /** 保存并移出缓存（玩家下线时调用）。 */
    fun unload(uuid: UUID) {
        save(uuid)
        cache.remove(uuid)
    }

    private fun write(uuid: UUID, data: PlayerDataFile) {
        try {
            val target = file(uuid).toPath()
            val tmp = Files.createTempFile(dir.toPath(), "$uuid", ".tmp")
            Files.writeString(tmp, gson.toJson(data))
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } catch (e: Exception) {
            logger.log(Level.SEVERE, "保存玩家数据失败：$uuid", e)
        }
    }

    /** 插件停用时调用：保存所有在线玩家数据并等待写入完成。 */
    fun shutdown() {
        cache.keys.forEach(::save)
        io.shutdown()
        if (!io.awaitTermination(10, TimeUnit.SECONDS)) logger.severe("等待玩家数据写入超时，部分数据可能未保存")
    }

    private fun file(uuid: UUID) = File(dir, "$uuid.json")
}
