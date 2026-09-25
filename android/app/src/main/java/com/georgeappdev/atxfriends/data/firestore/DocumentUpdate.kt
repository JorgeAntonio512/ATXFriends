package com.georgeappdev.atxfriends.data.firestore

import com.georgeappdev.atxfriends.data.model.FirestoreEnum
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.GeoPoint
import kotlinx.coroutines.tasks.await
import java.time.Instant

/**
 * THE way Android changes an existing document: a list of individual fields, applied with
 * `DocumentReference.update()`, which leaves every unlisted field exactly as it was.
 *
 * iOS and Android share one database. Replacing a whole document (`set()` without merge, or
 * `set()` of a model converted to a map) would silently delete every field iOS uses that
 * Android doesn't know about. Models therefore have no "to map" method, repositories expose
 * no "save object" call, and `NoWholeDocumentWritesTest` fails the build if `.set(` appears.
 *
 * Values are limited to Firestore primitives, and dates are always written as Timestamps
 * (the same form iOS writes). UNKNOWN enum values can't be written.
 */
class DocumentUpdate private constructor(val fields: Map<String, Any?>) {

    class Builder {
        private val fields = linkedMapOf<String, Any?>()

        fun put(field: String, value: String) = apply { fields[field] = value }
        fun put(field: String, value: Boolean) = apply { fields[field] = value }
        fun put(field: String, value: Double) = apply { fields[field] = value }
        fun put(field: String, value: Long) = apply { fields[field] = value }
        fun put(field: String, value: Instant) = apply { fields[field] = DocReader.toTimestamp(value) }
        fun putStrings(field: String, value: List<String>) = apply { fields[field] = value.toList() }
        fun putInstants(field: String, value: List<Instant>) = apply { fields[field] = value.map(DocReader::toTimestamp) }
        fun putBooleans(field: String, value: List<Boolean>) = apply { fields[field] = value.toList() }
        fun put(field: String, value: GeoPoint) = apply { fields[field] = value }

        /** Adds [value] to an array field unless already present (iOS `FieldValue.arrayUnion`). */
        fun arrayUnion(field: String, value: String) = apply { fields[field] = FieldValue.arrayUnion(value) }

        /** Removes every copy of [value] from an array field (iOS `FieldValue.arrayRemove`). */
        fun arrayRemove(field: String, value: String) = apply { fields[field] = FieldValue.arrayRemove(value) }

        fun put(field: String, value: FirestoreEnum) = apply {
            fields[field] = requireNotNull(value.raw) { "Refusing to write an UNKNOWN value to '$field'" }
        }

        /** Removes one field (iOS `FieldValue.delete()`). */
        fun delete(field: String) = apply { fields[field] = FieldValue.delete() }

        fun build(): DocumentUpdate {
            require(fields.isNotEmpty()) { "A DocumentUpdate must change at least one field" }
            return DocumentUpdate(fields.toMap())
        }
    }
}

/** Applies [update] field-by-field. Fails (rather than creating a doc) if the document doesn't exist. */
suspend fun DocumentReference.applyUpdate(update: DocumentUpdate) {
    update(update.fields).await()
}
