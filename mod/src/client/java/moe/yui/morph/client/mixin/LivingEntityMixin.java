package moe.yui.morph.client.mixin;

import moe.yui.morph.client.MorphClientState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** LivingEntity 重写了 isPushable，需要单独处理。 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {
    @Inject(method = "isPushable", at = @At("HEAD"), cancellable = true)
    private void yuimorph$notPushable(CallbackInfoReturnable<Boolean> cir) {
        if (MorphClientState.isProxy((Entity) (Object) this)) cir.setReturnValue(false);
    }
}
