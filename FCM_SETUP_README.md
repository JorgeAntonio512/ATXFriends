# Firebase Cloud Messaging (FCM) Setup for Avenue3

## Overview

This document explains the Firebase Cloud Messaging implementation for Avenue3's push notification system.

## Files Created/Modified

### New Files
1. **NotificationManager.swift** - Main notification handling class
2. **AppDelegate.swift** - UIKit AppDelegate for FCM lifecycle management
3. **NotificationSettingsView.swift** - UI for notification settings
4. **CloudFunctionsReference.swift** - Cloud Functions implementation guide

### Modified Files
1. **Avenue3App.swift** - Integrated AppDelegate and notification setup
2. **FirestoreService.swift** - Added FCM token storage methods

## Architecture

### 1. Notification Manager (`NotificationManager.swift`)

The `NotificationManager` is a singleton that handles:
- ✅ Requesting notification permissions
- ✅ Managing FCM token lifecycle
- ✅ Storing FCM tokens to Firestore
- ✅ Handling notification delivery (foreground & background)
- ✅ Routing notification taps to appropriate screens
- ✅ Badge management

**Key Methods:**
```swift
// Request notification permission
await notificationManager.requestNotificationPermission()

// Set current user (call on sign in)
notificationManager.setCurrentUser(userID)

// Update FCM token (called automatically)
notificationManager.updateFCMToken(token)

// Remove FCM token (call on sign out)
await notificationManager.removeFCMToken(for: userID)
```

### 2. App Delegate (`AppDelegate.swift`)

Handles:
- ✅ APNs device token registration
- ✅ FCM delegation setup
- ✅ Notification center delegate setup

The `@UIApplicationDelegateAdaptor` in Avenue3App.swift connects this to SwiftUI.

### 3. App Integration (`Avenue3App.swift`)

The main app file now:
- ✅ Requests notification permission on first launch (after 3 second delay)
- ✅ Listens for auth state changes via NotificationCenter
- ✅ Stores FCM token when user signs in
- ✅ Removes FCM token when user signs out
- ✅ Passes NotificationManager as environment object

### 4. Firestore Integration

FCM tokens are stored in the `users` collection:

```json
{
  "users/{userId}": {
    "fcmToken": "string",
    "fcmTokenUpdatedAt": "timestamp",
    // ... other user fields
  }
}
```

**New Methods in FirestoreService:**
- `updateFCMToken(userID:token:)` - Stores FCM token
- `removeFCMToken(userID:)` - Removes FCM token on sign out

## Notification Types

Avenue3 supports four notification types:

### 1. New Match 🎉
**Trigger:** When `isMutualMatch` becomes `true` in the `matches` collection

**Data:**
```json
{
  "type": "new_match",
  "matchID": "match_id",
  "matchedUserId": "user_id"
}
```

**Navigation:** Opens the Matches tab and shows the match detail

### 2. New Message 💬
**Trigger:** When a new document is created in `conversations/{id}/messages`

**Data:**
```json
{
  "type": "new_message",
  "conversationID": "conversation_id",
  "senderId": "sender_user_id"
}
```

**Navigation:** Opens the conversation with the sender

### 3. Plan Request 📅
**Trigger:** When a new document is created in the `plans` collection

**Data:**
```json
{
  "type": "plan_request",
  "planID": "plan_id",
  "senderId": "sender_user_id",
  "activityName": "activity"
}
```

**Navigation:** Opens the plan detail view

### 4. Plan Confirmed ✅
**Trigger:** When a plan's `status` field changes to `"confirmed"`

**Data:**
```json
{
  "type": "plan_confirmed",
  "planID": "plan_id",
  "confirmedById": "user_id",
  "activityName": "activity"
}
```

**Navigation:** Opens the confirmed plan detail view

## Backend Setup Required

### Firebase Cloud Functions

You need to deploy Firebase Cloud Functions to trigger notifications. See `CloudFunctionsReference.swift` for complete implementation.

**Steps:**

1. **Initialize Functions:**
   ```bash
   firebase init functions
   ```

2. **Install Dependencies:**
   ```bash
   cd functions
   npm install firebase-admin firebase-functions
   ```

3. **Copy Function Code:**
   - Open `CloudFunctionsReference.swift`
   - Copy the TypeScript code from the comments
   - Paste into `functions/src/index.ts`

4. **Deploy:**
   ```bash
   firebase deploy --only functions
   ```

### Required Cloud Functions

- `onNewMatch` - Triggers on match creation
- `onNewMessage` - Triggers on message creation
- `onNewPlanRequest` - Triggers on plan creation
- `onPlanConfirmed` - Triggers on plan status update

## Xcode Setup

### 1. Add Firebase Capabilities

In your Xcode project:

1. Select your app target
2. Go to **Signing & Capabilities**
3. Click **+ Capability**
4. Add **Push Notifications**
5. Add **Background Modes**
   - Check ✅ **Remote notifications**

### 2. Update Info.plist

No additional Info.plist changes are required. Firebase handles everything.

### 3. Add Firebase SDK

Make sure these packages are in your Package.swift or SPM:
```swift
.package(url: "https://github.com/firebase/firebase-ios-sdk", from: "10.0.0")
```

Dependencies:
- `FirebaseAuth`
- `FirebaseFirestore`
- `FirebaseMessaging`
- `FirebaseStorage`

## Testing

### Test Notification Permissions

