//
//  PrivacyAndSafetyView.swift
//  Avenue3
//
//  Created by George Anthony Pazdral II on 5/25/26.
//

import SwiftUI
import SafariServices

/// Main privacy and safety view with navigation to all privacy-related screens
struct PrivacyAndSafetyView: View {
    @State private var viewModel = PrivacyAndSafetyViewModel()
    @State private var showPrivacyPolicy = false
    
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
            
            ScrollView {
                VStack(spacing: 24) {
                    // Header description
                    VStack(spacing: 8) {
                        HStack(spacing: 8) {
                            Image(systemName: "hand.raised.fill")
                                .font(.system(size: 24))
                                .foregroundColor(Color.appPrimary)
                            
                            Text("Privacy & Safety")
                                .font(.system(size: 24, weight: .bold, design: .rounded))
                                .foregroundColor(Color.appPrimaryText)
                        }
                        
                        Text("Manage your privacy settings, blocked users, and account data.")
                            .font(.system(size: 15, weight: .regular, design: .rounded))
                            .foregroundColor(Color.appSecondaryText)
                            .multilineTextAlignment(.center)
                            .padding(.horizontal)
                    }
                    .padding(.top, 8)
                    
                    // Navigation options card
                    VStack(spacing: 0) {
                        // Block a User
                        NavigationLink {
                            BlockUserView(viewModel: viewModel)
                        } label: {
                            PrivacyRowLabel(
                                icon: "hand.raised.circle.fill",
                                title: "Block a User",
                                subtitle: "Block someone from your matches",
                                iconColor: Color.appIconWarm
                            )
                        }
                        
                        Divider()
                            .padding(.leading, 56)
                        
                        // Blocked Users
                        NavigationLink {
                            BlockedUsersView(viewModel: viewModel)
                        } label: {
                            PrivacyRowLabel(
                                icon: "person.2.slash.fill",
                                title: "Blocked Users",
                                subtitle: "Manage your blocked list",
                                iconColor: Color.appSecondaryText
                            )
                        }
                        
                        Divider()
                            .padding(.leading, 56)
                        
                        // Report a User
                        NavigationLink {
                            ReportUserView(viewModel: viewModel)
                        } label: {
                            PrivacyRowLabel(
                                icon: "exclamationmark.shield.fill",
                                title: "Report a User",
                                subtitle: "Report inappropriate behavior",
                                iconColor: Color.appDanger
                            )
                        }
                        
                        Divider()
                            .padding(.leading, 56)
                        
                        // Delete Account
                        NavigationLink {
                            DeleteAccountView(viewModel: viewModel)
                        } label: {
                            PrivacyRowLabel(
                                icon: "trash.circle.fill",
                                title: "Delete Account",
                                subtitle: "Permanently delete your data",
                                iconColor: Color.appDangerStrong
                            )
                        }
                        
                        Divider()
                            .padding(.leading, 56)
                        
                        // Export My Data
                        NavigationLink {
                            ExportDataView(viewModel: viewModel)
                        } label: {
                            PrivacyRowLabel(
                                icon: "square.and.arrow.up.circle.fill",
                                title: "Export My Data",
                                subtitle: "Download a copy of your data",
                                iconColor: Color.appIconInfo
                            )
                        }
                        
                        Divider()
                            .padding(.leading, 56)
                        
                        // How We Protect Your Data
                        Button {
                            showPrivacyPolicy = true
                        } label: {
                            PrivacyRowLabel(
                                icon: "lock.shield.fill",
                                title: "How We Protect Your Data",
                                subtitle: "Learn about our privacy practices",
                                iconColor: Color.appPrimary
                            )
                        }
                        .buttonStyle(.plain)
                    }
                    .padding()
                    .background(Color.appCardBackground.opacity(0.6))
                    .cornerRadius(16)
                    .padding(.horizontal, 20)
                }
                .padding(.vertical, 20)
            }
            .scrollIndicators(.hidden)
        }
        .navigationTitle("Privacy & Safety")
        .navigationBarTitleDisplayMode(.inline)
        .sheet(isPresented: $showPrivacyPolicy) {
            SafariView(url: URL(string: "https://avenue3.app/privacy.html")!)
        }
        .task {
            await viewModel.loadCurrentUser()
        }
    }
}

// MARK: - Privacy Row Label

struct PrivacyRowLabel: View {
    let icon: String
    let title: String
    let subtitle: String
    let iconColor: Color
    
    var body: some View {
        HStack(spacing: 12) {
            Image(systemName: icon)
                .font(.system(size: 22))
                .foregroundColor(iconColor)
                .frame(width: 32)
            
            VStack(alignment: .leading, spacing: 2) {
                Text(title)
                    .font(.system(size: 16, weight: .medium, design: .rounded))
                    .foregroundColor(Color.appTextStrong)
                
                Text(subtitle)
                    .font(.system(size: 14, weight: .regular, design: .rounded))
                    .foregroundColor(Color.appSecondaryText)
            }
            
            Spacer()
            
            Image(systemName: "chevron.right")
                .font(.system(size: 14, weight: .semibold))
                .foregroundColor(Color.appTextSubtle)
        }
        .padding(.vertical, 12)
        .contentShape(Rectangle())
    }
}

// MARK: - Placeholder Views

/// View for blocking a user from matches
struct BlockUserView: View {
    var viewModel: PrivacyAndSafetyViewModel
    @Environment(\.dismiss) private var dismiss
    
    @State private var selectedUser: FirebaseUser?
    @State private var showBlockConfirmation = false
    @State private var isBlocking = false
    @State private var showSuccessMessage = false
    @State private var blockedUserName = ""
    
