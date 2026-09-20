# Notification Permission Request

## Overview
The notification permission request has been removed from app launch. Instead, it should be requested at the right moment — specifically after a user gets their first match.

## How to Request Permission

The `NotificationManager` has a public method `requestNotificationPermission()` that you can call when needed.

### Example Usage

```swift
// In your ViewModel or View where you handle the first match

import SwiftUI

// Access the NotificationManager (injected via EnvironmentObject)
@EnvironmentObject var notificationManager: NotificationManager

// When the user gets their first match:
func handleFirstMatch() async {
    // Show the match UI, celebration, etc.
    
    // Then request notification permission
    let granted = await notificationManager.requestNotificationPermission()
    
    if granted {
        print("✅ User granted notification permission!")
    } else {
        print("❌ User denied notification permission")
    }
}
```

### Alternative: Direct Access to Singleton

If you don't have access to the environment object, you can also use the singleton:

```swift
// Request permission using the singleton
Task {
    let granted = await NotificationManager.shared.requestNotificationPermission()
    
    if granted {
        print("✅ User granted notification permission!")
    }
}
```

## Important Notes

- The function is marked `@MainActor`, so it must be called from the main thread or in an async context
- The function automatically:
  - Saves the permission state to UserDefaults
  - Updates the `hasRequestedPermission` property
  - Registers for remote notifications if permission is granted
  - Updates the `notificationPermissionStatus` property
- The function returns `true` if permission was granted, `false` otherwise

## Where to Call It

**Recommended location**: After a user receives their first match
- This creates a natural connection between the feature and the benefit
- Users understand why they're being asked for permission
- It follows Apple's best practices for requesting permissions in context

## Implementation Checklist

- [ ] Determine where "first match" occurs in your codebase
- [ ] Add the permission request call after the first match
- [ ] Test that the permission dialog appears at the right time
- [ ] Verify that FCM tokens are properly stored after permission is granted
