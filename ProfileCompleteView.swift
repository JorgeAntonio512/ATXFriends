//
//  ProfileCompleteView.swift
//  Avenue3
//
//  Created by George Anthony Pazdral II on 5/6/26.
//

import SwiftUI

/// Step 5: Celebratory profile completion screen
/// Shows a warm congratulations and summarizes the user's 3-by-3 profile
struct ProfileCompletionView: View {
    @Bindable var viewModel: ProfileViewModel
    let isSaving: Bool
    let onEnterApp: () -> Void
    
    @State private var animateCheckmark = false
    @State private var animateContent = false
    @State private var animateButton = false
    
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
                    Spacer()
                        .frame(height: 40)
                    
                    // Celebration icon
                    ZStack {
                        // Outer circle
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
                            .frame(width: 160, height: 160)
                        
                        // Inner circle with checkmark
                        ZStack {
                            Circle()
                                .fill(
                                    LinearGradient(
                                        colors: [
                                            Color.appPrimary,
                                            Color.appPrimary
                                        ],
                                        startPoint: .topLeading,
                                        endPoint: .bottomTrailing
                                    )
                                )
                                .frame(width: 120, height: 120)
                                .shadow(color: Color.appPrimary.opacity(0.3),
                                       radius: 20, x: 0, y: 10)
                            
                            Image(systemName: "checkmark")
                                .font(.system(size: 60, weight: .bold))
                                .foregroundColor(.white)
                                .scaleEffect(animateCheckmark ? 1 : 0.3)
                                .opacity(animateCheckmark ? 1 : 0)
                        }
                    }
                    
                    // Congratulations text
                    VStack(spacing: 16) {
                        Text("You're All Set!")
                            .font(.system(size: 36, weight: .bold, design: .rounded))
                            .foregroundStyle(
                                LinearGradient(
                                    colors: [
                                        Color.appNavy,
                                        Color.appPrimary
                                    ],
                                    startPoint: .leading,
                                    endPoint: .trailing
                                )
                            )
                            .multilineTextAlignment(.center)
                            .opacity(animateContent ? 1 : 0)
                            .offset(y: animateContent ? 0 : 20)
                        
                        Text("Your profile is ready.\nLet's find your people in Austin!")
                            .font(.system(size: 18, weight: .regular, design: .rounded))
                            .foregroundColor(Color(red: 0.50, green: 0.50, blue: 0.50))
                            .multilineTextAlignment(.center)
                            .lineSpacing(6)
                            .opacity(animateContent ? 1 : 0)
                            .offset(y: animateContent ? 0 : 20)
                    }
                    .padding(.horizontal, 40)
                    
                    // Profile summary cards
                    VStack(spacing: 16) {
                        // Photos
                        ProfileSummaryCard(
                            icon: "photo.fill",
                            title: "Photos",
                            value: "\(max(viewModel.selectedPhotos.count, viewModel.photoURLs.count)) photos selected",
                            gradient: [
                                Color.appPrimary,
                                Color.appPrimary
                            ]
                        )
                        
                        // Activities
                        VStack(alignment: .leading, spacing: 12) {
                            HStack(spacing: 12) {
                                ZStack {
                                    Circle()
                                        .fill(
                                            LinearGradient(
                                                colors: [
                                                    Color.appPrimary,
                                                    Color.appPrimary
                                                ],
                                                startPoint: .topLeading,
                                                endPoint: .bottomTrailing
                                            )
                                        )
                                        .frame(width: 44, height: 44)
                                    
                                    Image(systemName: "heart.fill")
                                        .font(.system(size: 20))
                                        .foregroundColor(.white)
                                }
                                
                                VStack(alignment: .leading, spacing: 4) {
                                    Text("Activities")
                                        .font(.system(size: 16, weight: .bold, design: .rounded))
                                        .foregroundColor(Color(red: 0.35, green: 0.35, blue: 0.35))
                                    
                                    Text("Your interests")
                                        .font(.system(size: 14, weight: .regular, design: .rounded))
                                        .foregroundColor(Color(red: 0.50, green: 0.50, blue: 0.50))
                                }
                                
                                Spacer()
                            }
                            
                            // Activity chips
                            FlowLayout(spacing: 8) {
                                ForEach(viewModel.selectedActivities, id: \.id) { activity in
                                    Text(activity.name)
                                        .font(.system(size: 14, weight: .semibold, design: .rounded))
                                        .foregroundColor(Color.appPrimary)
                                        .padding(.horizontal, 14)
                                        .padding(.vertical, 8)
                                        .background(Color.appPrimary.opacity(0.15))
                                        .cornerRadius(20)
                                }
                            }
                        }
                        .padding(16)
                        .background(Color.white.opacity(0.6))
                        .cornerRadius(16)
                        
                        // Time slots
                        VStack(alignment: .leading, spacing: 12) {
                            HStack(spacing: 12) {
                                ZStack {
                                    Circle()
                                        .fill(
                                            LinearGradient(
                                                colors: [
                                                    Color.appPrimary,
                                                    Color.appPrimary
                                                ],
                                                startPoint: .topLeading,
                                                endPoint: .bottomTrailing
                                            )
                                        )
                                        .frame(width: 44, height: 44)
                                    
                                    Image(systemName: "calendar.badge.clock")
                                        .font(.system(size: 20))
                                        .foregroundColor(.white)
                                }
                                
                                VStack(alignment: .leading, spacing: 4) {
                                    Text("Availability")
                                        .font(.system(size: 16, weight: .bold, design: .rounded))
                                        .foregroundColor(Color(red: 0.35, green: 0.35, blue: 0.35))
                                    
                                    Text("When you're free")
                                        .font(.system(size: 14, weight: .regular, design: .rounded))
                                        .foregroundColor(Color(red: 0.50, green: 0.50, blue: 0.50))
                                }
                                
                                Spacer()
                            }
                            
                            // Time slot chips
                            FlowLayout(spacing: 8) {
                                ForEach(viewModel.selectedDaySlotCombos, id: \.self) { combo in
                                    HStack(spacing: 4) {
                                        Text(combo.timeSlot.icon)
                                            .font(.system(size: 12))
                                        
                                        Text("\(combo.dayOfWeek.rawValue) \(combo.timeSlot.rawValue)")
                                            .font(.system(size: 13, weight: .semibold, design: .rounded))
                                    }
                                    .foregroundColor(Color.appPrimary)
                                    .padding(.horizontal, 12)
                                    .padding(.vertical, 8)
                                    .background(Color.appPrimary.opacity(0.15))
                                    .cornerRadius(20)
                                }
                            }
                        }
                        .padding(16)
                        .background(Color.white.opacity(0.6))
                        .cornerRadius(16)
                    }
                    .padding(.horizontal, 32)
                    .opacity(animateContent ? 1 : 0)
                    .offset(y: animateContent ? 0 : 30)
                    
