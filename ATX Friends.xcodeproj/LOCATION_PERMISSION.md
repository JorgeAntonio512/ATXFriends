# Location Permission Setup

## Required: Add Location Permission to Info.plist

Avenue3 requires location permission to find friends nearby in Austin.

---

## ⚠️ Important: Ensure iOS Target Selected

**If you see "macOS Application Target Properties", you need to select the iOS target!**

### Steps to select iOS target:

1. In Xcode **Project Navigator** (left sidebar), click the **Avenue3 project** (blue icon at top)
2. In the center pane, under **TARGETS**, select your **iOS target** (not macOS)
3. Verify **Deployment Info** shows **iOS** (not macOS)
4. Now you should see **Custom iOS Target Properties** in the Info tab

---

## Option 1: Using Xcode UI (Recommended)

1. Open your project in Xcode
2. Select the **Avenue3 project** in the navigator (blue icon)
3. Under **TARGETS**, select the **iOS target** (make sure it says iOS, not macOS)
4. Go to the **Info** tab
5. Under **Custom iOS Target Properties**, click the **+** button
6. Start typing "Location When" and select:
   - **Privacy - Location When In Use Usage Description**
   - Or manually enter key: `NSLocationWhenInUseUsageDescription`
7. Set the value to: `Avenue3 needs your location to find friends nearby in Austin.`

---

## Option 2: Create Info.plist File (if needed)

If your iOS project doesn't have an Info.plist:

1. Right-click on Avenue3 folder → **New File...**
2. Choose **Property List** (under Resource)
3. Name it `Info.plist`
4. Add the location permission:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
    <!-- Location Permission -->
    <key>NSLocationWhenInUseUsageDescription</key>
    <string>Avenue3 needs your location to find friends nearby in Austin.</string>
</dict>
</plist>
```

5. Select **iOS target** → **Build Settings**
6. Search for "Info.plist File"
7. Set path to `Avenue3/Info.plist`

---

## Option 3: Direct Info.plist Edit

If you already have an Info.plist file for iOS, add this entry:

```xml
<key>NSLocationWhenInUseUsageDescription</key>
<string>Avenue3 needs your location to find friends nearby in Austin.</string>
```

Full example:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
    <!-- Other keys... -->
    
    <!-- Location Permission -->
    <key>NSLocationWhenInUseUsageDescription</key>
    <string>Avenue3 needs your location to find friends nearby in Austin.</string>
    
    <!-- Firebase configuration (if needed) -->
    <key>FirebaseAppDelegateProxyEnabled</key>
    <false/>
</dict>
</plist>
```

---

## Why This Permission?

### NSLocationWhenInUseUsageDescription

- **When:** User opens the Home/Discovery tab
- **Purpose:** Find nearby users within selected radius
- **Scope:** Only while app is in use
- **Privacy:** Location not tracked continuously

### What Happens:

1. User opens Home tab for the first time
2. iOS shows permission alert with your custom message
3. User grants or denies:
   - **Granted:** App gets user's location, finds nearby users
   - **Denied:** App uses Austin, TX (30.2672, -97.7431) as fallback

### User Privacy:

- ✅ Only "when in use" (not "always")
- ✅ Single location fetch, then stops
- ✅ Not continuously tracked
- ✅ Stored in user's profile for matching
- ✅ Battery friendly
- ✅ Graceful fallback if denied

---

## Permission Dialog

When the user first accesses the Home tab, they'll see:

```
┌─────────────────────────────────────┐
│  "Avenue3" Would Like to Use Your  │
│         Current Location            │
├─────────────────────────────────────┤
│                                     │
│  Avenue3 needs your location to     │
│  find friends nearby in Austin.     │
│                                     │
├─────────────────────────────────────┤
│  [ Allow While Using App ]          │
│  [ Allow Once ]                     │
│  [ Don't Allow ]                    │
└─────────────────────────────────────┘
```

---

## Testing Location Permission

### Simulator

1. Run app in iOS Simulator
2. Open Home tab
3. Permission alert appears
4. Grant permission
5. Simulator menu: **Features** → **Location** → **Custom Location**
6. Set to Austin, TX: `30.2672, -97.7431`

### Device

1. Run app on physical device
2. Open Home tab
3. Grant permission when prompted
4. App uses actual GPS location
5. Finds real nearby users

### Permission States

**Not Determined:**
- First time user opens Home tab
- Shows permission alert

**Authorized:**
- Permission granted
- App gets location
- Shows nearby users

**Denied:**
- Permission denied by user
- App uses Austin, TX fallback
- Still works, just with default location

**Restricted:**
- Device restrictions (e.g., parental controls)
- App uses Austin, TX fallback

---

## Handling Permission in Code

Already implemented in `HomeViewModel.swift`:

```swift
func requestLocationPermission() async {
    let status = locationManager.authorizationStatus
    
    switch status {
    case .notDetermined:
        locationManager.requestWhenInUseAuthorization()
    case .authorizedWhenInUse, .authorizedAlways:
        locationManager.startUpdatingLocation()
    case .denied, .restricted:
        // Use Austin, TX as fallback
        userLocation = CLLocationCoordinate2D(latitude: 30.2672, longitude: -97.7431)
    @unknown default:
        userLocation = CLLocationCoordinate2D(latitude: 30.2672, longitude: -97.7431)
    }
}
```

---

## User Settings

If user denies permission, they can enable it later:

**iOS Settings Path:**
```
Settings → Privacy & Security → Location Services → Avenue3
```

Options:
- Never
- Ask Next Time
- While Using the App

---

## Best Practices

### ✅ Do:
- Use clear, specific language in permission message
- Request permission only when needed (Home tab)
- Provide fallback functionality (Austin default)
- Stop location updates after first fetch
- Respect user's decision

### ❌ Don't:
- Request permission on app launch
- Track location continuously
- Store location without consent
- Require location for all features

---

## Firebase Configuration (Optional)

If using Firebase, you may also want to disable the proxy:

```xml
<key>FirebaseAppDelegateProxyEnabled</key>
<false/>
```

This is already handled in `Avenue3App.swift` with manual `FirebaseApp.configure()`.

---

## Additional Permissions (Future)

For future features, you might need:

### Camera Access (for profile photos)
```xml
<key>NSCameraUsageDescription</key>
<string>Avenue3 needs camera access to take profile photos.</string>
```

### Photo Library (for profile photos)
```xml
<key>NSPhotoLibraryUsageDescription</key>
<string>Avenue3 needs photo library access to select profile photos.</string>
```

### Notifications (for new messages/matches)
```xml
<key>NSUserNotificationsUsageDescription</key>
<string>Avenue3 sends notifications for new matches and messages.</string>
```

---

## Verification Checklist

Before submitting to App Store:

- [ ] Location permission key added to Info.plist
- [ ] Permission message is clear and specific
- [ ] Permission requested at appropriate time (not on launch)
- [ ] Fallback behavior works when denied
- [ ] Location stops updating after first fetch
- [ ] Tested on simulator and device
- [ ] Privacy policy mentions location usage

---

## App Store Review Notes

Apple requires:

1. **Clear purpose:** Permission message must explain why location is needed
2. **Appropriate timing:** Request when user needs the feature
3. **Graceful handling:** App works without permission
4. **Privacy policy:** Document location usage

Avenue3 complies with all requirements:
- ✅ Clear message about finding friends nearby
- ✅ Requested only when Home tab opened
- ✅ Falls back to Austin, TX if denied
- ✅ Stops tracking after initial fetch

---

**Add this permission to enable location-based friend discovery!** 📍
