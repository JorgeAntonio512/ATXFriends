//
//  GhostSlotCardView.swift
//  Avenue3
//

import SwiftUI

/// The one shared "open slot" ghost card, used by both Today and Upcoming: bold title line,
/// orange "Activity?", filled pill button. Today and Upcoming differ in the title text, the
/// button's icon/label, and the card style (solid on Today, dashed on Upcoming) — all passed
/// in, never hard-coded here.
struct GhostSlotCardView: View {
    /// Today's cards read as "happening now" (solid, filled); Upcoming's read as "not posted
    /// yet" (dashed outline) — same visual language it always used.
    enum Style {
        case solid
        case dashed
    }

    let titleLine: String
    let style: Style
    let activityName: String
    let buttonIcon: String
    let buttonLabel: String
    let accessibilityLabel: String
    let onTap: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text(titleLine)
                .font(.system(size: 16, weight: .bold, design: .rounded))
                .foregroundColor(Color.appPrimaryText)

            Text("\(activityName)?")
                .font(.system(size: 14, weight: .semibold, design: .rounded))
                .foregroundColor(Color.appPrimary)

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
        .background(backgroundFill)
        .cornerRadius(16)
        .overlay(borderOverlay)
        .shadow(
            color: style == .solid ? .black.opacity(0.05) : .clear,
            radius: 8, x: 0, y: 4
        )
        .accessibilityElement(children: .combine)
        .accessibilityLabel(accessibilityLabel)
        .accessibilityAddTraits(.isButton)
    }

    private var backgroundFill: Color {
        switch style {
        case .solid: Color.appCardBackground.opacity(0.85)
        case .dashed: Color.appCardBackground.opacity(0.35)
        }
    }

    @ViewBuilder
    private var borderOverlay: some View {
        switch style {
        case .solid:
            EmptyView()
        case .dashed:
            RoundedRectangle(cornerRadius: 16)
                .strokeBorder(style: StrokeStyle(lineWidth: 1.5, dash: [7, 5]))
                .foregroundColor(Color.appPrimary.opacity(0.45))
        }
    }
}
