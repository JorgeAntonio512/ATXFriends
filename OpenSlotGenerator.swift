//
//  OpenSlotGenerator.swift
//  Avenue3
//

import Foundation

/// A suggested-but-unposted plan, built purely from the user's own profile (timeslots +
/// activities) and their existing plans. Backs the "ghost card" slots on Today and
/// Upcoming — never written to Firestore, never shown as if it were a real plan.
struct OpenSlot: Identifiable, Equatable {
    let id: String
    let start: Date
    /// The raw profile activity name (no "?" suffix) — this is what gets submitted if the
    /// user posts from this slot. Views append their own phrasing ("Trivia night?") for display.
    let activityName: String
    let dayOfWeek: DayOfWeek
    let timeSlot: TimeSlot
}

/// Pure, deterministic generator for open-slot ghost cards. Takes only data already loaded
/// client-side (the user's profile + their existing plans) and a date range — no Firestore
/// access, no randomness. Same inputs always produce the same slots in the same order.
enum OpenSlotGenerator {
    /// Mirrors ProposePlanSheet's "Today" minimum lead time — a slot can't be suggested if
    /// it's less than 15 minutes out.
    static let minimumLeadTime: TimeInterval = 15 * 60

    /// Today's fixed spontaneous-suggestion clock times, used only to fill remaining ghost
    /// card spots when the user's own timeslots don't land in the next 24 hours. Any 24-hour
    /// window contains each of these exactly once, so Today always has cards to offer.
    /// Change the candidate times here.
    static let fallbackCandidateTimes: [(hour: Int, minute: Int, bucket: TimeSlot)] = [
        (8, 0, .wakeUp),
        (12, 0, .afternoon),
        (19, 0, .evening)
    ]

    /// Where a Today ghost card's suggestion came from — the user's own recurring
    /// timeslots, or the fixed spontaneous fallback times.
    enum Source: String {
        case usual
        case fallback
    }

    /// An OpenSlot tagged with where it came from, for Today's mixed usual+fallback feed.
    struct RankedOpenSlot: Identifiable, Equatable {
        let slot: OpenSlot
        let source: Source
        var id: String { slot.id }
    }

    // MARK: - Tiered activity ordering

    /// Reorders a user's own activities so ones matching `tier` come first, followed by the
    /// rest — Today calls this with `.spontaneous` and Upcoming with `.planned`, so the two
    /// tabs' rotations (see `buildSlots`) draw from disjoint pools whenever there are enough
    /// distinct activities to do so, and only fall back to sharing a name when the preferred
    /// pool is too small to fill 3 slots on its own. Activities with no category (user-typed
    /// custom ones) default into the `.spontaneous` pool.
    static func activities(from activities: [Activity], preferring tier: ActivitySuggestionTier) -> [Activity] {
        func categoryTier(of activity: Activity) -> ActivitySuggestionTier {
            ActivityCategories.category(for: activity.name)?.suggestionTier ?? .spontaneous
        }
        let preferred = activities.filter { categoryTier(of: $0) == tier }
        let rest = activities.filter { categoryTier(of: $0) != tier }
        return preferred + rest
    }

