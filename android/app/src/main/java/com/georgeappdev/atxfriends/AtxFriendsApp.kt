package com.georgeappdev.atxfriends

import android.app.Application
import android.content.Context
import com.georgeappdev.atxfriends.data.places.GooglePlaceSearch
import com.georgeappdev.atxfriends.data.repository.AccountRepository
import com.georgeappdev.atxfriends.data.repository.ActivityRepository
import com.georgeappdev.atxfriends.data.repository.AppleAuthRepository
import com.georgeappdev.atxfriends.data.repository.AuthRepository
import com.georgeappdev.atxfriends.data.repository.GroupPlanRepository
import com.georgeappdev.atxfriends.data.repository.MatchRepository
import com.georgeappdev.atxfriends.data.repository.MessageRepository
import com.georgeappdev.atxfriends.data.repository.PhotoStorage
import com.georgeappdev.atxfriends.data.repository.PlanRepository
import com.georgeappdev.atxfriends.data.repository.PrivacyRepository
import com.georgeappdev.atxfriends.data.repository.ProfileRepository
import com.georgeappdev.atxfriends.data.repository.ShowUpRepository
import com.georgeappdev.atxfriends.data.repository.SignupRepository
import com.georgeappdev.atxfriends.data.repository.SimpaticoRepository
import com.georgeappdev.atxfriends.data.repository.TodayPlanRepository
import com.georgeappdev.atxfriends.data.repository.UserRepository
import com.georgeappdev.atxfriends.location.DeviceLocation
import com.georgeappdev.atxfriends.location.FusedGateLocation
import com.georgeappdev.atxfriends.location.LocationPermissionMonitor
import com.georgeappdev.atxfriends.location.LocationSharing
import com.georgeappdev.atxfriends.location.withLocationSharing
import com.georgeappdev.atxfriends.navigation.ThreadRequests
import com.georgeappdev.atxfriends.push.FirebaseDeviceToken
import com.georgeappdev.atxfriends.push.FirestoreTokenStore
import com.georgeappdev.atxfriends.push.PrefsPendingTokenDelete
import com.georgeappdev.atxfriends.push.PushChannels
import com.georgeappdev.atxfriends.push.PushRoutes
import com.georgeappdev.atxfriends.push.PushTokens
import com.georgeappdev.atxfriends.session.SessionManager
import com.georgeappdev.atxfriends.ui.auth.AppleSignInHandler
import com.georgeappdev.atxfriends.ui.signup.EmailAccountCreator
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class AtxFriendsApp : Application() {
    val container: AppContainer by lazy { AppContainer(this) }

    override fun onCreate() {
        super.onCreate()
        // Channels must exist before the first push arrives with the app closed.
        PushChannels.create(this)
        container.startPush()
        container.recoverAppleSignIn()
    }
}

/** App-wide singletons. Every Firebase call goes through these repositories. */
class AppContainer(context: Context) {
    val appContext: Context = context.applicationContext
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val firestore = FirebaseFirestore.getInstance()
    private val firebaseAuth = FirebaseAuth.getInstance()

    val auth = AuthRepository(firebaseAuth)
    val users = UserRepository(firestore)
    val matches = MatchRepository(firestore)
    val messages = MessageRepository(firestore)
    val plans = PlanRepository(firestore)
    val simpatico = SimpaticoRepository(firestore)
    val todayPlans = TodayPlanRepository(firestore)
    val showUps = ShowUpRepository(firestore)
    val groupPlans = GroupPlanRepository(firestore)
    val activities = ActivityRepository(firestore)
    val session = SessionManager(auth.currentUser, users::fetchUser, ::signOut, appScope)
    val threadRequests = ThreadRequests()

    // The plan composer's "Where?" search. Free text only when no Places key is configured.
    val placeSearch by lazy { GooglePlaceSearch.create(appContext, BuildConfig.PLACES_API_KEY) }

    // Settings tab.
    val profiles = ProfileRepository(firestore)
    val photos = PhotoStorage(FirebaseStorage.getInstance())
    val account = AccountRepository(FirebaseFunctions.getInstance(AccountRepository.REGION))
    val privacy = PrivacyRepository(firestore)
    val deviceLocation = DeviceLocation(appContext)
    val locationSharing = LocationSharing(
        currentUid = { firebaseAuth.currentUser?.uid },
        fetchUser = users::fetchUser,
        writer = profiles,
        location = deviceLocation,
        onWritten = { uid, mode, fix, at ->
            session.currentProfile?.takeIf { it.id == uid }
                ?.let { session.profileChanged(it.withLocationSharing(mode, fix, at)) }
        },
    )

    // Sign-up: the location gate, account creation, the waitlist and Google sign-in.
    val signup = SignupRepository(firebaseAuth, firestore)
    val gateLocation = FusedGateLocation(appContext, deviceLocation)
    val locationPermission = LocationPermissionMonitor(appContext)
    val emailAccounts = EmailAccountCreator(
        holdRouting = { block -> session.holdRoutingWhile(block) },
        createAccount = signup::createEmailAccount,
        createUserDoc = signup::createUserDocIfMissing,
        abandonAccount = signup::abandonPendingAccount,
    )

    // Sign in with Apple (Firebase's web flow; same two-phase new-user handling as Google).
    val appleAuth = AppleAuthRepository(firebaseAuth)
    val appleSignIn = AppleSignInHandler(session::startPendingSignup, appleAuth::saveNameIfMissing)

    // Push notifications.
    val pushTokens = PushTokens(FirestoreTokenStore(firestore), FirebaseDeviceToken(), PrefsPendingTokenDelete(appContext))
    val pushRoutes = PushRoutes()
    private var signingOut = false

    /** Registers this device's token whenever someone is signed in; forgets taps on sign-out. */
    fun startPush() {
        appScope.launch {
            auth.currentUser.collect { user ->
                if (user != null) pushTokens.onSignedIn(user.uid) else pushRoutes.clear()
            }
        }
    }

    /** An Apple sign-in that finished while the app's activity was gone (see pendingSignIn). */
    fun recoverAppleSignIn() {
        appScope.launch { appleAuth.pendingSignIn()?.let { appleSignIn.signedIn(it) } }
    }

    /** A refreshed FCM token (from the messaging service). */
    fun onNewPushToken(token: String) {
        appScope.launch { pushTokens.onNewToken(firebaseAuth.currentUser?.uid, token) }
    }

    /**
     * Every sign-out (and the one after account deletion): take this device's token off the
     * account first, while still signed in, then sign out of Firebase.
     */
    private fun signOut() {
        val uid = firebaseAuth.currentUser?.uid
        if (uid == null) {
            auth.signOut()
            return
        }
        if (signingOut) return
        signingOut = true
        appScope.launch {
            try {
                pushTokens.beforeSignOut(uid)
            } finally {
                auth.signOut()
                signingOut = false
            }
        }
    }

    /** Each time the app comes to the foreground (iOS scenePhase → .active). */
    fun onAppForeground() {
        appScope.launch { locationSharing.onAppForeground() }
    }
}
