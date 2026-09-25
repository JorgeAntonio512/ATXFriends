# Avenue3 — iOS Friend-Finding App

## What This App Is

Avenue3 is a warm, wholesome, family-friendly iOS app that helps people in Austin, TX build **meaningful friendships** — not romantic connections. This is explicitly NOT a dating app. There are no "likes," no swiping for romance, no hookup culture. Think neighborhood block party, not Tinder.

The app is **individuals-only**: every profile is one person looking for friends. (An earlier Individual / Couple / Couple with Kids "friendship mode" was deliberately removed and does not exist in the code.)

**Reference for how the live app behaves:** `docs/android-parity-spec.md`. When this file and the spec disagree, trust the spec (and the code).

---

## Tech Stack

- **Platform:** iOS only (initially)
- **UI Framework:** SwiftUI
- **Architecture:** MVVM (Model-View-ViewModel)
- **Local data:** SwiftData
- **Backend:** Firebase (Auth, Firestore, Storage)
- **Location:** CoreLocation for radius-based matching

---

## Design Vibe

- Warm, cozy, sleek, and modern
- Family-friendly — every screen should feel safe for all ages
- Playful but not childish
- Color palette: think earth tones, warm neutrals, soft greens — nothing cold or clinical
- NO dark patterns, NO aggressive notifications, NO engagement-bait mechanics

---

## Location

- Austin, TX only at launch
- Matching is **radius-based** (default 10 miles, user-adjustable)
- Use CoreLocation for user location; store and query via Firestore GeoPoints

---

## The Profile System

Every user builds their profile around three pieces:

1. **Photos** — exactly 3 profile pictures (no filters required, just real people)
2. **Activities** — 3 "Main" activities plus up to 7 "Extra" activities (3–10 total, exactly 3 flagged Main) (e.g. hiking, board games, tacos)
3. **Day/Slot combos** — at least 3, no maximum; each combo is a day of the week + a time slot (e.g. Monday Night, Saturday Wake Up, Sunday Owl Hours)

Activities and day/slot combos drive the matching algorithm.

---

## Activities

- Users search from a **fixed, shared list** of activities
- If their activity isn't found, they can **manually add it**
- User-added activities are appended to the global fixed list (available to all future users)
- Store activities in Firestore as a shared collection

---

## Time Slots

There are 5 named time slots. Each covers a distinct part of the day:

| Slot | Hours |
|------|-------|
| ☀️ Wake Up | 7:00am – 12:00pm |
| 🌤 Afternoon | 12:00pm – 5:00pm |
| 🌆 Evening | 5:00pm – 9:00pm |
| 🌙 Night | 9:00pm – 2:00am |
| 🦉 Owl Hours | 2:00am – 7:00am |

Users pick **at least 3 day/slot combos** (no maximum) from 7 days × 5 slots (e.g. Monday Night, Saturday Wake Up, Sunday Owl Hours).

---

## Matching Logic

- A match is triggered automatically when **at least one activity** AND **at least one time** overlap between two users
- Matched users appear in each other's **Matches View**
- Each user independently makes a **Yay or Nay** decision
- If **both say Yay** → they are connected and a **Messaging thread** opens between them
- If either says Nay → no connection, no notification to the other party

---

## App Views / Screens (High Level)

1. **Onboarding / Auth** — Sign up or sign in (Firebase Auth)
2. **Profile Setup** — Upload 3 photos, pick 3 Main + up to 7 Extra activities, pick at least 3 times

The main app is a custom tab bar with exactly **six tabs**, left to right (no Plans tab, no Home/Discover tab):

1. **Matches** — Pending Yay/Nay decisions
2. **Today** — Open slots and plans in the next 24 hours
3. **Upcoming** — Multi-invitee plans (`GroupPlan`)
4. **Simpatico** — Compatibility questions
5. **Messages** — In-app text messaging for mutual matches (1-on-1 plan proposals live here)
6. **Settings** — Adjust radius, edit profile, notifications, privacy & safety

---

## Key Principles for Code

- Write clean, idiomatic SwiftUI with MVVM separation — no logic in Views
- Use `@Observable` (Swift 5.9+) for ViewModels, not `ObservableObject` unless necessary
- SwiftData for local persistence; sync to Firestore for backend
- All Firebase calls go through a dedicated service layer (e.g. `FirebaseAuthService`, `FirestoreService`)
- Keep accessibility in mind — support Dynamic Type and VoiceOver
- No third-party dependencies unless absolutely necessary — prefer Apple frameworks

---

## What This App Is NOT

- Not a dating app
- Not a hookup app
- Not Tinder, Bumble, Feeld, Grindr, or anything of that nature
- Romantic connections happening organically outside the app is fine — but the app itself has zero romantic framing, zero suggestive content, and zero mechanics that encourage it

Do NOT generate README files, documentation files, or markdown 
guides unless explicitly asked. Code files only.
