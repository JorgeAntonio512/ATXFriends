//
//  ActivitiesSettingsView.swift
//  Avenue3
//
//  Created by George Anthony Pazdral II on 5/6/26.
//

import SwiftUI

/// Settings view for managing user's activities: 3 Main, plus up to 7 Extras (10 total max)
struct ActivitiesSettingsView: View {
    @Bindable var viewModel: ProfileViewModel
    @Environment(\.dismiss) private var dismiss

    @State private var searchText = ""
    @State private var showAddActivity = false
    @State private var newActivityName = ""
    @State private var isAddingActivity = false
    @State private var isSaving = false
    @FocusState private var isSearchFocused: Bool

    var filteredActivities: [Activity] {
        if searchText.isEmpty {
            return viewModel.allActivities.filter { activity in
                !viewModel.selectedActivities.contains(where: { $0.id == activity.id })
            }
        } else {
            return viewModel.allActivities.filter { activity in
                activity.name.localizedCaseInsensitiveContains(searchText) &&
                !viewModel.selectedActivities.contains(where: { $0.id == activity.id })
            }
        }
    }

    var hasExactMatch: Bool {
        let trimmedSearch = searchText.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmedSearch.isEmpty else { return false }

        return viewModel.allActivities.contains { activity in
            activity.name.localizedCaseInsensitiveCompare(trimmedSearch) == .orderedSame
        }
    }

    var canShowCustomActivityButton: Bool {
        let trimmedSearch = searchText.trimmingCharacters(in: .whitespacesAndNewlines)
        return !trimmedSearch.isEmpty && !hasExactMatch
    }

    var mainActivities: [Activity] {
        viewModel.selectedActivities.filter { $0.isPrimary }
    }

    var extraActivities: [Activity] {
        viewModel.selectedActivities.filter { !$0.isPrimary }
    }

    var canAddMore: Bool {
        viewModel.selectedActivities.count < 10
    }