    var body: some View {
        ZStack {
            LinearGradient(
                colors: [
                    Color.appBackground,
                    Color.appBackground
                ],
                startPoint: .topLeading,
                endPoint: .bottomTrailing
            )
            .ignoresSafeArea()
            
            if viewModel.isLoading {
                VStack(spacing: 16) {
                    ProgressView()
                        .scaleEffect(1.2)
                    Text("Loading matches...")
                        .font(.system(size: 16, weight: .medium, design: .rounded))
                        .foregroundColor(Color.appSecondaryText)
                }
            } else if viewModel.searchableContacts.isEmpty && viewModel.pendingMatches.isEmpty {
                // Empty state
                VStack(spacing: 20) {
                    Image(systemName: "person.2.slash")
                        .font(.system(size: 60))
                        .foregroundColor(Color.appSecondaryText)
                    
                    Text("No Matches to Block")
                        .font(.system(size: 24, weight: .bold, design: .rounded))
                        .foregroundColor(Color.appTextStrong)
                    
                    Text("You don't have any current matches. Users you block will appear here.")
                        .font(.system(size: 16, weight: .regular, design: .rounded))
                        .foregroundColor(Color.appSecondaryText)
                        .multilineTextAlignment(.center)
                        .padding(.horizontal, 40)
                }
            } else {
                // List of matches
                ScrollView {
                    VStack(spacing: 20) {
                        // Header
                        VStack(spacing: 8) {
                            Image(systemName: "hand.raised.circle.fill")
                                .font(.system(size: 40))
                                .foregroundColor(Color.appIconWarm)
                            
                            Text("Select a user to block")
                                .font(.system(size: 16, weight: .medium, design: .rounded))
                                .foregroundColor(Color.appSecondaryText)
                                .multilineTextAlignment(.center)
                        }
                        .padding(.top, 16)
                        .padding(.bottom, 8)
                        
                        // Pending Matches Section
                        if !viewModel.pendingMatches.isEmpty {
                            VStack(alignment: .leading, spacing: 12) {
                                // Section header
                                Text("Matches")
                                    .font(.system(size: 20, weight: .bold, design: .rounded))
                                    .foregroundColor(Color.appTextStrong)
                                    .padding(.horizontal, 20)
                                
                                // User list
                                VStack(spacing: 0) {
                                    ForEach(viewModel.pendingMatches) { user in
                                        Button {
                                            selectedUser = user
                                            showBlockConfirmation = true
                                        } label: {
                                            BlockUserRow(user: user)
                                        }
                                        .buttonStyle(.plain)
                                        
                                        if user.id != viewModel.pendingMatches.last?.id {
                                            Divider()
                                                .padding(.leading, 76)
                                        }
                                    }
                                }
                                .padding()
                                .background(Color.appCardBackground.opacity(0.6))
                                .cornerRadius(16)
                                .padding(.horizontal, 20)
                            }
                        }
                        
                        // Current Connections Section
                        if !viewModel.searchableContacts.isEmpty {
                            VStack(alignment: .leading, spacing: 12) {
                                // Section header
                                Text("Current Connections")
                                    .font(.system(size: 20, weight: .bold, design: .rounded))
                                    .foregroundColor(Color.appTextStrong)
                                    .padding(.horizontal, 20)
                                
                                // User list
                                VStack(spacing: 0) {
                                    ForEach(viewModel.searchableContacts) { user in
                                        Button {
                                            selectedUser = user
                                            showBlockConfirmation = true
                                        } label: {
                                            BlockUserRow(user: user)
                                        }
                                        .buttonStyle(.plain)
                                        
                                        if user.id != viewModel.searchableContacts.last?.id {
                                            Divider()
                                                .padding(.leading, 76)
                                        }
                                    }
                                }
                                .padding()
                                .background(Color.appCardBackground.opacity(0.6))
                                .cornerRadius(16)
                                .padding(.horizontal, 20)
                            }
                        }
                    }
                    .padding(.vertical, 20)
                }
                .scrollIndicators(.hidden)
            }
            
            // Success overlay
            if showSuccessMessage {
                VStack(spacing: 16) {
                    Image(systemName: "checkmark.circle.fill")
                        .font(.system(size: 60))
                        .foregroundColor(Color.appIconSafe)
                    
                    Text("User Blocked")
                        .font(.system(size: 20, weight: .bold, design: .rounded))
                        .foregroundColor(Color.appTextStrong)
                    
                    Text("\(blockedUserName) has been blocked")
                        .font(.system(size: 16, weight: .regular, design: .rounded))
                        .foregroundColor(Color.appSecondaryText)
                }
                .padding(32)
                .background(Color.appCardBackground.opacity(0.95))
                .cornerRadius(20)
                .shadow(radius: 20)
            }
        }
        .navigationTitle("Block a User")
        .navigationBarTitleDisplayMode(.inline)
        .task {
            await viewModel.loadSearchableContacts()
        }
        .alert("Block \(selectedUser?.displayName ?? "User")?", isPresented: $showBlockConfirmation) {
            Button("Cancel", role: .cancel) {
                selectedUser = nil
            }
            
            Button("Block", role: .destructive) {
                Task {
                    await blockSelectedUser()
                }
            }
        } message: {
            Text("They won't be able to see your profile or contact you.")
        }
        .disabled(isBlocking)
    }
    
    private func blockSelectedUser() async {
        guard let user = selectedUser else { return }
        
        isBlocking = true
        blockedUserName = user.displayName
        
        let success = await viewModel.blockUser(user)
        
        isBlocking = false
        selectedUser = nil
        
        if success {
            // Show success message
            withAnimation {
                showSuccessMessage = true
            }
            
            // Hide success message after 1.5 seconds
            try? await Task.sleep(nanoseconds: 1_500_000_000)
            
            withAnimation {
                showSuccessMessage = false
            }
            
            // If no more contacts, dismiss after showing success
            if viewModel.searchableContacts.isEmpty && viewModel.pendingMatches.isEmpty {
                try? await Task.sleep(nanoseconds: 500_000_000)
                dismiss()
            }
        }
    }
}

// MARK: - Block User Row

struct BlockUserRow: View {
    let user: FirebaseUser
    
