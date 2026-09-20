# Avenue3 Event DMs - Integration Checklist

## Files to Add to Xcode Project

- [ ] `MessageModels.swift` - Core data models
- [ ] `MessagingViewModel.swift` - ViewModel with business logic
- [ ] `MessagesView.swift` - Main messages tab UI
- [ ] `EventMessageThreadView.swift` - Event DM conversation UI

## Files to Update in Xcode Project

- [ ] `MessagingService.swift` - Updated message persistence methods
- [ ] `FirestoreService.swift` - Added `fetchMatch(matchID:)` method

## Xcode Project Configuration

### Add Files to Target
Ensure all new Swift files are added to your app target:
1. Right-click on Avenue3 folder in Xcode
2. Select "Add Files to Avenue3..."
3. Select all new `.swift` files
4. Check "Copy items if needed"
5. Select Avenue3 target

### Import Statements
All files already include necessary imports:
- `Foundation`
- `SwiftUI`
- `FirebaseFirestore`
- `Observation` (for @Observable)

## Integration Points

### 1. Replace Messages Tab
If you have an existing MessagesView, you'll need to:
- [ ] Replace old MessagesView with new one, OR
- [ ] Update your tab bar to use the new MessagesView

### 2. Navigation
The new MessagesView uses `.sheet()` for navigation. If you prefer NavigationLink:
- [ ] Update MessagesView to use NavigationLink instead
- [ ] Update thread selection logic

### 3. Event Fetching
The MessagingViewModel includes a helper method `fetchEvent(eventID:)` that:
- [ ] Fetches from Firestore `events` collection
- [ ] You may need to update this if your event storage differs
- [ ] Ensure events collection structure matches expectations

## Firestore Requirements

### Collections Needed
- [x] `messages` - Already exists (updated with eventID field)
- [x] `matches` - Already exists
- [ ] `events` - Verify this collection exists and has correct structure

### Events Collection Structure
```javascript
events/
  {eventID}/
    id: String
    name: String
    heroImageURL: String
    weekends: Array<Object>
```

## Dependencies Check

### Firebase
- [ ] FirebaseFirestore is installed
- [ ] FirebaseAuth is installed

### Swift Features
- [ ] Swift 5.9+ (for @Observable macro)
- [ ] iOS 17+ deployment target

## Testing Checklist

### Basic Functionality
- [ ] App builds successfully
- [ ] Messages tab loads without errors
- [ ] Can view existing match-based DMs
- [ ] Can send messages in match-based DMs
- [ ] Can create event-based DMs (if feature exists)
- [ ] Can send messages in event-based DMs

### UI Testing
- [ ] Messages section appears when regular DMs exist
- [ ] Events section appears when event DMs exist
- [ ] Sections hide when empty
- [ ] Empty state shows when no messages
- [ ] Thread rows display correctly
- [ ] Event badges show correct event names
- [ ] Unread counts display correctly
- [ ] Pull-to-refresh works

### Data Testing
- [ ] Messages persist to Firestore
- [ ] Real-time updates work
- [ ] Messages marked as read correctly
- [ ] Thread grouping works correctly
- [ ] Event thread IDs are unique

### Edge Cases
- [ ] Handles missing user data gracefully
- [ ] Handles missing event data gracefully
- [ ] Handles missing match data gracefully
- [ ] Works with no messages
- [ ] Works with only regular DMs
- [ ] Works with only event DMs

## Potential Issues & Solutions

### Issue: "Cannot find FirebaseAuthService"
**Solution:** Verify FirebaseAuthService exists and has `currentUserID` property

### Issue: "Cannot find User model"
**Solution:** Ensure User SwiftData model exists with toUser() conversion from FirebaseUser

### Issue: "Cannot find Match model"
**Solution:** Verify Match model exists with expected properties

### Issue: "Events not loading"
**Solution:** Check fetchEvent() method matches your Firestore events collection structure

### Issue: "Thread navigation not working"
**Solution:** Verify MessageThreadView exists and accepts thread parameter

### Issue: "Build errors with @Observable"
**Solution:** Ensure deployment target is iOS 17+ and Swift 5.9+

## Production Readiness

Before deploying to production:
- [ ] Remove all `print()` debug statements (or wrap in #if DEBUG)
- [ ] Add analytics events for event DM creation/sending
- [ ] Add error reporting (e.g., Crashlytics)
- [ ] Performance test with large message counts
- [ ] Test on slow networks
- [ ] Test with airplane mode (offline behavior)
- [ ] Review Firestore security rules
- [ ] Add rate limiting for message sending
- [ ] Add profanity/spam filters

## Security Review

- [ ] Firestore rules allow reading messages for authenticated users
- [ ] Firestore rules only allow writing messages where senderID = auth.uid
- [ ] User can only mark their own messages as read
- [ ] Event DM creation follows same security rules as regular DMs

## Documentation

- [ ] Update app documentation with Event DMs feature
- [ ] Add code comments where needed
- [ ] Create user-facing help documentation
- [ ] Update changelog

## Rollout Strategy

### Phase 1: Internal Testing
- [ ] Deploy to TestFlight for internal testing
- [ ] Test with real event data
- [ ] Verify Firestore costs are acceptable

### Phase 2: Beta Testing  
- [ ] Deploy to beta users
- [ ] Monitor for crashes/errors
- [ ] Collect feedback

### Phase 3: Production
- [ ] Deploy to App Store
- [ ] Monitor analytics
- [ ] Watch for user feedback

## Notes

- The implementation maintains visual consistency with existing Avenue3 design
- Event DMs use the same warm gradient and color scheme as regular DMs
- All Firestore field naming conventions are followed (capital "ID")
- Uses FirestoreService.shared.fetchUser() pattern as required
