//
//  GroupView.swift
//  Avenue3
//
//  Created by George Anthony Pazdral II on 6/3/26.
//

import SwiftUI

/// Main view for the Groups tab
struct GroupView: View {
    @StateObject private var viewModel = GroupsViewModel()
    @State private var showCreateGroup = false
    @State private var groupMemberCounts: [String: Int] = [:] // groupID -> member count
    
    var body: some View {
        NavigationStack {
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
                
                if viewModel.isLoading {
                    // Loading state
                    VStack(spacing: 20) {
                        ProgressView()
                            .tint(Color.appPrimary)
                            .scaleEffect(1.2)
                        
                        Text("Loading groups...")
                            .font(.system(size: 16, weight: .medium, design: .rounded))
                            .foregroundColor(Color(red: 0.50, green: 0.50, blue: 0.50))
                    }
                } else if viewModel.myGroups.isEmpty && viewModel.nearbyGroups.isEmpty {
                    // Empty state - only show when BOTH arrays are empty
                    emptyStateView
                        .onAppear {
                            print("🎨 UI: Showing empty state. myGroups.count = \(viewModel.myGroups.count), nearbyGroups.count = \(viewModel.nearbyGroups.count)")
                        }
                } else {
                    // Groups list
                    ScrollView {
                        VStack(alignment: .leading, spacing: 24) {
                            // My Groups section
                            if !viewModel.myGroups.isEmpty {
                                VStack(alignment: .leading, spacing: 16) {
                                    Text("My Groups")
                                        .font(.system(size: 22, weight: .bold, design: .rounded))
                                        .foregroundColor(Color.appNavy)
                                        .padding(.horizontal, 20)
                                        .padding(.top, 20)
                                    
                                    LazyVStack(spacing: 16) {
                                        ForEach(viewModel.myGroups) { group in
                                            NavigationLink(destination: GroupDetailView(group: group)
                                                .environmentObject(viewModel)
                                            ) {
                                                GroupCardView(
                                                    group: group,
                                                    memberCount: groupMemberCounts[group.id] ?? 1
                                                )
                                            }
                                            .buttonStyle(.plain)
                                            .onAppear {
                                                Task {
                                                    await loadMemberCount(for: group.id)
                                                }
                                            }
                                        }
                                    }
                                    .padding(.horizontal, 20)
                                }
                            }
                            
                            // Nearby Groups section
                            if !viewModel.nearbyGroups.isEmpty {
                                VStack(alignment: .leading, spacing: 16) {
                                    Text("Nearby")
                                        .font(.system(size: 22, weight: .bold, design: .rounded))
                                        .foregroundColor(Color.appNavy)
                                        .padding(.horizontal, 20)
                                        .padding(.top, viewModel.myGroups.isEmpty ? 20 : 0)
                                    
                                    LazyVStack(spacing: 16) {
                                        ForEach(viewModel.nearbyGroups) { group in
                                            NavigationLink(destination: GroupDetailView(group: group)
                                                .environmentObject(viewModel)
                                            ) {
                                                GroupCardView(
                                                    group: group,
                                                    memberCount: groupMemberCounts[group.id] ?? 1
                                                )
                                            }
                                            .buttonStyle(.plain)
                                            .onAppear {
                                                Task {
                                                    await loadMemberCount(for: group.id)
                                                }
                                            }
                                        }
                                    }
                                    .padding(.horizontal, 20)
                                }
                            }
                            
                            // Bottom padding
                            Color.clear
                                .frame(height: 20)
                        }
                    }
                    .refreshable {
                        await reloadGroups()
                    }
                    .onAppear {
                        print("🎨 UI: Showing groups list. myGroups.count = \(viewModel.myGroups.count), nearbyGroups.count = \(viewModel.nearbyGroups.count)")
                    }
                }
            }
            .navigationTitle("Groups")
            .navigationBarTitleDisplayMode(.large)
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button {
                        showCreateGroup = true
                    } label: {
                        Image(systemName: "plus")
                            .foregroundColor(Color.appPrimary)
                    }
                }
            }
            .sheet(isPresented: $showCreateGroup) {
                CreateGroupView(viewModel: viewModel)
                    .interactiveDismissDisabled(true)
            }
        }
        .task {
            await loadGroups()
        }
    }
    
    // MARK: - Subviews
    
    private var emptyStateView: some View {
        VStack(spacing: 24) {
            ZStack {
                Circle()
                    .fill(Color.appPrimary.opacity(0.2))
                    .frame(width: 120, height: 120)
                
                Image(systemName: "person.3")
                    .font(.system(size: 60))
                    .foregroundColor(Color.appPrimary)
            }
            
            VStack(spacing: 12) {
                Text("No Groups Nearby Yet")
                    .font(.system(size: 28, weight: .bold, design: .rounded))
                    .foregroundColor(Color.appNavy)
                
                Text("Be the first to create a group activity!\n\nGroups are a great way to meet new people who share your interests.")
                    .font(.system(size: 16, weight: .regular, design: .rounded))
                    .foregroundColor(Color(red: 0.50, green: 0.50, blue: 0.50))
                    .multilineTextAlignment(.center)
                    .lineSpacing(4)
            }
            .padding(.horizontal, 40)
            
            Button {
                showCreateGroup = true
            } label: {
                HStack(spacing: 8) {
                    Image(systemName: "plus.circle.fill")
                        .font(.system(size: 18))
                    Text("Create a Group")
                        .font(.system(size: 17, weight: .semibold, design: .rounded))
                }
                .foregroundColor(.white)
                .padding(.horizontal, 24)
                .padding(.vertical, 14)
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
                .cornerRadius(12)
                .shadow(color: Color.appNavy.opacity(0.3), radius: 8, x: 0, y: 4)
            }
        }
        .frame(maxHeight: .infinity)
    }
    
    // MARK: - Helper Methods
    
    private func loadGroups() async {
        guard let userID = FirebaseAuthService.shared.currentUserID else { return }
        
        do {
            // Get user's location for geohash
            let user = try await FirestoreService.shared.fetchUser(userID: userID)
            guard let user = user else { return }
            
            // Generate geohash prefix from user's location
            let geoHash = GeoHashUtility.encode(latitude: user.latitude, longitude: user.longitude, precision: 5)
            let geoHashPrefix = String(geoHash.prefix(4))
            
            await viewModel.loadGroups(userID: userID, geoHashPrefix: geoHashPrefix)
        } catch {
            print("❌ Error loading groups: \(error)")
        }
    }
    
    private func reloadGroups() async {
        await loadGroups()
    }
    
    private func loadMemberCount(for groupID: String) async {
        do {
            let members = try await FirestoreService.shared.fetchGroupMembers(groupID: groupID)
            groupMemberCounts[groupID] = members.count
        } catch {
            print("❌ Error loading member count for group \(groupID): \(error)")
        }
    }
}

#Preview {
    GroupView()
}