    /// - Parameters:
    ///   - daySlotCombos: the user's recurring day/slot preferences (from their profile).
    ///   - activities: the user's profile activities, rotated through for suggestions.
    ///   - from: lower bound of the search window (typically `Date()`).
    ///   - through: upper bound of the search window.
    ///   - existingPlanStarts: start times of plans the user already has, so slots that
    ///     collide with something already on their plate are skipped.
    ///   - maxCount: maximum number of slots to return.
    static func generate(
        daySlotCombos: [DaySlotCombo],
        activities: [Activity],
        from now: Date,
        through end: Date,
        existingPlanStarts: [Date],
        maxCount: Int,
        calendar: Calendar = .current
    ) -> [OpenSlot] {
        guard !daySlotCombos.isEmpty, !activities.isEmpty else {
            print("🎯 OpenSlotGenerator: skipped run — daySlotCombos=\(daySlotCombos.count) activities=\(activities.count) (need at least one of each)")
            return []
        }

        let earliestAllowed = now.addingTimeInterval(minimumLeadTime)

        // Stable order (weekday, then slot start hour) keeps output deterministic across runs
        // with the same inputs, independent of the order combos happen to be stored in.
        let orderedCombos = orderedByWeekday(daySlotCombos)

        var candidates: [(combo: DaySlotCombo, date: Date)] = []
        var skippedTooSoon = 0
        var skippedOverlap = 0

        for combo in orderedCombos {
            for candidateDate in occurrences(of: combo, from: now, through: end, calendar: calendar) {
                if candidateDate < earliestAllowed {
                    skippedTooSoon += 1
                    continue
                }
                if overlapsExistingPlan(candidateDate, existingPlanStarts: existingPlanStarts, calendar: calendar) {
                    skippedOverlap += 1
                    continue
                }
                candidates.append((combo, candidateDate))
            }
        }

        candidates.sort { $0.date < $1.date }

        let slots = buildSlots(from: candidates, activities: activities, maxCount: maxCount)

        print("🎯 OpenSlotGenerator: combos=\(daySlotCombos.count) activities=\(activities.count) existingPlans=\(existingPlanStarts.count) → candidates=\(candidates.count) output=\(slots.count) | skipped tooSoon=\(skippedTooSoon) overlap=\(skippedOverlap)")

        return slots
    }

    /// Today's feed: the user's own usual timeslots in the next 24 hours, topped up to
    /// `maxCount` with the fixed fallback clock times when the usual slots don't fill it.
    /// Usual slots always sort before fallback slots, regardless of clock time.
    static func generateForToday(
        daySlotCombos: [DaySlotCombo],
        activities: [Activity],
        from now: Date,
        existingPlanStarts: [Date],
        maxCount: Int = 3,
        calendar: Calendar = .current
    ) -> [RankedOpenSlot] {
        let end = now.addingTimeInterval(24 * 60 * 60)
        let usual = generate(
            daySlotCombos: daySlotCombos,
            activities: activities,
            from: now,
            through: end,
            existingPlanStarts: existingPlanStarts,
            maxCount: maxCount,
            calendar: calendar
        )
        var ranked = usual.map { RankedOpenSlot(slot: $0, source: .usual) }

        if ranked.count < maxCount, !activities.isEmpty {
            let earliestAllowed = now.addingTimeInterval(minimumLeadTime)
            var usedStarts = Set(ranked.map { $0.slot.start })
            var skippedTooSoon = 0
            var skippedOverlap = 0

            var fallbackCandidates: [(date: Date, bucket: TimeSlot)] = []
            for (hour, minute, bucket) in fallbackCandidateTimes {
                for dayOffset in [0, 1] {
                    guard let day = calendar.date(byAdding: .day, value: dayOffset, to: calendar.startOfDay(for: now)) else { continue }
                    var comps = calendar.dateComponents([.year, .month, .day], from: day)
                    comps.hour = hour
                    comps.minute = minute
                    guard let candidate = calendar.date(from: comps) else { continue }
                    // Any 24-hour window contains exactly one occurrence of a fixed clock
                    // time; stop at the first day offset that lands inside [now, end].
                    if candidate >= now, candidate <= end {
                        fallbackCandidates.append((candidate, bucket))
                        break
                    }
                }
            }
            fallbackCandidates.sort { $0.date < $1.date }

            for (index, entry) in fallbackCandidates.enumerated() {
                guard ranked.count < maxCount else { break }
                if entry.date < earliestAllowed {
                    skippedTooSoon += 1
                    continue
                }
                if usedStarts.contains(entry.date) || overlapsExistingPlan(entry.date, existingPlanStarts: existingPlanStarts, calendar: calendar) {
                    skippedOverlap += 1
                    continue
                }
                let activity = activities[(usual.count + index) % activities.count]
                let weekday = DayOfWeek.from(weekdayComponent: calendar.component(.weekday, from: entry.date))
                let slot = OpenSlot(
                    id: "fallback_\(Int(entry.date.timeIntervalSince1970))",
                    start: entry.date,
                    activityName: activity.name,
                    dayOfWeek: weekday,
                    timeSlot: entry.bucket
                )
                ranked.append(RankedOpenSlot(slot: slot, source: .fallback))
                usedStarts.insert(entry.date)
            }

            let fallbackAdded = ranked.count - usual.count
            print("🎯 OpenSlotGenerator(today fallback): candidates=\(fallbackCandidates.count) added=\(fallbackAdded) | skipped tooSoon=\(skippedTooSoon) overlap=\(skippedOverlap)")
        }

        let summary = ranked.map { "\($0.slot.dayOfWeek.rawValue) \($0.slot.timeSlot.rawValue)(\($0.source.rawValue))" }.joined(separator: ", ")
        print("🎯 OpenSlotGenerator(today): usual=\(usual.count) fallback=\(ranked.count - usual.count) total=\(ranked.count) → [\(summary)]")

        return ranked
    }

