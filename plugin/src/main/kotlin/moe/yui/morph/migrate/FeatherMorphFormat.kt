package moe.yui.morph.migrate

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.util.UUID

/** 一个玩家在 FeatherMorph 中的数据。 */
data class FeatherMorphPlayer(
    val uuid: UUID,
    val name: String?,
    /** 已解锁的伪装 ID，格式和 YuiMorph 相同（`minecraft:pig`、`player:Name`）。 */
    val unlocked: List<String>,
    /** 下线时的伪装（来自 saved_disguises），可能为 null。 */
    val savedDisguise: String? = null,
    /** 下线时伪装的属性（FeatherMorph 格式，例如 `cat/variant`）。 */
    val savedProperties: Map<String, String> = emptyMap(),
)

/**
 * FeatherMorph 数据格式（2.9.x）：
 * - `playerdata/<uuid>.json`：`{"playerName": "...", "unlockedDisguiseIdentifiers": ["minecraft:pig", ...], ...}`
 * - `saved_disguises/<uuid>.json`：`{"disguiseIdentifier": "minecraft:cat", "properties": {"cat/variant": "minecraft:red"}}`
 *
 * 只做格式解析和映射，不依赖服务器，便于测试。
 */
object FeatherMorphFormat {
    private val VALID_ID = Regex("^(minecraft:[a-z0-9_]+|player:[A-Za-z0-9_]{1,16})$")

    /** 解析 playerdata 文件；不是合法的玩家数据时返回 null（例如目录里的 package_version.txt）。 */
    fun parsePlayer(fileName: String, json: String): FeatherMorphPlayer? {
        if (!fileName.endsWith(".json")) return null
        val uuid = runCatching { UUID.fromString(fileName.removeSuffix(".json")) }.getOrNull() ?: return null
        val obj = runCatching { JsonParser.parseString(json) }.getOrNull() as? JsonObject ?: return null
        val ids = obj.getAsJsonArray("unlockedDisguiseIdentifiers")?.mapNotNull { e ->
            runCatching { e.asString.trim() }.getOrNull()?.takeIf(VALID_ID::matches)
        }.orEmpty()
        val name = obj.get("playerName")?.takeIf { it.isJsonPrimitive }?.asString
        return FeatherMorphPlayer(uuid, name, ids.distinct())
    }

    /** 解析 saved_disguises 文件，返回 (伪装 ID, 属性)。 */
    fun parseSaved(json: String): Pair<String, Map<String, String>>? {
        val obj = runCatching { JsonParser.parseString(json) }.getOrNull() as? JsonObject ?: return null
        val id = obj.get("disguiseIdentifier")?.takeIf { it.isJsonPrimitive }?.asString?.trim() ?: return null
        if (!VALID_ID.matches(id)) return null
        val props = obj.getAsJsonObject("properties")?.entrySet()
            ?.filter { it.value.isJsonPrimitive }
            ?.associate { it.key to it.value.asString }
            .orEmpty()
        return id to props
    }

    /**
     * FeatherMorph 属性 → YuiMorph 属性（只做名字和取值格式的转换，是否合法由 YuiMorph 的属性规则再校验）。
     *
     * 不迁移的：`player/skin`（编码过的皮肤数据，YuiMorph 按名字重新获取）、YuiMorph 没有的属性（例如盔甲架手臂）。
     */
    fun mapProperties(fmProps: Map<String, String>): Map<String, String> {
        val result = linkedMapOf<String, String>()
        for ((key, raw) in fmProps) {
            val owner = key.substringBefore('/')
            val name = key.substringAfter('/', "")
            val value = raw.trim().removePrefix("minecraft:").lowercase()
            val mapped = when {
                owner == "horse" && name == "color" -> "variant"
                owner == "horse" && name == "style" -> "markings"
                owner == "llama" && name == "color" -> "variant"
                name == "variant" -> "variant"
                name == "size" -> "size"
                name == "charged" -> "powered"
                name == "is_baby" || name == "baby" -> "baby"
                name == "color" -> "color"
                else -> null
            } ?: continue
            result[mapped] = HORSE_MARKINGS[value] ?: value
        }
        return result
    }

    /** FeatherMorph 的马花纹命名和原版不一致。 */
    private val HORSE_MARKINGS = mapOf("whitefield" to "white_field", "whitedots" to "white_dots", "blackdots" to "black_dots")
}
