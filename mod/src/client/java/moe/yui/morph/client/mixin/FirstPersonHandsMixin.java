package moe.yui.morph.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import moe.yui.morph.client.arm.FirstPersonArm;
import net.minecraft.client.renderer.FirstPersonHandsAndItemsRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.state.level.PlayerRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.HumanoidArm;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * 第一人称的手（空主手、拿地图）都经过 renderPlayerHand 里的这两次调用，
 * 伪装中换成伪装的手臂；返回 false 时照常画玩家自己的手。
 */
@Mixin(FirstPersonHandsAndItemsRenderer.class)
public abstract class FirstPersonHandsMixin {
    @WrapOperation(method = "renderPlayerHand", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/entity/player/AvatarRenderer;renderRightHand(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/resources/Identifier;Z)V"))
    private void yuimorph$rightHand(AvatarRenderer<?> renderer, PoseStack poseStack, SubmitNodeCollector collector, int light, Identifier skin, boolean sleeve,
                                    Operation<Void> original, @Local(argsOnly = true) PlayerRenderState playerState) {
        if (!FirstPersonArm.render(playerState, HumanoidArm.RIGHT, poseStack, collector, light)) {
            original.call(renderer, poseStack, collector, light, skin, sleeve);
        }
    }

    @WrapOperation(method = "renderPlayerHand", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/entity/player/AvatarRenderer;renderLeftHand(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/resources/Identifier;Z)V"))
    private void yuimorph$leftHand(AvatarRenderer<?> renderer, PoseStack poseStack, SubmitNodeCollector collector, int light, Identifier skin, boolean sleeve,
                                   Operation<Void> original, @Local(argsOnly = true) PlayerRenderState playerState) {
        if (!FirstPersonArm.render(playerState, HumanoidArm.LEFT, poseStack, collector, light)) {
            original.call(renderer, poseStack, collector, light, skin, sleeve);
        }
    }
}
