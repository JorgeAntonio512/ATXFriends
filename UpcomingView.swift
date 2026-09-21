//
//  UpcomingView.swift
//  Avenue3
//

import SwiftUI

/// Upcoming tab: every active group plan the current user hosts or is invited to, with a
/// future date, soonest first. Invitees currently discover invites by opening this tab —
/// there's no push notification yet (that's phase 2).
struct UpcomingView: View {
    @State private var viewModel = GroupPlansViewModel()
    @State private var showComposer = false
    @State private var selectedPlan: GroupPlan?

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

                if viewModel.isLoading {
                    VStack(spacing: 16) {
                        ProgressView()
                            .tint(Color.appPrimary)
                            .scaleEffect(1.2)
                        Text("Loading plans…")
                            .font(.system(size: 16, weight: .medium, design: .rounded))
                            .foregroundColor(Color(red: 0.50, green: 0.50, blue: 0.50))
                    }
                } else if viewModel.upcomingPlans.isEmpty {
                    emptyState
                } else {
                    ScrollView {
                        LazyVStack(spacing: 14) {
                            ForEach(viewModel.upcomingPlans) { plan in
                                GroupPlanCard(plan: plan, viewModel: viewModel)
                                    .onTapGesture {
                                        selectedPlan = plan
                                    }
                            }
                        }
                        .padding(.horizontal, 20)
                        .padding(.vertical, 16)
                    }
                    .scrollIndicators(.hidden)
                }
            }
            .navigationTitle("Upcoming")
            .navigationBarTitleDisplayMode(.large)
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button {
                        showComposer = true
                    } label: {
                        Image(systemName: "plus")
                            .foregroundColor(Color.appPrimary)
                    }
                }
            }
            .sheet(isPresented: $showComposer) {
                ProposePlanSheet()
            }
            .sheet(item: $selectedPlan) { plan in
                GroupPlanDetailView(plan: plan, viewModel: viewModel)
            }
            .alert("Something went wrong", isPresented: Binding(
                get: { viewModel.errorMessage != nil },
                set: { if !$0 { viewModel.errorMessage = nil } }
            )) {
                Button("OK") { viewModel.errorMessage = nil }
            } message: {
                Text(viewModel.errorMessage ?? "")
            }
        }
        .onAppear { viewModel.startListening() }
        .onDisappear { viewModel.stopListening() }
    }

    private var emptyState: some View {
        VStack(spacing: 20) {
            ZStack {
                Circle()
                    .fill(Color.appPrimary.opacity(0.15))
                    .frame(width: 110, height: 110)
                Image(systemName: "calendar.badge.clock")
                    .font(.system(size: 48))
                    .foregroundColor(Color.appPrimary.opacity(0.7))
            }
            VStack(spacing: 10) {
                Text("Nothing planned yet")
                    .font(.system(size: 22, weight: .semibold, design: .rounded))
                    .foregroundColor(Color.appNavy)

                Text("Tap + to invite some friends.")
                    .font(.system(size: 15, weight: .regular, design: .rounded))
                    .foregroundColor(Color(red: 0.55, green: 0.55, blue: 0.55))
                    .multilineTextAlignment(.center)
            }
        }
        .padding(.horizontal, 40)
    }
}

// MARK: - Group Plan Card

struct GroupPlanCard: View {
    let plan: GroupPlan
    let viewModel: GroupPlansViewModel

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            // Row 1: activity + date/time badge
            HStack(alignment: .top) {
                Text(plan.activity.name)
                    .font(.system(size: 18, weight: .bold, design: .rounded))
                    .foregroundColor(Color.appNavy)
                Spacer()
                Text(plan.date.formatted(date: .abbreviated, time: .shortened))
                    .font(.system(size: 13, weight: .semibold, design: .rounded))
                    .foregroundColor(.white)
                    .padding(.horizontal, 10)
                    .padding(.vertical, 5)
                    .background(Color.appPrimary)
                    .cornerRadius(10)
            }

            // Row 2: host name
            HStack(spacing: 5) {
                Image(systemName: "person.circle.fill")
                    .font(.system(size: 13))
                    .foregroundColor(Color.appPrimary)
                Text("Hosted by \(viewModel.hostName(for: plan))")
                    .font(.system(size: 13, weight: .medium, design: .rounded))
                    .foregroundColor(Color(red: 0.40, green: 0.40, blue: 0.40))
            }

            // Row 3: place
            if let location = plan.location, !location.isEmpty {
                HStack(spacing: 5) {
                    Image(systemName: "mappin.circle.fill")
                        .font(.system(size: 12))
                        .foregroundColor(Color.appPrimary)
                    Text(location)
                        .font(.system(size: 13, weight: .medium, design: .rounded))
                        .foregroundColor(Color(red: 0.40, green: 0.40, blue: 0.40))
                        .lineLimit(1)
                }
            }

            // Row 4: going count + my status
            HStack {
                Text("\(plan.goingCount) going")
                    .font(.system(size: 13, weight: .medium, design: .rounded))
                    .foregroundColor(Color(red: 0.55, green: 0.55, blue: 0.55))
                Spacer()
                Text(viewModel.myStatusText(for: plan))
                    .font(.system(size: 13, weight: .semibold, design: .rounded))
                    .foregroundColor(Color.appPrimary)
            }
        }
        .padding(16)
        .background(Color.white.opacity(0.85))
        .cornerRadius(16)
        .shadow(color: .black.opacity(0.05), radius: 8, x: 0, y: 4)
    }
}
