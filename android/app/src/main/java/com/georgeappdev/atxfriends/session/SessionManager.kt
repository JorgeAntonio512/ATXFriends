package com.georgeappdev.atxfriends.session

import android.util.Log
import com.georgeappdev.atxfriends.data.model.UserProfile
import com.georgeappdev.atxfriends.data.repository.AuthRepository
import com.georgeappdev.atxfriends.data.repository.AuthUser
import com.georgeappdev.atxfriends.data.repository.GoogleNames
import com.georgeappdev.atxfriends.data.repository.UserDoc
import com.georgeappdev.atxfriends.data.repository.UserRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** Which top-level screen the app shows (the Android equivalent of iOS RootView's routing). */
sealed interface SessionState {
    data object Loading : SessionState
    data object SignedOut : SessionState

    /** Signed in with a finished profile → the six tabs. */
    data class Ready(val user: AuthUser, val profile: UserProfile) : SessionState

    /**
     * Signed in with no `users/{uid}` doc: a brand-new Google account, or a sign-up the app was
     * killed in the middle of. iOS RootView shows LocationGateView (`pendingNewSSOUser`); the
     * doc is created only when the gate passes, and backing out deletes the Auth account.
     */
    data class NeedsLocationGate(val user: AuthUser, val pending: PendingSignup) : SessionState

    /** Signed in with a doc whose `isProfileComplete` is false → iOS ProfileSetupFlowView. */
    data class NeedsProfileSetup(val user: AuthUser, val profile: UserProfile) : SessionState

    /**
     * Signed in, but `users/{uid}` exists without fields iOS requires. iOS would re-create
     * (overwrite) it; Android never replaces a document, so it stops here. Nothing is written.
     */
    data class NotFinished(val user: AuthUser) : SessionState

    /** Signed in, but the profile couldn't be read (offline, permission error). */
    data class LoadFailed(val user: AuthUser) : SessionState
}

/**
 * iOS `PendingNewSSOUser`: a signed-in account still waiting on the location gate, with no
 * `users` doc yet. [path] ("email", "google", or "resume" after a relaunch) is for logs only.
 */
data class PendingSignup(val uid: String, val displayName: String, val path: String)

@OptIn(ExperimentalCoroutinesApi::class)
class SessionManager(
    currentUser: Flow<AuthUser?>,
    private val fetchUser: suspend (uid: String) -> UserDoc,
    private val signOutAction: () -> Unit,
    scope: CoroutineScope,
) {
    constructor(auth: AuthRepository, users: UserRepository, scope: CoroutineScope) :
        this(auth.currentUser, users::fetchUser, auth::signOut, scope)

    private val retryTick = MutableStateFlow(0)
    private val profileEdits = MutableSharedFlow<UserProfile>(extraBufferCapacity = 16)
    private val pending = MutableStateFlow<PendingSignup?>(null)
    private val creatingAccount = MutableStateFlow(false)

    val state: StateFlow<SessionState> =
        combine(currentUser, retryTick, creatingAccount) { user, _, creating -> user to creating }
            .flatMapLatest { (user, creating) ->
                when {
                    user == null -> flowOf<SessionState>(SessionState.SignedOut)
                    // Email sign-up has created the Auth account but not yet its `users` doc:
                    // keep the sign-up screen up (iOS routes only after both succeed).
                    creating -> flowOf<SessionState>(SessionState.SignedOut)
                    else -> flow<SessionState> {
                        emit(SessionState.Loading)
                        val loaded = loadProfile(user)
                        Log.i(TAG, "session: ${user.uid} → ${loaded::class.simpleName}")
                        when (loaded) {
                            is SessionState.Ready -> {
                                emit(loaded)
                                emitAll(profileEdits.filter { it.id == user.uid }.map { SessionState.Ready(user, it) })
                            }
                            // No doc: iOS RootView re-derives the pending sign-up from the live
                            // Auth session when the app was killed mid-gate (orphan recovery).
                            is SessionState.NeedsLocationGate -> emitAll(
                                pending.map { p -> SessionState.NeedsLocationGate(user, p?.takeIf { it.uid == user.uid } ?: loaded.pending) }
                            )
                            else -> emit(loaded)
                        }
                    }
                }
            }
            .stateIn(scope, SharingStarted.Eagerly, SessionState.Loading)

    fun retry() {
        retryTick.value++
    }

    fun signOut() {
        pending.value = null
        signOutAction()
    }

    /** A new Google account passed Firebase sign-in: gate it before any doc exists. */
    fun startPendingSignup(signup: PendingSignup) {
        Log.i(TAG, "[Onboarding] path=${signup.path} step=locationGate gate=notRun")
        pending.value = signup
    }

    /**
     * The pending account now has its `users` doc (gate passed) or no longer exists (abandoned):
     * forget it and route again from what's actually stored.
     */
    fun pendingSignupResolved() {
        pending.value = null
        retry()
    }

    /**
     * Runs email sign-up's "create the Auth account, then its `users` doc" while holding the
     * signed-out screens, so the half-made account never routes anywhere. Routes afresh after.
     */
    suspend fun <T> holdRoutingWhile(block: suspend () -> T): T {
        creatingAccount.value = true
        try {
            return block()
        } finally {
            creatingAccount.value = false
        }
    }

    /**
     * Replaces the signed-in user's profile in place after a successful write (e.g. from
     * Settings), so every tab sees the change without a reload. Ignored for any other user.
     */
    fun profileChanged(profile: UserProfile) {
        profileEdits.tryEmit(profile)
    }

    /** The signed-in user's profile, when ready. */
    val currentProfile: UserProfile?
        get() = (state.value as? SessionState.Ready)?.profile

    private suspend fun loadProfile(user: AuthUser): SessionState = try {
        when (val doc = fetchUser(user.uid)) {
            is UserDoc.Found ->
                if (doc.profile.isProfileComplete) SessionState.Ready(user, doc.profile)
                else SessionState.NeedsProfileSetup(user, doc.profile)
            UserDoc.Missing -> SessionState.NeedsLocationGate(user, resumedSignup(user))
            UserDoc.Unreadable -> SessionState.NotFinished(user)
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w(TAG, "session: couldn't load users/${user.uid}", e)
        SessionState.LoadFailed(user)
    }

    /** iOS `PendingNewSSOUser(userID, displayName: Auth displayName, provider: "sso")`. */
    private fun resumedSignup(user: AuthUser) =
        PendingSignup(user.uid, GoogleNames.initialDisplayName(user.displayName), path = "resume")

    private companion object {
        const val TAG = "ATXF"
    }
}
