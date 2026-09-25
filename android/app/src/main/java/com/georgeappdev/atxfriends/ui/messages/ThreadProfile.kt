package com.georgeappdev.atxfriends.ui.messages

import com.georgeappdev.atxfriends.data.model.SimpaticoState
import com.georgeappdev.atxfriends.data.model.UserProfile
import com.georgeappdev.atxfriends.data.repository.UserDoc
import com.georgeappdev.atxfriends.domain.distance.DistanceDisplay
import com.georgeappdev.atxfriends.domain.matching.showUpMeter
import com.georgeappdev.atxfriends.domain.messages.MessageThread
import com.georgeappdev.atxfriends.domain.simpatico.SimpaticoScoring
import com.georgeappdev.atxfriends.ui.matches.MatchUi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/**
 * What the thread's avatar opens: the same Match Detail sheet as the Matches tab (iOS
 * MatchDetailView with the thread's match and other user). A thread is always a mutual match,
 * so the sheet shows "You're connected" rather than Yay / Nay.
 */
object ThreadProfile {

    /** What the thread already knows, so the sheet can open at once while the rest loads. */
    fun basic(thread: MessageThread): MatchUi = MatchUi(
        matchID = thread.id,
        name = thread.otherUserName,
        photoURLs = listOfNotNull(thread.otherUserPhotoURL),
        distanceText = null,
        sharedActivities = thread.match.overlappingActivityNames,
        sharedCategory = thread.match.overlappingCategoryNames.firstOrNull(),
        sharedTimes = thread.match.overlappingDaySlots,
        showUpMeter = showUpMeter(0, 0),
        simpaticoScore = null,
        isPending = false,
        isMutual = true,
    )

    /** The full profile: every photo, bucketed distance, show-up meter, and Simpatico score. */
    fun full(thread: MessageThread, me: UserProfile?, other: UserProfile, simpaticoScore: Int?): MatchUi =
        basic(thread).copy(
            name = other.displayName,
            photoURLs = other.photoURLs,
            distanceText = me?.let { DistanceDisplay.between(it, other) },
            showUpMeter = showUpMeter(other.showUpThumbsUp, other.showUpTotal),
            simpaticoScore = simpaticoScore,
        )
}

/**
 * Loads [ThreadProfile.full] with the same reads the Matches tab makes. The Simpatico score is
 * best-effort (no badge if either side's answers can't be read); null if the profile can't be read.
 */
class ThreadProfileLoader(
    private val myID: String,
    private val myProfile: UserProfile?,
    private val fetchUser: suspend (uid: String) -> UserDoc,
    private val fetchSimpatico: suspend (uid: String) -> SimpaticoState,
) {
    suspend fun load(thread: MessageThread): MatchUi? = coroutineScope {
        val score = async {
            attempt {
                // Its own scope, so a failed read is caught here instead of failing the whole load.
                coroutineScope {
                    val mine = async { fetchSimpatico(myID) }
                    val theirs = async { fetchSimpatico(thread.otherUserID) }
                    SimpaticoScoring.score(mine.await(), theirs.await())
                }
            }
        }
        val other = (attempt { fetchUser(thread.otherUserID) } as? UserDoc.Found)?.profile
        other?.let { ThreadProfile.full(thread, myProfile, it, score.await()) }.also { if (it == null) score.cancel() }
    }

    private suspend fun <T> attempt(block: suspend () -> T): T? = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }
}
