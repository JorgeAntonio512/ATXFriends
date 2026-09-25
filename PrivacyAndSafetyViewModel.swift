//
//  PrivacyAndSafetyViewModel.swift
//  Avenue3
//
//  Created by George Anthony Pazdral II on 5/24/26.
//

import Foundation
import FirebaseFirestore
import FirebaseAuth
import AuthenticationServices

@Observable
class PrivacyAndSafetyViewModel {
    var currentUser: FirebaseUser?
    var blockedUsers: [FirebaseUser] = []
    var searchableContacts: [FirebaseUser] = []
    var pendingMatches: [FirebaseUser] = []
    var isLoading = false
    var errorMessage: String?
    
    private let db = Firestore.firestore()
    
    // MARK: - Load Current User
    
    func loadCurrentUser() async {
        print("👤 DEBUG: loadCurrentUser() called")
        
        guard let userId = Auth.auth().currentUser?.uid else {
            print("❌ DEBUG: No authenticated user ID found")
            return
        }
        
        print("👤 DEBUG: Fetching user document for ID: \(userId)")
        
        do {
            // Use FirestoreService which properly handles manual decoding
            if let user = try await FirestoreService.shared.fetchUser(userID: userId) {
                print("✅ DEBUG: Successfully fetched FirebaseUser via FirestoreService")
                print("✅ DEBUG: User displayName: \(user.displayName)")
                print("✅ DEBUG: User activities count: \(user.activities.count)")
                print("✅ DEBUG: User photoURLs count: \(user.photoURLs.count)")
                
                currentUser = user
                await loadBlockedUsers()
                
                print("✅ DEBUG: loadCurrentUser() completed successfully")
            } else {
                print("❌ DEBUG: FirestoreService.fetchUser returned nil - user document may not exist")
            }
        } catch {
            print("❌ DEBUG: Error loading current user")
            print("❌ DEBUG: Error type: \(type(of: error))")
            print("❌ DEBUG: Error: \(error)")
            print("❌ DEBUG: Localized description: \(error.localizedDescription)")
            
            if let firestoreError = error as NSError? {
                print("❌ DEBUG: Firestore error code: \(firestoreError.code)")
                print("❌ DEBUG: Firestore error domain: \(firestoreError.domain)")
                print("❌ DEBUG: Firestore error userInfo: \(firestoreError.userInfo)")
            }
            
            errorMessage = error.localizedDescription
        }
    }
    
    // MARK: - Block User
    
