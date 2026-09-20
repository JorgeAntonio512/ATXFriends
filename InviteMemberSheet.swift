//
//  InviteMemberSheet.swift
//  Avenue3
//
//  Created by George Anthony Pazdral II on 6/4/26.
//

import SwiftUI

/// Sheet for inviting members to a group by searching for users
struct InviteMemberSheet: View {
    let groupID: String
    
    @State private var searchText = ""
    @State private var searchResults: [FirebaseUser] = []
    @State private var isSearching = false
    @State private var invitedUserIDs = Set<String>()
    @State private var searchTask: Task<Void, Never>?
    
    @Environment(\.dismiss) private var dismiss
    @EnvironmentObject private var groupsViewModel: GroupsViewModel
    
    var body: some View {
        NavigationStack {
            ZStack {
                // Background gradient
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
                    // Header
                    VStack(alignment: .leading, spacing: 12) {
                        Text("Invite Someone")
                            .font(.system(size: 28, weight: .bold, design: .rounded))
                            .foregroundColor(Color(red: 0.35, green: 0.35, blue: 0.35))
                        
                        // Search field
                        HStack(spacing: 12) {
                            Image(systemName: "magnifyingglass")
                                .font(.system(size: 16))
                                .foregroundColor(Color(red: 0.50, green: 0.50, blue: 0.50))
                            
                            TextField("Search by name", text: $searchText)
                                .font(.system(size: 16, weight: .regular, design: .rounded))
                                .textFieldStyle(.plain)
                                .autocorrectionDisabled()
                                .onChange(of: searchText) { oldValue, newValue in
                                    performSearch()
                                }
                            
                            if !searchText.isEmpty {
                                Button {
                                    searchText = ""
                                    searchResults = []
                                } label: {
                                    Image(systemName: "xmark.circle.fill")
                                        .font(.system(size: 16))
                                        .foregroundColor(Color(red: 0.50, green: 0.50, blue: 0.50))
                                }
                            }
                        }
                        .padding(12)
                        .background(Color.white.opacity(0.8))
                        .cornerRadius(12)
                    }
                    .padding(.horizontal, 20)
                    .padding(.top, 20)
                    .padding(.bottom, 16)
                    
                    // Results area
                    if searchText.isEmpty {
                        emptyStateView
                    } else if isSearching {
                        loadingStateView
                    } else if searchResults.isEmpty {
                        noResultsView
                    } else {
                        resultsListView
                    }
                }
            }
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarLeading) {
                    Button("Done") {
                        dismiss()
                    }
                    .foregroundColor(Color.appPrimary)
                }
            }
        }
    }
    
    // MARK: - Subviews
    
    private var emptyStateView: some View {
        VStack(spacing: 16) {
            Image(systemName: "magnifyingglass")
                .font(.system(size: 50))
                .foregroundColor(Color.appPrimary.opacity(0.5))
            
            Text("Search for someone to invite")
                .font(.system(size: 16, weight: .medium, design: .rounded))
                .foregroundColor(.secondary)
            
            Text("Type at least 2 characters to start searching")
                .font(.system(size: 14, weight: .regular, design: .rounded))
                .foregroundColor(.secondary)
                .multilineTextAlignment(.center)
                .padding(.horizontal, 40)
        }
        .frame(maxHeight: .infinity)
    }
    
    private var loadingStateView: some View {
        VStack(spacing: 16) {
            ProgressView()
                .tint(Color.appPrimary)
            
            Text("Searching...")
                .font(.system(size: 16, weight: .medium, design: .rounded))
                .foregroundColor(.secondary)
        }
        .frame(maxHeight: .infinity)
    }
    
    private var noResultsView: some View {
        VStack(spacing: 16) {
            Image(systemName: "person.slash")
                .font(.system(size: 50))
                .foregroundColor(Color.appPrimary.opacity(0.5))
            
            Text("No users found")
                .font(.system(size: 16, weight: .medium, design: .rounded))
                .foregroundColor(.secondary)
            
            Text("Try a different search")
                .font(.system(size: 14, weight: .regular, design: .rounded))
                .foregroundColor(.secondary)
        }
        .frame(maxHeight: .infinity)
    }
    
    private var resultsListView: some View {
        ScrollView {
            VStack(spacing: 12) {
                ForEach(searchResults) { user in
                    userResultRow(user: user)
                }
            }
            .padding(.horizontal, 20)
            .padding(.bottom, 20)
        }
    }
    
    private func userResultRow(user: FirebaseUser) -> some View {
        HStack(spacing: 12) {
            // Profile photo
            if let photoURL = user.photoURLs.first, let url = URL(string: photoURL) {
                AsyncImage(url: url) { phase in
                    switch phase {
                    case .empty:
                        placeholderPhoto
                    case .success(let image):
                        image
                            .resizable()
                            .aspectRatio(contentMode: .fill)
                            .frame(width: 36, height: 36)
                            .clipShape(Circle())
                    case .failure:
                        placeholderPhoto
                    @unknown default:
                        placeholderPhoto
                    }
                }
            } else {
                placeholderPhoto
            }
            
            // Display name
            Text(user.displayName)
                .font(.system(size: 17, weight: .medium, design: .rounded))
                .foregroundColor(Color(red: 0.40, green: 0.40, blue: 0.40))
            
            Spacer()
            
            // Invite button
            if invitedUserIDs.contains(user.id) {
                HStack(spacing: 6) {
                    Image(systemName: "checkmark.circle.fill")
                        .font(.system(size: 14))
                    Text("Invited!")
                        .font(.system(size: 15, weight: .semibold, design: .rounded))
                }
                .foregroundColor(Color.appPrimary)
                .padding(.horizontal, 12)
                .padding(.vertical, 6)
            } else {
                Button {
                    inviteUser(user)
                } label: {
                    Text("Invite")
                        .font(.system(size: 15, weight: .semibold, design: .rounded))
                        .foregroundColor(Color.appPrimary)
                        .padding(.horizontal, 14)
                        .padding(.vertical, 8)
                        .background(Color.white.opacity(0.8))
                        .cornerRadius(8)
                        .overlay(
                            RoundedRectangle(cornerRadius: 8)
                                .stroke(Color.appPrimary, lineWidth: 1.5)
                        )
                }
            }
        }
        .padding(12)
        .background(Color.white.opacity(0.6))
        .cornerRadius(12)
    }
    
    private var placeholderPhoto: some View {
        ZStack {
            Circle()
                .fill(
                    LinearGradient(
                        colors: [
                            Color.appPrimary.opacity(0.3),
                            Color.appPrimary.opacity(0.3)
                        ],
                        startPoint: .topLeading,
                        endPoint: .bottomTrailing
                    )
                )
                .frame(width: 36, height: 36)
            
            Image(systemName: "person.fill")
                .font(.system(size: 18))
                .foregroundColor(Color.appPrimary.opacity(0.5))
        }
    }
    
    // MARK: - Methods
    
    private func performSearch() {
        // Cancel previous search task
        searchTask?.cancel()
        
        // Create new search task with debounce
        searchTask = Task {
            // Wait 300ms
            try? await Task.sleep(nanoseconds: 300_000_000)
            
            // Check if cancelled
            if Task.isCancelled { return }
            
            // Perform search
            await MainActor.run {
                isSearching = true
            }
            
            let results = await groupsViewModel.searchUsers(
                prefix: searchText,
                excludingGroupID: groupID
            )
            
            // Check if cancelled
            if Task.isCancelled { return }
            
            await MainActor.run {
                searchResults = results
                isSearching = false
            }
        }
    }
    
    private func inviteUser(_ user: FirebaseUser) {
        Task {
            do {
                try await groupsViewModel.inviteUser(groupID: groupID, userID: user.id)
                invitedUserIDs.insert(user.id)
                print("✅ Invited user: \(user.displayName)")
            } catch {
                print("❌ Error inviting user: \(error)")
            }
        }
    }
}

#Preview {
    InviteMemberSheet(groupID: "group123")
        .environmentObject(GroupsViewModel())
}
