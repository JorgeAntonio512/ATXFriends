package com.georgeappdev.atxfriends.data.repository

import com.georgeappdev.atxfriends.data.firestore.ActivityDocFields
import com.georgeappdev.atxfriends.data.firestore.Collections
import com.georgeappdev.atxfriends.data.firestore.DocReader
import com.georgeappdev.atxfriends.domain.matching.ActivityCategory
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import java.time.Instant

/** One entry of the shared `activities` catalog. The doc ID is the activity ID users store. */
data class CatalogActivity(val id: String, val name: String)

/** The shared activity catalog (a seam for tests). */
interface ActivityCatalog {
    /** Every activity, sorted by name. Throws on network/permission failure. */
    suspend fun fetchAll(): List<CatalogActivity>

    /** Creates a new user-added activity and returns it. Throws on failure. */
    suspend fun addCustom(name: String, category: ActivityCategory): CatalogActivity
}

/**
 * `activities` (the Settings activity picker and the plan composer's suggestions) — read with `order(by: "name")` like iOS `fetchActivities`. Android never seeds
 * the catalog (iOS only does that when the collection is empty, which it isn't in production).
 */
class ActivityRepository(private val db: FirebaseFirestore) : ActivityCatalog {

    override suspend fun fetchAll(): List<CatalogActivity> =
        db.collection(Collections.ACTIVITIES).orderBy(ActivityDocFields.NAME).get().await()
            .documents.mapNotNull { doc -> doc.data?.let { ActivityDocs.decode(doc.id, it) } }

    /** Every activity name, sorted by name — the plan composer's suggestion chips. Throws on failure. */
    suspend fun fetchActivityNames(): List<String> = fetchAll().map { it.name }

    /**
     * iOS `addActivity(_:category:)`. Activities are append-only (rules deny updates), so this
     * only ever creates a brand-new document; `add()` can't overwrite anything. The doc ID is
     * Firestore's auto-ID rather than iOS's UUID string — IDs are opaque everywhere they're used.
     */
    override suspend fun addCustom(name: String, category: ActivityCategory): CatalogActivity {
        val ref = db.collection(Collections.ACTIVITIES).add(ActivityDocs.newCustom(name, category, Instant.now())).await()
        return CatalogActivity(ref.id, name)
    }
}

object ActivityDocs {
    /** Same required fields as iOS `firestoreDataToActivity`; null (skipped) otherwise. */
    fun decode(id: String, data: Map<String, Any?>): CatalogActivity? {
        val r = DocReader(data)
        val name = r.string(ActivityDocFields.NAME) ?: return null
        r.bool(ActivityDocFields.IS_USER_ADDED) ?: return null
        r.instant(ActivityDocFields.CREATED_AT) ?: return null
        return CatalogActivity(id, name)
    }

    /** The exact fields iOS writes for a user-added activity. */
    fun newCustom(name: String, category: ActivityCategory, now: Instant): Map<String, Any> = linkedMapOf(
        ActivityDocFields.NAME to name,
        ActivityDocFields.IS_USER_ADDED to true,
        ActivityDocFields.CREATED_AT to DocReader.toTimestamp(now),
        ActivityDocFields.CATEGORY to category.raw,
        ActivityDocFields.NEEDS_REVIEW to true,
    )
}
