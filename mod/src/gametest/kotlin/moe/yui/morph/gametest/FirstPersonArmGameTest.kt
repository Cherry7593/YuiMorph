package moe.yui.morph.gametest

import com.google.gson.JsonParser
import moe.yui.morph.client.arm.FirstPersonArm
import moe.yui.morph.client.ui.PreviewEntities
import moe.yui.morph.client.ui.SkinCache
import moe.yui.morph.protocol.SkinTexture
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext
import net.minecraft.client.Minecraft
import net.minecraft.core.component.DataComponents
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.registries.Registries
import net.minecraft.world.entity.AgeableMob
import net.minecraft.world.entity.HumanoidArm
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.animal.pig.PigVariants
import net.minecraft.world.entity.animal.sheep.Sheep
import net.minecraft.world.item.DyeColor
import org.slf4j.LoggerFactory

/**
 * 第一人称手臂画廊：在单人世界里把每种生物依次当作手臂来源（代替代理实体），第一人称截图。
 * 截图在 `mod/build/run/clientGameTest/screenshots/`，文件名 `arm-<序号>-<名字>`。
 * 用来检查每种生物的手臂位置、贴图、图层，调 ArmProfiles。
 *
 * 只截部分：`./gradlew :mod:runClientGameTest -ParmGallery=pig,zombie,pig-cold`（逗号分隔，省略则全部）。
 */
class FirstPersonArmGameTest : FabricClientGameTest {
    private val logger = LoggerFactory.getLogger("YuiMorph-GameTest")

    private class Shot(val name: String, val id: String, val leftHanded: Boolean = false, val map: Boolean = false, val setup: (LivingEntity) -> Unit = {})

    override fun runTest(context: ClientGameTestContext) {
        javaClass.classLoader.getResourceAsStream("yuimorph-gametest-notch.json")?.use { input ->
            val obj = JsonParser.parseString(input.reader().readText()).asJsonObject
            val signature = obj.get("signature")?.takeIf { !it.isJsonNull }?.asString
            context.runOnClient<RuntimeException> { SkinCache.put("Notch", SkinTexture(obj.get("value").asString, signature)) }
        }

        context.worldBuilder().create().use { world ->
            val server = world.server
            server.runCommand("time set noon")
            server.runCommand("weather clear")
            context.waitTicks(40)
            context.runOnClient<RuntimeException> { mc ->
                mc.player?.let { it.xRot = 20f; it.yRot = 0f }
            }

            val filter = System.getProperty("yuimorph.armGallery")?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }?.toSet()
            val shots = shots(context).filter { filter == null || it.name in filter || it.id.substringAfter(':') in filter }
            logger.info("第一人称手臂画廊：{} 张", shots.size)

            var holdingMap = false
            shots.forEachIndexed { i, shot ->
                if (shot.map != holdingMap) {
                    server.runCommand(if (shot.map) "item replace entity @p weapon.mainhand with filled_map[map_id=0]" else "item replace entity @p weapon.mainhand with air")
                    holdingMap = shot.map
                    context.waitTicks(10)
                }
                context.runOnClient<RuntimeException> { mc ->
                    val mainHand = if (shot.leftHanded) HumanoidArm.LEFT else HumanoidArm.RIGHT
                    if (mc.options.mainHand().get() != mainHand) {
                        mc.options.mainHand().set(mainHand)
                        mc.options.broadcastOptions()
                    }
                    val player = mc.player!!
                    // 拿地图时两只手在地图下方，低头才看得到
                    player.xRot = if (shot.map) 70f else 20f
                    val entity = PreviewEntities.create(shot.id, null)
                    entity?.setPos(player.position())
                    entity?.let(shot.setup)
                    FirstPersonArm.sourceOverride = entity
                    if (entity == null) logger.warn("画廊：无法创建 {}", shot.id)
                }
                // 玩家皮肤异步加载；其他生物等两帧即可
                context.waitTicks(if (shot.id.startsWith(PreviewEntities.PLAYER_PREFIX)) 40 else 5)
                context.takeScreenshot("arm-%03d-%s".format(i, shot.name))
            }

            context.runOnClient<RuntimeException> { mc ->
                FirstPersonArm.sourceOverride = null
                mc.options.mainHand().set(HumanoidArm.RIGHT)
                mc.options.broadcastOptions()
            }
        }
    }

    private fun shots(context: ClientGameTestContext): List<Shot> {
        val all = context.computeOnClient<List<String>, RuntimeException> { _ ->
            BuiltInRegistries.ENTITY_TYPE.keySet().map { it.toString() }.sorted()
        }
        val living = context.computeOnClient<List<String>, RuntimeException> { _ ->
            all.filter { id ->
                id != "minecraft:player" && id != "minecraft:mannequin" && PreviewEntities.create(id, null) is LivingEntity
            }
        }
        val extras = listOf(
            Shot("player-notch", "player:Notch"),
            Shot("player-default", "player:YuiMorphNoSkin"),
            Shot("pig-cold", "minecraft:pig") { e -> setPigVariant(e) },
            Shot("pig-baby", "minecraft:pig") { e -> (e as AgeableMob).age = -24000 },
            Shot("sheep-red", "minecraft:sheep") { e -> (e as Sheep).color = DyeColor.RED },
            Shot("villager-left", "minecraft:villager", leftHanded = true),
            Shot("zombie-left", "minecraft:zombie", leftHanded = true),
            Shot("pig-left", "minecraft:pig", leftHanded = true),
            Shot("zombie-map", "minecraft:zombie", map = true),
            Shot("pig-map", "minecraft:pig", map = true),
            Shot("player-notch-map", "player:Notch", map = true),
        )
        return living.map { Shot(it.substringAfter(':'), it) } + extras
    }

    private fun setPigVariant(entity: LivingEntity) {
        val level = Minecraft.getInstance().level ?: return
        val holder = level.registryAccess().lookupOrThrow(Registries.PIG_VARIANT).getOrThrow(PigVariants.COLD)
        entity.setComponent(DataComponents.PIG_VARIANT, holder)
    }
}
