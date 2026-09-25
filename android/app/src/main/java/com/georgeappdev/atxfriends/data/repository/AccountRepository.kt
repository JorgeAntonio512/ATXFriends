package com.georgeappdev.atxfriends.data.repository

import com.google.firebase.functions.FirebaseFunctions
import kotlinx.coroutines.tasks.await

/** Account deletion (a seam for tests). */
fun interface AccountDeleter {
    /**
     * Permanently deletes the signed-in account server-side and returns the plan IDs the user
     * was part of. Throws on failure; retrying is safe.
     */
    suspend fun deleteMyAccount(): List<String>
}

/**
 * iOS AccountDeletionService.deleteMyAccount: calls the existing `deleteMyAccount` callable
 * Cloud Function (region us-central1, no arguments). The function deletes everything the user
 * created, their photos and finally their Firebase Auth user. Nothing is deleted on the device.
 */
class AccountRepository(private val functions: FirebaseFunctions) : AccountDeleter {
    override suspend fun deleteMyAccount(): List<String> {
        val result = functions.getHttpsCallable(FUNCTION).call().await()
        return planIDs(result.getData())
    }

    companion object {
        const val FUNCTION = "deleteMyAccount"
        const val REGION = "us-central1"

        /** `{ planIDs: [String] }`, read leniently like iOS (anything else → no IDs). */
        fun planIDs(data: Any?): List<String> =
            ((data as? Map<*, *>)?.get("planIDs") as? List<*>)?.filterIsInstance<String>().orEmpty()
    }
}
