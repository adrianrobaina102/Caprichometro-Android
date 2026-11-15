package com.example.caprichometro.infrastructure.util

import android.util.Base64

/**
 * Caché LRU acotada estrictamente en bytes para evitar picos de memoria con imágenes Base64 WebP.
 */
internal class BoundedImageByteCache(
    private val maxBytes: Int = DEFAULT_MAX_CACHE_BYTES,
    private val maxEntryBytes: Int = DEFAULT_MAX_ENTRY_BYTES
) {
    private val entries = LinkedHashMap<String, ByteArray>(16, 0.75f, true)
    private var currentBytes: Int = 0

    @Synchronized
    fun get(key: String): ByteArray? = entries[key]

    @Synchronized
    fun put(key: String, value: ByteArray) {
        if (value.isEmpty() || value.size > maxEntryBytes || maxBytes <= 0) return
        val previous = entries.put(key, value)
        if (previous != null) {
            currentBytes -= previous.size
        }
        currentBytes += value.size
        val iterator = entries.entries.iterator()
        while (currentBytes > maxBytes && iterator.hasNext()) {
            val eldest = iterator.next()
            currentBytes -= eldest.value.size
            iterator.remove()
        }
    }

    @Synchronized
    fun clear() {
        entries.clear()
        currentBytes = 0
    }

    @Synchronized
    fun currentSizeBytes(): Int = currentBytes

    @Synchronized
    fun entryCount(): Int = entries.size

    companion object {
        const val DEFAULT_MAX_CACHE_BYTES = 4 * 1024 * 1024
        const val DEFAULT_MAX_ENTRY_BYTES = 1 * 1024 * 1024
    }
}

object ImageModelHelper {
    private val memoryCache = BoundedImageByteCache()

    fun parseImageModel(url: String): Any =
        parseImageModelInternal(url, memoryCache) { base64Data ->
            Base64.decode(base64Data, Base64.DEFAULT)
        }

    fun clearCache() {
        memoryCache.clear()
    }

    internal fun parseImageModelInternal(
        url: String,
        cache: BoundedImageByteCache,
        decodeBase64: (String) -> ByteArray
    ): Any {
        if (url.isBlank() || !url.startsWith("data:")) return url

        val commaIndex = url.indexOf(',')
        if (commaIndex < 0 || commaIndex == url.lastIndex) return url

        cache.get(url)?.let { return it }

        return try {
            val base64Data = url.substring(commaIndex + 1)
            val decoded = decodeBase64(base64Data)
            if (decoded.isEmpty()) return url
            cache.put(url, decoded)
            decoded
        } catch (_: Exception) {
            url
        }
    }
}
