package com.example.caprichometro.infrastructure.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
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
            decodeSampledAndRotatedBitmap(context, imageUri, maxDimension)
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

    private fun decodeSampledAndRotatedBitmap(
        context: Context,
        imageUri: Uri,
        maxDimension: Int
    ): Bitmap {
        val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(imageUri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, boundsOptions)
        }

        var inSampleSize = 1
        if (boundsOptions.outHeight > maxDimension || boundsOptions.outWidth > maxDimension) {
            val halfHeight = boundsOptions.outHeight / 2
            val halfWidth = boundsOptions.outWidth / 2
            while ((halfHeight / inSampleSize) >= maxDimension && (halfWidth / inSampleSize) >= maxDimension) {
                inSampleSize *= 2
            }
        }

        val decodeOptions = BitmapFactory.Options().apply {
            this.inSampleSize = inSampleSize
        }
        val decoded = context.contentResolver.openInputStream(imageUri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, decodeOptions)
        } ?: throw IllegalStateException("No se pudo decodificar la imagen seleccionada")

        val orientation = context.contentResolver.openInputStream(imageUri)?.use { stream ->
            ExifInterface(stream).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )
        } ?: ExifInterface.ORIENTATION_NORMAL

        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.postRotate(90f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.postRotate(270f)
                matrix.postScale(-1f, 1f)
            }
            else -> return decoded
        }

        val rotated = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
        if (rotated != decoded) {
            decoded.recycle()
        }
        return rotated
    }
}