    var body: some View {
        HStack(spacing: 12) {
            // User photo
            if let firstPhotoURL = user.photoURLs.first,
               let url = URL(string: firstPhotoURL) {
                AsyncImage(url: url) { phase in
                    switch phase {
                    case .empty:
                        Rectangle()
                            .fill(Color.appBorder)
                            .overlay {
                                ProgressView()
                            }
                    case .success(let image):
                        image
                            .resizable()
                            .aspectRatio(contentMode: .fill)
                    case .failure:
                        Rectangle()
                            .fill(Color.appBorder)
                            .overlay {
                                Image(systemName: "person.fill")
                                    .foregroundColor(.white)
                                    .font(.system(size: 20))
                            }
                    @unknown default:
                        Rectangle()
                            .fill(Color.appBorder)
                    }
                }
                .frame(width: 52, height: 52)
                .clipShape(Circle())
            } else {
                // Placeholder when no photo
                Circle()
                    .fill(Color.appBorder)
                    .frame(width: 52, height: 52)
                    .overlay {
                        Image(systemName: "person.fill")
                            .foregroundColor(.white)
                            .font(.system(size: 20))
                    }
            }
            
            // User name
            Text(user.displayName)
                .font(.system(size: 17, weight: .medium, design: .rounded))
                .foregroundColor(Color.appTextStrong)
            
            Spacer()
            
            // Block icon
            Image(systemName: "hand.raised.circle.fill")
                .font(.system(size: 20))
                .foregroundColor(Color.appIconWarm)
        }
        .padding(.vertical, 12)
        .contentShape(Rectangle())
    }
}

/// View for managing blocked users list
struct BlockedUsersView: View {
    var viewModel: PrivacyAndSafetyViewModel
    @Environment(\.dismiss) private var dismiss
    
    @State private var userToUnblock: FirebaseUser?
    @State private var showUnblockConfirmation = false
    @State private var isUnblocking = false
    @State private var showSuccessMessage = false
    @State private var unblockedUserName = ""
    
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
            
            if viewModel.isLoading {
                // Loading state
                VStack(spacing: 16) {
                    ProgressView()
                        .scaleEffect(1.2)
                    Text("Loading blocked users...")
                        .font(.system(size: 16, weight: .medium, design: .rounded))
                        .foregroundColor(Color.appSecondaryText)
                }
            } else if viewModel.blockedUsers.isEmpty {
                // Empty state
                VStack(spacing: 20) {
                    Image(systemName: "person.2.slash")
                        .font(.system(size: 60))
                        .foregroundColor(Color.appSecondaryText)
                    
                    Text("No Blocked Users")
                        .font(.system(size: 24, weight: .bold, design: .rounded))
                        .foregroundColor(Color.appTextStrong)
                    
                    Text("You haven't blocked anyone yet. Users you block won't be able to see your profile or contact you.")
                        .font(.system(size: 16, weight: .regular, design: .rounded))
                        .foregroundColor(Color.appSecondaryText)
                        .multilineTextAlignment(.center)
                        .padding(.horizontal, 40)
                }
            } else {
                // List of blocked users
                ScrollView {
                    VStack(spacing: 20) {
                        // Header
                        VStack(spacing: 8) {
                            Image(systemName: "person.2.slash.fill")
                                .font(.system(size: 40))
                                .foregroundColor(Color.appSecondaryText)
                            
                            Text("Blocked Users")
                                .font(.system(size: 20, weight: .bold, design: .rounded))
                                .foregroundColor(Color.appTextStrong)
                            
                            Text("These users can't see your profile or contact you")
                                .font(.system(size: 15, weight: .regular, design: .rounded))
                                .foregroundColor(Color.appSecondaryText)
                                .multilineTextAlignment(.center)
                                .padding(.horizontal)
                        }
                        .padding(.top, 16)
                        .padding(.bottom, 8)
                        
                        // Blocked users list
                        VStack(spacing: 0) {
                            ForEach(viewModel.blockedUsers) { user in
                                BlockedUserRow(
                                    user: user,
                                    onUnblock: {
                                        userToUnblock = user
                                        showUnblockConfirmation = true
                                    }
                                )
                                
                                if user.id != viewModel.blockedUsers.last?.id {
                                    Divider()
                                        .padding(.leading, 76)
                                }
                            }
                        }
                        .padding()
                        .background(Color.appCardBackground.opacity(0.6))
                        .cornerRadius(16)
                        .padding(.horizontal, 20)
                    }
                    .padding(.vertical, 20)
                }
                .scrollIndicators(.hidden)
            }
            
            // Success overlay
            if showSuccessMessage {
                VStack(spacing: 16) {
                    Image(systemName: "checkmark.circle.fill")
                        .font(.system(size: 60))
                        .foregroundColor(Color.appIconSafe)
                    
                    Text("User Unblocked")
                        .font(.system(size: 20, weight: .bold, design: .rounded))
                        .foregroundColor(Color.appTextStrong)
                    
                    Text("\(unblockedUserName) has been unblocked")
                        .font(.system(size: 16, weight: .regular, design: .rounded))
                        .foregroundColor(Color.appSecondaryText)
                }
                .padding(32)
                .background(Color.appCardBackground.opacity(0.95))
                .cornerRadius(20)
                .shadow(radius: 20)
            }
        }
        .navigationTitle("Blocked Users")
        .navigationBarTitleDisplayMode(.inline)
        .task {
            await viewModel.loadBlockedUsers()
        }
        .alert("Unblock \(userToUnblock?.displayName ?? "User")?", isPresented: $showUnblockConfirmation) {
            Button("Cancel", role: .cancel) {
                userToUnblock = nil
            }
            
            Button("Unblock") {
                Task {
                    await unblockSelectedUser()
                }
            }
        } message: {
            Text("They'll be able to see your profile and contact you again.")
        }
        .disabled(isUnblocking)
    }
    
    private func unblockSelectedUser() async {
        guard let user = userToUnblock else { return }
        
        isUnblocking = true
        unblockedUserName = user.displayName
        
        let success = await viewModel.unblockUser(user)
        
        isUnblocking = false
        userToUnblock = nil
        
        if success {
            // Post notification to refresh other views (e.g., Matches)
            NotificationCenter.default.post(name: NSNotification.Name("userBlocked"), object: nil)
            
            // Show success message
            withAnimation {
                showSuccessMessage = true
            }
            
            // Hide success message after 1.5 seconds
            try? await Task.sleep(nanoseconds: 1_500_000_000)
            
            withAnimation {
                showSuccessMessage = false
            }
        }
    }
}

// MARK: - Blocked User Row

struct BlockedUserRow: View {
    let user: FirebaseUser
    let onUnblock: () -> Void
    
