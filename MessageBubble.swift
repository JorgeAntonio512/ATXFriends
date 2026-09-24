//
//  MessageBubble.swift
//  Avenue3
//
//  Created by George Anthony Pazdral II on 6/6/26.
//

import SwiftUI

/// A message bubble component for displaying individual messages in a chat thread
struct MessageBubble: View {
    let message: Message
    let isFromCurrentUser: Bool
    
    var body: some View {
        HStack {
            if isFromCurrentUser {
                Spacer(minLength: 60)
            }
            
            VStack(alignment: isFromCurrentUser ? .trailing : .leading, spacing: 4) {
                Text(message.text)
                    .font(.system(size: 16, weight: .regular, design: .rounded))
                    .foregroundColor(isFromCurrentUser ? .white : Color.appPrimaryText)
                    .padding(.horizontal, 14)
                    .padding(.vertical, 10)
                    .background(isFromCurrentUser ? Color.appPrimary : Color.appCardBackground)
                    .cornerRadius(18)
                    .shadow(
                        color: isFromCurrentUser ?
                        Color.appPrimary.opacity(0.2) :
                        .black.opacity(0.05),
                        radius: 4,
                        x: 0,
                        y: 2
                    )
                
                Text(message.sentAt.formatted(date: .omitted, time: .shortened))
                    .font(.system(size: 11, weight: .regular, design: .rounded))
                    .foregroundColor(Color.appSecondaryText)
                    .padding(.horizontal, 4)
            }
            
            if !isFromCurrentUser {
                Spacer(minLength: 60)
            }
        }
        .padding(.horizontal, 20)
    }
}
