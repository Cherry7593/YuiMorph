package moe.yui.morph.client.ui

import moe.yui.morph.client.mixin.ClientMannequinAccessor
import moe.yui.morph.client.mixin.MannequinAccessor
import net.minecraft.client.entity.ClientMannequin
import java.util.Optional
import net.minecraft.world.phys.Vec3
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.inventory.InventoryScreen
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.Identifier
import net.minecraft.world.entity.EntitySpawnReason
import net.minecraft.world.entity.EntitySpawnRequest
import net.minecraft.world.entity.EntityTypes
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.item.component.ResolvableProfile
import com.google.common.collect.ImmutableMultimap
import com.mojang.authlib.GameProfile
import com.mojang.authlib.properties.Property
import com.mojang.authlib.properties.PropertyMap
import moe.yui.morph.protocol.SkinTexture
import java.util.UUID
import org.slf4j.LoggerFactory
import kotlin.math.max
import kotlin.math.min

/**
 * 界面里用的预览实体：只创建、不加入世界，所以不会 tick、不会被别人看到。
 * - 生物：直接创建对应类型
 * - 玩家：用假人（Mannequin）+ 服务器提供的签名皮肤（没有时先显示默认皮肤）
 */
object PreviewEntities {
    private val logger = LoggerFactory.getLogger("YuiMorph")
    private val thumbnails = HashMap<String, LivingEntity>()

    /**
     * 26.x 的实体在加入世界前没有 ID，读取 ID 会抛异常（渲染时会读）。
     * 预览实体永远不加入世界，所以手动分配一段不会和真实实体、代理实体冲突的负数 ID。
     */
    private var nextId = -1_000_000

    /** 创建或渲染失败过的伪装，不再尝试（避免每帧刷错误）。 */
    private val broken = HashSet<String>()

    /** 网格缩略图（默认外观），按伪装 ID 缓存。 */
    fun thumbnail(id: String): LivingEntity? {
        if (id in broken) return null
        thumbnails[id]?.let { return it }
        val entity = create(id, null) ?: run { broken += id; return null }
        thumbnails[id] = entity
        return entity
    }

    /**
     * 新建一个预览实体。玩家伪装可以用 [skinName] 指定另一个玩家的皮肤，
     * [texture] 是该皮肤的纹理（为 null 时从 [SkinCache] 找，再找不到就用默认皮肤）。
     */
    fun create(id: String, skinName: String?, texture: SkinTexture? = null): LivingEntity? {
        val level = Minecraft.getInstance().level ?: return null
        val entity = try {
            if (id.startsWith(PLAYER_PREFIX)) {
                val mannequin = EntityTypes.MANNEQUIN.create(level, SPAWN_REQUEST) ?: return null
                val name = skinName?.takeIf { it.isNotBlank() } ?: id.removePrefix(PLAYER_PREFIX)
                mannequin.entityData.set(MannequinAccessor.yuimorph_getDataProfile(), profile(name, texture ?: SkinCache[name]))
                mannequin
            } else {
                val type = Identifier.tryParse(id)?.let { BuiltInRegistries.ENTITY_TYPE.getOptional(it).orElse(null) } ?: return null
                // create 会做生成检查（例如和平难度下敌对生物返回 null）；预览实体不进世界，跳过检查
                (type.create(level, SPAWN_REQUEST) as? LivingEntity).also {
                    if (it == null) logger.warn("无法创建预览实体 {}：create 返回 null", id)
                }
            }
        } catch (e: Throwable) {
            logger.warn("无法创建预览实体 {}：{}", id, e.toString())
            null
        }
        entity?.setId(nextId--)
        return entity
    }

    /** 有纹理时直接用（已解析的档案，不联网）；没有时只给名字，客户端会显示默认皮肤。 */
    private fun profile(name: String, texture: SkinTexture?): ResolvableProfile {
        if (texture == null) return ResolvableProfile.createUnresolved(name)
        val property = if (texture.signature != null) Property("textures", texture.value, texture.signature)
        else Property("textures", texture.value)
        val uuid = UUID.nameUUIDFromBytes("yuimorph-preview:${name.lowercase()}".toByteArray())
        return ResolvableProfile.createResolved(GameProfile(uuid, name.take(16), PropertyMap(ImmutableMultimap.of("textures", property))))
    }

    /** 皮肤到了以后重建这个玩家的缩略图。 */
    fun invalidatePlayer(name: String) {
        thumbnails.keys.removeIf { it.equals(PLAYER_PREFIX + name, ignoreCase = true) }
    }

    /**
     * 界面打开时每 tick 调用：推进实体年龄（部分动画按年龄计算），
     * 假人还要应用异步加载的皮肤、推进披风物理（否则披风会按初始状态被甩到一边）。
     */
    fun tick(extra: LivingEntity?) {
        val all = if (extra != null) thumbnails.values + extra else thumbnails.values
        for (e in all) {
            e.tickCount++
            if (e is ClientMannequin) {
                applySkin(e)
                e.avatarState().tick(e.position(), Vec3.ZERO)
            }
        }
    }

    /** 等同于 ClientMannequin#tick 里应用皮肤的那一段：查询完成后把皮肤设上去。 */
    private fun applySkin(mannequin: ClientMannequin) {
        val accessor = mannequin as ClientMannequinAccessor
        val lookup = accessor.yuimorph_getSkinLookup() ?: return
        if (!lookup.isDone) return
        accessor.yuimorph_setSkinLookup(null)
        try {
            lookup.getNow(Optional.empty()).ifPresent(accessor::yuimorph_setSkin)
        } catch (e: Exception) {
            logger.warn("预览皮肤加载失败：{}", e.toString())
        }
    }

    /**
     * 在矩形区域内画实体，按碰撞箱自动缩放。模型会转向 ([lookX], [lookY])：
     * 大预览传鼠标位置（跟随鼠标），缩略图传区域中心（正对玩家）。
     */
    fun draw(g: GuiGraphicsExtractor, id: String, entity: LivingEntity, x0: Int, y0: Int, x1: Int, y1: Int, lookX: Float, lookY: Float) {
        if (id in broken) return
        val size = max(entity.bbWidth, entity.bbHeight).coerceAtLeast(0.3f)
        val scale = ((min(x1 - x0, y1 - y0) * 0.72f) / size).toInt().coerceIn(1, 400)
        try {
            InventoryScreen.extractEntityInInventoryFollowsMouse(g, x0, y0, x1, y1, scale, 0.0625f, lookX, lookY, entity)
        } catch (e: Throwable) {
            logger.warn("无法渲染预览实体 {}：{}", id, e.toString())
            broken += id
        }
    }

    /** 换世界 / 断开连接时清空（实体引用了旧的世界）。 */
    fun clear() {
        thumbnails.clear()
        broken.clear()
    }

    const val PLAYER_PREFIX = "player:"

    /** 忽略生成检查（难度、特性开关）：预览实体只在界面里显示。 */
    private val SPAWN_REQUEST = EntitySpawnRequest(EntitySpawnReason.LOAD, true)
}
