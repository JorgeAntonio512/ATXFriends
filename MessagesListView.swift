//
//  MessagesListView.swift
//  Avenue3
//
//  Created by George Anthony Pazdral II on 5/6/26.
//
 
import SwiftUI
 
/// Messages tab showing all active message threads
struct MessagesListView: View {
    @State private var viewModel = MessagingViewModel()
    @State private var selectedThread: MessageThread?
    @StateObject private var unreadState = UnreadState.shared
 
    var body: some View {
        NavigationStack {
            ZStack {
                Color.appBackground
                    .ignoresSafeArea()

                if viewModel.isLoading {
                    VStack(spacing: 20) {
                        ProgressView()
                            .tint(Color.appPrimary)
                            .scaleEffect(1.2)
                        Text("Loading messages...")
                            .font(.system(size: 16, weight: .medium, design: .rounded))
                            .foregroundColor(Color.appSecondaryText)
                    }
                } else if viewModel.messageThreads.isEmpty {
                    EmptyMessagesView()
                } else {
                    ScrollView {
                        VStack(spacing: 20) {
                            // Section 1: Individuals & Couples
                            VStack(alignment: .leading, spacing: 12) {
                                Text("Individuals & Couples")
                                    .font(.system(size: 15, weight: .semibold, design: .rounded))
                                    .foregroundColor(Color.appSecondaryText)
                                    .textCase(.uppercase)
                                    .padding(.leading, 4)
                                
                                ForEach(viewModel.messageThreads) { thread in
                                    MessageThreadRow(
                                        thread: thread,
                                        isUnread: unreadState.unreadMatchIDs.contains(thread.id),
                                        onTap: {
                                            selectedThread = thread
                                            // Mark read immediately on tap
                                            if let userID = FirebaseAuthService.shared.currentUserID {
                                                UnreadState.shared.markConversationRead(
                                                    matchID: thread.id,
                                                    userID: userID
                                                )
                                            }
                                        }
                                    )
                                }
                            }
                            
                            // Section 2: Events
                            VStack(alignment: .leading, spacing: 12) {
                                Text("Events")
                                    .font(.system(size: 15, weight: .semibold, design: .rounded))
                                    .foregroundColor(Color.appSecondaryText)
                                    .textCase(.uppercase)
                                    .padding(.leading, 4)
                                
                                Text("Coming soon")
                                    .font(.system(size: 15, weight: .regular, design: .rounded))
                                    .foregroundColor(Color.appSecondaryText)
                                    .padding()
                                    .frame(maxWidth: .infinity)
                                    .background(Color.appCardBackground)
                                    .cornerRadius(12)
                            }
                            
                            // Section 3: Groups
                            VStack(alignment: .leading, spacing: 12) {
                                Text("Groups")
                                    .font(.system(size: 15, weight: .semibold, design: .rounded))
                                    .foregroundColor(Color.appSecondaryText)
                                    .textCase(.uppercase)
                                    .padding(.leading, 4)
                                
                                Text("Group conversations will appear here")
                                    .font(.system(size: 15, weight: .regular, design: .rounded))
                                    .foregroundColor(Color.appSecondaryText)
                                    .padding()
                                    .frame(maxWidth: .infinity)
                                    .background(Color.appCardBackground)
                                    .cornerRadius(12)
                            }
                        }
                        .padding(.horizontal, 20)
                        .padding(.top, 20)
                        .padding(.bottom, 40)
                    }
                    .refreshable {
                        await viewModel.loadMessageThreads()
                    }
                }
            }
            .navigationTitle("Messages")
            .navigationBarTitleDisplayMode(.large)
            .fullScreenCover(item: $selectedThread) { thread in
                MessageThreadView(thread: thread, viewModel: viewModel)
            }
        }
        .task {
            await viewModel.loadMessageThreads()
        }
    }
}
 
// MARK: - Message Thread Row
 
struct MessageThreadRow: View {
    let thread: MessageThread
    let isUnread: Bool      // driven by UnreadState, not thread.unreadCount
    let onTap: () -> Void
 
