//
//  EventDetailView.swift
//  Avenue3
//
//  Created by George Anthony Pazdral II on 6/6/26.
//

import SwiftUI
import FirebaseFirestore

/// Detailed view for a single event showing weekends, RSVP options, and attendees
struct EventDetailView: View {
    let event: Event
    @State var viewModel: EventsViewModel
    
    @State private var selectedWeekends: Set<Int> = []
    @State private var showingRSVPSuccess = false
    @State private var navigateToThread: MessageThread?
    
    private var currentUserID: String {
        FirebaseAuthService.shared.currentUserID ?? ""
    }
    
    var body: some View {
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
            
            ScrollView {
                VStack(alignment: .leading, spacing: 24) {
                    // Hero image
                    AsyncImage(url: URL(string: event.heroImageURL)) { phase in
                        switch phase {
                        case .empty:
                            Rectangle()
                                .fill(Color.gray.opacity(0.3))
                                .frame(height: 250)
                                .overlay {
                                    ProgressView()
                                }
                        case .success(let image):
                            image
                                .resizable()
                                .aspectRatio(contentMode: .fill)
                                .frame(height: 250)
                                .clipped()
                        case .failure:
                            Rectangle()
                                .fill(Color.gray.opacity(0.3))
                                .frame(height: 250)
                                .overlay {
                                    Image(systemName: "photo")
                                        .font(.system(size: 50))
                                        .foregroundColor(.gray)
                                }
                        @unknown default:
                            EmptyView()
                        }
                    }
                    .cornerRadius(16)
                    .shadow(color: .black.opacity(0.1), radius: 10, x: 0, y: 5)
                    .padding(.horizontal)
                    
                    VStack(alignment: .leading, spacing: 20) {
                        // Event name
                        Text(event.name)
                            .font(.system(size: 32, weight: .bold, design: .rounded))
                            .foregroundColor(Color.appNavy)
                            .padding(.horizontal)
                        
                        // Weekend selection
                        VStack(alignment: .leading, spacing: 16) {
                            Text("Select Your Weekends")
                                .font(.system(size: 20, weight: .semibold, design: .rounded))
                                .foregroundColor(Color.appNavy)
                                .padding(.horizontal)
                            
                            ForEach(event.weekends) { weekend in
                                WeekendSelectionCard(
                                    weekend: weekend,
                                    isSelected: selectedWeekends.contains(weekend.weekendNumber),
                                    onToggle: {
                                        toggleWeekend(weekend.weekendNumber)
                                    }
                                )
                                .padding(.horizontal)
                            }
                        }
                        
                        // RSVP button
                        if !selectedWeekends.isEmpty {
                            Button {
                                Task {
                                    await submitRSVP()
                                }
                            } label: {
                                Text("RSVP for \(selectedWeekends.count) Weekend\(selectedWeekends.count == 1 ? "" : "s")")
                                    .font(.system(size: 18, weight: .semibold, design: .rounded))
                                    .foregroundColor(.white)
                                    .frame(maxWidth: .infinity)
                                    .padding()
                                    .background(
                                        LinearGradient(
                                            colors: [
                                                Color.appNavy,
                                                Color.appNavy
                                            ],
                                            startPoint: .topLeading,
                                            endPoint: .bottomTrailing
                                        )
                                    )
                                    .cornerRadius(12)
                                    .shadow(color: Color.appPrimary.opacity(0.4), radius: 8, x: 0, y: 4)
                            }
                            .padding(.horizontal)
                        }
                        
                        // Attendees list (only shown after RSVP)
                        if viewModel.getRSVP(for: event.id) != nil {
                            Divider()
                                .padding(.vertical, 8)
                            
                            VStack(alignment: .leading, spacing: 16) {
                                HStack {
                                    Text("Attendees")
                                        .font(.system(size: 20, weight: .semibold, design: .rounded))
                                        .foregroundColor(Color.appNavy)
                                    
                                    Spacer()
                                    
                                    Text("\(viewModel.attendees.count)")
                                        .font(.system(size: 16, weight: .semibold, design: .rounded))
                                        .foregroundColor(Color(red: 0.50, green: 0.50, blue: 0.50))
                                }
                                .padding(.horizontal)
                                
                                if viewModel.attendees.isEmpty {
                                    Text("No other attendees yet")
                                        .font(.system(size: 15, weight: .regular, design: .rounded))
                                        .foregroundColor(Color(red: 0.60, green: 0.60, blue: 0.60))
                                        .padding(.horizontal)
                                } else {
                                    ForEach(viewModel.attendees, id: \.id) { attendee in
                                        AttendeeCard(
                                            attendee: attendee,
                                            onDMTapped: {
                                                Task {
                                                    await initiateEventDM(with: attendee)
                                                }
                                            }
                                        )
                                        .padding(.horizontal)
                                    }
                                }
                            }
                        }
                    }
                    
                    Spacer()
                        .frame(height: 40)
                }
            }
        }
        .navigationTitle("Event Details")
        .navigationBarTitleDisplayMode(.inline)
        .onAppear {
            loadUserSelection()
        }
        .task {
            // Load attendees if user has already RSVPed
            if viewModel.getRSVP(for: event.id) != nil {
                await viewModel.loadAttendees(for: event.id)
            }
        }
        .alert("RSVP Confirmed!", isPresented: $showingRSVPSuccess) {
            Button("OK") {
                showingRSVPSuccess = false
            }
        } message: {
            Text("You're all set for \(event.name)!")
        }
        .navigationDestination(item: $navigateToThread) { thread in
            // Use EventMessageThreadView for event threads, MessageThreadView for regular threads
            if thread.isEventThread {
                EventMessageThreadView(thread: thread, viewModel: MessagingViewModel())
            } else {
                MessageThreadView(thread: thread, viewModel: MessagingViewModel())
            }
        }
    }
    
    // MARK: - Helper Methods
    
    private func loadUserSelection() {
        if let rsvp = viewModel.getRSVP(for: event.id) {
            selectedWeekends = Set(rsvp.weekends)
        }
    }
    
    private func toggleWeekend(_ weekend: Int) {
        if selectedWeekends.contains(weekend) {
            selectedWeekends.remove(weekend)
        } else {
            selectedWeekends.insert(weekend)
        }
    }
    
    private func submitRSVP() async {
        let weekendsArray = Array(selectedWeekends).sorted()
        await viewModel.addOrUpdateRSVP(eventID: event.id, weekends: weekendsArray)
        
        // Load attendees after RSVP
        await viewModel.loadAttendees(for: event.id)
        
        showingRSVPSuccess = true
    }
    
    /// Initiates or retrieves an event DM thread
    private func initiateEventDM(with attendee: FirebaseUser) async {
        guard !currentUserID.isEmpty else { return }
        
        // Create deterministic thread ID: always sort user IDs alphabetically
        let sortedIDs = [currentUserID, attendee.id].sorted()
        let threadID = "event_\(event.id)_\(sortedIDs[0])_\(sortedIDs[1])"
        
        print("🟢 DEBUG: initiateEventDM called")
        print("🟢 DEBUG: Event ID: \(event.id)")
        print("🟢 DEBUG: Current User ID: \(currentUserID)")
        print("🟢 DEBUG: Attendee ID: \(attendee.id)")
        print("🟢 DEBUG: Generated Thread ID: \(threadID)")
        
        // Create MessageThread with event parameter (not match)
        // This ensures isEventThread returns true
        let thread = MessageThread(
            id: threadID,
            match: nil,
            event: event,
            otherUser: attendee.toUser(),
            lastMessage: nil,
            unreadCount: 0
        )
        
        navigateToThread = thread
    }
}

