//
//  MessagesView.swift
//  Avenue3
//
//  Created by George Anthony Pazdral II on 5/6/26.
//

import SwiftUI

/// Messages tab showing all message threads (regular and event)
struct MessagesView: View {
    @State private var viewModel = MessagingViewModel()
    @State private var selectedThread: MessageThread?
    @State private var showingThread = false
    
    var body: some View {
        NavigationStack {
            ZStack {
                // Warm gradient background
                LinearGradient(
                    colors: [
                        Color.white,
                        Color.white
                    ],
                    startPoint: .topLeading,
                    endPoint: .bottomTrailing
                )
                .ignoresSafeArea()
                
                if viewModel.isLoading {
                    ProgressView()
                        .scaleEffect(1.5)
                        .tint(Color.appPrimary)
                } else if viewModel.allThreads.isEmpty {
                    emptyStateView
                } else {
                    ScrollView {
                        VStack(spacing: 24) {
                            // Regular Messages Section (Individuals & Couples)
                            if !viewModel.regularThreads.isEmpty {
                                VStack(alignment: .leading, spacing: 12) {
                                    Text("Individuals & Couples")
                                        .font(.system(size: 22, weight: .bold, design: .rounded))
                                        .foregroundColor(Color.appNavy)
                                        .padding(.horizontal, 20)
                                    
                                    ForEach(viewModel.regularThreads) { thread in
                                        ThreadRow(thread: thread)
                                            .onTapGesture {
                                                selectedThread = thread
                                                showingThread = true
                                            }
                                    }
                                }
                            }
                            
                            // Event Messages Section
                            VStack(alignment: .leading, spacing: 12) {
                                Text("Events")
                                    .font(.system(size: 22, weight: .bold, design: .rounded))
                                    .foregroundColor(Color.appNavy)
                                    .padding(.horizontal, 20)
                                
                                if !viewModel.eventThreads.isEmpty {
                                    ForEach(viewModel.eventThreads) { thread in
                                        EventThreadRow(thread: thread)
                                            .onTapGesture {
                                                selectedThread = thread
                                                showingThread = true
                                            }
                                    }
                                } else {
                                    // Coming soon placeholder
                                    VStack(spacing: 12) {
                                        Image(systemName: "calendar.badge.clock")
                                            .font(.system(size: 40))
                                            .foregroundColor(Color.appPrimary.opacity(0.5))
                                        
                                        Text("Coming Soon")
                                            .font(.system(size: 17, weight: .semibold, design: .rounded))
                                            .foregroundColor(Color.appNavy)
                                        
                                        Text("Event-based connections will appear here")
                                            .font(.system(size: 14, weight: .regular, design: .rounded))
                                            .foregroundColor(Color(red: 0.55, green: 0.55, blue: 0.55))
                                            .multilineTextAlignment(.center)
                                    }
                                    .frame(maxWidth: .infinity)
                                    .padding(.vertical, 32)
                                    .padding(.horizontal, 20)
                                    .background(Color.white.opacity(0.5))
                                    .cornerRadius(16)
                                    .padding(.horizontal, 20)
                                }
                            }
                        }
                        .padding(.top, 20)
                        .padding(.bottom, 40)
                    }
                }
            }
            .navigationTitle("Messages")
            .navigationBarTitleDisplayMode(.large)
            .sheet(isPresented: $showingThread) {
                if let thread = selectedThread {
                    if thread.isEventThread {
                        EventMessageThreadView(thread: thread, viewModel: viewModel)
                    } else {
                        MessageThreadView(thread: thread, viewModel: viewModel)
                    }
                }
            }
            .task {
                await viewModel.fetchThreads()
            }
            .refreshable {
                await viewModel.fetchThreads()
            }
        }
    }
    
    // MARK: - Empty State
    
    private var emptyStateView: some View {
        VStack(spacing: 20) {
            ZStack {
                Circle()
                    .fill(Color.appPrimary.opacity(0.2))
                    .frame(width: 100, height: 100)
                
                Image(systemName: "message.fill")
                    .font(.system(size: 50))
                    .foregroundColor(Color.appPrimary)
            }
            
            VStack(spacing: 8) {
                Text("No Messages Yet")
                    .font(.system(size: 24, weight: .bold, design: .rounded))
                    .foregroundColor(Color.appNavy)
                
                Text("Start matching with people to begin chatting!")
                    .font(.system(size: 16, weight: .regular, design: .rounded))
                    .foregroundColor(Color(red: 0.50, green: 0.50, blue: 0.50))
                    .multilineTextAlignment(.center)
                    .padding(.horizontal, 40)
            }
        }
    }
}

// MARK: - Thread Row (Regular DMs)

struct ThreadRow: View {
    let thread: MessageThread
    
