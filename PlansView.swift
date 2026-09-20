//
//  PlansView.swift
//  Avenue3
//
//  Created by George Anthony Pazdral II on 5/7/26.
//

import SwiftUI

/// Plans tab showing proposed hangouts and upcoming events
struct PlansView: View {
    @State private var viewModel = PlansViewModel()
    @State private var selectedTab: PlanTab = .incoming
    @StateObject private var unreadState = UnreadState.shared

    enum PlanTab: String, CaseIterable {
        case incoming = "Incoming"
        case outgoing = "Outgoing"
        case upcoming = "Upcoming"

        var icon: String {
            switch self {
            case .incoming: "tray.and.arrow.down.fill"
            case .outgoing: "paperplane.fill"
            case .upcoming: "calendar.badge.checkmark"
            }
        }
    }

    /// Whether the Incoming tab has unread plans
    private var incomingHasUnread: Bool {
        viewModel.incomingPlans.contains { unreadState.unreadPlanIDs.contains($0.id) }
    }

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

                VStack(spacing: 0) {
                    // Custom Tab Selector
                    planTabSelector
                        .padding(.horizontal, 20)
                        .padding(.vertical, 12)

                    TabView(selection: $selectedTab) {
                        incomingPlansView
                            .tag(PlanTab.incoming)

                        outgoingPlansView
                            .tag(PlanTab.outgoing)

                        upcomingPlansView
                            .tag(PlanTab.upcoming)
                    }
                    .tabViewStyle(.page(indexDisplayMode: .never))
                }
            }
            .navigationTitle("Plans")
            .navigationBarTitleDisplayMode(.large)
            // The "+" that used to open CreatePlanView here was removed when that view was
            // deleted (2026-09-19) — PlansView is unreachable from any tab, so it stays dead
            // rather than being wired to the new ProposePlanSheet.
            .task {
                await viewModel.loadPlans()
                viewModel.setupListener()
            }
            .onDisappear {
                viewModel.removeListener()
            }
        }
    }

    // MARK: - Tab Selector

    private var planTabSelector: some View {
        HStack(spacing: 8) {
            ForEach(PlanTab.allCases, id: \.self) { tab in
                Button {
                    withAnimation(.spring(response: 0.3)) {
                        selectedTab = tab
                    }
                } label: {
                    ZStack(alignment: .topTrailing) {
                        VStack(spacing: 4) {
                            Image(systemName: tab.icon)
                                .font(.system(size: 16, weight: .semibold))
                            Text(tab.rawValue)
                                .font(.system(size: 12, weight: .semibold, design: .rounded))
                        }
                        .foregroundColor(
                            selectedTab == tab ? Color.white : Color.appPrimary
                        )
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 12)
                        .background(
                            selectedTab == tab ?
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
                        .shadow(
                            color: selectedTab == tab ?
                                Color.appPrimary.opacity(0.3) : Color.clear,
                            radius: 8, x: 0, y: 4
                        )

                        // Red dot for Incoming tab
                        if tab == .incoming && incomingHasUnread {
                            Circle()
                                .fill(Color(red: 0.85, green: 0.45, blue: 0.40))
                                .frame(width: 10, height: 10)
                                .offset(x: -4, y: 4)
                        }
                    }
                }
                .buttonStyle(.plain)
            }
        }
        .padding(4)
        .background(Color.white.opacity(0.3))
        .cornerRadius(16)
    }

    // MARK: - Incoming Plans View

    private var incomingPlansView: some View {
        ScrollView {
            VStack(spacing: 20) {
                // Section 1: Individuals & Couples
                VStack(alignment: .leading, spacing: 12) {
                    Text("Individuals & Couples")
                        .font(.system(size: 15, weight: .semibold, design: .rounded))
                        .foregroundColor(Color(red: 0.50, green: 0.50, blue: 0.50))
                        .textCase(.uppercase)
                        .padding(.leading, 4)
                    
                    if viewModel.incomingPlans.isEmpty {
                        emptyStateView(
                            icon: "tray.fill",
                            title: "No Incoming Plans",
                            subtitle: "When friends propose hangouts,\nthey'll appear here"
                        )
                        .padding(.vertical, 20)
                    } else {
                        ForEach(viewModel.incomingPlans) { plan in
                            NavigationLink {
                                PlanDetailView(plan: plan, viewModel: viewModel)
                                    .onAppear {
                                        // Mark viewed when detail opens
                                        if let userID = FirebaseAuthService.shared.currentUserID {
                                            UnreadState.shared.markPlanViewed(planID: plan.id, userID: userID)
                                        }
                                    }
                            } label: {
                                IncomingPlanCard(
                                    plan: plan,
                                    viewModel: viewModel,
                                    isUnread: unreadState.unreadPlanIDs.contains(plan.id)
                                )
                            }
                            .buttonStyle(.plain)
                        }
                    }
                }
                
                // Section 2: Events
                VStack(alignment: .leading, spacing: 12) {
                    Text("Events")
                        .font(.system(size: 15, weight: .semibold, design: .rounded))
                        .foregroundColor(Color(red: 0.50, green: 0.50, blue: 0.50))
                        .textCase(.uppercase)
                        .padding(.leading, 4)
                    
                    Text("Coming soon")
                        .font(.system(size: 15, weight: .regular, design: .rounded))
                        .foregroundColor(Color(red: 0.60, green: 0.60, blue: 0.60))
                        .padding()
                        .frame(maxWidth: .infinity)
                        .background(Color.white.opacity(0.5))
                        .cornerRadius(12)
                }
                
                // Section 3: Groups
                VStack(alignment: .leading, spacing: 12) {
                    Text("Groups")
                        .font(.system(size: 15, weight: .semibold, design: .rounded))
                        .foregroundColor(Color(red: 0.50, green: 0.50, blue: 0.50))
                        .textCase(.uppercase)
                        .padding(.leading, 4)
                    
                    Text("No group plans yet")
                        .font(.system(size: 15, weight: .regular, design: .rounded))
                        .foregroundColor(Color(red: 0.60, green: 0.60, blue: 0.60))
                        .padding()
                        .frame(maxWidth: .infinity)
                        .background(Color.white.opacity(0.5))
                        .cornerRadius(12)
                }
            }
            .padding(.horizontal, 20)
            .padding(.vertical, 12)
        }
        .scrollIndicators(.hidden)
    }

    // MARK: - Outgoing Plans View

    private var outgoingPlansView: some View {
        ScrollView {
            VStack(spacing: 20) {
                // Section 1: Individuals & Couples
                VStack(alignment: .leading, spacing: 12) {
                    Text("Individuals & Couples")
                        .font(.system(size: 15, weight: .semibold, design: .rounded))
                        .foregroundColor(Color(red: 0.50, green: 0.50, blue: 0.50))
                        .textCase(.uppercase)
                        .padding(.leading, 4)
                    
                    if viewModel.outgoingPlans.isEmpty {
                        emptyStateView(
                            icon: "paperplane.fill",
                            title: "No Outgoing Plans",
                            subtitle: "Tap + to propose a hangout\nwith your matches"
                        )
                        .padding(.vertical, 20)
                    } else {
                        ForEach(viewModel.outgoingPlans) { plan in
                            NavigationLink {
                                PlanDetailView(plan: plan, viewModel: viewModel)
                            } label: {
                                OutgoingPlanCard(plan: plan, viewModel: viewModel)
                            }
                            .buttonStyle(.plain)
                        }
                    }
                }
                
                // Section 2: Events
                VStack(alignment: .leading, spacing: 12) {
                    Text("Events")
                        .font(.system(size: 15, weight: .semibold, design: .rounded))
                        .foregroundColor(Color(red: 0.50, green: 0.50, blue: 0.50))
                        .textCase(.uppercase)
                        .padding(.leading, 4)
                    
                    Text("Coming soon")
                        .font(.system(size: 15, weight: .regular, design: .rounded))
                        .foregroundColor(Color(red: 0.60, green: 0.60, blue: 0.60))
                        .padding()
                        .frame(maxWidth: .infinity)
                        .background(Color.white.opacity(0.5))
                        .cornerRadius(12)
                }
                
                // Section 3: Groups
                VStack(alignment: .leading, spacing: 12) {
                    Text("Groups")
                        .font(.system(size: 15, weight: .semibold, design: .rounded))
                        .foregroundColor(Color(red: 0.50, green: 0.50, blue: 0.50))
                        .textCase(.uppercase)
                        .padding(.leading, 4)
                    
                    Text("No group plans yet")
                        .font(.system(size: 15, weight: .regular, design: .rounded))
                        .foregroundColor(Color(red: 0.60, green: 0.60, blue: 0.60))
                        .padding()
                        .frame(maxWidth: .infinity)
                        .background(Color.white.opacity(0.5))
                        .cornerRadius(12)
                }
            }
            .padding(.horizontal, 20)
            .padding(.vertical, 12)
        }
        .scrollIndicators(.hidden)
    }

    // MARK: - Upcoming Plans View

    private var upcomingPlansView: some View {
        ScrollView {
            VStack(spacing: 20) {
                // Section 1: Individuals & Couples
                VStack(alignment: .leading, spacing: 12) {
                    Text("Individuals & Couples")
                        .font(.system(size: 15, weight: .semibold, design: .rounded))
                        .foregroundColor(Color(red: 0.50, green: 0.50, blue: 0.50))
                        .textCase(.uppercase)
                        .padding(.leading, 4)
                    
                    if viewModel.upcomingPlans.isEmpty {
                        emptyStateView(
                            icon: "calendar.badge.checkmark",
                            title: "No Upcoming Plans",
                            subtitle: "Confirmed hangouts will\nappear here"
                        )
                        .padding(.vertical, 20)
                    } else {
                        ForEach(viewModel.upcomingPlans) { plan in
                            NavigationLink {
                                PlanDetailView(plan: plan, viewModel: viewModel)
                            } label: {
                                UpcomingPlanCard(plan: plan, viewModel: viewModel)
                            }
                            .buttonStyle(.plain)
                        }
                    }
                }
                
                // Section 2: Events
                VStack(alignment: .leading, spacing: 12) {
                    Text("Events")
                        .font(.system(size: 15, weight: .semibold, design: .rounded))
                        .foregroundColor(Color(red: 0.50, green: 0.50, blue: 0.50))
                        .textCase(.uppercase)
                        .padding(.leading, 4)
                    
                    Text("Coming soon")
                        .font(.system(size: 15, weight: .regular, design: .rounded))
                        .foregroundColor(Color(red: 0.60, green: 0.60, blue: 0.60))
                        .padding()
                        .frame(maxWidth: .infinity)
                        .background(Color.white.opacity(0.5))
                        .cornerRadius(12)
                }
                
                // Section 3: Groups
                VStack(alignment: .leading, spacing: 12) {
                    Text("Groups")
                        .font(.system(size: 15, weight: .semibold, design: .rounded))
                        .foregroundColor(Color(red: 0.50, green: 0.50, blue: 0.50))
                        .textCase(.uppercase)
                        .padding(.leading, 4)
                    
                    Text("No group plans yet")
                        .font(.system(size: 15, weight: .regular, design: .rounded))
                        .foregroundColor(Color(red: 0.60, green: 0.60, blue: 0.60))
                        .padding()
                        .frame(maxWidth: .infinity)
                        .background(Color.white.opacity(0.5))
                        .cornerRadius(12)
                }
            }
            .padding(.horizontal, 20)
            .padding(.vertical, 12)
        }
        .scrollIndicators(.hidden)
    }

    // MARK: - Empty State

    private func emptyStateView(icon: String, title: String, subtitle: String) -> some View {
        VStack(spacing: 16) {
            ZStack {
                Circle()
                    .fill(Color.appPrimary.opacity(0.15))
                    .frame(width: 100, height: 100)

                Image(systemName: icon)
                    .font(.system(size: 44))
                    .foregroundColor(Color.appPrimary.opacity(0.6))
            }

            Text(title)
                .font(.system(size: 20, weight: .semibold, design: .rounded))
                .foregroundColor(Color(red: 0.40, green: 0.40, blue: 0.40))

            Text(subtitle)
                .font(.system(size: 15, weight: .regular, design: .rounded))
                .foregroundColor(Color(red: 0.55, green: 0.55, blue: 0.55))
                .multilineTextAlignment(.center)
                .lineSpacing(4)
        }
    }
}

