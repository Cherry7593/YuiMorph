package moe.yui.morph

import moe.yui.morph.command.MorphCommands
import moe.yui.morph.config.MorphConfig
import moe.yui.morph.disguise.DisguiseManager
import moe.yui.morph.i18n.Messages
import moe.yui.morph.listener.PlayerListener
import moe.yui.morph.protocol.Protocol
import moe.yui.morph.render.DisguiseRenderer
import moe.yui.morph.storage.PlayerDataStore
import moe.yui.morph.util.runAsync
import org.bukkit.plugin.java.JavaPlugin
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

    override fun onEnable() {
        saveDefaultConfig()
        reload()

        store = PlayerDataStore(dataFolder, logger)
        disguises = DisguiseManager(this)
        renderer = DisguiseRenderer(disguises.sessions, logger).also { it.register() }

        server.pluginManager.registerEvents(PlayerListener(this), this)
        MorphCommands(this).register()

        // 插件在玩家在线时启用（例如热重载），补加载数据
        server.onlinePlayers.forEach { p -> runAsync { store.load(p.uniqueId) } }

        logger.info("YuiMorph ${pluginMeta.version} 已启用（协议版本 ${Protocol.VERSION}）")
    }

    override fun onDisable() {
        if (::renderer.isInitialized) {
            restoreAll()
            renderer.unregister()
        }
        if (::store.isInitialized) store.shutdown()
    }

    /**
     * 停用时尽量把伪装中的玩家恢复成本体。
     * Folia 停用阶段不能调度区域任务，只能在当前线程尽力而为；失败时玩家重进或换区块即可恢复。
     */
    private fun restoreAll() {
        for (session in disguises.sessions.all().toList()) {
            val player = server.getPlayer(session.playerId) ?: continue
            try {
                disguises.unmorph(player, silent = true)
            } catch (e: Exception) {
                logger.log(Level.FINE, "停用时恢复 ${player.name} 失败", e)
            }
        }
    }

    fun reload() {
        reloadConfig()
        morphConfig = MorphConfig.load(config)
        messages = Messages.load(this, morphConfig.language)
    }
}