    func loadSearchableContacts() async {
        guard let userId = Auth.auth().currentUser?.uid else {
            print("❌ DEBUG: No current user ID found")
            return
        }
        
        print("🔍 DEBUG: Starting loadSearchableContacts for userId: \(userId)")
        isLoading = true
        
        do {
            // Get blocked user IDs
            let blockedIds = Set(currentUser?.blockedUsers ?? [])
            print("🔍 DEBUG: Current user has \(blockedIds.count) blocked users: \(blockedIds)")
            
            // Query all matches for the current user
            print("🔍 DEBUG: Running OR query for user1ID or user2ID == \(userId)")
            let allMatches = try await db.collection("matches")
                .whereFilter(Filter.orFilter([
                    Filter.whereField("user1ID", isEqualTo: userId),
                    Filter.whereField("user2ID", isEqualTo: userId)
                ]))
                .getDocuments()
            
            print("🔍 DEBUG: OR query returned \(allMatches.documents.count) total match documents")
            
            // Separate into two lists: mutual matches and non-mutual pending matches
            var mutualMatchUserIds = Set<String>()
            var pendingMatchUserIds = Set<String>()
            
            for (index, doc) in allMatches.documents.enumerated() {
                let data = doc.data()
                
                let isMutualMatch = data["isMutualMatch"] as? Bool ?? false
                let isBlocked = data["isBlocked"] as? Bool ?? false
                
                guard let user1ID = data["user1ID"] as? String,
                      let user2ID = data["user2ID"] as? String else {
                    print("❌ DEBUG: Document \(index + 1) missing user1ID or user2ID fields")
                    continue
                }
                
                let otherUserId = user1ID == userId ? user2ID : user1ID
                
                // Skip if user is blocked
                if blockedIds.contains(otherUserId) {
                    print("🔍 DEBUG: Document \(index + 1) - Other user \(otherUserId) is blocked, skipping")
                    continue
                }
                
                // Categorize based on isMutualMatch
                if isMutualMatch {
                    print("🔍 DEBUG: Document \(index + 1) - Mutual match with user: \(otherUserId)")
                    mutualMatchUserIds.insert(otherUserId)
                } else if !isBlocked {
                    print("🔍 DEBUG: Document \(index + 1) - Pending match with user: \(otherUserId)")
                    pendingMatchUserIds.insert(otherUserId)
                }
            }
            
            print("🔍 DEBUG: Query Summary:")
            print("   - Total documents: \(allMatches.documents.count)")
            print("   - Mutual matches (isMutualMatch == true): \(mutualMatchUserIds.count)")
            print("   - Pending matches (isMutualMatch == false, isBlocked != true): \(pendingMatchUserIds.count)")
            print("   - Blocked users excluded: \(blockedIds.count)")
            
            // Fetch user details for mutual matches (searchableContacts)
            var mutualUsers: [FirebaseUser] = []
            for userIdToFetch in mutualMatchUserIds {
                print("🔍 DEBUG: Fetching mutual match user: \(userIdToFetch)")
                do {
                    if let user = try await FirestoreService.shared.fetchUser(userID: userIdToFetch) {
                        print("🔍 DEBUG: Successfully fetched FirebaseUser for: \(user.displayName)")
                        mutualUsers.append(user)
                    } else {
                        print("❌ DEBUG: User document \(userIdToFetch) does not exist or could not be parsed")
                    }
                } catch {
                    print("❌ DEBUG: Failed to fetch user \(userIdToFetch): \(error)")
                }
            }
            
            // Fetch user details for pending matches
            var pendingUsers: [FirebaseUser] = []
            for userIdToFetch in pendingMatchUserIds {
                print("🔍 DEBUG: Fetching pending match user: \(userIdToFetch)")
                do {
                    if let user = try await FirestoreService.shared.fetchUser(userID: userIdToFetch) {
                        print("🔍 DEBUG: Successfully fetched FirebaseUser for: \(user.displayName)")
                        pendingUsers.append(user)
                    } else {
                        print("❌ DEBUG: User document \(userIdToFetch) does not exist or could not be parsed")
                    }
                } catch {
                    print("❌ DEBUG: Failed to fetch user \(userIdToFetch): \(error)")
                }
            }
            
            // Sort both lists alphabetically
            searchableContacts = mutualUsers.sorted { $0.displayName < $1.displayName }
            pendingMatches = pendingUsers.sorted { $0.displayName < $1.displayName }
            
            print("🔍 DEBUG: Final Results:")
            print("   - searchableContacts (Current Connections): \(searchableContacts.count)")
            print("     Users: \(searchableContacts.map { $0.displayName })")
            print("   - pendingMatches (Matches): \(pendingMatches.count)")
            print("     Users: \(pendingMatches.map { $0.displayName })")
            
        } catch {
            print("❌ DEBUG: Error loading searchable contacts: \(error)")
            print("❌ DEBUG: Error details: \(error.localizedDescription)")
            errorMessage = error.localizedDescription
        }
        
        isLoading = false
        print("🔍 DEBUG: loadSearchableContacts completed")
    }
    
