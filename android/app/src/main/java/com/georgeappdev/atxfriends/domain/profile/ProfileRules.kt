package com.georgeappdev.atxfriends.domain.profile

import com.georgeappdev.atxfriends.data.model.DayOfWeek
import com.georgeappdev.atxfriends.data.model.DaySlotCombo
import com.georgeappdev.atxfriends.data.model.ProfileActivity
import com.georgeappdev.atxfriends.data.model.TimeSlot
import java.text.BreakIterator

/** Display name rule from EditProfileView / NameInputView: 2–30 characters once trimmed. */
object NameRules {
    const val MIN = 2
    const val MAX = 30

    fun trimmed(name: String): String = name.trim()

    fun isValid(name: String): Boolean = characterCount(trimmed(name)) in MIN..MAX

    /** Counts what people see as characters (Swift `String.count`), so an emoji counts once. */
    fun characterCount(text: String): Int {
        val it = BreakIterator.getCharacterInstance().apply { setText(text) }
        var count = 0
        while (it.next() != BreakIterator.DONE) count++
        return count
    }
}

/**
 * The activity picker rules from ProfileViewModel: 3 "Main" plus up to 7 "Extra" (3–10 total,
 * exactly 3 Main). The first 3 picks become Main; removing a Main promotes the first Extra;
 * starring an Extra swaps it with the last Main.
 */
object ActivitySelection {
    const val MAIN_COUNT = 3
    const val MIN_TOTAL = 3
    const val MAX_TOTAL = 10
    const val MAX_EXTRAS = MAX_TOTAL - MAIN_COUNT

    fun mainCount(list: List<ProfileActivity>) = list.count { it.isPrimary }

    fun isValid(list: List<ProfileActivity>): Boolean =
        list.size in MIN_TOTAL..MAX_TOTAL && mainCount(list) == MAIN_COUNT

    fun canAddMore(list: List<ProfileActivity>) = list.size < MAX_TOTAL

    /** `selectActivity`: ignored when full or already picked. */
    fun select(list: List<ProfileActivity>, id: String, name: String): List<ProfileActivity> {
        if (!canAddMore(list) || list.any { it.id == id }) return list
        return list + ProfileActivity(id, name, isPrimary = mainCount(list) < MAIN_COUNT)
    }

    /** `deselectActivity`: removes it, then promotes the first Extra if Mains dropped below 3. */
    fun deselect(list: List<ProfileActivity>, id: String): List<ProfileActivity> {
        val remaining = list.filterNot { it.id == id }.toMutableList()
        if (mainCount(remaining) < MAIN_COUNT) {
            val promote = remaining.indexOfFirst { !it.isPrimary }
            if (promote >= 0) remaining[promote] = remaining[promote].copy(isPrimary = true)
        }
        return remaining
    }

    /** `toggleMain`: an Extra becomes Main and the last-ordered Main becomes Extra. */
    fun toggleMain(list: List<ProfileActivity>, id: String): List<ProfileActivity> {
        val index = list.indexOfFirst { it.id == id }
        if (index < 0 || list[index].isPrimary) return list
        val result = list.toMutableList()
        val lastMain = result.indexOfLast { it.isPrimary }
        if (lastMain >= 0) result[lastMain] = result[lastMain].copy(isPrimary = false)
        result[index] = result[index].copy(isPrimary = true)
        return result
    }

    /**
     * Trims and collapses whitespace runs to one space, keeping the typed casing
     * (addCustomActivity). Any Unicode whitespace counts (tabs, newlines, no-break spaces), like
     * Swift's `.whitespacesAndNewlines`. Written without a regex: Android's regex engine rejects
     * the `(?U)` flag desktop Java needs for Unicode `\s`, which crashed the Activities screen.
     */
    fun cleanedName(name: String): String = buildString {
        var pendingSpace = false
        for (c in name.trim()) {
            if (c.isWhitespace()) {
                pendingSpace = true
            } else {
                if (pendingSpace) append(' ')
                pendingSpace = false
                append(c)
            }
        }
    }

    /** `Activity.normalizedForComparison`: cleaned and lowercased. */
    fun normalizedForComparison(name: String): String = cleanedName(name).lowercase()

    /** Catalog rows shown under "Available Activities": not yet picked, matching the search. */
    fun <T> available(catalog: List<T>, selected: List<ProfileActivity>, search: String, id: (T) -> String, name: (T) -> String): List<T> {
        val picked = selected.map { it.id }.toSet()
        return catalog.filter { id(it) !in picked && (search.isEmpty() || name(it).contains(search, ignoreCase = true)) }
    }

    /** Whether some catalog name equals the trimmed search, ignoring case (hides "Add '…'"). */
    fun <T> hasExactMatch(catalog: List<T>, search: String, name: (T) -> String): Boolean {
        val trimmed = search.trim()
        return trimmed.isNotEmpty() && catalog.any { name(it).equals(trimmed, ignoreCase = true) }
    }

}

/** Day/slot rules from AvailabilitySettingsView: at least 3 combos, no maximum. */
object AvailabilityRules {
    const val MIN = 3

    fun combo(day: DayOfWeek, slot: TimeSlot) = DaySlotCombo("${day.raw}_${slot.raw}")

    /** Only combos Android understands count toward the minimum (iOS drops the rest on read). */
    fun count(list: List<DaySlotCombo>) = list.count { it.isKnown }

    fun isValid(list: List<DaySlotCombo>) = count(list) >= MIN

    fun isSelected(list: List<DaySlotCombo>, day: DayOfWeek, slot: TimeSlot) = combo(day, slot) in list

    /** Adds the combo at the end (tap order, as iOS appends) or removes it if already picked. */
    fun toggle(list: List<DaySlotCombo>, day: DayOfWeek, slot: TimeSlot): List<DaySlotCombo> {
        val combo = combo(day, slot)
        return if (combo in list) list - combo else list + combo
    }

    val days = DayOfWeek.entries.filter { it != DayOfWeek.UNKNOWN }
    val slots = TimeSlot.entries.filter { it != TimeSlot.UNKNOWN }
}

/** TimeSlot.swift display helpers. */
val TimeSlot.icon: String
    get() = when (this) {
        TimeSlot.WAKE_UP -> "☀️"
        TimeSlot.AFTERNOON -> "🌤"
        TimeSlot.EVENING -> "🌆"
        TimeSlot.NIGHT -> "🌙"
        TimeSlot.OWL_HOURS -> "🦉"
        TimeSlot.UNKNOWN -> ""
    }

val TimeSlot.timeRange: String
    get() = when (this) {
        TimeSlot.WAKE_UP -> "7:00am – 12:00pm"
        TimeSlot.AFTERNOON -> "12:00pm – 5:00pm"
        TimeSlot.EVENING -> "5:00pm – 9:00pm"
        TimeSlot.NIGHT -> "9:00pm – 2:00am"
        TimeSlot.OWL_HOURS -> "2:00am – 7:00am"
        TimeSlot.UNKNOWN -> ""
    }

/** DaySlotCombo.displayName: "Monday Night" (raw values joined by a space). */
val DaySlotCombo.displayName: String
    get() = "${day.raw} ${slot.raw}"

/** Search radius slider (SearchRadiusSettingsView): 5–25 miles in 1-mile steps. */
object RadiusRules {
    const val MIN = 5
    const val MAX = 25

    fun clampToSlider(miles: Double): Int = miles.toInt().coerceIn(MIN, MAX)
}
