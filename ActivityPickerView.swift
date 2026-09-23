//
//  ActivityPickerView.swift
//  Avenue3
//
//  Created by George Anthony Pazdral II on 5/6/26.
//

import SwiftUI

/// Step 3: Search and select 3 Main activities, plus up to 7 Extras (10 total max)
struct ActivityPickerView: View {
    @Bindable var viewModel: ProfileViewModel
    let onNext: () -> Void
    let onBack: () -> Void
    
    @State private var searchText = ""
    @State private var isAddingActivity = false
    @FocusState private var isSearchFocused: Bool
    @State private var showErrorAlert = false
    
    var canContinue: Bool {
        viewModel.selectedActivities.count >= 3
    }

    var mainActivities: [Activity] {
        viewModel.selectedActivities.filter { $0.isPrimary }
    }

    var extraActivities: [Activity] {
        viewModel.selectedActivities.filter { !$0.isPrimary }
    }
    
    var filteredActivities: [Activity] {
        if searchText.isEmpty {
            return [] // Return empty array when search is empty
        }
        return viewModel.allActivities.filter { activity in
            activity.name.localizedCaseInsensitiveContains(searchText)
        }
    }
    
    /// Check if search text matches an existing activity exactly
    var searchMatchesExactActivity: Bool {
        guard !searchText.isEmpty else { return false }
        return viewModel.allActivities.contains { activity in
            activity.name.localizedCaseInsensitiveCompare(searchText.trimmingCharacters(in: .whitespaces)) == .orderedSame
        }
    }
    
    /// Check if we should show the "Add activity" option
    var shouldShowAddOption: Bool {
        !searchText.trimmingCharacters(in: .whitespaces).isEmpty && 
        !searchMatchesExactActivity &&
        filteredActivities.isEmpty
    }
    