// MARK: - Plan Cards

struct IncomingPlanCard: View {
    let plan: Plan
    @Bindable var viewModel: PlansViewModel
    let isUnread: Bool
    @State private var otherUser: FirebaseUser?

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack {
                VStack(alignment: .leading, spacing: 4) {
                    HStack(spacing: 6) {
                        Text(otherUser?.displayName ?? "Loading...")
                            .font(.system(size: 18, weight: .bold, design: .rounded))
                            .foregroundColor(Color.appNavy)

                        // Unread dot
                        if isUnread {
                            Circle()
                                .fill(Color(red: 0.85, green: 0.45, blue: 0.40))
                                .frame(width: 8, height: 8)
                        }
                    }

                    Text("wants to hang out!")
                        .font(.system(size: 14, weight: .medium, design: .rounded))
                        .foregroundColor(Color(red: 0.50, green: 0.50, blue: 0.50))
                }

                Spacer()
                statusBadge
            }

            HStack(spacing: 8) {
                Image(systemName: "figure.run")
                    .foregroundColor(Color.appPrimary)
                Text(plan.activity.name)
                    .font(.system(size: 15, weight: .semibold, design: .rounded))
                    .foregroundColor(Color(red: 0.40, green: 0.40, blue: 0.40))
            }

            if let location = plan.location {
                HStack(spacing: 8) {
                    Image(systemName: "mappin.circle.fill")
                        .foregroundColor(Color.appPrimary)
                    Text(location)
                        .font(.system(size: 14, weight: .medium, design: .rounded))
                        .foregroundColor(Color(red: 0.50, green: 0.50, blue: 0.50))
                }
            }

