//
//  SimpaticoView.swift
//  Avenue3
//

import SwiftUI

// MARK: - Root

struct SimpaticoView: View {
    @State private var viewModel      = SimpaticoViewModel()
    @State private var introDismissed = false   // flips to true when user taps Start

    var body: some View {
        NavigationStack {
            ZStack {
                appGradient.ignoresSafeArea()

                if viewModel.isLoading {
                    ProgressView()
                        .tint(Color.appPrimary)
                        .scaleEffect(1.2)
                } else if viewModel.questionnaire.isComplete {
                    SimpaticoCompleteView()
                } else if viewModel.questionnaire.answers.isEmpty && !introDismissed {
                    // Show intro only when the user has zero saved answers.
                    // Once they've answered even one question the flow resumes directly.
                    SimpaticoIntroView { introDismissed = true }
                } else {
                    SimpaticoQuestionFlowView(viewModel: viewModel)
                }
            }
            .navigationTitle("Simpatico")
            .navigationBarTitleDisplayMode(.large)
        }
        .task { await viewModel.load() }
    }

    private var appGradient: LinearGradient {
        LinearGradient(
            colors: [
                Color.white,
                Color.white
            ],
            startPoint: .topLeading,
            endPoint: .bottomTrailing
        )
    }
}

// MARK: - Intro screen

struct SimpaticoIntroView: View {
    let onStart: () -> Void

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 28) {
                // Icon
                HStack {
                    Spacer()
                    ZStack {
                        Circle()
                            .fill(Color.appPrimary.opacity(0.20))
                            .frame(width: 100, height: 100)
                        Image(systemName: "face.smiling.fill")
                            .font(.system(size: 50))
                            .foregroundColor(Color.appPrimary)
                    }
                    Spacer()
                }

                // Headline + summary
                VStack(alignment: .leading, spacing: 10) {
                    Text("Before you begin")
                        .font(.system(size: 26, weight: .bold, design: .rounded))
                        .foregroundColor(Color.appNavy)

                    Text("Simpatico is an 18-question self-assessment about what matters to you in a friendship. Once you and a connected friend have both finished, a compatibility score shows up next to their name in Messages.")
                        .font(.system(size: 15, weight: .regular, design: .rounded))
                        .foregroundColor(Color(red: 0.45, green: 0.45, blue: 0.45))
                        .lineSpacing(4)
                        .fixedSize(horizontal: false, vertical: true)
                }

                // Key facts card
                VStack(alignment: .leading, spacing: 16) {
                    IntroFactRow(
                        icon: "checkmark.seal.fill",
                        text: "All 18 questions need to be answered before any score is calculated. Partial answers don't count."
                    )
                    Divider().padding(.leading, 38)
                    IntroFactRow(
                        icon: "clock.fill",
                        text: "It takes roughly 30 minutes — no need to rush."
                    )
                    Divider().padding(.leading, 38)
                    IntroFactRow(
                        icon: "icloud.and.arrow.up.fill",
                        text: "Your progress saves after every answer. Leave anytime and pick up right where you left off."
                    )
                }
                .padding(20)
                .background(Color.white.opacity(0.75))
                .cornerRadius(18)
                .shadow(color: Color.black.opacity(0.06), radius: 12, x: 0, y: 4)
            }
            .padding(.horizontal, 24)
            .padding(.top, 12)
            .padding(.bottom, 32)
        }
        .safeAreaInset(edge: .bottom) {
            VStack(spacing: 0) {
                Button(action: onStart) {
                    HStack(spacing: 8) {
                        Text("Start")
                            .font(.system(size: 17, weight: .semibold, design: .rounded))
                        Image(systemName: "arrow.right")
                            .font(.system(size: 15, weight: .semibold))
                    }
                    .foregroundColor(.white)
                    .frame(maxWidth: .infinity)
                    .frame(height: 52)
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
                    .cornerRadius(14)
                    .shadow(color: Color.appPrimary.opacity(0.30), radius: 8, x: 0, y: 4)
                }
                .buttonStyle(.plain)
                .padding(.horizontal, 24)
                .padding(.vertical, 16)

                // NavigationStack does not reliably propagate an externally-applied
                // safeAreaInset across its boundary, so reserve the tab bar height
                // locally here rather than relying on MainTabView's outer inset.
                Color.clear.frame(height: MainTabView.tabBarHeight)
            }
        }
    }
}

private struct IntroFactRow: View {
    let icon: String
    let text: String

    var body: some View {
        HStack(alignment: .top, spacing: 14) {
            Image(systemName: icon)
                .font(.system(size: 16))
                .foregroundColor(Color.appPrimary)
                .frame(width: 24)
                .padding(.top, 1)

            Text(text)
                .font(.system(size: 14, weight: .regular, design: .rounded))
                .foregroundColor(Color(red: 0.40, green: 0.40, blue: 0.40))
                .lineSpacing(3)
                .fixedSize(horizontal: false, vertical: true)
        }
    }
}