    var body: some View {
        HStack(spacing: 12) {
            // User photo
            if let firstPhotoURL = user.photoURLs.first,
               let url = URL(string: firstPhotoURL) {
                AsyncImage(url: url) { phase in
                    switch phase {
                    case .empty:
                        Rectangle()
                            .fill(Color.appBorder)
                            .overlay {
                                ProgressView()
                            }
                    case .success(let image):
                        image
                            .resizable()
                            .aspectRatio(contentMode: .fill)
                    case .failure:
                        Rectangle()
                            .fill(Color.appBorder)
                            .overlay {
                                Image(systemName: "person.fill")
                                    .foregroundColor(.white)
                                    .font(.system(size: 20))
                            }
                    @unknown default:
                        Rectangle()
                            .fill(Color.appBorder)
                    }
                }
                .frame(width: 52, height: 52)
                .clipShape(Circle())
            } else {
                // Placeholder when no photo
                Circle()
                    .fill(Color.appBorder)
                    .frame(width: 52, height: 52)
                    .overlay {
                        Image(systemName: "person.fill")
                            .foregroundColor(.white)
                            .font(.system(size: 20))
                    }
            }
            
            // User name
            Text(user.displayName)
                .font(.system(size: 17, weight: .medium, design: .rounded))
                .foregroundColor(Color.appTextStrong)
            
            Spacer()
            
            // Unblock button
            Button(action: onUnblock) {
                Text("Unblock")
                    .font(.system(size: 15, weight: .semibold, design: .rounded))
                    .foregroundColor(.white)
                    .padding(.horizontal, 16)
                    .padding(.vertical, 8)
                    .background(Color.appPrimary)
                    .cornerRadius(8)
            }
            .buttonStyle(.plain)
        }
        .padding(.vertical, 12)
        .contentShape(Rectangle())
    }
}

/// View for reporting a user - Two-step process
struct ReportUserView: View {
    var viewModel: PrivacyAndSafetyViewModel
    @Environment(\.dismiss) private var dismiss
    
    @State private var selectedUser: FirebaseUser?
    @State private var selectedReason: ReportReason?
    @State private var additionalComments = ""
    @State private var isSubmitting = false
    @State private var showSuccessMessage = false
    @State private var showStep2 = false
    
    var body: some View {
        ZStack {
            LinearGradient(
                colors: [
                    Color.appBackground,
                    Color.appBackground
                ],
                startPoint: .topLeading,
                endPoint: .bottomTrailing
            )
            .ignoresSafeArea()
            
            if showSuccessMessage {
                // Success message overlay
                VStack(spacing: 20) {
                    Image(systemName: "checkmark.circle.fill")
                        .font(.system(size: 60))
                        .foregroundColor(Color.appIconSafe)
                    
                    Text("Report Submitted")
                        .font(.system(size: 22, weight: .bold, design: .rounded))
                        .foregroundColor(Color.appTextStrong)
                    
                    Text("We take all reports seriously and will review this promptly.")
                        .font(.system(size: 16, weight: .regular, design: .rounded))
                        .foregroundColor(Color.appSecondaryText)
                        .multilineTextAlignment(.center)
                        .padding(.horizontal, 32)
                }
                .padding(32)
                .background(Color.appCardBackground.opacity(0.95))
                .cornerRadius(20)
                .shadow(radius: 20)
                .padding(.horizontal, 40)
            } else if !showStep2 {
                // Step 1: Select user to report
                step1SelectUserView
            } else {
                // Step 2: Select reason and submit
                step2SelectReasonView
            }
        }
        .navigationTitle("Report a User")
        .navigationBarTitleDisplayMode(.inline)
        .navigationBarBackButtonHidden(showStep2)
        .toolbar {
            if showStep2 {
                ToolbarItem(placement: .navigationBarLeading) {
                    Button("Back") {
                        withAnimation {
                            showStep2 = false
                        }
                    }
                }
            }
        }
        .task {
            await viewModel.loadSearchableContacts()
        }
    }
    
    // MARK: - Step 1: Select User
    
    @ViewBuilder
    private var step1SelectUserView: some View {
        if viewModel.isLoading {
            VStack(spacing: 16) {
                ProgressView()
                    .scaleEffect(1.2)
                Text("Loading matches...")
                    .font(.system(size: 16, weight: .medium, design: .rounded))
                    .foregroundColor(Color.appSecondaryText)
            }
        } else if viewModel.searchableContacts.isEmpty && viewModel.pendingMatches.isEmpty {
            // Empty state
            VStack(spacing: 20) {
                Image(systemName: "person.2.badge.gearshape")
                    .font(.system(size: 60))
                    .foregroundColor(Color.appSecondaryText)
                
                Text("No Matches to Report")
                    .font(.system(size: 24, weight: .bold, design: .rounded))
                    .foregroundColor(Color.appTextStrong)
                
                Text("You don't have any current matches. Only users you've matched with can be reported.")
                    .font(.system(size: 16, weight: .regular, design: .rounded))
                    .foregroundColor(Color.appSecondaryText)
                    .multilineTextAlignment(.center)
                    .padding(.horizontal, 40)
            }
        } else {
            // List of matches
            ScrollView {
                VStack(spacing: 20) {
                    // Header
                    VStack(spacing: 8) {
                        Image(systemName: "exclamationmark.shield.fill")
                            .font(.system(size: 40))
                            .foregroundColor(Color.appDanger)
                        
                        Text("Select a user to report")
                            .font(.system(size: 16, weight: .medium, design: .rounded))
                            .foregroundColor(Color.appSecondaryText)
                            .multilineTextAlignment(.center)
                    }
                    .padding(.top, 16)
                    .padding(.bottom, 8)
                    
                    // Pending Matches Section
                    if !viewModel.pendingMatches.isEmpty {
                        VStack(alignment: .leading, spacing: 12) {
                            // Section header
                            Text("Matches")
                                .font(.system(size: 20, weight: .bold, design: .rounded))
                                .foregroundColor(Color.appTextStrong)
                                .padding(.horizontal, 20)
                            
                            // User list
                            VStack(spacing: 0) {
                                ForEach(viewModel.pendingMatches) { user in
                                    Button {
                                        selectedUser = user
                                        withAnimation {
                                            showStep2 = true
                                        }
                                    } label: {
                                        ReportUserRow(user: user)
                                    }
                                    .buttonStyle(.plain)
                                    
                                    if user.id != viewModel.pendingMatches.last?.id {
                                        Divider()
                                            .padding(.leading, 76)
                                    }
                                }
                            }
                            .padding()
                            .background(Color.appCardBackground.opacity(0.6))
                            .cornerRadius(16)
                            .padding(.horizontal, 20)
                        }
                    }
                    
                    // Current Connections Section
                    if !viewModel.searchableContacts.isEmpty {
                        VStack(alignment: .leading, spacing: 12) {
                            // Section header
                            Text("Current Connections")
                                .font(.system(size: 20, weight: .bold, design: .rounded))
                                .foregroundColor(Color.appTextStrong)
                                .padding(.horizontal, 20)
                            
                            // User list
                            VStack(spacing: 0) {
                                ForEach(viewModel.searchableContacts) { user in
                                    Button {
                                        selectedUser = user
                                        withAnimation {
                                            showStep2 = true
                                        }
                                    } label: {
                                        ReportUserRow(user: user)
                                    }
                                    .buttonStyle(.plain)
                                    
                                    if user.id != viewModel.searchableContacts.last?.id {
                                        Divider()
                                            .padding(.leading, 76)
                                    }
                                }
                            }
                            .padding()
                            .background(Color.appCardBackground.opacity(0.6))
                            .cornerRadius(16)
                            .padding(.horizontal, 20)
                        }
                    }
                }
                .padding(.vertical, 20)
            }
            .scrollIndicators(.hidden)
        }
    }
    
