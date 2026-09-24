//
//  AvatarRing.swift
//  Avenue3
//

import SwiftUI

/// A person's profile photo in a soft accent-colored ring, with an initials
/// fallback when there's no photo (or it fails to load). Shared by the
/// Messages list row and the message thread screen so both render the
/// same avatar treatment.
struct AvatarRing: View {
    /// `.needsAttention` is a bolder, fully-opaque ring used to flag unread
    /// activity in a list row. `.soft` is the calmer default for contexts
    /// (like a thread's own header) that aren't flagging anything.
    enum RingStyle {
        case soft
        case needsAttention
    }

    let photoURL: String?
    let displayName: String
    var size: CGFloat = 64
    var ringStyle: RingStyle = .soft

    private var initials: String {
        String(displayName.prefix(1)).uppercased()
    }

    private var ringColor: Color {
        ringStyle == .needsAttention ? Color.appPrimary : Color.appPrimary.opacity(0.35)
    }

    private var ringLineWidth: CGFloat {
        size * (ringStyle == .needsAttention ? 3.0 / 64.0 : 2.0 / 64.0)
    }

    private var photoDiameter: CGFloat {
        size * (56.0 / 64.0)
    }

    var body: some View {
        ZStack {
            Circle()
                .stroke(ringColor, lineWidth: ringLineWidth)
                .frame(width: size, height: size)

            photo
                .frame(width: photoDiameter, height: photoDiameter)
                .clipShape(Circle())
        }
        .frame(width: size, height: size)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("\(displayName), profile photo")
    }

    @ViewBuilder
    private var photo: some View {
        if let photoURL, let url = URL(string: photoURL) {
            AsyncImage(url: url) { phase in
                switch phase {
                case .success(let image):
                    image.resizable().scaledToFill()
                case .failure:
                    initialsFallback
                default:
                    Circle().fill(Color.appBorder.opacity(0.3))
                }
            }
        } else {
            initialsFallback
        }
    }

    private var initialsFallback: some View {
        Circle()
            .fill(Color.appPrimary.opacity(0.7))
            .overlay {
                Text(initials)
                    .font(.system(size: size * (20.0 / 56.0), weight: .semibold, design: .rounded))
                    .foregroundColor(.white)
            }
    }
}
