package moe.yui.morph.i18n

import net.kyori.adventure.audience.Audience
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.plugin.java.JavaPlugin
import java.io.File
import java.io.InputStreamReader

/**
 * 语言文件加载与消息发送。实例不可变，重载时整体替换。
 * 用户文件缺少的键会回落到 jar 内置的同名语言文件，再回落到内置中文。
 */
class Messages private constructor(private val yaml: YamlConfiguration) {
    private val mini = MiniMessage.miniMessage()
    private val prefix: Component = mini.deserialize(yaml.getString("prefix", "")!!)

    /** 解析消息，不带前缀。键不存在时返回键名本身，方便发现漏配。 */
    fun get(key: String, vararg placeholders: TagResolver): Component =
        mini.deserialize(yaml.getString(key) ?: key, *placeholders)

    /** 未解析的原始文本，用于嵌入其他消息的占位符。 */
    fun raw(key: String): String = yaml.getString(key) ?: key

    fun send(target: Audience, key: String, vararg placeholders: TagResolver) {
        target.sendMessage(prefix.append(get(key, *placeholders)))
    }

    companion object {
        private const val FALLBACK = "zh_cn"

        fun load(plugin: JavaPlugin, language: String): Messages {
            val path = "lang/$language.yml"
            val file = File(plugin.dataFolder, path)
            if (!file.exists() && plugin.getResource(path) != null) plugin.saveResource(path, false)

            val yaml = if (file.exists()) YamlConfiguration.loadConfiguration(file) else YamlConfiguration()
            val defaults = bundled(plugin, path) ?: bundled(plugin, "lang/$FALLBACK.yml")
            if (defaults != null) yaml.setDefaults(defaults)
            return Messages(yaml)
        }

        private fun bundled(plugin: JavaPlugin, path: String): YamlConfiguration? =
            plugin.getResource(path)?.use { YamlConfiguration.loadConfiguration(InputStreamReader(it, Charsets.UTF_8)) }
    }
}

/** 常用占位符简写。 */
fun ph(key: String, value: Component): TagResolver = Placeholder.component(key, value)
fun ph(key: String, value: String): TagResolver = Placeholder.unparsed(key, value)