    // MARK: - Step 2: Select Reason
    
    private var step2SelectReasonView: some View {
        ScrollView {
            VStack(spacing: 24) {
                // Header with selected user info
                VStack(spacing: 12) {
                    // User photo
                    if let firstPhotoURL = selectedUser?.photoURLs.first,
                       let url = URL(string: firstPhotoURL) {
                        AsyncImage(url: url) { phase in
                            switch phase {
                            case .empty:
                                Circle()
                                    .fill(Color.appBorder)
                                    .overlay {
                                        ProgressView()
                                    }
                            case .success(let image):
                                image
                                    .resizable()
                                    .aspectRatio(contentMode: .fill)
                                    .frame(width: 80, height: 80)
                                    .clipShape(Circle())
                            case .failure:
                                Circle()
                                    .fill(Color.appBorder)
                                    .frame(width: 80, height: 80)
                                    .overlay {
                                        Image(systemName: "person.fill")
                                            .foregroundColor(.white)
                                            .font(.system(size: 32))
                                    }
                            @unknown default:
                                Circle()
                                    .fill(Color.appBorder)
                                    .frame(width: 80, height: 80)
                            }
                        }
                        .frame(width: 80, height: 80)
                    } else {
                        Circle()
                            .fill(Color.appBorder)
                            .frame(width: 80, height: 80)
                            .overlay {
                                Image(systemName: "person.fill")
                                    .foregroundColor(.white)
                                    .font(.system(size: 32))
                            }
                    }
                    
                    Text("Reporting \(selectedUser?.displayName ?? "User")")
                        .font(.system(size: 20, weight: .bold, design: .rounded))
                        .foregroundColor(Color.appTextStrong)
                    
                    Text("Select a reason for your report")
                        .font(.system(size: 16, weight: .regular, design: .rounded))
                        .foregroundColor(Color.appSecondaryText)
                }
                .padding(.top, 16)
                
                // Report reasons
                VStack(spacing: 0) {
                    ForEach(ReportReason.allCases, id: \.self) { reason in
                        Button {
                            selectedReason = reason
                        } label: {
                            ReportReasonRow(
                                reason: reason,
                                isSelected: selectedReason == reason
                            )
                        }
                        .buttonStyle(.plain)
                        
                        if reason != ReportReason.allCases.last {
                            Divider()
                                .padding(.leading, 20)
                        }
                    }
                }
                .padding()
                .background(Color.appCardBackground.opacity(0.6))
                .cornerRadius(16)
                .padding(.horizontal, 20)
                
                // Additional comments
                VStack(alignment: .leading, spacing: 12) {
                    Text("Additional Comments (Optional)")
                        .font(.system(size: 16, weight: .semibold, design: .rounded))
                        .foregroundColor(Color.appTextStrong)
                        .padding(.horizontal, 20)
                    
                    TextEditor(text: $additionalComments)
                        .frame(minHeight: 120)
                        .padding(12)
                        .background(Color.appCardBackground.opacity(0.6))
                        .cornerRadius(12)
                        .overlay(
                            RoundedRectangle(cornerRadius: 12)
                                .stroke(Color.appBorder, lineWidth: 1)
                        )
                        .font(.system(size: 16, weight: .regular, design: .rounded))
                        .foregroundColor(Color.appTextStrong)
                        .scrollContentBackground(.hidden)
                        .padding(.horizontal, 20)
                }
                
                // Submit button
                Button {
                    Task {
                        await submitReport()
                    }
                } label: {
                    HStack(spacing: 8) {
                        if isSubmitting {
                            ProgressView()
                                .tint(.white)
                        } else {
                            Image(systemName: "paperplane.fill")
                        }
                        
                        Text(isSubmitting ? "Submitting..." : "Submit Report")
                            .font(.system(size: 17, weight: .semibold, design: .rounded))
                    }
                    .foregroundColor(.white)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 16)
                    .background(
                        selectedReason != nil ?
                        Color.appDanger :
                            Color.appTextSubtle
                    )
                    .cornerRadius(12)
                }
                .disabled(selectedReason == nil || isSubmitting)
                .padding(.horizontal, 20)
                .padding(.top, 8)
            }
            .padding(.vertical, 20)
        }
        .scrollIndicators(.hidden)
    }
    
    // MARK: - Actions
    
    private func submitReport() async {
        guard let user = selectedUser, let reason = selectedReason else { return }
        
        isSubmitting = true
        
        let success = await viewModel.submitReport(
            reportedUser: user,
            reason: reason,
            comments: additionalComments
        )
        
        isSubmitting = false
        
        if success {
            // Show success message
            withAnimation {
                showSuccessMessage = true
            }
            
            // Dismiss after 2.5 seconds
            try? await Task.sleep(nanoseconds: 2_500_000_000)
            
            dismiss()
        }
    }
}

// MARK: - Report User Row

struct ReportUserRow: View {
    let user: FirebaseUser
    
