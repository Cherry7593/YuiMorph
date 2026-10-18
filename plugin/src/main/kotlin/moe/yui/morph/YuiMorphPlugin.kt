package moe.yui.morph

import moe.yui.morph.action.ActionManager
import moe.yui.morph.client.ClientBridge
import moe.yui.morph.command.ActionCommands
import moe.yui.morph.command.AdminCommands
import moe.yui.morph.command.CommandModule
import moe.yui.morph.command.PlayerCommands
import moe.yui.morph.command.RequestCommands
import moe.yui.morph.config.MorphConfig
import moe.yui.morph.disguise.DisguiseId
import moe.yui.morph.disguise.DisguiseManager
import moe.yui.morph.disguise.property.DisguiseProperties
import moe.yui.morph.disguise.property.EntityLayouts
import moe.yui.morph.effect.DisguiseEffects
import moe.yui.morph.effect.DisguiseSounds
import moe.yui.morph.effect.EntityTraits
import moe.yui.morph.gui.DisguiseMenu
import moe.yui.morph.i18n.Messages
import moe.yui.morph.listener.PlayerListener
import moe.yui.morph.protocol.Protocol
import moe.yui.morph.render.DisguiseRenderer
import moe.yui.morph.render.PeMetadata
import moe.yui.morph.request.RequestManager
import moe.yui.morph.skin.SkinService
import moe.yui.morph.storage.PlayerDataStore
import moe.yui.morph.util.runAsync
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.plugin.java.JavaPlugin
import java.io.File
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit
import java.util.logging.Level

class YuiMorphPlugin : JavaPlugin() {
    /** 配置和语言是不可变快照，重载时整体替换，任意线程可读。 */
    @Volatile lateinit var morphConfig: MorphConfig
        private set

    @Volatile lateinit var messages: Messages
        private set

    lateinit var store: PlayerDataStore
        private set
    lateinit var disguises: DisguiseManager
        private set
    lateinit var renderer: DisguiseRenderer
        private set
    lateinit var properties: DisguiseProperties
        private set
    lateinit var skins: SkinService
        private set
    lateinit var requests: RequestManager
        private set
    lateinit var effects: DisguiseEffects
        private set
    lateinit var sounds: DisguiseSounds
        private set
    lateinit var layouts: EntityLayouts
        private set
    lateinit var actions: ActionManager
        private set
    lateinit var client: ClientBridge
        private set

    override fun onEnable() {
        saveDefaultConfig()
        reload()

        layouts = getResource("entity-layouts.tsv")?.use(EntityLayouts::load)
            ?: error("插件 jar 缺少 entity-layouts.tsv")
        actions = ActionManager(this)
        properties = DisguiseProperties(layouts, PeMetadata)
        val traits = getResource("entity-traits.tsv")?.use(EntityTraits::load) ?: error("插件 jar 缺少 entity-traits.tsv")
        effects = DisguiseEffects(this, traits)
        skins = SkinService(this) { morphConfig.skinCacheHours * 3_600_000L }.also { it.load() }

        store = PlayerDataStore(dataFolder, logger)
        disguises = DisguiseManager(this)
        sounds = DisguiseSounds(this)
        renderer = DisguiseRenderer(server, disguises.sessions, logger, sounds).also { it.register() }

        client = ClientBridge(this).also { it.register() }
        requests = RequestManager(this)
        server.asyncScheduler.runAtFixedRate(this, { requests.purgeExpired(); sounds.purgeExpired() }, 5, 5, TimeUnit.SECONDS)

        server.pluginManager.registerEvents(PlayerListener(this), this)
        server.pluginManager.registerEvents(DisguiseMenu.MenuListener(), this)
        server.pluginManager.registerEvents(sounds, this)
        server.pluginManager.registerEvents(actions, this)
        CommandModule.registerAll(this, listOf(PlayerCommands(this), ActionCommands(this), RequestCommands(this), AdminCommands(this)))

        // 插件在玩家在线时启用（例如热重载），补加载数据
        server.onlinePlayers.forEach { p -> runAsync { store.load(p.uniqueId) } }

        logger.info("YuiMorph ${pluginMeta.version} 已启用（协议版本 ${Protocol.VERSION}，实体布局 ${layouts.size} 种）")
    }

    override fun onDisable() {
        if (::client.isInitialized) client.unregister()
        if (::renderer.isInitialized) {
            restoreAll()
            renderer.unregister()
        }
        if (::skins.isInitialized) skins.shutdown()
        if (::store.isInitialized) store.shutdown()
    }

    /**
     * 停用时尽量把伪装中的玩家恢复成本体。
     * Folia 停用阶段不能调度区域任务，只能在当前线程尽力而为；失败时玩家重进或换区块即可恢复。
     */
    private fun restoreAll() {
        for (session in disguises.sessions.all().toList()) {
            val player = server.getPlayer(session.playerId) ?: continue
            // 记住伪装，服务器重启 / 插件重载后恢复
            store[player.uniqueId]?.current = session.disguise.toString()
            try {
                disguises.unmorph(player, silent = true, force = true)
            } catch (e: Exception) {
                logger.log(Level.FINE, "停用时恢复 ${player.name} 失败", e)
            }
        }
    }

    /**
     * 修改封禁列表并写回 config.yml。状态没有变化时返回 false。
     * 多线程同时调用时加锁，避免互相覆盖。
     */
    @Synchronized
    fun setBanned(entry: String, banned: Boolean): Boolean {
        val list = config.getStringList("banned-disguises").toMutableList()
        val changed = if (banned) entry !in list && list.add(entry) else list.remove(entry)
        if (!changed) return false
        config.set("banned-disguises", list)
        saveConfig()
        morphConfig = MorphConfig.load(config)
        return true
    }

    /**
     * 配置文件版本落后时，把用户已有的值写进 jar 内置模板再保存：
     * 新增选项带着注释出现在正确的位置，用户的设置保持不变。
     */
    private fun migrateConfig() {
        val template = getResource("config.yml")?.use {
            YamlConfiguration.loadConfiguration(InputStreamReader(it, Charsets.UTF_8))
        } ?: return
        val latest = template.getInt("config-version")
        val current = config.getInt("config-version", 0)
        if (current >= latest) return

        File(dataFolder, "config.yml").copyTo(File(dataFolder, "config.yml.v$current.bak"), overwrite = true)
        for (key in config.getKeys(true)) {
            if (key == "config-version" || config.isConfigurationSection(key) || !template.contains(key)) continue
            template.set(key, config.get(key))
        }
        template.save(File(dataFolder, "config.yml"))
        reloadConfig()
        logger.info("配置文件已从版本 $current 升级到 $latest（旧文件备份为 config.yml.v$current.bak）")
    }

    fun reload() {
        reloadConfig()
        migrateConfig()
        morphConfig = MorphConfig.load(config)
        morphConfig.forcedDisguise?.let { if (DisguiseId.parse(it) == null) logger.warning("forced-disguise 无效：$it") }
        messages = Messages.load(this, morphConfig.language)
    }
}
