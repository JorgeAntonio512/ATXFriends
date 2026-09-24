//
//  ProfileSetupFlowView.swift
//  Avenue3
//
//  Created by George Anthony Pazdral II on 5/6/26.
//

import SwiftUI

// MARK: - Keyboard Dismissal Extension

extension View {
    func hideKeyboard() {
        UIApplication.shared.sendAction(#selector(UIResponder.resignFirstResponder), to: nil, from: nil, for: nil)
    }
}

/// Main coordinator for the profile setup flow
/// Manages progression through all setup steps
struct ProfileSetupFlowView: View {
    @State private var viewModel = ProfileViewModel()
    @State private var currentStep: ProfileSetupStep = .name
    @State private var isSavingProfile = false
    @State private var showSaveError = false
    @Environment(\.dismiss) private var dismiss
    
    enum ProfileSetupStep: Int, CaseIterable {
        case name = 0
        case photos = 1
        case activities = 2
        case timeSlots = 3
        case complete = 4

        var title: String {
            switch self {
            case .name: return "Your Name"
            case .photos: return "Profile Photos"
            case .activities: return "Your Activities"
            case .timeSlots: return "Availability"
            case .complete: return "All Set!"
            }
        }

        var stepNumber: Int {
            rawValue + 1
        }

        var totalSteps: Int {
            4
        }
    }
    
    var body: some View {
        ZStack {
            // Warm gradient background
            LinearGradient(
                colors: [
                    Color.appBackground,
                    Color.appBackground
                ],
                startPoint: .topLeading,
                endPoint: .bottomTrailing
            )
            .ignoresSafeArea()
            
            VStack(spacing: 0) {
                // Progress indicator (hidden on complete screen)
                if currentStep != .complete {
                    ProfileSetupProgressBar(
                        currentStep: currentStep.stepNumber,
                        totalSteps: currentStep.totalSteps
                    )
                    .padding(.horizontal, 20)
                    .padding(.top, 20)
                    .padding(.bottom, 16)
                }
                
                // Current step view
                currentStepView
            }
        }
        .navigationBarBackButtonHidden(true)
        .task {
            await viewModel.loadUserProfile()
            await viewModel.loadActivities()
        }
        .alert("Save Error", isPresented: $showSaveError) {
            Button("OK", role: .cancel) {
                showSaveError = false
            }
        } message: {
            Text(viewModel.errorMessage ?? "Failed to save your profile. Please try again.")
        }
    }
    
    @ViewBuilder
    private var currentStepView: some View {
        switch currentStep {
        case .name:
            NameInputView(
                viewModel: viewModel,
                onNext: { currentStep = .photos },
                onBack: { /* No back on first step */ }
            )
        case .photos:
            PhotoPickerView(
                viewModel: viewModel,
                onNext: { currentStep = .activities },
                onBack: { currentStep = .name }
            )
        case .activities:
            ActivityPickerView(
                viewModel: viewModel,
                onNext: { currentStep = .timeSlots },
                onBack: { currentStep = .photos }
            )
        case .timeSlots:
            TimeSlotPickerView(
                viewModel: viewModel,
                onNext: { currentStep = .complete },
                onBack: { currentStep = .activities }
            )
        case .complete:
            ProfileCompletionView(
                viewModel: viewModel,
                isSaving: isSavingProfile,
                onEnterApp: {
                    Task {
                        isSavingProfile = true

                        // Save critical profile data first (fast)
                        let success = await viewModel.saveProfileCriticalData()
                        
                        if success {
                            // Profile saved successfully - navigate immediately
                            print("✅ Profile saved successfully, posting notification")
                            
                            // Post notification to trigger auth state refresh immediately
                            NotificationCenter.default.post(name: .authStateDidChange, object: nil)
                            
                            // Post onboarding completion notification to trigger matches reload
                            NotificationCenter.default.post(name: .onboardingCompleted, object: nil)
                            
                            // Upload photos in background (don't await)
                            Task.detached {
                                await viewModel.uploadPhotosInBackground()
                            }
                        } else {
                            // Handle error - show an alert to user
                            print("❌ Failed to save profile: \(viewModel.errorMessage ?? "Unknown error")")
                            isSavingProfile = false
                            showSaveError = true
                        }
                    }
                }
            )
        }
    }
}

/// Progress bar showing current step in profile setup
struct ProfileSetupProgressBar: View {
    let currentStep: Int
    let totalSteps: Int
    
    var progress: Double {
        Double(currentStep) / Double(totalSteps)
    }
    
