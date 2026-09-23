//
//  SignInView.swift
//  Avenue3
//
//  Created by George Anthony Pazdral II on 5/6/26.
//

import SwiftUI
import AuthenticationServices

/// Sign in view with email and password
/// Connects to AuthViewModel for authentication
struct SignInView: View {
    @Environment(\.dismiss) private var dismiss
    /// Shared with RootView (injected via .environment) — see OnboardingView for why
    /// a per-view local instance would race the location gate for new SSO users.
    @Environment(AuthViewModel.self) private var viewModel
    @State private var appleSignInHelper = AppleSignInHelper()
    @State private var googleSignInHelper = GoogleSignInHelper()
    
    // Form fields
    @State private var email = ""
    @State private var password = ""
    
    // UI State
    @State private var showForgotPassword = false
    @State private var forgotPasswordEmail = ""
    @State private var showPasswordResetSuccess = false
    @State private var showCreateAccount = false
    
    @FocusState private var focusedField: Field?
    
    enum Field {
        case email, password, forgotPasswordEmail
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
                            Text("Welcome Back")
                                .font(.system(size: min(28, geometry.size.width * 0.075), weight: .bold, design: .rounded))
                                .foregroundColor(Color.appNavy)
                                .multilineTextAlignment(.center)
                            
                            Text("Sign in to continue")
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
                                    
