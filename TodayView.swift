//
//  TodayView.swift
//  Avenue3
//

import SwiftUI

struct TodayView: View {
    @State private var viewModel = TodayViewModel()
    @State private var showCreatePlan = false

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
                    // Activity filter chips — only visible when there's something to filter
                    if !viewModel.availableActivities.isEmpty {
                        activityFilterBar
                            .padding(.horizontal, 20)
                            .padding(.top, 12)
                            .padding(.bottom, 4)
                    }

                    if viewModel.isLoading {
                        Spacer()
                        VStack(spacing: 16) {
                            ProgressView()
                                .tint(Color.appPrimary)
                                .scaleEffect(1.2)
                            Text("Loading plans…")
                                .font(.system(size: 16, weight: .medium, design: .rounded))
                                .foregroundColor(Color(red: 0.50, green: 0.50, blue: 0.50))
                        }
                        Spacer()
                    } else if viewModel.filteredPlans.isEmpty {
                        Spacer()
                        emptyState
                        Spacer()
                    } else {
                        ScrollView {
                            LazyVStack(spacing: 14) {
                                ForEach(viewModel.filteredPlans) { plan in
                                    TodayPlanCard(
                                        plan: plan,
                                        posterInfo: viewModel.posterInfo[plan.creatorID],
                                        isOwnPlan: plan.creatorID == viewModel.currentUserID,
                                        onClaim: {
                                            await viewModel.claimPlan(plan)
                                        }
                                    )
                                }
                            }
                            .padding(.horizontal, 20)
                            .padding(.vertical, 16)
                        }
                        .scrollIndicators(.hidden)
                        .refreshable { await viewModel.loadOpenPlans() }
                    }
                }
            }
            .navigationTitle("Today")
            .navigationBarTitleDisplayMode(.large)
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button {
                        showCreatePlan = true
                    } label: {
                        Image(systemName: "plus")
                            .foregroundColor(Color.appPrimary)
                    }
                }
            }
            .sheet(isPresented: $showCreatePlan) {
                ProposePlanSheet(todayViewModel: viewModel)
            }
            .alert("Something went wrong", isPresented: Binding(
                get: { viewModel.errorMessage != nil },
                set: { if !$0 { viewModel.errorMessage = nil } }
            )) {
                Button("OK") { viewModel.errorMessage = nil }
            } message: {
                Text(viewModel.errorMessage ?? "")
            }
            // Poster banner: slides down from the nav bar when someone claims their plan
            .overlay(alignment: .top) {
                if let message = viewModel.claimedPlanBanner {
                    claimedBanner(message: message)
                        .transition(.move(edge: .top).combined(with: .opacity))
                        .padding(.top, 8)
                }
            }
            .animation(.spring(response: 0.4, dampingFraction: 0.75), value: viewModel.claimedPlanBanner)
        }
        .task {
            await viewModel.loadOpenPlans()
            viewModel.setupListener()
        }
        .onDisappear {
            viewModel.removeListener()
        }
    }

    // MARK: - Claimed Banner

    private func claimedBanner(message: String) -> some View {
        HStack(spacing: 10) {
            Image(systemName: "person.fill.checkmark")
                .font(.system(size: 15, weight: .semibold))
            Text(message)
                .font(.system(size: 14, weight: .semibold, design: .rounded))
                .fixedSize(horizontal: false, vertical: true)
        }
        .foregroundColor(.white)
        .padding(.horizontal, 20)
        .padding(.vertical, 13)
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
        .cornerRadius(14)
        .shadow(color: Color.appPrimary.opacity(0.30), radius: 8, x: 0, y: 4)
        .padding(.horizontal, 20)
        .onAppear {
            Task {
                try? await Task.sleep(nanoseconds: 3_500_000_000)
                withAnimation { viewModel.claimedPlanBanner = nil }
            }
        }
    }

    // MARK: - Activity Filter Bar

    private var activityFilterBar: some View {
        ScrollView(.horizontal) {
            HStack(spacing: 8) {
                filterChip(label: "All", isSelected: viewModel.activityFilter == nil) {
                    viewModel.activityFilter = nil
                }
                ForEach(viewModel.availableActivities, id: \.self) { name in
                    filterChip(label: name, isSelected: viewModel.activityFilter == name) {
                        viewModel.activityFilter = (viewModel.activityFilter == name) ? nil : name
                    }
                }
            }
            .padding(.vertical, 4)
        }
        .scrollIndicators(.hidden)
    }

    private func filterChip(label: String, isSelected: Bool, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Text(label)
                .font(.system(size: 13, weight: .semibold, design: .rounded))
                .foregroundColor(isSelected ? .white : Color.appPrimary)
                .padding(.horizontal, 14)
                .padding(.vertical, 7)
                .background(
                    isSelected
                        ? Color.appPrimary
                        : Color.white.opacity(0.65)
                )
                .cornerRadius(20)
        }
        .buttonStyle(.plain)
        .animation(.spring(response: 0.25), value: isSelected)
    }

    // MARK: - Empty State

    private var emptyState: some View {
        VStack(spacing: 20) {
            ZStack {
                Circle()
                    .fill(Color.appPrimary.opacity(0.15))
                    .frame(width: 110, height: 110)
                Image(systemName: "calendar.circle")
                    .font(.system(size: 52))
                    .foregroundColor(Color.appPrimary.opacity(0.7))
            }
            VStack(spacing: 10) {
                Text(
                    viewModel.activityFilter.map { "No \($0) plans today" }
                        ?? "Nothing happening yet"
                )
                .font(.system(size: 22, weight: .semibold, design: .rounded))
                .foregroundColor(Color.appNavy)
                .multilineTextAlignment(.center)

                Text("Tap + to post a plan and see who's\nup for something today.")
                    .font(.system(size: 15, weight: .regular, design: .rounded))
                    .foregroundColor(Color(red: 0.55, green: 0.55, blue: 0.55))
                    .multilineTextAlignment(.center)
                    .lineSpacing(3)
            }
        }
        .padding(.horizontal, 40)
    }
}

