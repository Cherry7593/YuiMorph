package moe.yui.morph.client.arm;

import net.minecraft.client.renderer.entity.state.EntityRenderState;
import org.jspecify.annotations.Nullable;

/**
 * 由 Mixin 加到 {@code PlayerRenderState} 上：提取阶段存入代理实体的 render state，
 * 提交阶段画第一人称手臂时读取。
 */
public interface ProxyStateHolder {
    @Nullable EntityRenderState yuimorph_getProxyState();

    void yuimorph_setProxyState(@Nullable EntityRenderState state);
}
