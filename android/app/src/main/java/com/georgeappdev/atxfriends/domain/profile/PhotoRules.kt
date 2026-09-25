package com.georgeappdev.atxfriends.domain.profile

import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Profile photo rules. iOS Settings (PhotosSettingsView.processImage) scales the picked image to
 * at most 1024 px on its longest side, then FirebaseStorageService encodes it as JPEG at quality
 * 0.85 and uploads it to `profile_photos/{uid}/{uid}_photo_{index}.jpg` with content type
 * `image/jpeg`. Each slot has a fixed file name, so replacing a photo overwrites the old file
 * and nothing is left orphaned. Storage rules only accept images under 10 MB.
 */
object PhotoRules {
    const val SLOT_COUNT = 3
    const val MAX_DIMENSION = 1024
    const val JPEG_QUALITY = 85
    const val MAX_BYTES = 10 * 1024 * 1024
    const val CONTENT_TYPE = "image/jpeg"
    const val FOLDER = "profile_photos"

    fun fileName(uid: String, index: Int): String {
        require(index in 0 until SLOT_COUNT) { "Photo index must be 0, 1, or 2." }
        return "${uid}_photo_$index.jpg"
    }

    fun storagePath(uid: String, index: Int) = "$FOLDER/$uid/${fileName(uid, index)}"

    /** Storage rules: `request.resource.size < 10 * 1024 * 1024`. */
    fun isUnderSizeLimit(byteCount: Int) = byteCount < MAX_BYTES

    /** Size after fitting within [MAX_DIMENSION]; images already small enough are left as-is. */
    fun targetSize(width: Int, height: Int): Pair<Int, Int> {
        if (width <= MAX_DIMENSION && height <= MAX_DIMENSION) return width to height
        val ratio = min(MAX_DIMENSION.toDouble() / width, MAX_DIMENSION.toDouble() / height)
        return (width * ratio).roundToInt().coerceAtLeast(1) to (height * ratio).roundToInt().coerceAtLeast(1)
    }

    /** iOS updateSinglePhoto: pads missing slots with "" and replaces only [index]. */
    fun replacing(current: List<String>, index: Int, url: String): List<String> {
        require(index in 0 until SLOT_COUNT) { "Photo index must be 0, 1, or 2." }
        val result = current.toMutableList()
        while (result.size <= index) result += ""
        result[index] = url
        return result
    }
}