    func blockUser(_ user: FirebaseUser) async -> Bool {
        guard let currentUserId = Auth.auth().currentUser?.uid else { return false }
        
        do {
            // 1) Add to blocked users array
            try await db.collection("users").document(currentUserId).updateData([
                "blockedUsers": FieldValue.arrayUnion([user.id])
            ])
            
            // 2) Find match documents between these two users
            let matches = try await db.collection("matches")
                .whereFilter(Filter.orFilter([
                    Filter.andFilter([
                        Filter.whereField("user1ID", isEqualTo: currentUserId),
                        Filter.whereField("user2ID", isEqualTo: user.id)
                    ]),
                    Filter.andFilter([
                        Filter.whereField("user1ID", isEqualTo: user.id),
                        Filter.whereField("user2ID", isEqualTo: currentUserId)
                    ])
                ]))
                .getDocuments()
            
            // Update match documents to mark as blocked
            for doc in matches.documents {
                try await doc.reference.updateData([
                    "isBlocked": true,
                    "isMutualMatch": false
                ])
            }
            
            // Update local state
            currentUser?.blockedUsers.append(user.id)
            searchableContacts.removeAll { $0.id == user.id }
            pendingMatches.removeAll { $0.id == user.id }
            blockedUsers.append(user)
            
            // Reload contacts list
            await loadSearchableContacts()
            
            // Post notification to refresh other views
            NotificationCenter.default.post(name: NSNotification.Name("userBlocked"), object: nil)
            
            return true
        } catch {
            print("❌ Error blocking user: \(error)")
            errorMessage = error.localizedDescription
            return false
        }
    }
    
    // MARK: - Blocked Users
    
    func loadBlockedUsers() async {
        guard let currentUserId = Auth.auth().currentUser?.uid else {
            print("❌ No current user ID found")
            blockedUsers = []
            return
        }
        
        isLoading = true
        
        do {
            // Fetch the current user's document fresh from Firestore to get the latest blockedUsers array
            print("🔍 DEBUG: Fetching current user document from Firestore...")
            let doc = try await db.collection("users").document(currentUserId).getDocument()
            
            guard doc.exists, let data = doc.data() else {
                print("❌ Current user document not found")
                blockedUsers = []
                isLoading = false
                return
            }
            
            // Get the fresh blockedUsers array
            let blockedUserIds = data["blockedUsers"] as? [String] ?? []
            print("🔍 DEBUG: Raw blockedUsers array from Firestore: \(blockedUserIds)")
            
            // If no blocked users, clear the list and return
            guard !blockedUserIds.isEmpty else {
                print("🔍 DEBUG: No blocked users found")
                blockedUsers = []
                isLoading = false
                return
            }
            
            // Fetch each blocked user's details using FirestoreService
            var users: [FirebaseUser] = []
            for userId in blockedUserIds {
                print("🔍 DEBUG: Fetching blocked user details for: \(userId)")
                do {
                    if let user = try await FirestoreService.shared.fetchUser(userID: userId) {
                        print("🔍 DEBUG: Successfully fetched user: \(user.displayName)")
                        users.append(user)
                    } else {
                        print("❌ DEBUG: Blocked user \(userId) not found in Firestore")
                    }
                } catch {
                    print("❌ DEBUG: Error fetching blocked user \(userId): \(error)")
                    // Continue loading other users even if one fails
                }
            }
            
            // Sort alphabetically by display name
            blockedUsers = users.sorted { $0.displayName < $1.displayName }
            print("🔍 DEBUG: Successfully loaded \(blockedUsers.count) blocked users")
            
        } catch {
            print("❌ DEBUG: Error loading blocked users: \(error)")
            errorMessage = error.localizedDescription
            blockedUsers = []
        }
        
        isLoading = false
    }
    
