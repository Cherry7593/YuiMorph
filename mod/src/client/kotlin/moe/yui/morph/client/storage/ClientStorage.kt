package moe.yui.morph.client.storage

import com.google.gson.GsonBuilder
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.client.Minecraft
import org.slf4j.LoggerFactory
import java.io.File

/** 一个预设：伪装 + 属性 + 显示名。 */
data class Preset(val id: String, val properties: Map<String, String>, val label: String)

/**
 * 客户端本地数据（预设、最近使用），按服务器分开保存在 `config/yuimorph/<服务器>.json`。
 * 只在客户端主线程使用。
 */
object ClientStorage {
    private val logger = LoggerFactory.getLogger("YuiMorph")
    private val gson = GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create()
    private val dir get() = FabricLoader.getInstance().configDir.resolve("yuimorph").toFile()

    /** 磁盘格式；Gson 可能写入 null，所以字段可空。 */
    private class Data(
        var presets: MutableList<StoredPreset>? = null,
        var recent: MutableList<String>? = null,
    )

    private class StoredPreset(var id: String? = null, var properties: Map<String, String>? = null, var label: String? = null)

    private var file: File? = null
    private var data = Data()

    private const val MAX_RECENT = 8
    private const val MAX_PRESETS = 54

    /** 进服时调用：按服务器地址切换数据文件。 */
    fun load() {
        val server = Minecraft.getInstance().currentServer?.ip ?: "singleplayer"
        val name = server.lowercase().replace(Regex("[^a-z0-9._-]"), "_")
        val f = File(dir, "$name.json")
        file = f
        data = try {
            if (f.exists()) gson.fromJson(f.readText(Charsets.UTF_8), Data::class.java) ?: Data() else Data()
        } catch (e: Exception) {
            logger.warn("读取 YuiMorph 客户端数据失败，将重新创建：{}", e.toString())
            Data()
        }
    }

    private fun save() {
        val f = file ?: return
        try {
            f.parentFile.mkdirs()
            f.writeText(gson.toJson(data), Charsets.UTF_8)
        } catch (e: Exception) {
            logger.warn("保存 YuiMorph 客户端数据失败：{}", e.toString())
        }
    }

    fun presets(): List<Preset> = data.presets.orEmpty().mapNotNull { p ->
        val id = p.id ?: return@mapNotNull null
        Preset(id, p.properties.orEmpty(), p.label ?: id)
    }

    /** 保存预设；同一个伪装 + 同一组属性不会重复保存。返回 false 表示已存在或已满。 */
    fun addPreset(preset: Preset): Boolean {
        val list = data.presets ?: mutableListOf<StoredPreset>().also { data.presets = it }
        if (list.any { it.id == preset.id && it.properties.orEmpty() == preset.properties }) return false
        if (list.size >= MAX_PRESETS) return false
        list += StoredPreset(preset.id, preset.properties, preset.label)
        save()
        return true
    }

    fun removePreset(index: Int) {
        val list = data.presets ?: return
        if (index in list.indices) {
            list.removeAt(index)
            save()
        }
    }

    fun recent(): List<String> = data.recent.orEmpty()

    fun pushRecent(id: String) {
        val list = data.recent ?: mutableListOf<String>().also { data.recent = it }
        list.remove(id)
        list.add(0, id)
        while (list.size > MAX_RECENT) list.removeAt(list.lastIndex)
        save()
    }
}
