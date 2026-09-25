package com.georgeappdev.atxfriends

import android.app.Application
import android.content.Context
import com.georgeappdev.atxfriends.data.repository.AccountRepository
import com.georgeappdev.atxfriends.data.repository.ActivityRepository
import com.georgeappdev.atxfriends.data.repository.AuthRepository
import com.georgeappdev.atxfriends.data.repository.GroupPlanRepository
import com.georgeappdev.atxfriends.data.repository.MatchRepository
import com.georgeappdev.atxfriends.data.repository.MessageRepository
import com.georgeappdev.atxfriends.data.repository.PhotoStorage
import com.georgeappdev.atxfriends.data.repository.PlanRepository
import com.georgeappdev.atxfriends.data.repository.PrivacyRepository
import com.georgeappdev.atxfriends.data.repository.ProfileRepository
import com.georgeappdev.atxfriends.data.repository.ShowUpRepository
import com.georgeappdev.atxfriends.data.repository.SimpaticoRepository
import com.georgeappdev.atxfriends.data.repository.TodayPlanRepository
import com.georgeappdev.atxfriends.data.repository.UserRepository
import com.georgeappdev.atxfriends.location.DeviceLocation
import com.georgeappdev.atxfriends.location.LocationSharing
import com.georgeappdev.atxfriends.location.withLocationSharing
import com.georgeappdev.atxfriends.navigation.ThreadRequests
import com.georgeappdev.atxfriends.session.SessionManager
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
    val session = SessionManager(auth, users, appScope)
    val threadRequests = ThreadRequests()

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

    /** Each time the app comes to the foreground (iOS scenePhase → .active). */
    fun onAppForeground() {
        appScope.launch { locationSharing.onAppForeground() }
    }
}
