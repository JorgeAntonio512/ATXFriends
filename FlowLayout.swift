//
//  FlowLayout.swift
//  Avenue3
//
//  Created by George Anthony Pazdral II on 6/3/26.
//

import SwiftUI

/// A layout that arranges views in a flow, wrapping to new lines as needed
/// Similar to CSS flexbox with flex-wrap
struct FlowLayout: Layout {
    var spacing: CGFloat = 8
    
    func sizeThatFits(proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) -> CGSize {
        let result = FlowResult(
            in: proposal.replacingUnspecifiedDimensions().width,
            subviews: subviews,
            spacing: spacing
        )
        return result.size
    }
    
    func placeSubviews(in bounds: CGRect, proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) {
        let result = FlowResult(
            in: bounds.width,
            subviews: subviews,
            spacing: spacing
        )
        for (index, subview) in subviews.enumerated() {
            subview.place(at: CGPoint(x: bounds.minX + result.positions[index].x, y: bounds.minY + result.positions[index].y), proposal: .unspecified)
        }
    }
    
    struct FlowResult {
        var size: CGSize = .zero
        var positions: [CGPoint] = []
        
        init(in maxWidth: CGFloat, subviews: Subviews, spacing: CGFloat) {
            var x: CGFloat = 0
            var y: CGFloat = 0
            var lineHeight: CGFloat = 0
            
            for subview in subviews {
                let size = subview.sizeThatFits(.unspecified)
                
                if x + size.width > maxWidth && x > 0 {
                    // Move to next line
                    x = 0
                    y += lineHeight + spacing
                    lineHeight = 0
                }
                
                positions.append(CGPoint(x: x, y: y))
                lineHeight = max(lineHeight, size.height)
                x += size.width + spacing
            }
            
            // Calculate total size
            let finalWidth = maxWidth
            let finalHeight = y + lineHeight
            self.size = CGSize(width: finalWidth, height: finalHeight)
        }
    }
}

#Preview {
    FlowLayout(spacing: 8) {
        ForEach(["Hiking", "Board Games", "Coffee", "Running", "Movies", "Concerts"], id: \.self) { item in
            Text(item)
                .font(.system(size: 12, weight: .medium, design: .rounded))
                .foregroundColor(Color.appPrimary)
                .padding(.horizontal, 10)
                .padding(.vertical, 6)
                .background(Color.appPrimary.opacity(0.15))
                .cornerRadius(12)
        }
    }
    .padding()
    .background(
        LinearGradient(
            colors: [
                Color.white,
                Color.white
            ],
            startPoint: .topLeading,
            endPoint: .bottomTrailing
        )
    )
}
