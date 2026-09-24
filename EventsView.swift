//
//  EventsView.swift
//  Avenue3
//
//  Created by George Anthony Pazdral II on 6/6/26.
//

import SwiftUI

/// Main events list view showing available events
struct EventsView: View {
    @State private var viewModel = EventsViewModel()
    
    var body: some View {
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

                if viewModel.isLoading {
                    ProgressView()
                        .scaleEffect(1.5)
                        .tint(Color.appPrimary)
                } else if viewModel.events.isEmpty {
                    // Empty state
                    VStack(spacing: 20) {
                        Image(systemName: "calendar")
                            .font(.system(size: 60))
                            .foregroundColor(Color.appPrimary.opacity(0.5))
                        
                        Text("No events available")
                            .font(.system(size: 20, weight: .semibold, design: .rounded))
                            .foregroundColor(Color.appTextBody)
                    }
                } else {
                    ScrollView {
                        VStack(spacing: 20) {
                            ForEach(viewModel.events) { event in
                                EventCard(event: event, viewModel: viewModel)
                            }
                        }
                        .padding()
                    }
                }
            }
            .navigationTitle("Events")
            .navigationBarTitleDisplayMode(.large)
            .task {
                await viewModel.loadEvents()
            }
            .alert("Error", isPresented: .constant(viewModel.errorMessage != nil)) {
                Button("OK") {
                    viewModel.clearError()
                }
            } message: {
                if let error = viewModel.errorMessage {
                    Text(error)
                }
            }
        }
    }
}

// MARK: - Event Card

struct EventCard: View {
    let event: Event
    var viewModel: EventsViewModel
    
    var body: some View {
        NavigationLink(destination: EventDetailView(event: event, viewModel: viewModel)) {
            VStack(alignment: .leading, spacing: 0) {
                // Hero image
                AsyncImage(url: URL(string: event.heroImageURL)) { phase in
                    switch phase {
                    case .empty:
                        Rectangle()
                            .fill(Color.gray.opacity(0.3))
                            .frame(height: 200)
                            .overlay {
                                ProgressView()
                            }
                    case .success(let image):
                        image
                            .resizable()
                            .aspectRatio(contentMode: .fill)
                            .frame(height: 200)
                            .clipped()
                    case .failure:
                        Rectangle()
                            .fill(Color.gray.opacity(0.3))
                            .frame(height: 200)
                            .overlay {
                                Image(systemName: "photo")
                                    .font(.system(size: 40))
                                    .foregroundColor(.gray)
                            }
                    @unknown default:
                        EmptyView()
                    }
                }
                
                // Event info
                VStack(alignment: .leading, spacing: 12) {
                    Text(event.name)
                        .font(.system(size: 24, weight: .bold, design: .rounded))
                        .foregroundColor(Color.appPrimaryText)
                    
                    // Weekend dates
                    VStack(alignment: .leading, spacing: 6) {
                        ForEach(event.weekends) { weekend in
                            HStack(spacing: 8) {
                                Image(systemName: "calendar")
                                    .font(.system(size: 14))
                                    .foregroundColor(Color.appPrimary)
                                
                                Text("\(weekend.label): \(weekend.dateRangeString)")
                                    .font(.system(size: 15, weight: .medium, design: .rounded))
                                    .foregroundColor(Color.appSecondaryText)
                            }
                        }
                    }
                    
                    // RSVP status
                    if let rsvp = viewModel.getRSVP(for: event.id) {
                        HStack(spacing: 6) {
                            Image(systemName: "checkmark.circle.fill")
                                .font(.system(size: 14))
                                .foregroundColor(Color.appPrimary)
                            
                            Text("RSVPed for \(rsvp.weekends.count) weekend\(rsvp.weekends.count == 1 ? "" : "s")")
                                .font(.system(size: 14, weight: .semibold, design: .rounded))
                                .foregroundColor(Color.appPrimary)
                        }
                        .padding(.top, 4)
                    }
                }
                .padding()
            }
            .background(Color.appCardBackground)
            .cornerRadius(16)
            .shadow(color: .black.opacity(0.1), radius: 10, x: 0, y: 5)
        }
        .buttonStyle(PlainButtonStyle())
    }
}

#Preview {
    EventsView()
}
