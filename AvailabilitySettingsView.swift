//
//  AvailabilitySettingsView.swift
//  Avenue3
//
//  Created by George Anthony Pazdral II on 5/6/26.
//

import SwiftUI

/// Settings view for managing user's 3 day/slot combinations
struct AvailabilitySettingsView: View {
    @Bindable var viewModel: ProfileViewModel
    
    var canAddMore: Bool {
        viewModel.selectedDaySlotCombos.count < 3
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
                VStack(spacing: 24) {
                    // Header
                    VStack(spacing: 12) {
                        Text("Your Availability")
                            .font(.system(size: 28, weight: .bold, design: .rounded))
                            .foregroundColor(Color.appNavy)
                        
                        Text("Select 3 times when you're usually free to hang out")
                            .font(.system(size: 16, weight: .regular, design: .rounded))
                            .foregroundColor(Color(red: 0.50, green: 0.50, blue: 0.50))
                            .multilineTextAlignment(.center)
                    }
                    .padding(.top, 20)
                    .padding(.horizontal, 40)
                    
                    // Selected slots
                    if !viewModel.selectedDaySlotCombos.isEmpty {
                        VStack(alignment: .leading, spacing: 12) {
                            Text("Selected (\(viewModel.selectedDaySlotCombos.count)/3)")
                                .font(.system(size: 14, weight: .semibold, design: .rounded))
                                .foregroundColor(Color(red: 0.40, green: 0.40, blue: 0.40))
                                .padding(.horizontal, 32)
                            
                            VStack(spacing: 12) {
                                ForEach(viewModel.selectedDaySlotCombos, id: \.self) { combo in
                                    SelectedSlotChip(
                                        combo: combo,
                                        onRemove: {
                                            withAnimation(.spring(response: 0.3)) {
                                                viewModel.deselectDaySlotCombo(combo)
                                                saveChanges()
                                            }
                                        }
                                    )
                                }
                            }
                            .padding(.horizontal, 32)
                        }
                        .padding(.bottom, 8)
                    }
                    
                    // Grid selector
                    if canAddMore {
                        VStack(alignment: .leading, spacing: 16) {
                            Text("Tap to add a time slot")
                                .font(.system(size: 14, weight: .semibold, design: .rounded))
                                .foregroundColor(Color(red: 0.40, green: 0.40, blue: 0.40))
                                .padding(.horizontal, 32)
                            
                            TimeSlotGrid(viewModel: viewModel, onSelect: {
                                saveChanges()
                            })
                        }
                    } else {
                        VStack(spacing: 16) {
                            Image(systemName: "checkmark.circle.fill")
                                .font(.system(size: 60))
                                .foregroundColor(Color.appPrimary)
                            
                            Text("You've selected 3 time slots!")
                                .font(.system(size: 18, weight: .semibold, design: .rounded))
                                .foregroundColor(Color.appNavy)
                            
                            Text("Remove one to add a different time slot")
                                .font(.system(size: 15, weight: .regular, design: .rounded))
                                .foregroundColor(Color(red: 0.50, green: 0.50, blue: 0.50))
                                .multilineTextAlignment(.center)
                        }
                        .padding(.top, 20)
                        .padding(.horizontal, 40)
                    }
                }
                .padding(.bottom, 40)
            }
            .scrollIndicators(.hidden)
        }
        .navigationTitle("Availability")
        .navigationBarTitleDisplayMode(.inline)
    }
    
    private func saveChanges() {
        Task {
            _ = await viewModel.saveProfile()
        }
    }
}

/// Selected day/slot combination chip
struct SelectedSlotChip: View {
    let combo: DaySlotCombo
    let onRemove: () -> Void
    
    var body: some View {
        HStack(spacing: 12) {
            HStack(spacing: 6) {
                Text(combo.timeSlot.icon)
                    .font(.system(size: 16))
                
                Text("\(combo.dayOfWeek.rawValue) \(combo.timeSlot.rawValue)")
                    .font(.system(size: 15, weight: .semibold, design: .rounded))
            }
            .foregroundColor(.white)
            
            Spacer()
            
            Button(action: onRemove) {
                Image(systemName: "xmark.circle.fill")
                    .font(.system(size: 20))
                    .foregroundColor(.white.opacity(0.9))
            }
        }
        .padding()
        .background(
            LinearGradient(
                colors: [
                    Color.appPrimary,
                    Color.appPrimary
                ],
                startPoint: .leading,
                endPoint: .trailing
            )
        )
        .cornerRadius(12)
    }
}

