//
//  MemberRowView.swift
//  Avenue3
//
//  Created by George Anthony Pazdral II on 6/4/26.
//

import SwiftUI

/// Row view displaying an approved group member
struct MemberRowView: View {
    let userID: String
    let displayName: String
    let photoURL: String?
    let isOrganizer: Bool
    let onRemove: (() -> Void)?
    
    var body: some View {
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
            Text(displayName)
                .font(.system(size: 16, weight: .medium, design: .rounded))
                .foregroundColor(Color.appTextBody)
            
            Spacer()
            
            // Remove button (only for organizer view)
            if isOrganizer, let onRemove = onRemove {
                Button {
                    onRemove()
                } label: {
                    Text("Remove")
                        .font(.system(size: 13, weight: .semibold, design: .rounded))
                        .foregroundColor(Color.appDenyRed)
                        .padding(.horizontal, 12)
                        .padding(.vertical, 6)
                        .background(Color.appCardBackground.opacity(0.8))
                        .cornerRadius(8)
                        .overlay(
                            RoundedRectangle(cornerRadius: 8)
                                .stroke(Color.appDenyRed, lineWidth: 1)
                        )
                }
            }
        }
        .padding(.vertical, 8)
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
}

#Preview {
    VStack(spacing: 16) {
        MemberRowView(
            userID: "user1",
            displayName: "Sarah Johnson",
            photoURL: nil,
            isOrganizer: false,
            onRemove: nil
        )
        
        MemberRowView(
            userID: "user2",
            displayName: "Mike Chen",
            photoURL: nil,
            isOrganizer: true,
            onRemove: {
                print("Remove tapped")
            }
        )
    }
    .padding()
    .background(
        LinearGradient(
            colors: [
                Color.appBackground,
                Color.appBackground
            ],
            startPoint: .topLeading,
            endPoint: .bottomTrailing
        )
    )
}
