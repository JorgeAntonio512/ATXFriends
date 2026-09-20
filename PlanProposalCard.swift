//
//  PlanProposalCard.swift
//  Avenue3
//
//  Created by George Anthony Pazdral II on 8/27/26.
//

import SwiftUI

/// Renders a plan-proposal message as a rich card in the conversation thread.
///
/// The card loads the referenced Plan document on appear and shows:
///   - Activity name and proposed date/time
///   - Accept / Decline buttons when the current user is the receiver and the plan is pending
///   - A status pill once the plan is confirmed, declined, or cancelled
struct PlanProposalCard: View {
    let message: Message
    let isFromCurrentUser: Bool

    @State private var plan: Plan?
    @State private var isLoading = true
    @State private var isActing = false

    private var currentUserID: String {
        FirebaseAuthService.shared.currentUserID ?? ""
    }

    var body: some View {
        HStack {
            if isFromCurrentUser { Spacer(minLength: 60) }

            VStack(alignment: .leading, spacing: 12) {
                // ── card header ───────────────────────────────────────────
                HStack(spacing: 6) {
                    Image(systemName: "calendar.badge.plus")
                        .font(.system(size: 13, weight: .semibold))
                        .foregroundColor(Color.appPrimary)

                    Text("Plan Proposal")
                        .font(.system(size: 13, weight: .semibold, design: .rounded))
                        .foregroundColor(Color.appPrimary)

                    Spacer()

                    if let plan {
                        statusPill(for: plan.status)
                    }
                }

                Divider()
                    .background(Color.appPrimary.opacity(0.25))

                // ── plan details ──────────────────────────────────────────
                if isLoading {
                    Text(message.text)
                        .font(.system(size: 14, design: .rounded))
                        .foregroundColor(Color(red: 0.50, green: 0.50, blue: 0.50))
                        .lineLimit(2)
                    ProgressView()
                        .tint(Color.appPrimary)
                        .scaleEffect(0.75)
                } else if let plan {
                    // Activity
                    HStack(spacing: 8) {
                        Image(systemName: "figure.walk.circle.fill")
                            .font(.system(size: 20))
                            .foregroundColor(Color.appPrimary)

                        Text(plan.activity.name)
                            .font(.system(size: 17, weight: .semibold, design: .rounded))
                            .foregroundColor(Color(red: 0.25, green: 0.25, blue: 0.25))
                    }

                    // Date — show confirmedDate if accepted, otherwise first proposed date
                    let displayDate = plan.confirmedDate ?? plan.proposedDates.first
                    if let date = displayDate {
                        HStack(spacing: 8) {
                            Image(systemName: "clock.fill")
                                .font(.system(size: 14))
                                .foregroundColor(Color(red: 0.60, green: 0.60, blue: 0.60))

                            Text(formattedDate(date))
                                .font(.system(size: 15, design: .rounded))
                                .foregroundColor(Color(red: 0.45, green: 0.45, blue: 0.45))
                        }
                    }

                    // Accept / Decline — receiver only, pending plans
                    if plan.status == .pending && plan.receiverID == currentUserID {
                        HStack(spacing: 10) {
                            Button {
                                Task { await decline(plan: plan) }
                            } label: {
                                Text("Decline")
                                    .font(.system(size: 15, weight: .semibold, design: .rounded))
                                    .foregroundColor(Color(red: 0.72, green: 0.33, blue: 0.28))
                                    .frame(maxWidth: .infinity)
                                    .padding(.vertical, 9)
                                    .background(Color(red: 0.72, green: 0.33, blue: 0.28).opacity(0.10))
                                    .cornerRadius(10)
                            }

                            Button {
                                Task { await accept(plan: plan) }
                            } label: {
                                Text("Accept")
                                    .font(.system(size: 15, weight: .semibold, design: .rounded))
                                    .foregroundColor(.white)
                                    .frame(maxWidth: .infinity)
                                    .padding(.vertical, 9)
                                    .background(
                                        LinearGradient(
                                            colors: [
                                                Color.appPrimary,
                                                Color.appPrimary
                                            ],
                                            startPoint: .leading,
                                            endPoint: .trailing
                                        )
                                    )
                                    .cornerRadius(10)
                            }
                        }
                        .disabled(isActing)
                        .padding(.top, 4)
                        .overlay {
                            if isActing {
                                ProgressView()
                                    .tint(Color.appPrimary)
                            }
                        }
                    }
                } else {
                    // Plan failed to load — show summary text from message
                    Text(message.text)
                        .font(.system(size: 14, design: .rounded))
                        .foregroundColor(Color(red: 0.50, green: 0.50, blue: 0.50))
                }
            }
            .padding(14)
            .background(Color.white.opacity(0.93))
            .cornerRadius(16)
            .shadow(color: .black.opacity(0.07), radius: 8, x: 0, y: 2)
            .frame(maxWidth: 300)

            if !isFromCurrentUser { Spacer(minLength: 60) }
        }
        .padding(.horizontal, 16)
        .task {
            await loadPlan()
        }
    }