    var body: some View {
        VStack(spacing: 8) {
            // Progress bar
            GeometryReader { geometry in
                ZStack(alignment: .leading) {
                    // Background
                    RoundedRectangle(cornerRadius: 4)
                        .fill(Color.appCardBackground.opacity(0.3))
                        .frame(height: 8)
                    
                    // Progress fill
                    RoundedRectangle(cornerRadius: 4)
                        .fill(
                            LinearGradient(
                                colors: [
                                    Color.appPrimary,
                                    Color.appPrimary
                                ],
                                startPoint: .leading,
                                endPoint: .trailing
                            )
                        )
                        .frame(width: geometry.size.width * progress, height: 8)
                        .animation(.spring(response: 0.4, dampingFraction: 0.8), value: progress)
                }
            }
            .frame(height: 8)
            
            // Step counter
            HStack {
                Text("Step \(currentStep) of \(totalSteps)")
                    .font(.system(size: 13, weight: .medium, design: .rounded))
                    .foregroundColor(Color.appSecondaryText)
                
                Spacer()
            }
        }
    }
}

// MARK: - Name Input View

/// Step 1: Collect user's display name
struct NameInputView: View {
    @Bindable var viewModel: ProfileViewModel
    let onNext: () -> Void
    let onBack: () -> Void
    
    @FocusState private var isTextFieldFocused: Bool
    @FocusState private var isBioFocused: Bool
    @State private var showError = false
    
    private var isNameValid: Bool {
        let trimmed = viewModel.displayName.trimmingCharacters(in: .whitespacesAndNewlines)
        return trimmed.count >= 2 && trimmed.count <= 30
    }
    
    private var bioCharacterCount: Int {
        viewModel.bio.count
    }
    
