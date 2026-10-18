package moe.yui.morph.disguise.property

import org.bukkit.DyeColor

/** 元数据值的类型。和 PacketEvents 解耦，由 `render.PeMetadata` 转换成实际的 EntityData。 */
enum class DataKind {
    BYTE, INT, BOOLEAN,

    /** 值为 String，显示为自定义名字。 */
    OPTIONAL_TEXT,

    /** 以下值为注册表条目名（不带命名空间），例如 `black`。 */
    CAT_VARIANT, WOLF_VARIANT, FROG_VARIANT, PIG_VARIANT, COW_VARIANT, CHICKEN_VARIANT, ZOMBIE_NAUTILUS_VARIANT,

    /** 值为 [VillagerSpec]。 */
    VILLAGER_DATA,

    /** 以下值为枚举名，例如 `SITTING`、`ROLLING`。 */
    POSE, ARMADILLO_STATE, SNIFFER_STATE,
}

/** 属性值可选项来自的注册表。 */
enum class RegistrySource {
    CAT_VARIANT, WOLF_VARIANT, FROG_VARIANT, PIG_VARIANT, COW_VARIANT, CHICKEN_VARIANT, ZOMBIE_NAUTILUS_VARIANT,
    VILLAGER_TYPE, VILLAGER_PROFESSION,
}

data class VillagerSpec(val type: String, val profession: String)

/** 计算好的元数据：[index] 已经按实体布局解析。 */
data class MetaValue(val index: Int, val kind: DataKind, val value: Any)

/**
 * 一个可由玩家设置的属性键，例如 `color`。
 * 可选值来自 [fixed] 或 [registry]；两者都为 null 表示自由文本（例如自定义名字）。
 */
class PropertyKey(
    val name: String,
    val fixed: List<String>? = null,
    val registry: RegistrySource? = null,
    /** 额外需要的权限；null 表示只需要基础的属性权限。 */
    val permission: String? = null,
    val maxLength: Int = 32,
) {
    val isFreeText: Boolean get() = fixed == null && registry == null
}

/**
 * 一个元数据字段的写法：从属性值计算出某个字段的值。
 * [fields] 是候选字段名，取实体布局中第一个存在的；[encode] 返回 null 表示不覆盖该字段。
 */
class PropertyDef(
    val keys: List<PropertyKey>,
    val fields: List<String>,
    val kind: DataKind,
    val encode: (Map<String, String>) -> Any?,
)

object PropertyDefs {
    const val NAME = "name"
    const val SKIN = "skin"

    private val BOOL = listOf("true", "false")
    private val DYES = DyeColor.entries.map { it.name.lowercase() }
    private val HORSE_COLORS = listOf("white", "creamy", "chestnut", "brown", "black", "gray", "dark_brown")
    private val HORSE_MARKINGS = listOf("none", "white", "white_field", "white_dots", "black_dots")
    private val PANDA_GENES = listOf("normal", "lazy", "worried", "playful", "brown", "weak", "aggressive")

    /** 染料在原版中的 ID 就是 [DyeColor] 的声明顺序（white=0 … black=15）。 */
    private fun dyeId(value: String?): Int? = value?.let { DYES.indexOf(it.lowercase()).takeIf { i -> i >= 0 } }

    private fun bool(key: String, field: String) =
        PropertyDef(listOf(PropertyKey(key, BOOL)), listOf(field), DataKind.BOOLEAN) { it[key]?.toBooleanStrictOrNull() }

    /** 按顺序映射为整数（下标即原版 ID，除非给出 [ids]）。 */
    private fun indexed(key: String, values: List<String>, field: String, ids: List<Int> = values.indices.toList()) =
        PropertyDef(listOf(PropertyKey(key, values)), listOf(field), DataKind.INT) { props ->
            props[key]?.let { v -> values.indexOf(v).takeIf { it >= 0 }?.let(ids::get) }
        }

    /** 数据驱动注册表中的变种（猫、狼、青蛙等）。 */
    private fun variant(field: String, kind: DataKind, source: RegistrySource) =
        PropertyDef(listOf(PropertyKey("variant", registry = source)), listOf(field), kind) { it["variant"] }

    private val nameKey = PropertyKey(NAME, permission = "yuimorph.property.name")

    /** 玩家伪装专用：使用另一个玩家的皮肤。 */
    val skinKey = PropertyKey(SKIN, permission = "yuimorph.property.skin", maxLength = 16)

