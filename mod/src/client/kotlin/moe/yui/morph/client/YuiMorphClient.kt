package moe.yui.morph.client

import com.mojang.blaze3d.platform.InputConstants
import moe.yui.morph.client.debug.DriftDiagnostics
import moe.yui.morph.client.gui.ActionScreen
import moe.yui.morph.client.gui.DisguiseSelectScreen
import moe.yui.morph.client.net.MorphPayload
import moe.yui.morph.protocol.C2SHello
import moe.yui.morph.protocol.C2SUnmorph
import moe.yui.morph.protocol.PacketCodec
import moe.yui.morph.protocol.Protocol
import moe.yui.morph.protocol.ProtocolException
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.client.KeyMapping
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import org.slf4j.LoggerFactory

object YuiMorphClient : ClientModInitializer {
    private val logger = LoggerFactory.getLogger("YuiMorph")

    private val category = KeyMapping.Category.register(Identifier.fromNamespaceAndPath("yuimorph", "main"))
    private lateinit var openMenu: KeyMapping
    private lateinit var openActions: KeyMapping
    private lateinit var unmorph: KeyMapping

    override fun onInitializeClient() {
        registerNetworking()
        registerKeys()
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("yuimorph", "status"), StatusHud)
        ClientTickEvents.END_CLIENT_TICK.register(::onTick)
        logger.info("YuiMorph 客户端已加载（协议版本 {}）", Protocol.VERSION)
    }

    private fun registerNetworking() {
        PayloadTypeRegistry.clientboundPlay().register(MorphPayload.TYPE, MorphPayload.CODEC)
        PayloadTypeRegistry.serverboundPlay().register(MorphPayload.TYPE, MorphPayload.CODEC)

        ClientPlayNetworking.registerGlobalReceiver(MorphPayload.TYPE) { payload, _ ->
            try {
                MorphClientState.handle(PacketCodec.decodeS2C(payload.data))
            } catch (e: ProtocolException) {
                logger.warn("无法解析服务器发来的 YuiMorph 数据：{}", e.message)
            }
        }

        // 进服后握手；服务器没装 YuiMorph 时不会回复，客户端保持原版行为
        ClientPlayConnectionEvents.JOIN.register { _, _, _ ->
            MorphClientState.reset()
            val version = FabricLoader.getInstance().getModContainer("yuimorph").map { it.metadata.version.friendlyString }.orElse("?")
            MorphClientState.send(C2SHello(Protocol.VERSION, version))
        }
        ClientPlayConnectionEvents.DISCONNECT.register { _, _ -> MorphClientState.reset() }
    }

    private fun registerKeys() {
        openMenu = KeyMappingHelper.registerKeyMapping(KeyMapping("key.yuimorph.open_menu", InputConstants.Type.KEYBOARD, InputConstants.KEY_N, category))
        openActions = KeyMappingHelper.registerKeyMapping(KeyMapping("key.yuimorph.actions", InputConstants.Type.KEYBOARD, InputConstants.KEY_G, category))
        unmorph = KeyMappingHelper.registerKeyMapping(KeyMapping("key.yuimorph.unmorph", InputConstants.Type.KEYBOARD, InputConstants.KEY_DOWN, category))
    }

    private fun onTick(mc: Minecraft) {
        ProxyTicker.tick(mc)
        DriftDiagnostics.tick(mc)

        while (openMenu.consumeClick()) ifAccepted(mc) { mc.gui.setScreen(DisguiseSelectScreen()) }
        while (openActions.consumeClick()) ifAccepted(mc) {
            if (MorphClientState.self.disguiseId == null) mc.player?.sendOverlayMessage(Component.translatable("yuimorph.message.not_disguised"))
            else mc.gui.setScreen(ActionScreen())
        }
        while (unmorph.consumeClick()) ifAccepted(mc) {
            if (MorphClientState.self.disguiseId != null) MorphClientState.send(C2SUnmorph)
        }
    }

    private inline fun ifAccepted(mc: Minecraft, block: () -> Unit) {
        if (MorphClientState.accepted) block()
        else mc.player?.sendOverlayMessage(Component.translatable("yuimorph.message.server_unsupported"))
    }
}