    // MARK: - Helpers

    @ViewBuilder
    private func statusPill(for status: PlanStatus) -> some View {
        switch status {
        case .pending:
            Text("Pending")
                .font(.system(size: 11, weight: .semibold, design: .rounded))
                .foregroundColor(Color(red: 0.70, green: 0.55, blue: 0.20))
                .padding(.horizontal, 8)
                .padding(.vertical, 3)
                .background(Color(red: 0.97, green: 0.90, blue: 0.60).opacity(0.6))
                .cornerRadius(8)
        case .confirmed:
            HStack(spacing: 3) {
                Image(systemName: "checkmark.circle.fill")
                    .font(.system(size: 11))
                Text("Confirmed")
                    .font(.system(size: 11, weight: .semibold, design: .rounded))
            }
            .foregroundColor(Color(red: 0.30, green: 0.60, blue: 0.35))
            .padding(.horizontal, 8)
            .padding(.vertical, 3)
            .background(Color(red: 0.55, green: 0.80, blue: 0.55).opacity(0.18))
            .cornerRadius(8)
        case .declined, .cancelled:
            Text(status == .declined ? "Declined" : "Cancelled")
                .font(.system(size: 11, weight: .semibold, design: .rounded))
                .foregroundColor(Color(red: 0.60, green: 0.35, blue: 0.30))
                .padding(.horizontal, 8)
                .padding(.vertical, 3)
                .background(Color(red: 0.80, green: 0.40, blue: 0.35).opacity(0.12))
                .cornerRadius(8)
        case .counterProposed:
            Text("Counter")
                .font(.system(size: 11, weight: .semibold, design: .rounded))
                .foregroundColor(Color(red: 0.40, green: 0.40, blue: 0.70))
                .padding(.horizontal, 8)
                .padding(.vertical, 3)
                .background(Color(red: 0.50, green: 0.50, blue: 0.85).opacity(0.12))
                .cornerRadius(8)
        }
    }

    private func formattedDate(_ date: Date) -> String {
        let cal = Calendar.current
        let timeFormatter = DateFormatter()
        timeFormatter.dateFormat = "h:mma"
        timeFormatter.amSymbol = "am"
        timeFormatter.pmSymbol = "pm"
        let timeStr = timeFormatter.string(from: date)
            .replacingOccurrences(of: ":00", with: "")

        if cal.isDateInToday(date) {
            return "Today at \(timeStr)"
        } else if cal.isDateInTomorrow(date) {
            return "Tomorrow at \(timeStr)"
        } else {
            let df = DateFormatter()
            df.dateFormat = "EEE, MMM d"
            return "\(df.string(from: date)) at \(timeStr)"
        }
    }

    private func loadPlan() async {
        guard let planID = message.planID else {
            isLoading = false
            return
        }
        plan = try? await PlansService.shared.fetchPlan(planID: planID)
        isLoading = false
    }

    private func accept(plan: Plan) async {
        guard let date = plan.proposedDates.first else { return }
        isActing = true
        defer { isActing = false }
        try? await PlansService.shared.confirmPlan(planID: plan.id, selectedDate: date)
        self.plan = try? await PlansService.shared.fetchPlan(planID: plan.id)
    }

    private func decline(plan: Plan) async {
        isActing = true
        defer { isActing = false }
        try? await PlansService.shared.declinePlan(planID: plan.id)
        self.plan = try? await PlansService.shared.fetchPlan(planID: plan.id)
    }
}
