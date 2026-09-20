# Notification Settings Changes

## Summary
Updated the notification settings system to include interactive toggles that save user preferences to Firestore. Each notification type can now be independently enabled/disabled, and preferences are persisted to the user's document.

## Files Modified

### 1. UserModel.swift
- **Added**: `NotificationPreferences` struct with four boolean properties:
  - `newMatches`: For match notifications
  - `newMessages`: For message notifications  
  - `planRequests`: For plan request notifications
  - `planConfirmations`: For plan confirmation notifications
- **Added**: `notificationPreferences` property to `FirebaseUser`
- **Updated**: `FirebaseUser` initializer to include notification preferences with defaults (all enabled)

### 2. FirestoreService.swift
- **Updated**: `userToFirestoreData()` to serialize notification preferences to Firestore
- **Updated**: `firestoreDataToUser()` to deserialize notification preferences (defaults to all enabled for backward compatibility)
- **Added**: `updateNotificationPreferences()` method to update just the notification preferences without modifying other user data

### 3. NotificationSettingsView.swift
- **Removed**: NavigationStack wrapper (view is now pushed via NavigationLink)
- **Removed**: "Done" toolbar button (uses standard back button instead)
- **Removed**: Static `NotificationToggleRow` with checkmarks
- **Added**: `@State` properties for each toggle: `newMatchesEnabled`, `newMessagesEnabled`, `planRequestsEnabled`, `planConfirmationsEnabled`
- **Added**: `currentUser`, `isLoading`, and `isSaving` state properties
- **Added**: `loadUserPreferences()` method to load preferences from Firestore on appear
- **Added**: `savePreferences()` method to save preferences to Firestore when toggles change
- **Added**: New `InteractiveNotificationToggleRow` component with actual Toggle controls
- **Updated**: UI to match app's warm gradient design language
- **Updated**: All toggles are disabled when notification permission is not granted

### 4. MainTabView.swift
- **Added**: `@EnvironmentObject var notificationManager: NotificationManager` to `SettingsTabView`
- **Replaced**: TODO placeholder button with `NavigationLink` to `NotificationSettingsView`
- **Updated**: Notification settings navigation properly passes the `notificationManager` as an environment object

## Data Structure

### Firestore Document Structure
```json
{
  "notificationPreferences": {
    "newMatches": true,
    "newMessages": true,
    "planRequests": true,
    "planConfirmations": true
  }
}
```

## User Flow

1. User navigates to Settings → Notifications
2. View loads current preferences from Firestore
3. If notifications are disabled at system level:
   - Toggles are disabled (grayed out)
   - User sees button to open Settings app
4. If notifications are enabled:
   - User can toggle individual notification types
   - Each toggle immediately saves to Firestore
   - "Saving..." indicator appears briefly
5. Preferences are persisted across app launches

## Backward Compatibility

- Existing users without notification preferences will default to all notifications enabled
- The `firestoreDataToUser()` method handles missing `notificationPreferences` gracefully
- No migration required for existing user documents

## Testing Checklist

- [ ] Verify toggles load correctly from Firestore
- [ ] Verify toggles save correctly to Firestore
- [ ] Verify toggles are disabled when system notifications are off
- [ ] Verify "Enable Notifications" button works for .notDetermined state
- [ ] Verify "Open Settings" button works for .denied state
- [ ] Verify saving indicator appears when toggling
- [ ] Verify preferences persist across app restarts
- [ ] Verify backward compatibility with users who don't have preferences set
