package moe.yui.morph.disguise.property

import java.io.InputStream

/**
 * 实体元数据布局表，来自 `entity-layouts.tsv`（由 `scripts/gen-entity-layouts.py` 生成）。
 *
 * 每种实体的字段按索引顺序排列，字段名格式为 `类名.字段名`，例如 `Sheep.DATA_WOOL_ID`。
 * 属性按字段名查找索引，而不是写死数字，升级 MC 版本时只需重新生成表。
 */
class EntityLayouts private constructor(private val layouts: Map<String, List<String>>) {
    val size: Int get() = layouts.size

    /** 字段数量；未知实体返回 null。发出的索引必须小于这个值，否则客户端会断线。 */
    fun fieldCount(typeKey: String): Int? = layouts[typeKey]?.size

    fun indexOf(typeKey: String, field: String): Int? =
        layouts[typeKey]?.indexOf(field)?.takeIf { it >= 0 }

    companion object {
        fun load(input: InputStream): EntityLayouts {
            val map = input.bufferedReader(Charsets.UTF_8).useLines { lines ->
                lines.filter { it.isNotBlank() && !it.startsWith("#") }
                    .associate { line ->
                        val cols = line.split('\t')
                        cols[0] to cols.drop(1).map { it.substringBefore(':') }
                    }
            }
            return EntityLayouts(map)
        }
    }
}
