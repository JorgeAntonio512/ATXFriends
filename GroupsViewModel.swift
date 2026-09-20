//
//  GroupsViewModel.swift
//  Avenue3
//
//  Created by George Anthony Pazdral II on 6/3/26.
//

import Foundation
import SwiftUI
import Combine
import FirebaseFirestore

/// ViewModel for managing groups
@MainActor
class GroupsViewModel: ObservableObject {
    @Published var nearbyGroups: [Group] = []
    @Published var myGroups: [Group] = []
    @Published var isLoading = false
    @Published var errorMessage: String?
    
    private let firestoreService = FirestoreService.shared
    
    // Cache for user preview data to avoid redundant Firestore reads
    private var userPreviewCache: [String: (displayName: String, photoURL: String?)] = [:]
    
    /// Loads nearby public groups and the user's groups
    /// - Parameters:
    ///   - userID: The current user's ID
    ///   - geoHashPrefix: The first 4 characters of the user's geohash
    func loadGroups(userID: String, geoHashPrefix: String) async {
        print("🔄 GroupsViewModel.loadGroups() called for user: \(userID)")
        isLoading = true
        errorMessage = nil
        
        do {
            // Fetch nearby groups and user's groups concurrently
            async let nearbyTask = firestoreService.fetchNearbyGroups(geoHashPrefix: geoHashPrefix)
            async let myGroupsTask = firestoreService.fetchMyGroups(userID: userID)
            
            let (nearby, mine) = try await (nearbyTask, myGroupsTask)
            
            print("📦 Received \(nearby.count) nearby groups from Firestore")
            print("📦 Received \(mine.count) user's groups from Firestore")
            
            // Filter out groups the user is already a member of from nearby list
            let myGroupIDs = Set(mine.map { $0.id })
            print("🔍 User is a member of groups: \(myGroupIDs)")
            
            nearbyGroups = nearby.filter { !myGroupIDs.contains($0.id) }
                .sorted { $0.dateTime < $1.dateTime }
            
            myGroups = mine.sorted { $0.dateTime < $1.dateTime }
            
            print("✅ After filtering: \(nearbyGroups.count) nearby groups, \(myGroups.count) user groups")
            print("📊 nearbyGroups IDs: \(nearbyGroups.map { $0.id })")
            print("📊 myGroups IDs: \(myGroups.map { $0.id })")
        } catch {
            errorMessage = "Failed to load groups: \(error.localizedDescription)"
            print("❌ Error loading groups: \(error)")
        }
        
        isLoading = false
        print("🏁 GroupsViewModel.loadGroups() finished. isLoading = \(isLoading), nearbyGroups.count = \(nearbyGroups.count)")
    }
    
    /// Creates a new group
    /// - Parameter group: The group to create
    /// - Throws: Firestore errors
    func createGroup(_ group: Group) async throws {
        _ = try await firestoreService.createGroup(group)
    }
    
    /// Fetches the current user's membership role in a group
    /// - Parameters:
    ///   - groupID: The group's ID
    ///   - userID: The user's Firebase UID
    /// - Returns: The user's role if they are a member, nil otherwise
    func fetchMemberRole(groupID: String, userID: String) async -> GroupMember.GroupMemberRole? {
        return await firestoreService.fetchGroupMemberRole(groupID: groupID, userID: userID)
    }
    
    /// Fetches all members of a group
    /// - Parameter groupID: The group's ID
    /// - Returns: Array of group members
    func fetchMembers(groupID: String) async -> [GroupMember] {
        do {
            return try await firestoreService.fetchGroupMembers(groupID: groupID)
        } catch {
            print("❌ Error fetching members: \(error)")
            return []
        }
    }
    
