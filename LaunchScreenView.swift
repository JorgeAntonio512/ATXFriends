//
//  LaunchScreenView.swift
//  Avenue3
//
//  Created by George Anthony Pazdral II on 5/6/26.
//

import SwiftUI

/// Launch screen with animated Avenue3 branding
struct LaunchScreenView: View {
    @State private var animatePaths = false
    @State private var animateName = false
    @State private var animateTagline = false
    
    var body: some View {
        ZStack {
            // Warm gradient background - cream to beige only
            LinearGradient(
                colors: [
                    Color.white, // Warm cream
                    Color.white  // Soft beige
                ],
                startPoint: .topLeading,
                endPoint: .bottomTrailing
            )
            .ignoresSafeArea()
            
            VStack(spacing: 40) {
                Spacer()
                
                // Converging paths graphic
                ThreePathsLogo(animate: animatePaths)
                    .frame(width: 120, height: 120)
                    .opacity(animatePaths ? 1 : 0)
                    .scaleEffect(animatePaths ? 1 : 0.5)
                
                // App name
                VStack(spacing: 8) {
                    Text("ATX Friends")
                        .font(.system(size: 48, weight: .bold, design: .rounded))
                        .foregroundStyle(
                            LinearGradient(
                                colors: [
                                    Color.appNavy, // Deep forest green
                                    Color.appPrimary  // Sage green
                                ],
                                startPoint: .leading,
                                endPoint: .trailing
                            )
                        )
                        .opacity(animateName ? 1 : 0)
                        .offset(y: animateName ? 0 : 20)
                    
                    Text("Find Your People")
                        .font(.system(size: 18, weight: .medium, design: .rounded))
                        .foregroundColor(Color(red: 0.50, green: 0.50, blue: 0.50))
                        .opacity(animateTagline ? 1 : 0)
                        .offset(y: animateTagline ? 0 : 20)
                }
                
                Spacer()
            }
        }
        .onAppear {
            // Staggered animations
            withAnimation(.spring(response: 0.8, dampingFraction: 0.7).delay(0.2)) {
                animatePaths = true
            }
            withAnimation(.spring(response: 0.6, dampingFraction: 0.8).delay(0.5)) {
                animateName = true
            }
            withAnimation(.spring(response: 0.6, dampingFraction: 0.8).delay(0.7)) {
                animateTagline = true
            }
        }
    }
}

/// Three converging paths forming a "meeting point" graphic
struct ThreePathsLogo: View {
    let animate: Bool
    
    var body: some View {
        ZStack {
            // Background circle
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
            
            // Three paths converging to center
            Canvas { context, size in
                let center = CGPoint(x: size.width / 2, y: size.height / 2)
                let radius = min(size.width, size.height) / 2
                
                // Path 1: From top
                drawPath(
                    context: context,
                    from: CGPoint(x: center.x, y: 0),
                    to: center,
                    controlPoint: CGPoint(x: center.x - 10, y: radius * 0.3),
                    animate: animate,
                    color: Color.appPrimary
                )
                
                // Path 2: From bottom-left
                drawPath(
                    context: context,
                    from: CGPoint(x: radius * 0.3, y: size.height),
                    to: center,
                    controlPoint: CGPoint(x: radius * 0.4, y: center.y + 20),
                    animate: animate,
                    color: Color.appPrimary
                )
                
                // Path 3: From bottom-right
                drawPath(
                    context: context,
                    from: CGPoint(x: size.width - radius * 0.3, y: size.height),
                    to: center,
                    controlPoint: CGPoint(x: size.width - radius * 0.4, y: center.y + 20),
                    animate: animate,
                    color: Color.appNavy
                )
            }
            .padding(12)
            
            // Center point (meeting point)
            Circle()
                .fill(
                    LinearGradient(
                        colors: [
                            Color.appPrimary,
                            Color.appPrimary
                        ],
                        startPoint: .topLeading,
                        endPoint: .bottomTrailing
                    )
                )
                .frame(width: 16, height: 16)
                .scaleEffect(animate ? 1 : 0.3)
                .shadow(
                    color: Color.appPrimary.opacity(0.4),
                    radius: 8,
                    x: 0,
                    y: 2
                )
        }
    }
    
    private func drawPath(
        context: GraphicsContext,
        from start: CGPoint,
        to end: CGPoint,
        controlPoint: CGPoint,
        animate: Bool,
        color: Color
    ) {
        var path = Path()
        path.move(to: start)
        path.addQuadCurve(to: end, control: controlPoint)
        
        context.stroke(
            path,
            with: .color(color),
            style: StrokeStyle(
                lineWidth: 4,
                lineCap: .round,
                lineJoin: .round
            )
        )
        
        // Animated dot traveling along path
        if animate {
            let dotPosition = end // End position (center)
            context.fill(
                Path(ellipseIn: CGRect(
                    x: dotPosition.x - 3,
                    y: dotPosition.y - 3,
                    width: 6,
                    height: 6
                )),
                with: .color(color)
            )
        }
    }
}

#Preview("Launch Screen") {
    LaunchScreenView()
}

#Preview("Three Paths Logo") {
    ZStack {
        Color.white
            .ignoresSafeArea()
        
        ThreePathsLogo(animate: true)
            .frame(width: 120, height: 120)
    }
}
