package moe.yui.morph.render

import com.github.retrooper.packetevents.protocol.entity.cat.CatVariants
import com.github.retrooper.packetevents.protocol.entity.chicken.ChickenVariants
import com.github.retrooper.packetevents.protocol.entity.cow.CowVariants
import com.github.retrooper.packetevents.protocol.entity.data.EntityData
import com.github.retrooper.packetevents.protocol.entity.data.EntityDataType
import com.github.retrooper.packetevents.protocol.entity.data.EntityDataTypes
import com.github.retrooper.packetevents.protocol.entity.frog.FrogVariants
import com.github.retrooper.packetevents.protocol.entity.nautilus.ZombieNautilusVariants
import com.github.retrooper.packetevents.protocol.entity.pig.PigVariants
import com.github.retrooper.packetevents.protocol.entity.villager.VillagerData
import com.github.retrooper.packetevents.protocol.entity.villager.profession.VillagerProfessions
import com.github.retrooper.packetevents.protocol.entity.villager.type.VillagerTypes
import com.github.retrooper.packetevents.protocol.entity.wolfvariant.WolfVariants
import com.github.retrooper.packetevents.protocol.mapper.MappedEntity
import com.github.retrooper.packetevents.util.mappings.VersionedRegistry
import moe.yui.morph.disguise.property.DataKind
import moe.yui.morph.disguise.property.MetaValue
import moe.yui.morph.disguise.property.RegistryNames
import moe.yui.morph.disguise.property.RegistrySource
import moe.yui.morph.disguise.property.VillagerSpec
import net.kyori.adventure.text.Component
import java.util.Optional

/**
 * 属性元数据 → PacketEvents 的 EntityData。
 * 注册表变种使用 PacketEvents 的 TYPED 类型，由它按客户端同步的注册表映射网络 ID。
 */
object PeMetadata : RegistryNames {
    private fun registry(source: RegistrySource): VersionedRegistry<out MappedEntity> = when (source) {
        RegistrySource.CAT_VARIANT -> CatVariants.getRegistry()
        RegistrySource.WOLF_VARIANT -> WolfVariants.getRegistry()
        RegistrySource.FROG_VARIANT -> FrogVariants.getRegistry()
        RegistrySource.PIG_VARIANT -> PigVariants.getRegistry()
        RegistrySource.COW_VARIANT -> CowVariants.getRegistry()
        RegistrySource.CHICKEN_VARIANT -> ChickenVariants.getRegistry()
        RegistrySource.ZOMBIE_NAUTILUS_VARIANT -> ZombieNautilusVariants.getRegistry()
        RegistrySource.VILLAGER_TYPE -> VillagerTypes.getRegistry()
        RegistrySource.VILLAGER_PROFESSION -> VillagerProfessions.getRegistry()
    }

    override fun names(source: RegistrySource): List<String> = registry(source).entries.map { it.name.key }

    private fun entry(source: RegistrySource, key: String): MappedEntity? = registry(source).getByName("minecraft:$key")

    /** 转换失败（例如注册表里没有这个条目）返回 null，该字段不覆盖。 */
    fun toEntityData(meta: MetaValue): EntityData<*>? {
        val v = meta.value
        val (type, value) = when (meta.kind) {
            DataKind.BYTE -> EntityDataTypes.BYTE to v
            DataKind.INT -> EntityDataTypes.INT to v
            DataKind.BOOLEAN -> EntityDataTypes.BOOLEAN to v
            DataKind.OPTIONAL_TEXT -> EntityDataTypes.OPTIONAL_ADV_COMPONENT to Optional.of(Component.text(v as String))
            DataKind.CAT_VARIANT -> EntityDataTypes.TYPED_CAT_VARIANT to entry(RegistrySource.CAT_VARIANT, v as String)
            DataKind.WOLF_VARIANT -> EntityDataTypes.TYPED_WOLF_VARIANT to entry(RegistrySource.WOLF_VARIANT, v as String)
            DataKind.FROG_VARIANT -> EntityDataTypes.TYPED_FROG_VARIANT to entry(RegistrySource.FROG_VARIANT, v as String)
            DataKind.PIG_VARIANT -> EntityDataTypes.PIG_VARIANT to entry(RegistrySource.PIG_VARIANT, v as String)
            DataKind.COW_VARIANT -> EntityDataTypes.COW_VARIANT to entry(RegistrySource.COW_VARIANT, v as String)
            DataKind.CHICKEN_VARIANT -> EntityDataTypes.CHICKEN_VARIANT to entry(RegistrySource.CHICKEN_VARIANT, v as String)
            DataKind.ZOMBIE_NAUTILUS_VARIANT ->
                EntityDataTypes.ZOMBIE_NAUTILUS_VARIANT to entry(RegistrySource.ZOMBIE_NAUTILUS_VARIANT, v as String)
            DataKind.VILLAGER_DATA -> EntityDataTypes.VILLAGER_DATA to villager(v as VillagerSpec)
        }
        return value?.let { create(meta.index, type, it) }
    }

    private fun villager(spec: VillagerSpec): VillagerData? {
        val type = VillagerTypes.getRegistry().getByName("minecraft:${spec.type}") ?: return null
        val profession = VillagerProfessions.getRegistry().getByName("minecraft:${spec.profession}") ?: return null
        return VillagerData(type, profession, 1)
    }

    @Suppress("UNCHECKED_CAST")
    private fun create(index: Int, type: EntityDataType<*>, value: Any): EntityData<*> =
        EntityData(index, type as EntityDataType<Any>, value)
}
