package moe.yui.morph.protocol

/**
 * 所有包都经由 [Protocol.CHANNEL] 传输，第一个字节是包 ID。
 *
 * 握手流程：客户端进服后发送 [C2SHello]，服务端回复 [S2CHello]。
 * 只有 `accepted = true` 之后，双方才会发送其余的包。
 */
sealed interface Packet {
    fun write(w: PacketWriter)
}

/** 客户端 → 服务端 */
sealed interface C2SPacket : Packet

/** 服务端 → 客户端 */
sealed interface S2CPacket : Packet

/** 一个可设置的属性。[values] 为 null 表示自由文本（最长 [maxLength]）。 */
data class PropertySpec(val key: String, val values: List<String>?, val maxLength: Int) {
    fun write(w: PacketWriter) {
        w.string(key).bool(values != null)
        if (values != null) w.stringList(values)
        w.varInt(maxLength)
    }

    companion object {
        fun read(r: PacketReader) = PropertySpec(r.string(64), if (r.bool()) r.stringList() else null, r.varInt())
    }
}

// ---------- C2S ----------

data class C2SHello(val protocolVersion: Int, val modVersion: String) : C2SPacket {
    override fun write(w: PacketWriter) { w.varInt(protocolVersion).string(modVersion) }
}

/**
 * 请求变身为某个伪装，例如 `minecraft:pig`、`player:Notch`。
 * [properties] 为 null 时使用该伪装上次保存的属性；否则使用这组属性（服务器校验后保存）。
 */
data class C2SMorph(val disguiseId: String, val properties: Map<String, String>? = null) : C2SPacket {
    override fun write(w: PacketWriter) { w.string(disguiseId).nullableStringMap(properties) }
}

/**
 * 请求预览：服务器按 [properties]（null 表示该伪装保存的属性）计算外观，
 * 先回复 [S2CPreview]，再把元数据发到 [S2CPreview.previewEntityId] 这个只存在于客户端的实体 ID 上。
 * [requestId] 用来丢弃过期的回复。
 */
data class C2SPreview(val requestId: Int, val disguiseId: String, val properties: Map<String, String>?) : C2SPacket {
    override fun write(w: PacketWriter) { w.varInt(requestId).string(disguiseId).nullableStringMap(properties) }
}

data object C2SUnmorph : C2SPacket {
    override fun write(w: PacketWriter) {}
}

data class C2SPlayAction(val action: String) : C2SPacket {
    override fun write(w: PacketWriter) { w.string(action) }
}

// ---------- S2C ----------

data class S2CHello(val protocolVersion: Int, val accepted: Boolean, val serverVersion: String) : S2CPacket {
    override fun write(w: PacketWriter) { w.varInt(protocolVersion).bool(accepted).string(serverVersion) }
}

/** 可用伪装列表同步。`full = true` 时 [added] 是完整列表，客户端应先清空。 */
data class S2CUnlockedSync(
    val full: Boolean,
    val added: List<String>,
    val removed: List<String>,
) : S2CPacket {
    override fun write(w: PacketWriter) { w.bool(full).stringList(added).stringList(removed) }
}

/**
 * 玩家自身的伪装状态（用于自身渲染和 HUD）。
 *
 * - [disguiseId] 为 null 表示未伪装
 * - [displayName] 玩家伪装时为玩家名；生物伪装为 null，客户端用实体类型名
 * - [actions] 当前伪装可用的动作
 * - [proxyEntityId] 服务器只发给本人的「代理实体」ID：外观、属性、动作都由服务器同步到这个实体上，
 *   客户端每 tick 把它移到玩家位置并代替玩家模型渲染。没有代理时为 [NO_PROXY]
 */
data class S2CSelfDisguise(
    val disguiseId: String?,
    val displayName: String?,
    val properties: Map<String, String>,
    val actions: List<String>,
    val proxyEntityId: Int,
) : S2CPacket {
    override fun write(w: PacketWriter) {
        w.nullableString(disguiseId).nullableString(displayName).stringMap(properties).stringList(actions).varInt(proxyEntityId)
    }

    companion object {
        const val NO_PROXY = 0
        val NONE = S2CSelfDisguise(null, null, emptyMap(), emptyList(), NO_PROXY)
    }
}

