//
//  JoinRequestSheet.swift
//  Avenue3
//
//  Created by George Anthony Pazdral II on 6/4/26.
//

import SwiftUI

/// Sheet for requesting to join a group
struct JoinRequestSheet: View {
    let group: Group
    let onSuccess: (GroupMember.GroupMemberRole) -> Void
    
    @Environment(\.dismiss) private var dismiss
    @EnvironmentObject private var groupsViewModel: GroupsViewModel
    
    @State private var message: String = ""
    @State private var isSubmitting = false
    @State private var showError = false
    @State private var errorMessage = ""
    
    private let maxCharacters = 200
    
    var body: some View {
        NavigationStack {
            VStack(alignment: .leading, spacing: 24) {
                // Group name (read-only)
                VStack(alignment: .leading, spacing: 8) {
                    Text("Group")
                        .font(.system(size: 13, weight: .semibold, design: .rounded))
                        .foregroundColor(.secondary)
                        .textCase(.uppercase)
                    
                    Text(group.activityName)
                        .font(.system(size: 18, weight: .semibold, design: .rounded))
                        .foregroundColor(.secondary)
                }
                
                // Message text editor
                VStack(alignment: .leading, spacing: 8) {
                    Text("Say something to the Organizer (optional)")
                        .font(.system(size: 13, weight: .semibold, design: .rounded))
                        .foregroundColor(.secondary)
                        .textCase(.uppercase)
                    
                    ZStack(alignment: .topLeading) {
                        if message.isEmpty {
                            Text("I've been bowling for years!")
                                .font(.system(size: 16, weight: .regular, design: .rounded))
                                .foregroundColor(.secondary.opacity(0.5))
                                .padding(.horizontal, 5)
                                .padding(.vertical, 8)
                        }
                        
                        TextEditor(text: $message)
                            .font(.system(size: 16, weight: .regular, design: .rounded))
                            .foregroundColor(Color(red: 0.40, green: 0.40, blue: 0.40))
                            .frame(minHeight: 120)
                            .scrollContentBackground(.hidden)
                            .background(Color.white.opacity(0.6))
                            .cornerRadius(12)
                            .onChange(of: message) { oldValue, newValue in
                                // Limit to max characters
                                if newValue.count > maxCharacters {
                                    message = String(newValue.prefix(maxCharacters))
                                }
                            }
                    }
                    
                    // Character count
                    HStack {
                        Spacer()
                        Text("\(message.count) / \(maxCharacters)")
                            .font(.system(size: 13, weight: .regular, design: .rounded))
                            .foregroundColor(.secondary)
                    }
                }
                
                Spacer()
                
                // Send Request button
                Button {
                    Task {
                        await sendRequest()
                    }
                } label: {
                    if isSubmitting {
                        ProgressView()
                            .tint(.white)
                            .frame(maxWidth: .infinity)
                            .frame(height: 52)
                    } else {
                        Text("Send Request")
                            .font(.system(size: 17, weight: .semibold, design: .rounded))
                            .foregroundColor(.white)
                            .frame(maxWidth: .infinity)
                            .frame(height: 52)
                    }
                }
                .background(Color.appPrimary)
                .cornerRadius(14)
                .disabled(isSubmitting)
            }
            .padding(20)
            .background(
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
            .navigationTitle("Request to Join")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    Button("Cancel") {
                        dismiss()
                    }
                    .font(.system(size: 17, weight: .regular, design: .rounded))
                    .foregroundColor(Color.appPrimary)
                }
            }
            .alert("Error", isPresented: $showError) {
                Button("OK", role: .cancel) { }
            } message: {
                Text(errorMessage)
            }
        }
    }
    
    // MARK: - Helper Methods
    
    private func sendRequest() async {
        isSubmitting = true
        
        do {
            let trimmedMessage = message.trimmingCharacters(in: .whitespacesAndNewlines)
            let messageToSend = trimmedMessage.isEmpty ? nil : trimmedMessage
            
            try await groupsViewModel.requestToJoin(
                groupID: group.id,
                message: messageToSend
            )
            
            // Determine the role that was assigned
            // Check if group is at capacity to know if user was waitlisted
            let members = await groupsViewModel.fetchMembers(groupID: group.id)
            let approvedCount = members.filter {
                $0.role == .member || $0.role == .organizer
            }.count
            
            let assignedRole: GroupMember.GroupMemberRole = approvedCount >= group.maxParticipants ? .waitlisted : .pending
            
            // Call success callback
            onSuccess(assignedRole)
            
            // Dismiss sheet
            dismiss()
        } catch {
            errorMessage = error.localizedDescription
            showError = true
        }
        
        isSubmitting = false
    }
}

#Preview {
    JoinRequestSheet(
        group: Group(
            id: "1",
            activityName: "Hiking at Griffith Park",
            location: "Griffith Park Observatory",
            dateTime: Date().addingTimeInterval(86400 * 3),
            recurrence: .weekly,
            description: "Join us for a weekly hike!",
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
        ),
        onSuccess: { _ in }
    )
    .environmentObject(GroupsViewModel())
}
