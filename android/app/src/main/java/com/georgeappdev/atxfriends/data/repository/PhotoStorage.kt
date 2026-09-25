package com.georgeappdev.atxfriends.data.repository

import com.georgeappdev.atxfriends.domain.profile.PhotoRules
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.storageMetadata
import kotlinx.coroutines.tasks.await

/** Uploads a profile photo (a seam for tests). */
fun interface PhotoUploader {
    /** Uploads [jpeg] into slot [index] and returns its download URL. Throws on failure. */
    suspend fun upload(uid: String, index: Int, jpeg: ByteArray): String
}

/** Firebase Storage `profile_photos/{uid}/{uid}_photo_{index}.jpg`, as iOS FirebaseStorageService. */
class PhotoStorage(private val storage: FirebaseStorage) : PhotoUploader {
    override suspend fun upload(uid: String, index: Int, jpeg: ByteArray): String {
        require(PhotoRules.isUnderSizeLimit(jpeg.size)) { "Photo is over the 10 MB limit" }
        val ref = storage.reference.child(PhotoRules.storagePath(uid, index))
        ref.putBytes(jpeg, storageMetadata { contentType = PhotoRules.CONTENT_TYPE }).await()
        return ref.downloadUrl.await().toString()
    }
}
