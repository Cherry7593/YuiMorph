package moe.yui.morph.disguise

import com.github.retrooper.packetevents.protocol.entity.type.EntityType as PeEntityType
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** 一次伪装的不可变描述。Netty 线程会读取它，所以必须保持不可变。 */
data class DisguiseSession(
    val playerId: UUID,
    /** 伪装实体沿用玩家的 entity id。 */
    val entityId: Int,
    val disguise: DisguiseId,
    /** 发给客户端的实体类型。 */
    val entityType: PeEntityType,
)

/** 当前所有伪装会话，可以按玩家 UUID 或 entity id 查找。任意线程可读。 */
class SessionRegistry {
    private val byPlayer = ConcurrentHashMap<UUID, DisguiseSession>()
    private val byEntity = ConcurrentHashMap<Int, DisguiseSession>()

    val isEmpty: Boolean get() = byPlayer.isEmpty()

    fun all(): Collection<DisguiseSession> = byPlayer.values

    operator fun get(playerId: UUID): DisguiseSession? = byPlayer[playerId]

    fun byEntityId(entityId: Int): DisguiseSession? = byEntity[entityId]

    fun put(session: DisguiseSession) {
        remove(session.playerId)
        byEntity[session.entityId] = session
        byPlayer[session.playerId] = session
    }

    fun remove(playerId: UUID): DisguiseSession? =
        byPlayer.remove(playerId)?.also { byEntity.remove(it.entityId, it) }
}
