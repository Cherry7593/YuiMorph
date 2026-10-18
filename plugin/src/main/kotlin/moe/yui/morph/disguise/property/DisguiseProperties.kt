package moe.yui.morph.disguise.property

import java.util.concurrent.ConcurrentHashMap

/** 注册表条目名的来源（生产环境由 PacketEvents 提供，测试中可替换）。 */
fun interface RegistryNames {
    fun names(source: RegistrySource): List<String>
}

/** 某种实体可用的属性：已经按实体布局解析出索引。 */
private class Resolved(val def: PropertyDef, val index: Int)

/**
 * 把玩家设置的属性（`key -> value`）翻译成元数据覆盖值。
 * 所有索引都来自 [EntityLayouts]，不会发出该实体不存在的字段。
 */
class DisguiseProperties(private val layouts: EntityLayouts, private val registries: RegistryNames) {
    private val cache = ConcurrentHashMap<String, List<Resolved>>()
    private val registryCache = ConcurrentHashMap<RegistrySource, List<String>>()

    private fun resolved(typeKey: String): List<Resolved> = cache.computeIfAbsent(typeKey) { key ->
        PropertyDefs.all.mapNotNull { def ->
            // 玩家伪装的名字来自档案，不使用自定义名字
            if (key == PLAYER && def.keys.any { it.name == PropertyDefs.NAME }) return@mapNotNull null
            def.fields.firstNotNullOfOrNull { layouts.indexOf(key, it) }?.let { Resolved(def, it) }
        }
    }

    /** 该实体可设置的属性键。 */
    fun keys(typeKey: String): List<PropertyKey> {
        val keys = resolved(typeKey).flatMap { it.def.keys }.distinctBy { it.name }
        return if (typeKey == PLAYER) keys + PropertyDefs.skinKey else keys
    }

    fun key(typeKey: String, name: String): PropertyKey? = keys(typeKey).firstOrNull { it.name == name.lowercase() }

    /** 可选值；自由文本返回 null。 */
    fun values(key: PropertyKey): List<String>? =
        key.fixed ?: key.registry?.let { src -> registryCache.computeIfAbsent(src) { registries.names(it).distinct().sorted() } }

    /** 校验并规范化属性值；不合法返回 null。 */
    fun normalize(key: PropertyKey, raw: String): String? {
        val value = raw.trim()
        val options = values(key) ?: return value.takeIf { it.isNotEmpty() && it.length <= key.maxLength }
        return options.firstOrNull { it.equals(value, ignoreCase = true) }
    }

    /** 丢掉不适用于该实体或已经不合法的属性（例如从羊切换到猪时的 color）。 */
    fun retain(typeKey: String, props: Map<String, String>): Map<String, String> {
        val valid = keys(typeKey).associateBy { it.name }
        return props.filter { (k, v) -> valid[k]?.let { normalize(it, v) == v } == true }
    }

    /** 计算元数据覆盖值。 */
    fun metadata(typeKey: String, props: Map<String, String>): List<MetaValue> {
        val result = resolved(typeKey).mapNotNull { r ->
            r.def.encode(props)?.let { MetaValue(r.index, r.def.kind, it) }
        }.toMutableList()
        // 玩家伪装默认显示全部皮肤图层（帽子、外套等）
        if (typeKey == PLAYER) {
            layouts.indexOf(PLAYER, "Avatar.DATA_PLAYER_MODE_CUSTOMISATION")?.let {
                result += MetaValue(it, DataKind.BYTE, ALL_SKIN_PARTS)
            }
        }
        return result
    }

    companion object {
        const val PLAYER = "minecraft:player"
        private const val ALL_SKIN_PARTS: Byte = 0x7F
    }
}