// MARK: - Weekend Selection Card

struct WeekendSelectionCard: View {
    let weekend: EventWeekend
    let isSelected: Bool
    let onToggle: () -> Void
    
    var body: some View {
        Button(action: onToggle) {
            HStack(spacing: 16) {
                // Checkbox
                ZStack {
                    Circle()
                        .strokeBorder(
                            isSelected ?
                            Color.appPrimary :
                            Color.gray.opacity(0.3),
                            lineWidth: 2
                        )
                        .background(
                            Circle()
                                .fill(isSelected ?
                                      Color.appPrimary :
                                      Color.clear
                                )
                        )
                        .frame(width: 24, height: 24)
                    
                    if isSelected {
                        Image(systemName: "checkmark")
                            .font(.system(size: 14, weight: .bold))
                            .foregroundColor(.white)
                    }
                }
                
                VStack(alignment: .leading, spacing: 4) {
                    Text(weekend.label)
                        .font(.system(size: 18, weight: .semibold, design: .rounded))
                        .foregroundColor(Color.appNavy)
                    
                    Text(weekend.dateRangeString)
                        .font(.system(size: 15, weight: .regular, design: .rounded))
                        .foregroundColor(Color(red: 0.50, green: 0.50, blue: 0.50))
                }
                
                Spacer()
            }
            .padding()
            .background(
                RoundedRectangle(cornerRadius: 12)
                    .fill(isSelected ?
                          Color.appPrimary.opacity(0.1) :
                          Color.white
                    )
            )
            .overlay(
                RoundedRectangle(cornerRadius: 12)
                    .strokeBorder(
                        isSelected ?
                        Color.appPrimary :
                        Color.clear,
                        lineWidth: 2
                    )
            )
            .shadow(color: .black.opacity(0.05), radius: 5, x: 0, y: 2)
        }
        .buttonStyle(PlainButtonStyle())
    }
}

