//
//  GroupDetailView.swift
//  Avenue3
//
//  Created by George Anthony Pazdral II on 6/4/26.
//

import SwiftUI

/// Detail view for a specific group, showing full information and actions
struct GroupDetailView: View {
    let group: Group
    @State private var memberCount: Int = 1
    @State private var memberRole: GroupMember.GroupMemberRole? = nil
    @State private var showJoinRequestSheet = false
    @State private var showLeaveConfirmation = false
    @State private var showRemoveConfirmation = false
    @State private var showTransferSheet = false
    @State private var showCancelActionSheet = false
    @State private var showFinalCancelConfirmation = false
    @State private var showShareSheet = false
    @State private var showInviteSheet = false
    @State private var memberToRemove: GroupMember? = nil
    @State private var members: [GroupMember] = []
    @State private var isLoadingMembers = false
    @State private var userPreviews: [String: (displayName: String, photoURL: String?)] = [:]
    @State private var organizerPreview: (displayName: String, photoURL: String?)? = nil
    @Environment(\.dismiss) private var dismiss
    @EnvironmentObject private var groupsViewModel: GroupsViewModel
    
    // Computed property to check if current user is organizer
    private var isOrganizer: Bool {
        guard let currentUserID = FirebaseAuthService.shared.currentUserID else {
            return false
        }
        return currentUserID == group.organizerID
    }
    
    private var dateFormatter: DateFormatter {
        let formatter = DateFormatter()
        formatter.dateStyle = .medium
        formatter.timeStyle = .short
        return formatter
    }
    
    private var bottomPadding: CGFloat {
        let tabBarHeight: CGFloat = 50
        let safeAreaBottomInset: CGFloat = {
            if let windowScene = UIApplication.shared.connectedScenes.first as? UIWindowScene,
               let window = windowScene.windows.first {
                return window.safeAreaInsets.bottom
            }
            return 0
        }()
        return tabBarHeight + safeAreaBottomInset + 16
    }
    
