package com.georgeappdev.atxfriends.data.firestore

import com.google.firebase.Timestamp
import java.time.Instant
import java.util.Date

/**
 * Type-safe, crash-proof access to raw Firestore document data. Every getter returns null
 * when the field is missing, null, or of an unexpected type — it never throws. Models build
 * on this so old or odd production documents can't crash the app.
 *
 * Mirrors how the iOS decoders cast (`data["x"] as? T`), including Swift's NSNumber
 * bridging: any Firestore number reads as a Double, and a whole-number value reads as an Int.
 */
class DocReader(private val data: Map<String, Any?>) {

    fun has(key: String): Boolean = data[key] != null

    fun string(key: String): String? = data[key] as? String

    fun bool(key: String): Boolean? = data[key] as? Boolean

    fun double(key: String): Double? = (data[key] as? Number)?.toDouble()

    fun int(key: String): Int? = when (val v = data[key]) {
        is Long -> v.toInt()
        is Int -> v
        is Double -> if (v % 1.0 == 0.0) v.toInt() else null
        else -> null
    }

    /** Reads a Firestore Timestamp (what iOS writes via `Timestamp(date:)`). */
    fun instant(key: String): Instant? = toInstant(data[key])

    /** An array where every element is a String; null if any element isn't (like Swift `as? [String]`). */
    fun stringList(key: String): List<String>? = typedList(key)

    fun boolList(key: String): List<Boolean>? = typedList(key)

    /** An array of Timestamps; null if any element isn't one (like Swift `as? [Timestamp]`). */
    fun instantList(key: String): List<Instant>? {
        val list = data[key] as? List<*> ?: return null
        return list.map { toInstant(it) ?: return null }
    }

    /** A nested map with String keys, or null. */
    fun map(key: String): Map<String, Any?>? {
        val m = data[key] as? Map<*, *> ?: return null
        if (m.keys.any { it !is String }) return null
        @Suppress("UNCHECKED_CAST")
        return m as Map<String, Any?>
    }

    fun reader(key: String): DocReader? = map(key)?.let(::DocReader)

    private inline fun <reified T> typedList(key: String): List<T>? {
        val list = data[key] as? List<*> ?: return null
        return list.map { it as? T ?: return null }
    }

    companion object {
        fun toInstant(value: Any?): Instant? = when (value) {
            is Timestamp -> Instant.ofEpochSecond(value.seconds, value.nanoseconds.toLong())
            is Date -> value.toInstant()
            else -> null
        }

        /** How every date is written: a Firestore Timestamp, same as iOS `Timestamp(date:)`. */
        fun toTimestamp(instant: Instant): Timestamp = Timestamp(instant.epochSecond, instant.nano)
    }
}
