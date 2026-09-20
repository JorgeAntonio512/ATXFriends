//
//  GroupCardView.swift
//  Avenue3
//
//  Created by George Anthony Pazdral II on 6/3/26.
//

import SwiftUI

/// Card view displaying a group's information
struct GroupCardView: View {
    let group: Group
    let memberCount: Int
    
    private var dateFormatter: DateFormatter {
        let formatter = DateFormatter()
        formatter.dateStyle = .medium
        formatter.timeStyle = .short
        return formatter
    }
    
    var body: some View {
        VStack(spacing: 0) {
            // Cover photo (if present)
            if let coverPhotoURL = group.coverPhotoURL, !coverPhotoURL.isEmpty {
                AsyncImage(url: URL(string: coverPhotoURL)) { phase in
                    switch phase {
                    case .empty:
                        placeholderImage
                    case .success(let image):
                        image
                            .resizable()
                            .aspectRatio(contentMode: .fill)
                            .frame(height: 160)
                            .clipped()
                    case .failure:
                        placeholderImage
                    @unknown default:
                        placeholderImage
                    }
                }
            } else {
                placeholderImage
            }
            
            // Group info
            VStack(alignment: .leading, spacing: 12) {
                // Activity name
                Text(group.activityName)
                    .font(.system(size: 20, weight: .bold, design: .rounded))
                    .foregroundColor(Color(red: 0.35, green: 0.35, blue: 0.35))
                
                // Location
                HStack(spacing: 6) {
                    Image(systemName: "mappin.circle.fill")
                        .font(.system(size: 14))
                        .foregroundColor(Color.appPrimary)
                    
                    Text(group.location)
                        .font(.system(size: 14, weight: .medium, design: .rounded))
                        .foregroundColor(Color(red: 0.50, green: 0.50, blue: 0.50))
                        .lineLimit(1)
                }
                
                // Date and time
                HStack(spacing: 6) {
                    Image(systemName: "calendar.badge.clock")
                        .font(.system(size: 14))
                        .foregroundColor(Color.appPrimary)
                    
                    Text(dateFormatter.string(from: group.dateTime))
                        .font(.system(size: 14, weight: .medium, design: .rounded))
                        .foregroundColor(Color(red: 0.50, green: 0.50, blue: 0.50))
                }
                
                // Badges row
                HStack(spacing: 8) {
                    // Participant count
                    participantBadge
                    
                    // Recurrence badge (if not one-time)
                    if group.recurrence != .none {
                        recurrenceBadge
                    }
                    
                    // Privacy badge (if invite only)
                    if group.privacy == .inviteOnly {
                        privacyBadge
                    }
                    
                    Spacer()
                    
                    // Status badge
                    statusBadge
                }
            }
            .padding(16)
        }
        .background(Color.white.opacity(0.85))
        .cornerRadius(16)
        .shadow(color: .black.opacity(0.08), radius: 10, x: 0, y: 4)
    }
    
    // MARK: - Subviews
    
    private var placeholderImage: some View {
        Rectangle()
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
            .frame(height: 160)
            .overlay {
                Image(systemName: "person.3")
                    .font(.system(size: 50))
                    .foregroundColor(Color.appPrimary.opacity(0.5))
            }
    }
    
    private var participantBadge: some View {
        HStack(spacing: 4) {
            Image(systemName: "person.2.fill")
                .font(.system(size: 11))
            Text("\(memberCount) / \(group.maxParticipants)")
                .font(.system(size: 12, weight: .semibold, design: .rounded))
        }
        .foregroundColor(memberCount >= group.maxParticipants ? Color(red: 0.85, green: 0.45, blue: 0.40) : Color.appPrimary)
        .padding(.horizontal, 8)
        .padding(.vertical, 4)
        .background((memberCount >= group.maxParticipants ? Color(red: 0.85, green: 0.45, blue: 0.40) : Color.appPrimary).opacity(0.1))
        .cornerRadius(8)
    }
    
    private var recurrenceBadge: some View {
        HStack(spacing: 4) {
            Image(systemName: "arrow.clockwise")
                .font(.system(size: 11))
            Text(group.recurrence.label)
                .font(.system(size: 12, weight: .semibold, design: .rounded))
        }
        .foregroundColor(Color.appPrimary)
        .padding(.horizontal, 8)
        .padding(.vertical, 4)
        .background(Color.appPrimary.opacity(0.1))
        .cornerRadius(8)
    }
    
    private var privacyBadge: some View {
        HStack(spacing: 4) {
            Image(systemName: "lock.fill")
                .font(.system(size: 11))
            Text("Invite Only")
                .font(.system(size: 12, weight: .semibold, design: .rounded))
        }
        .foregroundColor(Color(red: 0.60, green: 0.60, blue: 0.60))
        .padding(.horizontal, 8)
        .padding(.vertical, 4)
        .background(Color(red: 0.60, green: 0.60, blue: 0.60).opacity(0.1))
        .cornerRadius(8)
    }
    
    private var statusBadge: some View {
        Text(statusLabel)
            .font(.system(size: 12, weight: .bold, design: .rounded))
            .foregroundColor(statusColor)
            .padding(.horizontal, 10)
            .padding(.vertical, 4)
            .background(statusColor.opacity(0.15))
            .cornerRadius(8)
    }
    
    private var statusLabel: String {
        if memberCount >= group.maxParticipants {
            return "Full"
        }
        return group.status.label
    }
    
    private var statusColor: Color {
        if memberCount >= group.maxParticipants {
            return Color(red: 0.85, green: 0.45, blue: 0.40)
        }
        
        switch group.status {
        case .open:
            return Color.appPrimary
        case .confirming:
            return Color(red: 0.85, green: 0.65, blue: 0.30)
        case .confirmed:
            return Color.appPrimary
        case .canceled:
            return Color(red: 0.60, green: 0.60, blue: 0.60)
        }
    }
}

#Preview {
    VStack(spacing: 20) {
        GroupCardView(
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
            memberCount: 4
        )
        .padding()
        
        GroupCardView(
            group: Group(
                id: "2",
                activityName: "Board Game Night",
                location: "Downtown Game Café",
                dateTime: Date().addingTimeInterval(86400 * 5),
                recurrence: .none,
                description: "One-time board game night!",
                coverPhotoURL: nil,
                minParticipants: 3,
                maxParticipants: 6,
                privacy: .inviteOnly,
                status: .confirming,
                organizerID: "organizer456",
                createdAt: Date(),
                confirmedAt: nil,
                confirmationDeadline: nil,
                geoHash: "9q5ct"
            ),
            memberCount: 6
        )
        .padding()
    }
    .background(
        LinearGradient(
            colors: [
                Color.white,
                Color.white
            ],
            startPoint: .topLeading,
            endPoint: .bottomTrailing
        )
    )
}
