//
//  PlanDetailView.swift
//  Avenue3
//
//  Created by George Anthony Pazdral II on 5/17/26.
//

import SwiftUI
import EventKit
import EventKitUI
import UIKit

/// Detail view for a specific plan showing all information and actions
struct PlanDetailView: View {
    let plan: Plan
    @Bindable var viewModel: PlansViewModel

    @Environment(\.dismiss) private var dismiss
    @State private var otherUser: FirebaseUser?
    @State private var selectedDate: Date?
    @State private var showCounterProposal = false
    @State private var showCancelConfirmation = false

    @State private var counterDate1: Date = Date().addingTimeInterval(86400 * 3)
    @State private var counterDate2: Date = Date().addingTimeInterval(86400 * 4)
    @State private var counterDate3: Date = Date().addingTimeInterval(86400 * 5)

    @State private var calendarHandler = PlanCalendarActionHandler()

    private let authService = FirebaseAuthService.shared
    
    private var isReceiver: Bool {
        guard let userID = authService.currentUserID else { return false }
        return plan.receiverID == userID
    }
    
    private var datesToShow: [Date] {
        if plan.status == .counterProposed, let counterDates = plan.counterProposedDates {
            return counterDates
        }
        return plan.proposedDates
    }
    
    var body: some View {
        ZStack {
            // Warm gradient background
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
                    // Header Card
                    VStack(spacing: 16) {
                        // Status Badge
                        HStack {
                            Spacer()
                            
                            HStack(spacing: 6) {
                                Image(systemName: plan.status.icon)
                                    .font(.system(size: 14))
                                
                                Text(plan.status.displayName)
                                    .font(.system(size: 14, weight: .semibold, design: .rounded))
                            }
                            .foregroundColor(.white)
                            .padding(.horizontal, 16)
                            .padding(.vertical, 8)
                            .background(statusColor)
                            .cornerRadius(20)
                        }
                        
                        // User Info
                        VStack(spacing: 8) {
                            Circle()
                                .fill(
                                    LinearGradient(
                                        colors: [
                                            Color.appPrimary,
                                            Color.appPrimary
                                        ],
                                        startPoint: .topLeading,
                                        endPoint: .bottomTrailing
                                    )
                                )
                                .frame(width: 80, height: 80)
                                .overlay(
                                    Text(otherUser?.displayName.prefix(1).uppercased() ?? "?")
                                        .font(.system(size: 36, weight: .bold, design: .rounded))
                                        .foregroundColor(.white)
                                )
                            
                            Text(otherUser?.displayName ?? "Loading...")
                                .font(.system(size: 24, weight: .bold, design: .rounded))
                                .foregroundColor(Color.appNavy)
                            
                            if isReceiver {
                                Text("wants to hang out with you!")
                                    .font(.system(size: 16, weight: .medium, design: .rounded))
                                    .foregroundColor(Color(red: 0.50, green: 0.50, blue: 0.50))
                            } else {
                                Text("Your proposal")
                                    .font(.system(size: 16, weight: .medium, design: .rounded))
                                    .foregroundColor(Color(red: 0.50, green: 0.50, blue: 0.50))
                            }
                        }
                        
                        Divider()
                            .background(Color.appPrimary.opacity(0.3))
                        
                        // Activity
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
                        
                        // Location (if provided) — tap to get directions
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
                        
                        // Confirmed Date (if confirmed)
                        if plan.status == .confirmed, let confirmedDate = plan.confirmedDate {
                            VStack(spacing: 12) {
                                Divider()
                                    .background(Color.appPrimary.opacity(0.3))
                                
                                HStack(spacing: 12) {
                                    Image(systemName: "checkmark.seal.fill")
                                        .font(.system(size: 28))
                                        .foregroundColor(Color.appPrimary)
                                    
                                    VStack(alignment: .leading, spacing: 4) {
                                        Text("Confirmed Date")
                                            .font(.system(size: 13, weight: .semibold, design: .rounded))
                                            .foregroundColor(Color.appPrimary)
                                        
                                        Text(confirmedDate.formatted(date: .long, time: .shortened))
                                            .font(.system(size: 18, weight: .bold, design: .rounded))
                                            .foregroundColor(Color.appNavy)
                                    }
                                    
                                    Spacer()
                                }
                                .padding()
                                .background(Color.appPrimary.opacity(0.15))
                                .cornerRadius(12)
                            }
                        }
                    }
                    .padding(20)
                    .background(Color.white.opacity(0.9))
                    .cornerRadius(20)
                    .shadow(color: .black.opacity(0.08), radius: 12, x: 0, y: 6)
                    .padding(.horizontal, 20)
                    .padding(.top, 20)
                    
                    // Proposed Dates Section (if not confirmed)
                    if plan.status != .confirmed {
                        VStack(alignment: .leading, spacing: 16) {
                            Text(plan.status == .counterProposed ? "Counter-Proposed Dates" : "Proposed Dates")
                                .font(.system(size: 20, weight: .bold, design: .rounded))
                                .foregroundColor(Color.appNavy)
                                .padding(.horizontal, 20)
                            
                            VStack(spacing: 12) {
                                ForEach(datesToShow, id: \.self) { date in
                                    DateOptionCard(
                                        date: date,
                                        isSelected: selectedDate == date,
                                        onTap: {
                                            withAnimation {
                                                selectedDate = date
                                            }
                                        }
                                    )
                                }
                            }
                            .padding(.horizontal, 20)
                        }
                    }
                    
                    // Action Buttons
                    if isReceiver && (plan.status == .pending || plan.status == .counterProposed) {
                        VStack(spacing: 12) {
                            // Confirm button (if date selected)
                            if let date = selectedDate {
                                Button {
                                    Task {
                                        let success = await viewModel.confirmPlan(plan, selectedDate: date)
                                        if success {
                                            dismiss()
                                        }
                                    }
                                } label: {
                                    HStack {
                                        Image(systemName: "checkmark.circle.fill")
                                        Text("Confirm This Date")
                                    }
                                    .font(.system(size: 18, weight: .semibold, design: .rounded))
                                    .foregroundColor(.white)
                                    .frame(maxWidth: .infinity)
                                    .frame(height: 56)
                                    .background(
                                        LinearGradient(
                                            colors: [
                                                Color.appNavy,
                                                Color.appNavy
                                            ],
                                            startPoint: .leading,
                                            endPoint: .trailing
                                        )
                                    )
                                    .cornerRadius(16)
                                    .shadow(color: Color.appNavy.opacity(0.3), radius: 12, x: 0, y: 6)
                                }
                            }
                            
                            // Counter-propose button
                            Button {
                                showCounterProposal = true
                            } label: {
                                HStack {
                                    Image(systemName: "arrow.left.arrow.right")
                                    Text("Suggest Different Dates")
                                }
                                .font(.system(size: 16, weight: .semibold, design: .rounded))
                                .foregroundColor(Color.appPrimary)
                                .frame(maxWidth: .infinity)
                                .frame(height: 50)
                                .background(Color.white.opacity(0.8))
                                .cornerRadius(14)
                                .overlay(
                                    RoundedRectangle(cornerRadius: 14)
                                        .stroke(Color.appPrimary, lineWidth: 2)
                                )
                            }
                            
                            // Decline button
                            Button {
                                Task {
                                    let success = await viewModel.declinePlan(plan)
                                    if success {
                                        dismiss()
                                    }
                                }
                            } label: {
                                Text("Decline")
                                    .font(.system(size: 14, weight: .medium, design: .rounded))
                                    .foregroundColor(Color(red: 0.85, green: 0.45, blue: 0.40))
                            }
                        }
                        .padding(.horizontal, 20)
                    } else if plan.status == .confirmed {
                        // Add to Calendar + Cancel buttons for confirmed plans
                        HStack(spacing: 12) {
                            addToCalendarButton
                            cancelPlanButton
                        }
                        .padding(.horizontal, 20)
                    }
                    
                    Spacer()
                        .frame(height: 40)
                }
            }
            .scrollIndicators(.hidden)
        }
        .navigationTitle("Plan Details")
        .navigationBarTitleDisplayMode(.inline)
        .sheet(isPresented: $showCounterProposal) {
            CounterProposalSheet(
                plan: plan,
                viewModel: viewModel,
                date1: $counterDate1,
                date2: $counterDate2,
                date3: $counterDate3,
                onSubmit: {
                    dismiss()
                }
            )
        }
        .alert("Cancel Plan?", isPresented: $showCancelConfirmation) {
            Button("Cancel Plan", role: .destructive) {
                Task {
                    let success = await viewModel.cancelPlan(plan)
                    if success {
                        dismiss()
                    }
                }
            }
            Button("Keep Plan", role: .cancel) {}
        } message: {
            Text("Are you sure you want to cancel this plan? \(otherUser?.displayName ?? "Your friend") will be notified.")
        }
        .task {
            let otherUserID = plan.otherUserID(for: authService.currentUserID ?? "")
            otherUser = await viewModel.fetchUser(userID: otherUserID)
            calendarHandler.loadAddedState(planID: plan.id)
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

    private var statusColor: Color {
        switch plan.status {
        case .pending: Color.appPrimary
        case .counterProposed: Color.orange
        case .confirmed: Color.appPrimary
        case .declined: Color(red: 0.85, green: 0.45, blue: 0.40)
        case .cancelled: Color.gray
        }
    }

    private var addToCalendarButton: some View {
        Menu {
            ForEach(CalendarProvider.allCases) { provider in
                Button {
                    calendarHandler.tap(
                        provider: provider,
                        plan: plan,
                        otherUserDisplayName: otherUser?.displayName ?? "your friend"
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

// MARK: - Event Edit View

/// Wraps the system EKEventEditViewController (the standard iOS "New Event" sheet)
/// so the user can review and tweak the event before explicitly tapping "Add".
struct EventEditView: UIViewControllerRepresentable {
    let event: EKEvent
    let eventStore: EKEventStore
    let onComplete: (EKEventEditViewAction) -> Void

    func makeUIViewController(context: Context) -> EKEventEditViewController {
        let controller = EKEventEditViewController()
        controller.event = event
        controller.eventStore = eventStore
        controller.editViewDelegate = context.coordinator
        return controller
    }

    func updateUIViewController(_ uiViewController: EKEventEditViewController, context: Context) {}

    func makeCoordinator() -> Coordinator {
        Coordinator(onComplete: onComplete)
    }

    final class Coordinator: NSObject, EKEventEditViewDelegate {
        let onComplete: (EKEventEditViewAction) -> Void

        init(onComplete: @escaping (EKEventEditViewAction) -> Void) {
            self.onComplete = onComplete
        }

        func eventEditViewController(_ controller: EKEventEditViewController, didCompleteWith action: EKEventEditViewAction) {
            // Dismissal is driven by the SwiftUI `.sheet` binding (onComplete clears
            // eventToAdd), not by calling dismiss() on this embedded controller directly —
            // mirrors Apple's own EventKitUI + SwiftUI sample pattern.
            onComplete(action)
        }
    }
}

// MARK: - Date Option Card

struct DateOptionCard: View {
    let date: Date
    let isSelected: Bool
    let onTap: () -> Void
    
    var body: some View {
        Button(action: onTap) {
            HStack(spacing: 16) {
                // Date Circle
                VStack(spacing: 2) {
                    Text(date.formatted(.dateTime.month(.abbreviated)))
                        .font(.system(size: 12, weight: .semibold, design: .rounded))
                        .foregroundColor(isSelected ? .white : Color(red: 0.50, green: 0.50, blue: 0.50))
                    
                    Text(date.formatted(.dateTime.day()))
                        .font(.system(size: 24, weight: .bold, design: .rounded))
                        .foregroundColor(isSelected ? .white : Color.appNavy)
                }
                .frame(width: 60, height: 60)
                .background(
                    isSelected ?
                    LinearGradient(
                        colors: [
                            Color.appPrimary,
                            Color.appPrimary
                        ],
                        startPoint: .topLeading,
                        endPoint: .bottomTrailing
                    ) :
                    LinearGradient(
                        colors: [Color.white.opacity(0.5), Color.white.opacity(0.5)],
                        startPoint: .topLeading,
                        endPoint: .bottomTrailing
                    )
                )
                .cornerRadius(12)
                
                // Date Info
                VStack(alignment: .leading, spacing: 4) {
                    Text(date.formatted(.dateTime.weekday(.wide)))
                        .font(.system(size: 16, weight: .semibold, design: .rounded))
                        .foregroundColor(Color.appNavy)
                    
                    Text(date.formatted(date: .long, time: .omitted))
                        .font(.system(size: 13, weight: .medium, design: .rounded))
                        .foregroundColor(Color(red: 0.50, green: 0.50, blue: 0.50))
                    
                    Text(date.formatted(date: .omitted, time: .shortened))
                        .font(.system(size: 14, weight: .medium, design: .rounded))
                        .foregroundColor(Color.appPrimary)
                }
                
                Spacer()
                
                if isSelected {
                    Image(systemName: "checkmark.circle.fill")
                        .font(.system(size: 28))
                        .foregroundColor(Color.appPrimary)
                }
            }
            .padding()
            .background(
                isSelected ?
                Color.appPrimary.opacity(0.15) :
                Color.white.opacity(0.8)
            )
            .cornerRadius(16)
            .overlay(
                RoundedRectangle(cornerRadius: 16)
                    .stroke(
                        isSelected ?
                        Color.appPrimary :
                        Color.clear,
                        lineWidth: 2
                    )
            )
        }
        .buttonStyle(.plain)
    }
}

// MARK: - Counter Proposal Sheet

struct CounterProposalSheet: View {
    let plan: Plan
    @Bindable var viewModel: PlansViewModel
    
    @Binding var date1: Date
    @Binding var date2: Date
    @Binding var date3: Date
    
    let onSubmit: () -> Void
    
    @Environment(\.dismiss) private var dismiss
    @State private var isSubmitting = false
    
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
                
                VStack(spacing: 24) {
                    ScrollView {
                        VStack(spacing: 20) {
                            // Header
                            VStack(spacing: 12) {
                                Image(systemName: "arrow.left.arrow.right.circle.fill")
                                    .font(.system(size: 50))
                                    .foregroundColor(Color.orange)
                                
                                Text("Suggest Different Dates")
                                    .font(.system(size: 24, weight: .bold, design: .rounded))
                                    .foregroundColor(Color.appNavy)
                                
                                Text("Propose 3 alternative dates")
                                    .font(.system(size: 15, weight: .regular, design: .rounded))
                                    .foregroundColor(Color(red: 0.50, green: 0.50, blue: 0.50))
                            }
                            .padding(.top, 20)
                            
                            // Date Pickers
                            VStack(spacing: 16) {
                                DatePickerRow(title: "Option 1", date: $date1)
                                DatePickerRow(title: "Option 2", date: $date2)
                                DatePickerRow(title: "Option 3", date: $date3)
                            }
                            .padding(.horizontal, 20)
                        }
                    }
                    
                    // Submit Button
                    Button {
                        Task {
                            isSubmitting = true
                            let dates = [date1, date2, date3].sorted()
                            let success = await viewModel.counterPropose(plan, newDates: dates)
                            isSubmitting = false
                            
                            if success {
                                dismiss()
                                onSubmit()
                            }
                        }
                    } label: {
                        HStack {
                            if isSubmitting {
                                ProgressView()
                                    .tint(.white)
                            } else {
                                Image(systemName: "paperplane.fill")
                                Text("Send Counter-Proposal")
                            }
                        }
                        .font(.system(size: 18, weight: .semibold, design: .rounded))
                        .foregroundColor(.white)
                        .frame(maxWidth: .infinity)
                        .frame(height: 56)
                        .background(
                            LinearGradient(
                                colors: [Color.orange, Color.orange.opacity(0.8)],
                                startPoint: .leading,
                                endPoint: .trailing
                            )
                        )
                        .cornerRadius(16)
                        .shadow(color: Color.orange.opacity(0.3), radius: 12, x: 0, y: 6)
                    }
                    .disabled(isSubmitting)
                    .padding(.horizontal, 20)
                    .padding(.bottom, 20)
                }
            }
            .navigationTitle("Counter Proposal")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    Button("Cancel") {
                        dismiss()
                    }
                }
            }
        }
    }
}

// Moved from the deleted CreatePlanView.swift (2026-09-19) — this dead-code-but-still-compiled
// view is its only remaining consumer.
struct DatePickerRow: View {
    let title: String
    @Binding var date: Date
    
    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(title)
                .font(.system(size: 14, weight: .semibold, design: .rounded))
                .foregroundColor(Color.appPrimary)
            
            DatePicker("", selection: $date, in: Date()..., displayedComponents: [.date, .hourAndMinute])
                .datePickerStyle(.compact)
                .labelsHidden()
                .environment(\.locale, Locale(identifier: "en_US"))
                .padding()
                .background(Color.white.opacity(0.8))
                .cornerRadius(10)
        }
    }
}

#Preview {
    NavigationStack {
        PlanDetailView(
            plan: Plan(
                matchID: "test",
                proposerID: "user1",
                receiverID: "user2",
                activity: Activity(name: "Coffee"),
                location: "Starbucks Downtown",
                proposedDates: [
                    Date().addingTimeInterval(86400 * 3),
                    Date().addingTimeInterval(86400 * 4),
                    Date().addingTimeInterval(86400 * 5)
                ]
            ),
            viewModel: PlansViewModel()
        )
    }
}