// MARK: - Today Plan Card

struct TodayPlanCard: View {
    let plan: TodayPlan
    let posterInfo: TodayViewModel.PosterInfo?
    let isOwnPlan: Bool
    /// Async closure; returns true if the claim succeeded, false if it failed.
    let onClaim: () async -> Bool

    @State private var isClaiming = false

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {

            // Row 1: activity name + time badge
            HStack(alignment: .top) {
                Text(plan.activity.name)
                    .font(.system(size: 18, weight: .bold, design: .rounded))
                    .foregroundColor(Color.appNavy)
                Spacer()
                Text(plan.scheduledTime.formatted(date: .omitted, time: .shortened))
                    .font(.system(size: 13, weight: .semibold, design: .rounded))
                    .foregroundColor(.white)
                    .padding(.horizontal, 10)
                    .padding(.vertical, 5)
                    .background(Color.appPrimary)
                    .cornerRadius(10)
            }

            // Row 2: poster name + show-up meter
            HStack(spacing: 5) {
                Image(systemName: "person.circle.fill")
                    .font(.system(size: 13))
                    .foregroundColor(Color.appPrimary)
                Text(posterInfo?.displayName ?? "…")
                    .font(.system(size: 13, weight: .medium, design: .rounded))
                    .foregroundColor(Color(red: 0.40, green: 0.40, blue: 0.40))
                Text("·")
                    .foregroundColor(Color(red: 0.70, green: 0.70, blue: 0.70))
                Image(systemName: "checkmark.seal.fill")
                    .font(.system(size: 11))
                    .foregroundColor(Color.appPrimary)
                Text(posterInfo?.showUpMeter ?? "New")
                    .font(.system(size: 12, weight: .semibold, design: .rounded))
                    .foregroundColor(Color.appPrimary)
            }

            // Row 3: location
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

            // Row 4: optional note
            if let note = plan.note, !note.isEmpty {
                Text(note)
                    .font(.system(size: 13, weight: .regular, design: .rounded))
                    .foregroundColor(Color(red: 0.55, green: 0.55, blue: 0.55))
                    .italic()
                    .lineLimit(2)
            }

            // Row 4: action
            HStack {
                Spacer()
                if isOwnPlan {
                    Text("Your post")
                        .font(.system(size: 13, weight: .medium, design: .rounded))
                        .foregroundColor(Color(red: 0.65, green: 0.65, blue: 0.65))
                } else {
                    Button {
                        guard !isClaiming else { return }
                        isClaiming = true
                        Task {
                            let success = await onClaim()
                            // On failure reset so the user can try another plan or see the error.
                            // On success the card disappears from the feed via the listener.
                            if !success {
                                isClaiming = false
                            }
                        }
                    } label: {
                        HStack(spacing: 6) {
                            if isClaiming {
                                ProgressView()
                                    .tint(.white)
                                    .scaleEffect(0.75)
                                    .frame(width: 14, height: 14)
                            } else {
                                Image(systemName: "hand.thumbsup.fill")
                                    .font(.system(size: 13))
                            }
                            Text(isClaiming ? "Joining…" : "I'm in")
                                .font(.system(size: 15, weight: .semibold, design: .rounded))
                        }
                        .foregroundColor(.white)
                        .padding(.horizontal, 20)
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
                        .cornerRadius(12)
                        .shadow(
                            color: Color.appPrimary.opacity(0.30),
                            radius: 6, x: 0, y: 3
                        )
                    }
                    .buttonStyle(.plain)
                    .disabled(isClaiming)
                }
            }
        }
        .padding(16)
        .background(Color.white.opacity(0.85))
        .cornerRadius(16)
        .shadow(color: .black.opacity(0.05), radius: 8, x: 0, y: 4)
    }
}
