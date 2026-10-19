package moe.yui.morph.client.mixin;

import net.minecraft.client.entity.ClientMannequin;
import net.minecraft.world.entity.player.PlayerSkin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * ClientMannequin 设置档案后会异步查询皮肤，但只在自己的 tick() 里把结果应用上。
 * 界面里的预览实体不 tick，所以由我们检查查询结果并应用。
 */
@Mixin(ClientMannequin.class)
public interface ClientMannequinAccessor {
    @Accessor("skinLookup")
    CompletableFuture<Optional<PlayerSkin>> yuimorph_getSkinLookup();

    @Accessor("skinLookup")
    void yuimorph_setSkinLookup(CompletableFuture<Optional<PlayerSkin>> lookup);

    @Invoker("setSkin")
    void yuimorph_setSkin(PlayerSkin skin);
}
