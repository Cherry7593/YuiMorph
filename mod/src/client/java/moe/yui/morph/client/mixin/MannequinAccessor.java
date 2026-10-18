package moe.yui.morph.client.mixin;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.world.entity.decoration.Mannequin;
import net.minecraft.world.item.component.ResolvableProfile;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** 玩家伪装的预览用假人（Mannequin）实现：设置按名字解析的档案后，客户端会自己获取皮肤。 */
@Mixin(Mannequin.class)
public interface MannequinAccessor {
    @Accessor("DATA_PROFILE")
    static EntityDataAccessor<ResolvableProfile> yuimorph_getDataProfile() {
        throw new AssertionError();
    }
}
