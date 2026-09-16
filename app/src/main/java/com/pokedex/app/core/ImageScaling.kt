package com.pokedex.app.core

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import java.io.ByteArrayOutputStream
import kotlin.math.roundToInt

/** Result of preparing a photo for the vision model. */
data class ScaledImage(val base64: String, val mediaType: String = "image/jpeg")

object ImageScaling {

    const val MAX_EDGE = 1024
    private const val JPEG_QUALITY = 85

    /** Decode [uri], fix EXIF rotation, downscale so the long edge is <= [MAX_EDGE], re-encode as JPEG. */
    fun fromUri(context: Context, uri: Uri): ScaledImage {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: error("Could not read image")
        return fromBytes(bytes)
    }

    fun fromBytes(bytes: ByteArray): ScaledImage {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)

        val longEdge = maxOf(bounds.outWidth, bounds.outHeight).coerceAtLeast(1)
        var sample = 1
        while (longEdge / (sample * 2) >= MAX_EDGE) sample *= 2

        val decodeOptions = BitmapFactory.Options().apply { inSampleSize = sample }
        var bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOptions)
            ?: error("Could not decode image")

        bitmap = applyExifRotation(bytes, bitmap)

        val scale = MAX_EDGE.toFloat() / maxOf(bitmap.width, bitmap.height)
        if (scale < 1f) {
            bitmap = Bitmap.createScaledBitmap(
                bitmap,
                (bitmap.width * scale).roundToInt().coerceAtLeast(1),
                (bitmap.height * scale).roundToInt().coerceAtLeast(1),
                true,
            )
        }

        val out = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
        val encoded = android.util.Base64.encodeToString(out.toByteArray(), android.util.Base64.NO_WRAP)
        return ScaledImage(encoded)
    }

    private fun applyExifRotation(bytes: ByteArray, bitmap: Bitmap): Bitmap {
        val orientation = runCatching {
            ExifInterface(bytes.inputStream()).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL,
            )
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)

        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            else -> return bitmap
        }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }
}