    var hasValidSelection: Bool {
        (3...10).contains(viewModel.selectedActivities.count) && mainActivities.count == 3
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
            
            VStack(spacing: 0) {
                // Navigation warning banner
                if !hasValidSelection {
                    HStack(spacing: 12) {
                        Image(systemName: "exclamationmark.triangle.fill")
                            .font(.system(size: 20))
                            .foregroundColor(.orange)

                        VStack(alignment: .leading, spacing: 4) {
                            Text("Select 3 Main activities to continue")
                                .font(.system(size: 15, weight: .semibold, design: .rounded))
                                .foregroundColor(Color(red: 0.35, green: 0.35, blue: 0.35))

                            Text("\(mainActivities.count)/3 Main selected")
                                .font(.system(size: 13, weight: .regular, design: .rounded))
                                .foregroundColor(Color(red: 0.50, green: 0.50, blue: 0.50))
                        }

                        Spacer()
                    }
                    .padding()
                    .background(Color.orange.opacity(0.15))
                    .cornerRadius(12)
                    .padding(.horizontal, 16)
                    .padding(.top, 12)
                    .padding(.bottom, 8)
                }

                // Header
                VStack(spacing: 12) {
                    Text("Your Activities")
                        .font(.system(size: 28, weight: .bold, design: .rounded))
                        .foregroundColor(Color.appNavy)

                    Text("Select 3 Main activities, plus up to 7 Extras")
                        .font(.system(size: 16, weight: .regular, design: .rounded))
                        .foregroundColor(Color(red: 0.50, green: 0.50, blue: 0.50))
                }
                .padding(.top, 20)
                .padding(.bottom, 16)

                // Selected activities, grouped Main / Extra
                if !viewModel.selectedActivities.isEmpty {
                    VStack(alignment: .leading, spacing: 16) {
                        if !mainActivities.isEmpty {
                            VStack(alignment: .leading, spacing: 12) {
                                Text("Main (\(mainActivities.count)/3)")
                                    .font(.system(size: 14, weight: .semibold, design: .rounded))
                                    .foregroundColor(Color(red: 0.40, green: 0.40, blue: 0.40))
                                    .padding(.horizontal, 32)

                                VStack(spacing: 12) {
                                    ForEach(mainActivities) { activity in
                                        ActivityChip(
                                            activity: activity,
                                            isSelected: true,
                                            onToggleMain: { viewModel.toggleMain(for: activity) },
                                            onTap: {
                                                withAnimation(.spring(response: 0.3)) {
                                                    viewModel.deselectActivity(activity)
                                                }
                                                Task {
                                                    await saveChangesImmediately()
                                                }
                                            }
                                        )
                                    }
                                }
                                .padding(.horizontal, 32)
                            }
                        }

                        if !extraActivities.isEmpty {
                            VStack(alignment: .leading, spacing: 12) {
                                Text("Extras (\(extraActivities.count)/7)")
                                    .font(.system(size: 14, weight: .semibold, design: .rounded))
                                    .foregroundColor(Color(red: 0.40, green: 0.40, blue: 0.40))
                                    .padding(.horizontal, 32)

                                VStack(spacing: 12) {
                                    ForEach(extraActivities) { activity in
                                        ActivityChip(
                                            activity: activity,
                                            isSelected: true,
                                            onToggleMain: { viewModel.toggleMain(for: activity) },
                                            onTap: {
                                                withAnimation(.spring(response: 0.3)) {
                                                    viewModel.deselectActivity(activity)
                                                }
                                                Task {
                                                    await saveChangesImmediately()
                                                }
                                            }
                                        )
                                    }
                                }
                                .padding(.horizontal, 32)
                            }
                        }
                    }
                    .padding(.bottom, 16)
                }
                
                // Search bar
                HStack(spacing: 12) {
                    Image(systemName: "magnifyingglass")
                        .foregroundColor(Color.appPrimary)
                    
                    TextField("Search activities...", text: $searchText)
                        .font(.system(size: 16, weight: .regular, design: .rounded))
                        .foregroundStyle(.primary)
                        .tint(.primary)
                        .focused($isSearchFocused)
                        .autocorrectionDisabled()
                }
                .padding()
                .background(Color.white.opacity(0.9))
                .cornerRadius(12)
                .padding(.horizontal, 32)
                .padding(.bottom, 16)
                
                // Available activities
                ScrollView {
                    VStack(alignment: .leading, spacing: 16) {
                        if canAddMore {
                            Text("Available Activities")
                                .font(.system(size: 14, weight: .semibold, design: .rounded))
                                .foregroundColor(Color(red: 0.40, green: 0.40, blue: 0.40))
                                .padding(.horizontal, 32)
                            
                            LazyVStack(spacing: 12) {
                                ForEach(filteredActivities) { activity in
                                    ActivityRow(
                                        activity: activity,
                                        onTap: {
                                            withAnimation(.spring(response: 0.3)) {
                                                viewModel.selectActivity(activity)
                                                if viewModel.selectedActivities.count == 10 {
                                                    isSearchFocused = false
                                                }
                                            }
                                            Task {
                                                await saveChangesImmediately()
                                            }
                                        }
                                    )
                                }

                                // Add custom activity button - shows search text, requires a category
                                if canShowCustomActivityButton {
                                    Menu {
                                        ForEach(ActivityCategory.allCases, id: \.self) { category in
                                            Button(category.displayName) {
                                                addCustomActivityDirectly(category: category)
                                            }
                                        }
                                    } label: {
                                        HStack(spacing: 12) {
                                            Image(systemName: "plus.circle.fill")
                                                .font(.system(size: 24))
                                                .foregroundColor(Color.appPrimary)

                                            Text("Add '\(searchText.trimmingCharacters(in: .whitespacesAndNewlines))' — choose a category")
                                                .font(.system(size: 16, weight: .semibold, design: .rounded))
                                                .foregroundColor(Color.appPrimary)
                                                .lineLimit(2)

                                            Spacer()
                                        }
                                        .padding()
                                        .background(Color.appPrimary.opacity(0.15))
                                        .cornerRadius(12)
                                        .overlay(
                                            RoundedRectangle(cornerRadius: 12)
                                                .strokeBorder(
                                                    Color.appPrimary,
                                                    style: StrokeStyle(lineWidth: 2, dash: [6, 4])
                                                )
                                        )
                                    }
                                    .padding(.horizontal, 32)
                                    .disabled(isAddingActivity)
                                } else if !searchText.isEmpty && filteredActivities.isEmpty && !canShowCustomActivityButton {
                                    // No results and exact match exists (already added)
                                    VStack(spacing: 12) {
                                        Image(systemName: "checkmark.circle")
                                            .font(.system(size: 40))
                                            .foregroundColor(Color.appPrimary)
                                        
                                        Text("Activity already exists")
                                            .font(.system(size: 16, weight: .medium, design: .rounded))
                                            .foregroundColor(Color(red: 0.40, green: 0.40, blue: 0.40))
                                    }
                                    .padding(.vertical, 20)
                                } else if searchText.isEmpty {
                                    // Generic add button when no search
                                    Button {
                                        showAddActivity = true
                                    } label: {
                                        HStack(spacing: 12) {
                                            Image(systemName: "plus.circle.fill")
                                                .font(.system(size: 24))
                                                .foregroundColor(Color.appPrimary)
                                            
                                            Text("Add Custom Activity")
                                                .font(.system(size: 16, weight: .semibold, design: .rounded))
                                                .foregroundColor(Color.appPrimary)
                                            
                                            Spacer()
                                        }
                                        .padding()
                                        .background(Color.white.opacity(0.6))
                                        .cornerRadius(12)
                                    }
                                    .buttonStyle(.plain)
                                    .padding(.horizontal, 32)
                                }
                            }
                        } else {
                            VStack(spacing: 16) {
                                Image(systemName: "checkmark.circle.fill")
                                    .font(.system(size: 60))
                                    .foregroundColor(Color.appPrimary)

                                Text("You've selected the max of 10 activities!")
                                    .font(.system(size: 18, weight: .semibold, design: .rounded))
                                    .foregroundColor(Color.appNavy)

                                Text("Remove one to add a different activity")
                                    .font(.system(size: 15, weight: .regular, design: .rounded))
                                    .foregroundColor(Color(red: 0.50, green: 0.50, blue: 0.50))
                                    .multilineTextAlignment(.center)
                            }
                            .padding(.top, 40)
                            .padding(.horizontal, 40)
                        }
                    }
                    .padding(.bottom, 40)
                }
                .scrollIndicators(.hidden)
            }
        }
        .navigationTitle("Activities")
        .navigationBarTitleDisplayMode(.inline)
        .navigationBarBackButtonHidden(!hasValidSelection)
        .toolbar {
            // Custom back button that's disabled until 3 Mains and 3-10 total are selected
            ToolbarItem(placement: .navigationBarLeading) {
                if !hasValidSelection {
                    Button {
                        // Do nothing - disabled
                    } label: {
                        HStack(spacing: 4) {
                            Image(systemName: "chevron.left")
                                .font(.system(size: 17, weight: .semibold))
                            Text("Back")
                        }
                        .foregroundColor(Color.gray.opacity(0.5))
                    }
                    .disabled(true)
                }
            }
        }
        .interactiveDismissDisabled(!hasValidSelection)
        .task {
            await viewModel.loadActivities()
        }
        .sheet(isPresented: $showAddActivity) {
            AddCustomActivitySheet(
                viewModel: viewModel,
                activityName: $newActivityName,
                isAdding: $isAddingActivity,
                onAdd: {
                    Task {
                        await saveChangesImmediately()
                    }
                }
            )
            .presentationDetents([.medium])
            .presentationDragIndicator(.visible)
        }
        .overlay {
            if isSaving {
                ZStack {
                    Color.clear
                    
                    HStack(spacing: 12) {
                        ProgressView()
                            .tint(Color.appPrimary)
                        
                        Text("Saving...")
                            .font(.system(size: 15, weight: .medium, design: .rounded))
                            .foregroundColor(Color.appNavy)
                    }
                    .padding(.horizontal, 20)
                    .padding(.vertical, 12)
                    .background(Color.white)
                    .cornerRadius(12)
                    .shadow(radius: 8)
                }
                .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
                .padding(.top, 80)
            }
            
            if isAddingActivity {
                ZStack {
                    Color.black.opacity(0.3)
                        .ignoresSafeArea()
                    
                    HStack(spacing: 12) {
                        ProgressView()
                            .tint(Color.appPrimary)
                        
                        Text("Adding activity...")
                            .font(.system(size: 15, weight: .medium, design: .rounded))
                            .foregroundColor(Color.appNavy)
                    }
                    .padding(.horizontal, 20)
                    .padding(.vertical, 12)
                    .background(Color.white)
                    .cornerRadius(12)
                    .shadow(radius: 8)
                }
            }
        }
    }
    
    /// Saves changes to Firestore immediately
    private func saveChangesImmediately() async {
        guard viewModel.user != nil else { return }
        
        await MainActor.run {
            isSaving = true
        }
        
        // Update user document with new activities
        if var existingUser = viewModel.user {
            existingUser.activities = viewModel.selectedActivities
            existingUser.updatedAt = Date()
            
            do {
                try await FirestoreService.shared.updateUser(existingUser)
                viewModel.user = existingUser
                
                // Small delay to show saving indicator
                try? await Task.sleep(nanoseconds: 300_000_000) // 0.3 seconds
            } catch {
                print("❌ Failed to save activities: \(error.localizedDescription)")
            }
        }
        
        await MainActor.run {
            isSaving = false
        }
    }
    
    /// Adds custom activity directly from search text, filed under the chosen category
    private func addCustomActivityDirectly(category: ActivityCategory) {
        let trimmed = searchText.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return }

        isSearchFocused = false
        isAddingActivity = true

        Task {
            let success = await viewModel.addCustomActivity(name: trimmed, category: category)
            
            await MainActor.run {
                isAddingActivity = false
                
                if success {
                    // Clear search text
                    searchText = ""
                    
                    // Save changes immediately
                    Task {
                        await saveChangesImmediately()
                    }
                }
            }
        }
    }
    
    private func saveChanges() {
        Task {
            _ = await viewModel.saveProfile()
        }
    }
}

