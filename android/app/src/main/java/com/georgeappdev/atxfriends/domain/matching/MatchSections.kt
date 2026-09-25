package com.georgeappdev.atxfriends.domain.matching

import com.georgeappdev.atxfriends.data.model.Match
import com.georgeappdev.atxfriends.data.model.UserProfile

/** A match plus the other person's current profile. */
data class MatchEntry(val match: Match, val other: UserProfile)

data class MatchSections(
    /** "Pending Matches": I haven't decided, and nobody said Nay. */
    val pending: List<Match>,
    /** "Connected": both said Yay. */
    val connected: List<Match>,
)

/**
 * Port of iOS MatchesViewModel.loadMatches' filtering plus MatchesView's list building.
 * Order is the fetch order (matches where I'm user1, then where I'm user2) — iOS never sorts.
 */
object MatchSectioning {

    fun otherUserID(match: Match, myID: String): String? = when (myID) {
        match.user1ID -> match.user2ID
        match.user2ID -> match.user1ID
        else -> null
    }

    fun decision(match: Match, userID: String): Boolean? = when (userID) {
        match.user1ID -> match.user1Decision
        match.user2ID -> match.user2Decision
        else -> null
    }

    fun isPending(match: Match, userID: String): Boolean = decision(match, userID) == null

    fun isRejected(match: Match): Boolean = match.user1Decision == false || match.user2Decision == false

    /** Drops matches that aren't mine and matches with people I've blocked. */
    fun withoutBlocked(matches: List<Match>, myID: String, me: UserProfile?): List<Match> {
        val blocked = me?.blockedUsers.orEmpty().toSet()
        return matches.filter { match -> otherUserID(match, myID)?.let { it !in blocked } ?: false }
    }

    /** Categorizes matches the way iOS loadMatches does. Run on every full load. */
    fun sections(
        matches: List<Match>,
        myID: String,
        me: UserProfile?,
        profiles: Map<String, UserProfile>,
    ): MatchSections {
        val visible = withoutBlocked(matches, myID, me)

        // Display filter: hide (never delete) matches that no longer overlap under both
        // people's current profiles. If a profile isn't loaded, don't hide.
        val live = visible.filter { match ->
            val other = otherUserID(match, myID)?.let(profiles::get)
            if (other == null || me == null) true else MatchingRules.shouldMatch(me, other)
        }

        return MatchSections(
            pending = live.filter { isPending(it, myID) && !isRejected(it) },
            connected = live.filter { it.isMutualMatch },
        )
    }

    /** Pairs each match with the other person's profile; the view only shows matches whose profile loaded. */
    fun entries(matches: List<Match>, myID: String, profiles: Map<String, UserProfile>): List<MatchEntry> =
        matches.mapNotNull { match ->
            otherUserID(match, myID)?.let(profiles::get)?.let { MatchEntry(match, it) }
        }
}

/** iOS `FirebaseUser.showUpMeter`: "New", or e.g. "80% (4/5)" (percentage truncated, not rounded). */
fun showUpMeter(thumbsUp: Int, total: Int): String {
    if (total <= 0) return "New"
    val percent = (thumbsUp.toDouble() / total.toDouble() * 100).toInt()
    return "$percent% ($thumbsUp/$total)"
}