    var body: some View {
        HStack(spacing: 12) {
            // User photo
            if let firstPhotoURL = user.photoURLs.first,
               let url = URL(string: firstPhotoURL) {
                AsyncImage(url: url) { phase in
                    switch phase {
                    case .empty:
                        Rectangle()
                            .fill(Color.appBorder)
                            .overlay {
                                ProgressView()
                            }
                    case .success(let image):
                        image
                            .resizable()
                            .aspectRatio(contentMode: .fill)
                    case .failure:
                        Rectangle()
                            .fill(Color.appBorder)
                            .overlay {
                                Image(systemName: "person.fill")
                                    .foregroundColor(.white)
                                    .font(.system(size: 20))
                            }
                    @unknown default:
                        Rectangle()
                            .fill(Color.appBorder)
                    }
                }
                .frame(width: 52, height: 52)
                .clipShape(Circle())
            } else {
                // Placeholder when no photo
                Circle()
                    .fill(Color.appBorder)
                    .frame(width: 52, height: 52)
                    .overlay {
                        Image(systemName: "person.fill")
                            .foregroundColor(.white)
                            .font(.system(size: 20))
                    }
            }
            
            // User name
            Text(user.displayName)
                .font(.system(size: 17, weight: .medium, design: .rounded))
                .foregroundColor(Color.appTextStrong)
            
            Spacer()
            
            // Report icon
            Image(systemName: "exclamationmark.shield.fill")
                .font(.system(size: 20))
                .foregroundColor(Color.appDanger)
        }
        .padding(.vertical, 12)
        .contentShape(Rectangle())
    }
}

// MARK: - Report Reason Row

struct ReportReasonRow: View {
    let reason: ReportReason
    let isSelected: Bool
    
    var body: some View {
        HStack(spacing: 12) {
            // Selection indicator
            ZStack {
                Circle()
                    .strokeBorder(
                        isSelected ?
                        Color.appDanger :
                            Color.appTextSubtle,
                        lineWidth: 2
                    )
                    .frame(width: 24, height: 24)
                
                if isSelected {
                    Circle()
                        .fill(Color.appDanger)
                        .frame(width: 14, height: 14)
                }
            }
            
            // Reason text
            Text(reason.rawValue)
                .font(.system(size: 17, weight: isSelected ? .semibold : .regular, design: .rounded))
                .foregroundColor(Color.appTextStrong)
            
            Spacer()
        }
        .padding(.vertical, 12)
        .contentShape(Rectangle())
    }
}

/// View for deleting user account with 30-day grace period
struct DeleteAccountView: View {
    var viewModel: PrivacyAndSafetyViewModel
    @Environment(\.dismiss) private var dismiss
    
    @State private var confirmationText = ""
    @State private var isDeleting = false
    @State private var showError = false
    @State private var errorMessage = ""
    
