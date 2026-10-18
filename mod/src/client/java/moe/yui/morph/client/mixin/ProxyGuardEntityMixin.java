package moe.yui.morph.client.mixin;

import moe.yui.morph.client.MorphClientState;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 在「动作」本身上隔离代理实体，不依赖实体是从哪条路径被 tick、用什么方式被查询到的
 * （EntityCulling 会用自己的 basicTick 跑被剔除的实体，Lithium 会替换实体查询）。
 */
@Mixin(Entity.class)
public abstract class ProxyGuardEntityMixin {
    /** 代理实体和任何实体之间都不互相推挤（LivingEntity.push 也会调用到这里）。 */
    @Inject(method = "push(Lnet/minecraft/world/entity/Entity;)V", at = @At("HEAD"), cancellable = true)
    private void yuimorph$noProxyPush(Entity other, CallbackInfo ci) {
        if (MorphClientState.isProxy((Entity) (Object) this) || MorphClientState.isProxy(other)) ci.cancel();
    }

    /** 移动时的实体碰撞：不和代理实体碰撞（覆盖潜影贝、快乐恶魂这类可站立的生物）。 */
    @Inject(method = "canCollideWith", at = @At("HEAD"), cancellable = true)
    private void yuimorph$noProxyCollision(Entity other, CallbackInfoReturnable<Boolean> cir) {
        if (MorphClientState.isProxy(other) || MorphClientState.isProxy((Entity) (Object) this)) cir.setReturnValue(false);
    }
}