                                    SecureField("Enter your password", text: $password)
                                        .font(.system(size: 16, weight: .regular, design: .rounded))
                                        .foregroundStyle(.primary)
                                        .tint(.primary)
                                        .textContentType(.password)
                                        .focused($focusedField, equals: .password)
                                        .submitLabel(.done)
                                        .onSubmit {
                                            handleSignIn()
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
                            
                            // Forgot Password Link
                            HStack {
                                Spacer()
                                Button {
                                    showForgotPassword = true
                                    forgotPasswordEmail = email
                                } label: {
                                    Text("Forgot Password?")
                                        .font(.system(size: 13, weight: .medium, design: .rounded))
                                        .foregroundColor(Color.appPrimary)
                                }
                            }
                            .padding(.top, -8)
                            
                            // Error Message
                            if let errorMessage = viewModel.errorMessage {
                                VStack(alignment: .leading, spacing: 8) {
                                    HStack(spacing: 8) {
                                        Image(systemName: "exclamationmark.circle.fill")
                                            .foregroundColor(Color(red: 0.85, green: 0.45, blue: 0.40))
                                        
                                        Text(errorMessage)
                                            .font(.system(size: 14, weight: .medium, design: .rounded))
                                            .foregroundColor(Color(red: 0.85, green: 0.45, blue: 0.40))
                                        
                                        Spacer()
                                    }
                                    
                                    if viewModel.showCreateAccountPrompt {
                                        Button {
                                            showCreateAccount = true
                                        } label: {
                                            Text("New here? Create an account")
                                                .font(.system(size: 13, weight: .semibold, design: .rounded))
                                                .foregroundColor(Color.appPrimary)
                                        }
                                    }
                                }
                                .padding()
                                .background(Color(red: 0.85, green: 0.45, blue: 0.40).opacity(0.1))
                                .cornerRadius(12)
                            }
                            
                            // Sign In Button
                            Button {
                                handleSignIn()
                            } label: {
                                HStack {
                                    if viewModel.isLoading {
                                        ProgressView()
                                            .tint(.white)
                                    } else {
                                        Text("Sign In")
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
                            
                            // Divider with "or"
                            HStack(spacing: 12) {
                                Rectangle()
                                    .fill(Color.gray.opacity(0.3))
                                    .frame(height: 1)
                                
                                Text("or")
                                    .font(.system(size: 13, weight: .medium, design: .rounded))
                                    .foregroundColor(Color(red: 0.60, green: 0.60, blue: 0.60))
                                
                                Rectangle()
                                    .fill(Color.gray.opacity(0.3))
                                    .frame(height: 1)
                            }
                            .padding(.vertical, geometry.size.height > 700 ? 8 : 4)
                            
                            // Custom Sign in with Apple Button
                            Button {
                                appleSignInHelper.signIn(
                                    nonce: viewModel.prepareAppleSignIn()
                                ) { result in
                                    Task {
                                        switch result {
                                        case .success(let authorization):
                                            let success = await viewModel.handleAppleSignIn(authorization)
                                            if success {
                                                dismiss()
                                            }
                                        case .failure(let error):
                                            print("❌ Apple Sign In failed: \(error)")
                                        }
                                    }
                                }
                            } label: {
                                HStack(spacing: 12) {
                                    Image(systemName: "apple.logo")
                                        .font(.system(size: 18, weight: .semibold))
                                        .foregroundColor(.black)
                                    
                                    Text("Sign in with Apple")
                                        .font(.system(size: 17, weight: .semibold, design: .rounded))
                                        .foregroundColor(.black)
                                }
                                .frame(maxWidth: .infinity)
                                .frame(height: 52)
                                .background(
                                    RoundedRectangle(cornerRadius: 16)
                                        .fill(Color.white)
                                        .overlay(
                                            RoundedRectangle(cornerRadius: 16)
                                                .stroke(Color.black, lineWidth: 1)
                                        )
                                )
                            }
                            .buttonStyle(.plain)
                            
                            // Google Sign In Button
                            Button {
                                googleSignInHelper.signIn { result in
                                    Task {
                                        switch result {
                                        case .success(let credential):
                                            let success = await viewModel.handleGoogleSignIn(credential: credential)
                                            if success {
                                                dismiss()
                                            }
                                        case .failure(let error):
                                            print("❌ Google Sign In failed: \(error)")
                                        }
                                    }
                                }
                            } label: {
                                HStack(spacing: 12) {
                                    // Google "G" logo
                                    Image(systemName: "g.circle.fill")
                                        .font(.system(size: 18, weight: .semibold))
                                        .foregroundStyle(
                                            LinearGradient(
                                                colors: [
                                                    Color(red: 0.26, green: 0.52, blue: 0.96), // Google Blue
                                                    Color(red: 0.92, green: 0.25, blue: 0.21)  // Google Red
                                                ],
                                                startPoint: .topLeading,
                                                endPoint: .bottomTrailing
                                            )
                                        )
                                    
                                    Text("Continue with Google")
                                        .font(.system(size: 17, weight: .semibold, design: .rounded))
                                        .foregroundColor(.black)
                                }
                                .frame(maxWidth: .infinity)
                                .frame(height: 52)
                                .background(
                                    RoundedRectangle(cornerRadius: 16)
                                        .fill(Color.white)
                                        .overlay(
                                            RoundedRectangle(cornerRadius: 16)
                                                .stroke(Color.black, lineWidth: 1)
                                        )
                                )
                            }
                            .buttonStyle(.plain)
                        }
                        .padding(.horizontal, 32)
                        .padding(.bottom, max(geometry.size.height * 0.02, 16))
                    }
                    .frame(minHeight: geometry.size.height - geometry.safeAreaInsets.top - geometry.safeAreaInsets.bottom)
                }
                .scrollIndicators(.hidden)
                .scrollBounceBehavior(.basedOnSize)
            }
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
            .navigationDestination(isPresented: $showCreateAccount) {
                LocationGateView(path: "email", onDismissAll: { showCreateAccount = false })
            }
            .sheet(isPresented: $showForgotPassword) {
                ForgotPasswordSheet(
                    viewModel: viewModel,
                    email: $forgotPasswordEmail,
                    showSuccess: $showPasswordResetSuccess
                )
                .presentationDetents([.medium])
                .presentationDragIndicator(.visible)
            }
            .alert("Password Reset Email Sent", isPresented: $showPasswordResetSuccess) {
                Button("OK", role: .cancel) { }
            } message: {
                Text("Check your email for instructions to reset your password.")
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
        }
    }
    
    // MARK: - Actions
    
