//
//  PendingRequestRowView.swift
//  Avenue3
//
//  Created by George Anthony Pazdral II on 6/4/26.
//

import SwiftUI

/// Row view displaying a pending join request with approve/deny actions
struct PendingRequestRowView: View {
    let userID: String
    let displayName: String
    let photoURL: String?
    let joinMessage: String?
    let onApprove: () -> Void
    let onDeny: () -> Void
    
    var body: some View {
        VStack(spacing: 12) {
            // User info
            HStack(spacing: 12) {
                // Profile photo
                if let photoURL = photoURL, let url = URL(string: photoURL) {
                    AsyncImage(url: url) { phase in
                        switch phase {
                        case .empty:
                            placeholderPhoto
                        case .success(let image):
                            image
                                .resizable()
                                .aspectRatio(contentMode: .fill)
                                .frame(width: 40, height: 40)
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
                
                VStack(alignment: .leading, spacing: 4) {
                    // Display name
                    Text(displayName)
                        .font(.system(size: 16, weight: .bold, design: .rounded))
                        .foregroundColor(Color(red: 0.35, green: 0.35, blue: 0.35))
                    
                    // Join message
                    if let message = joinMessage, !message.isEmpty {
                        Text(message)
                            .font(.system(size: 14, weight: .regular, design: .rounded))
                            .foregroundColor(.secondary)
                            .lineLimit(2)
                    } else {
                        Text("No message")
                            .font(.system(size: 14, weight: .regular, design: .rounded))
                            .foregroundColor(.secondary)
                            .italic()
                    }
                }
                
                Spacer()
            }
            
            // Action buttons
            HStack(spacing: 12) {
                // Deny button
                Button {
                    onDeny()
                } label: {
                    Text("Deny")
                        .font(.system(size: 15, weight: .semibold, design: .rounded))
                        .foregroundColor(Color(red: 0.75, green: 0.35, blue: 0.35))
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 12)
                        .background(Color.white.opacity(0.8))
                        .cornerRadius(10)
                        .overlay(
                            RoundedRectangle(cornerRadius: 10)
                                .stroke(Color(red: 0.75, green: 0.35, blue: 0.35), lineWidth: 1.5)
                        )
                }
                
                // Approve button
                Button {
                    onApprove()
                } label: {
                    Text("Approve")
                        .font(.system(size: 15, weight: .semibold, design: .rounded))
                        .foregroundColor(.white)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 12)
                        .background(Color.appPrimary)
                        .cornerRadius(10)
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
                .frame(width: 40, height: 40)
            
            Image(systemName: "person.fill")
                .font(.system(size: 20))
                .foregroundColor(Color.appPrimary.opacity(0.5))
        }
    }
}

#Preview {
    VStack(spacing: 16) {
        PendingRequestRowView(
            userID: "user1",
            displayName: "Emma Rodriguez",
            photoURL: nil,
            joinMessage: "Hey! I'd love to join this hiking group. I'm an experienced hiker and can't wait to explore new trails with everyone!",
            onApprove: { print("Approved") },
            onDeny: { print("Denied") }
        )
        
        PendingRequestRowView(
            userID: "user2",
            displayName: "Alex Kim",
            photoURL: nil,
            joinMessage: nil,
            onApprove: { print("Approved") },
            onDeny: { print("Denied") }
        )
    }
    .padding()
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