/// 7-day x 5-slot grid for selecting time slots
struct TimeSlotGrid: View {
    @Bindable var viewModel: ProfileViewModel
    let onSelect: () -> Void
    
    var body: some View {
        VStack(spacing: 0) {
            // Header row with time slot labels
            HStack(spacing: 0) {
                // Empty corner
                Color.clear
                    .frame(width: 80)
                
                ForEach(TimeSlot.allCases) { slot in
                    VStack(spacing: 2) {
                        Text(slot.icon)
                            .font(.system(size: 16))
                        Text(slot.rawValue)
                            .font(.system(size: 10, weight: .semibold, design: .rounded))
                            .multilineTextAlignment(.center)
                            .lineLimit(2)
                            .fixedSize(horizontal: false, vertical: true)
                            .foregroundColor(Color(red: 0.40, green: 0.40, blue: 0.40))
                        Text(slot.timeRange)
                            .font(.system(size: 9, weight: .regular, design: .rounded))
                            .multilineTextAlignment(.center)
                            .lineLimit(2)
                            .fixedSize(horizontal: false, vertical: true)
                            .foregroundColor(Color(red: 0.60, green: 0.60, blue: 0.60))
                    }
                    .frame(maxWidth: .infinity)
                }
            }
            .padding(.bottom, 8)
            .padding(.horizontal, 16)
            
            // Grid rows
            ForEach(DayOfWeek.allCases) { day in
                HStack(spacing: 4) {
                    // Day label
                    Text(day.rawValue.prefix(3))
                        .font(.system(size: 12, weight: .semibold, design: .rounded))
                        .foregroundColor(Color(red: 0.40, green: 0.40, blue: 0.40))
                        .frame(width: 80, alignment: .leading)
                    
                    // Time slot buttons
                    ForEach(TimeSlot.allCases) { slot in
                        let combo = DaySlotCombo(dayOfWeek: day, timeSlot: slot)
                        let isSelected = viewModel.selectedDaySlotCombos.contains(where: { $0 == combo })
                        
                        Button {
                            withAnimation(.spring(response: 0.3)) {
                                if isSelected {
                                    viewModel.deselectDaySlotCombo(combo)
                                } else {
                                    viewModel.selectDaySlotCombo(day: day, slot: slot)
                                }
                                onSelect()
                            }
                        } label: {
                            ZStack {
                                RoundedRectangle(cornerRadius: 6)
                                    .fill(
                                        isSelected ?
                                        LinearGradient(
                                            colors: [
                                                Color.appPrimary,
                                                Color.appPrimary
                                            ],
                                            startPoint: .topLeading,
                                            endPoint: .bottomTrailing
                                        ) :
                                        LinearGradient(
                                            colors: [Color.white.opacity(0.6), Color.white.opacity(0.6)],
                                            startPoint: .topLeading,
                                            endPoint: .bottomTrailing
                                        )
                                    )
                                    .overlay(
                                        RoundedRectangle(cornerRadius: 6)
                                            .stroke(
                                                isSelected ? Color.clear : Color.appPrimary.opacity(0.3),
                                                lineWidth: 1
                                            )
                                    )
                                
                                if isSelected {
                                    Image(systemName: "checkmark")
                                        .font(.system(size: 12, weight: .bold))
                                        .foregroundColor(.white)
                                }
                            }
                            .frame(maxWidth: .infinity)
                            .frame(height: 36)
                        }
                        .buttonStyle(.plain)
                    }
                }
                .padding(.horizontal, 16)
                .padding(.vertical, 2)
            }
        }
    }
}

#Preview {
    NavigationStack {
        AvailabilitySettingsView(viewModel: ProfileViewModel())
    }
}