// MARK: - Question flow

struct SimpaticoQuestionFlowView: View {
    @Bindable var viewModel: SimpaticoViewModel

    var body: some View {
        // Nav buttons are a safeAreaInset on the ScrollView so they always sit
        // above the tab bar and the scroll content never disappears behind them.
        ScrollView {
            VStack(spacing: 0) {
                SimpaticoProgressHeader(viewModel: viewModel)
                    .padding(.horizontal, 20)
                    .padding(.top, 8)
                    .padding(.bottom, 12)

                SimpaticoQuestionCard(viewModel: viewModel)
                    .padding(.horizontal, 20)
                    .padding(.top, 4)
                    .padding(.bottom, 24)
                    .id(viewModel.currentIndex)
                    .transition(.opacity)
            }
        }
        .animation(.easeInOut(duration: 0.2), value: viewModel.currentIndex)
        .safeAreaInset(edge: .bottom) {
            VStack(spacing: 0) {
                if let error = viewModel.errorMessage {
                    Text(error)
                        .font(.system(size: 13, weight: .medium, design: .rounded))
                        .foregroundColor(Color(red: 0.85, green: 0.45, blue: 0.40))
                        .multilineTextAlignment(.center)
                        .padding(.horizontal, 20)
                        .padding(.top, 8)
                }

                SimpaticoNavButtons(viewModel: viewModel)
                    .padding(.horizontal, 20)
                    .padding(.top, 12)
                    .padding(.bottom, 20)

                // NavigationStack does not reliably propagate an externally-applied
                // safeAreaInset across its boundary, so reserve the tab bar height
                // locally here rather than relying on MainTabView's outer inset.
                Color.clear.frame(height: MainTabView.tabBarHeight)
            }
        }
    }
}

// MARK: - Progress header

struct SimpaticoProgressHeader: View {
    let viewModel: SimpaticoViewModel

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack {
                Text("Question \(viewModel.currentIndex + 1) of \(viewModel.totalCount)")
                    .font(.system(size: 15, weight: .semibold, design: .rounded))
                    .foregroundColor(Color(red: 0.40, green: 0.40, blue: 0.40))

                Spacer()

                Text(viewModel.currentComponent.category.displayName)
                    .font(.system(size: 12, weight: .medium, design: .rounded))
                    .foregroundColor(Color.appPrimary)
                    .padding(.horizontal, 10)
                    .padding(.vertical, 4)
                    .background(Color.appPrimary.opacity(0.12))
                    .clipShape(Capsule())
            }

            GeometryReader { geo in
                ZStack(alignment: .leading) {
                    Capsule()
                        .fill(Color(red: 0.80, green: 0.80, blue: 0.75).opacity(0.35))
                    Capsule()
                        .fill(Color.appPrimary)
                        .frame(
                            width: geo.size.width
                                * Double(viewModel.currentIndex + 1)
                                / Double(viewModel.totalCount)
                        )
                        .animation(.easeInOut(duration: 0.3), value: viewModel.currentIndex)
                }
                .frame(height: 5)
            }
            .frame(height: 5)
        }
    }
}

// MARK: - Question card

struct SimpaticoQuestionCard: View {
    @Bindable var viewModel: SimpaticoViewModel

    private var component: SimpaticoComponent { viewModel.currentComponent }

    var body: some View {
        VStack(alignment: .leading, spacing: 20) {
            VStack(alignment: .leading, spacing: 8) {
                Text(component.displayName)
                    .font(.system(size: 24, weight: .bold, design: .rounded))
                    .foregroundColor(Color(red: 0.25, green: 0.25, blue: 0.25))

                Text(component.componentDescription)
                    .font(.system(size: 15, weight: .regular, design: .rounded))
                    .foregroundColor(Color(red: 0.50, green: 0.50, blue: 0.50))
                    .lineSpacing(3)
                    .fixedSize(horizontal: false, vertical: true)
            }

            Divider()

            RatingPicker(
                question: "To you, is this important?",
                selection: $viewModel.selectedToYou
            )

            RatingPicker(
                question: "To a friend, is this important?",
                selection: $viewModel.selectedToFriend
            )
        }
        .padding(20)
        .background(
            RoundedRectangle(cornerRadius: 20)
                .fill(Color.white.opacity(0.85))
        )
        .shadow(color: Color.black.opacity(0.07), radius: 16, x: 0, y: 4)
    }
}

// MARK: - Rating picker

struct RatingPicker: View {
    let question: String
    @Binding var selection: SimpaticoRating?

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text(question)
                .font(.system(size: 15, weight: .semibold, design: .rounded))
                .foregroundColor(Color(red: 0.35, green: 0.35, blue: 0.35))