1. Run the app on a device (notifications don't work in simulator)
2. Wait 3 seconds after launch
3. You should see the permission alert
4. Grant permission

### Test FCM Token Storage

1. Sign in with a user account
2. Check Firestore console
3. Navigate to `users/{userId}`
4. Verify `fcmToken` field is populated

### Test Cloud Functions

1. Deploy functions: `firebase deploy --only functions`
2. Create a test match in Firestore
3. Check Firebase Functions logs: `firebase functions:log`
4. Check device for notification

### Test Notification Handling

1. Send a test notification from Firebase Console:
   - Go to Cloud Messaging
   - Click "Send test message"
   - Enter your FCM token (visible in NotificationSettingsView in DEBUG mode)
   - Add custom data: `{"type": "new_match", "matchID": "test123"}`

2. Test foreground delivery (app open)
3. Test background delivery (app in background)
4. Test tap handling (tap notification)

## Firestore Security Rules

Update your Firestore security rules to allow Cloud Functions to read user tokens:

```javascript
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {
    match /users/{userId} {
      allow read: if request.auth != null;
      allow write: if request.auth != null && request.auth.uid == userId;
    }
    
    match /matches/{matchId} {
      allow read, write: if request.auth != null;
    }
    
    match /conversations/{conversationId} {
      allow read, write: if request.auth != null;
      
      match /messages/{messageId} {
        allow read, write: if request.auth != null;
      }
    }
    
    match /plans/{planId} {
      allow read, write: if request.auth != null;
    }
  }
}
```

## User Flow

### First Launch
1. App launches
2. After 3 seconds, permission alert appears
3. User grants/denies permission
4. If granted, app registers for remote notifications
5. FCM token is generated
6. Token is stored when user signs in

### Sign In
1. User authenticates
2. `AuthViewModel` posts `authStateDidChange` notification
3. App gets current user ID from Firebase Auth
4. `NotificationManager.setCurrentUser()` is called
5. FCM token is stored to user's Firestore document

### Sign Out
1. User signs out
2. `authStateDidChange` notification is posted
3. App removes FCM token from Firestore
4. Local state is cleared

### Receiving Notifications

**Foreground (app open):**
- Notification appears as banner
- Plays sound
- Updates badge
- Handled by `userNotificationCenter(_:willPresent:)`

**Background (app closed/backgrounded):**
- System shows notification
- Badge is updated
- Handled by `userNotificationCenter(_:didReceive:)`

**Tap:**
- App opens
- Navigation occurs based on notification type
- Notification routing via NotificationCenter

## Navigation Handling

Notifications post `NotificationCenter` messages for navigation:

```swift
// Listen for navigation events in your root view
.onReceive(NotificationCenter.default.publisher(for: .navigateToMatch)) { notification in
    if let matchID = notification.userInfo?["matchID"] as? String {
        // Navigate to match detail
    }
}

.onReceive(NotificationCenter.default.publisher(for: .navigateToConversation)) { notification in
    if let conversationID = notification.userInfo?["conversationID"] as? String {
        // Navigate to conversation
    }
}

.onReceive(NotificationCenter.default.publisher(for: .navigateToPlan)) { notification in
    if let planID = notification.userInfo?["planID"] as? String {
        // Navigate to plan detail
    }
}
```

You'll need to implement the actual navigation logic in your `RootView` or navigation coordinator.

## Debugging

### Enable Debug Logging

In `AppDelegate.swift`:
```swift
func application(
    _ application: UIApplication,
    didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey : Any]? = nil
) -> Bool {
    
    // Enable FCM debug logging
    Messaging.messaging().isAutoInitEnabled = true
    
    // ... rest of setup
}
```

### Common Issues

**No FCM Token:**
- Check that APNs is properly configured in Firebase Console
- Verify push notification capability is enabled
- Make sure you're testing on a real device

**Token Not Stored:**
- Check that user is signed in
- Verify `setCurrentUser()` is called after sign in
- Check Firestore security rules

**Notifications Not Received:**
- Verify Cloud Functions are deployed
- Check Functions logs: `firebase functions:log`
- Test with Firebase Console test message
- Ensure device has internet connection

**Notification Tap Not Working:**
- Check that notification includes correct `type` field
- Verify navigation listeners are set up
- Check console for routing logs

## NotificationSettingsView

A ready-to-use settings view is provided:

```swift
NavigationLink {
    NotificationSettingsView()
        .environmentObject(notificationManager)
} label: {
    Label("Notifications", systemImage: "bell.badge")
}
```

Features:
- ✅ Shows current permission status
- ✅ Button to request permission
- ✅ Button to open Settings (if denied)
- ✅ List of notification types
- ✅ Debug info (FCM token in DEBUG builds)

## Next Steps

1. ✅ Add the files to your Xcode project
2. ✅ Deploy Cloud Functions
3. ✅ Test on a real device
4. ✅ Implement navigation handlers in RootView
5. ✅ Add NotificationSettingsView to your settings
6. ✅ Test all four notification types
7. ✅ Update Firestore security rules

## Support

For Firebase Cloud Messaging documentation:
- [FCM iOS Setup](https://firebase.google.com/docs/cloud-messaging/ios/client)
- [Cloud Functions](https://firebase.google.com/docs/functions)
- [Cloud Messaging](https://firebase.google.com/docs/cloud-messaging)

---

**Created:** May 24, 2026  
**Last Updated:** May 24, 2026  
**Version:** 1.0