    var body: some View {
        HStack(spacing: 12) {
            // Profile photo
            if let photoURL = thread.otherUserPhotoURL {
                AsyncImage(url: URL(string: photoURL)) { image in
                    image
                        .resizable()
                        .scaledToFill()
                } placeholder: {
                    Circle()
                        .fill(Color.appPrimary.opacity(0.3))
                        .overlay {
                            Image(systemName: "person.fill")
                                .foregroundColor(.white)
                        }
                }
                .frame(width: 56, height: 56)
                .clipShape(Circle())
            } else {
                Circle()
                    .fill(Color.appPrimary.opacity(0.3))
                    .frame(width: 56, height: 56)
                    .overlay {
                        Image(systemName: "person.fill")
                            .foregroundColor(.white)
                    }
            }
            
            // Thread info
            VStack(alignment: .leading, spacing: 4) {
                HStack {
                    Text(thread.otherUser.displayName)
                        .font(.system(size: 17, weight: .semibold, design: .rounded))
                        .foregroundColor(Color(red: 0.35, green: 0.35, blue: 0.35))
                    
                    Spacer()
                    
                    if let lastMessage = thread.lastMessage {
                        Text(lastMessage.sentAt.formatted(date: .omitted, time: .shortened))
                            .font(.system(size: 13, weight: .regular, design: .rounded))
                            .foregroundColor(Color(red: 0.60, green: 0.60, blue: 0.60))
                    }
                }
                
                HStack {
                    if let lastMessage = thread.lastMessage {
                        Text(lastMessage.text)
                            .font(.system(size: 15, weight: .regular, design: .rounded))
                            .foregroundColor(Color(red: 0.55, green: 0.55, blue: 0.55))
                            .lineLimit(2)
                    } else {
                        Text("Say hello!")
                            .font(.system(size: 15, weight: .regular, design: .rounded))
                            .foregroundColor(Color(red: 0.55, green: 0.55, blue: 0.55))
                            .italic()
                    }
                    
                    Spacer()
                    
                    if thread.unreadCount > 0 {
                        ZStack {
                            Circle()
                                .fill(Color.appPrimary)
                                .frame(width: 24, height: 24)
                            
                            Text("\(thread.unreadCount)")
                                .font(.system(size: 12, weight: .bold, design: .rounded))
                                .foregroundColor(.white)
                        }
                    }
                }
            }
        }
        .padding(16)
        .background(Color.white.opacity(0.7))
        .cornerRadius(16)
        .shadow(color: .black.opacity(0.05), radius: 8, x: 0, y: 4)
        .padding(.horizontal, 20)
    }
}

// MARK: - Event Thread Row

struct EventThreadRow: View {
    let thread: MessageThread
    
    var eventName: String {
        thread.event?.name ?? "Event"
    }
    
    var body: some View {
        HStack(spacing: 12) {
            // Profile photo
            if let photoURL = thread.otherUserPhotoURL {
                AsyncImage(url: URL(string: photoURL)) { image in
                    image
                        .resizable()
                        .scaledToFill()
                } placeholder: {
                    Circle()
                        .fill(Color.appPrimary.opacity(0.3))
                        .overlay {
                            Image(systemName: "person.fill")
                                .foregroundColor(.white)
                        }
                }
                .frame(width: 56, height: 56)
                .clipShape(Circle())
            } else {
                Circle()
                    .fill(Color.appPrimary.opacity(0.3))
                    .frame(width: 56, height: 56)
                    .overlay {
                        Image(systemName: "person.fill")
                            .foregroundColor(.white)
                    }
            }
            
            // Thread info
            VStack(alignment: .leading, spacing: 4) {
                HStack {
                    VStack(alignment: .leading, spacing: 2) {
                        Text(thread.otherUser.displayName)
                            .font(.system(size: 17, weight: .semibold, design: .rounded))
                            .foregroundColor(Color(red: 0.35, green: 0.35, blue: 0.35))
                        
                        // Event name badge
                        HStack(spacing: 4) {
                            Image(systemName: "calendar")
                                .font(.system(size: 10))
                            Text(eventName)
                                .font(.system(size: 12, weight: .medium, design: .rounded))
                        }
                        .foregroundColor(Color.appPrimary)
                        .padding(.horizontal, 8)
                        .padding(.vertical, 4)
                        .background(Color.appPrimary.opacity(0.15))
                        .cornerRadius(8)
                    }
                    
                    Spacer()
                    
                    if let lastMessage = thread.lastMessage {
                        Text(lastMessage.sentAt.formatted(date: .omitted, time: .shortened))
                            .font(.system(size: 13, weight: .regular, design: .rounded))
                            .foregroundColor(Color(red: 0.60, green: 0.60, blue: 0.60))
                    }
                }
                
                HStack {
                    if let lastMessage = thread.lastMessage {
                        Text(lastMessage.text)
                            .font(.system(size: 15, weight: .regular, design: .rounded))
                            .foregroundColor(Color(red: 0.55, green: 0.55, blue: 0.55))
                            .lineLimit(2)
                    } else {
                        Text("Say hello!")
                            .font(.system(size: 15, weight: .regular, design: .rounded))
                            .foregroundColor(Color(red: 0.55, green: 0.55, blue: 0.55))
                            .italic()
                    }
                    
                    Spacer()
                    
                    if thread.unreadCount > 0 {
                        ZStack {
                            Circle()
                                .fill(Color.appPrimary)
                                .frame(width: 24, height: 24)
                            
                            Text("\(thread.unreadCount)")
                                .font(.system(size: 12, weight: .bold, design: .rounded))
                                .foregroundColor(.white)
                        }
                    }
                }
            }
        }
        .padding(16)
        .background(Color.white.opacity(0.7))
        .cornerRadius(16)
        .shadow(color: .black.opacity(0.05), radius: 8, x: 0, y: 4)
        .padding(.horizontal, 20)
    }
}

// MARK: - Preview

#Preview {
    MessagesView()
}
