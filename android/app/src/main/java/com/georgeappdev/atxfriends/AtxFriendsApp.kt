package com.georgeappdev.atxfriends

import android.app.Application
import com.georgeappdev.atxfriends.data.repository.AuthRepository
import com.georgeappdev.atxfriends.data.repository.MatchRepository
import com.georgeappdev.atxfriends.data.repository.MessageRepository
import com.georgeappdev.atxfriends.data.repository.PlanRepository
import com.georgeappdev.atxfriends.data.repository.SimpaticoRepository
import com.georgeappdev.atxfriends.data.repository.TodayPlanRepository
import com.georgeappdev.atxfriends.data.repository.UserRepository
import com.georgeappdev.atxfriends.session.SessionManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class AtxFriendsApp : Application() {
    val container: AppContainer by lazy { AppContainer() }
}

/** App-wide singletons. Every Firebase call goes through these repositories. */
class AppContainer {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val firestore = FirebaseFirestore.getInstance()

    val auth = AuthRepository(FirebaseAuth.getInstance())
    val users = UserRepository(firestore)
    val matches = MatchRepository(firestore)
    val messages = MessageRepository(firestore)
    val plans = PlanRepository(firestore)
    val simpatico = SimpaticoRepository(firestore)
    val todayPlans = TodayPlanRepository(firestore)
    val session = SessionManager(auth, users, appScope)
}
