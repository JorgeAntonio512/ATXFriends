//
//  WaitlistView.swift
//  Avenue3
//
//  Shown when a user fails the Austin location gate (out of range, or permission
//  denied). Collects an email and writes { email, submittedAt } to the
//  waitlistSignups Firestore collection — no Auth account is created here.
//

import SwiftUI
import FirebaseFirestore

struct WaitlistView: View {
    let onDismissAll: () -> Void

    @State private var email = ""
    @State private var isSubmitting = false
    @State private var didSubmit = false
    @State private var submitError: String?
    @FocusState private var isEmailFocused: Bool

    private var isValidEmail: Bool {
        let pattern = "[A-Z0-9a-z._%+\\-]+@[A-Za-z0-9.\\-]+\\.[A-Za-z]{2,64}"
        return NSPredicate(format: "SELF MATCHES %@", pattern).evaluate(with: email)
    }

    var body: some View {
        ZStack {
            Color.white.ignoresSafeArea()

            VStack(spacing: 0) {
                ScrollView {
                    VStack(spacing: 28) {
                        Spacer().frame(height: 32)

                        ZStack {
                            Circle()
                                .fill(Color.appPrimary.opacity(0.15))
                                .frame(width: 160, height: 160)

                            Circle()
                                .fill(Color.appPrimary)
                                .frame(width: 110, height: 110)
                                .shadow(color: Color.appPrimary.opacity(0.3), radius: 20, x: 0, y: 10)

                            Image(systemName: "mappin.circle.fill")
                                .font(.system(size: 48))
                                .foregroundColor(.white)
                        }

                        VStack(spacing: 12) {
                            Text("ATX Friends is Austin-only for now")
                                .font(.system(size: 26, weight: .bold, design: .rounded))
                                .foregroundColor(Color.appNavy)
                                .multilineTextAlignment(.center)

                            Text("We're starting in Austin, TX. Leave your email and we'll notify you when we expand to your area.")
                                .font(.system(size: 17, weight: .regular, design: .rounded))
                                .foregroundColor(Color(red: 0.50, green: 0.50, blue: 0.50))
                                .multilineTextAlignment(.center)
                                .lineSpacing(4)
                        }
                        .padding(.horizontal, 40)

                        if didSubmit {
                            VStack(spacing: 12) {
                                Image(systemName: "checkmark.circle.fill")
                                    .font(.system(size: 48))
                                    .foregroundColor(Color.appPrimary)

                                Text("You're on the list!")
                                    .font(.system(size: 20, weight: .semibold, design: .rounded))
                                    .foregroundColor(Color.appNavy)

                                Text("We'll email you when ATX Friends launches in your area.")
                                    .font(.system(size: 15, weight: .regular, design: .rounded))
                                    .foregroundColor(Color(red: 0.50, green: 0.50, blue: 0.50))
                                    .multilineTextAlignment(.center)
                                    .lineSpacing(3)
                            }
                            .padding(.horizontal, 40)
                        } else {
                            VStack(alignment: .leading, spacing: 8) {
                                Text("Email")
                                    .font(.system(size: 14, weight: .semibold, design: .rounded))
                                    .foregroundColor(Color(red: 0.40, green: 0.40, blue: 0.40))

                                HStack(spacing: 12) {
                                    Image(systemName: "envelope.fill")
                                        .font(.system(size: 18))
                                        .foregroundColor(Color.appPrimary)
                                        .frame(width: 24)

                                    TextField("you@example.com", text: $email)
                                        .font(.system(size: 16, weight: .regular, design: .rounded))
                                        .textContentType(.emailAddress)
                                        .textInputAutocapitalization(.never)
                                        .keyboardType(.emailAddress)
                                        .autocorrectionDisabled(true)
                                        .focused($isEmailFocused)
                                }
                                .padding()
                                .background(Color.white)
                                .cornerRadius(12)
                                .overlay(
                                    RoundedRectangle(cornerRadius: 12)
                                        .stroke(
                                            isEmailFocused ? Color.appPrimary : Color.gray.opacity(0.2),
                                            lineWidth: isEmailFocused ? 2 : 1
                                        )
                                )

                                if let err = submitError {
                                    Text(err)
                                        .font(.system(size: 13, weight: .medium, design: .rounded))
                                        .foregroundColor(.red.opacity(0.8))
                                        .padding(.leading, 4)
                                }
                            }
                            .padding(.horizontal, 32)
                        }

                        Spacer().frame(height: 20)
                    }
                }
                .scrollIndicators(.hidden)
                .scrollBounceBehavior(.basedOnSize)
                .scrollDismissesKeyboard(.interactively)

                VStack(spacing: 12) {
                    if !didSubmit {
                        Button {
                            Task { await submit() }
                        } label: {
                            HStack {
                                if isSubmitting {
                                    ProgressView().tint(.white)
                                } else {
                                    Text("Notify me when you expand")
                                        .font(.system(size: 18, weight: .semibold, design: .rounded))
                                }
                            }
                            .foregroundColor(.white)
                            .frame(maxWidth: .infinity)
                            .frame(height: 56)
                            .background(
                                LinearGradient(
                                    colors: [Color.appNavy, Color.appNavy],
                                    startPoint: .leading,
                                    endPoint: .trailing
                                )
                            )
                            .cornerRadius(16)
                            .shadow(color: Color.appNavy.opacity(0.3), radius: 12, x: 0, y: 6)
                            .opacity(isValidEmail ? 1.0 : 0.5)
                        }
                        .disabled(!isValidEmail || isSubmitting)
                    }

                    Button {
                        onDismissAll()
                    } label: {
                        Text(didSubmit ? "Done" : "Not now")
                            .font(.system(size: 16, weight: .medium, design: .rounded))
                            .foregroundColor(Color.appPrimary)
                    }
                }
                .padding(.horizontal, 32)
                .padding(.vertical, 20)
                .background(
                    LinearGradient(
                        colors: [Color.white.opacity(0.95), Color.white.opacity(0.95)],
                        startPoint: .top,
                        endPoint: .bottom
                    )
                    .shadow(color: .black.opacity(0.1), radius: 10, x: 0, y: -5)
                )
            }
        }
        .navigationBarBackButtonHidden(true)
    }

    private func submit() async {
        guard isValidEmail else { return }
        isSubmitting = true
        submitError = nil
        defer { isSubmitting = false }

        do {
            let db = Firestore.firestore()
            let docRef = db.collection("waitlistSignups").document()
            try await docRef.setData([
                "email": email.lowercased().trimmingCharacters(in: .whitespaces),
                "submittedAt": FieldValue.serverTimestamp()
            ])
            didSubmit = true
            // Trigger cleanup after showing the success message briefly.
            // This ensures any pending Firebase Auth account is deleted even if the
            // user closes the app without tapping "Done".
            Task {
                try? await Task.sleep(nanoseconds: 2_000_000_000) // 2 seconds
                onDismissAll()
            }
        } catch {
            submitError = "Failed to sign up — please try again."
        }
    }
}

#Preview {
    NavigationStack {
        WaitlistView(onDismissAll: {})
    }
}