    /// Requests to join a group (creates a pending or waitlisted membership)
    /// - Parameters:
    ///   - groupID: The group's ID
    ///   - message: Optional message to the organizer
    /// - Throws: Firestore errors
    func requestToJoin(groupID: String, message: String?) async throws {
        guard let currentUserID = FirebaseAuthService.shared.currentUserID else {
            throw NSError(domain: "GroupsViewModel", code: -1, userInfo: [NSLocalizedDescriptionKey: "No user signed in"])
        }
        
        // First, fetch all members and count approved members
        let members = await fetchMembers(groupID: groupID)
        let approvedCount = members.filter {
            $0.role == .member || $0.role == .organizer
        }.count
        
        // Fetch the group to get maxParticipants
        guard let group = try await firestoreService.fetchGroup(groupID: groupID) else {
            throw NSError(domain: "GroupsViewModel", code: -1, userInfo: [NSLocalizedDescriptionKey: "Group not found"])
        }
        
        // Determine if the user should be pending or waitlisted
        let role: GroupMember.GroupMemberRole = approvedCount >= group.maxParticipants ? .waitlisted : .pending
        
        let memberData: [String: Any] = [
            "userID": currentUserID,
            "role": role.rawValue,
            "joinedAt": Timestamp(date: Date()),
            "confirmedPlan": false,
            "joinMessage": message ?? ""
        ]
        
        try await firestoreService.setGroupMember(groupID: groupID, userID: currentUserID, memberData: memberData)
    }
    
    /// Approves a pending member, either moving them to member or waitlist based on capacity
    /// - Parameters:
    ///   - groupID: The group's ID
    ///   - userID: The user's ID to approve
    ///   - currentApprovedCount: The current count of approved members (including organizer)
    ///   - maxParticipants: The maximum number of participants for the group
    /// - Throws: Firestore errors
    func approveMember(groupID: String, userID: String, currentApprovedCount: Int, maxParticipants: Int) async throws {
        let newRole = currentApprovedCount >= maxParticipants ? "waitlisted" : "member"
        try await firestoreService.updateGroupMemberRole(groupID: groupID, userID: userID, role: newRole)
    }
    
    /// Denies a pending member by removing them from the group
    /// - Parameters:
    ///   - groupID: The group's ID
    ///   - userID: The user's ID to deny
    /// - Throws: Firestore errors
    func denyMember(groupID: String, userID: String) async throws {
        try await firestoreService.removeGroupMember(groupID: groupID, userID: userID)
    }
    
    /// Fetches user preview data (display name and photo URL) with caching
    /// - Parameter userID: The user's Firebase UID
    /// - Returns: Tuple containing display name and optional photo URL
    func fetchUserPreview(userID: String) async -> (displayName: String, photoURL: String?) {
        // Check cache first
        if let cached = userPreviewCache[userID] {
            return cached
        }
        
        // Fetch from Firestore
        let preview = await firestoreService.fetchUserPreview(userID: userID)
        
        // Cache the result
        userPreviewCache[userID] = preview
        
        return preview
    }
    
    /// Promotes the next waitlisted member to full member when a spot opens
    /// - Parameter groupID: The group's ID
    func promoteFromWaitlist(groupID: String) async {
        do {
            // 1. Fetch all members with role == "waitlisted" ordered by joinedAt ascending
            let snapshot = try await firestoreService.fetchWaitlistedMembers(groupID: groupID)
            
            // 2. If none exist, return — nothing to do
            guard let firstWaitlistedDoc = snapshot.documents.first else {
                print("✅ No waitlisted members to promote")
                return
            }
            
            // 3. Take the first one (next in line)
            let userID = firstWaitlistedDoc.documentID
            
            // 4. Update their role to "member"
            try await firestoreService.updateGroupMemberRole(groupID: groupID, userID: userID, role: "member")
            
            // 5. Send a local notification (for now just print — notifications wired in Chunk 6)
            print("🎉 A spot opened up — \(userID) promoted from waitlist")
            
        } catch {
            print("❌ Error promoting from waitlist: \(error)")
        }
    }
    
