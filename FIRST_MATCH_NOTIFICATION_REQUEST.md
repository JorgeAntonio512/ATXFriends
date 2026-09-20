# First Match Notification Permission Request

## Overview
Added intelligent notification permission request that appears after a user gets their first mutual match. This creates a natural, contextual moment to explain the value of notifications.

## Implementation Details

### When It Triggers
- **Only after a mutual match**: When both users say "Yay" to each other
- **Only if permission is .notDetermined**: Won't ask if already granted or denied
- **Only once ever**: Uses UserDefaults flag to never ask again

### User Flow

1. User says "Yay" to a match
2. If the other user already said "Yay", it becomes a mutual match
3. System checks:
   - Has user been asked before? (UserDefaults: `hasBeenAskedForNotifications`)
   - Is notification permission `.notDetermined`?
4. If both conditions are met, show custom alert
5. User chooses:
   - **"Enable Notifications"**: Triggers system permission request
   - **"Not Now"**: Dismisses and never asks again

### Alert Message
```
Title: "Stay Connected!"

Message: "You have a new match! 🎉

Enable notifications so you never miss a message or plan from your new friends."

Buttons:
- Enable Notifications (primary - sage green filled button with white text and icon)
- Not Now (secondary - plain text button with no background)
```

### Visual Design
The prompt appears as a **custom overlay** (not a system alert) with:
- **Dimmed background**: Semi-transparent black overlay (40% opacity)
- **Card design**: Rounded rectangle with warm gradient background matching app theme
- **Icon**: Large bell badge icon in sage green within a circular light green background
- **Primary button**: 
  - Filled sage green gradient background
  - White text and bell badge icon
  - Prominent with shadow effect
  - 56pt height for easy tapping
- **Secondary button**: 
  - Plain gray text
  - No background or border
  - 44pt height
  - Less prominent for secondary action
- **Smooth transition**: Fades in/out with opacity animation
- **Non-dismissible tap outside**: Tapping the dimmed background doesn't dismiss the prompt

## Files Modified

### 1. MatchesViewModel.swift

**Added Properties:**
- `@Published var showNotificationPrompt: Bool` - Controls alert visibility
- `private let hasBeenAskedForNotificationsKey = "hasBeenAskedForNotifications"` - UserDefaults key

**Modified Methods:**
- `makeDecision(on:decision:)` - Detects when a mutual match is created and triggers check

**New Methods:**
- `checkAndRequestNotificationPermission()` - Private method that checks conditions and shows prompt
- `requestNotificationPermission()` - Called when user taps "Enable Notifications"
- `dismissNotificationPrompt()` - Called when user taps "Not Now"

### 2. MainTabView.swift (MatchesTabView)

**Added:**
- `.overlay()` modifier with custom `NotificationPermissionPromptView`
- New `NotificationPermissionPromptView` struct for custom-styled prompt
- Replaces system alert with fully-styled custom UI matching app design

## Logic Flow

```swift
User says Yay
    ↓
Check if this creates mutual match
    ↓
If YES: checkAndRequestNotificationPermission()
    ↓
Check UserDefaults: hasBeenAskedForNotifications?
    ↓
If NO: Check NotificationManager.notificationPermissionStatus
    ↓
If .notDetermined: Set showNotificationPrompt = true
    ↓
Alert appears with two options:
    ├─→ "Enable Notifications"
    │       ↓
    │   Set UserDefaults flag = true
    │   Call NotificationManager.shared.requestNotificationPermission()
    │   System permission dialog appears
    │
    └─→ "Not Now"
            ↓
        Set UserDefaults flag = true
        Dismiss alert
        Never ask again
```

## UserDefaults Flag

**Key:** `hasBeenAskedForNotifications`
**Type:** `Bool`
**Purpose:** Ensures we only ask once, regardless of user's choice
**Set to true when:**
- User taps "Enable Notifications"
- User taps "Not Now"

## Benefits

1. **Contextual**: Asks at the perfect moment - right after first match
2. **One-time only**: Never annoys users with repeated prompts
3. **Value-driven**: Explains *why* notifications are useful
4. **Non-intrusive**: If dismissed, never asks again
5. **Smart**: Respects existing permission states

## Testing Checklist

- [ ] Alert appears after first mutual match
- [ ] Alert does NOT appear if permission already granted
- [ ] Alert does NOT appear if permission already denied
- [ ] Alert does NOT appear after second mutual match
- [ ] "Enable Notifications" triggers system permission dialog
- [ ] "Not Now" dismisses and never shows again
- [ ] UserDefaults flag persists across app restarts
- [ ] Works correctly when other user said Yay first
- [ ] Works correctly when current user said Yay first

## Edge Cases Handled

1. **Permission already granted**: Alert never shows
2. **Permission already denied**: Alert never shows
3. **User already asked**: Alert never shows again
4. **Multiple matches at once**: Only asks on first mutual match
5. **User says Nay**: No alert triggered
6. **One-sided match**: No alert triggered

## Future Enhancements (Optional)

- Add analytics to track:
  - How many users enable notifications after first match
  - How many users dismiss the prompt
- A/B test different messages
- Add visual celebration animation before showing prompt
