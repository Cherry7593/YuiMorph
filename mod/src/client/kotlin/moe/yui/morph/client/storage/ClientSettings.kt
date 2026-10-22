package moe.yui.morph.client.storage

import com.google.gson.GsonBuilder
import net.fabricmc.loader.api.FabricLoader
import org.slf4j.LoggerFactory

/**
 * 全局客户端设置（不分服务器），保存在 `config/yuimorph/settings.json`。
 * 暂时没有设置界面，直接编辑文件后重启游戏生效。
 */
object ClientSettings {
    private val logger = LoggerFactory.getLogger("YuiMorph")
    private val gson = GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create()
    private val file get() = FabricLoader.getInstance().configDir.resolve("yuimorph").resolve("settings.json").toFile()

    enum class NoArmFallback {
        /** 不画手。 */
        HIDE,

        /** 显示玩家自己的手。 */
        VANILLA,
    }

    /** 第一人称显示伪装的手臂；关掉后永远显示玩家自己的手。 */
    var firstPersonArm = true
        private set

    /** 伪装没有可用的手臂（史莱姆、鱼等）时怎么办。 */
    var noArmFallback = NoArmFallback.HIDE
        private set

    /** 磁盘格式；Gson 可能写入 null，所以字段可空。 */
    private class Data(
        var firstPersonArm: Boolean? = null,
        var noArmFallback: String? = null,
    )

    fun load() {
        val f = file
        val data = try {
            if (f.exists()) gson.fromJson(f.readText(Charsets.UTF_8), Data::class.java) ?: Data() else Data()
        } catch (e: Exception) {
            logger.warn("读取 YuiMorph 设置失败，使用默认值：{}", e.toString())
            Data()
        }
        firstPersonArm = data.firstPersonArm ?: true
        noArmFallback = data.noArmFallback?.let { v -> NoArmFallback.entries.firstOrNull { it.name.equals(v, ignoreCase = true) } }
            ?: NoArmFallback.HIDE
        // 写回一次，补上缺少的项，方便玩家找到可以改什么
        try {
            f.parentFile.mkdirs()
            f.writeText(gson.toJson(Data(firstPersonArm, noArmFallback.name.lowercase())), Charsets.UTF_8)
        } catch (e: Exception) {
            logger.warn("保存 YuiMorph 设置失败：{}", e.toString())
        }
    }
}
