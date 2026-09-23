//
//  SignUpView.swift
//  Avenue3
//
//  Created by George Anthony Pazdral II on 5/6/26.
//

import SwiftUI
import CoreLocation

/// Sign up view with email, password, and basic info
/// Connects to AuthViewModel for account creation
struct SignUpView: View {
    let coordinate: CLLocationCoordinate2D
    @Environment(\.dismiss) private var dismiss
    /// Shared with RootView (injected via .environment) — see OnboardingView for why
    /// a per-view local instance would race the location gate for new SSO users.
    /// SignUpView is only ever reached after the gate already passed (email path),
    /// so this doesn't fix a race here — it's for consistency, so errorMessage/
    /// isLoading aren't split across yet another disconnected instance.
    @Environment(AuthViewModel.self) private var viewModel
    
    // Form fields
    @State private var email = ""
    @State private var password = ""
    @State private var confirmPassword = ""
    
    @FocusState private var focusedField: Field?
    
    enum Field {
        case email, password, confirmPassword
    }
    
    var body: some View {
        GeometryReader { geometry in
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
                    VStack(spacing: 0) {
                        // Dynamic top spacing based on screen height
                        Spacer()
                            .frame(height: max(geometry.size.height * 0.02, 8))
                        
                        // Header
                        VStack(spacing: 8) {
                            Text("Welcome to ATX Friends")
                                .font(.system(size: min(28, geometry.size.width * 0.075), weight: .bold, design: .rounded))
                                .foregroundColor(Color.appNavy)
                                .multilineTextAlignment(.center)
                            
                            Text("Let's create your account")
                                .font(.system(size: min(16, geometry.size.width * 0.043), weight: .regular, design: .rounded))
                                .foregroundColor(Color(red: 0.50, green: 0.50, blue: 0.50))
                        }
                        .padding(.bottom, max(geometry.size.height * 0.025, 16))
                        
                        // Form Card
                        VStack(spacing: geometry.size.height > 700 ? 16 : 12) {
                        // Email Field
                        VStack(alignment: .leading, spacing: 8) {
                            Text("Email")
                                .font(.system(size: 14, weight: .semibold, design: .rounded))
                                .foregroundColor(Color(red: 0.40, green: 0.40, blue: 0.40))
                            
                            HStack(spacing: 12) {
                                Image(systemName: "envelope.fill")
                                    .font(.system(size: 18))
                                    .foregroundColor(Color.appPrimary)
                                    .frame(width: 24)
                                
                                TextField("you@example.com", text: $email)
                                    .font(.system(size: 16, weight: .regular, design: .rounded))
                                    .foregroundStyle(.primary)
                                    .tint(.primary)
                                    .textContentType(.emailAddress)
                                    .textInputAutocapitalization(.never)
                                    .keyboardType(.emailAddress)
                                    .autocorrectionDisabled(true)
                                    .focused($focusedField, equals: .email)
                                    .submitLabel(.next)
                                    .onSubmit {
                                        focusedField = .password
                                    }
                            }
                            .padding()
                            .background(Color.white)
                            .cornerRadius(12)
                            .overlay(
                                RoundedRectangle(cornerRadius: 12)
                                    .stroke(focusedField == .email ?
                                           Color.appPrimary :
                                            Color.gray.opacity(0.2),
                                           lineWidth: focusedField == .email ? 2 : 1)
                            )
                        }
                        
                        // Password Field
                        VStack(alignment: .leading, spacing: 8) {
                            Text("Password")
                                .font(.system(size: 14, weight: .semibold, design: .rounded))
                                .foregroundColor(Color(red: 0.40, green: 0.40, blue: 0.40))
                            
                            HStack(spacing: 12) {
                                Image(systemName: "lock.fill")
                                    .font(.system(size: 18))
                                    .foregroundColor(Color.appPrimary)
                                    .frame(width: 24)
                                
                                SecureField("At least 6 characters", text: $password)
                                    .font(.system(size: 16, weight: .regular, design: .rounded))
                                    .foregroundStyle(.primary)
                                    .tint(.primary)
                                    .textContentType(.newPassword)
                                    .focused($focusedField, equals: .password)
                                    .submitLabel(.next)
                                    .onSubmit {
                                        focusedField = .confirmPassword
                                    }
                            }
                            .padding()
                            .background(Color.white)
                            .cornerRadius(12)
                            .overlay(
                                RoundedRectangle(cornerRadius: 12)
                                    .stroke(focusedField == .password ?
                                           Color.appPrimary :
                                            Color.gray.opacity(0.2),
                                           lineWidth: focusedField == .password ? 2 : 1)
                            )
                        }
                        
                        // Confirm Password Field
                        VStack(alignment: .leading, spacing: 8) {
                            Text("Confirm Password")
                                .font(.system(size: 14, weight: .semibold, design: .rounded))
                                .foregroundColor(Color(red: 0.40, green: 0.40, blue: 0.40))
                            
                            HStack(spacing: 12) {
                                Image(systemName: "lock.fill")
                                    .font(.system(size: 18))
                                    .foregroundColor(Color.appPrimary)
                                    .frame(width: 24)
                                
                                SecureField("Re-enter password", text: $confirmPassword)
                                    .font(.system(size: 16, weight: .regular, design: .rounded))
                                    .foregroundStyle(.primary)
                                    .tint(.primary)
                                    .textContentType(.newPassword)
                                    .focused($focusedField, equals: .confirmPassword)
                                    .submitLabel(.done)
                                    .onSubmit {
                                        handleSignUp()
                                    }
                            }
                            .padding()
                            .background(Color.white)
                            .cornerRadius(12)
                            .overlay(
                                RoundedRectangle(cornerRadius: 12)
                                    .stroke(focusedField == .confirmPassword ?
                                           Color.appPrimary :
                                            Color.gray.opacity(0.2),
                                           lineWidth: focusedField == .confirmPassword ? 2 : 1)
                            )
                        }
                        
                        // Error Message
                        if let errorMessage = viewModel.errorMessage {
                            HStack(spacing: 8) {
                                Image(systemName: "exclamationmark.circle.fill")
                                    .foregroundColor(Color(red: 0.85, green: 0.45, blue: 0.40))
                                
                                Text(errorMessage)
                                    .font(.system(size: 14, weight: .medium, design: .rounded))
                                    .foregroundColor(Color(red: 0.85, green: 0.45, blue: 0.40))
                                
                                Spacer()
                            }
                            .padding()
                            .background(Color(red: 0.85, green: 0.45, blue: 0.40).opacity(0.1))
                            .cornerRadius(12)
                        }
                        
                        // Sign Up Button
                        Button {
                            handleSignUp()
                        } label: {
                            HStack {
                                if viewModel.isLoading {
                                    ProgressView()
                                        .tint(.white)
                                } else {
                                    Text("Create Account")
                                        .font(.system(size: 18, weight: .semibold, design: .rounded))
                                }
                            }
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
                            .shadow(color: Color.appNavy.opacity(0.3),
                                   radius: 12, x: 0, y: 6)
                        }
                        .disabled(viewModel.isLoading)
                        .padding(.top, 4)
                        
                        // Info Box
                        VStack(alignment: .leading, spacing: 6) {
                            HStack(spacing: 8) {
                                Image(systemName: "info.circle.fill")
                                    .foregroundColor(Color.appPrimary)
                                
                                Text("What happens next?")
                                    .font(.system(size: 13, weight: .semibold, design: .rounded))
                                    .foregroundColor(Color(red: 0.40, green: 0.40, blue: 0.40))
                            }
                            
                            Text("After creating your account, you'll set up your profile with 3 photos, 3+ activities, and 3+ time slots.")
                                .font(.system(size: 12, weight: .regular, design: .rounded))
                                .foregroundColor(Color(red: 0.50, green: 0.50, blue: 0.50))
                                .lineSpacing(1)
                        }
                        .padding(12)
                        .background(Color.appPrimary.opacity(0.1))
                        .cornerRadius(12)
                        
                        // Terms and Privacy (placeholder)
                        Text("By creating an account, you agree to our\nTerms of Service and Privacy Policy")
                            .font(.system(size: 11, weight: .regular, design: .rounded))
                            .foregroundColor(Color(red: 0.60, green: 0.60, blue: 0.60))
                            .multilineTextAlignment(.center)
                            .padding(.top, geometry.size.height > 700 ? 8 : 4)
                    }
                    .padding(.horizontal, 32)
                    .padding(.bottom, max(geometry.size.height * 0.02, 16))
                }
                .frame(minHeight: geometry.size.height - geometry.safeAreaInsets.top - geometry.safeAreaInsets.bottom)
            }
            .scrollIndicators(.hidden)
            .scrollBounceBehavior(.basedOnSize)
            .navigationBarTitleDisplayMode(.inline)
            .navigationBarBackButtonHidden(true)
            .toolbar {
                ToolbarItem(placement: .navigationBarLeading) {
                    Button {
                        dismiss()
                    } label: {
                        HStack(spacing: 4) {
                            Image(systemName: "chevron.left")
                                .font(.system(size: 16, weight: .semibold))
                            Text("Back")
                                .font(.system(size: 17, weight: .regular, design: .rounded))
                        }
                        .foregroundColor(Color.appPrimary)
                    }
                }
            }
            .onTapGesture {
                // Dismiss keyboard when tapping outside
                focusedField = nil
            }
            .onChange(of: email) { _, _ in
                viewModel.clearError()
            }
            .onChange(of: password) { _, _ in
                viewModel.clearError()
            }
            .onChange(of: confirmPassword) { _, _ in
                viewModel.clearError()
            }
            }
        }
    }
    
    // MARK: - Actions
    
    private func handleSignUp() {
        focusedField = nil
        
        print("🔵 SignUpView: handleSignUp called")
        print("   Email: \(email)")
        print("   Password length: \(password.count)")
        
        Task {
            print("🔵 SignUpView: Calling viewModel.signUp...")
            let success = await viewModel.signUp(
                email: email,
                password: password,
                confirmPassword: confirmPassword,
                coordinate: coordinate
            )
            
            print("🔵 SignUpView: signUp returned: \(success)")
            
            if success {
                print("✅ SignUpView: Sign up successful!")
                print("   User ID: \(viewModel.currentUserID ?? "nil")")
                print("   Auth state: \(viewModel.authState)")
                // Success! The RootView will automatically detect the authenticated state
                // and route the user to the ProfileSetupFlowView (since profile is incomplete)
                // No need to dismiss or navigate - RootView handles this
            } else {
                print("❌ SignUpView: Sign up failed")
                print("   Error: \(viewModel.errorMessage ?? "No error message")")
            }
        }
    }
}

#Preview {
    NavigationStack {
        SignUpView(coordinate: .austin)
            .environment(AuthViewModel())
    }
}
