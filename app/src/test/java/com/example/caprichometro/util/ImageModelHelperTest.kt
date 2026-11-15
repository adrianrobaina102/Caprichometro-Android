package com.example.caprichometro.util

import com.example.caprichometro.infrastructure.util.BoundedImageByteCache
import com.example.caprichometro.infrastructure.util.ImageModelHelper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ImageModelHelperTest {

    @Test
    fun `devuelve url original sin decodificar cuando esta vacia no es data uri o esta mal formada`() {
        var decoderCalls = 0
        val cache = BoundedImageByteCache(maxBytes = 200, maxEntryBytes = 100)
        val decoder: (String) -> ByteArray = {
            decoderCalls++
            byteArrayOf(1, 2, 3)
        }

        assertThat(ImageModelHelper.parseImageModelInternal("", cache, decoder)).isEqualTo("")
        assertThat(ImageModelHelper.parseImageModelInternal("https://example.com/a.webp", cache, decoder))
            .isEqualTo("https://example.com/a.webp")
        assertThat(ImageModelHelper.parseImageModelInternal("data:image/webp;base64", cache, decoder))
            .isEqualTo("data:image/webp;base64")
        assertThat(ImageModelHelper.parseImageModelInternal("data:image/webp;base64,", cache, decoder))
            .isEqualTo("data:image/webp;base64,")

        assertThat(decoderCalls).isZero()
        assertThat(cache.entryCount()).isZero()
    }

    @Test
    fun `cache hit reutiliza la misma instancia exacta de ByteArray sin volver a decodificar`() {
        var decoderCalls = 0
        val cache = BoundedImageByteCache(maxBytes = 200, maxEntryBytes = 100)
        val decoder: (String) -> ByteArray = {
            decoderCalls++
            byteArrayOf(10, 20, 30, 40)
        }

        val uri = "data:image/webp;base64,QUJDRA=="
        val first = ImageModelHelper.parseImageModelInternal(uri, cache, decoder) as ByteArray
        val second = ImageModelHelper.parseImageModelInternal(uri, cache, decoder) as ByteArray

        assertThat(decoderCalls).isEqualTo(1)
        assertThat(second).isSameAs(first)
        assertThat(cache.currentSizeBytes()).isEqualTo(4)
    }

    @Test
    fun `evicta entradas menos recientes cuando se supera el limite en bytes respetando LRU`() {
        val cache = BoundedImageByteCache(maxBytes = 100, maxEntryBytes = 60)
        val a = ByteArray(40) { 1 }
        val b = ByteArray(40) { 2 }
        val c = ByteArray(40) { 3 }

        cache.put("k1", a)
        cache.put("k2", b)
        // Accedemos a k1 para promoverlo a más reciente antes de insertar k3
        assertThat(cache.get("k1")).isSameAs(a)

        cache.put("k3", c)

        assertThat(cache.currentSizeBytes()).isEqualTo(80)
        assertThat(cache.entryCount()).isEqualTo(2)
        assertThat(cache.get("k1")).isSameAs(a)
        assertThat(cache.get("k2")).isNull()
        assertThat(cache.get("k3")).isSameAs(c)
    }

    @Test
    fun `no cachea entradas mayores a maxEntryBytes pero devuelve los bytes decodificados`() {
        val cache = BoundedImageByteCache(maxBytes = 100, maxEntryBytes = 50)
        val small = ByteArray(30) { 7 }
        cache.put("small", small)

        val oversized = ByteArray(80) { 9 }
        val result = ImageModelHelper.parseImageModelInternal(
            url = "data:image/webp;base64,OVERSIZED",
            cache = cache
        ) { oversized }

        assertThat(result).isSameAs(oversized)
        assertThat(cache.entryCount()).isEqualTo(1)
        assertThat(cache.currentSizeBytes()).isEqualTo(30)
        assertThat(cache.get("small")).isSameAs(small)
    }

    @Test
    fun `si falla la decodificacion devuelve la url original y clear vacia la cache`() {
        val cache = BoundedImageByteCache(maxBytes = 200, maxEntryBytes = 100)
        cache.put("ok", ByteArray(16) { 1 })

        val bad = ImageModelHelper.parseImageModelInternal("data:image/webp;base64,bad", cache) {
            throw IllegalArgumentException("bad base64")
        }

        assertThat(bad).isEqualTo("data:image/webp;base64,bad")
        assertThat(cache.currentSizeBytes()).isEqualTo(16)

        cache.clear()
        assertThat(cache.currentSizeBytes()).isZero()
        assertThat(cache.entryCount()).isZero()
    }
}
