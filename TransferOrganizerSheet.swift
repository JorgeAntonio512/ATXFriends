//
//  TransferOrganizerSheet.swift
//  Avenue3
//
//  Created by George Anthony Pazdral II on 6/4/26.
//

import SwiftUI

/// Sheet for transferring organizer role to another member
struct TransferOrganizerSheet: View {
    let group: Group
    let members: [GroupMember]
    let userPreviews: [String: (displayName: String, photoURL: String?)]
    let onTransfer: (String) -> Void
    
    @State private var showConfirmation = false
    @State private var selectedUserID: String?
    @State private var selectedDisplayName: String = ""
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
                    // Header section
                    VStack(alignment: .leading, spacing: 8) {
                        Text("Transfer Organizer")
                            .font(.system(size: 28, weight: .bold, design: .rounded))
                            .foregroundColor(Color(red: 0.35, green: 0.35, blue: 0.35))
                        
                        Text("Choose a member to take over as Organizer.")
                            .font(.system(size: 16, weight: .regular, design: .rounded))
                            .foregroundColor(.secondary)
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(.horizontal, 20)
                    .padding(.top, 20)
                    .padding(.bottom, 16)
                    
                    // Members list
                    if members.isEmpty {
                        VStack(spacing: 16) {
                            Image(systemName: "person.2.slash")
                                .font(.system(size: 50))
                                .foregroundColor(Color.appPrimary.opacity(0.5))
                            
                            Text("No members to transfer to")
                                .font(.system(size: 16, weight: .medium, design: .rounded))
                                .foregroundColor(.secondary)
                            
                            Text("You need at least one approved member to transfer organizer role.")
                                .font(.system(size: 14, weight: .regular, design: .rounded))
                                .foregroundColor(.secondary)
                                .multilineTextAlignment(.center)
                                .padding(.horizontal, 40)
                        }
                        .frame(maxHeight: .infinity)
                    } else {
                        ScrollView {
                            VStack(spacing: 12) {
                                ForEach(members) { member in
                                    if let preview = userPreviews[member.userID] {
                                        transferMemberRow(member: member, preview: preview)
                                    }
                                }
                            }
                            .padding(.horizontal, 20)
                            .padding(.bottom, 20)
                        }
                    }
                }
            }
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarLeading) {
                    Button("Cancel") {
                        dismiss()
                    }
                    .foregroundColor(Color.appPrimary)
                }
            }
            .alert("Make \(selectedDisplayName) the new Organizer?", isPresented: $showConfirmation) {
                Button("Transfer", role: .destructive) {
                    if let userID = selectedUserID {
                        onTransfer(userID)
                        dismiss()
                    }
                }
                Button("Cancel", role: .cancel) {}
            } message: {
                Text("You'll become a regular member.")
            }
        }
    }
    
    // MARK: - Subviews
    
    private func transferMemberRow(member: GroupMember, preview: (displayName: String, photoURL: String?)) -> some View {
        HStack(spacing: 12) {
            // Profile photo
            if let photoURLString = preview.photoURL,
               let photoURL = URL(string: photoURLString) {
                AsyncImage(url: photoURL) { phase in
                    switch phase {
                    case .empty:
                        placeholderPhoto
                    case .success(let image):
                        image
                            .resizable()
                            .aspectRatio(contentMode: .fill)
                            .frame(width: 44, height: 44)
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
            Text(preview.displayName)
                .font(.system(size: 17, weight: .medium, design: .rounded))
                .foregroundColor(Color(red: 0.40, green: 0.40, blue: 0.40))
            
            Spacer()
            
            // Make Organizer button
            Button {
                selectedUserID = member.userID
                selectedDisplayName = preview.displayName
                showConfirmation = true
            } label: {
                Text("Make Organizer")
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
                .frame(width: 44, height: 44)
            
            Image(systemName: "person.fill")
                .font(.system(size: 22))
                .foregroundColor(Color.appPrimary.opacity(0.5))
        }
    }
}

#Preview {
    TransferOrganizerSheet(
        group: Group(
            id: "1",
            activityName: "Hiking",
            location: "Griffith Park",
            dateTime: Date(),
            recurrence: .none,
            description: "Test group",
            coverPhotoURL: nil,
            minParticipants: 2,
            maxParticipants: 8,
            privacy: .public,
            status: .open,
            organizerID: "org123",
            createdAt: Date(),
            confirmedAt: nil,
            confirmationDeadline: nil,
            geoHash: "9q5ct"
        ),
        members: [
            GroupMember(
                userID: "user1",
                role: .member,
                joinedAt: Date(),
                confirmedPlan: false,
                joinMessage: nil
            ),
            GroupMember(
                userID: "user2",
                role: .member,
                joinedAt: Date(),
                confirmedPlan: false,
                joinMessage: nil
            )
        ],
        userPreviews: [
            "user1": (displayName: "Sarah Johnson", photoURL: nil),
            "user2": (displayName: "Mike Chen", photoURL: nil)
        ],
        onTransfer: { userID in
            print("Transfer to: \(userID)")
        }
    )
    .environmentObject(GroupsViewModel())
}
