package moe.yui.morph.client.mixin;

import moe.yui.morph.client.MorphClientState;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 代理实体和玩家重叠，必须对本地物理和准星「隐形」：
 * 不能被选中（否则第一人称准星永远对着它）、不能推人、不能被站上去（例如潜影贝）。
 */
@Mixin(Entity.class)
public abstract class EntityMixin {
    @Inject(method = "isPickable", at = @At("HEAD"), cancellable = true)
    private void yuimorph$notPickable(CallbackInfoReturnable<Boolean> cir) {
        if (MorphClientState.isProxy((Entity) (Object) this)) cir.setReturnValue(false);
    }

    @Inject(method = "isPushable", at = @At("HEAD"), cancellable = true)
    private void yuimorph$notPushable(CallbackInfoReturnable<Boolean> cir) {
        if (MorphClientState.isProxy((Entity) (Object) this)) cir.setReturnValue(false);
    }

    @Inject(method = "canBeCollidedWith", at = @At("HEAD"), cancellable = true)
    private void yuimorph$notCollidable(Entity other, CallbackInfoReturnable<Boolean> cir) {
        if (MorphClientState.isProxy((Entity) (Object) this)) cir.setReturnValue(false);
    }
}
