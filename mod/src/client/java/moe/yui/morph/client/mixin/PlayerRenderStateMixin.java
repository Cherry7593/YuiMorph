package moe.yui.morph.client.mixin;

import moe.yui.morph.client.arm.ProxyStateHolder;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.PlayerRenderState;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 在本地玩家的 render state 上挂代理实体的 render state（第一人称手臂用）。 */
@Mixin(PlayerRenderState.class)
public abstract class PlayerRenderStateMixin implements ProxyStateHolder {
    @Unique
    private @Nullable EntityRenderState yuimorph$proxyState;

    @Override
    public @Nullable EntityRenderState yuimorph_getProxyState() {
        return yuimorph$proxyState;
    }

    @Override
    public void yuimorph_setProxyState(@Nullable EntityRenderState state) {
        yuimorph$proxyState = state;
    }

    @Inject(method = "reset", at = @At("TAIL"))
    private void yuimorph$reset(CallbackInfo ci) {
        yuimorph$proxyState = null;
    }
}