            Divider()
                .background(Color.appPrimary.opacity(0.3))

            Text("Proposed Dates:")
                .font(.system(size: 13, weight: .semibold, design: .rounded))
                .foregroundColor(Color.appPrimary)

            VStack(spacing: 6) {
                ForEach(plan.proposedDates.prefix(3), id: \.self) { date in
                    HStack {
                        Image(systemName: "calendar")
                            .font(.system(size: 12))
                            .foregroundColor(Color(red: 0.50, green: 0.50, blue: 0.50))
                        Text(date.formatted(date: .abbreviated, time: .shortened))
                            .font(.system(size: 14, weight: .medium, design: .rounded))
                            .foregroundColor(Color(red: 0.40, green: 0.40, blue: 0.40))
                        Spacer()
                    }
                }
            }
        }
        .padding(16)
        .background(Color.white.opacity(isUnread ? 0.95 : 0.8))
        .cornerRadius(16)
        .shadow(color: .black.opacity(isUnread ? 0.08 : 0.05), radius: 8, x: 0, y: 4)
        .task {
            otherUser = await viewModel.fetchUser(userID: plan.proposerID)
        }
    }

    private var statusBadge: some View {
        HStack(spacing: 4) {
            Image(systemName: plan.status.icon)
                .font(.system(size: 12))
            Text(plan.status == .counterProposed ? "Counter" : "New")
                .font(.system(size: 12, weight: .semibold, design: .rounded))
        }
        .foregroundColor(.white)
        .padding(.horizontal, 12)
        .padding(.vertical, 6)
        .background(
            plan.status == .counterProposed ? Color.orange : Color.appPrimary
        )
        .cornerRadius(12)
    }
}

