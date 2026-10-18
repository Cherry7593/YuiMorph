package moe.yui.morph.client.mixin;

import moe.yui.morph.client.debug.DriftDiagnostics;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 临时诊断：记录谁在修改本地玩家的速度（只在 DriftDiagnostics.tracing 为 true 的那一 tick 生效）。 */
@Mixin(Entity.class)
public abstract class VelocityProbeMixin {
    @Inject(method = "push(DDD)V", at = @At("HEAD"))
    private void yuimorph$probePush(double x, double y, double z, CallbackInfo ci) {
        if (DriftDiagnostics.getTracing()) DriftDiagnostics.onVelocityChange((Entity) (Object) this, "push", x, y, z);
    }

    @Inject(method = "addDeltaMovement", at = @At("HEAD"))
    private void yuimorph$probeAdd(Vec3 v, CallbackInfo ci) {
        if (DriftDiagnostics.getTracing()) DriftDiagnostics.onVelocityChange((Entity) (Object) this, "addDeltaMovement", v.x, v.y, v.z);
    }

    @Inject(method = "setDeltaMovement(Lnet/minecraft/world/phys/Vec3;)V", at = @At("HEAD"))
    private void yuimorph$probeSet(Vec3 v, CallbackInfo ci) {
        if (DriftDiagnostics.getTracing()) DriftDiagnostics.onVelocityChange((Entity) (Object) this, "setDeltaMovement", v.x, v.y, v.z);
    }
}
