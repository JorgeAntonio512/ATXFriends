package com.georgeappdev.atxfriends.ui.signup

import android.util.Log
import com.georgeappdev.atxfriends.data.firestore.NewDocument
import com.georgeappdev.atxfriends.data.repository.SignupWrites
import com.georgeappdev.atxfriends.domain.location.Coordinate
import com.georgeappdev.atxfriends.session.PendingSignup
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import java.time.Instant

private const val TAG = "ATXF"

/**
 * A signed-in account with no `users` doc (a new Google account, or one resumed after the app
 * was killed mid-gate). iOS AuthViewModel.createNewSSOUser / cancelNewSSOSignup.
 */
class PendingAccountOutcome(
    /** Read when used, so a name that arrives after the gate opened still counts. */
    private val pending: () -> PendingSignup,
    /** The signed-in uid now. Nothing is written for an account that's no longer signed in. */
    private val currentUid: () -> String?,
    private val createUserDoc: suspend (uid: String, doc: NewDocument) -> Boolean,
    private val abandonAccount: suspend () -> Unit,
    /** Re-route from what's now stored (SessionManager.pendingSignupResolved). */
    private val resolved: () -> Unit,
    private val clock: () -> Instant = Instant::now,
) : GateOutcome {

    /** Gate passed: create the doc (with the gate's coordinate), then on to profile setup. */
    override suspend fun passed(coordinate: Coordinate) {
        val p = pending()
        if (currentUid() != p.uid) {
            Log.w(TAG, "gate passed for ${p.uid}, which is no longer signed in — not creating a doc")
            return resolved()
        }
        Log.i(TAG, "[Onboarding] path=${p.path} step=createAccount gate=passed")
        createUserDoc(p.uid, SignupWrites.newUser(p.displayName, coordinate, clock()))
        resolved()
    }

    /** Backed out or waitlisted: delete the Auth account so nothing is left without a doc. */
    override suspend fun abandoned() {
        if (currentUid() == pending().uid) abandonAccount()
        resolved()
    }
}

/**
 * iOS AuthViewModel.signUp, reached only after the gate passed: create the Firebase Auth
 * account, then immediately its `users` doc (displayName "", the gate's coordinate). If the doc
 * can't be written the new Auth account is deleted again, so a failure never leaves an account
 * without a profile (iOS leaves it, and recovers on the next launch).
 */
class EmailAccountCreator(
    /** SessionManager.holdRoutingWhile: the half-made account must not route anywhere. */
    private val holdRouting: suspend (suspend () -> Unit) -> Unit,
    private val createAccount: suspend (email: String, password: String) -> String,
    private val createUserDoc: suspend (uid: String, doc: NewDocument) -> Boolean,
    private val abandonAccount: suspend () -> Unit,
    private val clock: () -> Instant = Instant::now,
) {
    /** Throws the first failure (Auth or Firestore); map it with AuthErrorMapper. */
    suspend fun create(email: String, password: String, coordinate: Coordinate) = holdRouting {
        // Once the Auth account exists, finish (or undo) it even if the screen goes away.
        withContext(NonCancellable) {
            Log.i(TAG, "[Onboarding] path=email step=signUp gate=passed")
            val uid = createAccount(email, password)
            try {
                createUserDoc(uid, SignupWrites.newUser("", coordinate, clock()))
            } catch (e: Exception) {
                Log.w(TAG, "sign-up: users/$uid write failed — deleting the new account", e)
                try {
                    abandonAccount()
                } catch (cleanup: Exception) {
                    if (cleanup is CancellationException) throw cleanup
                    // It resumes at the location gate on next launch (iOS orphan recovery).
                    Log.w(TAG, "sign-up: couldn't delete $uid", cleanup)
                }
                throw e
            }
        }
    }
}
