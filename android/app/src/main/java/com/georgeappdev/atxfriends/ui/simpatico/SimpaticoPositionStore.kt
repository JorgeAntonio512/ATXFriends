package com.georgeappdev.atxfriends.ui.simpatico

import android.content.Context
import androidx.core.content.edit

/**
 * Where the user is in the question flow, kept on the device because Firestore has no record
 * of skipped questions (iOS UserDefaults `simpaticoV2Position.{uid}`, parity spec §8).
 */
interface SimpaticoPositionStore {
    /** 0 when nothing is saved, like iOS `UserDefaults.integer(forKey:)`. */
    fun get(uid: String): Int
    fun save(uid: String, index: Int)
    fun clear(uid: String)
}

class SharedPrefsSimpaticoPositionStore(context: Context) : SimpaticoPositionStore {
    private val prefs = context.getSharedPreferences("simpatico", Context.MODE_PRIVATE)

    override fun get(uid: String) = prefs.getInt(key(uid), 0)
    override fun save(uid: String, index: Int) = prefs.edit { putInt(key(uid), index) }
    override fun clear(uid: String) = prefs.edit { remove(key(uid)) }

    private fun key(uid: String) = "simpaticoV2Position.$uid"
}