            HStack(spacing: 8) {
                ForEach(SimpaticoRating.allCases, id: \.self) { rating in
                    RatingOptionButton(rating: rating, isSelected: selection == rating) {
                        withAnimation(.easeInOut(duration: 0.15)) {
                            selection = rating
                        }
                    }
                }
            }
        }
    }
}

struct RatingOptionButton: View {
    let rating: SimpaticoRating
    let isSelected: Bool
    let onTap: () -> Void

    var body: some View {
        Button(action: onTap) {
            Text(rating.shortName)
                .font(.system(size: 13, weight: isSelected ? .semibold : .medium, design: .rounded))
                .foregroundColor(isSelected ? .white : Color.appPrimary)
                .frame(maxWidth: .infinity)
                .padding(.vertical, 11)
                .background(
                    RoundedRectangle(cornerRadius: 10)
                        .fill(
                            isSelected
                                ? Color.appPrimary
                                : Color.appPrimary.opacity(0.10)
                        )
                )
        }
        .buttonStyle(.plain)
    }
}

// MARK: - Navigation buttons

struct SimpaticoNavButtons: View {
    @Bindable var viewModel: SimpaticoViewModel

    var body: some View {
        HStack(spacing: 12) {
            if viewModel.canGoBack {
                Button {
                    viewModel.goBack()
                } label: {
                    HStack(spacing: 6) {
                        Image(systemName: "chevron.left")
                            .font(.system(size: 14, weight: .semibold))
                        Text("Back")
                            .font(.system(size: 16, weight: .semibold, design: .rounded))
                    }
                    .foregroundColor(Color.appPrimary)
                    .frame(maxWidth: .infinity)
                    .frame(height: 52)
                    .background(Color.white.opacity(0.8))
                    .cornerRadius(14)
                }
                .buttonStyle(.plain)
            }

            Button {
                Task { await viewModel.saveAndAdvance() }
            } label: {
                ZStack {
                    if viewModel.isSaving {
                        ProgressView()
                            .tint(.white)
                            .scaleEffect(0.85)
                    } else {
                        HStack(spacing: 6) {
                            Text(viewModel.isLastQuestion ? "Finish" : "Next")
                                .font(.system(size: 16, weight: .semibold, design: .rounded))
                            Image(systemName: viewModel.isLastQuestion ? "checkmark" : "chevron.right")
                                .font(.system(size: 14, weight: .semibold))
                        }
                    }
                }
                .foregroundColor(viewModel.canAdvance ? .white : Color(red: 0.70, green: 0.70, blue: 0.70))
                .frame(maxWidth: .infinity)
                .frame(height: 52)
                .background(
                    RoundedRectangle(cornerRadius: 14)
                        .fill(
                            viewModel.canAdvance
                                ? Color.appPrimary
                                : Color(red: 0.85, green: 0.85, blue: 0.83)
                        )
                )
                .shadow(
                    color: viewModel.canAdvance
                        ? Color.appPrimary.opacity(0.30)
                        : .clear,
                    radius: 8, x: 0, y: 4
                )
            }
            .buttonStyle(.plain)
            .disabled(!viewModel.canAdvance || viewModel.isSaving)
        }
    }
}

// MARK: - Completion screen

struct SimpaticoCompleteView: View {
    var body: some View {
        ScrollView {
            VStack(spacing: 32) {
                ZStack {
                    Circle()
                        .fill(Color.appPrimary.opacity(0.20))
                        .frame(width: 130, height: 130)
                    Image(systemName: "face.smiling.fill")
                        .font(.system(size: 64))
                        .foregroundColor(Color.appPrimary)
                }

                VStack(spacing: 10) {
                    Text("All done!")
                        .font(.system(size: 30, weight: .bold, design: .rounded))
                        .foregroundColor(Color.appNavy)

                    Text("Your Simpatico answers are saved.")
                        .font(.system(size: 16, weight: .medium, design: .rounded))
                        .foregroundColor(Color(red: 0.40, green: 0.40, blue: 0.40))
                }

                HStack(alignment: .top, spacing: 14) {
                    Image(systemName: "sparkles")
                        .font(.system(size: 18))
                        .foregroundColor(Color.appPrimary)
                        .padding(.top, 1)

                    Text("Once a friend finishes their questionnaire too, your Simpatico score appears next to their name in Messages.")
                        .font(.system(size: 15, weight: .regular, design: .rounded))
                        .foregroundColor(Color(red: 0.45, green: 0.45, blue: 0.45))
                        .lineSpacing(3)
                        .fixedSize(horizontal: false, vertical: true)
                }
                .padding(20)
                .background(Color.white.opacity(0.70))
                .cornerRadius(16)
            }
            .padding(.horizontal, 24)
            .padding(.top, 64)
            // Extra tab bar height prevents the bottom of the scroll content from
            // being clipped — same NavigationStack boundary issue as the intro screen.
            .padding(.bottom, 40 + MainTabView.tabBarHeight)
        }
    }
}
