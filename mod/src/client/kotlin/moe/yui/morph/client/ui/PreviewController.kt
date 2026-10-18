package moe.yui.morph.client.ui

import moe.yui.morph.client.MorphClientState
import moe.yui.morph.protocol.C2SPreview
import moe.yui.morph.protocol.PropertySpec
import moe.yui.morph.protocol.S2CPreview
import net.minecraft.client.Minecraft
import net.minecraft.network.syncher.SynchedEntityData
import net.minecraft.world.entity.LivingEntity
import org.slf4j.LoggerFactory

/**
 * 选择界面右侧的预览状态。只在客户端主线程使用。
 *
 * 流程：选中伪装或修改属性 → 发送 [C2SPreview] → 服务器回复 [S2CPreview]（属性表、实际生效的属性）
 * → 新建一个默认外观的预览实体 → 服务器随后发来的元数据包应用到它上面。
 * 每次请求带递增的 ID，只接受最新一次的回复，快速连点不会显示错乱。
 */
object PreviewController {
    private val logger = LoggerFactory.getLogger("YuiMorph")

    var selectedId: String? = null
        private set

    /** 当前预览使用的属性（服务器回复后以服务器校验的结果为准）。 */
    var properties: Map<String, String> = emptyMap()
        private set
    var specs: List<PropertySpec> = emptyList()
        private set
    var actions: List<String> = emptyList()
        private set
    var entity: LivingEntity? = null
        private set

    /** 每次收到服务器回复都会变化，界面据此重建属性控件。 */
    var revision = 0
        private set

    private var requestSeq = 0
    private var pendingRequest = -1
    private var previewEntityId = Int.MIN_VALUE

    fun select(id: String, properties: Map<String, String>? = null) {
        selectedId = id
        specs = emptyList()
        actions = emptyList()
        this.properties = properties.orEmpty()
        // 先显示默认外观，服务器回复后再换成带属性的
        entity = PreviewEntities.create(id, properties?.get("skin"))
        request(properties)
        revision++
    }

    fun set(key: String, value: String?) {
        val updated = properties.toMutableMap().apply { if (value == null) remove(key) else put(key, value) }
        properties = updated
        request(updated)
    }

    fun reset() {
        properties = emptyMap()
        request(emptyMap())
    }

    private fun request(properties: Map<String, String>?) {
        val id = selectedId ?: return
        if (!MorphClientState.accepted) return
        pendingRequest = ++requestSeq
        MorphClientState.send(C2SPreview(pendingRequest, id, properties))
    }

    fun onPreview(packet: S2CPreview) {
        if (packet.requestId != pendingRequest || packet.disguiseId != selectedId) return
        properties = packet.properties
        specs = packet.specs
        actions = packet.actions
        previewEntityId = packet.previewEntityId
        entity = PreviewEntities.create(packet.disguiseId, packet.properties["skin"])
        revision++
    }

    /**
     * 由 Mixin 调用（ClientPacketListener#handleSetEntityData）：是发给预览实体的元数据就应用并返回 true。
     * 在网络线程上先放行，让原版把包转到主线程再处理。
     */
    @JvmStatic
    fun handleMetadata(entityId: Int, items: List<SynchedEntityData.DataValue<*>>): Boolean {
        if (entityId != previewEntityId) return false
        if (!Minecraft.getInstance().isSameThread) return false
        try {
            entity?.entityData?.assignValues(items)
        } catch (e: Exception) {
            logger.warn("无法应用预览外观：{}", e.toString())
        }
        return true
    }

    fun clear() {
        selectedId = null
        properties = emptyMap()
        specs = emptyList()
        actions = emptyList()
        entity = null
        pendingRequest = -1
    }
}