struct OutgoingPlanCard: View {
    let plan: Plan
    @Bindable var viewModel: PlansViewModel
    @State private var otherUser: FirebaseUser?

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack {
                VStack(alignment: .leading, spacing: 4) {
                    Text("To: \(otherUser?.displayName ?? "Loading...")")
                        .font(.system(size: 18, weight: .bold, design: .rounded))
                        .foregroundColor(Color.appNavy)
                    Text(plan.activity.name)
                        .font(.system(size: 14, weight: .medium, design: .rounded))
                        .foregroundColor(Color(red: 0.50, green: 0.50, blue: 0.50))
                }

                Spacer()

                VStack(spacing: 2) {
                    Image(systemName: plan.status.icon)
                        .font(.system(size: 20))
                        .foregroundColor(plan.status == .counterProposed ? .orange : Color.appPrimary)
                    Text(plan.status == .counterProposed ? "Counter" : "Sent")
                        .font(.system(size: 11, weight: .medium, design: .rounded))
                        .foregroundColor(Color(red: 0.50, green: 0.50, blue: 0.50))
                }
            }

            Text("Sent \(plan.createdAt.formatted(date: .abbreviated, time: .omitted))")
                .font(.system(size: 12, weight: .regular, design: .rounded))
                .foregroundColor(Color(red: 0.60, green: 0.60, blue: 0.60))
        }
        .padding(16)
        .background(Color.white.opacity(0.8))
        .cornerRadius(16)
        .shadow(color: .black.opacity(0.05), radius: 8, x: 0, y: 4)
        .task {
            otherUser = await viewModel.fetchUser(userID: plan.receiverID)
        }
    }
}

