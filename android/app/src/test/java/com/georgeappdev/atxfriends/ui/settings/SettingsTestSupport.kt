package com.georgeappdev.atxfriends.ui.settings

import com.georgeappdev.atxfriends.data.TestDocs
import com.georgeappdev.atxfriends.data.firestore.DocumentUpdate
import com.georgeappdev.atxfriends.data.model.UserProfile
import com.georgeappdev.atxfriends.data.repository.AuthUser
import com.georgeappdev.atxfriends.data.repository.ProfileWriter
import com.georgeappdev.atxfriends.data.repository.UserDoc
import com.georgeappdev.atxfriends.session.SessionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import java.io.IOException
import java.time.Instant

/** Records every `users/{uid}` update; set [failNext] / [failAll] to simulate a failed write. */
class FakeProfileWriter : ProfileWriter {
    val writes = mutableListOf<Pair<String, DocumentUpdate>>()
    var failAll = false
    var failNext = false

    /** When set, called before each write; lets a test hold a write "in flight". */
    var beforeWrite: (suspend () -> Unit)? = null

    override suspend fun update(uid: String, update: DocumentUpdate) {
        beforeWrite?.invoke()
        if (failAll || failNext) {
            failNext = false
            throw IOException("offline")
        }
        writes += uid to update
    }

    val lastFields: Map<String, Any?> get() = writes.last().second.fields
}

/** A signed-in session holding the sample profile, settled synchronously. */
@OptIn(ExperimentalCoroutinesApi::class)
class TestSession(profile: UserProfile = sampleProfile()) {
    val user = MutableStateFlow<AuthUser?>(AuthUser(profile.id, "sam@example.com"))
    val scope = CoroutineScope(UnconfinedTestDispatcher())
    val manager = SessionManager(user, { UserDoc.Found(profile) }, { user.value = null }, scope)

    val profile: UserProfile get() = manager.currentProfile!!
}

fun sampleProfile(): UserProfile = UserProfile.fromFirestore("uid-sam", TestDocs.user())!!

val FIXED_NOW: Instant = Instant.ofEpochSecond(1_770_000_000)

/** Elements of a FieldValue.arrayUnion / arrayRemove (package-private in the Firestore SDK). */
fun fieldValueElements(value: Any?): List<Any?> {
    val field = value!!.javaClass.declaredFields.first { List::class.java.isAssignableFrom(it.type) }
    field.isAccessible = true
    @Suppress("UNCHECKED_CAST")
    return field.get(value) as List<Any?>
}
