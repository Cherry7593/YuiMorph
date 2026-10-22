package moe.yui.morph.client.mixin;

import moe.yui.morph.client.arm.FirstPersonArm;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.extract.LevelExtractor;
import net.minecraft.client.renderer.state.level.PlayerRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 提取本地玩家状态之后，顺带提取代理实体（第一人称时它不在世界渲染里，不会被提取）。 */
@Mixin(LevelExtractor.class)
public abstract class LevelExtractorMixin {
    @Inject(method = "extractPlayerState", at = @At("TAIL"))
    private void yuimorph$extractProxy(Camera camera, DeltaTracker deltaTracker, float worldPartialTicks, PlayerRenderState state, CallbackInfo ci) {
        FirstPersonArm.extract(state, deltaTracker);
    }
}