struct UpcomingPlanCard: View {
    let plan: Plan
    @Bindable var viewModel: PlansViewModel
    @State private var otherUser: FirebaseUser?

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            if let confirmedDate = plan.confirmedDate {
                HStack {
                    VStack(alignment: .leading, spacing: 2) {
                        Text(confirmedDate.formatted(.dateTime.month(.wide).day()))
                            .font(.system(size: 22, weight: .bold, design: .rounded))
                            .foregroundColor(Color.appNavy)
                        Text(confirmedDate.formatted(.dateTime.weekday(.wide)))
                            .font(.system(size: 14, weight: .medium, design: .rounded))
                            .foregroundColor(Color(red: 0.50, green: 0.50, blue: 0.50))
                    }

                    Spacer()

                    Text(confirmedDate.formatted(date: .omitted, time: .shortened))
                        .font(.system(size: 16, weight: .semibold, design: .rounded))
                        .foregroundColor(Color.appPrimary)
                        .padding(.horizontal, 12)
                        .padding(.vertical, 8)
                        .background(Color.appPrimary.opacity(0.2))
                        .cornerRadius(10)
                }
            }

            Divider()
                .background(Color.appPrimary.opacity(0.3))

            HStack(spacing: 12) {
                Image(systemName: "figure.run")
                    .font(.system(size: 20))
                    .foregroundColor(Color.appPrimary)

                VStack(alignment: .leading, spacing: 2) {
                    Text(plan.activity.name)
                        .font(.system(size: 16, weight: .semibold, design: .rounded))
                        .foregroundColor(Color.appNavy)
                    Text("with \(otherUser?.displayName ?? "...")")
                        .font(.system(size: 14, weight: .medium, design: .rounded))
                        .foregroundColor(Color(red: 0.50, green: 0.50, blue: 0.50))
                }
            }

            if let location = plan.location {
                HStack(spacing: 8) {
                    Image(systemName: "mappin.circle.fill")
                        .foregroundColor(Color.appPrimary)
                    Text(location)
                        .font(.system(size: 14, weight: .medium, design: .rounded))
                        .foregroundColor(Color(red: 0.50, green: 0.50, blue: 0.50))
                }
            }
        }
        .padding(16)
        .background(
            LinearGradient(
                colors: [
                    Color.white.opacity(0.9),
                    Color.appPrimary.opacity(0.1)
                ],
                startPoint: .topLeading,
                endPoint: .bottomTrailing
            )
        )
        .cornerRadius(16)
        .shadow(color: Color.appPrimary.opacity(0.2), radius: 10, x: 0, y: 5)
        .task {
            let otherUserID = plan.otherUserID(for: FirebaseAuthService.shared.currentUserID ?? "")
            otherUser = await viewModel.fetchUser(userID: otherUserID)
        }
    }
}

#Preview {
    PlansView()
}