                    // Encouragement message
                    VStack(spacing: 12) {
                        HStack(spacing: 8) {
                            Image(systemName: "sparkles")
                                .font(.system(size: 18))
                                .foregroundColor(Color.appPrimary)
                            
                            Text("What's Next?")
                                .font(.system(size: 16, weight: .semibold, design: .rounded))
                                .foregroundColor(Color(red: 0.35, green: 0.35, blue: 0.35))
                        }
                        
                        Text("We'll find people who share your interests and availability. Start browsing to discover potential friends nearby!")
                            .font(.system(size: 15, weight: .regular, design: .rounded))
                            .foregroundColor(Color(red: 0.50, green: 0.50, blue: 0.50))
                            .multilineTextAlignment(.center)
                            .lineSpacing(4)
                    }
                    .padding(20)
                    .frame(maxWidth: .infinity)
                    .background(Color.appPrimary.opacity(0.1))
                    .cornerRadius(16)
                    .padding(.horizontal, 32)
                    .opacity(animateContent ? 1 : 0)
                    .offset(y: animateContent ? 0 : 30)
                    
                    // Enter app button
                    Button {
                        onEnterApp()
                    } label: {
                        HStack(spacing: 8) {
                            if isSaving {
                                ProgressView()
                                    .tint(.white)
                                Text("Saving...")
                                    .font(.system(size: 18, weight: .semibold, design: .rounded))
                            } else {
                                Text("Enter ATX Friends")
                                    .font(.system(size: 18, weight: .semibold, design: .rounded))
                                
                                Image(systemName: "arrow.right")
                                    .font(.system(size: 16, weight: .semibold))
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
                    .disabled(isSaving)
                    .padding(.horizontal, 32)
                    .padding(.top, 8)
                    .opacity(animateButton ? 1 : 0)
                    .offset(y: animateButton ? 0 : 30)
                    
                    Spacer()
                        .frame(height: 40)
                }
            }
            .scrollIndicators(.hidden)
        }
        .onAppear {
            // Staggered entrance animations
            withAnimation(.spring(response: 0.6, dampingFraction: 0.6).delay(0.2)) {
                animateCheckmark = true
            }
            withAnimation(.spring(response: 0.6, dampingFraction: 0.8).delay(0.5)) {
                animateContent = true
            }
            withAnimation(.spring(response: 0.6, dampingFraction: 0.8).delay(0.8)) {
                animateButton = true
            }
        }
    }
}

/// Summary card for profile attributes
struct ProfileSummaryCard: View {
    let icon: String
    let title: String
    let value: String
    let gradient: [Color]
    
    var body: some View {
        HStack(spacing: 12) {
            ZStack {
                Circle()
                    .fill(
                        LinearGradient(
                            colors: gradient,
                            startPoint: .topLeading,
                            endPoint: .bottomTrailing
                        )
                    )
                    .frame(width: 44, height: 44)
                
                Image(systemName: icon)
                    .font(.system(size: 20))
                    .foregroundColor(.white)
            }
            
            VStack(alignment: .leading, spacing: 4) {
                Text(title)
                    .font(.system(size: 16, weight: .bold, design: .rounded))
                    .foregroundColor(Color(red: 0.35, green: 0.35, blue: 0.35))
                
                Text(value)
                    .font(.system(size: 14, weight: .regular, design: .rounded))
                    .foregroundColor(Color(red: 0.50, green: 0.50, blue: 0.50))
            }
            
            Spacer()
        }
        .padding(16)
        .background(Color.white.opacity(0.6))
        .cornerRadius(16)
    }
}

#Preview {
    // Create a sample ProfileViewModel with completed data
    let viewModel = ProfileViewModel()
    viewModel.displayName = "Alex"
    viewModel.photoURLs = ["url1", "url2", "url3"]
    viewModel.selectedActivities = [
        Activity(name: "Hiking", isUserAdded: false),
        Activity(name: "Coffee", isUserAdded: false),
        Activity(name: "Board Games", isUserAdded: false)
    ]
    viewModel.selectedDaySlotCombos = [
        DaySlotCombo(dayOfWeek: .saturday, timeSlot: .wakeUp),
        DaySlotCombo(dayOfWeek: .friday, timeSlot: .night),
        DaySlotCombo(dayOfWeek: .sunday, timeSlot: .afternoon)
    ]
    
    return ProfileCompletionView(viewModel: viewModel, isSaving: false) {
        print("Enter app tapped")
    }
}