    var body: some View {
        VStack(spacing: 0) {
            ScrollView {
                VStack(spacing: 24) {
                    // Header
                    VStack(spacing: 12) {
                        Text("Your Activities")
                            .font(.system(size: 32, weight: .bold, design: .rounded))
                            .foregroundColor(Color.appNavy)
                            .multilineTextAlignment(.center)

                        Text("Pick 3 Main activities you love,\nplus up to 7 Extras.")
                            .font(.system(size: 17, weight: .regular, design: .rounded))
                            .foregroundColor(Color(red: 0.50, green: 0.50, blue: 0.50))
                            .multilineTextAlignment(.center)
                            .lineSpacing(4)
                    }
                    .padding(.top, 20)
                    .padding(.horizontal, 40)

                    // Selected activities, grouped Main / Extra
                    if !viewModel.selectedActivities.isEmpty {
                        VStack(alignment: .leading, spacing: 16) {
                            if !mainActivities.isEmpty {
                                VStack(alignment: .leading, spacing: 12) {
                                    Text("Main (\(mainActivities.count)/3)")
                                        .font(.system(size: 14, weight: .semibold, design: .rounded))
                                        .foregroundColor(Color(red: 0.50, green: 0.50, blue: 0.50))
                                        .padding(.horizontal, 4)

                                    FlowLayout(spacing: 8) {
                                        ForEach(mainActivities, id: \.id) { activity in
                                            SelectedActivityChip(
                                                activity: activity,
                                                onRemove: { viewModel.deselectActivity(activity) },
                                                onToggleMain: { viewModel.toggleMain(for: activity) }
                                            )
                                        }
                                    }
                                }
                            }

                            if !extraActivities.isEmpty {
                                VStack(alignment: .leading, spacing: 12) {
                                    Text("Extras (\(extraActivities.count)/7)")
                                        .font(.system(size: 14, weight: .semibold, design: .rounded))
                                        .foregroundColor(Color(red: 0.50, green: 0.50, blue: 0.50))
                                        .padding(.horizontal, 4)

                                    FlowLayout(spacing: 8) {
                                        ForEach(extraActivities, id: \.id) { activity in
                                            SelectedActivityChip(
                                                activity: activity,
                                                onRemove: { viewModel.deselectActivity(activity) },
                                                onToggleMain: { viewModel.toggleMain(for: activity) }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        .padding(.horizontal, 32)
                    }
                    
                    // Search bar with inline add
                    VStack(spacing: 0) {
                        HStack(spacing: 12) {
                            Image(systemName: "magnifyingglass")
                                .foregroundColor(Color.appPrimary)
                            
                            TextField("Search activities...", text: $searchText)
                                .font(.system(size: 16, weight: .regular, design: .rounded))
                                .focused($isSearchFocused)
                                .submitLabel(.done)
                                .onSubmit {
                                    // Creating a new activity requires picking a category from
                                    // the "Add" menu below — return just dismisses the keyboard.
                                    isSearchFocused = false
                                }
                            
                            if !searchText.isEmpty {
                                Button {
                                    searchText = ""
                                } label: {
                                    Image(systemName: "xmark.circle.fill")
                                        .foregroundColor(Color(red: 0.50, green: 0.50, blue: 0.50))
                                }
                            }
                        }
                        .padding()
                        .background(Color.white.opacity(0.7))
                        .cornerRadius(12)
                        
                        // Inline "Add new activity" option — picking a category is required
                        if shouldShowAddOption {
                            Menu {
                                ForEach(ActivityCategory.allCases, id: \.self) { category in
                                    Button(category.displayName) {
                                        addActivityFromSearch(category: category)
                                    }
                                }
                            } label: {
                                HStack(spacing: 12) {
                                    Image(systemName: "plus.circle.fill")
                                        .font(.system(size: 20))
                                        .foregroundColor(Color.appPrimary)

                                    VStack(alignment: .leading, spacing: 2) {
                                        Text("Add \"\(searchText.trimmingCharacters(in: .whitespaces))\"")
                                            .font(.system(size: 15, weight: .semibold, design: .rounded))
                                            .foregroundColor(Color(red: 0.35, green: 0.35, blue: 0.35))

                                        Text("Choose a category to create it")
                                            .font(.system(size: 13, weight: .regular, design: .rounded))
                                            .foregroundColor(Color(red: 0.55, green: 0.55, blue: 0.55))
                                    }

                                    Spacer()

                                    Image(systemName: "arrow.right.circle")
                                        .font(.system(size: 18))
                                        .foregroundColor(Color.appPrimary)
                                }
                                .padding()
                                .background(Color.appPrimary.opacity(0.15))
                                .cornerRadius(12)
                            }
                            .padding(.top, 8)
                            .transition(.move(edge: .top).combined(with: .opacity))
                        }
                    }
                    .padding(.horizontal, 32)
                    .animation(.spring(response: 0.3, dampingFraction: 0.8), value: shouldShowAddOption)
                    
                    // Activity grid or empty/placeholder state
                    if viewModel.isLoading {
                        // Loading state
                        VStack(spacing: 16) {
                            ProgressView()
                                .scaleEffect(1.5)
                                .tint(Color.appPrimary)
                            
                            Text("Loading activities...")
                                .font(.system(size: 18, weight: .semibold, design: .rounded))
                                .foregroundColor(Color(red: 0.35, green: 0.35, blue: 0.35))
                        }
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 60)
                    } else if searchText.isEmpty {
                        // Placeholder when search is empty
                        VStack(spacing: 16) {
                            Image(systemName: "magnifyingglass")
                                .font(.system(size: 48))
                                .foregroundColor(Color.appPrimary.opacity(0.5))
                            
                            Text("Start typing to find activities...")
                                .font(.system(size: 18, weight: .semibold, design: .rounded))
                                .foregroundColor(Color(red: 0.35, green: 0.35, blue: 0.35))
                            
                            Text("Search for activities like hiking, cooking, gaming, etc.")
                                .font(.system(size: 15, weight: .regular, design: .rounded))
                                .foregroundColor(Color(red: 0.55, green: 0.55, blue: 0.55))
                                .multilineTextAlignment(.center)
                                .padding(.horizontal, 40)
                        }
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 60)
                    } else if filteredActivities.isEmpty && !shouldShowAddOption {
                        VStack(spacing: 16) {
                            Image(systemName: "magnifyingglass")
                                .font(.system(size: 48))
                                .foregroundColor(Color.appPrimary.opacity(0.5))
                            
                            Text("No activities found")
                                .font(.system(size: 18, weight: .semibold, design: .rounded))
                                .foregroundColor(Color(red: 0.35, green: 0.35, blue: 0.35))
                            
                            Text("Try a different search term")
                                .font(.system(size: 15, weight: .regular, design: .rounded))
                                .foregroundColor(Color(red: 0.55, green: 0.55, blue: 0.55))
                        }
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 60)
                    } else if !filteredActivities.isEmpty {
                        LazyVGrid(
                            columns: [
                                GridItem(.flexible(), spacing: 12),
                                GridItem(.flexible(), spacing: 12)
                            ],
                            spacing: 12
                        ) {
                            ForEach(filteredActivities, id: \.id) { activity in
                                ActivityCard(
                                    activity: activity,
                                    isSelected: viewModel.selectedActivities.contains(where: { $0.id == activity.id }),
                                    onTap: {
                                        if viewModel.selectedActivities.contains(where: { $0.id == activity.id }) {
                                            viewModel.deselectActivity(activity)
                                        } else {
                                            viewModel.selectActivity(activity)
                                        }
                                    }
                                )
                            }
                        }
                        .padding(.horizontal, 32)
                    }
                    
                    // Bottom spacing
                    Spacer()
                        .frame(height: 100)
                }
            }
            .scrollIndicators(.hidden)
            
            // Bottom buttons
            VStack(spacing: 12) {
                // Continue button
                Button {
                    onNext()
                } label: {
                    Text("Continue")
                        .font(.system(size: 18, weight: .semibold, design: .rounded))
                        .foregroundColor(.white)
                        .frame(maxWidth: .infinity)
                        .frame(height: 56)
                        .background(
                            canContinue ?
                            LinearGradient(
                                colors: [
                                    Color.appPrimary,
                                    Color.appPrimary
                                ],
                                startPoint: .leading,
                                endPoint: .trailing
                            ) :
                            LinearGradient(
                                colors: [Color.gray.opacity(0.3), Color.gray.opacity(0.3)],
                                startPoint: .leading,
                                endPoint: .trailing
                            )
                        )
                        .cornerRadius(16)
                        .shadow(
                            color: canContinue ?
                            Color.appPrimary.opacity(0.3) :
                            Color.clear,
                            radius: 12,
                            x: 0,
                            y: 6
                        )
                }
                .disabled(!canContinue)
                
                // Back button
                Button {
                    onBack()
                } label: {
                    Text("Back")
                        .font(.system(size: 16, weight: .medium, design: .rounded))
                        .foregroundColor(Color.appPrimary)
                }
            }
            .padding(.horizontal, 32)
            .padding(.vertical, 20)
            .background(
                LinearGradient(
                    colors: [
                        Color.white.opacity(0.95),
                        Color.white.opacity(0.95)
                    ],
                    startPoint: .top,
                    endPoint: .bottom
                )
                .shadow(color: .black.opacity(0.1), radius: 10, x: 0, y: -5)
            )
        }
        .task {
            // Ensure activities are loaded when this view appears
            // This handles cases where the parent's .task might not have completed yet
            if viewModel.allActivities.isEmpty {
                print("⚠️ ActivityPickerView: allActivities is empty, reloading...")
                await viewModel.loadActivities()
                
                // Show error if loading failed
                if viewModel.allActivities.isEmpty && viewModel.errorMessage != nil {
                    showErrorAlert = true
                }
            } else {
                print("✅ ActivityPickerView: \(viewModel.allActivities.count) activities already loaded")
            }
        }
        .alert("Error Loading Activities", isPresented: $showErrorAlert) {
            Button("Retry") {
                Task {
                    await viewModel.loadActivities()
                }
            }
            Button("Cancel", role: .cancel) { }
        } message: {
            Text(viewModel.errorMessage ?? "Failed to load activities. Please check your connection and try again.")
        }
    }
    
    // MARK: - Helper Methods
    
    /// Adds a new activity from the search text, filed under the chosen category
    private func addActivityFromSearch(category: ActivityCategory) {
        let activityName = searchText.trimmingCharacters(in: .whitespaces)

        guard !activityName.isEmpty else { return }

        isAddingActivity = true

        Task {
            let success = await viewModel.addCustomActivity(name: activityName, category: category)

            await MainActor.run {
                isAddingActivity = false

                if success {
                    searchText = ""
                    isSearchFocused = false
                }
            }
        }
    }
}

/// Activity selection card
struct ActivityCard: View {
    let activity: Activity
    let isSelected: Bool
    let onTap: () -> Void
    
    var body: some View {
        Button(action: onTap) {
            VStack(spacing: 8) {
                Text(activity.name)
                    .font(.system(size: 15, weight: .semibold, design: .rounded))
                    .foregroundColor(
                        isSelected ?
                        .white :
                        Color(red: 0.35, green: 0.35, blue: 0.35)
                    )
                    .multilineTextAlignment(.center)
                    .lineLimit(2)
                    .frame(height: 40)
            }
            .frame(maxWidth: .infinity)
            .padding(.vertical, 16)
            .padding(.horizontal, 12)
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
                    colors: [Color.white.opacity(0.7), Color.white.opacity(0.7)],
                    startPoint: .topLeading,
                    endPoint: .bottomTrailing
                )
            )
            .cornerRadius(12)
            .overlay(
                RoundedRectangle(cornerRadius: 12)
                    .stroke(
                        isSelected ?
                        Color.appPrimary :
                        Color.gray.opacity(0.2),
                        lineWidth: isSelected ? 2 : 1
                    )
            )
        }
        .buttonStyle(.plain)
        .animation(.spring(response: 0.3, dampingFraction: 0.7), value: isSelected)
    }
}

/// Selected activity chip (removable, with a star to toggle Main/Extra)
struct SelectedActivityChip: View {
    let activity: Activity
    let onRemove: () -> Void
    let onToggleMain: () -> Void

    var body: some View {
        HStack(spacing: 6) {
            Button(action: onToggleMain) {
                Image(systemName: activity.isPrimary ? "star.fill" : "star")
                    .font(.system(size: 14))
                    .foregroundColor(.white.opacity(activity.isPrimary ? 1.0 : 0.7))
            }

            Text(activity.name)
                .font(.system(size: 14, weight: .semibold, design: .rounded))
                .foregroundColor(.white)

            Button(action: onRemove) {
                Image(systemName: "xmark.circle.fill")
                    .font(.system(size: 16))
                    .foregroundColor(.white.opacity(0.8))
            }
        }
        .padding(.horizontal, 12)
        .padding(.vertical, 8)
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
        .cornerRadius(20)
    }
}

#Preview {
    ProfileSetupFlowView()
}
