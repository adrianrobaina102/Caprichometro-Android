package com.example.caprichometro.infrastructure.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

object ImageCompressor {

    suspend fun compressUriToWebP(
        context: Context,
        imageUri: Uri,
        maxDimension: Int = 800,
        quality: Int = 75
    ): ByteArray = withContext(Dispatchers.IO) {
        val originalBitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val source = ImageDecoder.createSource(context.contentResolver, imageUri)
            ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        } else {
            @Suppress("DEPRECATION")
            MediaStore.Images.Media.getBitmap(context.contentResolver, imageUri)
        }

        val width = originalBitmap.width
        val height = originalBitmap.height
        val ratio = width.toFloat() / height.toFloat()

        val (targetWidth, targetHeight) = if (width > height) {
            val w = minOf(width, maxDimension)
            Pair(w, (w / ratio).toInt().coerceAtLeast(1))
        } else {
            val h = minOf(height, maxDimension)
            Pair((h * ratio).toInt().coerceAtLeast(1), h)
        }

        val scaledBitmap = if (targetWidth != width || targetHeight != height) {
            Bitmap.createScaledBitmap(originalBitmap, targetWidth, targetHeight, true)
        } else {
            originalBitmap
        }

        val outputStream = ByteArrayOutputStream()
        val format = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Bitmap.CompressFormat.WEBP_LOSSY
        } else {
            @Suppress("DEPRECATION")
            Bitmap.CompressFormat.WEBP
        }

        scaledBitmap.compress(format, quality, outputStream)

        if (scaledBitmap != originalBitmap) {
            scaledBitmap.recycle()
        }
        originalBitmap.recycle()

        outputStream.toByteArray()
    }
}
