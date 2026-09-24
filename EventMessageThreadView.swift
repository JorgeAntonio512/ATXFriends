//
//  EventMessageThreadView.swift
//  Avenue3
//
//  Created by George Anthony Pazdral II on 6/6/26.
//

import SwiftUI

/// Full-screen chat interface for an event-based message thread
struct EventMessageThreadView: View {
    @Environment(\.dismiss) private var dismiss
    let thread: MessageThread
    @Bindable var viewModel: MessagingViewModel
    
    @State private var messageText: String = ""
    @FocusState private var isMessageFieldFocused: Bool
    
    private var currentUserID: String {
        FirebaseAuthService.shared.currentUserID ?? ""
    }
    
    private var eventID: String {
        thread.event?.id ?? ""
    }
    
    private var eventName: String {
        thread.event?.name ?? "Event"
    }
    
    init(thread: MessageThread, viewModel: MessagingViewModel) {
        self.thread = thread
        self.viewModel = viewModel
        print("🟢 DEBUG: EventMessageThreadView init called for user: \(thread.otherUser.displayName)")
        print("🟢 DEBUG: Event ID: \(thread.event?.id ?? "nil")")
    }
    
    var body: some View {
        let _ = print("🟢 DEBUG: EventMessageThreadView body executing")
        NavigationStack {
            ZStack {
                // Warm gradient background
                LinearGradient(
                    colors: [
                        Color.appBackground,
                        Color.appBackground
                    ],
                    startPoint: .topLeading,
                    endPoint: .bottomTrailing
                )
                .ignoresSafeArea()

                VStack(spacing: 0) {
                    // Event context header
                    HStack(spacing: 8) {
                        Image(systemName: "calendar")
                            .font(.system(size: 14))
                            .foregroundColor(Color.appPrimary)
                        
                        Text(eventName)
                            .font(.system(size: 14, weight: .medium, design: .rounded))
                            .foregroundColor(Color.appPrimary)
                    }
                    .padding(.horizontal, 16)
                    .padding(.vertical, 8)
                    .background(Color.appPrimary.opacity(0.15))
                    .cornerRadius(12)
                    .padding(.top, 12)
                    .padding(.bottom, 8)
                    
                    // Messages scroll view
                    ScrollViewReader { proxy in
                        ScrollView {
                            VStack(spacing: 16) {
                                // Date header
                                Text("Today")
                                    .font(.system(size: 13, weight: .semibold, design: .rounded))
                                    .foregroundColor(Color.appSecondaryText)
                                    .padding(.horizontal, 16)
                                    .padding(.vertical, 6)
                                    .background(Color.appCardBackground.opacity(0.6))
                                    .cornerRadius(12)
                                    .padding(.top, 20)
                                
                                // Messages
                                if viewModel.messages.isEmpty {
                                    // Empty conversation state
                                    VStack(spacing: 16) {
                                        ZStack {
                                            Circle()
                                                .fill(Color.appPrimary.opacity(0.2))
                                                .frame(width: 80, height: 80)
                                            
                                            Image(systemName: "message.fill")
                                                .font(.system(size: 36))
                                                .foregroundColor(Color.appPrimary)
                                        }
                                        .padding(.top, 40)
                                        
                                        VStack(spacing: 8) {
                                            Text("Start the conversation!")
                                                .font(.system(size: 20, weight: .bold, design: .rounded))
                                                .foregroundColor(Color.appPrimaryText)
                                            
                                            Text("You're both going to \(eventName).\nSay hello!")
                                                .font(.system(size: 15, weight: .regular, design: .rounded))
                                                .foregroundColor(Color.appSecondaryText)
                                                .multilineTextAlignment(.center)
                                                .lineSpacing(4)
                                        }
                                    }
                                } else {
                                    // Message bubbles
                                    ForEach(viewModel.messages, id: \.id) { message in
                                        MessageBubble(
                                            message: message,
                                            isFromCurrentUser: message.senderID == currentUserID
                                        )
                                        .id(message.id)
                                    }
                                }
                                
                                Spacer()
                                    .frame(height: 20)
                            }
                        }
                        .scrollDismissesKeyboard(.interactively)
                        .safeAreaInset(edge: .bottom) {
                            // Add padding for custom tab bar (50pt) + safe area
                            GeometryReader { geometry in
                                Color.clear
                                    .frame(height: 50 + geometry.safeAreaInsets.bottom)
                            }
                            .frame(height: 50)
                        }
                        .onChange(of: viewModel.messages.count) { oldValue, newValue in
                            // Scroll to bottom when new message arrives
                            if let lastMessage = viewModel.messages.last {
                                withAnimation {
                                    proxy.scrollTo(lastMessage.id, anchor: .bottom)
                                }
                            }
                        }
                    }
                    
                    // Input bar
                    VStack(spacing: 0) {
                        MessageInputBar(
                            text: $viewModel.draftMessage,
                            isFocused: $isMessageFieldFocused,
                            isSending: viewModel.isSendingMessage,
                            onSend: {
                                Task {
                                    await viewModel.sendEventMessage(
                                        matchID: thread.id,
                                        eventID: eventID,
                                        receiverID: thread.otherUser.id
                                    )
                                }
                            }
                        )
                        .padding(.horizontal, 16)
                        .padding(.bottom, 50) // Add bottom padding for custom tab bar height
                    }
                }
            }
            .navigationTitle(thread.otherUser.displayName)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    // Profile button (future enhancement)
                    Button {
                        // Show user profile
                    } label: {
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
                                .frame(width: 32, height: 32)
                            
                            Image(systemName: "person.circle.fill")
                                .font(.system(size: 24))
                                .foregroundColor(Color.appPrimary.opacity(0.7))
                        }
                    }
                }
            }
        }
        .task {
            print("🟢 DEBUG: .task modifier executing for event: \(eventID)")
            await viewModel.loadMessagesForEvent(eventID: eventID, otherUserID: thread.otherUser.id)
            print("🟢 DEBUG: loadMessagesForEvent completed")
        }
        .onAppear {
            print("🟢 DEBUG: EventMessageThreadView onAppear called")
        }
        .onDisappear {
            print("🟢 DEBUG: EventMessageThreadView onDisappear - stopping listener")
            viewModel.stopListening()
        }
    }
}

#Preview {
    let user = User(
        id: "user2",
        displayName: "Jordan",
        photoURLs: ["url1", "url2", "url3"],
        activities: [
            ActivityModel(name: "Hiking", isUserAdded: false),
            ActivityModel(name: "Coffee", isUserAdded: false),
            ActivityModel(name: "Reading", isUserAdded: false)
        ],
        daySlotCombos: [
            DaySlotComboModel(dayOfWeek: .saturday, timeSlot: .wakeUp),
            DaySlotComboModel(dayOfWeek: .sunday, timeSlot: .afternoon),
            DaySlotComboModel(dayOfWeek: .friday, timeSlot: .evening)
        ],
        latitude: 30.2700,
        longitude: -97.7400,
        isProfileComplete: true
    )
    
    let event = Event(
        id: "ACL2025",
        name: "ACL Music Festival",
        heroImageURL: "https://example.com/acl.jpg",
        weekends: []
    )
    
    let thread = MessageThread(
        id: "ACL2025_user2",
        match: nil,
        event: event,
        otherUser: user,
        lastMessage: nil,
        unreadCount: 0
    )
    
    EventMessageThreadView(thread: thread, viewModel: MessagingViewModel())
}
