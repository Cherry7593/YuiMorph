package moe.yui.morph.disguise

import com.github.retrooper.packetevents.protocol.entity.data.EntityData
import com.github.retrooper.packetevents.protocol.entity.type.EntityType as PeEntityType
import moe.yui.morph.skin.Skin
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** 玩家伪装使用的虚拟档案。[uuid] 是虚拟 UUID，不会和真实玩家冲突。 */
data class DisguiseProfile(val uuid: UUID, val name: String, val skin: Skin?)

/** 一次伪装的不可变描述。Netty 线程会读取它，所以必须保持不可变。 */
data class DisguiseSession(
    val playerId: UUID,
    /** 伪装实体沿用玩家的 entity id。 */
    val entityId: Int,
    val disguise: DisguiseId,
    /** 发给客户端的实体类型。 */
    val entityType: PeEntityType,
    /** 玩家设置的属性（已校验）。 */
    val properties: Map<String, String> = emptyMap(),
    /** 由属性计算出的元数据覆盖值，服务器发出的同索引数据会被丢弃。 */
    val metadata: List<EntityData<*>> = emptyList(),
    /** 仅玩家伪装。 */
    val profile: DisguiseProfile? = null,
    /** 正在进行的动作带来的元数据，叠加在属性之上（同索引以动作为准）。 */
    val actionMetadata: List<EntityData<*>> = emptyList(),
) {
    /** 实际生效的覆盖值：属性 + 动作。 */
    val allMetadata: List<EntityData<*>> = run {
        val actionIndices = actionMetadata.mapTo(HashSet()) { it.index }
        metadata.filter { it.index !in actionIndices } + actionMetadata
    }

    /** 服务器发出的这些索引会被丢弃（例如动作进行中不让潜行改变姿态）。 */
    val overriddenIndices: Set<Int> = allMetadata.mapTo(HashSet()) { it.index }
}

/** 当前所有伪装会话，可以按玩家 UUID 或 entity id 查找。任意线程可读。 */
class SessionRegistry {
    private val byPlayer = ConcurrentHashMap<UUID, DisguiseSession>()
    private val byEntity = ConcurrentHashMap<Int, DisguiseSession>()

    val isEmpty: Boolean get() = byPlayer.isEmpty()

    fun all(): Collection<DisguiseSession> = byPlayer.values

    operator fun get(playerId: UUID): DisguiseSession? = byPlayer[playerId]

    fun byEntityId(entityId: Int): DisguiseSession? = byEntity[entityId]

    fun put(session: DisguiseSession) {
        byPlayer.put(session.playerId, session)?.let { old -> byEntity.remove(old.entityId, old) }
        byEntity[session.entityId] = session
    }

    fun remove(playerId: UUID): DisguiseSession? =
        byPlayer.remove(playerId)?.also { byEntity.remove(it.entityId, it) }
}