    private var isConfirmationValid: Bool {
        confirmationText == "DELETE"
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
            
            ScrollView {
                VStack(spacing: 24) {
                    // Warning Header
                    VStack(spacing: 16) {
                        Image(systemName: "exclamationmark.triangle.fill")
                            .font(.system(size: 60))
                            .foregroundColor(Color.appDangerStrong)
                        
                        Text("Delete Account")
                            .font(.system(size: 28, weight: .bold, design: .rounded))
                            .foregroundColor(Color.appTextStrong)
                        
                        Text("This action will schedule your account for permanent deletion")
                            .font(.system(size: 16, weight: .medium, design: .rounded))
                            .foregroundColor(Color.appDangerStrong)
                            .multilineTextAlignment(.center)
                            .padding(.horizontal, 20)
                    }
                    .padding(.top, 16)
                    
                    // 30-Day Grace Period Info
                    VStack(alignment: .leading, spacing: 12) {
                        HStack(spacing: 12) {
                            Image(systemName: "calendar.badge.clock")
                                .font(.system(size: 24))
                                .foregroundColor(Color.appIconInfo)
                            
                            VStack(alignment: .leading, spacing: 4) {
                                Text("30-Day Grace Period")
                                    .font(.system(size: 18, weight: .bold, design: .rounded))
                                    .foregroundColor(Color.appTextStrong)
                                
                                Text("Your account will be scheduled for deletion in 30 days. You can cancel this by signing back in before the deletion date.")
                                    .font(.system(size: 15, weight: .regular, design: .rounded))
                                    .foregroundColor(Color.appSecondaryText)
                                    .lineSpacing(4)
                            }
                        }
                    }
                    .padding()
                    .background(Color.appCardBackground.opacity(0.6))
                    .cornerRadius(16)
                    .padding(.horizontal, 20)
                    
                    // What Will Be Deleted
                    VStack(alignment: .leading, spacing: 16) {
                        Text("What will be deleted:")
                            .font(.system(size: 20, weight: .bold, design: .rounded))
                            .foregroundColor(Color.appTextStrong)
                            .padding(.horizontal, 20)
                        
                        VStack(spacing: 0) {
                            DeletedDataRow(
                                icon: "person.fill",
                                title: "Profile",
                                description: "Your name, bio, and all profile information"
                            )
                            
                            Divider()
                                .padding(.leading, 56)
                            
                            DeletedDataRow(
                                icon: "photo.fill",
                                title: "Photos",
                                description: "All photos uploaded to your profile"
                            )
                            
                            Divider()
                                .padding(.leading, 56)
                            
                            DeletedDataRow(
                                icon: "person.2.fill",
                                title: "Matches",
                                description: "All your matches and connections"
                            )
                            
                            Divider()
                                .padding(.leading, 56)
                            
                            DeletedDataRow(
                                icon: "message.fill",
                                title: "Messages",
                                description: "All conversations and message history"
                            )
                            
                            Divider()
                                .padding(.leading, 56)
                            
                            DeletedDataRow(
                                icon: "calendar.badge.exclamationmark",
                                title: "Plans",
                                description: "All proposed and confirmed plans"
                            )
                        }
                        .padding()
                        .background(Color.appCardBackground.opacity(0.6))
                        .cornerRadius(16)
                        .padding(.horizontal, 20)
                    }
                    
                    // Confirmation Section
                    VStack(alignment: .leading, spacing: 16) {
                        Text("Type DELETE to confirm")
                            .font(.system(size: 20, weight: .bold, design: .rounded))
                            .foregroundColor(Color.appTextStrong)
                            .padding(.horizontal, 20)
                        
                        VStack(alignment: .leading, spacing: 12) {
                            Text("To confirm deletion, please type DELETE in the field below:")
                                .font(.system(size: 15, weight: .regular, design: .rounded))
                                .foregroundColor(Color.appSecondaryText)
                            
                            TextField("Type DELETE", text: $confirmationText)
                                .font(.system(size: 17, weight: .medium, design: .rounded))
                                .foregroundColor(Color.appTextStrong)
                                .padding()
                                .background(Color.appCardBackground.opacity(0.8))
                                .cornerRadius(12)
                                .overlay(
                                    RoundedRectangle(cornerRadius: 12)
                                        .stroke(
                                            isConfirmationValid ?
                                            Color.appDangerStrong :
                                                Color.appBorder,
                                            lineWidth: isConfirmationValid ? 2 : 1
                                        )
                                )
                                .autocorrectionDisabled()
                                .textInputAutocapitalization(.characters)
                        }
                        .padding()
                        .background(Color.appCardBackground.opacity(0.6))
                        .cornerRadius(16)
                        .padding(.horizontal, 20)
                    }
                    
                    // Delete Button
                    Button {
                        Task {
                            await scheduleAccountDeletion()
                        }
                    } label: {
                        HStack(spacing: 8) {
                            if isDeleting {
                                ProgressView()
                                    .tint(.white)
                            } else {
                                Image(systemName: "trash.fill")
                            }
                            
                            Text(isDeleting ? "Scheduling Deletion..." : "Delete My Account")
                                .font(.system(size: 17, weight: .bold, design: .rounded))
                        }
                        .foregroundColor(.white)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 16)
                        .background(
                            isConfirmationValid ?
                            Color.appDangerStrong :
                                Color.appTextSubtle
                        )
                        .cornerRadius(12)
                    }
                    .disabled(!isConfirmationValid || isDeleting)
                    .padding(.horizontal, 20)
                    
                    // Cancel Note
                    Text("Changed your mind? You can cancel by signing back in within 30 days.")
                        .font(.system(size: 14, weight: .regular, design: .rounded))
                        .foregroundColor(Color.appSecondaryText)
                        .multilineTextAlignment(.center)
                        .padding(.horizontal, 40)
                        .padding(.bottom, 20)
                }
                .padding(.vertical, 20)
            }
            .scrollIndicators(.hidden)
        }
        .navigationTitle("Delete Account")
        .navigationBarTitleDisplayMode(.inline)
        .alert("Error", isPresented: $showError) {
            Button("OK", role: .cancel) {}
        } message: {
            Text(errorMessage)
        }
    }
    
    // MARK: - Actions
    
    private func scheduleAccountDeletion() async {
        print("🗑️ VIEW: scheduleAccountDeletion() called in DeleteAccountView")
        isDeleting = true
        
        print("🗑️ VIEW: Calling viewModel.scheduleAccountDeletion()")
        // Schedule account deletion in Firestore
        let success = await viewModel.scheduleAccountDeletion()
        
        print("🗑️ VIEW: viewModel.scheduleAccountDeletion() returned: \(success)")
        
        if success {
            print("✅ VIEW: Account successfully scheduled for deletion")
            print("🗑️ VIEW: Attempting to sign out user")
            
            // Sign out the user
            do {
                print("🗑️ VIEW: Calling FirebaseAuthService.shared.signOut()")
                try FirebaseAuthService.shared.signOut()
                print("✅ VIEW: Sign out successful! Posting userSignedOut notification")
                
                // Post notification to trigger navigation to auth screen
                NotificationCenter.default.post(name: NSNotification.Name("userSignedOut"), object: nil)
                print("✅ VIEW: userSignedOut notification posted")
                
                // Reset state
                isDeleting = false
                
            } catch {
                print("❌ VIEW: Sign out failed!")
                print("❌ VIEW: Sign out error type: \(type(of: error))")
                print("❌ VIEW: Sign out error: \(error)")
                print("❌ VIEW: Sign out error localized: \(error.localizedDescription)")
                
                isDeleting = false
                errorMessage = "Account scheduled for deletion, but sign out failed. Please sign out manually."
                showError = true
            }
        } else {
            print("❌ VIEW: Failed to schedule account deletion")
            print("❌ VIEW: Error message from viewModel: \(viewModel.errorMessage ?? "No error message")")
            
            isDeleting = false
            errorMessage = viewModel.errorMessage ?? "Failed to schedule account deletion. Please try again."
            showError = true
        }
        
        print("🗑️ VIEW: scheduleAccountDeletion() function complete")
    }
}

// MARK: - Deleted Data Row

struct DeletedDataRow: View {
    let icon: String
    let title: String
    let description: String
    
    var body: some View {
        HStack(spacing: 12) {
            Image(systemName: icon)
                .font(.system(size: 22))
                .foregroundColor(Color.appDangerStrong)
                .frame(width: 32)
            
            VStack(alignment: .leading, spacing: 2) {
                Text(title)
                    .font(.system(size: 16, weight: .medium, design: .rounded))
                    .foregroundColor(Color.appTextStrong)
                
                Text(description)
                    .font(.system(size: 14, weight: .regular, design: .rounded))
                    .foregroundColor(Color.appSecondaryText)
            }
            
            Spacer()
        }
        .padding(.vertical, 12)
    }
}

/// View for exporting user data
struct ExportDataView: View {
    var viewModel: PrivacyAndSafetyViewModel
    @Environment(\.dismiss) private var dismiss
    
    @State private var isExporting = false
    @State private var exportedText: String?
    @State private var showShareSheet = false
    @State private var showError = false
    @State private var errorMessage = ""
    
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
            