    /// The earliest future occurrence of any of the user's recurring day/slot combos within
    /// [from, through] — regardless of lead time or existing-plan overlap. Backs Today's
    /// "Your next usual slot" link; not used for anything postable.
    static func nextUsualOccurrence(
        daySlotCombos: [DaySlotCombo],
        from now: Date,
        through end: Date,
        calendar: Calendar = .current
    ) -> OpenSlot? {
        guard !daySlotCombos.isEmpty else { return nil }
        var earliest: (combo: DaySlotCombo, date: Date)?
        for combo in orderedByWeekday(daySlotCombos) {
            for date in occurrences(of: combo, from: now, through: end, calendar: calendar) {
                if earliest == nil || date < earliest!.date {
                    earliest = (combo, date)
                }
            }
        }
        guard let earliest else { return nil }
        return OpenSlot(
            id: "next_\(earliest.combo.dayOfWeek.rawValue)_\(earliest.combo.timeSlot.rawValue)",
            start: earliest.date,
            activityName: "",
            dayOfWeek: earliest.combo.dayOfWeek,
            timeSlot: earliest.combo.timeSlot
        )
    }

    // MARK: - Shared helpers

    private static func orderedByWeekday(_ combos: [DaySlotCombo]) -> [DaySlotCombo] {
        combos.sorted { lhs, rhs in
            if lhs.dayOfWeek.sortIndex != rhs.dayOfWeek.sortIndex {
                return lhs.dayOfWeek.sortIndex < rhs.dayOfWeek.sortIndex
            }
            return lhs.timeSlot.startHour < rhs.timeSlot.startHour
        }
    }

    private static func overlapsExistingPlan(_ date: Date, existingPlanStarts: [Date], calendar: Calendar) -> Bool {
        existingPlanStarts.contains { existing in
            calendar.isDate(existing, inSameDayAs: date) &&
            calendar.isDate(existing, equalTo: date, toGranularity: .hour)
        }
    }

    private static func buildSlots(
        from candidates: [(combo: DaySlotCombo, date: Date)],
        activities: [Activity],
        maxCount: Int
    ) -> [OpenSlot] {
        candidates.prefix(maxCount).enumerated().map { index, entry in
            let activity = activities[index % activities.count]
            return OpenSlot(
                id: "\(entry.combo.dayOfWeek.rawValue)_\(entry.combo.timeSlot.rawValue)_\(Int(entry.date.timeIntervalSince1970))",
                start: entry.date,
                activityName: activity.name,
                dayOfWeek: entry.combo.dayOfWeek,
                timeSlot: entry.combo.timeSlot
            )
        }
    }

    /// Every concrete occurrence of a recurring day/slot combo's start time within [from, through].
    private static func occurrences(
        of combo: DaySlotCombo,
        from: Date,
        through: Date,
        calendar: Calendar
    ) -> [Date] {
        var results: [Date] = []
        var dayCursor = calendar.startOfDay(for: from)

        // Bounded walk — comfortably covers Upcoming's 7-day window; guards against an
        // infinite loop if `through` is ever malformed.
        var guardCount = 0
        while dayCursor <= through && guardCount < 400 {
            guardCount += 1
            if calendar.component(.weekday, from: dayCursor) == combo.dayOfWeek.weekdayComponent {
                var comps = calendar.dateComponents([.year, .month, .day], from: dayCursor)
                comps.hour = combo.timeSlot.startHour
                comps.minute = 0
                if let candidate = calendar.date(from: comps), candidate >= from, candidate <= through {
                    results.append(candidate)
                }
            }
            guard let next = calendar.date(byAdding: .day, value: 1, to: dayCursor) else { break }
            dayCursor = next
        }
        return results
    }
}
