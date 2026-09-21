//
//  GroupPlanDetailView.swift
//  Avenue3
//

import SwiftUI
import EventKit
import EventKitUI
import UIKit

/// Detail view for a single group plan: who's invited and their status, Going/Can't make it
/// for invitees, Cancel for the host, and Add to Calendar via the same three providers the
/// 1-on-1 flow uses.
struct GroupPlanDetailView: View {
    let initialPlan: GroupPlan
    let viewModel: GroupPlansViewModel

    @Environment(\.dismiss) private var dismiss
    @State private var calendarHandler = PlanCalendarActionHandler()
    @State private var showCancelConfirmation = false
    @State private var isResponding = false

    init(plan: GroupPlan, viewModel: GroupPlansViewModel) {
        self.initialPlan = plan
        self.viewModel = viewModel
    }

    /// Always reads the live plan from the listener so status/response changes (including a
    /// host cancelling from elsewhere) show up immediately while this sheet is open.
    private var plan: GroupPlan {
        viewModel.groupPlans.first(where: { $0.id == initialPlan.id }) ?? initialPlan
    }

    private var currentUserID: String? { viewModel.currentUserID }
    private var isHost: Bool { currentUserID.map(plan.isHost) ?? false }
    private var isInvitee: Bool { currentUserID.map(plan.isInvitee) ?? false }
    private var myResponse: GroupPlanResponse? { currentUserID.flatMap(plan.response) }

