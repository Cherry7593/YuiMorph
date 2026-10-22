package moe.yui.morph.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import moe.yui.morph.client.arm.CapturingCollector;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.EquipmentLayerRenderer;
import net.minecraft.client.resources.model.EquipmentClientInfo;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.equipment.EquipmentAsset;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 捕获第一人称手臂时跳过所有装备（盔甲、鞍、马铠、鞘翅都走这里），
 * 和原版第一人称手不显示盔甲袖子一致。
 */
@Mixin(EquipmentLayerRenderer.class)
public abstract class EquipmentLayerRendererMixin {
    @Inject(method = "renderLayers(Lnet/minecraft/client/resources/model/EquipmentClientInfo$LayerType;Lnet/minecraft/resources/ResourceKey;Lnet/minecraft/client/model/Model;Ljava/lang/Object;Lnet/minecraft/world/item/ItemStack;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/resources/Identifier;II)V",
            at = @At("HEAD"), cancellable = true)
    private <S> void yuimorph$skipWhenCapturing(EquipmentClientInfo.LayerType layerType, ResourceKey<EquipmentAsset> assetId, Model<? super S> model, S state,
                                                ItemStack itemStack, PoseStack poseStack, SubmitNodeCollector collector, int lightCoords,
                                                @Nullable Identifier playerTextureOverride, int outlineColor, int order, CallbackInfo ci) {
        if (collector instanceof CapturingCollector) ci.cancel();
    }
}