    var body: some View {
        ScrollView {
            VStack(spacing: 0) {
                // Cover photo area
                coverPhotoSection
                
                // Content
                VStack(alignment: .leading, spacing: 20) {
                    // Activity name
                    Text(group.activityName)
                        .font(.system(size: 26, weight: .bold, design: .rounded))
                        .foregroundColor(Color(red: 0.35, green: 0.35, blue: 0.35))
                    
                    // Location
                    HStack(spacing: 8) {
                        Image(systemName: "mappin.circle.fill")
                            .font(.system(size: 16))
                            .foregroundColor(Color.appPrimary)
                        
                        Text(group.location)
                            .font(.system(size: 16, weight: .medium, design: .rounded))
                            .foregroundColor(.secondary)
                    }
                    
                    // Date and time
                    HStack(spacing: 8) {
                        Image(systemName: "calendar.badge.clock")
                            .font(.system(size: 16))
                            .foregroundColor(Color.appPrimary)
                        
                        Text(dateFormatter.string(from: group.dateTime))
                            .font(.system(size: 16, weight: .medium, design: .rounded))
                            .foregroundColor(.secondary)
                    }
                    
                    // Recurrence badge
                    if group.recurrence != .none {
                        HStack(spacing: 6) {
                            Image(systemName: "arrow.clockwise")
                                .font(.system(size: 13))
                            Text(group.recurrence.label)
                                .font(.system(size: 14, weight: .semibold, design: .rounded))
                        }
                        .foregroundColor(Color.appPrimary)
                        .padding(.horizontal, 12)
                        .padding(.vertical, 6)
                        .background(Color.appPrimary.opacity(0.15))
                        .cornerRadius(10)
                    }
                    
                    Divider()
                        .padding(.vertical, 8)
                    
                    // About section
                    VStack(alignment: .leading, spacing: 12) {
                        Text("About")
                            .font(.system(size: 13, weight: .semibold, design: .rounded))
                            .foregroundColor(.secondary)
                            .textCase(.uppercase)
                        
                        Text(group.description)
                            .font(.system(size: 16, weight: .regular, design: .rounded))
                            .foregroundColor(Color(red: 0.40, green: 0.40, blue: 0.40))
                            .lineSpacing(4)
                    }
                    
                    Divider()
                        .padding(.vertical, 8)
                    
                    // Organizer section
                    VStack(alignment: .leading, spacing: 12) {
                        Text("Organizer")
                            .font(.system(size: 13, weight: .semibold, design: .rounded))
                            .foregroundColor(.secondary)
                            .textCase(.uppercase)
                        
                        // Organizer row
                        HStack(spacing: 12) {
                            // Profile photo
                            if let preview = organizerPreview,
                               let photoURLString = preview.photoURL,
                               let photoURL = URL(string: photoURLString) {
                                AsyncImage(url: photoURL) { phase in
                                    switch phase {
                                    case .empty:
                                        organizerPlaceholderPhotoLarge
                                    case .success(let image):
                                        image
                                            .resizable()
                                            .aspectRatio(contentMode: .fill)
                                            .frame(width: 50, height: 50)
                                            .clipShape(Circle())
                                    case .failure:
                                        organizerPlaceholderPhotoLarge
                                    @unknown default:
                                        organizerPlaceholderPhotoLarge
                                    }
                                }
                            } else {
                                organizerPlaceholderPhotoLarge
                            }
                            
                            VStack(alignment: .leading, spacing: 4) {
                                Text(organizerPreview?.displayName ?? "Loading...")
                                    .font(.system(size: 16, weight: .semibold, design: .rounded))
                                    .foregroundColor(Color(red: 0.40, green: 0.40, blue: 0.40))
                                
                                Text("Organizer")
                                    .font(.system(size: 14, weight: .regular, design: .rounded))
                                    .foregroundColor(.secondary)
                            }
                            
                            Spacer()
                        }
                        .padding(12)
                        .background(Color.white.opacity(0.6))
                        .cornerRadius(12)
                    }
                    
                    Divider()
                        .padding(.vertical, 8)
                    
                    // Participants section
                    participantsSection
                    
                    // Pending requests section (organizer only)
                    if let currentUserID = FirebaseAuthService.shared.currentUserID,
                       currentUserID == group.organizerID {
                        let pendingMembers = members.filter { $0.role == .pending }
                        if !pendingMembers.isEmpty {
                            Divider()
                                .padding(.vertical, 8)
                            
                            pendingRequestsSection(pendingMembers: pendingMembers)
                        }
                        
                        // Waitlist section (organizer only)
                        let waitlistedMembers = members.filter { $0.role == .waitlisted }
                        if !waitlistedMembers.isEmpty {
                            Divider()
                                .padding(.vertical, 8)
                            
                            waitlistSection(waitlistedMembers: waitlistedMembers)
                        }
                    }
                    
                    // Bottom spacing for safe area inset
                    Color.clear
                        .frame(height: 20)
                }
                .padding(.horizontal, 20)
                .padding(.top, 20)
            }
        }
        .background(
            // Warm gradient background matching GroupView and MatchesView
            LinearGradient(
                colors: [
                    Color.white,
                    Color.white
                ],
                startPoint: .topLeading,
                endPoint: .bottomTrailing
            )
            .ignoresSafeArea()
        )
        .navigationTitle(group.activityName)
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            if isOrganizer {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Menu {
                        Button {
                            showShareSheet = true
                        } label: {
                            Label("Share Invite Link", systemImage: "link")
                        }
                        
                        Button {
                            showInviteSheet = true
                        } label: {
                            Label("Invite Someone", systemImage: "person.badge.plus")
                        }
                        
                        Button {
                            showTransferSheet = true
                        } label: {
                            Label("Transfer Organizer", systemImage: "person.badge.arrow.right")
                        }
                        
                        Button(role: .destructive) {
                            showCancelActionSheet = true
                        } label: {
                            Label("Cancel Group", systemImage: "xmark.circle")
                        }
                    } label: {
                        Image(systemName: "ellipsis.circle")
                            .foregroundColor(Color.appPrimary)
                    }
                }
            }
        }
        .safeAreaInset(edge: .bottom) {
            // Bottom action area
            VStack {
                bottomActionView
                    .frame(maxWidth: .infinity)
                    .padding(.horizontal, 20)
                    .padding(.top, 12)
                    .padding(.bottom, bottomPadding)
            }
            .padding(.top, 12)
            .padding(.bottom, 12)
            .background(
                // Extend background to bottom of screen
                LinearGradient(
                    colors: [
                        Color.white,
                        Color.white
                    ],
                    startPoint: .topLeading,
                    endPoint: .bottomTrailing
                )
                .ignoresSafeArea()
            )
        }
        .sheet(isPresented: $showJoinRequestSheet) {
            JoinRequestSheet(group: group) { assignedRole in
                // Update the member role after successful request
                memberRole = assignedRole
            }
            .environmentObject(groupsViewModel)
        }
        .confirmationDialog(
            "Leave \(group.activityName)?",
            isPresented: $showLeaveConfirmation,
            titleVisibility: .visible
        ) {
            Button("Leave Group", role: .destructive) {
                Task {
                    try? await groupsViewModel.leaveGroup(groupID: group.id)
                    dismiss()
                }
            }
            Button("Cancel", role: .cancel) {}
        } message: {
            Text("Your spot may go to someone on the waitlist.")
        }
        .confirmationDialog(
            "Remove \(memberToRemove.map { userPreviews[$0.userID]?.displayName ?? $0.userID } ?? "this person")?",
            isPresented: $showRemoveConfirmation,
            titleVisibility: .visible
        ) {
            Button("Remove from Group", role: .destructive) {
                Task {
                    guard let member = memberToRemove else { return }
                    try? await groupsViewModel.removeMember(groupID: group.id, userID: member.userID)
                    members = await groupsViewModel.fetchMembers(groupID: group.id)
                }
            }
            Button("Cancel", role: .cancel) {}
        } message: {
            Text("Their spot will go to the next person on the waitlist.")
        }
        .sheet(isPresented: $showTransferSheet) {
            TransferOrganizerSheet(
                group: group,
                members: members.filter { $0.role == .member },
                userPreviews: userPreviews,
                onTransfer: { toUserID in
                    Task {
                        try? await groupsViewModel.transferOrganizer(groupID: group.id, toUserID: toUserID)
                        // Refresh members list
                        members = await groupsViewModel.fetchMembers(groupID: group.id)
                        // Refresh member role
                        await loadMemberRole()
                    }
                }
            )
            .environmentObject(groupsViewModel)
        }
        .confirmationDialog(
            "Cancel \(group.activityName)?",
            isPresented: $showCancelActionSheet,
            titleVisibility: .visible
        ) {
            Button("Transfer Organizer First") {
                showTransferSheet = true
            }
            Button("Cancel Group", role: .destructive) {
                showFinalCancelConfirmation = true
            }
            Button("Never Mind", role: .cancel) {}
        } message: {
            Text("Would you like to pass Organizer duties to someone else before canceling?")
        }
        .alert("Cancel Group?", isPresented: $showFinalCancelConfirmation) {
            Button("Yes, Cancel Group", role: .destructive) {
                Task {
                    try? await groupsViewModel.cancelGroup(groupID: group.id)
                    dismiss()
                }
            }
            Button("Keep Group", role: .cancel) {}
        } message: {
            Text("All members and waitlisted users will be notified.")
        }
        .sheet(isPresented: $showShareSheet) {
            if let inviteURL = URL(string: "https://avenue3.app/groups/\(group.id)") {
                ShareLink(
                    item: inviteURL,
                    subject: Text("Join my group on ATX Friends"),
                    message: Text("I'd love for you to join \(group.activityName)!")
                )
                .presentationDetents([.medium])
            }
        }
        .sheet(isPresented: $showInviteSheet) {
            InviteMemberSheet(groupID: group.id)
                .environmentObject(groupsViewModel)
        }
        .task {
            await loadMemberCount()
            await loadMemberRole()
            await loadMembers()
            await loadOrganizerPreview()
        }
    }
    
    // MARK: - Subviews
    
    private var participantsSection: some View {
        VStack(alignment: .leading, spacing: 12) {
            let organizer = members.first { $0.role == .organizer }
            let approvedMembers = members.filter { $0.role == .member }
            
            Text("Participants  \(approvedMembers.count + 1) / \(group.maxParticipants)")
                .font(.system(size: 13, weight: .semibold, design: .rounded))
                .foregroundColor(.secondary)
                .textCase(.uppercase)
            
            if isLoadingMembers {
                HStack {
                    Spacer()
                    ProgressView()
                    Spacer()
                }
                .padding()
            } else {
                // Organizer row
                if let organizer = organizer {
                    organizerRow(organizer: organizer)
                }
                
                // Approved members list
                if !approvedMembers.isEmpty {
                    VStack(spacing: 8) {
                        ForEach(approvedMembers) { member in
                            if let preview = userPreviews[member.userID] {
                                MemberRowView(
                                    userID: member.userID,
                                    displayName: preview.displayName,
                                    photoURL: preview.photoURL,
                                    isOrganizer: FirebaseAuthService.shared.currentUserID == group.organizerID,
                                    onRemove: {
                                        Task {
                                            await removeMember(userID: member.userID)
                                        }
                                    }
                                )
                            }
                        }
                    }
                } else if organizer == nil {
                    Text("No members yet")
                        .font(.system(size: 16, weight: .regular, design: .rounded))
                        .foregroundColor(.secondary)
                        .italic()
                }
            }
        }
    }
    
    private func organizerRow(organizer: GroupMember) -> some View {
        HStack(spacing: 12) {
            // Profile photo
            if let preview = userPreviews[organizer.userID],
               let photoURLString = preview.photoURL,
               let photoURL = URL(string: photoURLString) {
                AsyncImage(url: photoURL) { phase in
                    switch phase {
                    case .empty:
                        organizerPlaceholderPhoto
                    case .success(let image):
                        image
                            .resizable()
                            .aspectRatio(contentMode: .fill)
                            .frame(width: 40, height: 40)
                            .clipShape(Circle())
                    case .failure:
                        organizerPlaceholderPhoto
                    @unknown default:
                        organizerPlaceholderPhoto
                    }
                }
            } else {
                organizerPlaceholderPhoto
            }
            
            VStack(alignment: .leading, spacing: 4) {
                Text(userPreviews[organizer.userID]?.displayName ?? "Unknown")
                    .font(.system(size: 16, weight: .semibold, design: .rounded))
                    .foregroundColor(Color(red: 0.40, green: 0.40, blue: 0.40))
                
                Text("Organizer")
                    .font(.system(size: 14, weight: .regular, design: .rounded))
                    .foregroundColor(Color.appPrimary)
            }
            
            Spacer()
        }
        .padding(12)
        .background(Color.white.opacity(0.6))
        .cornerRadius(12)
    }
    
    private var organizerPlaceholderPhoto: some View {
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
                .frame(width: 40, height: 40)
            
            Image(systemName: "person.fill")
                .font(.system(size: 20))
                .foregroundColor(Color.appPrimary.opacity(0.5))
        }
    }
    
    private var organizerPlaceholderPhotoLarge: some View {
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
                .frame(width: 50, height: 50)
            
            Image(systemName: "person.fill")
                .font(.system(size: 25))
                .foregroundColor(Color.appPrimary.opacity(0.5))
        }
    }
    
    private func pendingRequestsSection(pendingMembers: [GroupMember]) -> some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("Requests (\(pendingMembers.count))")
                .font(.system(size: 13, weight: .semibold, design: .rounded))
                .foregroundColor(.secondary)
                .textCase(.uppercase)
            
            VStack(spacing: 12) {
                ForEach(pendingMembers) { member in
                    if let preview = userPreviews[member.userID] {
                        PendingRequestRowView(
                            userID: member.userID,
                            displayName: preview.displayName,
                            photoURL: preview.photoURL,
                            joinMessage: member.joinMessage,
                            onApprove: {
                                Task {
                                    await approveMember(userID: member.userID)
                                }
                            },
                            onDeny: {
                                Task {
                                    await denyMember(userID: member.userID)
                                }
                            }
                        )
                    }
                }
            }
        }
    }
    
    private func waitlistSection(waitlistedMembers: [GroupMember]) -> some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("Waitlist (\(waitlistedMembers.count))")
                .font(.system(size: 13, weight: .semibold, design: .rounded))
                .foregroundColor(.secondary)
                .textCase(.uppercase)
            
            VStack(spacing: 8) {
                ForEach(Array(waitlistedMembers.enumerated()), id: \.element.userID) { index, member in
                    if let preview = userPreviews[member.userID] {
                        HStack(spacing: 12) {
                            // Profile photo
                            if let photoURLString = preview.photoURL,
                               let photoURL = URL(string: photoURLString) {
                                AsyncImage(url: photoURL) { phase in
                                    switch phase {
                                    case .empty:
                                        waitlistPlaceholderPhoto
                                    case .success(let image):
                                        image
                                            .resizable()
                                            .aspectRatio(contentMode: .fill)
                                            .frame(width: 36, height: 36)
                                            .clipShape(Circle())
                                    case .failure:
                                        waitlistPlaceholderPhoto
                                    @unknown default:
                                        waitlistPlaceholderPhoto
                                    }
                                }
                            } else {
                                waitlistPlaceholderPhoto
                            }
                            
                            Text(preview.displayName)
                                .font(.system(size: 16, weight: .medium, design: .rounded))
                                .foregroundColor(Color(red: 0.40, green: 0.40, blue: 0.40))
                            
                            Spacer()
                            
                            Text("#\(index + 1)")
                                .font(.system(size: 15, weight: .semibold, design: .rounded))
                                .foregroundColor(.secondary)
                        }
                        .padding(.vertical, 8)
                    }
                }
            }
        }
    }
    
    private var waitlistPlaceholderPhoto: some View {
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
    
    private var bottomActionView: some View {
        SwiftUI.Group {
            // Determine current user's relationship to group
            if let currentUserID = FirebaseAuthService.shared.currentUserID {
                if group.organizerID == currentUserID {
                    // User is the organizer - show nothing
                    EmptyView()
                } else if let role = memberRole {
                    // User has a membership role
                    switch role {
                    case .organizer:
                        // Shouldn't happen (covered above), but handle it
                        EmptyView()
                        
                    case .member:
                        Button {
                            showLeaveConfirmation = true
                        } label: {
                            Text("Leave Group")
                                .font(.system(size: 17, weight: .semibold, design: .rounded))
                                .foregroundColor(Color(red: 0.75, green: 0.35, blue: 0.35))
                                .frame(maxWidth: .infinity)
                                .frame(height: 52)
                                .background(Color.white.opacity(0.8))
                                .cornerRadius(14)
                                .overlay(
                                    RoundedRectangle(cornerRadius: 14)
                                        .stroke(Color(red: 0.75, green: 0.35, blue: 0.35), lineWidth: 1.5)
                                )
                        }
                        
                    case .pending:
                        Text("Request Pending...")
                            .font(.system(size: 15, weight: .semibold, design: .rounded))
                            .foregroundColor(.secondary)
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 16)
                            .background(Color.white.opacity(0.8))
                            .cornerRadius(12)
                        
                    case .waitlisted:
                        Text("You're on the Waitlist")
                            .font(.system(size: 15, weight: .semibold, design: .rounded))
                            .foregroundColor(.secondary)
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 16)
                            .background(Color.white.opacity(0.8))
                            .cornerRadius(12)
                    }
                } else {
                    // User is not a member - show join options
                    if group.privacy == .inviteOnly {
                        Text("This group is invite-only")
                            .font(.system(size: 15, weight: .semibold, design: .rounded))
                            .foregroundColor(.secondary)
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 16)
                            .background(Color.white.opacity(0.8))
                            .cornerRadius(12)
                    } else {
                        Button {
                            showJoinRequestSheet = true
                        } label: {
                            Text("Request to Join")
                                .font(.system(size: 17, weight: .semibold, design: .rounded))
                                .foregroundColor(.white)
                                .frame(maxWidth: .infinity)
                                .frame(height: 52)
                                .background(Color.appPrimary)
                                .cornerRadius(14)
                        }
                    }
                }
            } else {
                // No user signed in
                Text("Sign in to join")
                    .font(.system(size: 15, weight: .semibold, design: .rounded))
                    .foregroundColor(.secondary)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 16)
                    .background(Color.white.opacity(0.8))
                    .cornerRadius(12)
            }
        }
    }
    
    private var coverPhotoSection: some View {
        SwiftUI.Group {
            if let coverPhotoURL = group.coverPhotoURL,
               !coverPhotoURL.isEmpty,
               coverPhotoURL != "placeholder_photo_url" {
                // AsyncImage for actual photo
                AsyncImage(url: URL(string: coverPhotoURL)) { phase in
                    switch phase {
                    case .empty:
                        placeholderCoverPhoto
                    case .success(let image):
                        image
                            .resizable()
                            .aspectRatio(contentMode: .fill)
                            .frame(height: 200)
                            .clipped()
                            .clipShape(
                                UnevenRoundedRectangle(
                                    bottomLeadingRadius: 20,
                                    bottomTrailingRadius: 20
                                )
                            )
                    case .failure:
                        placeholderCoverPhoto
                    @unknown default:
                        placeholderCoverPhoto
                    }
                }
            } else {
                // Placeholder with sage green gradient
                placeholderCoverPhoto
            }
        }
    }
    
    private var placeholderCoverPhoto: some View {
        ZStack {
            Rectangle()
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
                .frame(height: 200)
            
            Image(systemName: "person.3")
                .font(.system(size: 60))
                .foregroundColor(.white.opacity(0.7))
        }
        .clipShape(
            UnevenRoundedRectangle(
                bottomLeadingRadius: 20,
                bottomTrailingRadius: 20
            )
        )
    }
    
    // MARK: - Helper Methods
    
    private func loadMemberCount() async {
        do {
            let members = try await FirestoreService.shared.fetchGroupMembers(groupID: group.id)
            memberCount = members.count
        } catch {
            print("❌ Error loading member count: \(error)")
        }
    }
    
    private func loadMemberRole() async {
        guard let currentUserID = FirebaseAuthService.shared.currentUserID else {
            memberRole = nil
            return
        }
        
        memberRole = await groupsViewModel.fetchMemberRole(groupID: group.id, userID: currentUserID)
    }
    
    private func loadMembers() async {
        isLoadingMembers = true
        members = await groupsViewModel.fetchMembers(groupID: group.id)
        
        // Fetch user previews for all members
        for member in members {
            if userPreviews[member.userID] == nil {
                let preview = await groupsViewModel.fetchUserPreview(userID: member.userID)
                userPreviews[member.userID] = preview
            }
        }
        
        isLoadingMembers = false
    }
    
    private func approveMember(userID: String) async {
        do {
            let approvedMembers = members.filter { $0.role == .member || $0.role == .organizer }
            try await groupsViewModel.approveMember(
                groupID: group.id,
                userID: userID,
                currentApprovedCount: approvedMembers.count,
                maxParticipants: group.maxParticipants
            )
            // Refresh members list
            await loadMembers()
        } catch {
            print("❌ Error approving member: \(error)")
        }
    }
    
    private func denyMember(userID: String) async {
        do {
            try await groupsViewModel.denyMember(groupID: group.id, userID: userID)
            // Refresh members list
            await loadMembers()
        } catch {
            print("❌ Error denying member: \(error)")
        }
    }
    
    private func removeMember(userID: String) async {
        // Find the member to remove
        if let member = members.first(where: { $0.userID == userID }) {
            memberToRemove = member
            showRemoveConfirmation = true
        }
    }
    
    private func loadOrganizerPreview() async {
        organizerPreview = await groupsViewModel.fetchUserPreview(userID: group.organizerID)
    }
}

#Preview {
    NavigationStack {
        GroupDetailView(
            group: Group(
                id: "1",
                activityName: "Hiking at Griffith Park",
                location: "Griffith Park Observatory",
                dateTime: Date().addingTimeInterval(86400 * 3),
                recurrence: .weekly,
                description: "Join us for a weekly hike up to the Griffith Observatory! We'll meet at the trailhead and take the scenic route. Bring water and comfortable shoes. All fitness levels welcome!",
                coverPhotoURL: nil,
                minParticipants: 3,
                maxParticipants: 8,
                privacy: .public,
                status: .open,
                organizerID: "organizer123",
                createdAt: Date(),
                confirmedAt: nil,
                confirmationDeadline: nil,
                geoHash: "9q5ct"
            )
        )
        .environmentObject(GroupsViewModel())
    }
}
