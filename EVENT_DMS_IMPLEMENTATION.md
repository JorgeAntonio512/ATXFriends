# Avenue3 Event DMs Implementation Summary

## Overview
This update adds support for Event-based Direct Messages alongside existing match-based DMs in Avenue3's Messages tab. The implementation follows MVVM architecture and maintains consistency with Avenue3's existing code patterns.

## Files Created

### 1. **MessageModels.swift** - Core Data Models
- **Message struct**: Updated to include optional `eventID` field
  - `matchID`: Empty string for event messages
  - `eventID`: Optional string for event-based messages
  - Regular DMs have matchID, no eventID
  - Event DMs have eventID, empty matchID

- **MessageThread struct**: Represents a conversation thread
  - `match`: Optional - present for regular DMs
  - `event`: Optional - present for event DMs
  - `otherUser`: The other participant
  - `isEventThread`: Computed property to distinguish thread types

### 2. **MessagingViewModel.swift** - Business Logic
Manages all message-related state and operations using `@Observable` macro.

**Key Properties:**
- `allThreads`: All message threads
- `regularThreads`: Filtered match-based threads (computed)
- `eventThreads`: Filtered event-based threads (computed)
- `messages`: Current conversation messages

**Key Methods:**
- `fetchThreads()`: Fetches all threads, automatically separates regular vs event
- `loadMessages(for matchID:)`: Loads match-based conversation
- `loadMessagesForEvent(eventID:otherUserID:)`: Loads event-based conversation
- `sendMessage(matchID:receiverID:)`: Sends regular DM
- `sendEventMessage(eventID:receiverID:)`: Sends event DM

**Threading Logic:**
- Groups messages by `matchID` for regular DMs
- Groups by `eventID + otherUserID` for event DMs
- Fetches user and event/match data using `FirestoreService.shared.fetchUser(userID:)` pattern
- Calculates unread counts per thread

### 3. **MessagesView.swift** - Main Messages Tab UI
SwiftUI view with two distinct sections:

**Messages Section** (Regular DMs):
- Shows all match-based conversations
- Uses `ThreadRow` component
- Hidden if empty

**Events Section** (Event DMs):
- Shows all event-based conversations
- Uses `EventThreadRow` component
- Hidden if no event DMs exist
- Displays event name badge with calendar icon

**Features:**
- Pull-to-refresh support
- Empty state when no messages exist
- Navigation to appropriate thread view (regular or event)
- Consistent Avenue3 warm gradient design

### 4. **EventMessageThreadView.swift** - Event Conversation UI
Full-screen chat interface for event-based DMs.

**Key Features:**
- Event context header showing event name with calendar icon
- Same message bubble design as regular DMs
- Uses `MessageInputBar` component (shared with regular DMs)
- Calls `viewModel.sendEventMessage(eventID:receiverID:)`
- Real-time message listener for event threads
- Auto-marks messages as read

## Files Modified

### 5. **MessagingService.swift** - Message Persistence
Updated to support optional eventID field:

**Changes to `sendMessage()`:**
- Added optional `eventID` parameter
- Constructs message with either matchID or eventID

**Changes to `messageToFirestoreData()`:**
- Conditionally adds `matchID` if not empty
- Conditionally adds `eventID` if present

**Changes to `firestoreDataToMessage()`:**
- `matchID` defaults to empty string if not present
- `eventID` is optional
- Creates `Message` with both fields

### 6. **FirestoreService.swift** - Data Fetching
Added new method:

**`fetchMatch(matchID:)`:**
- Fetches a single match by ID
- Used by MessagingViewModel when reconstructing thread data
- Returns `Match?` (nil if not found)

## Firestore Structure

### Messages Collection
```
messages/
  {messageID}/
    senderID: String
    receiverID: String
    text: String
    sentAt: Timestamp
    isRead: Boolean
    matchID: String (empty for event messages)
    eventID: String? (optional, only for event messages)
```

**Regular DM Example:**
```javascript
{
  senderID: "user123",
  receiverID: "user456",
  matchID: "match789",
  text: "Hey! Want to grab coffee?",
  sentAt: Timestamp,
  isRead: false
}
```

**Event DM Example:**
```javascript
{
  senderID: "user123",
  receiverID: "user456",
  matchID: "",
  eventID: "ACL2025",
  text: "Excited for the festival!",
  sentAt: Timestamp,
  isRead: false
}
```

## Thread Identification

### Regular DM Thread ID
- Uses `matchID` directly
- Thread key: `"match_{matchID}"`

### Event DM Thread ID  
- Combines `eventID` and `otherUserID`
- Thread key: `"event_{eventID}_{otherUserID}"`
- Ensures unique threads per event per user pair

## UI Flow

1. User opens Messages tab
2. `MessagesView` calls `viewModel.fetchThreads()`
3. ViewModel fetches all messages from Firestore
4. Groups messages into threads (regular vs event)
5. Displays two sections if both types exist
6. User taps a thread:
   - Regular DM → Opens `MessageThreadView`
   - Event DM → Opens `EventMessageThreadView`
7. View loads messages and sets up real-time listener
8. User sends message via appropriate send method

## Code Conventions Followed

✅ Firestore field naming: `userID`, `eventID`, `senderID`, `receiverID`, `matchID` (capital "ID")  
✅ Use `FirestoreService.shared.fetchUser(userID:)` for user decoding  
✅ Use `updateData()` over `setData(merge: true)`  
✅ MVVM architecture with `@Observable` macro  
✅ SwiftUI best practices  
✅ Consistent warm gradient design matching Avenue3 style  

## Security

- Existing Firestore security rules remain unchanged
- Broad read rule on messages collection already permits reading event DMs
- All writes check authentication via `Auth.auth().currentUser`

## Edge Cases Handled

1. **Empty threads list**: Shows appropriate empty state
2. **No event DMs**: Hides Events section entirely
3. **No regular DMs**: Hides Messages section entirely
4. **Missing user data**: Skips thread creation, logs warning
5. **Missing event data**: Skips thread creation, logs warning
6. **Unread count**: Separately calculated for each thread type

## Testing Recommendations

1. Create a regular match-based DM
2. Create an event-based DM (same users, different event)
3. Verify both sections appear
4. Send messages in each thread type
5. Verify unread counts update correctly
6. Test pull-to-refresh
7. Verify real-time updates work for both thread types
8. Test with no messages (empty state)
9. Test with only regular DMs (Events section hidden)
10. Test with only event DMs (Messages section hidden)

## Future Enhancements

- Group event DMs by event name with expandable sections
- Event thumbnail images in thread list
- Filter/search functionality
- Archive conversations
- Delete conversations
- Typing indicators
- Read receipts
