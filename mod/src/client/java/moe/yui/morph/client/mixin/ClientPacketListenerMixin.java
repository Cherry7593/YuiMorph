package moe.yui.morph.client.mixin;

import moe.yui.morph.client.ui.PreviewController;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 服务器把预览外观作为一个普通的实体元数据包发到「预览实体 ID」上。
 * 这个实体只存在于界面里，原版找不到它会直接忽略，所以在这里接住并应用到预览实体。
 */
@Mixin(ClientPacketListener.class)
public abstract class ClientPacketListenerMixin {
    @Inject(method = "handleSetEntityData", at = @At("HEAD"), cancellable = true)
    private void yuimorph$previewMetadata(ClientboundSetEntityDataPacket packet, CallbackInfo ci) {
        if (PreviewController.handleMetadata(packet.id(), packet.packedItems())) ci.cancel();
    }
}
