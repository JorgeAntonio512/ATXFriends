package com.georgeappdev.atxfriends.push

import com.georgeappdev.atxfriends.data.firestore.DocumentUpdate
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.time.Instant

class PushTest {

    /** Elements of a FieldValue.arrayUnion / arrayRemove (package-private in the SDK). */
    private fun elements(value: Any?): List<Any?> {
        val field = value!!.javaClass.declaredFields.first { List::class.java.isAssignableFrom(it.type) }
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        return field.get(value) as List<Any?>
    }

    private fun kind(value: Any?) = value!!.javaClass.simpleName

    // Writes — exactly iOS FirestoreService.addFCMToken / removeFCMToken / migrateLegacyFCMTokenIfNeeded.

    private val now = Instant.ofEpochSecond(1_770_000_000)

    @Test
    fun addToken_isArrayUnionPlusTimestamp() {
        val f = PushWrites.addToken("tok", now).fields
        assertEquals(listOf("fcmTokens", "fcmTokenUpdatedAt"), f.keys.toList())
        assertTrue(kind(f["fcmTokens"]).contains("Union"))
        assertEquals(listOf("tok"), elements(f["fcmTokens"]))
        assertEquals(Timestamp(1_770_000_000, 0), f["fcmTokenUpdatedAt"])
    }

    @Test
    fun removeToken_isArrayRemoveOfThisDevicesTokenOnly() {
        val f = PushWrites.removeToken("tok").fields
        assertEquals(listOf("fcmTokens"), f.keys.toList())
        assertTrue(kind(f["fcmTokens"]).contains("Remove"))
        assertEquals(listOf("tok"), elements(f["fcmTokens"]))
    }

    @Test
    fun legacyMigration_foldsTheOldFieldIn_andDeletesIt() {
        val f = PushWrites.migrateLegacy("old").fields
        assertEquals(listOf("fcmTokens", "fcmToken", "fcmTokenUpdatedAt"), f.keys.toList())
        assertEquals(listOf("old"), elements(f["fcmTokens"]))
        assertTrue(f["fcmToken"] is FieldValue && kind(f["fcmToken"]).contains("Delete"))
        assertTrue(kind(f["fcmTokenUpdatedAt"]).contains("Delete"))
    }

    // Registration lifecycle.

    private class FakeStore : TokenStore {
        val writes = mutableListOf<Pair<String, DocumentUpdate>>()
        var legacy: String? = null
        var fail = false
        var hang = false
        override suspend fun update(uid: String, update: DocumentUpdate) {
            if (hang) awaitCancellation()
            if (fail) throw IOException("offline")
            writes += uid to update
        }
        override suspend fun legacyToken(uid: String) = legacy
    }

    private class FakeDevice(var token: String = "device-token") : DeviceToken {
        var deletes = 0
        var failDelete = false
        override suspend fun current() = token
        override suspend fun delete() {
            if (failDelete) throw IOException("offline")
            deletes++
            token = "fresh-token"
        }
    }

    private class FakePending : PendingTokenDelete {
        override var pending = false
    }

    private val store = FakeStore()
    private val device = FakeDevice()
    private val pending = FakePending()
    private val tokens = PushTokens(store, device, pending) { now }

    private fun tokenIn(i: Int) = elements(store.writes[i].second.fields["fcmTokens"]).single()

    @Test
    fun signIn_storesThisDevicesToken_onTheUsersDoc() = runTest {
        tokens.onSignedIn("uid-a")
        assertEquals(1, store.writes.size)
        assertEquals("uid-a", store.writes[0].first)
        assertEquals("device-token", tokenIn(0))
    }

    @Test
    fun signIn_migratesALegacyToken() = runTest {
        store.legacy = "legacy-token"
        tokens.onSignedIn("uid-a")
        assertEquals(2, store.writes.size)
        assertEquals("legacy-token", tokenIn(1))
        assertTrue("fcmToken" in store.writes[1].second.fields)
    }

    @Test
    fun newToken_storedOnlyWhileSignedIn() = runTest {
        tokens.onNewToken(null, "t2")
        assertTrue(store.writes.isEmpty())
        tokens.onNewToken("uid-a", "t2")
        assertEquals("t2", tokenIn(0))
    }

    @Test
    fun signOut_removesThisDevicesToken_andNeedsNoTokenDelete() = runTest {
        tokens.beforeSignOut("uid-a")
        assertTrue(kind(store.writes.single().second.fields["fcmTokens"]).contains("Remove"))
        assertEquals("device-token", tokenIn(0))
        assertEquals(0, device.deletes)
        assertFalse(pending.pending)
    }

    @Test
    fun signOut_whenRemovalFails_deletesTheTokenSoItStopsReceiving() = runTest {
        store.fail = true // e.g. offline, or the account was just deleted
        tokens.beforeSignOut("uid-a")
        assertEquals(1, device.deletes)
        assertFalse(pending.pending)
    }

