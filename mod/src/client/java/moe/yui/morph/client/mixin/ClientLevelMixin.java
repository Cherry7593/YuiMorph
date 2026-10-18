package moe.yui.morph.client.mixin;

import moe.yui.morph.client.MorphClientState;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 代理实体不随世界 tick，由 ProxyTicker 在玩家移动之后手动 tick。 */
@Mixin(ClientLevel.class)
public abstract class ClientLevelMixin {
    @Inject(method = "tickNonPassenger", at = @At("HEAD"), cancellable = true)
    private void yuimorph$skipProxy(Entity entity, CallbackInfo ci) {
        if (MorphClientState.isProxy(entity)) ci.cancel();
    }
}
