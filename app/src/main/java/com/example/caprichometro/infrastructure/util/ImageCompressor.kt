package com.example.caprichometro.infrastructure.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import androidx.core.graphics.scale
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

object ImageCompressor {

    const val DEFAULT_MAX_OUTPUT_BYTES = 550_000

    suspend fun compressUriToWebP(
        context: Context,
        imageUri: Uri,
        maxDimension: Int = 800,
        quality: Int = 75,
        maxOutputBytes: Int = DEFAULT_MAX_OUTPUT_BYTES
    ): ByteArray = withContext(Dispatchers.IO) {
        val originalBitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val source = ImageDecoder.createSource(context.contentResolver, imageUri)
            ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                val (targetW, targetH) = computeTargetSize(info.size.width, info.size.height, maxDimension)
                decoder.setTargetSize(targetW, targetH)
            }
        } else {
            decodeSampledAndRotatedBitmap(context, imageUri, maxDimension)
        }

        val format = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Bitmap.CompressFormat.WEBP_LOSSY
        } else {
            @Suppress("DEPRECATION")
            Bitmap.CompressFormat.WEBP
        }

        var workingBitmap = originalBitmap
        try {
            val (targetWidth, targetHeight) = computeTargetSize(workingBitmap.width, workingBitmap.height, maxDimension)
            if (targetWidth != workingBitmap.width || targetHeight != workingBitmap.height) {
                val scaled = workingBitmap.scale(targetWidth, targetHeight)
                if (scaled !== workingBitmap) {
                    workingBitmap.recycle()
                    workingBitmap = scaled
                }
            }

            val outputStream = ByteArrayOutputStream()
            val candidateQualities = listOf(quality, 55, 40, 25).distinct()
            for (q in candidateQualities) {
                outputStream.reset()
                workingBitmap.compress(format, q, outputStream)
                if (outputStream.size() <= maxOutputBytes) {
                    return@withContext outputStream.toByteArray()
                }
            }

            // Si aun a calidad mínima supera el límite Firestore, reescala un nivel adicional
            val reducedDim = (maxDimension / 2).coerceAtLeast(256)
            val (smallW, smallH) = computeTargetSize(workingBitmap.width, workingBitmap.height, reducedDim)
            if (smallW != workingBitmap.width || smallH != workingBitmap.height) {
                val smaller = workingBitmap.scale(smallW, smallH)
                if (smaller !== workingBitmap) {
                    workingBitmap.recycle()
                    workingBitmap = smaller
                }
                outputStream.reset()
                workingBitmap.compress(format, 40, outputStream)
            }
            outputStream.toByteArray()
        } finally {
            if (!workingBitmap.isRecycled) {
                workingBitmap.recycle()
            }
        }
    }

    private fun computeTargetSize(width: Int, height: Int, maxDimension: Int): Pair<Int, Int> {
        if (width <= 0 || height <= 0) return Pair(maxDimension, maxDimension)
        val ratio = width.toFloat() / height.toFloat()
        return if (width > height) {
            val w = minOf(width, maxDimension)
            Pair(w, (w / ratio).toInt().coerceAtLeast(1))
        } else {
            val h = minOf(height, maxDimension)
            Pair((h * ratio).toInt().coerceAtLeast(1), h)
        }
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
            while ((halfHeight / inSampleSize) >= maxDimension || (halfWidth / inSampleSize) >= maxDimension) {
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

        val rotated = try {
            Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
        } catch (t: Throwable) {
            if (!decoded.isRecycled) decoded.recycle()
            throw t
        }
        if (rotated != decoded) {
            decoded.recycle()
        }
        return rotated
    }
}