    val all: List<PropertyDef> = listOf(
        // ---- 通用 ----
        PropertyDef(listOf(nameKey), listOf("Entity.DATA_CUSTOM_NAME"), DataKind.OPTIONAL_TEXT) { it[NAME] },
        PropertyDef(listOf(nameKey), listOf("Entity.DATA_CUSTOM_NAME_VISIBLE"), DataKind.BOOLEAN) { props ->
            if (props[NAME] != null) true else null
        },
        PropertyDef(
            listOf(PropertyKey("baby", BOOL)),
            listOf("AgeableMob.DATA_BABY_ID", "Zombie.DATA_BABY_ID", "Piglin.DATA_BABY_ID", "Zoglin.DATA_BABY_ID"),
            DataKind.BOOLEAN,
        ) { it["baby"]?.toBooleanStrictOrNull() },

        // ---- 颜色 ----
        PropertyDef(
            listOf(PropertyKey("color", DYES), PropertyKey("sheared", BOOL)),
            listOf("Sheep.DATA_WOOL_ID"),
            DataKind.BYTE,
        ) { props ->
            if (props["color"] == null && props["sheared"] == null) return@PropertyDef null
            val sheared = if (props["sheared"] == "true") 0x10 else 0
            ((dyeId(props["color"]) ?: 0) or sheared).toByte()
        },
        PropertyDef(listOf(PropertyKey("color", DYES)), listOf("Shulker.DATA_COLOR_ID"), DataKind.BYTE) { props ->
            dyeId(props["color"])?.toByte()
        },

        // ---- 大小 ----
        PropertyDef(listOf(PropertyKey("size", (1..8).map(Int::toString))), listOf("AbstractCubeMob.ID_SIZE"), DataKind.INT) {
            it["size"]?.toIntOrNull()
        },
        PropertyDef(listOf(PropertyKey("size", (0..8).map(Int::toString))), listOf("Phantom.ID_SIZE"), DataKind.INT) {
            it["size"]?.toIntOrNull()
        },

        // ---- 注册表变种 ----
        variant("Cat.DATA_VARIANT_ID", DataKind.CAT_VARIANT, RegistrySource.CAT_VARIANT),
        variant("Wolf.DATA_VARIANT_ID", DataKind.WOLF_VARIANT, RegistrySource.WOLF_VARIANT),
        variant("Frog.DATA_VARIANT_ID", DataKind.FROG_VARIANT, RegistrySource.FROG_VARIANT),
        variant("Pig.DATA_VARIANT_ID", DataKind.PIG_VARIANT, RegistrySource.PIG_VARIANT),
        variant("Cow.DATA_VARIANT_ID", DataKind.COW_VARIANT, RegistrySource.COW_VARIANT),
        variant("Chicken.DATA_VARIANT_ID", DataKind.CHICKEN_VARIANT, RegistrySource.CHICKEN_VARIANT),
        variant("ZombieNautilus.DATA_VARIANT_ID", DataKind.ZOMBIE_NAUTILUS_VARIANT, RegistrySource.ZOMBIE_NAUTILUS_VARIANT),

        // ---- 整数变种 ----
        indexed("variant", listOf("lucy", "wild", "gold", "cyan", "blue"), "Axolotl.DATA_VARIANT"),
        indexed("variant", listOf("red_blue", "blue", "green", "yellow_blue", "gray"), "Parrot.DATA_VARIANT_ID"),
        indexed(
            "variant", listOf("brown", "white", "black", "white_splotched", "gold", "salt", "evil"),
            "Rabbit.DATA_TYPE_ID", ids = listOf(0, 1, 2, 3, 4, 5, 99),
        ),
        indexed("variant", listOf("red", "snow"), "Fox.DATA_TYPE_ID"),
        indexed("variant", listOf("red", "brown"), "MushroomCow.DATA_TYPE"),
        indexed("variant", listOf("creamy", "white", "brown", "gray"), "Llama.DATA_VARIANT_ID"),
        PropertyDef(
            listOf(PropertyKey("variant", HORSE_COLORS), PropertyKey("markings", HORSE_MARKINGS)),
            listOf("Horse.DATA_ID_TYPE_VARIANT"),
            DataKind.INT,
        ) { props ->
            if (props["variant"] == null && props["markings"] == null) return@PropertyDef null
            val color = HORSE_COLORS.indexOf(props["variant"]).coerceAtLeast(0)
            val markings = HORSE_MARKINGS.indexOf(props["markings"]).coerceAtLeast(0)
            color or (markings shl 8)
        },

        // ---- 其他外观 ----
        bool("powered", "Creeper.DATA_IS_POWERED"),
        PropertyDef(listOf(PropertyKey("pumpkin", BOOL)), listOf("SnowGolem.DATA_PUMPKIN_ID"), DataKind.BYTE) { props ->
            props["pumpkin"]?.toBooleanStrictOrNull()?.let { if (it) 0x10.toByte() else 0.toByte() }
        },
        // 熊猫的显性和隐性基因都设成同一个，隐性基因（brown、weak）才能显示出来
        pandaGene("Panda.MAIN_GENE_ID"),
        pandaGene("Panda.HIDDEN_GENE_ID"),
        PropertyDef(
            listOf(
                PropertyKey("type", registry = RegistrySource.VILLAGER_TYPE),
                PropertyKey("profession", registry = RegistrySource.VILLAGER_PROFESSION),
            ),
            listOf("Villager.DATA_VILLAGER_DATA", "ZombieVillager.DATA_VILLAGER_DATA"),
            DataKind.VILLAGER_DATA,
        ) { props ->
            if (props["type"] == null && props["profession"] == null) return@PropertyDef null
            VillagerSpec(props["type"] ?: "plains", props["profession"] ?: "none")
        },
    )

    private fun pandaGene(field: String) =
        PropertyDef(listOf(PropertyKey("gene", PANDA_GENES)), listOf(field), DataKind.BYTE) { props ->
            PANDA_GENES.indexOf(props["gene"]).takeIf { it >= 0 }?.toByte()
        }
}