    private func handleSignIn() {
        focusedField = nil
        
        Task {
            let success = await viewModel.signIn(
                email: email,
                password: password
            )
            
            if success {
                // Navigate to main app (will be handled by parent view)
                dismiss()
            }
        }
    }
}
    
    // MARK: - Forgot Password Sheet
    
    struct ForgotPasswordSheet: View {
        @Environment(\.dismiss) private var dismiss
        var viewModel: AuthViewModel
        @Binding var email: String
        @Binding var showSuccess: Bool
        
        @State private var isLoading = false
        @FocusState private var isEmailFocused: Bool
        
        var body: some View {
            NavigationStack {
                GeometryReader { geometry in
                    ZStack {
                        Color.white
                            .ignoresSafeArea()
                        
                        ScrollView {
                            VStack(spacing: max(geometry.size.height * 0.03, 16)) {
                                // Dynamic top spacing
                                Spacer()
                                    .frame(height: max(geometry.size.height * 0.015, 8))
                                
                                // Icon
                                ZStack {
                                    Circle()
                                        .fill(Color.appPrimary.opacity(0.2))
                                        .frame(width: min(80, geometry.size.width * 0.21), height: min(80, geometry.size.width * 0.21))
                                    
                                    Image(systemName: "lock.rotation")
                                        .font(.system(size: min(36, geometry.size.width * 0.096)))
                                        .foregroundColor(Color.appPrimary)
                                }
                                
                                // Text
                                VStack(spacing: 8) {
                                    Text("Reset Password")
                                        .font(.system(size: min(24, geometry.size.width * 0.064), weight: .bold, design: .rounded))
                                        .foregroundColor(Color.appNavy)
                                    
                                    Text("Enter your email and we'll send you instructions to reset your password.")
                                        .font(.system(size: min(15, geometry.size.width * 0.04), weight: .regular, design: .rounded))
                                        .foregroundColor(Color(red: 0.50, green: 0.50, blue: 0.50))
                                        .multilineTextAlignment(.center)
                                        .lineSpacing(2)
                                }
                                .padding(.horizontal, 32)
                                
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
                                            .focused($isEmailFocused)
                                            .submitLabel(.send)
                                            .onSubmit {
                                                handleSendReset()
                                            }
                                    }
                                    .padding()
                                    .background(Color.white)
                                    .cornerRadius(12)
                                    .overlay(
                                        RoundedRectangle(cornerRadius: 12)
                                            .stroke(isEmailFocused ?
                                                    Color.appPrimary :
                                                        Color.gray.opacity(0.2),
                                                    lineWidth: isEmailFocused ? 2 : 1)
                                    )
                                }
                                .padding(.horizontal, 32)
                                
                                // Send Button
                                Button {
                                    handleSendReset()
                                } label: {
                                    HStack {
                                        if isLoading {
                                            ProgressView()
                                                .tint(.white)
                                        } else {
                                            Text("Send Reset Email")
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
                                .disabled(isLoading)
                                .padding(.horizontal, 32)
                                
                                // Bottom spacing
                                Spacer()
                                    .frame(height: max(geometry.size.height * 0.02, 16))
                            }
                            .frame(minHeight: geometry.size.height - geometry.safeAreaInsets.top - geometry.safeAreaInsets.bottom)
                        }
                        .scrollIndicators(.hidden)
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
        }
        
        // MARK: - Actions
        
        private func handleSendReset() {
            isEmailFocused = false
            isLoading = true
            
            Task {
                let success = await viewModel.sendPasswordReset(email: email)
                isLoading = false
                
                if success {
                    dismiss()
                    showSuccess = true
                }
            }
        }
    }
#Preview("Sign In") {
    NavigationStack {
        SignInView()
            .environment(AuthViewModel())
    }
}
#Preview("Forgot Password") {
    ForgotPasswordSheet(
        viewModel: AuthViewModel(),
        email: .constant("test@example.com"),
        showSuccess: .constant(false)
    )
}
