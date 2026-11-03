package com.example.caprichometro.infrastructure.util

import android.util.Base64
import android.util.LruCache

object ImageModelHelper {
    // Cache en memoria para reutilizar la misma instancia exacta de ByteArray
    // Evita decodificar en cada recomposición y evita que Coil parpadee
    private val memoryCache = LruCache<String, ByteArray>(30)

    fun parseImageModel(url: String): Any {
        if (url.isBlank()) return url
        if (!url.startsWith("data:")) return url

        val cached = memoryCache.get(url)
        if (cached != null) return cached

        return try {
            val base64Data = url.substringAfter(",")
            val decoded = Base64.decode(base64Data, Base64.DEFAULT)
            memoryCache.put(url, decoded)
            decoded
        } catch (_: Exception) {
            url
        }
    }
}
