package moe.yui.morph.protocol

/** 协议常量。双方 [VERSION] 不一致时，服务端拒绝启用客户端增强，玩家仍可正常游玩。 */
object Protocol {
    /** 插件消息频道（Bukkit / Fabric 两侧都用这个标识）。 */
    const val CHANNEL = "yuimorph:main"

    /** 协议版本，任何包结构变化都要 +1。 */
    const val VERSION = 1

    /** 单个包的最大字节数，防止恶意客户端发送超大包。 */
    const val MAX_PACKET_BYTES = 1 shl 20
}
