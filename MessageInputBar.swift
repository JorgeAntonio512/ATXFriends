//
//  MessageInputBar.swift
//  Avenue3
//
//  Created by George Anthony Pazdral II on 6/6/26.
//

import SwiftUI

/// A message input bar component for composing and sending messages
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
                .background(Color.appCardBackground)
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
                        .fill(canSend ? Color.appPrimary : Color.appBorder)
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
                    Color.appPrimary.opacity(0.3) :
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
            Color.appCardBackground
                .shadow(color: .black.opacity(0.1), radius: 10, x: 0, y: -5)
        )
    }
}
