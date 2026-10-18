package moe.yui.morph.client.ui

import net.minecraft.locale.Language
import net.minecraft.network.chat.Component

/** 动作、属性名、属性值的显示文本：有翻译用翻译，没有就显示原始 ID。 */
object Labels {
    private fun translatedOr(key: String, fallback: String): Component =
        if (Language.getInstance().has(key)) Component.translatable(key) else Component.literal(fallback)

    fun action(action: String): Component = translatedOr("yuimorph.action.$action", action)

    fun property(key: String): Component = translatedOr("yuimorph.property.$key", key)

    fun value(key: String, value: String?): Component {
        if (value == null) return Component.translatable("yuimorph.value.default")
        return when {
            value == "true" || value == "false" -> Component.translatable("yuimorph.value.$value")
            // 染料颜色原版就有翻译
            key == "color" && Language.getInstance().has("color.minecraft.$value") -> Component.translatable("color.minecraft.$value")
            // 村民职业原版也有翻译
            key == "profession" && Language.getInstance().has("entity.minecraft.villager.$value") ->
                Component.translatable("entity.minecraft.villager.$value")
            else -> translatedOr("yuimorph.value.$key.$value", value)
        }
    }
}