/**
 * 预览结果：该伪装可设置的属性（已按玩家权限过滤）、校验后实际生效的属性、动作列表。
 * 紧接着服务器会向 [previewEntityId] 发送一个原版的实体元数据包。
 */
data class S2CPreview(
    val requestId: Int,
    val disguiseId: String,
    val previewEntityId: Int,
    val properties: Map<String, String>,
    val specs: List<PropertySpec>,
    val actions: List<String>,
) : S2CPacket {
    override fun write(w: PacketWriter) {
        w.varInt(requestId).string(disguiseId).varInt(previewEntityId).stringMap(properties)
        w.varInt(specs.size)
        specs.forEach { it.write(w) }
        w.stringList(actions)
    }
}

object PacketCodec {
    private const val ID_C2S_HELLO = 0
    private const val ID_C2S_MORPH = 1
    private const val ID_C2S_UNMORPH = 2
    private const val ID_C2S_PLAY_ACTION = 3
    private const val ID_C2S_PREVIEW = 4

    private const val ID_S2C_HELLO = 0
    private const val ID_S2C_UNLOCKED_SYNC = 1
    private const val ID_S2C_SELF_DISGUISE = 2
    private const val ID_S2C_PREVIEW = 3

    fun encode(packet: Packet): ByteArray {
        val id = when (packet) {
            is C2SHello -> ID_C2S_HELLO
            is C2SMorph -> ID_C2S_MORPH
            C2SUnmorph -> ID_C2S_UNMORPH
            is C2SPlayAction -> ID_C2S_PLAY_ACTION
            is C2SPreview -> ID_C2S_PREVIEW
            is S2CHello -> ID_S2C_HELLO
            is S2CUnlockedSync -> ID_S2C_UNLOCKED_SYNC
            is S2CSelfDisguise -> ID_S2C_SELF_DISGUISE
            is S2CPreview -> ID_S2C_PREVIEW
        }
        val w = PacketWriter().byte(id)
        packet.write(w)
        return w.toByteArray().also {
            if (it.size > Protocol.MAX_PACKET_BYTES) throw ProtocolException("包过大：${it.size} 字节")
        }
    }

    fun decodeC2S(data: ByteArray): C2SPacket = decode(data) { id, r ->
        when (id) {
            ID_C2S_HELLO -> C2SHello(r.varInt(), r.string(64))
            ID_C2S_MORPH -> C2SMorph(r.string(256), r.nullableStringMap())
            ID_C2S_UNMORPH -> C2SUnmorph
            ID_C2S_PLAY_ACTION -> C2SPlayAction(r.string(64))
            ID_C2S_PREVIEW -> C2SPreview(r.varInt(), r.string(256), r.nullableStringMap())
            else -> throw ProtocolException("未知的 C2S 包 ID $id")
        }
    }

    fun decodeS2C(data: ByteArray): S2CPacket = decode(data) { id, r ->
        when (id) {
            ID_S2C_HELLO -> S2CHello(r.varInt(), r.bool(), r.string(64))
            ID_S2C_UNLOCKED_SYNC -> S2CUnlockedSync(r.bool(), r.stringList(), r.stringList())
            ID_S2C_SELF_DISGUISE -> S2CSelfDisguise(r.nullableString(), r.nullableString(), r.stringMap(), r.stringList(), r.varInt())
            ID_S2C_PREVIEW -> S2CPreview(
                r.varInt(), r.string(256), r.varInt(), r.stringMap(),
                List(r.varInt().also { if (it > 64) throw ProtocolException("属性过多：$it") }) { PropertySpec.read(r) },
                r.stringList(),
            )
            else -> throw ProtocolException("未知的 S2C 包 ID $id")
        }
    }

    private inline fun <T> decode(data: ByteArray, read: (Int, PacketReader) -> T): T {
        if (data.size > Protocol.MAX_PACKET_BYTES) throw ProtocolException("包过大：${data.size} 字节")
        val r = PacketReader(data)
        val packet = read(r.byte(), r)
        if (r.remaining != 0) throw ProtocolException("包末尾有 ${r.remaining} 字节多余数据")
        return packet
    }
}