    @Test
    fun signOut_neverHangs_onAStuckWrite() = runTest {
        store.hang = true
        tokens.beforeSignOut("uid-a") // returns after the timeout (virtual time)
        assertEquals(1, device.deletes)
    }

    @Test
    fun signOut_whenEverythingFails_retriesTheDeleteBeforeTheNextSignIn() = runTest {
        store.fail = true
        device.failDelete = true
        tokens.beforeSignOut("uid-a")
        assertTrue(pending.pending)

        store.fail = false
        device.failDelete = false
        tokens.onSignedIn("uid-b")
        assertFalse(pending.pending)
        assertEquals(1, device.deletes)
        assertEquals("the next account gets a new token, not the old one", "fresh-token", tokenIn(0))
    }

    @Test
    fun registrationFailures_neverThrow() = runTest {
        store.fail = true
        tokens.onSignedIn("uid-a")
        tokens.onNewToken("uid-a", "t")
    }

    // Channels ↔ iOS preference toggles (and the preference each Cloud Function checks).

    @Test
    fun eachPushTypeGoesToTheChannelOfItsPreference() {
        assertEquals(PushChannel.NEW_MATCHES, PushType.channelFor("newMatch"))
        assertEquals(PushChannel.MESSAGES, PushType.channelFor("newMessage"))
        assertEquals(PushChannel.PLAN_REQUESTS, PushType.channelFor("planRequest"))
        assertEquals(PushChannel.PLAN_REQUESTS, PushType.channelFor("planRescheduleRequested"))
        assertEquals(PushChannel.PLAN_CONFIRMATIONS, PushType.channelFor("planConfirmed"))
        assertEquals(PushChannel.PLAN_CONFIRMATIONS, PushType.channelFor("planRescheduleDeclined"))
        assertEquals(PushChannel.OTHER, PushType.channelFor("somethingNew"))
        assertEquals(PushChannel.OTHER, PushType.channelFor(null))
    }

    @Test
    fun channelIds_areStable() {
        assertEquals(
            listOf("new_matches", "messages", "plan_requests", "plan_confirmations", "other"),
            PushChannel.entries.map { it.id },
        )
    }

    // Tapping each payload (the exact data maps the Cloud Functions send).

    @Test
    fun tap_newMatch_opensTheThread_withMatchesFallback() {
        val data = mapOf("type" to "newMatch", "matchID" to "m1", "otherUserId" to "u2")
        assertEquals(PushRoute("m1", fallbackToMatchesTab = true), PushRoute.fromData(data))
    }

    @Test
    fun tap_messageAndPlanPushes_openTheThread_noFallback() {
        val payloads = listOf(
            mapOf("type" to "newMessage", "messageId" to "x", "senderId" to "u2", "matchID" to "m1"),
            mapOf("type" to "planRequest", "planId" to "p", "proposerId" to "u2", "matchID" to "m1"),
            mapOf("type" to "planConfirmed", "planId" to "p", "matchID" to "m1"),
            mapOf("type" to "planRescheduleRequested", "planId" to "p", "matchID" to "m1"),
            mapOf("type" to "planRescheduleDeclined", "planId" to "p", "matchID" to "m1"),
        )
        payloads.forEach { assertEquals(it["type"], PushRoute("m1", false), PushRoute.fromData(it)) }
    }

    @Test
    fun tap_unknownType_orMissingMatch_goesNowhere() {
        assertNull(PushRoute.fromData(mapOf("type" to "mystery", "matchID" to "m1")))
        assertNull(PushRoute.fromData(mapOf("type" to "newMessage")))
        assertNull(PushRoute.fromData(mapOf("type" to "newMessage", "matchID" to "")))
        assertNull(PushRoute.fromData(emptyMap()))
    }

    // Foreground (iOS willPresent).

    @Test
    fun foreground_hiddenOnlyForTheOpenConversation() {
        val push = mapOf("type" to "newMessage", "matchID" to "m1")
        assertFalse(shouldShowInForeground(push, openMatchID = "m1"))
        assertTrue(shouldShowInForeground(push, openMatchID = "m2"))
        assertTrue(shouldShowInForeground(push, openMatchID = null))
        assertTrue(shouldShowInForeground(mapOf("type" to "other"), openMatchID = "m1"))
    }

    @Test
    fun openThreadTracker_onlyClearsItsOwnThread() {
        OpenThreadTracker.opened("m1")
        OpenThreadTracker.opened("m2")
        OpenThreadTracker.closed("m1")
        assertEquals("m2", OpenThreadTracker.openMatchID)
        OpenThreadTracker.closed("m2")
        assertNull(OpenThreadTracker.openMatchID)
    }

    @Test
    fun pushRoutes_consumeOnce() {
        val routes = PushRoutes()
        routes.post(PushRoute("m1", false))
        assertEquals(PushRoute("m1", false), routes.consume())
        assertNull(routes.consume())
    }
}