    var body: some View {
        Button(action: onTap) {
            HStack(spacing: 12) {
                // Photo placeholder
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
                        .frame(width: 60, height: 60)
 
                    Image(systemName: "person.circle.fill")
                        .font(.system(size: 40))
                        .foregroundColor(Color.appPrimary.opacity(0.5))
 
                    // Unread dot
                    if isUnread {
                        VStack {
                            HStack {
                                Spacer()
                                Circle()
                                    .fill(Color.appDanger)
                                    .frame(width: 14, height: 14)
                                    .offset(x: 4, y: -4)
                            }
                            Spacer()
                        }
                        .frame(width: 60, height: 60)
                    }
                }
 
                // Message info
                VStack(alignment: .leading, spacing: 4) {
                    HStack(spacing: 6) {
                        Text(thread.otherUser.displayName)
                            .font(.system(size: 17, weight: isUnread ? .bold : .semibold, design: .rounded))
                            .foregroundColor(Color.appPrimaryText)
                            .lineLimit(1)

                        HStack(spacing: 4) {
                            Image(systemName: "calendar.badge.plus")
                                .font(.system(size: 11))
                            Text(thread.showUpMeter)
                                .font(.system(size: 12, weight: .semibold, design: .rounded))
                        }
                        .foregroundColor(Color.appPrimary)

                        if let score = thread.simpaticoScore {
                            HStack(spacing: 3) {
                                Image(systemName: "face.smiling.fill")
                                    .font(.system(size: 10, weight: .semibold))
                                Text("\(score)%")
                                    .font(.system(size: 11, weight: .semibold, design: .rounded))
                            }
                            .foregroundColor(Color.appPrimary)
                            .padding(.horizontal, 6)
                            .padding(.vertical, 2)
                            .background(Color.appPrimary.opacity(0.12))
                            .clipShape(Capsule())
                        }
                    }
 
                    if let pillText = thread.planPillText {
                        HStack(spacing: 4) {
                            Image(systemName: "calendar.badge.checkmark")
                                .font(.system(size: 11, weight: .semibold))
                            Text(pillText)
                                .font(.system(size: 13, weight: .medium, design: .rounded))
                        }
                        .foregroundColor(Color.appPrimary)
                        .padding(.horizontal, 8)
                        .padding(.vertical, 3)
                        .background(Color.appPrimary.opacity(0.15))
                        .cornerRadius(8)
                    } else {
                        Text(thread.displayText)
                            .font(.system(size: 15, weight: isUnread ? .medium : .regular, design: .rounded))
                            .foregroundColor(
                                isUnread ?
                                Color.appPrimaryText :
                                Color.appSecondaryText
                            )
                            .lineLimit(2)
                    }
                }
 
                Spacer()
 
                if !thread.timeText.isEmpty {
                    Text(thread.timeText)
                        .font(.system(size: 14, weight: .regular, design: .rounded))
                        .foregroundColor(Color.appSecondaryText)
                }
            }
            .padding(16)
            .background(Color.appCardBackground.opacity(isUnread ? 1.0 : 0.7))
            .cornerRadius(16)
        }
        .buttonStyle(.plain)
    }
}
 
// MARK: - Empty State
 
struct EmptyMessagesView: View {
    var body: some View {
        VStack(spacing: 24) {
            ZStack {
                Circle()
                    .fill(Color.appPrimary.opacity(0.2))
                    .frame(width: 120, height: 120)
 
                Image(systemName: "message.circle")
                    .font(.system(size: 60))
                    .foregroundColor(Color.appPrimary)
            }
 
            VStack(spacing: 12) {
                Text("No Messages Yet")
                    .font(.system(size: 28, weight: .bold, design: .rounded))
                    .foregroundColor(Color.appPrimaryText)
 
                Text("When you match with someone and\nboth say Yay, you can start chatting!")
                    .font(.system(size: 16, weight: .regular, design: .rounded))
                    .foregroundColor(Color.appSecondaryText)
                    .multilineTextAlignment(.center)
                    .lineSpacing(4)
            }
            .padding(.horizontal, 40)
 
            VStack(alignment: .leading, spacing: 8) {
                HStack(spacing: 8) {
                    Image(systemName: "play.circle.fill")
                        .foregroundColor(Color.appPrimary)
                    Text("Get Started")
                        .font(.system(size: 15, weight: .semibold, design: .rounded))
                        .foregroundColor(Color.appSecondaryText)
                }
 
                Text("Check the Matches tab to see potential friends. Say Yay to people you'd like to connect with!")
                    .font(.system(size: 14, weight: .regular, design: .rounded))
                    .foregroundColor(Color.appSecondaryText)
                    .lineSpacing(2)
            }
            .padding()
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(Color.appPrimary.opacity(0.1))
            .cornerRadius(12)
            .padding(.horizontal, 32)
        }
    }
}
 
#Preview("Messages List") {
    MainTabView()
}
 
#Preview("Empty Messages") {
    NavigationStack {
        ZStack {
            Color.appBackground
                .ignoresSafeArea()
            EmptyMessagesView()
        }
        .navigationTitle("Messages")
    }
}
