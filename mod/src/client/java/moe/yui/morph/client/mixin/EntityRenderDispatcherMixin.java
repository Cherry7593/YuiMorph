package moe.yui.morph.client.mixin;

import moe.yui.morph.client.MorphClientState;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 第三人称隐藏自己的玩家模型（由代理实体代替），第一人称隐藏代理实体。 */
@Mixin(EntityRenderDispatcher.class)
public abstract class EntityRenderDispatcherMixin {
    @Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true)
    private <E extends Entity> void yuimorph$hideSelf(E entity, Frustum frustum, double camX, double camY, double camZ, float partialTick,
                                                      CallbackInfoReturnable<Boolean> cir) {
        if (MorphClientState.shouldHide(entity)) cir.setReturnValue(false);
    }
}