/// Activity chip for selected activities, with a star to toggle Main/Extra
struct ActivityChip: View {
    let activity: Activity
    let isSelected: Bool
    let onToggleMain: () -> Void
    let onTap: () -> Void

    var body: some View {
        Button(action: onTap) {
            HStack(spacing: 8) {
                Button(action: onToggleMain) {
                    Image(systemName: activity.isPrimary ? "star.fill" : "star")
                        .font(.system(size: 14))
                }
                .buttonStyle(.plain)

                Text(activity.name)
                    .font(.system(size: 15, weight: .semibold, design: .rounded))

                Spacer()

                if isSelected {
                    Image(systemName: "xmark.circle.fill")
                        .font(.system(size: 16))
                }
            }
            .foregroundColor(isSelected ? .white : Color.appPrimary)
            .padding(.horizontal, 16)
            .padding(.vertical, 12)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(
                isSelected ?
                LinearGradient(
                    colors: [
                        Color.appPrimary,
                        Color.appPrimary
                    ],
                    startPoint: .leading,
                    endPoint: .trailing
                ) :
                LinearGradient(
                    colors: [Color.white.opacity(0.6), Color.white.opacity(0.6)],
                    startPoint: .leading,
                    endPoint: .trailing
                )
            )
            .cornerRadius(12)
            .overlay(
                RoundedRectangle(cornerRadius: 12)
                    .stroke(
                        isSelected ? Color.clear : Color.appPrimary,
                        lineWidth: 1.5
                    )
            )
        }
        .buttonStyle(.plain)
    }
}

