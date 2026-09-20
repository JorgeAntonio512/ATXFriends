# Compiler Errors - MessageBubble and MessageInputBar

## Current Errors
```
error: Cannot find 'MessageBubble' in scope
error: Cannot find 'MessageInputBar' in scope
```

## Why This Happens
These errors appear in `EventMessageThreadView.swift` because Xcode's static analysis hasn't yet recognized that `MessageBubble` and `MessageInputBar` are defined in `MessageThreadView.swift`.

## Solution 1: Build the Project (Recommended)
These errors should resolve when you build the project (⌘B). Swift types are internal by default and accessible within the same module, so as long as both files are in the same target, they will compile successfully.

## Solution 2: Extract to Shared File (If Errors Persist)
If the errors persist after building, we can extract these shared components into a separate file:

### Create MessageComponents.swift
```swift
//
//  MessageComponents.swift
//  Avenue3
//
//  Created by George Anthony Pazdral II on 6/6/26.
//

import SwiftUI

// MARK: - Message Bubble

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
                    .foregroundColor(isFromCurrentUser ? .white : Color(red: 0.35, green: 0.35, blue: 0.35))
                    .padding(.horizontal, 14)
                    .padding(.vertical, 10)
                    .background(
                        isFromCurrentUser ?
                        LinearGradient(
                            colors: [
                                Color(red: 0.55, green: 0.70, blue: 0.55),
                                Color(red: 0.45, green: 0.60, blue: 0.50)
                            ],
                            startPoint: .topLeading,
                            endPoint: .bottomTrailing
                        ) :
                        LinearGradient(
                            colors: [Color.white.opacity(0.9), Color.white.opacity(0.9)],
                            startPoint: .topLeading,
                            endPoint: .bottomTrailing
                        )
                    )
                    .cornerRadius(18)
                    .shadow(
                        color: isFromCurrentUser ?
                        Color(red: 0.45, green: 0.60, blue: 0.50).opacity(0.2) :
                        .black.opacity(0.05),
                        radius: 4,
                        x: 0,
                        y: 2
                    )
                
                Text(message.sentAt.formatted(date: .omitted, time: .shortened))
                    .font(.system(size: 11, weight: .regular, design: .rounded))
                    .foregroundColor(Color(red: 0.60, green: 0.60, blue: 0.60))
                    .padding(.horizontal, 4)
            }
            
            if !isFromCurrentUser {
                Spacer(minLength: 60)
            }
        }
        .padding(.horizontal, 20)
    }
}

// MARK: - Message Input Bar

struct MessageInputBar: View {
    @Binding var text: String
    var isFocused: FocusState<Bool>.Binding
    let isSending: Bool
    let onSend: () -> Void
    
    var canSend: Bool {
        !text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty && !isSending
    }
    
    var body: some View {
        HStack(spacing: 12) {
            // Text input
            TextField("Message...", text: $text, axis: .vertical)
                .font(.system(size: 16, weight: .regular, design: .rounded))
                .textFieldStyle(.plain)
                .focused(isFocused)
                .lineLimit(1...5)
                .padding(.horizontal, 16)
                .padding(.vertical, 10)
                .background(Color.white.opacity(0.9))
                .cornerRadius(20)
                .onSubmit {
                    if canSend {
                        onSend()
                    }
                }
            
            // Send button
            Button(action: onSend) {
                ZStack {
                    Circle()
                        .fill(
                            canSend ?
                            LinearGradient(
                                colors: [
                                    Color(red: 0.55, green: 0.70, blue: 0.55),
                                    Color(red: 0.45, green: 0.60, blue: 0.50)
                                ],
                                startPoint: .topLeading,
                                endPoint: .bottomTrailing
                            ) :
                            LinearGradient(
                                colors: [Color.gray.opacity(0.3), Color.gray.opacity(0.3)],
                                startPoint: .topLeading,
                                endPoint: .bottomTrailing
                            )
                        )
                        .frame(width: 40, height: 40)
                    
                    if isSending {
                        ProgressView()
                            .tint(.white)
                            .scaleEffect(0.8)
                    } else {
                        Image(systemName: "arrow.up")
                            .font(.system(size: 18, weight: .semibold))
                            .foregroundColor(.white)
                    }
                }
                .shadow(
                    color: canSend ?
                    Color(red: 0.45, green: 0.60, blue: 0.50).opacity(0.3) :
                    Color.clear,
                    radius: 8,
                    x: 0,
                    y: 4
                )
            }
            .disabled(!canSend)
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 12)
        .background(
            LinearGradient(
                colors: [
                    Color(red: 0.96, green: 0.93, blue: 0.87).opacity(0.95),
                    Color(red: 0.91, green: 0.89, blue: 0.82).opacity(0.95)
                ],
                startPoint: .top,
                endPoint: .bottom
            )
            .shadow(color: .black.opacity(0.1), radius: 10, x: 0, y: -5)
        )
    }
}
```

Then, remove these components from `MessageThreadView.swift` and they'll be accessible to both `MessageThreadView` and `EventMessageThreadView`.

## Recommendation
Try building the project first. If the errors persist after a clean build (⇧⌘K then ⌘B), then extract the components as described above.
