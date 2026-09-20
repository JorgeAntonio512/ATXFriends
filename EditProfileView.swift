//
//  EditProfileView.swift
//  Avenue3
//
//  Created by George Anthony Pazdral II on 5/6/26.
//

import SwiftUI

/// Settings view for editing user's display name
struct EditProfileView: View {
    @Environment(\.dismiss) private var dismiss
    @Bindable var viewModel: ProfileViewModel
    
    @State private var displayName: String = ""
    @State private var isSaving = false
    @State private var showError = false
    @FocusState private var isTextFieldFocused: Bool
    
    var isNameValid: Bool {
        let trimmed = displayName.trimmingCharacters(in: .whitespacesAndNewlines)
        return trimmed.count >= 2 && trimmed.count <= 30
    }
    
    var hasChanges: Bool {
        displayName.trimmingCharacters(in: .whitespacesAndNewlines) != viewModel.displayName
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
                VStack(spacing: 32) {
                    // Header Icon
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
                            .frame(width: 100, height: 100)
                        
                        Image(systemName: "person.circle.fill")
                            .font(.system(size: 60))
                            .foregroundColor(Color.appPrimary)
                    }
                    .padding(.top, 20)
                    
                    // Name input field
                    VStack(alignment: .leading, spacing: 8) {
                        Text("Display Name")
                            .font(.system(size: 14, weight: .semibold, design: .rounded))
                            .foregroundColor(Color(red: 0.40, green: 0.40, blue: 0.40))
                            .padding(.leading, 4)
                        
                        TextField("Enter your name", text: $displayName)
                            .font(.system(size: 18, weight: .medium, design: .rounded))
                            .foregroundColor(Color(red: 0.35, green: 0.35, blue: 0.35))
                            .padding(16)
                            .background(Color.white.opacity(0.9))
                            .cornerRadius(12)
                            .focused($isTextFieldFocused)
                            .textInputAutocapitalization(.words)
                            .autocorrectionDisabled()
                            .submitLabel(.done)
                            .onSubmit {
                                if isNameValid && hasChanges {
                                    saveChanges()
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
                            Text("\(displayName.count)/30")
                                .font(.system(size: 12, weight: .regular, design: .rounded))
                                .foregroundColor(Color(red: 0.60, green: 0.60, blue: 0.60))
                        }
                        .padding(.trailing, 4)
                    }
                    .padding(.horizontal, 32)
                    
                    // Info box
                    VStack(alignment: .leading, spacing: 8) {
                        HStack(spacing: 8) {
                            Image(systemName: "info.circle.fill")
                                .foregroundColor(Color.appPrimary)
                            
                            Text("Tips")
                                .font(.system(size: 15, weight: .semibold, design: .rounded))
                                .foregroundColor(Color(red: 0.40, green: 0.40, blue: 0.40))
                        }
                        
                        VStack(alignment: .leading, spacing: 6) {
                            Text("• Use your first name or a friendly nickname")
                            Text("• Keep it appropriate and respectful")
                            Text("• This is how others will see you")
                        }
                        .font(.system(size: 14, weight: .regular, design: .rounded))
                        .foregroundColor(Color(red: 0.50, green: 0.50, blue: 0.50))
                        .lineSpacing(2)
                    }
                    .padding()
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .background(Color.appPrimary.opacity(0.1))
                    .cornerRadius(12)
                    .padding(.horizontal, 32)
                }
            }
            .scrollIndicators(.hidden)
        }
        .navigationTitle("Display Name")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .navigationBarTrailing) {
                Button {
                    saveChanges()
                } label: {
                    if isSaving {
                        ProgressView()
                            .tint(Color.appPrimary)
                    } else {
                        Text("Save")
                            .font(.system(size: 17, weight: .semibold, design: .rounded))
                            .foregroundColor(
                                (isNameValid && hasChanges) ?
                                Color.appPrimary :
                                Color.gray
                            )
                    }
                }
                .disabled(!isNameValid || !hasChanges || isSaving)
            }
        }
        .onAppear {
            displayName = viewModel.displayName
        }
        .alert("Error", isPresented: $showError) {
            Button("OK", role: .cancel) { }
        } message: {
            Text(viewModel.errorMessage ?? "Failed to save changes")
        }
    }
    
    private func saveChanges() {
        guard isNameValid && hasChanges else { return }
        
        isTextFieldFocused = false
        isSaving = true
        
        Task {
            viewModel.displayName = displayName.trimmingCharacters(in: .whitespacesAndNewlines)
            let success = await viewModel.saveProfile()
            
            await MainActor.run {
                isSaving = false
                
                if success {
                    dismiss()
                } else {
                    showError = true
                }
            }
        }
    }
}

#Preview {
    NavigationStack {
        EditProfileView(viewModel: ProfileViewModel())
    }
}