    func unblockUser(_ user: FirebaseUser) async -> Bool {
        guard let currentUserId = Auth.auth().currentUser?.uid else { return false }
        
        do {
            // 1) Remove from blocked users array
            try await db.collection("users").document(currentUserId).updateData([
                "blockedUsers": FieldValue.arrayRemove([user.id])
            ])
            
            // 2) Find match documents between these two users and update them
            let matches = try await db.collection("matches")
                .whereFilter(Filter.orFilter([
                    Filter.andFilter([
                        Filter.whereField("user1ID", isEqualTo: currentUserId),
                        Filter.whereField("user2ID", isEqualTo: user.id)
                    ]),
                    Filter.andFilter([
                        Filter.whereField("user1ID", isEqualTo: user.id),
                        Filter.whereField("user2ID", isEqualTo: currentUserId)
                    ])
                ]))
                .getDocuments()
            
            // Update match documents to unblock them
            for doc in matches.documents {
                try await doc.reference.updateData([
                    "isBlocked": false
                ])
            }
            
            // Update local state
            currentUser?.blockedUsers.removeAll { $0 == user.id }
            blockedUsers.removeAll { $0.id == user.id }
            
            // Reload blocked users list to ensure it's fresh
            await loadBlockedUsers()
            
            return true
        } catch {
            print("❌ Error unblocking user: \(error)")
            errorMessage = error.localizedDescription
            return false
        }
    }
    
    // MARK: - Report User
    
    func submitReport(reportedUser: FirebaseUser, reason: ReportReason, comments: String) async -> Bool {
        guard let currentUserId = Auth.auth().currentUser?.uid else { return false }
        
        do {
            let report: [String: Any] = [
                "reportedUserId": reportedUser.id,
                "reportingUserId": currentUserId,
                "reason": reason.rawValue,
                "comments": comments,
                "timestamp": Timestamp(date: Date())
            ]
            
            try await db.collection("reports").addDocument(data: report)
            return true
        } catch {
            print("❌ Error submitting report: \(error)")
            errorMessage = error.localizedDescription
            return false
        }
    }
    
    // MARK: - Delete Account

    /// Permanently deletes the signed-in account and everything the user created (server-side,
    /// via the deleteMyAccount Cloud Function), then signs out and clears this device's local
    /// state for the user. Sign in with Apple accounts are first re-authenticated so the app's
    /// Apple sign-in can be revoked, as Apple requires. Returns false and sets errorMessage on
    /// failure; nothing is signed out unless the deletion succeeded.
    @MainActor
    func deleteAccount() async -> Bool {
        errorMessage = nil
        guard let userID = Auth.auth().currentUser?.uid else {
            errorMessage = "You're not signed in."
            return false
        }

        let deletionService = AccountDeletionService.shared
        if deletionService.isSignedInWithApple {
            do {
                try await deletionService.revokeAppleSignIn()
            } catch let error as ASAuthorizationError where error.code == .canceled {
                errorMessage = "Deletion cancelled. Confirm with Apple to delete your account."
                return false
            } catch {
                // Don't block deletion on revocation (e.g. if Apple revocation isn't configured
                // in Firebase yet); the account and its data are still deleted.
                print("⚠️ Apple sign-in revocation failed: \(error.localizedDescription)")
            }
        }

        let planIDs: [String]
        do {
            planIDs = try await deletionService.deleteMyAccount()
        } catch {
            print("❌ deleteMyAccount failed: \(error)")
            errorMessage = "We couldn't delete your account. Check your connection and try again."
            return false
        }

        // Local cleanup for this user. The server already removed their FCM tokens with the
        // users doc; this stops this device from registering the old token again.
        NotificationManager.shared.handleAccountDeleted()
        AddedToCalendarStore.clear(planIDs: planIDs)
        SimpaticoViewModel.clearSavedPosition(userID: userID)
        GoogleSignInHelper().signOut()
        try? Auth.auth().signOut()
        NotificationCenter.default.post(name: .authStateDidChange, object: nil)
        return true
    }
    
    // MARK: - Export Data
    