    var body: some View {
        GeometryReader { geometry in
            VStack(spacing: 0) {
                // Scrollable content
                ScrollView {
                    VStack(spacing: max(geometry.size.height * 0.03, 20)) {
                        // Dynamic top spacing
                        Spacer()
                            .frame(height: max(geometry.size.height * 0.015, 8))
                        
                        // Header
                        VStack(spacing: 12) {
                            Text("What's your name?")
                                .font(.system(size: min(32, geometry.size.width * 0.085), weight: .bold, design: .rounded))
                                .foregroundColor(Color.appPrimaryText)
                                .multilineTextAlignment(.center)
                            
                            Text("This is how other people will see you on ATX Friends")
                                .font(.system(size: min(17, geometry.size.width * 0.045), weight: .regular, design: .rounded))
                                .foregroundColor(Color.appSecondaryText)
                                .multilineTextAlignment(.center)
                                .lineSpacing(4)
                        }
                        .padding(.horizontal, 40)
                        .onTapGesture {
                            hideKeyboard()
                        }
                        
                        // Name icon
                        ZStack {
                            Circle()
                                .fill(
                                    LinearGradient(
                                        colors: [
                                            Color.appPrimary.opacity(0.2),
                                            Color.appPrimary.opacity(0.2)
                                        ],
                                        startPoint: .topLeading,
                                        endPoint: .bottomTrailing
                                    )
                                )
                                .frame(
                                    width: min(140, geometry.size.width * 0.37),
                                    height: min(140, geometry.size.width * 0.37)
                                )
                            
                            Image(systemName: "person.circle.fill")
                                .font(.system(size: min(80, geometry.size.width * 0.21)))
                                .foregroundColor(Color.appPrimary)
                        }
                        .onTapGesture {
                            hideKeyboard()
                        }
                        
                        // Name input field
                        VStack(alignment: .leading, spacing: 8) {
                            Text("Display Name")
                                .font(.system(size: 14, weight: .semibold, design: .rounded))
                                .foregroundColor(Color.appTextBody)
                                .padding(.leading, 4)

                            TextField("Enter your name", text: $viewModel.displayName)
                                .font(.system(size: 18, weight: .medium, design: .rounded))
                                .foregroundColor(Color.appTextStrong)
                                .padding(16)
                                .background(Color.appCardBackground.opacity(0.9))
                                .cornerRadius(12)
                                .focused($isTextFieldFocused)
                                .textInputAutocapitalization(.words)
                                .autocorrectionDisabled()
                                .submitLabel(.next)
                                .onSubmit {
                                    if isNameValid {
                                        isBioFocused = true
                                    } else {
                                        showError = true
                                    }
                                }
                            
                            // Error message
                            if showError && !isNameValid {
                                HStack(spacing: 6) {
                                    Image(systemName: "exclamationmark.circle.fill")
                                        .font(.system(size: 12))
                                    Text("Please enter a name (2-30 characters)")
                                        .font(.system(size: 13, weight: .medium, design: .rounded))
                                }
                                .foregroundColor(.red.opacity(0.8))
                                .padding(.leading, 4)
                            }
                            
                            // Character count
                            HStack {
                                Spacer()
                                Text("\(viewModel.displayName.count)/30")
                                    .font(.system(size: 12, weight: .regular, design: .rounded))
                                    .foregroundColor(Color.appTextMuted)
                            }
                            .padding(.trailing, 4)
                        }
                        .padding(.horizontal, 32)
                        
                        // Bio input field
                        VStack(alignment: .leading, spacing: 8) {
                            Text("Short Bio")
                                .font(.system(size: 14, weight: .semibold, design: .rounded))
                                .foregroundColor(Color.appTextBody)
                                .padding(.leading, 4)

                            ZStack(alignment: .topLeading) {
                                // Background
                                RoundedRectangle(cornerRadius: 12)
                                    .fill(Color.appCardBackground.opacity(0.9))

                                // Placeholder
                                if viewModel.bio.isEmpty {
                                    Text("A little about you... (optional)")
                                        .font(.system(size: 16, weight: .regular, design: .rounded))
                                        .foregroundColor(Color.appTextMuted)
                                        .padding(.horizontal, 20)
                                        .padding(.vertical, 20)
                                }

                                // TextEditor
                                TextEditor(text: $viewModel.bio)
                                    .font(.system(size: 16, weight: .regular, design: .rounded))
                                    .foregroundColor(Color.appTextStrong)
                                    .padding(.horizontal, 16)
                                    .padding(.vertical, 12)
                                    .scrollContentBackground(.hidden)
                                    .background(Color.clear)
                                    .focused($isBioFocused)
                                    .textInputAutocapitalization(.sentences)
                                    .onChange(of: viewModel.bio) { oldValue, newValue in
                                        // Limit to 150 characters
                                        if newValue.count > 150 {
                                            viewModel.bio = String(newValue.prefix(150))
                                        }
                                    }
                            }
                            .frame(height: 100)
                            
                            // Character count
                            HStack {
                                Spacer()
                                Text("\(bioCharacterCount)/150")
                                    .font(.system(size: 12, weight: .regular, design: .rounded))
                                    .foregroundColor(Color.appTextMuted)
                            }
                            .padding(.trailing, 4)
                        }
                        .padding(.horizontal, 32)
                        
                        // Info box
                        VStack(alignment: .leading, spacing: 8) {
                            HStack(spacing: 8) {
                                Image(systemName: "lightbulb.fill")
                                    .foregroundColor(Color.appPrimary)
                                
                                Text("Tips")
                                    .font(.system(size: 15, weight: .semibold, design: .rounded))
                                    .foregroundColor(Color.appTextBody)
                            }
                            
                            VStack(alignment: .leading, spacing: 6) {
                                Text("• Use your first name or a friendly nickname")
                                Text("• Keep it appropriate and respectful")
                                Text("• You can change this later in Settings")
                            }
                            .font(.system(size: 14, weight: .regular, design: .rounded))
                            .foregroundColor(Color.appSecondaryText)
                            .lineSpacing(2)
                        }
                        .padding()
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .background(Color.appPrimary.opacity(0.1))
                        .cornerRadius(12)
                        .padding(.horizontal, 32)
                        .onTapGesture {
                            hideKeyboard()
                        }
                        
                        // Bottom spacing
                        Spacer()
                            .frame(height: max(geometry.size.height * 0.02, 16))
                    }
                    .scrollIndicators(.hidden)
                }
                .scrollDismissesKeyboard(.interactively)
                
                // Bottom buttons - pinned outside ScrollView
                VStack(spacing: 12) {
                    Button {
                        if isNameValid {
                            onNext()
                        } else {
                            showError = true
                            // Shake animation or haptic feedback could go here
                        }
                    } label: {
                        Text("Continue")
                            .font(.system(size: 18, weight: .semibold, design: .rounded))
                            .foregroundColor(.white)
                            .frame(maxWidth: .infinity)
                            .frame(height: 56)
                            .background(
                                isNameValid ?
                                LinearGradient(
                                    colors: [
                                        Color.appPrimary,
                                        Color.appPrimary
                                    ],
                                    startPoint: .leading,
                                    endPoint: .trailing
                                ) :
                                    LinearGradient(
                                        colors: [Color.gray.opacity(0.5), Color.gray.opacity(0.5)],
                                        startPoint: .leading,
                                        endPoint: .trailing
                                    )
                            )
                            .cornerRadius(16)
                            .shadow(
                                color: isNameValid ?
                                Color.appPrimary.opacity(0.3) :
                                    Color.clear,
                                radius: 12, x: 0, y: 6
                            )
                    }
                    .disabled(!isNameValid)
                    
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
                            Color.appCardBackground.opacity(0.95),
                            Color.appCardBackground.opacity(0.95)
                        ],
                        startPoint: .top,
                        endPoint: .bottom
                    )
                    .shadow(color: .black.opacity(0.1), radius: 10, x: 0, y: -5)
                )
            }
        }

    }
}
#Preview {
    ProfileSetupFlowView()
}
