//
//  GhostSlotCardView.swift
//  Avenue3
//

import SwiftUI

/// The one shared "open slot" ghost card, used by both Today and Upcoming: dashed border,
/// bold title line, orange "Activity?" + grey "· Your plan here.", filled orange pill button.
/// Today and Upcoming only differ in the title text and the button's icon/label — both are
/// passed in, never hard-coded here.
struct GhostSlotCardView: View {
    let titleLine: String
    let activityName: String
    let buttonIcon: String
    let buttonLabel: String
    let accessibilityLabel: String
    let onTap: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text(titleLine)
                .font(.system(size: 16, weight: .bold, design: .rounded))
                .foregroundColor(Color.appNavy)

            HStack(spacing: 6) {
                Text("\(activityName)?")
                    .font(.system(size: 14, weight: .semibold, design: .rounded))
                    .foregroundColor(Color.appPrimary)
                Text("·")
                    .foregroundColor(Color(red: 0.70, green: 0.70, blue: 0.70))
                Text("Your plan here.")
                    .font(.system(size: 13, weight: .regular, design: .rounded))
                    .foregroundColor(Color(red: 0.55, green: 0.55, blue: 0.55))
                    .italic()
            }

            HStack {
                Spacer()
                Button(action: onTap) {
                    HStack(spacing: 6) {
                        Image(systemName: buttonIcon)
                            .font(.system(size: 13))
                        Text(buttonLabel)
                            .font(.system(size: 15, weight: .semibold, design: .rounded))
                    }
                    .foregroundColor(.white)
                    .padding(.horizontal, 20)
                    .padding(.vertical, 9)
                    .background(Color.appPrimary)
                    .cornerRadius(12)
                }
                .buttonStyle(.plain)
            }
        }
        .padding(16)
        .background(Color.white.opacity(0.35))
        .cornerRadius(16)
        .overlay(
            RoundedRectangle(cornerRadius: 16)
                .strokeBorder(style: StrokeStyle(lineWidth: 1.5, dash: [7, 5]))
                .foregroundColor(Color.appPrimary.opacity(0.45))
        )
        .accessibilityElement(children: .combine)
        .accessibilityLabel(accessibilityLabel)
        .accessibilityAddTraits(.isButton)
    }
}
