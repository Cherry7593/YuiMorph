package moe.yui.morph.client.mixin;

import moe.yui.morph.client.MorphClientState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * 代理实体和玩家完全重叠，必须对本地的物理和准星「不存在」。
 *
 * 推挤（getPushableEntities）、实体碰撞（getEntityCollisions）、准星 / 弹射物选中（ProjectileUtil）、
 * 玩家接触（Player.aiStep）在 26.3 里都经过这个方法，所以在这里统一处理，
 * 不受各个生物类重写 isPushable / isPickable / canBeCollidedWith 的影响：
 * - 任何查询结果都不包含代理实体
 * - 代理实体自己发起的查询（例如它的 pushEntities）一律返回空
 */
@Mixin(Level.class)
public abstract class LevelMixin {
    private static final String GET_ENTITIES =
            "getEntities(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/AABB;Ljava/util/function/Predicate;)Ljava/util/List;";

    @Inject(method = GET_ENTITIES, at = @At("HEAD"), cancellable = true)
    private void yuimorph$proxyQueriesNothing(Entity except, AABB box, Predicate<? super Entity> predicate,
                                              CallbackInfoReturnable<List<Entity>> cir) {
        if (except != null && MorphClientState.isProxy(except)) cir.setReturnValue(new ArrayList<>());
    }

    @ModifyVariable(method = GET_ENTITIES, at = @At("HEAD"), argsOnly = true)
    private Predicate<? super Entity> yuimorph$excludeProxy(Predicate<? super Entity> predicate) {
        return MorphClientState.excludeProxy(predicate);
    }
}