/// Activity row for available activities
struct ActivityRow: View {
    let activity: Activity
    let onTap: () -> Void
    
    var body: some View {
        Button(action: onTap) {
            HStack(spacing: 12) {
                Image(systemName: "heart")
                    .font(.system(size: 20))
                    .foregroundColor(Color.appPrimary)
                    .frame(width: 32)
                
                Text(activity.name)
                    .font(.system(size: 16, weight: .medium, design: .rounded))
                    .foregroundColor(Color(red: 0.35, green: 0.35, blue: 0.35))
                
                Spacer()
                
                Image(systemName: "plus.circle")
                    .font(.system(size: 24))
                    .foregroundColor(Color.appPrimary)
            }
            .padding()
            .background(Color.white.opacity(0.6))
            .cornerRadius(12)
        }
        .buttonStyle(.plain)
        .padding(.horizontal, 32)
    }
}

/// Sheet for adding custom activity
struct AddCustomActivitySheet: View {
    @Environment(\.dismiss) private var dismiss
    @Bindable var viewModel: ProfileViewModel
    @Binding var activityName: String
    @Binding var isAdding: Bool
    let onAdd: () -> Void

    @State private var selectedCategory: ActivityCategory?
    @FocusState private var isTextFieldFocused: Bool
    
    var body: some View {
        NavigationStack {
            ZStack {
                Color.white
                    .ignoresSafeArea()
                
                VStack(spacing: 24) {
                    // Icon
                    ZStack {
                        Circle()
                            .fill(Color.appPrimary.opacity(0.2))
                            .frame(width: 80, height: 80)
                        
                        Image(systemName: "plus.circle.fill")
                            .font(.system(size: 36))
                            .foregroundColor(Color.appPrimary)
                    }
                    .padding(.top, 20)
                    
                    // Text
                    VStack(spacing: 8) {
                        Text("Add Custom Activity")
                            .font(.system(size: 24, weight: .bold, design: .rounded))
                            .foregroundColor(Color.appNavy)
                        
                        Text("Can't find what you're looking for? Add your own!")
                            .font(.system(size: 15, weight: .regular, design: .rounded))
                            .foregroundColor(Color(red: 0.50, green: 0.50, blue: 0.50))
                            .multilineTextAlignment(.center)
                    }
                    .padding(.horizontal, 32)
                    
                    // Activity name field
                    VStack(alignment: .leading, spacing: 8) {
                        Text("Activity Name")
                            .font(.system(size: 14, weight: .semibold, design: .rounded))
                            .foregroundColor(Color(red: 0.40, green: 0.40, blue: 0.40))
                        
                        TextField("e.g. Rock Climbing", text: $activityName)
                            .font(.system(size: 16, weight: .regular, design: .rounded))
                            .foregroundStyle(.primary)
                            .tint(.primary)
                            .padding()
                            .background(Color.white)
                            .cornerRadius(12)
                            .focused($isTextFieldFocused)
                            .submitLabel(.done)
                            .onSubmit {
                                handleAdd()
                            }
                    }
                    .padding(.horizontal, 32)

                    // Category picker — required before submitting
                    VStack(alignment: .leading, spacing: 8) {
                        Text("Category")
                            .font(.system(size: 14, weight: .semibold, design: .rounded))
                            .foregroundColor(Color(red: 0.40, green: 0.40, blue: 0.40))

                        Menu {
                            ForEach(ActivityCategory.allCases, id: \.self) { category in
                                Button(category.displayName) {
                                    selectedCategory = category
                                }
                            }
                        } label: {
                            HStack {
                                Text(selectedCategory?.displayName ?? "Choose a category")
                                    .foregroundColor(selectedCategory == nil ? Color(red: 0.55, green: 0.55, blue: 0.55) : Color(red: 0.35, green: 0.35, blue: 0.35))
                                Spacer()
                                Image(systemName: "chevron.up.chevron.down")
                                    .foregroundColor(Color.appPrimary)
                            }
                            .font(.system(size: 16, weight: .regular, design: .rounded))
                            .padding()
                            .background(Color.white)
                            .cornerRadius(12)
                        }
                    }
                    .padding(.horizontal, 32)

                    // Add button
                    Button {
                        handleAdd()
                    } label: {
                        HStack {
                            if isAdding {
                                ProgressView()
                                    .tint(.white)
                            } else {
                                Text("Add Activity")
                                    .font(.system(size: 18, weight: .semibold, design: .rounded))
                            }
                        }
                        .foregroundColor(.white)
                        .frame(maxWidth: .infinity)
                        .frame(height: 56)
                        .background(
                            canSubmit ?
                            LinearGradient(
                                colors: [
                                    Color.appPrimary,
                                    Color.appPrimary
                                ],
                                startPoint: .leading,
                                endPoint: .trailing
                            ) :
                            LinearGradient(colors: [Color.gray.opacity(0.3), Color.gray.opacity(0.3)], startPoint: .leading, endPoint: .trailing)
                        )
                        .cornerRadius(16)
                        .shadow(
                            color: canSubmit ? Color.appPrimary.opacity(0.3) : Color.clear,
                            radius: 12, x: 0, y: 6
                        )
                    }
                    .disabled(!canSubmit || isAdding)
                    .padding(.horizontal, 32)

                    Spacer()
                }
            }
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button("Cancel") {
                        dismiss()
                    }
                    .font(.system(size: 17, weight: .regular, design: .rounded))
                    .foregroundColor(Color.appPrimary)
                }
            }
        }
        .onAppear {
            isTextFieldFocused = true
        }
    }
    
    private var canSubmit: Bool {
        !activityName.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty && selectedCategory != nil
    }

    private func handleAdd() {
        let trimmed = activityName.trimmingCharacters(in: .whitespacesAndNewlines)
        guard let category = selectedCategory, !trimmed.isEmpty else { return }

        isTextFieldFocused = false
        isAdding = true

        Task {
            let success = await viewModel.addCustomActivity(name: trimmed, category: category)

            await MainActor.run {
                isAdding = false

                if success {
                    activityName = ""
                    selectedCategory = nil
                    dismiss()
                    onAdd()
                }
            }
        }
    }
}

#Preview {
    NavigationStack {
        ActivitiesSettingsView(viewModel: ProfileViewModel())
    }
}
