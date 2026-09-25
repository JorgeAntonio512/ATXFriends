//
//  UpcomingSectionsTests.swift
//  Avenue3
//

import Foundation
import Testing
@testable import ATX_Friends

/// Every upcoming group plan must land in exactly one visible place on the Upcoming tab:
/// the "Today" section, one of the 7 strip days (tomorrow … today + 7), or "Later".
struct UpcomingSectionsTests {

    private var calendar: Calendar {
        var cal = Calendar(identifier: .gregorian)
        cal.timeZone = TimeZone(identifier: "America/Chicago")!
        return cal
    }

    /// Thursday, Sep 24 2026, 12:15pm in Austin.
    private var now: Date {
        calendar.date(from: DateComponents(year: 2026, month: 9, day: 24, hour: 12, minute: 15))!
    }

    private func plan(daysFromToday days: Int, hour: Int) -> GroupPlan {
        let day = calendar.date(byAdding: .day, value: days, to: calendar.startOfDay(for: now))!
        let date = calendar.date(bySettingHour: hour, minute: 0, second: 0, of: day)!
        return GroupPlan(hostID: "me", inviteeIDs: ["sam"], activity: Activity(name: "Board Games"), date: date)
    }

    @Test func inviteForLaterToday_showsInToday() {
        let oneOClock = plan(daysFromToday: 0, hour: 13) // the composer's default: next whole hour
        #expect(GroupPlansViewModel.todayPlans([oneOClock], now: now, calendar: calendar).count == 1)
        #expect(GroupPlansViewModel.laterPlans([oneOClock], now: now, calendar: calendar).isEmpty)
    }

    @Test func inviteTenDaysOut_showsInLater() {
        let tenDays = plan(daysFromToday: 10, hour: 19)
        #expect(GroupPlansViewModel.laterPlans([tenDays], now: now, calendar: calendar).count == 1)
        #expect(GroupPlansViewModel.todayPlans([tenDays], now: now, calendar: calendar).isEmpty)
    }

    @Test func stripDays_areNeitherTodayNorLater() {
        for days in 1...7 {
            let p = plan(daysFromToday: days, hour: 23)
            #expect(GroupPlansViewModel.todayPlans([p], now: now, calendar: calendar).isEmpty, "day +\(days)")
            #expect(GroupPlansViewModel.laterPlans([p], now: now, calendar: calendar).isEmpty, "day +\(days)")
        }
        let justPastStrip = plan(daysFromToday: 8, hour: 0)
        #expect(GroupPlansViewModel.laterPlans([justPastStrip], now: now, calendar: calendar).count == 1)
    }
}