            ScrollView {
                VStack(spacing: 24) {
                    // Header
                    VStack(spacing: 16) {
                        Image(systemName: "square.and.arrow.up.circle.fill")
                            .font(.system(size: 60))
                            .foregroundColor(Color.appIconInfo)
                        
                        Text("Export My Data")
                            .font(.system(size: 28, weight: .bold, design: .rounded))
                            .foregroundColor(Color.appTextStrong)
                        
                        Text("Download a complete copy of your ATX Friends data")
                            .font(.system(size: 16, weight: .regular, design: .rounded))
                            .foregroundColor(Color.appSecondaryText)
                            .multilineTextAlignment(.center)
                            .padding(.horizontal, 20)
                    }
                    .padding(.top, 16)
                    
                    // What's Included Section
                    VStack(alignment: .leading, spacing: 16) {
                        Text("What's included in your export:")
                            .font(.system(size: 20, weight: .bold, design: .rounded))
                            .foregroundColor(Color.appTextStrong)
                            .padding(.horizontal, 20)
                        
                        VStack(spacing: 0) {
                            ExportDataRow(
                                icon: "person.fill",
                                title: "Profile Information",
                                description: "Your name, friendship mode, and preferences"
                            )
                            
                            Divider()
                                .padding(.leading, 56)
                            
                            ExportDataRow(
                                icon: "figure.run",
                                title: "Activities",
                                description: "All activities you've selected"
                            )
                            
                            Divider()
                                .padding(.leading, 56)
                            
                            ExportDataRow(
                                icon: "calendar",
                                title: "Availability",
                                description: "Your selected days and time slots"
                            )
                            
                            Divider()
                                .padding(.leading, 56)
                            
                            ExportDataRow(
                                icon: "photo.fill",
                                title: "Photos",
                                description: "URLs to all your profile photos"
                            )
                            
                            Divider()
                                .padding(.leading, 56)
                            
                            ExportDataRow(
                                icon: "person.2.fill",
                                title: "Matches",
                                description: "All your mutual connections"
                            )
                            
                            Divider()
                                .padding(.leading, 56)
                            
                            ExportDataRow(
                                icon: "message.fill",
                                title: "Messages",
                                description: "Complete message history"
                            )
                            
                            Divider()
                                .padding(.leading, 56)
                            
                            ExportDataRow(
                                icon: "calendar.badge.checkmark",
                                title: "Plans",
                                description: "All proposed and confirmed plans"
                            )
                        }
                        .padding()
                        .background(Color.appCardBackground.opacity(0.6))
                        .cornerRadius(16)
                        .padding(.horizontal, 20)
                    }
                    
                    // Info Card
                    VStack(alignment: .leading, spacing: 12) {
                        HStack(spacing: 12) {
                            Image(systemName: "info.circle.fill")
                                .font(.system(size: 24))
                                .foregroundColor(Color.appIconInfo)
                            
                            VStack(alignment: .leading, spacing: 4) {
                                Text("Your Data, Your Way")
                                    .font(.system(size: 18, weight: .bold, design: .rounded))
                                    .foregroundColor(Color.appTextStrong)
                                
                                Text("Export your data to save it to Files, email it, or share it via AirDrop. The export is in plain text format for easy reading.")
                                    .font(.system(size: 15, weight: .regular, design: .rounded))
                                    .foregroundColor(Color.appSecondaryText)
                                    .lineSpacing(4)
                            }
                        }
                    }
                    .padding()
                    .background(Color.appCardBackground.opacity(0.6))
                    .cornerRadius(16)
                    .padding(.horizontal, 20)
                    
                    // Export Button
                    Button {
                        Task {
                            await exportData()
                        }
                    } label: {
                        HStack(spacing: 8) {
                            if isExporting {
                                ProgressView()
                                    .tint(.white)
                            } else {
                                Image(systemName: "square.and.arrow.up.fill")
                            }
                            
                            Text(isExporting ? "Exporting..." : "Export My Data")
                                .font(.system(size: 17, weight: .semibold, design: .rounded))
                        }
                        .foregroundColor(.white)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 16)
                        .background(Color.appIconInfo)
                        .cornerRadius(12)
                    }
                    .disabled(isExporting)
                    .padding(.horizontal, 20)
                    .padding(.bottom, 20)
                }
                .padding(.vertical, 20)
            }
            .scrollIndicators(.hidden)
        }
        .navigationTitle("Export My Data")
        .navigationBarTitleDisplayMode(.inline)
        .sheet(isPresented: $showShareSheet) {
            if let text = exportedText {
                ShareSheet(items: [text])
            }
        }
        .alert("Error", isPresented: $showError) {
            Button("OK", role: .cancel) {}
        } message: {
            Text(errorMessage)
        }
    }
    
    // MARK: - Actions
    
    private func exportData() async {
        isExporting = true
        
        let result = await viewModel.exportUserData()
        
        isExporting = false
        
        if let exportText = result {
            exportedText = exportText
            showShareSheet = true
        } else {
            errorMessage = "Failed to export data. Please try again."
            showError = true
        }
    }
}

// MARK: - Export Data Row

struct ExportDataRow: View {
    let icon: String
    let title: String
    let description: String
    
    var body: some View {
        HStack(spacing: 12) {
            Image(systemName: icon)
                .font(.system(size: 22))
                .foregroundColor(Color.appIconInfo)
                .frame(width: 32)
            
            VStack(alignment: .leading, spacing: 2) {
                Text(title)
                    .font(.system(size: 16, weight: .medium, design: .rounded))
                    .foregroundColor(Color.appTextStrong)
                
                Text(description)
                    .font(.system(size: 14, weight: .regular, design: .rounded))
                    .foregroundColor(Color.appSecondaryText)
            }
            
            Spacer()
        }
        .padding(.vertical, 12)
    }
}

// MARK: - Share Sheet

struct ShareSheet: UIViewControllerRepresentable {
    let items: [Any]
    
    func makeUIViewController(context: Context) -> UIActivityViewController {
        let controller = UIActivityViewController(
            activityItems: items,
            applicationActivities: nil
        )
        return controller
    }
    
    func updateUIViewController(_ uiViewController: UIActivityViewController, context: Context) {
        // No update needed
    }
}

// MARK: - Safari View

struct SafariView: UIViewControllerRepresentable {
    let url: URL
    
    func makeUIViewController(context: Context) -> SFSafariViewController {
        return SFSafariViewController(url: url)
    }
    
    func updateUIViewController(_ uiViewController: SFSafariViewController, context: Context) {
        // No update needed
    }
}

#Preview("Privacy & Safety View") {
    NavigationStack {
        PrivacyAndSafetyView()
    }
}

#Preview("Blocked Users - Empty") {
    NavigationStack {
        let viewModel = PrivacyAndSafetyViewModel()
        BlockedUsersView(viewModel: viewModel)
    }
}
