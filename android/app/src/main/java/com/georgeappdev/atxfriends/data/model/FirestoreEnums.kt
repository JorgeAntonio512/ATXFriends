package com.georgeappdev.atxfriends.data.model

/**
 * A string enum stored in Firestore. [raw] is the exact string iOS writes; the UNKNOWN case
 * has `raw == null` and is what any unrecognized value (e.g. a status added by a newer iOS
 * build) decodes to, so reading never fails. UNKNOWN can never be written back.
 */
interface FirestoreEnum {
    val raw: String?
}

inline fun <reified E> firestoreEnumOf(raw: String?, unknown: E): E
    where E : Enum<E>, E : FirestoreEnum =
    enumValues<E>().firstOrNull { it.raw != null && it.raw == raw } ?: unknown

/** Raw values: spec §3 / Plan.swift. Note iOS's `counterProposed` case is stored as "counter". */
enum class PlanStatus(override val raw: String?) : FirestoreEnum {
    PENDING("pending"),
    COUNTER_PROPOSED("counter"),
    CONFIRMED("confirmed"),
    DECLINED("declined"),
    CANCELLED("cancelled"),
    UNKNOWN(null);

    companion object {
        fun fromRaw(raw: String?) = firestoreEnumOf(raw, UNKNOWN)
    }
}

enum class MessageKind(override val raw: String?) : FirestoreEnum {
    TEXT("text"),
    PLAN_PROPOSAL("planProposal"),
    UNKNOWN(null);

    companion object {
        /** A missing field means a legacy plain-text message (iOS defaults it the same way). */
        fun fromRaw(raw: String?) = if (raw == null) TEXT else firestoreEnumOf(raw, UNKNOWN)
    }
}

enum class TodayPlanStatus(override val raw: String?) : FirestoreEnum {
    OPEN("open"),
    CLAIMED("claimed"),
    UNKNOWN(null);

    companion object {
        fun fromRaw(raw: String?) = firestoreEnumOf(raw, UNKNOWN)
    }
}

enum class GroupPlanResponse(override val raw: String?) : FirestoreEnum {
    INVITED("invited"),
    GOING("going"),
    CANT_MAKE("cantMake"),
    UNKNOWN(null);

    companion object {
        fun fromRaw(raw: String?) = firestoreEnumOf(raw, UNKNOWN)
    }
}

enum class GroupPlanStatus(override val raw: String?) : FirestoreEnum {
    ACTIVE("active"),
    CANCELLED("cancelled"),
    UNKNOWN(null);

    companion object {
        fun fromRaw(raw: String?) = firestoreEnumOf(raw, UNKNOWN)
    }
}

enum class LocationSharingMode(override val raw: String?) : FirestoreEnum {
    OFF("off"),
    ONCE("once"),
    ON_OPEN("onOpen"),
    UNKNOWN(null);

    /** Unknown modes count as not sharing, like iOS `isSharingLocation`. */
    val isSharing: Boolean get() = this == ONCE || this == ON_OPEN

    companion object {
        /** Missing means "off" — every account that predates the setting. */
        fun fromRaw(raw: String?) = if (raw == null) OFF else firestoreEnumOf(raw, UNKNOWN)
    }
}

enum class SimpaticoImportance(override val raw: String?, val weight: Int) : FirestoreEnum {
    LITTLE("little", 1),
    SOMEWHAT("somewhat", 10),
    VERY("very", 50),
    UNKNOWN(null, 0);

    companion object {
        fun fromRaw(raw: String?) = firestoreEnumOf(raw, UNKNOWN)
    }
}

/** Raw value is the display string itself (DayOfWeek.swift). */
enum class DayOfWeek(override val raw: String?) : FirestoreEnum {
    MONDAY("Monday"),
    TUESDAY("Tuesday"),
    WEDNESDAY("Wednesday"),
    THURSDAY("Thursday"),
    FRIDAY("Friday"),
    SATURDAY("Saturday"),
    SUNDAY("Sunday"),
    UNKNOWN(null);

    companion object {
        fun fromRaw(raw: String?) = firestoreEnumOf(raw, UNKNOWN)
    }
}

/** Raw values keep their internal space, e.g. "Wake Up" (TimeSlot.swift). */
enum class TimeSlot(override val raw: String?) : FirestoreEnum {
    WAKE_UP("Wake Up"),
    AFTERNOON("Afternoon"),
    EVENING("Evening"),
    NIGHT("Night"),
    OWL_HOURS("Owl Hours"),
    UNKNOWN(null);

    companion object {
        fun fromRaw(raw: String?) = firestoreEnumOf(raw, UNKNOWN)
    }
}

/** iOS `ReportReason`: the raw value is also the text iOS shows. */
enum class ReportReason(override val raw: String?) : FirestoreEnum {
    INAPPROPRIATE_BEHAVIOR("Inappropriate behavior"),
    HARASSMENT("Harassment"),
    FAKE_PROFILE("Fake profile"),
    SPAM("Spam"),
    OTHER("Other"),
    UNKNOWN(null);

    companion object {
        fun fromRaw(raw: String?) = firestoreEnumOf(raw, UNKNOWN)

        /** The choices, in iOS's order. */
        val choices: List<ReportReason> get() = entries.filter { it != UNKNOWN }
    }
}
