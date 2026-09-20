//
//  SearchRadiusSettingsView.swift
//  Avenue3
//
//  Created by George Anthony Pazdral II on 5/6/26.
//

import SwiftUI

/// Settings view for adjusting search radius
struct SearchRadiusSettingsView: View {
    @Bindable var viewModel: ProfileViewModel
    @Environment(\.dismiss) private var dismiss
    
    @State private var radiusMiles: Double
    @State private var isSaving = false
    
    init(viewModel: ProfileViewModel) {
        self.viewModel = viewModel
        _radiusMiles = State(initialValue: viewModel.radiusMiles)
    }
    
    var hasChanges: Bool {
        radiusMiles != viewModel.radiusMiles
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
                VStack(spacing: 32) {
                    // Header
                    VStack(spacing: 12) {
                        ZStack {
                            Circle()
                                .fill(
                                    LinearGradient(
                                        colors: [
                                            Color.appPrimary.opacity(0.2),
                                            Color.appPrimary.opacity(0.2)
                                        ],
                                        startPoint: .topLeading,
                                        endPoint: .bottomTrailing
                                    )
                                )
                                .frame(width: 100, height: 100)
                            
                            Image(systemName: "location.circle.fill")
                                .font(.system(size: 60))
                                .foregroundColor(Color.appPrimary)
                        }
                        
                        Text("Search Radius")
                            .font(.system(size: 28, weight: .bold, design: .rounded))
                            .foregroundColor(Color.appNavy)
                        
                        Text("How far away should we look for matches?")
                            .font(.system(size: 16, weight: .regular, design: .rounded))
                            .foregroundColor(Color(red: 0.50, green: 0.50, blue: 0.50))
                            .multilineTextAlignment(.center)
                    }
                    .padding(.top, 20)
                    .padding(.horizontal, 40)
                    
                    // Radius display
                    VStack(spacing: 8) {
                        Text("\(Int(radiusMiles))")
                            .font(.system(size: 72, weight: .bold, design: .rounded))
                            .foregroundStyle(
                                LinearGradient(
                                    colors: [
                                        Color.appPrimary,
                                        Color.appPrimary
                                    ],
                                    startPoint: .leading,
                                    endPoint: .trailing
                                )
                            )
                        
                        Text(radiusMiles == 1 ? "mile" : "miles")
                            .font(.system(size: 20, weight: .medium, design: .rounded))
                            .foregroundColor(Color(red: 0.50, green: 0.50, blue: 0.50))
                    }
                    
                    // Slider
                    VStack(spacing: 16) {
                        Slider(value: $radiusMiles, in: 5...25, step: 1)
                            .tint(Color.appPrimary)
                            .padding(.horizontal, 32)
                        
                        HStack {
                            Text("5 miles")
                                .font(.system(size: 13, weight: .medium, design: .rounded))
                                .foregroundColor(Color(red: 0.60, green: 0.60, blue: 0.60))
                            
                            Spacer()
                            
                            Text("25 miles")
                                .font(.system(size: 13, weight: .medium, design: .rounded))
                                .foregroundColor(Color(red: 0.60, green: 0.60, blue: 0.60))
                        }
                        .padding(.horizontal, 32)
                    }
                    
                    // Info card
                    VStack(alignment: .leading, spacing: 12) {
                        HStack(spacing: 8) {
                            Image(systemName: "map.fill")
                                .foregroundColor(Color.appPrimary)
                            
                            Text("Distance Guide")
                                .font(.system(size: 15, weight: .semibold, design: .rounded))
                                .foregroundColor(Color(red: 0.40, green: 0.40, blue: 0.40))
                        }
                        
                        VStack(alignment: .leading, spacing: 8) {
                            DistanceGuideRow(distance: "5-10 miles", description: "Your neighborhood")
                            DistanceGuideRow(distance: "10-15 miles", description: "Nearby areas")
                            DistanceGuideRow(distance: "15-25 miles", description: "Greater Austin area")
                        }
                    }
                    .padding()
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .background(Color.appPrimary.opacity(0.1))
                    .cornerRadius(12)
                    .padding(.horizontal, 32)
                    
                    // Info box
                    VStack(alignment: .leading, spacing: 8) {
                        HStack(spacing: 8) {
                            Image(systemName: "info.circle.fill")
                                .foregroundColor(Color.appPrimary)
                            
                            Text("How This Works")
                                .font(.system(size: 15, weight: .semibold, design: .rounded))
                                .foregroundColor(Color(red: 0.40, green: 0.40, blue: 0.40))
                        }
                        
                        VStack(alignment: .leading, spacing: 6) {
                            Text("• We'll show you people within this radius")
                            Text("• Larger radius = more potential matches")
                            Text("• Smaller radius = closer connections")
                            Text("• You can change this anytime")
                        }
                        .font(.system(size: 14, weight: .regular, design: .rounded))
                        .foregroundColor(Color(red: 0.50, green: 0.50, blue: 0.50))
                        .lineSpacing(2)
                    }
                    .padding()
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .background(Color.appPrimary.opacity(0.1))
                    .cornerRadius(12)
                    .padding(.horizontal, 32)
                    .padding(.bottom, 40)
                }
            }
            .scrollIndicators(.hidden)
        }
        .navigationTitle("Search Radius")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .navigationBarTrailing) {
                Button {
                    saveChanges()
                } label: {
                    if isSaving {
                        ProgressView()
                            .tint(Color.appPrimary)
                    } else {
                        Text("Save")
                            .font(.system(size: 17, weight: .semibold, design: .rounded))
                            .foregroundColor(
                                hasChanges ?
                                Color.appPrimary :
                                Color.gray
                            )
                    }
                }
                .disabled(!hasChanges || isSaving)
            }
        }
    }
    
    private func saveChanges() {
        guard hasChanges else { return }
        
        isSaving = true
        
        Task {
            viewModel.radiusMiles = radiusMiles
            let success = await viewModel.saveProfile()
            
            await MainActor.run {
                isSaving = false
                
                if success {
                    // Dismiss back to Settings menu after successful save
                    dismiss()
                }
            }
        }
    }
}

/// Row for distance guide
struct DistanceGuideRow: View {
    let distance: String
    let description: String
    
    var body: some View {
        HStack(spacing: 12) {
            Image(systemName: "arrow.right.circle.fill")
                .font(.system(size: 14))
                .foregroundColor(Color.appPrimary)
            
            VStack(alignment: .leading, spacing: 2) {
                Text(distance)
                    .font(.system(size: 14, weight: .semibold, design: .rounded))
                    .foregroundColor(Color(red: 0.35, green: 0.35, blue: 0.35))
                
                Text(description)
                    .font(.system(size: 12, weight: .regular, design: .rounded))
                    .foregroundColor(Color(red: 0.50, green: 0.50, blue: 0.50))
            }
        }
    }
}

#Preview {
    NavigationStack {
        SearchRadiusSettingsView(viewModel: ProfileViewModel())
    }
}
