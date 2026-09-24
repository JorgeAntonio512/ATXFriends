//
//  TimeSlotPickerView.swift
//  Avenue3
//
//  Created by George Anthony Pazdral II on 5/6/26.
//

import SwiftUI

/// Step 4: Pick at least 3 day/slot combos from a 7×5 grid (no maximum)
struct TimeSlotPickerView: View {
    @Bindable var viewModel: ProfileViewModel
    let onNext: () -> Void
    let onBack: () -> Void

    var canContinue: Bool {
        viewModel.selectedDaySlotCombos.count >= 3
    }
    
    var body: some View {
        VStack(spacing: 0) {
            ScrollView {
                VStack(spacing: 24) {
                    // Header
                    VStack(spacing: 12) {
                        Text("When Are You\nAvailable?")
                            .font(.system(size: 32, weight: .bold, design: .rounded))
                            .foregroundColor(Color.appPrimaryText)
                            .multilineTextAlignment(.center)
                        
                        Text("Pick at least 3 times when you're usually\nfree to hang out. Pick more if you're free a lot!")
                            .font(.system(size: 17, weight: .regular, design: .rounded))
                            .foregroundColor(Color.appSecondaryText)
                            .multilineTextAlignment(.center)
                            .lineSpacing(4)
                    }
                    .padding(.top, 20)
                    .padding(.horizontal, 40)

                    // Selected count
                    if !viewModel.selectedDaySlotCombos.isEmpty {
                        VStack(alignment: .leading, spacing: 12) {
                            Text("\(viewModel.selectedDaySlotCombos.count) selected")
                                .font(.system(size: 14, weight: .semibold, design: .rounded))
                                .foregroundColor(Color.appSecondaryText)
                                .padding(.horizontal, 4)
                            
                            FlowLayout(spacing: 8) {
                                ForEach(viewModel.selectedDaySlotCombos, id: \.self) { combo in
                                    SelectedTimeChip(
                                        combo: combo,
                                        onRemove: {
                                            viewModel.deselectDaySlotCombo(combo)
                                        }
                                    )
                                }
                            }
                        }
                        .padding(.horizontal, 32)
                    }
                    

                    // Grid of days and time slots
                    VStack(spacing: 16) {
                        ForEach(DayOfWeek.allCases, id: \.self) { day in
                            VStack(alignment: .leading, spacing: 8) {
                                Text(day.rawValue)
                                    .font(.system(size: 16, weight: .bold, design: .rounded))
                                    .foregroundColor(Color.appPrimaryText)
                                    .padding(.leading, 4)
                                
                                ScrollView(.horizontal, showsIndicators: false) {
                                    HStack(spacing: 8) {
                                        ForEach(TimeSlot.allCases, id: \.self) { slot in
                                            TimeSlotButton(
                                                day: day,
                                                slot: slot,
                                                isSelected: viewModel.selectedDaySlotCombos.contains(
                                                    where: { $0.dayOfWeek == day && $0.timeSlot == slot }
                                                ),
                                                onTap: {
                                                    let combo = DaySlotCombo(dayOfWeek: day, timeSlot: slot)
                                                    if viewModel.selectedDaySlotCombos.contains(combo) {
                                                        viewModel.deselectDaySlotCombo(combo)
                                                    } else {
                                                        viewModel.selectDaySlotCombo(day: day, slot: slot)
                                                    }
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                    .padding(.horizontal, 32)
                    .padding(.bottom, 100)
                }
            }
            .scrollIndicators(.hidden)
            
            // Bottom buttons
            VStack(spacing: 12) {
                // Continue button
                Button {
                    onNext()
                } label: {
                    Text("Continue")
                        .font(.system(size: 18, weight: .semibold, design: .rounded))
                        .foregroundColor(.white)
                        .frame(maxWidth: .infinity)
                        .frame(height: 56)
                        .background(
                            canContinue ?
                            LinearGradient(
                                colors: [
                                    Color.appPrimary,
                                    Color.appPrimary
                                ],
                                startPoint: .leading,
                                endPoint: .trailing
                            ) :
                            LinearGradient(
                                colors: [Color.gray.opacity(0.3), Color.gray.opacity(0.3)],
                                startPoint: .leading,
                                endPoint: .trailing
                            )
                        )
                        .cornerRadius(16)
                        .shadow(
                            color: canContinue ?
                            Color.appPrimary.opacity(0.3) :
                            Color.clear,
                            radius: 12,
                            x: 0,
                            y: 6
                        )
                }
                .disabled(!canContinue)
                
                // Back button
                Button {
                    onBack()
                } label: {
                    Text("Back")
                        .font(.system(size: 16, weight: .medium, design: .rounded))
                        .foregroundColor(Color.appPrimary)
                }
            }
            .padding(.horizontal, 32)
            .padding(.vertical, 20)
            .background(
                LinearGradient(
                    colors: [
                        Color.appCardBackground.opacity(0.95),
                        Color.appCardBackground.opacity(0.95)
                    ],
                    startPoint: .top,
                    endPoint: .bottom
                )
                .shadow(color: .black.opacity(0.1), radius: 10, x: 0, y: -5)
            )
        }
    }
}

/// Individual time slot button
struct TimeSlotButton: View {
    let day: DayOfWeek
    let slot: TimeSlot
    let isSelected: Bool
    let onTap: () -> Void
    
    var body: some View {
        Button(action: onTap) {
            VStack(spacing: 4) {
                // Line 1: emoji + slot name
                Text("\(slot.icon) \(slot.rawValue)")
                    .font(.system(size: 12, weight: .semibold, design: .rounded))
                    .foregroundColor(
                        isSelected ?
                        .white :
                        Color.appTextBody
                    )

                // Line 2: time range
                Text(slot.timeRange)
                    .font(.system(size: 10, weight: .regular, design: .rounded))
                    .foregroundColor(
                        isSelected ?
                        .white.opacity(0.8) :
                        Color.appTextMuted
                    )
            }
            .frame(width: 90, height: 70)
            .background(
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
                    colors: [Color.appCardBackground.opacity(0.7), Color.appCardBackground.opacity(0.7)],
                    startPoint: .topLeading,
                    endPoint: .bottomTrailing
                )
            )
            .cornerRadius(12)
            .overlay(
                RoundedRectangle(cornerRadius: 12)
                    .stroke(
                        isSelected ?
                        Color.appPrimary :
                        Color.gray.opacity(0.2),
                        lineWidth: isSelected ? 2 : 1
                    )
            )
        }
        .buttonStyle(.plain)
        .animation(.spring(response: 0.3, dampingFraction: 0.7), value: isSelected)
    }
}

/// Selected time chip (removable)
struct SelectedTimeChip: View {
    let combo: DaySlotCombo
    let onRemove: () -> Void
    
    var body: some View {
        HStack(spacing: 6) {
            Text("\(combo.timeSlot.icon) \(combo.dayOfWeek.rawValue) \(combo.timeSlot.rawValue)")
                .font(.system(size: 14, weight: .semibold, design: .rounded))
                .foregroundColor(.white)
            
            Button(action: onRemove) {
                Image(systemName: "xmark.circle.fill")
                    .font(.system(size: 16))
                    .foregroundColor(.white.opacity(0.8))
            }
        }
        .padding(.horizontal, 12)
        .padding(.vertical, 8)
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
        .cornerRadius(20)
    }
}

#Preview {
    ProfileSetupFlowView()
}
