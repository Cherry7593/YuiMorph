package moe.yui.morph.client.mixin;

import moe.yui.morph.client.MorphClientState;
import moe.yui.morph.client.ProxyTicker;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 代理实体只允许由 ProxyTicker 手动 tick。其他 Mod 绕过 ClientLevel#tickNonPassenger 去 tick 它时
 * （例如 EntityCulling 对被剔除实体执行的 basicTick → aiStep），直接跳过。
 */
@Mixin(LivingEntity.class)
public abstract class ProxyGuardLivingEntityMixin {
    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void yuimorph$onlyManualTick(CallbackInfo ci) {
        if (!ProxyTicker.isManualTicking() && MorphClientState.isProxy((Entity) (Object) this)) ci.cancel();
    }

    @Inject(method = "aiStep", at = @At("HEAD"), cancellable = true)
    private void yuimorph$onlyManualAiStep(CallbackInfo ci) {
        if (!ProxyTicker.isManualTicking() && MorphClientState.isProxy((Entity) (Object) this)) ci.cancel();
    }

    /** 代理实体永远不推挤别人（即使是手动 tick 时）。 */
    @Inject(method = "pushEntities", at = @At("HEAD"), cancellable = true)
    private void yuimorph$noPushEntities(CallbackInfo ci) {
        if (MorphClientState.isProxy((Entity) (Object) this)) ci.cancel();
    }
}