    var body: some View {
        NavigationStack {
            ZStack {
                LinearGradient(
                    colors: [
                        Color.white,
                        Color.white
                    ],
                    startPoint: .topLeading,
                    endPoint: .bottomTrailing
                )
                .ignoresSafeArea()

                ScrollView {
                    VStack(spacing: 24) {
                        headerCard
                        inviteesCard

                        if isInvitee {
                            responseButtons
                                .padding(.horizontal, 20)
                        }

                        if isHost {
                            HStack(spacing: 12) {
                                addToCalendarButton
                                cancelPlanButton
                            }
                            .padding(.horizontal, 20)
                        } else {
                            addToCalendarButton
                                .padding(.horizontal, 20)
                        }

                        Spacer().frame(height: 40)
                    }
                }
                .scrollIndicators(.hidden)
            }
            .navigationTitle("Plan Details")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarLeading) {
                    Button("Done") { dismiss() }
                        .font(.system(size: 16, design: .rounded))
                        .foregroundColor(Color.appPrimary)
                }
            }
        }
        .task {
            calendarHandler.loadAddedState(planID: plan.id)
        }
        .alert("Cancel Plan?", isPresented: $showCancelConfirmation) {
            Button("Cancel Plan", role: .destructive) {
                Task {
                    let success = await viewModel.cancel(plan)
                    if success { dismiss() }
                }
            }
            Button("Keep Plan", role: .cancel) {}
        } message: {
            Text("This removes the plan for everyone invited.")
        }
        .sheet(isPresented: Binding(
            get: { calendarHandler.eventToAdd != nil },
            set: { if !$0 { calendarHandler.eventToAdd = nil } }
        )) {
            if let eventToAdd = calendarHandler.eventToAdd {
                EventEditView(
                    event: eventToAdd,
                    eventStore: calendarHandler.eventStore,
                    onComplete: { action in
                        calendarHandler.handleAppleEventEditCompletion(action, planID: plan.id)
                    }
                )
            }
        }
        .alert("Calendar Access Needed", isPresented: $calendarHandler.showCalendarPermissionAlert) {
            Button("Open Settings") {
                if let url = URL(string: UIApplication.openSettingsURLString) {
                    UIApplication.shared.open(url)
                }
            }
            Button("Cancel", role: .cancel) {}
        } message: {
            Text("Enable calendar access in Settings to add this event.")
        }
        .alert("Couldn't Add to Google Calendar", isPresented: Binding(
            get: { calendarHandler.googleErrorMessage != nil },
            set: { if !$0 { calendarHandler.googleErrorMessage = nil } }
        )) {
            Button("OK") { calendarHandler.googleErrorMessage = nil }
        } message: {
            Text(calendarHandler.googleErrorMessage ?? "")
        }
        .alert("Couldn't Add to Outlook Calendar", isPresented: Binding(
            get: { calendarHandler.microsoftErrorMessage != nil },
            set: { if !$0 { calendarHandler.microsoftErrorMessage = nil } }
        )) {
            Button("OK") { calendarHandler.microsoftErrorMessage = nil }
        } message: {
            Text(calendarHandler.microsoftErrorMessage ?? "")
        }
    }

    // MARK: - Header

    private var headerCard: some View {
        VStack(spacing: 16) {
            HStack {
                Spacer()
                HStack(spacing: 6) {
                    Image(systemName: plan.status == .cancelled ? "xmark.circle" : "calendar.badge.clock")
                        .font(.system(size: 14))
                    Text(plan.status == .cancelled ? "Cancelled" : "Active")
                        .font(.system(size: 14, weight: .semibold, design: .rounded))
                }
                .foregroundColor(.white)
                .padding(.horizontal, 16)
                .padding(.vertical, 8)
                .background(plan.status == .cancelled ? Color.gray : Color.appPrimary)
                .cornerRadius(20)
            }

            HStack(spacing: 12) {
                Image(systemName: "figure.run")
                    .font(.system(size: 24))
                    .foregroundColor(Color.appPrimary)
                VStack(alignment: .leading, spacing: 2) {
                    Text("Activity")
                        .font(.system(size: 12, weight: .medium, design: .rounded))
                        .foregroundColor(Color(red: 0.50, green: 0.50, blue: 0.50))
                    Text(plan.activity.name)
                        .font(.system(size: 18, weight: .semibold, design: .rounded))
                        .foregroundColor(Color.appNavy)
                }
                Spacer()
            }

            HStack(spacing: 12) {
                Image(systemName: "clock.fill")
                    .font(.system(size: 24))
                    .foregroundColor(Color.appPrimary)
                VStack(alignment: .leading, spacing: 2) {
                    Text("When")
                        .font(.system(size: 12, weight: .medium, design: .rounded))
                        .foregroundColor(Color(red: 0.50, green: 0.50, blue: 0.50))
                    Text(plan.date.formatted(date: .long, time: .shortened))
                        .font(.system(size: 18, weight: .semibold, design: .rounded))
                        .foregroundColor(Color.appNavy)
                }
                Spacer()
            }

            if let location = plan.location {
                Menu {
                    ForEach(NavigationApp.availableApps) { app in
                        Button {
                            if let url = app.directionsURL(for: plan) {
                                UIApplication.shared.open(url)
                            }
                        } label: {
                            Label(app.displayName, systemImage: "location.fill")
                        }
                    }
                } label: {
                    HStack(spacing: 12) {
                        Image(systemName: "mappin.circle.fill")
                            .font(.system(size: 24))
                            .foregroundColor(Color.appPrimary)
                        VStack(alignment: .leading, spacing: 2) {
                            Text("Location")
                                .font(.system(size: 12, weight: .medium, design: .rounded))
                                .foregroundColor(Color(red: 0.50, green: 0.50, blue: 0.50))
                            Text(location)
                                .font(.system(size: 16, weight: .medium, design: .rounded))
                                .foregroundColor(Color.appNavy)
                        }
                        Spacer()
                        Image(systemName: "chevron.right")
                            .font(.system(size: 12, weight: .semibold))
                            .foregroundColor(Color(red: 0.70, green: 0.70, blue: 0.70))
                    }
                }
                .buttonStyle(.plain)
            }

            HStack(spacing: 12) {
                Image(systemName: "person.circle.fill")
                    .font(.system(size: 24))
                    .foregroundColor(Color.appPrimary)
                VStack(alignment: .leading, spacing: 2) {
                    Text("Host")
                        .font(.system(size: 12, weight: .medium, design: .rounded))
                        .foregroundColor(Color(red: 0.50, green: 0.50, blue: 0.50))
                    Text(viewModel.hostName(for: plan))
                        .font(.system(size: 18, weight: .semibold, design: .rounded))
                        .foregroundColor(Color.appNavy)
                }
                Spacer()
            }
        }
        .padding(20)
        .background(Color.white.opacity(0.9))
        .cornerRadius(20)
        .shadow(color: .black.opacity(0.08), radius: 12, x: 0, y: 6)
        .padding(.horizontal, 20)
        .padding(.top, 20)
    }

    // MARK: - Invitees

    private var inviteesCard: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("Who's invited")
                .font(.system(size: 20, weight: .bold, design: .rounded))
                .foregroundColor(Color.appNavy)
                .padding(.horizontal, 20)

            VStack(spacing: 0) {
                ForEach(plan.inviteeIDs, id: \.self) { inviteeID in
                    HStack {
                        Text(viewModel.inviteeName(for: inviteeID))
                            .font(.system(size: 16, weight: .medium, design: .rounded))
                            .foregroundColor(Color.appNavy)
                        Spacer()
                        Text(statusLabel(for: plan.response(for: inviteeID)))
                            .font(.system(size: 14, weight: .semibold, design: .rounded))
                            .foregroundColor(statusColor(for: plan.response(for: inviteeID)))
                    }
                    .padding(.horizontal, 14)
                    .padding(.vertical, 10)

                    if inviteeID != plan.inviteeIDs.last {
                        Divider().padding(.leading, 14)
                    }
                }
            }
            .background(Color.white.opacity(0.85))
            .cornerRadius(12)
            .padding(.horizontal, 20)
        }
    }

    private func statusLabel(for response: GroupPlanResponse?) -> String {
        switch response {
        case .going: return "Going"
        case .cantMake: return "Can't make it"
        case .invited, .none: return "Invited"
        }
    }

    private func statusColor(for response: GroupPlanResponse?) -> Color {
        switch response {
        case .going: return Color(red: 0.30, green: 0.60, blue: 0.35)
        case .cantMake: return Color(red: 0.72, green: 0.33, blue: 0.28)
        case .invited, .none: return Color(red: 0.60, green: 0.60, blue: 0.60)
        }
    }

    // MARK: - Response Buttons

    private var responseButtons: some View {
        HStack(spacing: 12) {
            Button {
                Task {
                    isResponding = true
                    await viewModel.respond(to: plan, response: .cantMake)
                    isResponding = false
                }
            } label: {
                Text("Can't make it")
                    .font(.system(size: 15, weight: .semibold, design: .rounded))
                    .foregroundColor(myResponse == .cantMake ? .white : Color(red: 0.72, green: 0.33, blue: 0.28))
                    .frame(maxWidth: .infinity)
                    .frame(height: 50)
                    .background(myResponse == .cantMake ? Color(red: 0.72, green: 0.33, blue: 0.28) : Color.white.opacity(0.8))
                    .cornerRadius(14)
                    .overlay(
                        RoundedRectangle(cornerRadius: 14)
                            .stroke(Color(red: 0.72, green: 0.33, blue: 0.28), lineWidth: 2)
                    )
            }

            Button {
                Task {
                    isResponding = true
                    await viewModel.respond(to: plan, response: .going)
                    isResponding = false
                }
            } label: {
                Text("Going")
                    .font(.system(size: 15, weight: .semibold, design: .rounded))
                    .foregroundColor(.white)
                    .frame(maxWidth: .infinity)
                    .frame(height: 50)
                    .background(myResponse == .going ? Color.appNavy : Color.appPrimary)
                    .cornerRadius(14)
            }
        }
        .disabled(isResponding)
    }

    // MARK: - Host Actions

    private var addToCalendarButton: some View {
        Menu {
            ForEach(CalendarProvider.allCases) { provider in
                Button {
                    calendarHandler.tap(
                        provider: provider,
                        planID: plan.id,
                        fields: CalendarEventFields(
                            groupPlan: plan,
                            hostName: viewModel.hostName(for: plan),
                            inviteeNames: plan.inviteeIDs.map { viewModel.inviteeName(for: $0) }
                        )
                    )
                } label: {
                    Label(
                        calendarHandler.isAdded(provider) ? "\(provider.displayName) — Added ✓" : provider.displayName,
                        systemImage: calendarHandler.isAdded(provider) ? "checkmark.circle.fill" : "calendar"
                    )
                }
            }
        } label: {
            HStack(spacing: 6) {
                Image(systemName: calendarHandler.addedProviders.isEmpty ? "calendar.badge.plus" : "checkmark.circle.fill")
                Text(calendarHandler.addedProviders.isEmpty ? "Add to Calendar" : "Added ✓")
            }
            .font(.system(size: 15, weight: .semibold, design: .rounded))
            .foregroundColor(.white)
            .frame(maxWidth: .infinity)
            .frame(height: 50)
            .background(Color.appPrimary)
            .cornerRadius(14)
        }
    }

    private var cancelPlanButton: some View {
        Button {
            showCancelConfirmation = true
        } label: {
            Text("Cancel Plan")
                .font(.system(size: 16, weight: .semibold, design: .rounded))
                .foregroundColor(Color(red: 0.85, green: 0.45, blue: 0.40))
                .frame(maxWidth: .infinity)
                .frame(height: 50)
                .background(Color.white.opacity(0.8))
                .cornerRadius(14)
                .overlay(
                    RoundedRectangle(cornerRadius: 14)
                        .stroke(Color(red: 0.85, green: 0.45, blue: 0.40), lineWidth: 2)
                )
        }
    }
}
