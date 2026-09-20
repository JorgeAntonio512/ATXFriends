# Sign in with Apple Implementation

## Overview
Added Sign in with Apple authentication to the Avenue3 app following Apple's Human Interface Guidelines and security best practices.

## Files Modified

### 1. FirebaseAuthService.swift
**Added:**
- Import statements for `AuthenticationServices` and `CryptoKit`
- `currentNonce` property to store the unhashed nonce for Apple sign-in
- `prepareAppleSignIn()` method to generate and return a hashed nonce
- `signInWithApple(credential:)` method to handle Apple credential authentication with Firebase
- Helper methods:
  - `randomNonceString()` - Generates a cryptographically secure random nonce
  - `sha256()` - Hashes the nonce using SHA-256

**Security Implementation:**
- Uses secure random nonce generation with `SecRandomCopyBytes`
- Hashes nonce with SHA-256 before sending to Apple
- Validates nonce on return from Apple
- Clears nonce after use

### 2. AuthViewModel.swift
**Added:**
- Import statement for `AuthenticationServices`
- `prepareAppleSignIn()` method to prepare the nonce for Apple sign-in
- `handleAppleSignIn(_:)` method to process Apple authorization
  - Extracts Apple ID credential
  - Signs in with Firebase using the credential
  - Creates Firestore user document for new users
  - Extracts display name from Apple (if provided)
  - Updates auth state and notifies observers

### 3. SignInWithAppleButton.swift (NEW)
**Created a reusable SwiftUI component:**
- `SignInWithAppleButton` - Main SwiftUI view
- `SignInWithAppleButtonRepresentable` - UIViewRepresentable wrapper for `ASAuthorizationAppleIDButton`
- `Coordinator` - Handles Apple sign-in delegation
  - Implements `ASAuthorizationControllerDelegate`
  - Implements `ASAuthorizationControllerPresentationContextProviding`
  - Requests full name and email scopes
  - Provides callbacks for request and completion

**Features:**
- Uses Apple's native `ASAuthorizationAppleIDButton` with black style
- 56pt height to match other buttons in the app
- 16pt corner radius matching app design
- Properly handles authorization flow and errors

### 4. OnboardingView.swift
**Added:**
- Import statement for `AuthenticationServices`
- `@State private var viewModel = AuthViewModel()` instance
- Sign in with Apple button at the top of the action buttons section
- "or" divider between Apple sign-in and email sign-up
- Changed "Get Started" button text to "Sign Up with Email" for clarity

**UI Layout:**
1. Sign in with Apple button (primary action)
2. "or" divider
3. Sign Up with Email button
4. "I Already Have an Account" text button

### 5. SignInView.swift
**Added:**
- Import statement for `AuthenticationServices`
- Sign in with Apple button below the email sign-in button
- "or" divider between email sign-in and Apple sign-in
- Proper dismiss() handling on successful Apple sign-in

**UI Layout:**
1. Email and password fields
2. Forgot password link
3. Sign In button (email/password)
4. "or" divider
5. Sign in with Apple button

## User Flow

### New User with Apple Sign In
1. User taps "Sign in with Apple" button
2. App generates secure nonce and requests authorization from Apple
3. User authenticates with Face ID/Touch ID
4. Apple returns authorization with optional name and email
5. App signs in with Firebase using Apple credential
6. App creates Firestore user document with:
   - Firebase UID
   - Display name (from Apple if provided)
   - Empty profile (isProfileComplete = false)
7. App routes user to ProfileSetupFlowView to complete their profile

### Returning User with Apple Sign In
1. User taps "Sign in with Apple" button
2. App generates secure nonce and requests authorization from Apple
3. User authenticates with Face ID/Touch ID
4. Apple returns authorization
5. App signs in with Firebase using Apple credential
6. App detects existing user (isNewUser = false)
7. App routes user to main app (RootView handles routing)

## Security Considerations

1. **Nonce Security:**
   - Cryptographically secure random nonce generation
   - SHA-256 hashing before sending to Apple
   - Nonce validation on return
   - Automatic cleanup after use

2. **Credential Validation:**
   - Validates Apple ID credential before processing
   - Validates identity token presence and format
   - Error handling for invalid credentials

3. **Privacy:**
   - Only requests necessary scopes (full name, email)
   - Apple may hide email and provide relay email
   - Display name is optional and may not be provided on subsequent sign-ins

## Testing Checklist

- [ ] Test new user sign-in with Apple
- [ ] Test returning user sign-in with Apple
- [ ] Test profile creation after Apple sign-in
- [ ] Test error handling (user cancels, network error, etc.)
- [ ] Test on physical device (required for Sign in with Apple)
- [ ] Verify nonce security implementation
- [ ] Verify proper navigation after sign-in
- [ ] Test with email hiding enabled
- [ ] Test with private relay email

## Requirements

### Xcode Configuration
1. Enable "Sign in with Apple" capability in project settings
2. Configure Apple Sign In in Firebase console
3. Add Apple as a sign-in provider in Firebase Authentication
4. Test on physical device (Simulator support is limited)

### Info.plist (if needed)
No additional Info.plist entries required for basic implementation.

## Apple HIG Compliance

✅ Uses official `ASAuthorizationAppleIDButton`  
✅ Black button style (recommended for light backgrounds)  
✅ Proper button sizing (56pt height)  
✅ Correct corner radius matching app design  
✅ Prominent placement as primary sign-in option  
✅ Clear alternative options for email sign-in  

## Future Enhancements

- Add Sign in with Apple to Settings for account linking
- Implement credential revocation handling
- Add keychain integration for faster re-authentication
- Support for Sign in with Apple on web (for future web version)
