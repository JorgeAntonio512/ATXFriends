# Firebase Cloud Messaging - Implementation Checklist

## ✅ Completed: iOS App Setup

All the iOS-side code has been set up for you! Here's what was done:

### Files Created

1. **NotificationManager.swift**
   - ✅ Singleton manager for all notification operations
   - ✅ Requests notification permissions on first launch
   - ✅ Manages FCM token lifecycle
   - ✅ Stores FCM tokens to user's Firestore document
   - ✅ Handles notification delivery (foreground & background)
   - ✅ Routes notification taps to appropriate screens
   - ✅ Implements UNUserNotificationCenterDelegate
   - ✅ Implements MessagingDelegate

2. **AppDelegate.swift**
   - ✅ UIKit AppDelegate for FCM lifecycle
   - ✅ Sets up notification delegates
   - ✅ Handles APNs token registration
   - ✅ Passes device token to FCM

3. **NotificationSettingsView.swift**
   - ✅ Beautiful UI for notification settings
   - ✅ Shows permission status
   - ✅ Allows users to enable/disable notifications
   - ✅ Links to iOS Settings if denied
   - ✅ Shows FCM token in DEBUG mode

4. **CloudFunctionsReference.swift**
   - ✅ Complete Cloud Functions implementation guide
   - ✅ TypeScript code for all 4 notification triggers
   - ✅ Copy-paste ready for deployment

5. **NotificationNavigationExample.swift**
   - ✅ Example code for handling notification navigation
   - ✅ Two approaches: simple and coordinator pattern
   - ✅ Ready to integrate into your RootView

6. **FCM_SETUP_README.md**
   - ✅ Comprehensive documentation
   - ✅ Architecture explanation
   - ✅ Testing guide
   - ✅ Troubleshooting tips

### Files Modified

1. **Avenue3App.swift**
   - ✅ Added AppDelegate adapter
   - ✅ Requests notification permission on first launch (3 second delay)
   - ✅ Listens for auth state changes
   - ✅ Stores FCM token when user signs in
   - ✅ Removes FCM token when user signs out
   - ✅ Passes NotificationManager as environment object

2. **FirestoreService.swift**
   - ✅ Added `updateFCMToken(userID:token:)` method
   - ✅ Added `removeFCMToken(userID:)` method

---

## 🔧 Required: Xcode Configuration

### 1. Add Push Notification Capability
1. Open your Xcode project
2. Select your app target
3. Go to **Signing & Capabilities**
4. Click **+ Capability**
5. Add **Push Notifications**

### 2. Add Background Modes
1. Still in **Signing & Capabilities**
2. Click **+ Capability**
3. Add **Background Modes**
4. Check ✅ **Remote notifications**

### 3. Verify Firebase SDK Packages
Make sure these are in your Swift Package Manager:
- FirebaseAuth
- FirebaseFirestore
- FirebaseMessaging
- FirebaseStorage

---

## 🚀 Required: Firebase Backend Setup

### 1. Deploy Cloud Functions

The Cloud Functions handle sending notifications when events occur.

**Steps:**

```bash
# 1. Navigate to your Firebase project folder
cd /path/to/your/firebase/project

# 2. Initialize Cloud Functions (if not already done)
firebase init functions
# Select TypeScript or JavaScript when prompted

# 3. Install dependencies
cd functions
npm install firebase-admin firebase-functions

# 4. Copy the Cloud Functions code
# Open CloudFunctionsReference.swift in Xcode
# Copy the TypeScript code from the comments
# Paste into functions/src/index.ts

# 5. Deploy the functions
cd ..
firebase deploy --only functions
```

### 2. Create Firestore Collections

Your Cloud Functions will trigger on these collections:

**matches** collection:
```
matches/{matchId}
  - user1ID: string
  - user2ID: string
  - isMutualMatch: boolean
  - user1Decision: boolean?
  - user2Decision: boolean?
  - createdAt: timestamp
  - updatedAt: timestamp
```

**conversations** collection (for messages):
```
conversations/{conversationId}
  - participants: [string] (array of user IDs)
  - lastMessageAt: timestamp
  
  /messages/{messageId}
    - senderId: string
    - text: string
    - createdAt: timestamp
```

**plans** collection:
```
plans/{planId}
  - creatorId: string
  - invitedUserId: string
  - activityName: string
  - status: string ("pending", "confirmed", "declined")
  - confirmedById: string?
  - dateTime: timestamp?
  - createdAt: timestamp
```

### 3. Update Firestore Security Rules

