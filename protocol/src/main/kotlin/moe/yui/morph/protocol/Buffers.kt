package moe.yui.morph.protocol

import java.io.ByteArrayOutputStream

/** 编码失败或数据不合法时抛出。 */
class ProtocolException(message: String) : RuntimeException(message)

/** 只写缓冲区，格式与 MC 一致：VarInt 长度前缀的 UTF-8 字符串。 */
class PacketWriter {
    private val out = ByteArrayOutputStream()

    fun byte(value: Int) = apply { out.write(value and 0xFF) }

    fun bool(value: Boolean) = byte(if (value) 1 else 0)

    fun varInt(value: Int) = apply {
        var v = value
        while (v and 0x7F.inv() != 0) {
            out.write((v and 0x7F) or 0x80)
            v = v ushr 7
        }
        out.write(v)
    }

    fun string(value: String) = apply {
        val bytes = value.toByteArray(Charsets.UTF_8)
        varInt(bytes.size)
        out.write(bytes)
    }

    fun nullableString(value: String?) = apply {
        bool(value != null)
        if (value != null) string(value)
    }

    fun stringList(values: Collection<String>) = apply {
        varInt(values.size)
        values.forEach(::string)
    }

    fun stringMap(values: Map<String, String>) = apply {
        varInt(values.size)
        values.forEach { (k, v) -> string(k); string(v) }
    }

    fun toByteArray(): ByteArray = out.toByteArray()
}

/** 只读缓冲区，所有读取都做边界检查，非法数据抛 [ProtocolException]。 */
class PacketReader(private val data: ByteArray) {
    private var pos = 0

    val remaining: Int get() = data.size - pos

    fun byte(): Int {
        if (pos >= data.size) throw ProtocolException("意外的数据结尾")
        return data[pos++].toInt() and 0xFF
    }

    fun bool(): Boolean = when (val b = byte()) {
        0 -> false
        1 -> true
        else -> throw ProtocolException("非法布尔值 $b")
    }

    fun varInt(): Int {
        var result = 0
        var shift = 0
        while (true) {
            val b = byte()
            result = result or ((b and 0x7F) shl shift)
            if (b and 0x80 == 0) return result
            shift += 7
            if (shift >= 35) throw ProtocolException("VarInt 过长")
        }
    }

    private fun length(max: Int): Int {
        val len = varInt()
        if (len < 0 || len > max || len > remaining) throw ProtocolException("非法长度 $len")
        return len
    }

    fun string(maxBytes: Int = 32767): String {
        val len = length(maxBytes)
        val s = String(data, pos, len, Charsets.UTF_8)
        pos += len
        return s
    }

    fun nullableString(): String? = if (bool()) string() else null

    fun stringList(maxSize: Int = 4096): List<String> {
        val size = length(maxSize)
        return List(size) { string() }
    }

    fun stringMap(maxSize: Int = 256): Map<String, String> {
        val size = length(maxSize)
        return buildMap(size) { repeat(size) { put(string(), string()) } }
    }
}
