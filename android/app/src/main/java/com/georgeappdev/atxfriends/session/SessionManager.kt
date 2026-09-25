package com.georgeappdev.atxfriends.session

import com.georgeappdev.atxfriends.data.model.UserProfile
import com.georgeappdev.atxfriends.data.repository.AuthRepository
import com.georgeappdev.atxfriends.data.repository.AuthUser
import com.georgeappdev.atxfriends.data.repository.UserDoc
import com.georgeappdev.atxfriends.data.repository.UserRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn

/** Which top-level screen the app shows (the Android equivalent of iOS RootView's routing). */
sealed interface SessionState {
    data object Loading : SessionState
    data object SignedOut : SessionState

    /** Signed in with a finished profile → the six tabs. */
    data class Ready(val user: AuthUser, val profile: UserProfile) : SessionState

    /**
     * Signed in, but no usable `users/{uid}` doc or `isProfileComplete` is false. iOS would route
     * to the location gate / profile setup, which Android doesn't have yet. Nothing is written.
     */
    data class NotFinished(val user: AuthUser) : SessionState

    /** Signed in, but the profile couldn't be read (offline, permission error). */
    data class LoadFailed(val user: AuthUser) : SessionState
}

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

    val state: StateFlow<SessionState> =
        combine(currentUser, retryTick) { user, _ -> user }
            .flatMapLatest { user ->
                if (user == null) flowOf<SessionState>(SessionState.SignedOut)
                else flow<SessionState> {
                    emit(SessionState.Loading)
                    emit(loadProfile(user))
                }
            }
            .stateIn(scope, SharingStarted.Eagerly, SessionState.Loading)

    fun retry() {
        retryTick.value++
    }

    fun signOut() = signOutAction()

    private suspend fun loadProfile(user: AuthUser): SessionState = try {
        when (val doc = fetchUser(user.uid)) {
            is UserDoc.Found ->
                if (doc.profile.isProfileComplete) SessionState.Ready(user, doc.profile)
                else SessionState.NotFinished(user)
            UserDoc.Missing, UserDoc.Unreadable -> SessionState.NotFinished(user)
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        SessionState.LoadFailed(user)
    }
}
