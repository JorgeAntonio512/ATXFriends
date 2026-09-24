//
//  AvailabilitySettingsView.swift
//  Avenue3
//
//  Created by George Anthony Pazdral II on 5/6/26.
//

import SwiftUI

/// Settings view for managing user's day/slot combinations (at least 3, no maximum)
struct AvailabilitySettingsView: View {
    @Bindable var viewModel: ProfileViewModel

    var hasValidSelection: Bool {
        viewModel.selectedDaySlotCombos.count >= 3
    }

    var body: some View {
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

            ScrollView {
                VStack(spacing: 24) {
                    // Navigation warning banner
                    if !hasValidSelection {
                        HStack(spacing: 12) {
                            Image(systemName: "exclamationmark.triangle.fill")
                                .font(.system(size: 20))
                                .foregroundColor(.orange)

                            VStack(alignment: .leading, spacing: 4) {
                                Text("Select at least 3 time slots to continue")
                                    .font(.system(size: 15, weight: .semibold, design: .rounded))
                                    .foregroundColor(Color.appTextStrong)

                                Text("\(viewModel.selectedDaySlotCombos.count)/3 selected")
                                    .font(.system(size: 13, weight: .regular, design: .rounded))
                                    .foregroundColor(Color.appSecondaryText)
                            }

                            Spacer()
                        }
                        .padding()
                        .background(Color.orange.opacity(0.15))
                        .cornerRadius(12)
                        .padding(.horizontal, 16)
                        .padding(.top, 12)
                    }

                    // Header
                    VStack(spacing: 12) {
                        Text("Your Availability")
                            .font(.system(size: 28, weight: .bold, design: .rounded))
                            .foregroundColor(Color.appPrimaryText)

                        Text("Select at least 3 times when you're usually free to hang out")
                            .font(.system(size: 16, weight: .regular, design: .rounded))
                            .foregroundColor(Color.appSecondaryText)
                            .multilineTextAlignment(.center)
                    }
                    .padding(.top, 20)
                    .padding(.horizontal, 40)

                    // Selected slots
                    if !viewModel.selectedDaySlotCombos.isEmpty {
                        VStack(alignment: .leading, spacing: 12) {
                            Text("\(viewModel.selectedDaySlotCombos.count) selected")
                                .font(.system(size: 14, weight: .semibold, design: .rounded))
                                .foregroundColor(Color.appTextBody)
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

                    // Grid selector — always available, no maximum
                    VStack(alignment: .leading, spacing: 16) {
                        Text("Tap to add a time slot")
                            .font(.system(size: 14, weight: .semibold, design: .rounded))
                            .foregroundColor(Color.appTextBody)
                            .padding(.horizontal, 32)

                        TimeSlotGrid(viewModel: viewModel, onSelect: {
                            saveChanges()
                        })
                    }
                }
                .padding(.bottom, 40)
            }
            .scrollIndicators(.hidden)
        }
        .navigationTitle("Availability")
        .navigationBarTitleDisplayMode(.inline)
        .navigationBarBackButtonHidden(!hasValidSelection)
        .toolbar {
            // Custom back button that's disabled until at least 3 time slots are selected
            ToolbarItem(placement: .navigationBarLeading) {
                if !hasValidSelection {
                    Button {
                        // Do nothing - disabled
                    } label: {
                        HStack(spacing: 4) {
                            Image(systemName: "chevron.left")
                                .font(.system(size: 17, weight: .semibold))
                            Text("Back")
                        }
                        .foregroundColor(Color.gray.opacity(0.5))
                    }
                    .disabled(true)
                }
            }
        }
        .interactiveDismissDisabled(!hasValidSelection)
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
                            .foregroundColor(Color.appTextBody)
                        Text(slot.timeRange)
                            .font(.system(size: 9, weight: .regular, design: .rounded))
                            .multilineTextAlignment(.center)
                            .lineLimit(2)
                            .fixedSize(horizontal: false, vertical: true)
                            .foregroundColor(Color.appTextMuted)
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
                        .foregroundColor(Color.appTextBody)
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
                                            colors: [Color.appCardBackground.opacity(0.6), Color.appCardBackground.opacity(0.6)],
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
