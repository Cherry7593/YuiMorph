package moe.yui.morph.client

import moe.yui.morph.protocol.Protocol
import net.fabricmc.api.ClientModInitializer
import org.slf4j.LoggerFactory

object YuiMorphClient : ClientModInitializer {
    private val logger = LoggerFactory.getLogger("YuiMorph")

    override fun onInitializeClient() {
        logger.info("YuiMorph 客户端已加载（协议版本 {}）", Protocol.VERSION)
    }
}
