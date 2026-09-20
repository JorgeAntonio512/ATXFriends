//
//  OnboardingView.swift
//  Avenue3
//
//  Created by George Anthony Pazdral II on 5/6/26.
//

import SwiftUI
import AuthenticationServices

/// Warm welcome screen for Avenue3
/// Introduces the app and leads to sign up or sign in
struct OnboardingView: View {
    @State private var viewModel = AuthViewModel()
    @State private var showSignUp = false
    @State private var showSignIn = false
    @State private var animateTitle = false
    @State private var animateSubtitle = false
    @State private var animateButtons = false
    private let appleSignInHelper = AppleSignInHelper()
    private let googleSignInHelper = GoogleSignInHelper()
    
    var body: some View {
        NavigationStack {
            GeometryReader { geometry in
                ZStack {
                    // Warm gradient background
                    LinearGradient(
                        colors: [
                            Color.white, // Warm cream
                            Color.white  // Soft beige
                        ],
                        startPoint: .topLeading,
                        endPoint: .bottomTrailing
                    )
                    .ignoresSafeArea()
                    
                    ScrollView {
                        VStack(spacing: 0) {
                            // Dynamic top spacer based on screen height
                            Spacer()
                                .frame(height: max(geometry.size.height * 0.04, 20))
                            
                            // Logo/Brand Area
                            VStack(spacing: 12) {
                                // App Icon Placeholder (replace with actual logo)
                                ZStack {
                                    Circle()
                                        .fill(
                                            LinearGradient(
                                                colors: [
                                                    Color.appPrimary, // Soft sage green
                                                    Color.appPrimary  // Deeper sage
                                                ],
                                                startPoint: .topLeading,
                                                endPoint: .bottomTrailing
                                            )
                                        )
                                        .frame(
                                            width: min(100, geometry.size.width * 0.27),
                                            height: min(100, geometry.size.width * 0.27)
                                        )
                                        .shadow(color: .black.opacity(0.1), radius: 20, x: 0, y: 10)
                                    
                                    Image(systemName: "person.3.fill")
                                        .font(.system(size: min(42, geometry.size.width * 0.112)))
                                        .foregroundStyle(.white)
                                }
                                .scaleEffect(animateTitle ? 1 : 0.5)
                                .opacity(animateTitle ? 1 : 0)
                                
                                // App Name
                                Text("ATX Friends")
                                    .font(.system(size: min(42, geometry.size.width * 0.112), weight: .bold, design: .rounded))
                                    .foregroundStyle(
                                        LinearGradient(
                                            colors: [
                                                Color.appNavy, // Deep forest green
                                                Color.appPrimary  // Sage green
                                            ],
                                            startPoint: .leading,
                                            endPoint: .trailing
                                        )
                                    )
                                    .offset(y: animateTitle ? 0 : 20)
                                    .opacity(animateTitle ? 1 : 0)
                            }
                            .padding(.bottom, max(geometry.size.height * 0.025, 16))
                            
                            // Tagline
                            VStack(spacing: 8) {
                                Text("Find Your People")
                                    .font(.system(size: min(24, geometry.size.width * 0.064), weight: .semibold, design: .rounded))
                                    .foregroundColor(Color(red: 0.35, green: 0.35, blue: 0.35))
                                    .multilineTextAlignment(.center)
                                    .offset(y: animateSubtitle ? 0 : 20)
                                    .opacity(animateSubtitle ? 1 : 0)
                                
                                Text("Build meaningful friendships\nin Austin, TX")
                                    .font(.system(size: min(16, geometry.size.width * 0.043), weight: .regular, design: .rounded))
                                    .foregroundColor(Color(red: 0.50, green: 0.50, blue: 0.50))
                                    .multilineTextAlignment(.center)
                                    .fixedSize(horizontal: false, vertical: true)
                                    .lineSpacing(2)
                                    .offset(y: animateSubtitle ? 0 : 20)
                                    .opacity(animateSubtitle ? 1 : 0)
                            }
                            .padding(.horizontal, 40)
                            .padding(.bottom, max(geometry.size.height * 0.02, 12))
                    
                    // Feature Highlights
                    VStack(spacing: 12) {
                        FeatureRow(
                            icon: "person.3.fill",
                            text: "Not a dating app — genuine friendships only"
                        )
                        
                        FeatureRow(
                            icon: "calendar.badge.clock",
                            text: "Match based on activities & availability"
                        )
                        
                        FeatureRow(
                            icon: "mappin.circle.fill",
                            text: "Connect with neighbors in Austin"
                        )
                    }
                    .padding(.horizontal, 40)
                    .opacity(animateButtons ? 1 : 0)
                    .offset(y: animateButtons ? 0 : 20)
                    
                    // Dynamic spacer
                    Spacer()
                        .frame(minHeight: max(geometry.size.height * 0.02, 12))
                    
                    // Action Buttons
                    VStack(spacing: geometry.size.height > 700 ? 12 : 8) {
                        // Custom Sign in with Apple Button
                        Button {
                            appleSignInHelper.signIn(
                                nonce: viewModel.prepareAppleSignIn()
                            ) { result in
                                Task {
                                    switch result {
                                    case .success(let authorization):
                                        _ = await viewModel.handleAppleSignIn(authorization)
                                    case .failure(let error):
                                        print("❌ Apple Sign In failed: \(error)")
                                    }
                                }
                            }
                        } label: {
                            HStack(spacing: 12) {
                                Image(systemName: "apple.logo")
                                    .font(.system(size: 20, weight: .semibold))
                                    .foregroundColor(.black)
                                
                                Text("Sign in with Apple")
                                    .font(.system(size: 18, weight: .semibold, design: .rounded))
                                    .foregroundColor(.black)
                            }
                            .frame(maxWidth: .infinity)
                            .frame(height: 56)
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
                                        _ = await viewModel.handleGoogleSignIn(credential: credential)
                                    case .failure(let error):
                                        print("❌ Google Sign In failed: \(error)")
                                    }
                                }
                            }
                        } label: {
                            HStack(spacing: 12) {
                                // Google "G" logo
                                Image(systemName: "g.circle.fill")
                                    .font(.system(size: 20, weight: .semibold))
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
                                    .font(.system(size: 18, weight: .semibold, design: .rounded))
                                    .foregroundColor(.black)
                            }
                            .frame(maxWidth: .infinity)
                            .frame(height: 56)
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
                        
                        // Divider with "or"
                        HStack(spacing: 12) {
                            Rectangle()
                                .fill(Color.gray.opacity(0.3))
                                .frame(height: 1)
                            
                            Text("or")
                                .font(.system(size: 14, weight: .medium, design: .rounded))
                                .foregroundColor(Color(red: 0.60, green: 0.60, blue: 0.60))
                            
                            Rectangle()
                                .fill(Color.gray.opacity(0.3))
                                .frame(height: 1)
                        }
                        .padding(.vertical, 4)
                        
                        // Register and Sign In Buttons (Side by Side)
                        HStack(spacing: 12) {
                            // Register Button
                            Button {
                                print("🟦 OnboardingView: Register tapped")
                                print("   Current showSignUp: \(showSignUp)")
                                showSignUp = true
                                print("   New showSignUp: \(showSignUp)")
                            } label: {
                                Text("Register")
                                    .font(.system(size: 18, weight: .semibold, design: .rounded))
                                    .foregroundColor(.white)
                                    .frame(maxWidth: .infinity)
                                    .frame(height: 56)
                                    .background(
                                        LinearGradient(
                                            colors: [
                                                Color.appNavy, // Sage green
                                                Color.appNavy  // Deeper sage
                                            ],
                                            startPoint: .leading,
                                            endPoint: .trailing
                                        )
                                    )
                                    .cornerRadius(16)
                                    .shadow(color: Color.appNavy.opacity(0.3),
                                           radius: 12, x: 0, y: 6)
                            }
                            .buttonStyle(.plain)
                            .frame(maxWidth: .infinity)
                            
                            // Sign In Button
                            Button {
                                print("🟦 OnboardingView: Sign In tapped")
                                print("   Current showSignIn: \(showSignIn)")
                                showSignIn = true
                                print("   New showSignIn: \(showSignIn)")
                            } label: {
                                Text("Sign In")
                                    .font(.system(size: 18, weight: .semibold, design: .rounded))
                                    .foregroundColor(.black)
                                    .frame(maxWidth: .infinity)
                                    .frame(height: 56)
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
                            .frame(maxWidth: .infinity)
                        }
                    }
                    .padding(.horizontal, 40)
                    .padding(.bottom, max(geometry.size.height * 0.03, 20))
                    .opacity(animateButtons ? 1 : 0)
                    .offset(y: animateButtons ? 0 : 20)
                    .allowsHitTesting(animateButtons) // Only allow interaction after animation
                        }
                        .frame(minHeight: geometry.size.height)
                    }
                    .scrollIndicators(.hidden)
                }
            }
            .navigationDestination(isPresented: $showSignUp) {
                LocationGateView(onDismissAll: { showSignUp = false })
            }
            .navigationDestination(isPresented: $showSignIn) {
                SignInView()
            }
            .onChange(of: showSignUp) { oldValue, newValue in
                print("🟦 OnboardingView: showSignUp changed from \(oldValue) to \(newValue)")
            }
            .onChange(of: showSignIn) { oldValue, newValue in
                print("🟦 OnboardingView: showSignIn changed from \(oldValue) to \(newValue)")
            }
            .onAppear {
                print("🟦 OnboardingView: View appeared")
                // Staggered animations for smooth entrance
                withAnimation(.spring(response: 0.6, dampingFraction: 0.8).delay(0.1)) {
                    animateTitle = true
                }
                withAnimation(.spring(response: 0.6, dampingFraction: 0.8).delay(0.3)) {
                    animateSubtitle = true
                }
                withAnimation(.spring(response: 0.6, dampingFraction: 0.8).delay(0.5)) {
                    animateButtons = true
                }
            }
        }
    }
}

/// Reusable feature row component
struct FeatureRow: View {
    let icon: String
    let text: String
    
    var body: some View {
        HStack(spacing: 16) {
            Image(systemName: icon)
                .font(.system(size: 24))
                .foregroundStyle(
                    LinearGradient(
                        colors: [
                            Color.appPrimary,
                            Color.appPrimary
                        ],
                        startPoint: .topLeading,
                        endPoint: .bottomTrailing
                    )
                )
                .frame(width: 40)
            
            Text(text)
                .font(.system(size: 15, weight: .medium, design: .rounded))
                .foregroundColor(Color(red: 0.40, green: 0.40, blue: 0.40))
                .multilineTextAlignment(.leading)
                .fixedSize(horizontal: false, vertical: true)
                .lineLimit(nil)
            
            Spacer()
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}

#Preview {
    OnboardingView()
}