    func exportUserData() async -> String? {
        print("📦 DEBUG: Starting data export")
        guard let userId = Auth.auth().currentUser?.uid else {
            print("❌ DEBUG: No authenticated user found")
            return nil
        }
        
        print("📦 DEBUG: Exporting data for user: \(userId)")
        
        // Only load current user if not already loaded to avoid redundant calls
        if currentUser == nil {
            print("📦 DEBUG: Current user not loaded, loading now...")
            await loadCurrentUser()
        } else {
            print("📦 DEBUG: Current user already loaded, using cached data")
        }
        
        guard let user = currentUser else {
            print("❌ DEBUG: Could not load current user data")
            return nil
        }
        
        print("✅ DEBUG: Current user available: \(user.displayName)")
        
        var exportText = "ATX FRIENDS DATA EXPORT\n"
        exportText += "=======================\n"
        exportText += "Export Date: \(Date().formatted())\n\n"
        
        // Profile Information
        exportText += "PROFILE INFORMATION\n"
        exportText += "-------------------\n"
        exportText += "Name: \(user.displayName)\n"
        exportText += "Account Created: \(user.createdAt.formatted())\n\n"
        
        // Preferences
        exportText += "PREFERENCES\n"
        exportText += "-----------\n"
        exportText += "Search Radius: \(user.radiusMiles) miles\n\n"
        
        // Activities
        exportText += "ACTIVITIES\n"
        exportText += "----------\n"
        if user.activities.isEmpty {
            exportText += "No activities added\n\n"
        } else {
            for activity in user.activities {
                exportText += "- \(activity.name)\n"
            }
            exportText += "\n"
        }
        
        // Availability
        exportText += "AVAILABILITY\n"
        exportText += "------------\n"
        if user.daySlotCombos.isEmpty {
            exportText += "No availability set\n\n"
        } else {
            for combo in user.daySlotCombos {
                exportText += "- \(combo.displayName)\n"
            }
            exportText += "\n"
        }
        
        // Photos
        exportText += "PHOTOS\n"
        exportText += "------\n"
        if user.photoURLs.isEmpty {
            exportText += "No photos uploaded\n\n"
        } else {
            for (index, photoURL) in user.photoURLs.enumerated() {
                exportText += "Photo \(index + 1): \(photoURL)\n"
            }
            exportText += "\n"
        }
        
        // Matches
        print("📦 DEBUG: Fetching matches...")
        exportText += "MATCHES\n"
        exportText += "-------\n"
        
        do {
            let matches = try await db.collection("matches")
                .whereFilter(Filter.orFilter([
                    Filter.whereField("user1ID", isEqualTo: userId),
                    Filter.whereField("user2ID", isEqualTo: userId)
                ]))
                .whereField("isMutualMatch", isEqualTo: true)
                .getDocuments()
            
            print("📦 DEBUG: Found \(matches.documents.count) mutual matches")
            
            if matches.documents.isEmpty {
                exportText += "No mutual matches yet\n\n"
            } else {
                for doc in matches.documents {
                    let data = doc.data()
                    let user1ID = data["user1ID"] as? String ?? ""
                    let user2ID = data["user2ID"] as? String ?? ""
                    let createdAt = (data["createdAt"] as? Timestamp)?.dateValue()
                    
                    let otherUserId = user1ID == userId ? user2ID : user1ID
                    
                    if let otherUser = try? await FirestoreService.shared.fetchUser(userID: otherUserId) {
                        let dateString = createdAt?.formatted() ?? "Unknown date"
                        exportText += "Match with \(otherUser.displayName) on \(dateString)\n"
                    }
                }
                exportText += "\n"
            }
        } catch {
            print("❌ DEBUG: Error loading matches for export: \(error)")
            exportText += "Error loading matches\n\n"
        }
        
        // Messages
        print("📦 DEBUG: Fetching messages...")
        exportText += "MESSAGES\n"
        exportText += "--------\n"
        
        do {
            let messages = try await db.collection("messages")
                .whereFilter(Filter.orFilter([
                    Filter.whereField("senderID", isEqualTo: userId),
                    Filter.whereField("receiverID", isEqualTo: userId)
                ]))
                .order(by: "sentAt", descending: false)
                .getDocuments()
            
            print("📦 DEBUG: Found \(messages.documents.count) messages")
            
            if messages.documents.isEmpty {
                exportText += "No messages yet\n\n"
            } else {
                // Group messages by matchID
                var messagesByMatch: [String: [(date: Date, senderID: String, receiverID: String, text: String)]] = [:]
                
                for doc in messages.documents {
                    let data = doc.data()
                    guard let matchID = data["matchID"] as? String,
                          let senderID = data["senderID"] as? String,
                          let receiverID = data["receiverID"] as? String,
                          let text = data["text"] as? String,
                          let sentAt = (data["sentAt"] as? Timestamp)?.dateValue() else {
                        continue
                    }
                    
                    if messagesByMatch[matchID] == nil {
                        messagesByMatch[matchID] = []
                    }
                    messagesByMatch[matchID]?.append((date: sentAt, senderID: senderID, receiverID: receiverID, text: text))
                }
                
                // Export messages grouped by match
                for (matchID, msgs) in messagesByMatch.sorted(by: { $0.key < $1.key }) {
                    // Get the other user's ID from the first message
                    guard let firstMsg = msgs.first else { continue }
                    let otherUserID = firstMsg.senderID == userId ? firstMsg.receiverID : firstMsg.senderID
                    
                    // Fetch other user's name
                    var otherUserName = "Unknown User"
                    if let otherUser = try? await FirestoreService.shared.fetchUser(userID: otherUserID) {
                        otherUserName = otherUser.displayName
                    }
                    
                    exportText += "\nConversation with \(otherUserName) (Match ID: \(matchID)):\n"
                    
                    for msg in msgs {
                        let senderName = msg.senderID == userId ? "You" : otherUserName
                        exportText += "[\(msg.date.formatted())] \(senderName): \(msg.text)\n"
                    }
                }
                exportText += "\n"
            }
        } catch {
            print("❌ DEBUG: Error loading messages for export: \(error)")
            print("❌ DEBUG: Error details: \(error.localizedDescription)")
            exportText += "Error loading messages\n\n"
        }
        
        // Plans
        print("📦 DEBUG: Fetching plans...")
        exportText += "PLANS\n"
        exportText += "-----\n"
        
        do {
            let plans = try await db.collection("plans")
                .whereFilter(Filter.orFilter([
                    Filter.whereField("proposerID", isEqualTo: userId),
                    Filter.whereField("receiverID", isEqualTo: userId)
                ]))
                .getDocuments()
            
            print("📦 DEBUG: Found \(plans.documents.count) plans")
            
            if plans.documents.isEmpty {
                exportText += "No plans yet\n\n"
            } else {
                for doc in plans.documents {
                    let data = doc.data()
                    
                    let activityName = data["activityName"] as? String ?? "Unknown Activity"
                    let location = data["location"] as? String
                    let status = data["status"] as? String ?? "unknown"
                    let proposedDates = (data["proposedDates"] as? [Timestamp])?.map { $0.dateValue().formatted() } ?? []
                    let confirmedDate = (data["confirmedDate"] as? Timestamp)?.dateValue()
                    
                    exportText += "Activity: \(activityName)\n"
                    exportText += "Proposed Dates: \(proposedDates.joined(separator: ", "))\n"
                    exportText += "Location: \(location ?? "No location")\n"
                    exportText += "Status: \(status)\n"
                    exportText += "Confirmed Date: \(confirmedDate?.formatted() ?? "Not confirmed")\n\n"
                }
            }
        } catch {
            print("❌ DEBUG: Error loading plans for export: \(error)")
            exportText += "Error loading plans\n\n"
        }
        
        print("✅ DEBUG: Data export complete")
        return exportText
    }
}

// MARK: - Report Reason

enum ReportReason: String, CaseIterable {
    case inappropriateBehavior = "Inappropriate behavior"
    case harassment = "Harassment"
    case fakeProfile = "Fake profile"
    case spam = "Spam"
    case other = "Other"
}
