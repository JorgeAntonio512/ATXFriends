package com.georgeappdev.atxfriends.ui.settings

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import com.georgeappdev.atxfriends.domain.profile.PhotoRules
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

/**
 * Turns a picked image into the JPEG iOS uploads: fitted within 1024 px on its longest side
 * (PhotosSettingsView.processImage) and encoded at quality 0.85 (FirebaseStorageService),
 * upright according to the photo's orientation. Returns null if the image can't be read.
 */
class PhotoEncoder(private val resolver: ContentResolver) {

    suspend fun encode(uri: Uri): ByteArray? = withContext(Dispatchers.IO) {
        val bitmap = runCatching { decode(uri) }.getOrNull() ?: return@withContext null
        ByteArrayOutputStream().use { out ->
            if (!bitmap.compress(Bitmap.CompressFormat.JPEG, PhotoRules.JPEG_QUALITY, out)) return@withContext null
            out.toByteArray()
        }
    }

    private fun decode(uri: Uri): Bitmap? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) decodeModern(uri) else decodeLegacy(uri)

    /** ImageDecoder applies EXIF orientation itself and handles HEIF. */
    private fun decodeModern(uri: Uri): Bitmap =
        ImageDecoder.decodeBitmap(ImageDecoder.createSource(resolver, uri)) { decoder, info, _ ->
            val (w, h) = PhotoRules.targetSize(info.size.width, info.size.height)
            decoder.setTargetSize(w, h)
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }

    /** Android 8.x: sample down while decoding, scale exactly, then rotate per EXIF. */
    private fun decodeLegacy(uri: Uri): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        val (targetW, targetH) = PhotoRules.targetSize(bounds.outWidth, bounds.outHeight)
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= targetW && bounds.outHeight / (sample * 2) >= targetH) sample *= 2
        val sampled = resolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: return null
        val scaled = if (sampled.width == targetW && sampled.height == targetH) sampled
        else Bitmap.createScaledBitmap(sampled, targetW, targetH, true)
        val degrees = resolver.openInputStream(uri)?.use { stream ->
            when (ExifInterface(stream).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
        } ?: 0f
        if (degrees == 0f) return scaled
        return Bitmap.createBitmap(scaled, 0, 0, scaled.width, scaled.height, Matrix().apply { postRotate(degrees) }, true)
    }
}
