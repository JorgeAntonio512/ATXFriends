package com.georgeappdev.atxfriends.domain.export

import com.georgeappdev.atxfriends.data.model.UserProfile
import com.georgeappdev.atxfriends.domain.profile.displayName
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** One mutual match, with the other person's name (null if their profile couldn't be read). */
data class ExportMatch(val otherName: String?, val createdAt: Instant?)

data class ExportMessage(val matchID: String, val senderID: String, val receiverID: String, val text: String, val sentAt: Instant)

data class ExportPlan(
    val activityName: String?,
    val location: String?,
    val status: String?,
    val proposedDates: List<Instant>,
    val confirmedDate: Instant?,
)

/** A section's rows, or null when loading that section failed (iOS prints "Error loading …"). */
data class ExportInput(
    val uid: String,
    val profile: UserProfile,
    val matches: List<ExportMatch>?,
    val messages: List<ExportMessage>?,
    val plans: List<ExportPlan>?,
    /** Other users' display names by ID, for message threads. */
    val names: Map<String, String>,
)

/**
 * Builds the same plain-text export as iOS PrivacyAndSafetyViewModel.exportUserData, section by
 * section and line by line. One fix: iOS reads a `plans.activityName` key that no plan has, so
 * every iOS plan exports as "Unknown Activity"; Android uses the plan's real activity name.
 */
object DataExport {

    fun build(input: ExportInput, now: Instant, zone: ZoneId, locale: Locale = Locale.getDefault()): String {
        val fmt = formatter(zone, locale)
        val user = input.profile
        val out = StringBuilder()

        out.append("ATX FRIENDS DATA EXPORT\n")
        out.append("=======================\n")
        out.append("Export Date: ${fmt.format(now)}\n\n")

        out.append("PROFILE INFORMATION\n")
        out.append("-------------------\n")
        out.append("Name: ${user.displayName}\n")
        out.append("Account Created: ${fmt.format(user.createdAt)}\n\n")

        out.append("PREFERENCES\n")
        out.append("-----------\n")
        // Swift prints a Double with its decimal point, e.g. "10.0".
        out.append("Search Radius: ${user.radiusMiles} miles\n\n")

        out.append("ACTIVITIES\n")
        out.append("----------\n")
        if (user.activities.isEmpty()) out.append("No activities added\n\n")
        else {
            user.activities.forEach { out.append("- ${it.name}\n") }
            out.append("\n")
        }

        out.append("AVAILABILITY\n")
        out.append("------------\n")
        val combos = user.daySlotCombos.filter { it.isKnown }
        if (combos.isEmpty()) out.append("No availability set\n\n")
        else {
            combos.forEach { out.append("- ${it.displayName}\n") }
            out.append("\n")
        }

        out.append("PHOTOS\n")
        out.append("------\n")
        if (user.photoURLs.isEmpty()) out.append("No photos uploaded\n\n")
        else {
            user.photoURLs.forEachIndexed { i, url -> out.append("Photo ${i + 1}: $url\n") }
            out.append("\n")
        }

        out.append("MATCHES\n")
        out.append("-------\n")
        when {
            input.matches == null -> out.append("Error loading matches\n\n")
            input.matches.isEmpty() -> out.append("No mutual matches yet\n\n")
            else -> {
                // iOS skips a match whose other profile can't be fetched.
                input.matches.filter { it.otherName != null }.forEach { m ->
                    val date = m.createdAt?.let(fmt::format) ?: "Unknown date"
                    out.append("Match with ${m.otherName} on $date\n")
                }
                out.append("\n")
            }
        }

        out.append("MESSAGES\n")
        out.append("--------\n")
        when {
            input.messages == null -> out.append("Error loading messages\n\n")
            input.messages.isEmpty() -> out.append("No messages yet\n\n")
            else -> {
                input.messages.sortedBy { it.sentAt }.groupBy { it.matchID }.toSortedMap().forEach { (matchID, msgs) ->
                    val first = msgs.first()
                    val otherID = if (first.senderID == input.uid) first.receiverID else first.senderID
                    val otherName = input.names[otherID] ?: "Unknown User"
                    out.append("\nConversation with $otherName (Match ID: $matchID):\n")
                    msgs.forEach { msg ->
                        val sender = if (msg.senderID == input.uid) "You" else otherName
                        out.append("[${fmt.format(msg.sentAt)}] $sender: ${msg.text}\n")
                    }
                }
                out.append("\n")
            }
        }

        out.append("PLANS\n")
        out.append("-----\n")
        when {
            input.plans == null -> out.append("Error loading plans\n\n")
            input.plans.isEmpty() -> out.append("No plans yet\n\n")
            else -> input.plans.forEach { p ->
                out.append("Activity: ${p.activityName ?: "Unknown Activity"}\n")
                out.append("Proposed Dates: ${p.proposedDates.joinToString(", ") { fmt.format(it) }}\n")
                out.append("Location: ${p.location ?: "No location"}\n")
                out.append("Status: ${p.status ?: "unknown"}\n")
                out.append("Confirmed Date: ${p.confirmedDate?.let(fmt::format) ?: "Not confirmed"}\n\n")
            }
        }
        return out.toString()
    }

    /** Swift `Date.formatted()` in en-US: "9/25/2026, 3:04 PM". */
    private fun formatter(zone: ZoneId, locale: Locale): DateTimeFormatter =
        DateTimeFormatter.ofPattern("M/d/yyyy, h:mm a", locale).withZone(zone)
}
