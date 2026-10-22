package moe.yui.morph.client.arm;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.OrderedSubmitNodeCollector;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.MovingBlockRenderState;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.gizmos.DrawableGizmoPrimitives;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.state.level.QuadParticleRenderState;
import net.minecraft.client.renderer.texture.UvMapping;
import net.minecraft.client.resources.model.geometry.ItemQuads;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.joml.Quaternionf;
import org.jspecify.annotations.Nullable;

/**
 * 只记录、不渲染的 collector：让实体 renderer 照常走一遍 {@code submit()}，
 * 记下它实际提交的模型（变种 / 幼体模型、贴图、染色、各个图层），用来画第一人称手臂。
 * <p>
 * 只关心模型；名字牌、阴影、物品、方块等全部丢弃。装备（盔甲、鞍、鞘翅）在
 * {@code EquipmentLayerRendererMixin} 里直接跳过，和原版第一人称手不显示盔甲一致。
 * <p>
 * MC 升级给接口加方法时这里会编译失败，按新接口补一个空实现即可。
 */
public final class CapturingCollector implements SubmitNodeCollector {
    public record Entry(Model<?> model, RenderType renderType, int tint, @Nullable UvMapping uvMapping) {
    }

    public final List<Entry> entries = new ArrayList<>();

    @Override
    public OrderedSubmitNodeCollector order(final int order) {
        return this;
    }

    @Override
    public <S> void submitModel(final Model<? super S> model, final S state, final PoseStack poseStack, final RenderType renderType,
                                final int lightCoords, final int overlayCoords, final int tintedColor, final @Nullable UvMapping uvMapping,
                                final int outlineColor) {
        entries.add(new Entry(model, renderType, tintedColor, uvMapping));
    }

    // ---------------- 以下全部丢弃 ----------------

    @Override
    public void submitShadow(final PoseStack poseStack, final float radius, final List<EntityRenderState.ShadowPiece> pieces) {
    }

    @Override
    public void submitNameTag(final PoseStack poseStack, final @Nullable Vec3 nameTagAttachment, final int offset, final Component name,
                              final boolean seeThrough, final int lightCoords, final CameraRenderState camera) {
    }

    @Override
    public void submitText(final PoseStack poseStack, final float x, final float y, final FormattedCharSequence string, final boolean dropShadow,
                           final Font.DisplayMode displayMode, final int lightCoords, final int color, final int backgroundColor,
                           final int outlineColor) {
    }

    @Override
    public void submitTextBackground(final PoseStack poseStack, final float x0, final float y0, final float x1, final float y1, final int color,
                                     final Font.DisplayMode displayMode, final int lightCoords) {
    }

    @Override
    public void submitFlame(final PoseStack poseStack, final EntityRenderState renderState, final Quaternionf rotation) {
    }

    @Override
    public void submitLeash(final PoseStack poseStack, final EntityRenderState.LeashState leashState) {
    }

    @Override
    public <S> void submitCrumblingOverlay(final Model<? super S> model, final S state, final PoseStack poseStack, final RenderType renderType,
                                           final int lightCoords, final int overlayCoords, final int tintedColor,
                                           final ModelFeatureRenderer.CrumblingOverlay crumblingOverlay) {
    }

    @Override
    public void submitMovingBlock(final PoseStack poseStack, final MovingBlockRenderState movingBlockRenderState, final int outlineColor) {
    }

    @Override
    public void submitBlockModel(final PoseStack poseStack, final RenderType renderType, final List<BlockStateModelPart> parts, final int[] tintLayers,
                                 final int lightCoords, final int overlayCoords, final int outlineColor) {
    }

    @Override
    public void submitBreakingBlockModel(final PoseStack poseStack, final List<BlockStateModelPart> parts, final int progress,
                                         final boolean isBlockTranslucent) {
    }

    @Override
    public void submitShapeOutline(final PoseStack poseStack, final VoxelShape shape, final RenderType renderType, final int color, final float width,
                                   final boolean afterTerrain) {
    }

    @Override
    public void submitItem(final PoseStack poseStack, final ItemDisplayContext displayContext, final int lightCoords, final int overlayCoords,
                           final int outlineColor, final int[] tintLayers, final ItemQuads quads, final ItemStackRenderState.FoilType foilType) {
    }

    @Override
    public void submitCustomGeometry(final PoseStack poseStack, final RenderType renderType,
                                     final SubmitNodeCollector.CustomGeometryRenderer customGeometryRenderer) {
    }

    @Override
    public void submitQuadParticleGroup(final QuadParticleRenderState particles) {
    }

    @Override
    public void submitGizmoPrimitives(final DrawableGizmoPrimitives.Group group, final CameraRenderState camera, final boolean onTop) {
    }
}
