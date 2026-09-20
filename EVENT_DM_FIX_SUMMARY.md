# Event DM Navigation Fix Summary

## Problem
When tapping the DM button on an attendee in EventDetailView, the thread was being initialized without proper `eventID` and `matchID` values, resulting in "Thread has neither event nor match" errors on send.

## Root Causes
1. **Wrong View Selection**: EventDetailView was navigating to `MessageThreadView` instead of `EventMessageThreadView` for event threads
2. **Overly Complex Thread ID Logic**: The original code was querying Firestore for existing messages and trying to determine the thread ID, which was unnecessary and error-prone
3. **Incorrect Thread ID Format**: The logic was inconsistent in how it generated thread IDs

## Changes Made

### 1. Fixed Navigation Destination (EventDetailView.swift)
**Before:**
```swift
.navigationDestination(item: $navigateToThread) { thread in
    MessageThreadView(thread: thread, viewModel: MessagingViewModel())
}
```

**After:**
```swift
.navigationDestination(item: $navigateToThread) { thread in
    // Use EventMessageThreadView for event threads, MessageThreadView for regular threads
    if thread.isEventThread {
        EventMessageThreadView(thread: thread, viewModel: MessagingViewModel())
    } else {
        MessageThreadView(thread: thread, viewModel: MessagingViewModel())
    }
}
```

### 2. Simplified Thread ID Generation (EventDetailView.swift)
**Before:**
```swift
private func initiateEventDM(with attendee: FirebaseUser) async {
    guard !currentUserID.isEmpty else { return }
    
    do {
        let db = Firestore.firestore()
        
        // Query for existing messages with this user and this event
        let snapshot = try await db.collection("messages")
            .whereField("eventID", isEqualTo: event.id)
            .getDocuments()
        
        // Find a message between current user and attendee
        var existingThreadID: String?
        
        for document in snapshot.documents {
            let data = document.data()
            let senderID = data["senderID"] as? String
            let receiverID = data["receiverID"] as? String
            let matchID = data["matchID"] as? String ?? ""
            
            // Make sure matchID is empty (event messages don't have matches)
            if matchID.isEmpty {
                if (senderID == currentUserID && receiverID == attendee.id) ||
                   (senderID == attendee.id && receiverID == currentUserID) {
                    existingThreadID = document.documentID
                    break
                }
            }
        }
        
        // Create a thread ID for this conversation
        // Use a consistent format: smaller userID first for consistency
        let sortedIDs = [currentUserID, attendee.id].sorted()
        let threadID = existingThreadID ?? "event_\(event.id)_\(sortedIDs[0])_\(sortedIDs[1])"
        
        // Create MessageThread with event parameter (not match)
        let thread = MessageThread(
            id: threadID,
            match: nil,
            event: event,
            otherUser: attendee.toUser(),
            lastMessage: nil,
            unreadCount: 0
        )
        
        navigateToThread = thread
        
    } catch {
        print("❌ Error initiating event DM: \(error)")
    }
}
```

**After:**
```swift
private func initiateEventDM(with attendee: FirebaseUser) async {
    guard !currentUserID.isEmpty else { return }
    
    // Create deterministic thread ID: always sort user IDs alphabetically
    let sortedIDs = [currentUserID, attendee.id].sorted()
    let threadID = "event_\(event.id)_\(sortedIDs[0])_\(sortedIDs[1])"
    
    print("🟢 DEBUG: initiateEventDM called")
    print("🟢 DEBUG: Event ID: \(event.id)")
    print("🟢 DEBUG: Current User ID: \(currentUserID)")
    print("🟢 DEBUG: Attendee ID: \(attendee.id)")
    print("🟢 DEBUG: Generated Thread ID: \(threadID)")
    
    // Create MessageThread with event parameter (not match)
    // This ensures isEventThread returns true
    let thread = MessageThread(
        id: threadID,
        match: nil,
        event: event,
        otherUser: attendee.toUser(),
        lastMessage: nil,
        unreadCount: 0
    )
    
    navigateToThread = thread
}
```

## How It Works Now

### Thread ID Format
For event-based DMs, the thread ID is deterministically generated as:
```
event_[eventID]_[smallerUserID]_[largerUserID]
```

Example: `"event_ACL2025_user123_user456"`

### Data Flow
1. User taps "Send Message" on an attendee in EventDetailView
2. `initiateEventDM()` creates a deterministic thread ID by sorting user IDs
3. A `MessageThread` object is created with:
   - `id`: The event-prefixed thread ID
   - `match`: `nil` (no match for event threads)
   - `event`: The full event object
   - `otherUser`: The attendee as a User object
4. Navigation triggers to `EventMessageThreadView` (because `thread.isEventThread` is `true`)
5. EventMessageThreadView calls `viewModel.sendEventMessage()` with:
   - `matchID`: `thread.id` (e.g., "event_ACL2025_user123_user456")
   - `eventID`: `thread.event?.id` (e.g., "ACL2025")
   - `receiverID`: `thread.otherUser.id`

### Consistency Guarantees
- **Deterministic IDs**: Sorting user IDs alphabetically ensures the same thread ID is generated regardless of which user initiates the conversation
- **No Redundant Queries**: Thread ID is computed locally without querying Firestore
- **Proper Routing**: Event threads use `EventMessageThreadView`, regular threads use `MessageThreadView`

## Expected Behavior
✅ Tapping DM on an attendee navigates to EventMessageThreadView
✅ Thread ID is formatted as `event_ACL2025_user1_user2` (sorted user IDs)
✅ Event ID is properly set (e.g., "ACL2025")
✅ Messages are sent with both `matchID` (thread ID) and `eventID` (event ID)
✅ The same two users always get the same thread ID for the same event
✅ No more "Thread has neither event nor match" errors

## Testing Checklist
- [ ] Navigate to an event detail page
- [ ] RSVP for the event
- [ ] View the attendees list
- [ ] Tap "Send Message" on an attendee
- [ ] Verify EventMessageThreadView opens (shows event name at top)
- [ ] Verify debug logs show correct thread ID format
- [ ] Send a message
- [ ] Verify message appears in chat
- [ ] Check Firestore to confirm message has both `matchID` and `eventID` fields
