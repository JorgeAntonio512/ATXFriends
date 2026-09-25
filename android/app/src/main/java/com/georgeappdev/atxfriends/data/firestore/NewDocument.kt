package com.georgeappdev.atxfriends.data.firestore

import com.georgeappdev.atxfriends.data.model.FirestoreEnum
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.GeoPoint
import com.google.firebase.firestore.Transaction
import com.google.firebase.firestore.WriteBatch
import kotlinx.coroutines.tasks.await
import java.time.Instant

/**
 * The fields of a brand-new document. Creating is the one way Android writes whole documents
 * (the `plans`, `messages`, `todayPlans`, `groupPlans`, `matches` and `showUpReports` iOS creates); existing
 * documents still change only through [DocumentUpdate].
 *
 * Same value rules as [DocumentUpdate]: Firestore primitives only, dates as Timestamps, and
 * UNKNOWN enum values refused. Nested maps (the embedded `activity`, a group plan's
 * `responses`) are themselves built as a NewDocument, so they follow the same rules.
 */
class NewDocument private constructor(val fields: Map<String, Any>) {

    class Builder {
        private val fields = linkedMapOf<String, Any>()

        fun put(field: String, value: String) = apply { fields[field] = value }
        fun put(field: String, value: Boolean) = apply { fields[field] = value }
        fun put(field: String, value: Double) = apply { fields[field] = value }
        fun put(field: String, value: Instant) = apply { fields[field] = DocReader.toTimestamp(value) }
        fun putStrings(field: String, value: List<String>) = apply { fields[field] = value.toList() }
        fun putInstants(field: String, value: List<Instant>) = apply { fields[field] = value.map(DocReader::toTimestamp) }
        fun putMap(field: String, value: NewDocument) = apply { fields[field] = value.fields }
        fun putBooleans(field: String, value: List<Boolean>) = apply { fields[field] = value.toList() }
        fun put(field: String, value: GeoPoint) = apply { fields[field] = value }

        /** iOS `FieldValue.serverTimestamp()`. */
        fun putServerTimestamp(field: String) = apply { fields[field] = FieldValue.serverTimestamp() }

        fun put(field: String, value: FirestoreEnum) = apply {
            fields[field] = requireNotNull(value.raw) { "Refusing to write an UNKNOWN value to '$field'" }
        }

        /** Sets [field] only when [value] is non-null — iOS's `if let v { data[k] = v }`. */
        fun putIfPresent(field: String, value: String?) = apply { if (value != null) put(field, value) }
        fun putIfPresent(field: String, value: Double?) = apply { if (value != null) put(field, value) }

        fun build(): NewDocument {
            require(fields.isNotEmpty()) { "A NewDocument must have at least one field" }
            return NewDocument(fields.toMap())
        }
    }
}

/** Creates [doc] under a new auto-ID and returns that ID once the server accepts it. */
suspend fun CollectionReference.addDocument(doc: NewDocument): String = add(doc.fields).await().id

/**
 * Adds "create [doc] at [ref]" to a batch. Only for a reference that can't already exist: a
 * fresh auto-ID (`collection.document()`), or a deterministic ID the caller has just looked
 * up and found missing. This and [Transaction.createDocument] are the only `set` calls in app
 * code (allow-listed by NoWholeDocumentWritesTest); on an existing document it would replace
 * every field.
 */
fun WriteBatch.createDocument(ref: DocumentReference, doc: NewDocument): WriteBatch = this.set(ref, doc.fields)

/**
 * Adds "create [doc] at [ref]" to a transaction that has just read [ref] and found it missing.
 * The transaction fails and retries if the doc appears in between, so this can never replace one.
 */
fun Transaction.createDocument(ref: DocumentReference, doc: NewDocument): Transaction = this.set(ref, doc.fields)