// MARK: - Attendee Card

struct AttendeeCard: View {
    let attendee: FirebaseUser
    let onDMTapped: () -> Void
    
    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            // Profile photos
            HStack(spacing: 8) {
                ForEach(0..<3, id: \.self) { index in
                    if index < attendee.photoURLs.count {
                        AsyncImage(url: URL(string: attendee.photoURLs[index])) { phase in
                            switch phase {
                            case .empty:
                                Rectangle()
                                    .fill(Color.gray.opacity(0.3))
                                    .frame(width: 100, height: 100)
                                    .cornerRadius(8)
                                    .overlay {
                                        ProgressView()
                                    }
                            case .success(let image):
                                image
                                    .resizable()
                                    .aspectRatio(contentMode: .fill)
                                    .frame(width: 100, height: 100)
                                    .cornerRadius(8)
                                    .clipped()
                            case .failure:
                                Rectangle()
                                    .fill(Color.gray.opacity(0.3))
                                    .frame(width: 100, height: 100)
                                    .cornerRadius(8)
                                    .overlay {
                                        Image(systemName: "photo")
                                            .foregroundColor(.gray)
                                    }
                            @unknown default:
                                EmptyView()
                            }
                        }
                    }
                }
            }
            
            // Name
            Text(attendee.displayName)
                .font(.system(size: 20, weight: .bold, design: .rounded))
                .foregroundColor(Color.appNavy)
            
            // Activities
            if !attendee.activities.isEmpty {
                VStack(alignment: .leading, spacing: 6) {
                    Text("Activities")
                        .font(.system(size: 14, weight: .semibold, design: .rounded))
                        .foregroundColor(Color(red: 0.50, green: 0.50, blue: 0.50))
                    
                    Text(attendee.activities.map { $0.name }.joined(separator: ", "))
                        .font(.system(size: 15, weight: .regular, design: .rounded))
                        .foregroundColor(Color(red: 0.40, green: 0.40, blue: 0.40))
                }
            }
            
            // Day/Time slots
            if !attendee.daySlotCombos.isEmpty {
                VStack(alignment: .leading, spacing: 6) {
                    Text("Availability")
                        .font(.system(size: 14, weight: .semibold, design: .rounded))
                        .foregroundColor(Color(red: 0.50, green: 0.50, blue: 0.50))
                    
                    Text(attendee.daySlotCombos.map { $0.displayName }.joined(separator: ", "))
                        .font(.system(size: 15, weight: .regular, design: .rounded))
                        .foregroundColor(Color(red: 0.40, green: 0.40, blue: 0.40))
                }
            }
            
            // DM button
            Button(action: onDMTapped) {
                HStack {
                    Image(systemName: "message.fill")
                        .font(.system(size: 16))
                    
                    Text("Send Message")
                        .font(.system(size: 16, weight: .semibold, design: .rounded))
                }
                .foregroundColor(.white)
                .frame(maxWidth: .infinity)
                .padding(.vertical, 12)
                .background(
                    LinearGradient(
                        colors: [
                            Color.appPrimary,
                            Color.appPrimary
                        ],
                        startPoint: .topLeading,
                        endPoint: .bottomTrailing
                    )
                )
                .cornerRadius(10)
                .shadow(color: Color.appNavy.opacity(0.3), radius: 6, x: 0, y: 3)
            }
        }
        .padding()
        .background(Color.white)
        .cornerRadius(12)
        .shadow(color: .black.opacity(0.08), radius: 8, x: 0, y: 4)
    }
}

#Preview {
    let event = Event(
        id: "ACL2025",
        name: "Austin City Limits 2025",
        heroImageURL: "https://example.com/acl.jpg",
        weekends: [
            EventWeekend(
                weekendNumber: 1,
                label: "Weekend 1",
                startDate: Date(),
                endDate: Date().addingTimeInterval(86400 * 3)
            ),
            EventWeekend(
                weekendNumber: 2,
                label: "Weekend 2",
                startDate: Date().addingTimeInterval(86400 * 7),
                endDate: Date().addingTimeInterval(86400 * 10)
            )
        ]
    )
    
    EventDetailView(event: event, viewModel: EventsViewModel())
}
