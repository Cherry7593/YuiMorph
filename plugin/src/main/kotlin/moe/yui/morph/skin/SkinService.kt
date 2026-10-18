package moe.yui.morph.skin

import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import org.bukkit.plugin.Plugin
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.logging.Level

/** 皮肤纹理（Mojang 签名过的 `textures` 属性；未签名的皮肤客户端不会显示）。 */
data class Skin(val value: String, val signature: String?)

/** 解析结果：[name] 是 Mojang 档案里的正确大小写，查不到时保持原样。 */
data class ResolvedSkin(val name: String, val skin: Skin?)

/**
 * 按玩家名获取皮肤：在线玩家 → 未过期的缓存 → Mojang 查询（失败时回落到过期缓存）。
 * 缓存保存在 `skins.json`。
 */
class SkinService(private val plugin: Plugin, private val ttlMillis: () -> Long) {
    private class Cached(val name: String, val value: String?, val signature: String?, val fetchedAt: Long)

    private val file = File(plugin.dataFolder, "skins.json")
    private val gson = GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create()
    private val cache = ConcurrentHashMap<String, Cached>()
    private val inflight = ConcurrentHashMap<String, CompletableFuture<ResolvedSkin>>()

    /** 网络查询放在独立线程池，避免阻塞 Folia 的异步调度器。 */
    private val lookup = Executors.newFixedThreadPool(2) { Thread(it, "YuiMorph-Skin").apply { isDaemon = true } }
    private val io = Executors.newSingleThreadExecutor { Thread(it, "YuiMorph-SkinIO").apply { isDaemon = true } }

    fun load() {
        if (!file.exists()) return
        try {
            val type = object : TypeToken<Map<String, Cached>>() {}.type
            gson.fromJson<Map<String, Cached>>(file.readText(Charsets.UTF_8), type)?.let(cache::putAll)
        } catch (e: Exception) {
            plugin.logger.log(Level.WARNING, "读取皮肤缓存失败，将重新获取", e)
        }
    }

    fun resolve(name: String): CompletableFuture<ResolvedSkin> {
        val key = name.lowercase()

        plugin.server.getPlayerExact(name)?.let { online ->
            val textures = online.playerProfile.properties.firstOrNull { it.name == "textures" }
            val skin = textures?.let { Skin(it.value, it.signature) }
            if (skin != null) remember(key, online.name, skin)
            return CompletableFuture.completedFuture(ResolvedSkin(online.name, skin))
        }

        val cached = cache[key]
        if (cached != null && System.currentTimeMillis() - cached.fetchedAt < ttlMillis()) {
            return CompletableFuture.completedFuture(cached.toResolved())
        }

        // 同一个名字同时只查一次
        return inflight.computeIfAbsent(key) {
            CompletableFuture.supplyAsync({ fetch(name, cached) }, lookup)
                .whenComplete { _, _ -> inflight.remove(key) }
        }
    }

    private fun fetch(name: String, stale: Cached?): ResolvedSkin = try {
        val profile = plugin.server.createProfile(name)
        if (profile.complete(true)) {
            val textures = profile.properties.firstOrNull { it.name == "textures" }
            val skin = textures?.let { Skin(it.value, it.signature) }
            val resolvedName = profile.name ?: name
            remember(name.lowercase(), resolvedName, skin)
            ResolvedSkin(resolvedName, skin)
        } else {
            stale?.toResolved() ?: ResolvedSkin(name, null)
        }
    } catch (e: Exception) {
        plugin.logger.log(Level.FINE, "获取 $name 的皮肤失败", e)
        stale?.toResolved() ?: ResolvedSkin(name, null)
    }

    private fun remember(key: String, name: String, skin: Skin?) {
        cache[key] = Cached(name, skin?.value, skin?.signature, System.currentTimeMillis())
        val snapshot = HashMap(cache)
        io.execute {
            try {
                val tmp = Files.createTempFile(plugin.dataFolder.toPath(), "skins", ".tmp")
                Files.writeString(tmp, gson.toJson(snapshot))
                Files.move(tmp, file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
            } catch (e: Exception) {
                plugin.logger.log(Level.WARNING, "保存皮肤缓存失败", e)
            }
        }
    }

    private fun Cached.toResolved() = ResolvedSkin(name, value?.let { Skin(it, signature) })

    fun shutdown() {
        lookup.shutdownNow()
        io.shutdown()
    }
}