    /// Leaves a group (for members)
    /// - Parameter groupID: The group's ID
    /// - Throws: Firestore errors
    func leaveGroup(groupID: String) async throws {
        guard let currentUserID = FirebaseAuthService.shared.currentUserID else { return }
        
        // 1. Delete groups/{groupID}/members/{currentUserID}
        try await firestoreService.removeGroupMember(groupID: groupID, userID: currentUserID)
        
        // 2. Promote next from waitlist
        await promoteFromWaitlist(groupID: groupID)
        
        // 3. Reload the user's groups list
        if let userID = FirebaseAuthService.shared.currentUserID {
            await loadGroups(userID: userID, geoHashPrefix: "")
        }
    }
    
    /// Removes a member from a group (for organizers)
    /// - Parameters:
    ///   - groupID: The group's ID
    ///   - userID: The user's ID to remove
    /// - Throws: Firestore errors
    func removeMember(groupID: String, userID: String) async throws {
        // 1. Delete groups/{groupID}/members/{userID}
        try await firestoreService.removeGroupMember(groupID: groupID, userID: userID)
        
        // 2. Promote next from waitlist
        await promoteFromWaitlist(groupID: groupID)
    }
    
    /// Transfers organizer role to another member
    /// - Parameters:
    ///   - groupID: The group's ID
    ///   - toUserID: The user ID to receive organizer role
    /// - Throws: Firestore errors
    func transferOrganizer(groupID: String, toUserID: String) async throws {
        guard let currentUserID = FirebaseAuthService.shared.currentUserID else { return }
        
        try await firestoreService.transferGroupOrganizer(
            groupID: groupID,
            currentOrganizerID: currentUserID,
            newOrganizerID: toUserID
        )
    }
    
    /// Cancels a group (sets status to "canceled")
    /// - Parameter groupID: The group's ID
    /// - Throws: Firestore errors
    func cancelGroup(groupID: String) async throws {
        // 1. Update group status to "canceled"
        try await firestoreService.updateGroupStatus(groupID: groupID, status: "canceled")
        
        // 2. Reload groups list for current user
        if let userID = FirebaseAuthService.shared.currentUserID {
            await loadGroups(userID: userID, geoHashPrefix: "")
        }
        
        // Note: Notifications to members handled in Chunk 6
    }
    
    /// Searches for users by display name prefix
    /// - Parameters:
    ///   - prefix: The search prefix (minimum 2 characters)
    ///   - excludingGroupID: Group ID to exclude existing members from results
    /// - Returns: Array of matching users
    func searchUsers(prefix: String, excludingGroupID: String) async -> [FirebaseUser] {
        guard prefix.count >= 2 else { return [] }
        
        do {
            // Standard Firestore prefix search
            let snapshot = try await firestoreService.searchUsersByDisplayName(prefix: prefix, limit: 10)
            
            // Fetch current members of the group
            let membersSnapshot = try await firestoreService.fetchGroupMembersSnapshot(groupID: excludingGroupID)
            
            let memberIDs = Set(membersSnapshot.documents.map { $0.documentID })
            
            // Parse users and filter out existing members
            var users: [FirebaseUser] = []
            for document in snapshot.documents {
                let userID = document.documentID
                
                // Skip if already a member
                if memberIDs.contains(userID) {
                    continue
                }
                
                if let user = try await firestoreService.fetchUser(userID: userID) {
                    users.append(user)
                }
            }
            
            return users
        } catch {
            print("❌ Error searching users: \(error)")
            return []
        }
    }
    
    /// Invites a user to a group
    /// - Parameters:
    ///   - groupID: The group's ID
    ///   - userID: The user ID to invite
    /// - Throws: Firestore errors
    func inviteUser(groupID: String, userID: String) async throws {
        let memberData: [String: Any] = [
            "userID": userID,
            "role": "pending",
            "joinMessage": "Invited by Organizer",
            "joinedAt": Timestamp(date: Date()),
            "confirmedPlan": false
        ]
        
        try await firestoreService.setGroupMember(groupID: groupID, userID: userID, memberData: memberData)
    }
}
