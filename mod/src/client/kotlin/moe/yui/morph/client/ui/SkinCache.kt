package moe.yui.morph.client.ui

import moe.yui.morph.client.MorphClientState
import moe.yui.morph.protocol.C2SRequestSkins
import moe.yui.morph.protocol.S2CSkin
import moe.yui.morph.protocol.SkinTexture

/**
 * 玩家皮肤缓存（按名字，不区分大小写）。皮肤由服务器提供：
 * 服务器已经为玩家伪装获取过 Mojang 签名的皮肤，客户端不需要（也不一定能）自己访问 Mojang。
 */
object SkinCache {
    private val skins = HashMap<String, SkinTexture?>()
    private val requested = HashSet<String>()

    /** 已知的皮肤；还没拿到或获取失败返回 null。 */
    operator fun get(name: String): SkinTexture? = skins[name.lowercase()]

    /** 请求还没请求过的名字（分批，每批最多 [C2SRequestSkins.MAX_NAMES] 个）。 */
    fun request(names: Collection<String>) {
        if (!MorphClientState.accepted) return
        val missing = names.filter { requested.add(it.lowercase()) }
        missing.chunked(C2SRequestSkins.MAX_NAMES).forEach { MorphClientState.send(C2SRequestSkins(it)) }
    }

    fun onSkin(packet: S2CSkin) {
        skins[packet.name.lowercase()] = packet.skin
        // 让用到这个名字的缩略图用新皮肤重建
        if (packet.skin != null) PreviewEntities.invalidatePlayer(packet.name)
    }

    /** 服务器直接给的皮肤（例如预览回复里带的）。 */
    fun put(name: String, skin: SkinTexture) {
        skins[name.lowercase()] = skin
        requested += name.lowercase()
    }

    fun clear() {
        skins.clear()
        requested.clear()
    }
}