```javascript
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {
    // Users collection
    match /users/{userId} {
      allow read: if request.auth != null;
      allow write: if request.auth != null && request.auth.uid == userId;
    }
    
    // Matches collection
    match /matches/{matchId} {
      allow read, write: if request.auth != null;
    }
    
    // Conversations collection
    match /conversations/{conversationId} {
      allow read, write: if request.auth != null;
      
      match /messages/{messageId} {
        allow read, write: if request.auth != null;
      }
    }
    
    // Plans collection
    match /plans/{planId} {
      allow read, write: if request.auth != null;
    }
  }
}
```

---

## 📱 Optional: Add Navigation Handling

To handle notification taps and navigate to the right screen:

1. Open `NotificationNavigationExample.swift`
2. Choose your approach (simple or coordinator)
3. Add the navigation code to your `RootView.swift`
4. Test by tapping notifications

**Quick Version:**
```swift
// In your RootView
.onReceive(NotificationCenter.default.publisher(for: .navigateToMatch)) { notification in
    if let matchID = notification.userInfo?["matchID"] as? String {
        // Navigate to match detail
        selectedTab = 1 // Switch to Matches tab
        self.matchToShow = matchID
    }
}

.onReceive(NotificationCenter.default.publisher(for: .navigateToConversation)) { notification in
    if let conversationID = notification.userInfo?["conversationID"] as? String {
        // Navigate to conversation
        selectedTab = 2 // Switch to Messages tab
        self.conversationToShow = conversationID
    }
}

.onReceive(NotificationCenter.default.publisher(for: .navigateToPlan)) { notification in
    if let planID = notification.userInfo?["planID"] as? String {
        // Navigate to plan
        selectedTab = 3 // Switch to Plans tab
        self.planToShow = planID
    }
}
```

---

## 🧪 Testing Checklist

### On Device (Simulator won't work for push notifications)

1. **Test Permission Request**
   - [ ] Run app on device
   - [ ] Wait 3 seconds
   - [ ] Permission alert appears
   - [ ] Grant permission

2. **Test FCM Token Storage**
   - [ ] Sign in with a user
   - [ ] Check Firestore console
   - [ ] Verify `users/{userId}/fcmToken` exists

3. **Test Cloud Functions**
   - [ ] Deploy functions
   - [ ] Trigger an event (create a match, send a message, etc.)
   - [ ] Check Firebase Functions logs: `firebase functions:log`
   - [ ] Check device receives notification

4. **Test Notification Display**
   - [ ] Foreground: App is open, notification appears as banner
   - [ ] Background: App is closed, notification appears in notification center
   - [ ] Tap notification, app opens and navigates correctly

5. **Test Sign Out**
   - [ ] Sign out
   - [ ] Check Firestore - fcmToken should be removed
   - [ ] Sign back in
   - [ ] Check Firestore - fcmToken should be re-added

---

## 📚 Documentation

All documentation is in these files:

- **FCM_SETUP_README.md** - Complete setup guide
- **CloudFunctionsReference.swift** - Cloud Functions code
- **NotificationNavigationExample.swift** - Navigation examples

---

## 🐛 Common Issues & Solutions

### Issue: No FCM token generated
**Solution:** 
- Verify APNs is configured in Firebase Console
- Check Push Notifications capability is enabled
- Test on a real device (not simulator)

### Issue: Token not stored to Firestore
**Solution:**
- Ensure user is signed in
- Check that `authStateDidChange` notification is firing
- Verify Firestore security rules allow writes

### Issue: Notifications not received
**Solution:**
- Deploy Cloud Functions first
- Check Functions logs: `firebase functions:log`
- Test with Firebase Console test message
- Verify device has internet connection
- Check notification permission is granted

### Issue: Notification tap doesn't navigate
**Solution:**
- Implement navigation handlers in RootView
- Verify notification includes correct `type` field
- Check NotificationCenter observers are set up

---

## ✨ Features Implemented

Your app now supports:

✅ **Automatic Permission Request** - Asks for notification permission 3 seconds after first launch  
✅ **FCM Token Management** - Automatically stores/removes tokens on sign in/out  
✅ **Four Notification Types:**
   - 🎉 New Match
   - 💬 New Message
   - 📅 Plan Request
   - ✅ Plan Confirmed

✅ **Smart Notification Handling** - Shows in foreground, handles taps, routes to correct screen  
✅ **Settings UI** - Beautiful settings view for users to manage notifications  
✅ **Debug Support** - Shows FCM token in DEBUG mode for testing

---

## 🎯 Next Steps

1. Add files to Xcode project (they're all in `/repo/`)
2. Configure Xcode capabilities (Push Notifications + Background Modes)
3. Deploy Cloud Functions to Firebase
4. Test on a real device
5. Add navigation handlers to RootView (optional)
6. Ship it! 🚀

---

**Need Help?**
- Check FCM_SETUP_README.md for detailed docs
- Review CloudFunctionsReference.swift for backend code
- See NotificationNavigationExample.swift for navigation

**Questions?**
Just ask! I'm here to help. 😊
