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

    /// A tapped push notification's destination (set by MainTabView). Resolved
    /// against `viewModel.messageThreads` once loaded, then cleared.
    @Binding var pendingRoute: PendingThreadRoute?

    /// Called when `pendingRoute.fallbackToMatchesTab` is true and the target
    /// thread never shows up once loading finishes (e.g. a brand-new mutual
    /// match whose thread failed to load) — switches MainTabView to Matches.
    var onRouteFallbackToMatches: () -> Void

    /// Most recent activity first — latest message or plan update.
    private var sortedThreads: [MessageThread] {
        viewModel.messageThreads.sorted { $0.lastActivityDate > $1.lastActivityDate }
    }

    /// Opens the pending route's thread if it's in the loaded list, clears the
    /// route either way, and falls back to Matches when asked to and the
    /// thread never showed up. No-ops while still loading — it's re-checked by
    /// the `.task` and `.onChange(of: viewModel.messageThreads)` call sites,
    /// so it always runs again once loading finishes.
    private func resolvePendingRouteIfPossible() {
        guard let route = pendingRoute, !viewModel.isLoading else { return }
        pendingRoute = nil
        if let thread = viewModel.messageThreads.first(where: { $0.id == route.matchID }) {
            selectedThread = thread
            if let userID = FirebaseAuthService.shared.currentUserID {
                UnreadState.shared.markConversationRead(matchID: thread.id, userID: userID)
            }
        } else if route.fallbackToMatchesTab {
            onRouteFallbackToMatches()
        }
    }

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
                        LazyVStack(spacing: 12) {
                            ForEach(sortedThreads) { thread in
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
            resolvePendingRouteIfPossible()
        }
        .onChange(of: pendingRoute) { _, _ in
            resolvePendingRouteIfPossible()
        }
    }
}

// MARK: - Message Thread Row

struct MessageThreadRow: View {
    let thread: MessageThread
    let isUnread: Bool      // driven by UnreadState, not thread.unreadCount — covers both
                             // unread chat messages and unread plan-proposal messages
    let onTap: () -> Void

    private var accessibilityLabel: String {
        var parts = [thread.otherUser.displayName]
        parts.append(isUnread ? "Unread" : "")
        if let lastMessage = thread.lastMessage {
            parts.append("Last message: \(lastMessage.text)")
        } else {
            parts.append("Say hi")
        }
        if let pillText = thread.planPillText {
            parts.append("Plan: \(pillText)")
        }
        return parts.filter { !$0.isEmpty }.joined(separator: ". ")
    }

    var body: some View {
        Button(action: onTap) {
            HStack(alignment: .top, spacing: 12) {
                avatar

                VStack(alignment: .leading, spacing: 4) {
                    HStack(spacing: 6) {
                        Text(thread.otherUser.displayName)
                            .font(.system(size: 17, weight: isUnread ? .bold : .semibold, design: .rounded))
                            .foregroundColor(Color.appPrimaryText)
                            .lineLimit(1)

                        Spacer(minLength: 8)

                        if !thread.timeText.isEmpty {
                            Text(thread.timeText)
                                .font(.system(size: 13, weight: .regular, design: .rounded))
                                .foregroundColor(isUnread ? Color.appPrimary : Color.appSecondaryText)
                                .lineLimit(1)
                                .fixedSize(horizontal: true, vertical: false)
                        }
                    }

                    if let lastMessage = thread.lastMessage {
                        Text(lastMessage.text)
                            .font(.system(size: 15, weight: isUnread ? .medium : .regular, design: .rounded))
                            .foregroundColor(isUnread ? Color.appPrimaryText : Color.appSecondaryText)
                            .lineLimit(1)
                    } else {
                        Text("Say hi 👋")
                            .font(.system(size: 15, weight: .regular, design: .rounded))
                            .foregroundColor(Color.appSecondaryText)
                    }

                    if let pillText = thread.planPillText {
                        HStack(spacing: 4) {
                            Image(systemName: "calendar.badge.checkmark")
                                .font(.system(size: 11, weight: .semibold))
                            Text(pillText)
                                .font(.system(size: 13, weight: .medium, design: .rounded))
                                .lineLimit(1)
                                .truncationMode(.tail)
                        }
                        .foregroundColor(Color.appPrimary)
                        .padding(.horizontal, 8)
                        .padding(.vertical, 3)
                        .background(Color.appPrimary.opacity(0.15))
                        .cornerRadius(8)
                    }
                }
            }
            .padding(16)
            .background(Color.appCardBackground.opacity(isUnread ? 1.0 : 0.7))
            .cornerRadius(16)
        }
        .buttonStyle(.plain)
        .accessibilityElement(children: .combine)
        .accessibilityLabel(accessibilityLabel)
    }

    // MARK: - Avatar

    private var avatar: some View {
        AvatarRing(
            photoURL: thread.otherUserPhotoURL,
            displayName: thread.otherUser.displayName,
            size: 64,
            ringStyle: isUnread ? .needsAttention : .soft
        )
    }
}

// MARK: - Empty State

struct EmptyMessagesView: View {
    var body: some View {
        VStack(spacing: 12) {
            Text("No conversations yet")
                .font(.system(size: 20, weight: .bold, design: .rounded))
                .foregroundColor(Color.appPrimaryText)

            Text("Match with someone, then propose a plan to start chatting.")
                .font(.system(size: 15, weight: .regular, design: .rounded))
                .foregroundColor(Color.appSecondaryText)
                .multilineTextAlignment(.center)
                .lineSpacing(3)
        }
        .padding(.horizontal, 40)
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
