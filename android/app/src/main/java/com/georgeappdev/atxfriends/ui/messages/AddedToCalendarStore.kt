package com.georgeappdev.atxfriends.ui.messages

import android.content.Context

/**
 * Which plans this device has already sent to the calendar (iOS AddedToCalendarStore). Local
 * only and never synced, as on iOS. iOS keeps one flag per provider (Apple / Google / Outlook);
 * Android hands the event to the device's calendar app, which covers every account on the
 * phone, so there's one flag per plan.
 */
interface AddedToCalendarStore {
    fun isAdded(planID: String): Boolean
    fun markAdded(planID: String)
    /** Forgets the given plans (for account deletion, as iOS does). */
    fun clear(planIDs: Collection<String>)
}

class SharedPrefsAddedToCalendarStore(context: Context) : AddedToCalendarStore {
    private val prefs = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    override fun isAdded(planID: String) = prefs.getBoolean(key(planID), false)

    override fun markAdded(planID: String) {
        prefs.edit().putBoolean(key(planID), true).apply()
    }

    override fun clear(planIDs: Collection<String>) {
        prefs.edit().apply { planIDs.forEach { remove(key(it)) } }.apply()
    }

    private companion object {
        const val FILE = "added_to_calendar"

        /** iOS's `addedToCalendar.{provider}.{planID}`, with "device" as Android's one provider. */
        fun key(planID: String) = "addedToCalendar.device.$planID"
    }
}
