# ATX Friends — Android Parity Spec

Read-only inventory of the live iOS app, compiled by tracing real navigation paths and call
sites in the code as of 2026-09-24. Nothing in this document was validated by running the app —
every claim is derived from static reading of the Swift/TypeScript/rules source and must be
checked against the real app per the test plan at the end.

## 0. Naming and ground truth

- **In-app brand name:** "ATX Friends" (every user-facing string, launch screen, app display
  name). **Xcode project/target/module name:** `Avenue3` (`ATX Friends.xcodeproj`, `@main struct
  Avenue3App`, `import Avenue3` in tests). **Bundle ID:** `com.georgeAppDev.Avenue3`. **Firebase
  project ID:** `avenue3-73bb4`. Most `.swift` files sit at the repo root; a smaller set lives in
  an `Avenue3/` subfolder that Xcode treats as a synchronized source group (both compile into the
  same target).
- **CLAUDE.md is out of date in three material ways**, confirmed against the live code, not
  assumed:
  1. The "3-by-3" system is **not** 3/3/3. Photos are exactly 3. Activities are **3 "Main" + up to
     7 "Extra" (3–10 total, exactly 3 flagged Main)**. Day/slot combos are **at least 3, no
     maximum**.
  2. **Friendship mode (Individual / Couple / Couple with Kids) does not exist anywhere in the
     shipped code** — no enum, no selection UI, no Settings entry. See §11 and §12.
  3. There is no "Home/Discover" or "Plans" tab. The live tab bar is exactly six tabs, in this
     order: **Matches, Today, Upcoming, Simpatico, Messages, Settings** (`MainTabView.swift:86-93`,
     `enum Tab { matches, today, upcoming, simpatico, messages, settings }`).
- The tab bar is a fully custom view (`CustomTabBar`, `MainTabView.swift:248-348`), not SwiftUI's
  `TabView` chrome.

---

## 1. Screen inventory (by tab)

Every screen below was confirmed reachable by tracing real navigation/call sites, not by name
matching. Dead screens are listed only in §10.

### 1.1 Matches tab

**`MatchesView.swift`** — reached via tab bar → Matches. Nav title `"Matches"` (`:219`).
- Loading: spinner + `"Finding your matches..."` (`:63`).
- Sections: `"Pending Matches"` / `"Say Yay to connect"` (`:92,96`); `"Connected"` /
  `"You've both said Yay"` (`:154,158`).
- Pending card: `"Shared Interests"` (`:544`), category label `"You both like {category}"`
  (`MatchesViewModel.swift:597-600`, only rendered when there's no exact-activity chip to show),
  `"Free at the same time"` (`:579`), buttons `"Nay"` (`:610`) / `"Yay"` (`:625`).
- Connected row: `showUpMeter` string next to a calendar icon (`:736`), Simpatico badge
  `"{score}%"` (`:746`), a Plan icon and a Message icon.
- Empty state (`EmptyMatchesView`, `:817-866`): `"No Matches Yet"` (`:831`); body `"We're looking
  for people who share\nyour interests and availability.\nCheck back soon!"` (`:835`); info box
  `"How Matching Works"` (`:849`) / `"We automatically find people nearby who share at least one
  activity AND one time slot with you."` (`:854`).
- Mutual-match celebration overlay (`:246-256,352-397`): `"🎉"`, `"You're connected!"`, other
  user's name, `"Go say hi 👋"`, buttons `"Open"` / `"Maybe later"`; auto-dismisses after 6s.
- Actions: tap a pending card's photo/info → sheet `MatchDetailView` (`:226-228`). Tap Yay/Nay →
  `handleYay`/`handleNay` (`:424-438`) → `MatchesViewModel.makeDecision` (see §5). Tap a connected
  row → same `MatchDetailView` sheet. Tap the connected row's Plan icon → `ProposePlanSheet(mode:
  .proposal)` (`:229-235`). Tap the Message icon → posts `.navigateToMatchThread` (cross-tab hop
  into Messages, `:440-450`). Pull-to-refresh / `.onAppear` / `.onReceive(.onboardingCompleted)` →
  full or partial reload (`:79,215,237-262`).
- Error state: `viewModel.errorMessage` is set on failures but **never displayed anywhere in this
  file's body** — a real gap, not a design choice confirmed elsewhere. UNCONFIRMED whether a
  shared error surface exists in `MainTabView`.
- Fetch model: one-time `fetchMatches(for:)` on load/refresh — **no realtime listener** on the
  matches list itself.

**`MatchDetailView.swift`** — sheet from `MatchesView` only (`.sheet(item: $selectedMatch)`).
- Other user's name (large title, `:71-73`), bucketed distance line, `"Shared Interests"` (`:99`)
  + activity chips or category label `"You both like {category}"` (`:125-136`), `"Free at the
  Same Time"` (`:146`) + time-slot chips.
- If pending: `"Say Yay"` (`:180`) / `"Say Nay"` (`:208`), each behind a confirm alert (`"Say
  Yay?"` / `"This will let {name} know you're interested in being friends."`; `"Say Nay?"` /
  `"This match will be removed. {name} won't be notified."`, destructive).
- If mutual: `"You're Connected!"` (`:230`), `"You both said Yay. Start a conversation!"`
  (`:235`), buttons `"Send Message"` (`:255`, posts `.navigateToMatchThread` + dismiss) and
  `"Propose a Plan"` (`:286`, opens `ProposePlanSheet(mode: .proposal)` or calls an
  `onProposePlanRequested` closure when nested inside another sheet).
- Photo gallery: tap a photo → `fullScreenCover` `PhotoGalleryView` (`:419-510`); loads
  asynchronously with a gray gradient + spinner placeholder while photos download; silent
  per-photo failure (console-only).

### 1.2 Today tab

**`TodayView.swift`** — nav title `"Today"` (`:76`).
- Loading: `"Loading plans…"` (`:41`). Empty (filtered): `"No {activity} plans today"`; empty
  (unfiltered): `"Nobody's posted yet. Be the first one."` (`:236-239`).
- Filter chip `"All"` + per-activity chips (client-side filter only).
- Own-plan-claimed banner: `"Someone claimed your {activity} plan! 🎉"`
  (`TodayViewModel.swift:143`). Post confirmation toast: `"Posted!"` (`:222`).
- Ghost-suggestion section header: `"Or post your own"` / `"Free in the next day?"` / `"Your open
  slots"` depending on state (`:249-254`). Ghost card: `"In 3 hrs · 7pm"`-style time line, activity
  line `"{activity}?"`, button `"Post it"`. `"Your next usual slot: {day} {slot}"` link → posts
  `.navigateToUpcoming`. `"Or start from scratch"` link.
- Plan card: activity name, time badge (`"Today 7:00 PM"` / `"Tomorrow 9:00 AM"` / raw time),
  poster name + show-up meter, optional location/note, button `"I'm in"` (own posts show `"Your
  post"` instead, no button).
- Error alert: `"Something went wrong"` (`:94`).
- Actions: `+` toolbar → `ProposePlanSheet(mode: .openPost)` (blank). Tap a ghost card →
  `ProposePlanSheet(mode: .openPost, prefill:)`. Tap `"I'm in"` → `TodayViewModel.claimPlan` →
  `TodayPlanService.claimTodayPlan` (transactional: marks plan claimed, finds/creates a mutual
  `Match` for the pair, writes a pre-confirmed `Plan` so the resulting message thread shows a
  pinned plan card, then posts `.navigateToMatchThread`).
- Fetch model: **both** — one-time `loadOpenPlans()` then `setupListener()` attaches a realtime
  Firestore listener (`TodayPlanService.listenToOpenPlans`) that also detects the claimed-by-someone-
  else case for the banner; listener torn down `.onDisappear`.

### 1.3 Upcoming tab

**`UpcomingView.swift`** — nav title `"Upcoming"`, backed by `GroupPlansViewModel` (not
`PlansViewModel` — that type belongs to dead code, §10).
- 7-day strip (tomorrow through +6 days). Per day: a `GroupPlanCard` for each active `GroupPlan`
  that day, or — only when the day has no real plan — a single dashed ghost slot: `"{weekday}
  {timeslot} is open"` + `"Invite"` button (paperplane icon) → `ProposePlanSheet(mode:
  .groupInvite, prefill:)`.
- Week-strip dots: filled = real plan, hollow outline = ghost suggestion only, none = nothing.
- Loading: `"Loading plans…"`. Empty (`hasAnyContent == false`): `"Nothing planned yet"` /
  `"Tap + to invite some friends."`. Error alert: `"Something went wrong"`.
- `GroupPlanCard`: activity name, date/time pill, `"Hosted by {host}"`, location row (if set),
  footer `"{n} going"` + `myStatusText` (`"Hosting"` / `"Going"` / `"Can't make it"` /
  `"Invited"`).
- Actions: `+` toolbar → `ProposePlanSheet(mode: .groupInvite)` (blank). Tap a `GroupPlanCard` →
  sheet `GroupPlanDetailView(plan:, viewModel:)`. `.onAppear`/`.onDisappear` start/stop a realtime
  listener (`viewModel.startListening/stopListening`).
- Sorting/filtering: `upcomingPlans` = `status == .active && date > now && (isHost ||
  isInvitee)`, sorted ascending by date — cancelled/past plans never show. Ghost slots: generated
  tomorrow→+6 days, max 7, biased toward `.planned`-tier activities, deduped to **at most one ghost
  slot per calendar day**, and suppressed entirely on any day that already has a real plan.

**`GroupPlanDetailView.swift`** — sheet from `UpcomingView`.
- Shows plan detail, responses; invitee response buttons (`Going`/`Can't make it`) →
  `GroupPlansViewModel.respond(to:response:)` (writes only that invitee's key in the `responses`
  map). Host-only `"Cancel Plan"` button (only rendered in the `isHost` branch, `:62-71`) →
  `status: .active → .cancelled`.
- Calendar/directions actions go through the shared `PlanCalendarActionHandler` (§6).

### 1.4 Simpatico tab

**`SimpaticoView.swift`** — nav title `"Simpatico"` (`:33`). **Assessment: substantially
complete, not stubbed** — full 12-question bank, full persistence, full scoring math (unit-tested),
full UI flow. The one deliberately incomplete piece is a **one-way legacy migration**: a pre-v2
`answers` field is detected (`hasLegacyAnswers`) and drives an "upgrade banner" but is never
read/migrated into v2 scoring (`SimpaticoService.swift:12`, explicit code comment) — a real
functional gap if any v1-only answers exist in production, but documented/intentional, not an
accidental stub.
- Intro (first visit, no upgrade): `"Before you begin"` + explanatory copy about the 12 quick
  questions, skippability, and autosave (`:65-91`). Intro (upgrade path): `"Simpatico got an
  upgrade"` + `"It's quicker, and it helps find friends who actually fit — not just people who
  rated the same virtues \"very important.\""` (`:65,140-141`). Button `"Start"`.
- Question flow: header `"Question {n} of {total}"` + category chip; `"Your answer"` section;
  `"I'm good with friends who say…"` multi-select section; `"Doesn't matter to you"` (all options
  checked) or `"How much does this matter?"` with `"A little"/"Somewhat"/"Very"` importance
  buttons. Nav buttons `"Skip"` / `"Next"`/`"Finish"`.
- Completion: `"All done!"`, `"You answered {n} of {total}"`, `"Edit answers"` re-entry link.
  Explanatory copy: `"Once a friend finishes their questionnaire too, your Simpatico score
  appears next to their name in Messages."`
- Fetch model: one-time `fetchState()` (`getDocument`) — **no realtime listener**. Each
  Next/Skip persists immediately (partial per-question `mergeFields` write) and also saves the
  current question index to `UserDefaults` (Firestore has no record of skipped questions).
- Score display lives on the **Matches** tab (`"{score}%"` badge), computed from two independent
  one-time fetches of both users' answer docs — not live/realtime.

### 1.5 Messages tab

**`MessagesListView.swift`** — the live Messages screen (`MainTabView` wires `.messages` to this
type, **not** the dead `MessagesView.swift` — see §10). Nav title `"Messages"` (large).
- Loading: `"Loading messages..."`. Empty: `"No conversations yet"` / `"Match with someone, then
  propose a plan to start chatting."`.
- Row: other user's name, `"Say hi 👋"` placeholder if no message yet, plan pill (e.g. `"Poker ·
  Sat 7pm"`) when a confirmed plan exists.
- List order: rendered order is `sortedThreads` = all threads sorted purely by `lastActivityDate`
  descending (max of last-message time and plan-update time) — this **overrides** the
  view-model's own plan-priority sort (see §5).
- Actions: pull-to-refresh (populated state only). Tap a row → marks that match's unread state
  read immediately (before the thread even loads), then presents `MessageThreadView` via
  `fullScreenCover`.

**`MessageThreadView.swift`** — full-screen cover from `MessagesListView` (also reachable via
push-notification deep link and a second, independent `MainTabView`-owned cover for that path).
- Nav title: other user's display name. Toolbar: back chevron; trailing avatar button →
  `MatchDetailView` sheet (only when `thread.match != nil`).
- **Pinned confirmed-plan card** (non-event threads with an active confirmed plan): `"Plan
  confirmed"` badge, `"+{n} more"` overflow chip → `UpcomingPlansSheet`, `⋯` menu
  `"Reschedule"`/`"Cancel plan"`, `"You & {name}"`, day/time headline, activity + countdown
  subline (`"starts in N min"` / `"starts in N hr"` / `"happening now"`), `"Get directions"` +
  calendar icon, `"Add to Calendar"`/`"Added to Calendar"`.
- **Show-up prompt card** (after a Today-tab claimed plan's time has passed): `"How did it go?"`
  / `"Your {activity} hangout has passed."` / `"Did {name} show up?"`, buttons `"Didn't show"` /
  `"They showed up!"` → writes to `showUpReports` (§4/§5).
- Message list: date header always literally `"Today"` (not date-aware — a real bug, see §11).
  Confirmed-plan-no-messages empty state: `"You're on for tonight/today/tomorrow."` or `"You're on
  for {weekday, date}."` + `"Say hi to {name} before you head out."`. Default empty state: name +
  (`"You matched. Say hi, or skip straight to a plan."` or, for event threads, `"You're both going
  to {event}. Say hello!"`) + `"You both like"` chip row (tappable, prefills a plan) +
  `"Propose a plan"` button. `PlanProposalCard` renders in place of a normal bubble for
  `.planProposal` messages; once a proposal is confirmed its inline card is hidden (it "moves"
  into the pinned card).
- Input bar: `"+"` shortcut (calendar-badge-plus, non-event threads with a match) →
  `ProposePlanSheet(mode: .proposal)`. `MessageInputBar`: placeholder `"Message..."`, send button.
- Sheets/alerts: `ProposePlanSheet`; `ReschedulePlanSheet` (`"Suggest a new time"` / `"Your friend
  will need to re-confirm."` / `"Suggest New Time"`); `UpcomingPlansSheet` (`"More upcoming
  plans"`, per-row `"Cancel"` + confirm alert `"Cancel this plan?"`); `MatchDetailView`; Apple
  `EventEditView` (add-to-calendar); alerts for calendar-access-needed, Google/Outlook
  add-failures, cancel-plan confirm, and show-up-report failure.
- **No visible loading indicator** for message history itself (VM's `isLoading` is never read by
  this view) and **no visible error banner** for send/load failures (`errorMessage` is set but
  never displayed) — real UI gaps, not by-design omissions.
- Fetch model: messages load via one-time fetch **and** a realtime listener, both **unordered by
  any limit** — the entire message history for a match streams down every time the thread opens.
- **Confirmed bug**: for a thread with `thread.match == nil` (an event thread), the `.task` that
  kicks off message loading returns immediately via a guard and never calls `loadMessages` or
  attaches a listener — and because `MessagesListView` always opens threads with this view (never
  the purpose-built `EventMessageThreadView`), any event-DM row in the live Messages list is
  effectively broken (opens to a screen that never loads messages). See §10/§11.

### 1.6 Settings tab

**`SettingsTabView`** (defined inside `MainTabView.swift:914-1185`) — reached via tab bar →
Settings. Single scrolling list of cards.

- **"PROFILE" card**: rows "Display Name" (→ `EditProfileView`), "Photos" (→
  `PhotosSettingsView`), "Activities" (→ `ActivitiesSettingsView`), "Availability" (→
  `AvailabilitySettingsView`). Row subtitles ("Your 3 interests" / "Your 3 time slots") are stale
  copy left over from the old 3-3-3 spec — the real rules are 3-10/exactly-3-Main and ≥3/no-max.
- **"PREFERENCES" card**: "Search Radius" (subtitle `"{n} miles"`) → `SearchRadiusSettingsView`.
- **"SHARE MY LOCATION" card**: inline segmented picker (`ShareMyLocationContent`), not a pushed
  screen.
- **"ACCOUNT" card**: "Notifications" → `NotificationSettingsView`; "Privacy & Safety" →
  `PrivacyAndSafetyView`; "Location" (status badge "Precise"/"Approximate"/"Enabled"/"Denied"/"Not
  Set"/"Unknown") → deep-links to the iOS Settings app, not an in-app screen; "Sign Out"
  (destructive, confirm alert `"Sign Out"` / `"Are you sure you want to sign out?"`) →
  `AuthViewModel.signOut()`.
- Footer: `"ATX Friends"` / `"Version 1.0.0"` (hardcoded, not read from the bundle).
- **No account-deletion entry point here** — deletion lives one level deeper, in Privacy &
  Safety (below).

**`ActivitiesSettingsView.swift`** — autosave, no Save button (`saveChangesImmediately()` on
every toggle/star/add). Rule: `(3...10).contains(count) && mainCount == 3`. Back navigation and
sheet-dismiss are **blocked** while invalid (`navigationBarBackButtonHidden` +
`.interactiveDismissDisabled`). Strings: warning banner `"Select 3 Main activities to continue"` /
`"{n}/3 Main selected"`; header `"Your Activities"` / `"Select 3 Main activities, plus up to 7
Extras"`; section labels `"Main ({n}/3)"` / `"Extras ({n}/7)"`; search placeholder `"Search
activities..."`; inline add `"Add '{text}' — choose a category"` (menu of the 13
`ActivityCategory` cases) or `"Activity already exists"`; `AddCustomActivitySheet` (`"Add Custom
Activity"`, name field, required category menu). Max-reached: `"You've selected the max of 10
activities!"` / `"Remove one to add a different activity"`.

**`AvailabilitySettingsView.swift`** — rule: `count >= 3`, no max. Same back-button lock pattern.
7×5 grid (`TimeSlotGrid`). Every tap immediately runs the **full-profile** save path
(`viewModel.saveProfile()`, not a narrow field update). Strings: `"Select at least 3 time slots
to continue"` / `"{n}/3 selected"`; header `"Your Availability"` / `"Select at least 3 times when
you're usually free to hang out"`.

**`PhotosSettingsView.swift`** — exactly 3 fixed slots; tapping any slot replaces only that slot
(`PhotosPicker(maxSelectionCount: 1)`), resized client-side to max 1024px longest side before
upload. **No back-button lock here** — the 3-photo minimum is only enforced at onboarding-time
full-profile validation, not re-enforced from Settings. Strings: `"Your Photos"` / `"Tap any photo
to replace it"`; tips `"Photo Tips"` / bullet list; uploading overlay `"Uploading..."`.

**`EditProfileView.swift`** — display name, 2–30 chars (`"{n}/30"` counter, error `"Please enter
a name (2-30 characters)"`). Save re-runs the **entire** `validateProfile()` gate (all fields, not
just name) — same for `SearchRadiusSettingsView`. Only Activities and Share-My-Location bypass
full validation via dedicated narrow writes.

**`SearchRadiusSettingsView.swift`** — slider **5–25 miles**, step 1. Default 10.0 miles.
(`ProfileViewModel.updateRadius` clamps 1–50 programmatically, but that range is unreachable from
this UI.) Info card shows unrelated marketing copy ("Your neighborhood" / "Nearby areas" /
"Greater Austin area") that doesn't match the real `DistanceDisplay` bucket thresholds — don't
conflate the two (see §5).

**`ShareLocationSettingsView.swift`** — inline segmented picker, 3 modes exactly as named:
`"Off"`, `"Update once"`, `"When I open the app"`. Static copy: `"Your matches see about how far
away you are — never your exact location."` Last-updated label (`"today"`/`"this week"`/`"over a
week ago"`). `.once` mode shows an `"Update now"` button. Full mechanics in §5.

**`NotificationSettingsView.swift`** (`Avenue3/` subfolder) — permission-status card
(Enabled/Disabled/Not Set/Provisional/Ephemeral). Five independently-saved toggles: `"New
Matches"`, `"Messages"`, `"Plan Requests"`, `"Plan Confirmations"`, `"Group Updates"` (this last
one's subtitle — "Group join requests and membership changes" — references the deleted Groups
feature; see §10/§11). Toggles disabled unless system permission is `.authorized`. DEBUG-only raw
FCM token display.

**`PrivacyAndSafetyView.swift`** / **`PrivacyAndSafetyViewModel.swift`** — rows: "Block a User" /
"Blocked Users" (writes `users/{uid}.blockedUsers` via arrayUnion/arrayRemove, and flips
`isBlocked: true, isMutualMatch: false` on every `matches` doc between the pair); "Report a User"
(pick a match → pick a `ReportReason`: Inappropriate behavior / Harassment / Fake profile / Spam /
Other → optional comments → new `reports` doc); "Export My Data" (client-side plain-text export
of profile/activities/availability/photo URLs/matches/messages/plans via
`UIActivityViewController` — no write; description text says "friendship mode" which doesn't
exist, see §11); "How We Protect Your Data" (opens `https://avenue3.app/privacy.html` in an
in-app Safari view); **"Delete Account"** (nav link, subtitle "Permanently delete your data") →
`DeleteAccountView`: **immediate, permanent deletion — no grace period.** Header: "This
permanently deletes your account right away. It can't be undone." "What will be deleted" list:
Profile & Photos (profile, photos, Simpatico answers); Matches ("removed for the other person
too"); Messages ("every conversation, including the messages you received — the other person's
thread with you disappears"); Plans (plans, Today plans, hosted group plans removed for everyone;
removed from group plans you were invited to). The single confirmation step is typing the literal
word `"DELETE"` to enable the button `"Permanently Delete My Account"`. While deleting: button
shows a spinner and "Deleting Your Account…", back navigation is disabled. On failure: alert
"Couldn't Delete Account" with a retry-able message; nothing is signed out. Full flow in §2.8.

### 1.7 Screens shared across tabs

**`ProposePlanSheet.swift`** — the app's only plan-creation screen, three `Mode`s
(`ProposePlanSheet.swift:28-32`):

| Mode | Entry points | Nav title / header | Date UI | Invitee picker | Activity source | Writes |
|---|---|---|---|---|---|---|
| `.proposal(matchID, receiverID)` | Matches (connected row's plan icon), `MatchDetailView`'s "Propose a Plan", Messages thread `+`/shared-interest chip | `"Propose a Plan"` / `"What do you want to do?"` | Graphical `DatePicker`, any future date/time (`Date()...`) | none | Global shared `activities` list | 1-on-1 `Plan` doc, status `pending`, `proposedDates` = single date (array field, only ever 1 element) |
| `.openPost` | Today tab `+` button, Today ghost-card tap | `"Post a Plan"` / `"Open to everyone on ATX Friends · next 24 hours"` | `Today`\|`Tomorrow` segmented picker + compact time picker, clamped ranges (today: `[now+15min, end of today]`; tomorrow: `[start of tomorrow, now+24h]` — a rolling 24h ceiling, not "end of tomorrow") | none | **User's own profile activities**, not the global list | `TodayPlan` doc, `todayPlans` collection, no recipient, claimable first-come-first-served |
| `.groupInvite` | Upcoming tab `+` button, Upcoming ghost-slot "Invite" | `"Plan Something"` / `"Invite matches — any date."` | Graphical `DatePicker`, any future date/time | **Only this mode** — checklist of mutual matches, pre-checked if opened from a ghost card matching that activity; requires ≥1 selected | Global shared `activities` list | `GroupPlan` doc, `hostID` + `inviteeIDs` + `responses` all defaulted `invited`, status `active` |

Shared across all three: a required "Where?" field (`PlanLocationField`, always required to
submit), an activity text field + filtered suggestion chips, an `isPrefilled` banner when opened
from a ghost card, and a `"Cancel"` button that always dismisses without writing anything.

**`PlanProposalCard.swift`** — renders inline in `MessageThreadView` for `.planProposal` messages;
the only live UI for `pending`→`confirmed`/`declined` transitions (Accept/Decline gated to the
receiver, `status == .pending`). States: loading (spinner + raw text fallback), loaded, failed
(falls back to raw message text).

**`PlanLocationField.swift`** — shared "Where?" field (all 3 `ProposePlanSheet` modes + Today
plan creation). Free-typed text is valid on its own; MapKit match is optional enrichment. Red hint
`"Add a place so people know where to meet"` only after focus-then-blur while empty. States:
`"Searching…"`, `"No matches — you can enter any location"`, `"Couldn't search right now — you
can still type a location"`, results list with name/address/distance.

**`UserProfileView.swift`** — reachable from `MatchDetailView`'s photo gallery, and (in the dead
`MatchesTabView`) from the old Matches implementation — not reachable from any other live screen
in the files traced for this spec.

---

## 2. Launch, auth, and onboarding flow

### 2.1 Launch

`Avenue3App.swift` — `FirebaseApp.configure()` in `init()`. `WindowGroup` is `ZStack { RootView()
… LaunchScreenView() }`; splash is a **hardcoded 2.0s timer** (not tied to any readiness check),
faded out over 0.5s. `.onAppear` also silently restores any existing Google Sign-In (`GIDSignIn`)
Keychain session — unrelated to Firebase Auth, only used later for Google Calendar. `.onOpenURL`
routes Google and MSAL (Microsoft) redirect URLs. `LaunchScreenView` shows `"ATX Friends"` /
`"Find Your People"` over a custom canvas-drawn logo; no logic, no states.

### 2.2 RootView — the router

`RootView.swift` is the single source of truth for which top-level screen shows:

```
if isCheckingAuth                 → LoadingView()  ("ATX Friends" + spinner)
else if authenticated:
    if pendingNewSSOUser != nil       → LocationGateView(...)
    else if isProfileComplete         → MainTabView()
    else                              → ProfileSetupFlowView()
else                                  → OnboardingView()
```

`isCheckingAuth` clears after a **300ms artificial delay** (explicit anti-flicker comment) +
`refreshAuthState()`. `isProfileComplete` comes from fetching `users/{uid}` and reading
`isProfileComplete`; if the doc is missing entirely while Firebase Auth is authenticated and there
is no in-memory `pendingNewSSOUser` (app was killed mid-SSO-signup), RootView **reconstructs** a
synthetic pending-SSO state from the live Auth session and routes back into the location gate —
the only orphan-recovery mechanism found; there is no server-side sweep for this case.

### 2.3 Onboarding screen (`OnboardingView.swift`)

Strings: `"ATX Friends"`, `"Find Your People"`, `"Build meaningful friendships\nin Austin, TX"`,
three bullet lines ("Not a dating app — genuine friendships only", "Match based on activities &
availability", "Connect with neighbors in Austin"), buttons `"Sign in with Apple"`, `"Continue
with Google"`, `"or"`, `"Register"`, `"Sign In"`.

- **Register** → always through the geofence gate first: `LocationGateView(path: "email")`.
- **Sign In** → `SignInView()` directly — **existing users never see the location gate** (an
  explicit design decision documented in `LocationGateView`'s own header comment).
- Apple/Google buttons call the respective SSO helper, then `AuthViewModel.handleAppleSignIn` /
  `handleGoogleSignIn`.
- `OnboardingView` reads `AuthViewModel` via `@Environment`, deliberately the **same instance**
  `RootView` owns (injected via `.environment(authViewModel)`) — this closes a race where a fresh
  local view model would let `ProfileSetupFlowView` flash before the location gate for a brand-new
  SSO user.

### 2.4 Email sign-in / sign-up

**`SignInView.swift`**: `"Welcome Back"`, email + password fields, `"Forgot Password?"` →
sheet (`"Reset Password"`, sends via Firebase, success alert `"Password Reset Email Sent"`), `"Sign
In"` button, Apple/Google buttons, and — on auth failure — a `"New here? Create an account"`
prompt that itself routes through `LocationGateView(path: "email")` (i.e. even a failed sign-in
funnels toward the same gated signup path as fresh Register).

**`SignUpView.swift`**: takes a **required** `coordinate: CLLocationCoordinate2D` init parameter
— i.e. this screen is only ever reached after the location gate has already captured/passed a
coordinate. Fields: email, password (placeholder "At least 6 characters"), confirm password.
Explanatory copy: `"After creating your account, you'll set up your profile with 3 photos, 3+
activities, and 3+ time slots."` (note: this copy is itself more accurate than CLAUDE.md). ToS/
Privacy line is explicitly commented `// placeholder` in source — no real links wired.

**Validation** (`AuthViewModel.swift`): sign-up requires a valid email
(`[A-Z0-9a-z._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,64}`), password ≥6 chars, password==confirm.
Sign-in requires valid email + non-empty password. Error-message mapping (`AuthErrorMessage.swift`)
deliberately gives `.invalidCredential`/`.wrongPassword`/`.userNotFound` the **same** message
(`"Email or password is incorrect."`, with `suggestsAccountCreation = true`) to avoid leaking which
emails exist.

### 2.5 SSO two-phase flow (`pendingNewSSOUser`)

`AuthViewModel.handleAppleSignIn`/`handleGoogleSignIn` sign in via Firebase first (Auth session
exists immediately either way), then branch on Firebase's own
`authResult.additionalUserInfo?.isNewUser` flag:

- **New user**: sets `pendingNewSSOUser = PendingNewSSOUser(userID, displayName, provider: "apple"
  | "google")`. **No Firestore user doc is created yet.** RootView sees this and shows
  `LocationGateView` instead of `ProfileSetupFlowView`.
- **Returning user**: goes straight in (no extra checks; there is no scheduled-deletion state to
  cancel — account deletion is immediate, §2.8).

**Gate pass** → `AuthViewModel.createNewSSOUser(coordinate:)` creates the initial
`FirebaseUser(photoURLs: [], activities: [], daySlotCombos: [], radiusMiles: 10.0,
isProfileComplete: false)` via `createUser`, then clears `pendingNewSSOUser`.

**Abandonment cleanup** — `cancelNewSSOSignup()` calls `FirebaseAuthService.deleteAccount()` (the
Firebase Auth identity itself, not just Firestore), clears local state, posts
`.authStateDidChange`. Triggered by: tapping "Back" on the location gate; "Not now" on
`LocationPermissionNeededView`; "Not now"/"Done" on `WaitlistView` (which **also auto-fires this
after a 2s delay following a successful waitlist submission**, even if the user never taps
anything, explicitly to guarantee the orphaned Auth account gets deleted); or "Cancel" on
RootView's "Account Error" alert. **No server-side sweep exists for orphans that never trigger any
of these** (e.g. the user just kills the app) beyond the client-side reconstruction-on-relaunch
described in §2.2.

### 2.6 Geofence gate (`LocationGateView.swift`)

- Threshold: **50.0 miles**, verified in code — `austinGateMaxMiles: Double = 50.0`
  (`LocationGateView.swift:15`), compared against `CLLocationCoordinate2D.austin = (30.2672,
  -97.7431)` via `CLLocation.distance(from:) / 1609.34`.
- Shown only on the Register/new-SSO-user path, never for existing-user sign-in.
- Strings: `"One quick check"`, `"ATX Friends is Austin-only for now — we need to confirm you're
  nearby before creating your account."`, `"Your Privacy"` / `"We only check your location once,
  at signup. We don't track you after that."`, buttons `"Continue"` / `"Back"`.
- Pass (≤50mi): email path sets a captured coordinate and shows `SignUpView`; SSO path calls
  `onGatePass(coordinate)` → `createNewSSOUser`.
- Fail (>50mi): shows `WaitlistView`.
- Permission wrapper (`LocationPermissionManager.swift`): `kCLLocationAccuracyReduced`, no
  background updates, 12-second fetch timeout, `.locationUnknown` treated as transient/retryable.

**`LocationPermissionNeededView.swift`** (denied/restricted): `"We need your location"` + one of
two bodies depending on `.restricted` vs `.denied`; button `"Open Settings"` (hidden when
`.restricted`) deep-links to the iOS Settings app; `"Not now"` triggers the same abandonment
cleanup as above. Explicitly framed as "not a rejection" in the code's own comment.

**`WaitlistView.swift`** (failed the 50-mile check): `"ATX Friends is Austin-only for now"` /
explanatory copy → email field → `"Notify me when you expand"`. On submit, writes to Firestore
**`waitlistSignups/{auto-id}`**: `{email (lowercased+trimmed), submittedAt: serverTimestamp()}` —
**no Firebase Auth account is created for the waitlist itself.** Success state: `"You're on the
list!"` / `"We'll email you when ATX Friends launches in your area."`; auto-dismisses (triggering
SSO-account cleanup) 2 seconds after submit even without a tap.

### 2.7 Profile setup (`ProfileSetupFlowView.swift`)

5-step machine: `name(0) → photos(1) → activities(2) → timeSlots(3) → complete(4)`. Progress bar
denominator is hardcoded 4 and hidden entirely on the complete step.

1. **Name** (`NameInputView`): display name 2–30 chars (counter shown), optional bio, hard-capped
   at 150 chars by silent truncation (not rejection). Error: `"Please enter a name (2-30
   characters)"`. No functional Back button on this first step.
2. **Photos** (`PhotoPickerView.swift`): exactly 3, `PhotosPicker(maxSelectionCount: 3 -
   selected.count)`, client-side resize to max 1024px longest side. `"Loading photos..."` while
   processing. Full-screen preview supports pinch-zoom + double-tap reset.
3. **Activities** (`ActivityPickerView.swift`): first 3 selections auto-become "Main"
   (`isPrimary`); removing a Main auto-promotes the next Extra; tapping a star on an Extra swaps
   it with the last-ordered Main. Cap 10 total (`"You can only select up to 10 activities."`).
   Adding a custom activity normalizes whitespace/case to reuse an existing near-duplicate before
   creating a new global `activities` doc (requires a category).
4. **Time slots** (`TimeSlotPickerView.swift`): 7×5 grid, minimum 3, no maximum.
5. **Complete** (`ProfileCompleteView.swift`): summary + `"You're All Set!"` → button `"Enter ATX
   Friends"` (shows `"Saving..."` while in flight). Save sequence:
   1. `saveProfileCriticalData()` writes the `FirebaseUser` doc with `isProfileComplete: true`
      **before** photo upload finishes (`photoURLs` may still be stale/empty for a few seconds —
      a real, code-confirmed sequencing quirk, see §11).
   2. Posts `.authStateDidChange` (→ RootView routes to `MainTabView`) and `.onboardingCompleted`
      (consumed by `MatchesView` to reload).
   3. `Task.detached` uploads the 3 photos in the background, then updates `photoURLs` afterward.
   4. Failure → alert `"Save Error"`.

**Validation contract, everywhere it's enforced identically** (`ProfileViewModel.validateProfile`,
`User.swift`'s local mirror): name 2–30 chars; exactly 3 photo URLs; 3–10 activities with exactly
3 flagged Main; ≥3 day/slot combos; a non-nil location.

### 2.8 Sign-out and account deletion

- **Sign out** (`AuthViewModel.signOut()`): `Auth.auth().signOut()` only. No Firestore cleanup, no
  local SwiftData reset, no explicit call inside this method to remove the FCM token (that happens
  reactively elsewhere via `.authStateDidChange`). **`GoogleSignInHelper.signOut()` is defined but
  never called from here** — Google's own cached Calendar session survives an app sign-out (real
  gap, confirmed by exhaustive grep — its only caller is unused).
- **Account deletion** (Settings → Privacy & Safety → Delete Account, §1.6) is **immediate and
  permanent — there is no grace period and no scheduled/soft deletion.** Everything the user
  created is deleted, including both sides of their message threads; the other person's thread
  with them simply disappears. Client flow (`PrivacyAndSafetyViewModel.deleteAccount()`):
  1. **Sign in with Apple accounts only** (`providerData` contains `apple.com`): re-authenticate
     with Apple (`ASAuthorizationAppleIDProvider` request) to get a fresh authorization code, then
     `Auth.auth().revokeToken(withAuthorizationCode:)` — Apple requires revoking the app's Apple
     sign-in on account deletion. If the user dismisses the Apple sheet, deletion is aborted
     ("Deletion cancelled…"). If revocation itself fails (e.g. not configured in Firebase), it is
     logged and deletion **continues**. Android equivalent: revoke via the Apple token as Firebase
     documents for Android, or skip if Apple sign-in isn't offered there.
  2. Call the callable Cloud Function **`deleteMyAccount`** (no arguments; §4). It deletes
     everything server-side, then the Firebase Auth user, and returns `{ planIDs: [String] }` — the
     1-on-1 plan and group-plan IDs the user was part of.
  3. On success only: forget this device's FCM token (`NotificationManager.handleAccountDeleted()`
     — clears the in-memory user/token and calls `Messaging.deleteToken()`, so the old token is
     never re-registered; the server already removed `fcmTokens` with the users doc), clear local
     UserDefaults for the user (`addedToCalendar.{provider}.{planID}` for every returned plan ID
     and every provider, and `simpaticoV2Position.{uid}`), `GIDSignIn` sign-out, Firebase
     `signOut()`, then post `.authStateDidChange` → RootView returns to the onboarding screen.
  4. On failure: show the error, stay signed in, nothing local is cleared. Retrying is safe.
- The old client-side paths are gone: `AuthViewModel.deleteAccount()` (users doc + photos + Auth
  only), `PrivacyAndSafetyViewModel.scheduleAccountDeletion()`, `cancelScheduledDeletionIfNeeded`,
  and the `scheduledDeletionDate`/`isScheduledForDeletion` model fields. Nothing writes those
  fields anymore; they may still exist on old production user docs and should be ignored.
  (`FirebaseAuthService.deleteAccount()` — deleting only the Auth identity — still exists solely
  for abandoning a brand-new SSO signup before any user doc exists, §2.5.)

---

## 3. Data model (Firestore)

All one-time reads use `getDocument(s)`; "realtime" means an active `addSnapshotListener`. Field
lists reflect what the Swift models declare and what write paths actually populate — divergences
are called out explicitly.

### `users/{userID}` — model `FirebaseUser` (`Avenue3/UserModel.swift`)

| Field | Type | Required (rules) | Notes |
|---|---|---|---|
| displayName | String | yes | |
| photoURLs | [String] | yes (array) | intended exactly 3; not array-bounded by the type |
| activityIDs / activityNames / activityIsPrimary | [String]/[String]/[Bool] | activityIDs yes | flattened arrays; `isPrimary` missing on legacy docs defaults every activity to Main on read |
| daySlotCombos | [String] `"Day_Slot"` | yes | e.g. `"Monday_Night"`, `"Saturday_Wake Up"` (slot name keeps its internal space; day/slot delimiter is `_`) |
| latitude, longitude, location (GeoPoint) | Double/Double/GeoPoint | yes | **always coarse-snapped** to 2 decimals (~0.7mi grid) before write, at every write path, including the very first signup write |
| radiusMiles | Double | yes | default 10.0 |
| createdAt, updatedAt | Timestamp | yes | |
| isProfileComplete | Bool | yes | |
| notificationPreferences | map{newMatches,newMessages,planRequests,planConfirmations,groupUpdates: Bool} | optional | defaults all-true on read |
| blockedUsers | [String] | optional | default `[]` |
| bio | String | optional | default `""` |
| showUpThumbsUp, showUpTotal | Int | optional | default 0; **client never writes these** — Admin-SDK-only via the `applyShowUpReport` Cloud Function |
| locationSharingMode | String enum (`off`\|`once`\|`onOpen`) | optional | default `"off"` |
| locationUpdatedAt | Timestamp | optional | nil until first coarse-location write |
| fcmTokens | [String] | — | not in the Swift model; written only via raw dict methods / Cloud Functions |
| fcmToken (legacy), fcmTokenUpdatedAt | String / Timestamp | — | legacy single-token field, migrated into `fcmTokens` |
| unreadCount | Int | — | server-mirrored copy of the client-computed badge count |
| email | String | — | **on the Swift model but absent from the actual write dict** (`userToFirestoreData`) — likely never persisted despite existing on the type |
| scheduledDeletionDate, isScheduledForDeletion | Date?/Bool | — | **legacy, no longer written or read** — may exist on old docs from the removed 30-day scheduled-deletion flow; ignore (§2.8) |

Reads: one-time only, everywhere (`FirestoreService.swift` has zero `addSnapshotListener` calls).
Writes: `createUser` (create), `updateUser` (update, merge), plus narrow single-purpose updates —
`updateUserLocation`, `updateLocationSharing`, `addFCMToken`/`removeFCMToken`, `setUnreadCount`,
`updateNotificationPreferences`. Any signed-in user may read any **complete** profile in full
(`resource.data.isProfileComplete == true`) — Firestore has no field-level security, which is why
location is pre-coarsened before it ever reaches this document.

### `activities/{activityID}` — model `Activity`

`name` (String, 1–50 chars, required), `isUserAdded` (Bool, required), `createdAt` (Timestamp,
required), `category` (String enum, optional, only on user-added), `needsReview` (Bool, optional,
only on user-added). Write-once/append-only by rules (`allow update, delete: if false`). Seeded
once from a fixed 284-name list (`ActivitiesDatabase.swift`) if the collection is empty.

### `matches/{matchID}` — model `Match`

`user1ID, user2ID` (String, required), `user1Decision, user2Decision` (Bool?, optional),
`isMutualMatch` (Bool, required), `createdAt, updatedAt` (Timestamp, required),
`overlappingActivityNames` ([String]), `overlappingDaySlots` ([String]),
`overlappingCategoryNames` ([String], optional/back-compat). Deterministic doc ID
`"{min(uid1,uid2)}_{max(uid1,uid2)}"`. Rules restrict updates to exactly
`{user1Decision,user2Decision,isMutualMatch,updatedAt}`. One-time fetch only.

### `plans/{planID}` — model `Plan` (the live 1-on-1 proposal, embedded in Messages — **not** the
dead standalone Plans screens, see §10)

`matchID, proposerID, receiverID` (String, required), `activity` (embedded `Activity`, required),
`location`/`locationName`/`locationLatitude`/`locationLongitude` (optional — `location`'s free-text
field is read verbatim by all 3 calendar integrations; source comment: "never rename"),
`proposedDates` ([Date], required — in practice always exactly 1 element), `status` (String enum
`PlanStatus`, required — raw values **`pending`, `counter`** (Swift case name `counterProposed`;
Cloud Functions reference a named constant, never the literal, to avoid drift), **`confirmed`,
`declined`, `cancelled`**), `confirmedDate` (Date?), `createdAt, updatedAt` (Date, required),
`counterProposedDates` ([Date]?, in practice always ≤1 element), `isViewed` (Bool, write-only via
`markPlanViewed`, UNCONFIRMED whether ever decoded back). Rules: proposer creates; proposer or
receiver may update any field; delete never allowed (lifecycle is status-driven).

### `messages/{messageID}` — model `Message` (flat top-level collection, filtered by `matchID` or
`eventID` field — no subcollections; the separate `conversations` collection described below is
dead)

`matchID` (String, required, empty for event messages), `eventID` (String?, optional),
`senderID, receiverID` (String, required), `text` (String, 1–5000 chars, required), `sentAt`
(Date, required), `isRead` (Bool, required), `kind` (String enum `text`\|`planProposal`, defaults
`text` on legacy docs missing the field), `planID` (String?, set only when `kind==planProposal`).
Rules: sender creates; **only the receiver** may update, and only to flip `isRead→true`
(field-diff restricted). No pagination anywhere — full history streams on every thread open via
both a one-time fetch and a `.addSnapshotListener`, unordered by any `.limit`.

### `todayPlans/{planID}` — model `TodayPlan`

`creatorID` (String, required), `activity` (embedded `Activity`, required), `scheduledTime`
(Date, required), `note` (String?, optional), `location`/`locationName`/`locationLatitude`/
`locationLongitude` (optional), `status` (String enum `open`\|`claimed`, required), `claimerID`
(String?, absent at creation, set on claim), `createdAt, updatedAt` (Date, required),
`creatorReportedClaimer`, `claimerReportedCreator` (Bool?, optional show-up verdicts — see the
decode bug in §11). Rules allow four distinct update shapes: claim (non-creator, exactly
`status+claimerID+updatedAt`), creator's `note` edit while open, and each side's own show-up-report
field while claimed — **but the live client never uses those last two update paths**; it always
submits show-up reports through the separate `showUpReports` queue collection, processed
server-side by `applyShowUpReport` (confirmed by reading `TodayPlanService.submitShowUpReport`,
which only ever writes to `showUpReports`). One-time fetch **and** a realtime listener (for
detecting "someone claimed your plan").

### `simpaticoAnswers/{userID}` — model `SimpaticoV2State`/`SimpaticoV2Answer`

Keyed directly by user ID (not auto-ID). `answers: {questionID: {answer: String (chosen option
ID), acceptable: [String] (option IDs, always includes the chosen answer), importance: String
enum? ("little"|"somewhat"|"very")}}`, `completedAt` (Date?). A separate, unread-by-v2 legacy
`answers` field variant also exists on some docs (exact shape UNCONFIRMED — not present in the v2
Swift model). Any signed-in user may read any user's doc (rules comment: needed so both match
participants can compute the score client-side; data is non-sensitive). Owner-only write. One-time
fetch only, no listener.

### `groupPlans/{planID}` — model `GroupPlan` (the live multi-invitee plan, used by Upcoming — do
**not** confuse with the dead `Group`/`GroupMember` stack, §10)

`hostID` (String, required, immutable after create), `inviteeIDs` ([String], required,
non-empty), `responses` (map `{userID: "invited"|"going"|"cantMake"}`, required, every invitee
starts `"invited"`), `activity` (embedded `Activity`), `location`/`locationName`/
`locationLatitude`/`locationLongitude` (optional), `date` (Date, required), `status` (String enum
`"active"`\|`"cancelled"`), `createdAt, updatedAt` (Date, required). Rules: host creates and can
change any field except `hostID`; an invitee may touch **only their own key** inside `responses`
plus `updatedAt`, and only to `"going"`/`"cantMake"`. No delete. No counter-proposal concept, no
messaging-thread tie-in — invitees only discover invites by opening the Upcoming tab (no push
notification for this yet, per an explicit code comment).

### `showUpReports/{reportID}` — no Swift Codable model, raw dict only

`reporterID, reportedUserID, planID` (String, required), `didShowUp` (Bool, required),
`createdAt` (required). Write-once input queue for the `applyShowUpReport` Cloud Function; rules
deny **all** reads/updates/deletes — the client never reads its own submitted report back.

### `waitlistSignups/{docID}`

Exactly 2 fields, extras rejected by rules: `email` (regex-validated), `submittedAt`.
Unauthenticated writes allowed (pre-auth landing form). No reads allowed.

### `groups/{groupID}` + subcollection `groups/{groupID}/members/{userID}` — dead feature, still
partially implemented in `FirestoreService.swift` (§10). **No firestore.rules block exists for
this collection at all** — under Firestore's default-deny model, either production rules differ
from the checked-in file, or this dead code would fail outright against real rules if anything
ever called it. UNCONFIRMED which is true; flagged for George in §12.

### `events/{eventID}` — dead feature (§10). `name, heroImageURL, weekends: [{label, startDate,
endDate}]` (weekend number derived from array index, not stored).

### `conversations/{conversationID}` + subcollection `messages` — **fully dead**, no rules block,
zero call sites. Do not port; messaging is modeled on the flat `messages` collection above.

### Firebase Storage

Base path `profile_photos/{userID}/{userID}_photo_{index}.jpg`, index 0–2 (exactly 3). Resize: max
1920px on the longest side (skipped if already smaller), JPEG quality 0.85, explicit
`Content-Type: image/jpeg`. Parallel upload of all 3 via a `TaskGroup`; fails the whole operation
if any one of the 3 fails. No Storage security-rules file (`storage.rules`) was found in the repo
— UNCONFIRMED/unverified Storage-side authorization.

### Every string-serialized enum, all raw values

- `PlanStatus`: `pending`, `counter` (⚠ Swift case `counterProposed`, stored raw value is
  literally `"counter"`), `confirmed`, `declined`, `cancelled`.
- `MessageKind`: `text`, `planProposal`.
- `TodayPlanStatus`: `open`, `claimed`.
- `GroupPlanResponse`: `invited`, `going`, `cantMake`.
- `GroupPlanStatus`: `active`, `cancelled`.
- `LocationSharingMode`: `off`, `once`, `onOpen`.
- `ActivityCategory` (13): sportsAndFitness, outdoorAndNature, foodAndDrink, artsAndCulture,
  entertainmentAndSocial, musicAndPerformance, learningAndEducation, hobbiesAndCrafts,
  wellnessAndSelfCare, travelAndAdventure, communityAndVolunteering, professionalAndBusiness,
  uniqueAndNiche.
- `SimpaticoCategory`: hangingOut, socialStyle, lifestyle.
- `SimpaticoImportance`: little (weight 1), somewhat (weight 10), very (weight 50).
- `DayOfWeek`: Monday…Sunday (raw value = the display string itself).
- `TimeSlot`: `"Wake Up"`, `"Afternoon"`, `"Evening"`, `"Night"`, `"Owl Hours"`.
- Dead-feature enums (do not port): `Group.GroupRecurrence` (none/weekly/biweekly/monthly),
  `Group.GroupPrivacy` (public/invite_only), `Group.GroupStatus`
  (open/confirming/confirmed/canceled), `GroupMember.GroupMemberRole`
  (organizer/member/waitlisted/pending).

---

## 4. Backend touchpoints

### Cloud Functions (`functions/src/index.ts`) — Firebase Functions v1.
All are Firestore-triggered except **`deleteMyAccount`**, the only callable (HTTPS) function. No
scheduled/cron functions exist in this codebase.

1. **`onNewMutualMatch`** — trigger `matches/{matchId}` onUpdate, fires only on
   `isMutualMatch: false→true`. Notifies both users (each gated by their own
   `prefs.newMatches` and block status).
2. **`onNewMessage`** — trigger `messages/{messageId}` onCreate. Skips `kind === "planProposal"`
   (avoids double-notifying — that case is covered by #3). Gated by `prefs.newMessages` + block.
3. **`onNewPlanRequest`** — trigger `plans/{planId}` onCreate. Gated by `prefs.planRequests` +
   block.
4. **`onPlanConfirmed`** — trigger `plans/{planId}` onUpdate, fires on `status→"confirmed"`.
   Because Firestore triggers have no auth context, it infers who just confirmed from the *prior*
   status (`counter` → proposer confirmed; anything else → receiver confirmed) and notifies the
   other party. Gated by `prefs.planConfirmations` + block.
5. **`applyShowUpReport`** — trigger `showUpReports/{reportId}` onCreate. The only function with
   real validation: checks the referenced `todayPlans` doc is `claimed`, past its scheduled time,
   and that the reporter/reportedUser pairing is legitimate; is idempotent (won't double-apply if
   the target field is already set); then in one atomic batch sets
   `todayPlans/{id}.{creatorReportedClaimer|claimerReportedCreator}` and increments the reported
   user's `showUpTotal` (+1) and, if `didShowUp`, `showUpThumbsUp` (+1). **Sends no push
   notification.** This function — not any client code — is the only writer of
   `showUpTotal`/`showUpThumbsUp`.

6. **`deleteMyAccount`** — **callable** (`https.onCall`), region `us-central1`. Deletes only the
   caller's own account, identified by `context.auth.uid`; takes no input. Unauthenticated calls
   are rejected with `unauthenticated`. Runs with admin access, so **no security rules were
   loosened** — clients still cannot delete other users' data directly. Deletes, in order:
   - every `messages` doc in any of the user's matches (queried by `matchID`, 30 IDs per `in`
     query), plus every message where they are `senderID` or `receiverID` — flushed first, so a
     partial failure can still find the rest;
   - every `matches` doc where they are `user1ID` or `user2ID`; every `plans` doc where they are
     `proposerID` or `receiverID`; every `todayPlans` doc they created (`creatorID`) or claimed
     (`claimerID`); every `groupPlans` doc they host (`hostID`); every `showUpReports` doc they
     filed (`reporterID`) or were the subject of (`reportedUserID`);
   - `groupPlans` they're **invited** to are kept for everyone else: the user is removed from
     `inviteeIDs` (`arrayRemove`) and their key is deleted from `responses`. (Nothing else changes,
     even if that leaves the plan with no invitees.);
   - `simpaticoAnswers/{uid}` and `users/{uid}` (which also removes their `fcmTokens`);
   - every file under Storage `profile_photos/{uid}/`;
   - **last**, the Firebase Auth user (Admin SDK; `auth/user-not-found` is treated as success).
   Other users' `showUpTotal`/`showUpThumbsUp` counts are **not** adjusted. Writes go through a
   Firestore `BulkWriter` (handles batch limits and retries); if any write ultimately fails it
   throws `internal` so the client can retry. **Safe to retry**: every step tolerates data that's
   already gone. Logs one line of counts only (no personal data). Returns `{ planIDs }` (§2.8).
   Emulator-tested in `firestore-tests/deleteAccount.test.js` (`npm run test:functions`).

Shared helper `sendNotification`: increments the recipient's `unreadCount` optimistically, sends
one FCM message per registered token with `aps.badge` set to that new count, and prunes any token
that comes back `registration-token-not-registered`/`invalid-registration-token`; rolls the
increment back if every token failed.

### Client assumptions that rules enforce (Android must match exactly)

- `matches` update: diff must be `hasOnly([user1Decision, user2Decision, isMutualMatch,
  updatedAt])` — writing any other field is rejected outright.
- `messages` update: only the **receiver**, only `hasOnly([isRead])` with `isRead == true`.
- `todayPlans` claim: non-creator, `status` must currently be `"open"`, new write must be
  `hasOnly([status, claimerID, updatedAt])` with `claimerID` absent beforehand.
- `groupPlans` invitee response: outer diff `hasOnly([responses, updatedAt])` **and** the nested
  `responses` diff must touch only the invitee's own key, value `"going"`/`"cantMake"`.
- `plans`/`messages` **list** queries are not scoped by rules (rules can't filter a query) — the
  practical access control is "always query by a known matchID/proposerID/receiverID," never an
  unscoped list.
- `activities`: create requires `name` 1–50 chars; update/delete are always denied — "editing" a
  name means creating a new doc.
- `waitlistSignups`: create requires exactly 2 keys; extra fields are rejected outright.
- **Gap, not to replicate**: `groups`/`groupMembers` and `conversations` have **no** rules block
  at all in the checked-in `firestore.rules`. Treat both as unverified — do not build an Android
  equivalent against them without first confirming what's actually deployed in production (§12).

---

## 5. Business logic the client computes

### 5.1 Matching (`MatchingService.swift`)

```
shouldMatch(u1, u2) =
    (hasOverlappingActivities(u1,u2) || !sharedActivityCategories(u1,u2).isEmpty)
    && hasOverlappingTimes(u1,u2)
```

- **Exact activity overlap**: matched by activity `id` first, falling back to a
  whitespace/case-normalized name comparison for legacy duplicate entries.
- **Time overlap**: plain set intersection of each user's day/slot combos — any single shared
  combo suffices.
- **Category overlap**: `ActivityCategories.category(for:)` is a static, exact-string lookup of
  ~284 predefined names → one of 13 categories. A custom (non-predefined) activity name returns
  nil and **never participates in category matching** — confirmed by a dedicated unit test.
- **Label rule**: the UI shows a category label (`"You both like {category}"`) **only when there
  is no exact-activity chip to show instead** — exact matches always take display priority over
  category matches.
- Match doc creation is deduped by a deterministic ID scheme
  (`"{min(uid1,uid2)}_{max(uid1,uid2)}"`), reused by `TodayPlanService.claimTodayPlan` so a
  Today-tab claim and an independently-computed "real" match on the same pair resolve to the same
  doc.
- On every load, `MatchesViewModel` re-checks `shouldMatch` against the two users' **current**
  profiles and hides (never deletes) any match that no longer overlaps — a pure display filter;
  editing a profile back into overlap brings it back.
- A separate `MatchStatistics`/`calculateMatchScore`/`qualityDescription` scoring path exists in
  `MatchingService` but is **not used anywhere in the live UI** — the live app shows raw
  overlapping-name/category lists, not a score.

### 5.2 Simpatico scoring (`SimpaticoModels.swift`)

12 fixed questions across 3 categories (hangingOut, socialStyle, lifestyle) — full text is in
`SimpaticoQuestionBank.all`. Each user, per question, picks exactly one `answer` and a set of
`acceptable` option IDs (always including their own answer) plus an optional `importance`.

- **Per-answer weight**: `importance.weight` (little=1, somewhat=10, very=50) — but **0** if the
  user marked every option acceptable ("doesn't matter"), regardless of the importance value
  stored.
- **Per-person satisfaction ratio** (for user A, rated by B's answer): sum the weight of every
  shared question where B's chosen answer is in A's acceptable set, divided by the sum of all
  weights for those shared questions; if total weight is 0 (all "doesn't matter"), satisfaction is
  defined as 1.0.
- **Score requires ≥5 shared answered questions**, else the score is `nil` (badge hidden).
- **Combining both directions**: **geometric mean**, not an average —
  `score = round(sqrt(satisfactionA * satisfactionB) * 100)`. One person being fully unsatisfied
  drives the combined score toward 0 even if the other is 100% satisfied (verified by a unit
  test asserting the asymmetric case scores below 50).
- No per-match explanation text is ever shown — only the bare `"{score}%"` badge on the Matches
  tab connected-row.
- Scores are computed **client-side, on demand** (two one-time fetches, then local math) —
  never live/realtime, never server-computed/stored.

### 5.3 Today tab's "next 24 hours" + 15-minute lead time (`OpenSlotGenerator.swift`)

- `minimumLeadTime = 15 * 60` seconds — any candidate slot less than 15 minutes from "now" is
  dropped.
- `generateForToday(from: now)`: search window is exactly `[now, now + 86400s]`.
- If the user's own recurring day/slot combos don't fill the suggestion list to `maxCount`
  (default 3), fallback clock times `08:00 wakeUp / 12:00 afternoon / 19:00 evening` are tried at
  day-offset 0 then 1, keeping the first occurrence that lands inside the window (comment: "any
  24-hour window contains each of these exactly once").
- `TodayPlan.isExpired` = `scheduledTime < Date()`, re-evaluated on every read; used both as a
  server-query lower bound and a client-side filter.

### 5.4 Upcoming-tab multi-invitee plans (`GroupPlan`)

- Invitees are a flat `[String]` array on the plan doc, not a subcollection. Each invitee's
  individual response lives in a `{userID: response}` map, all defaulting to `"invited"`.
- **Host-controlled, not consensus-based** — there is no "all must accept" gate anywhere. The
  plan stays `"active"` regardless of how many invitees say `"cantMake"`; the only status
  transition is `active → cancelled`, host-only. `goingCount` is a pure display tally, never a
  logic gate.
- Invitee transitions freely between `invited`/`going`/`cantMake` — no one-way lock, no
  counter-proposal concept, no messaging-thread tie-in.
- Ghost-slot suggestions for Upcoming: generated tomorrow→+6 days, capped at 7, deduped to at most
  one per calendar day, and suppressed on any day that already has a real plan.

### 5.5 Plan statuses and transitions (1-on-1 `Plan`)

| From | To | Who | Trigger UI | Call |
|---|---|---|---|---|
| (new) | pending | proposer | `ProposePlanSheet(.proposal)` submit | `PlansService.createPlan` |
| pending | confirmed | receiver only | `PlanProposalCard` "Accept" | `confirmPlan(planID:, selectedDate: proposedDates.first)` |
| pending | declined | receiver only | `PlanProposalCard` "Decline" | `declinePlan` |
| confirmed | counter (`counterProposed`) | either party | Pinned card ⋯ → "Reschedule" → `ReschedulePlanSheet` → "Suggest New Time" | `counterPropose(newDates: [singleDate])` |
| confirmed | cancelled | either party | Pinned card ⋯ → "Cancel plan", or `UpcomingPlansSheet` row cancel | `cancelPlan` |
| counter | *(no live transition found)* | — | — | — |

**Confirmed gap**: once a plan is counter-proposed, it drops out of the "confirmed plans" listener
(pinned card disappears), the original `PlanProposalCard` reappears showing a `"Counter"` pill,
but its Accept/Decline buttons are gated to `status == .pending` only — there is **no live UI
action that re-confirms a counter-proposed plan or reads `counterProposedDates`** other than the
dead `PlanDetailView`. This looks like an incomplete reschedule loop, not an intentional dead end.

### 5.6 Distance buckets and Share My Location (`DistanceDisplay.swift`, `LocationSharingManager.swift`)

Three modes, raw values persisted verbatim: `off` ("Off"), `once` ("Update once"), `onOpen` ("When
I open the app").

- **Off**: only `locationSharingMode` is written; no coordinate touch. `isSharingLocation` is
  `false`, so `DistanceDisplay` shows **no distance line at all** to other users, regardless of
  whatever stale coordinate remains stored.
- **Update once**: switching in immediately requests permission (if needed) and writes one coarse
  fix; further updates only on an explicit "Update now" tap.
- **When I open the app**: same immediate write, plus a foreground-transition hook re-fetches and
  rewrites, throttled by **15 minutes since last write** and **0.5 miles minimum movement** from
  the last stored coordinate (both must be exceeded to write again).
- Permission is always `.authorizedWhenInUse` only — the manager's own comment states "Never
  request Always, never enable background updates." 10-second fetch timeout; on timeout/denial,
  mode reverts to `.off` with an alert.
- **Coarsening**: `CoarseLocation.snap()` rounds both lat/long to 2 decimal places (~0.7mi fixed
  grid, explicitly chosen over jitter because "jitter averages out over repeated updates and leaks
  the true spot, a fixed grid never does") — applied at every write path before the coordinate
  reaches Firestore.
- **Distance bucket thresholds** (`DistanceDisplay.swift`, exact):
  `<1mi → "Under 1 mi away"`, `<3mi → "~2 mi away"`, `<7mi → "~5 mi away"`, `<12mi → "~10 mi
  away"`, `<20mi → "~15 mi away"`, `<35mi → "~25 mi away"`, `<60mi → "~50 mi away"`, `≥60mi → "50+
  mi away"`. (The Search-Radius screen's own "Distance Guide" info card shows unrelated marketing
  copy with different numbers — don't conflate the two.)

### 5.7 Geofence radius math

Signup/registration gate: 50-mile straight-line distance from Austin city center (30.2672,
-97.7431), computed via `CLLocation.distance(from:) / 1609.34`. Separate from this: the
user-adjustable **matching search radius**, default 10.0 miles, UI-exposed range 5–25 miles
(1-mile steps) though the underlying setter clamps 1–50.

### 5.8 Sorting / filtering / dedup summary (cross-referenced from §1)

- Matches: matches whose users no longer overlap are hidden (not deleted) on every load.
- Today: client-side activity-name filter only; server query lower-bounds by non-expired time.
- Upcoming: `active && date > now && (host || invitee)`, ascending by date; ghost slots deduped to
  1/day and suppressed where a real plan exists.
- Messages: thread list is **re-sorted purely by `lastActivityDate` descending** in the View,
  which overrides the ViewModel's own "plan-priority, else most-recent-message" sort — the View's
  sort is what actually renders.

---

## 6. Platform APIs and third-party integrations

### MapKit place search — `LocationSearchViewModel.swift` (the "Where?" component)

`MKLocalSearchCompleter` (`resultTypes: [.pointOfInterest, .address]`), region-biased to a 20km ×
20km box around the user's coordinate (skipped if the coordinate is the app's `(0,0)` "unset"
sentinel). Resolves the **first 8** completions in parallel via `MKLocalSearch` (deliberately more
than the final count, since resolution can fail or reorder), then **explicitly re-sorts by
distance** from the user (MapKit's own order is relevance, not distance), and caps the final list
at **5** results. States: `idle | searching | hasResults | noMatches | error`. Address line is
built from `thoroughfare` + `locality` only (no full formatted address).

### Calendar integrations

- **EventKit (Apple)** (`EventKitPermissionManager.swift`): requests **write-only** access only
  (iOS 17+ `requestWriteOnlyAccessToEvents`), never read/full access.
- **Google Calendar** (`GoogleCalendarService.swift`): reuses an existing `GIDSignIn` session if
  present (incrementally adds the `calendar.events` scope) or does a fresh interactive Google
  sign-in for that scope — explicitly documented as **never** touching Firebase Auth; this session
  is calendar-only. `POST .../calendars/primary/events` with `summary, start.dateTime,
  end.dateTime, description, location (if non-empty)`. OAuth client ID is read from
  `FirebaseApp.app()?.options.clientID` (i.e. from `GoogleService-Info.plist`), not a separate key.
- **Microsoft/Outlook via MSAL** (`MicrosoftCalendarService.swift`): **always** a fresh interactive
  sign-in — the app has no persistent Microsoft login anywhere else. Authority
  `login.microsoftonline.com/common`, scope `Calendars.ReadWrite`. Client ID is hardcoded as a
  source constant (`MicrosoftCalendarService.swift:40` — name only, not the value, per
  instructions). `POST graph.microsoft.com/v1.0/me/events` with `subject, start/end
  {dateTime,timeZone:"UTC"} (custom "yyyy-MM-dd'T'HH:mm:ss" formatter, no offset), body.content
  (notes), location.displayName (if non-empty)`.
- **"Already added" tracking**: local-only, per-device, per-(plan, provider) — `UserDefaults` key
  `"addedToCalendar.{provider}.{planID}"` via `AddedToCalendarStore`
  (`PlanCalendarActionHandler.swift`), explicitly never synced to Firestore because event IDs
  aren't stable across devices/resyncs. Re-tapping an already-added provider deep-links into that
  provider's calendar instead of re-creating the event. All three providers share one
  `CalendarEventFields` shape: `title, start, location, notes`; `end` is always computed as
  `start + 2 hours` (no explicit end time is ever asked for or stored).

### Navigation / directions picker

`NavigationApp` enum (`PlanCalendarActionHandler.swift:43-104`): `apple | google | waze`. Apple
Maps is the guaranteed system fallback (no install probe); Google Maps and Waze are offered only
when `UIApplication.canOpenURL` succeeds against their custom scheme (`comgooglemaps://`,
`waze://`). Deep-link formats, exact:
- Apple: `http://maps.apple.com/?daddr={lat},{lng}` (or `?daddr={url-encoded query}` when no
  stored coordinate exists).
- Google: `comgooglemaps://?daddr={lat},{lng}&directionsmode=driving` (or `&daddr={query}` form).
- Waze: `waze://?ll={lat},{lng}&navigate=yes` (or `waze://?q={query}` form).

Fallback logic: exact coordinates are preferred when the plan/group-plan has them stored;
free-text query is the fallback for plans predating stored coordinates.

### Sign-in SDKs

- **Sign in with Apple**: `ASAuthorizationAppleIDProvider`, scopes `[.fullName, .email]`, nonce
  generated via `SecRandomCopyBytes` (raw nonce → Firebase, SHA256 hash → Apple).
  `presentationAnchor` will `fatalError` if no window scene is found (a real, if unlikely, crash
  path).
- **Google Sign-In**: `GIDSignIn`, client ID pulled from `Auth.auth().app?.options.clientID`.
  `GoogleSignInHelper.signOut()` exists but — per §2.8 — is never actually called by the app's own
  sign-out flow.
- **MSAL** (Microsoft): used exclusively for Calendar, never for primary app sign-in.

### Photo picking / camera

`PHPickerViewController` via SwiftUI's `PhotosPicker` (both onboarding `PhotoPickerView` and
Settings' `PhotosSettingsView`) — no camera capture path was found anywhere in the traced files.
`PHPicker` doesn't require an `NSPhotoLibraryUsageDescription` string, which is consistent with
none being present in `Info.plist`.

### Info.plist — permission strings, URL schemes, query schemes (names/values only, no secrets)

- `NSLocationWhenInUseUsageDescription`: "ATX Friends uses your location to confirm you're in the
  Austin area, and — only if you turn on location sharing in Settings — to show your approximate
  distance to matches."
- `NSCalendarsWriteOnlyAccessUsageDescription`: "ATX Friends adds your confirmed plans to your
  calendar when you ask it to."
- **No** `NSPhotoLibraryUsageDescription`, `NSCameraUsageDescription`,
  `NSContactsUsageDescription`, or `NSUserTrackingUsageDescription` exists.
- `CFBundleURLTypes` (two schemes): the Google Sign-In reversed-client-ID scheme, and
  `msauth.com.georgeAppDev.Avenue3` (MSAL redirect).
- `LSApplicationQueriesSchemes`: `msauthv2`, `msauthv3`, `comgooglemaps`, `waze`.
- `INFOPLIST_KEY_CFBundleDisplayName` = "ATX Friends" (injected via build settings, not the static
  plist).

---

## 7. Push notifications

Registration: FCM tokens stored as an **array** field `fcmTokens` on the user doc (multi-device —
`arrayUnion`/`arrayRemove`, never overwrite-by-index). A legacy single-token field `fcmToken` is
migrated into the array once, both client-side (`migrateLegacyFCMTokenIfNeeded`) and defensively
re-folded server-side inside the Cloud Functions' shared `getUserData` helper for clients that
haven't relaunched since migrating. Removed on sign-out (this device's token only, via
array-remove). Foreground presentation is suppressed to badge-only (no banner/sound) when the
incoming message's `matchID` matches the thread currently open on screen, to avoid double-showing
a message that will also arrive live in that open thread.

| Type (`data.type`) | Trigger (Cloud Function) | Title / body | Payload | Tap destination | Status |
|---|---|---|---|---|---|
| `newMatch` | `onNewMutualMatch` | `"New Match! 🎉"` / `"You and {name} both said Yay!"` | `type, matchID, otherUserId` | opens the match's thread; falls back to the Matches tab if the thread isn't resolvable yet | live |
| `newMessage` | `onNewMessage` | sender's display name / message text truncated to 60 chars + "…" | `type, messageId, senderId, matchID` | opens the thread (no fallback) | live |
| `planRequest` | `onNewPlanRequest` | `"New Plan Request! 📅"` / `"{proposer} wants to {activity} with you!"` | `type, planId, proposerId, matchID` | opens the thread | live |
| `planConfirmed` | `onPlanConfirmed` | `"Plan Confirmed! ✅"` / `"{confirmer} confirmed your {activity} plan. It's on!"` | `type, planId, matchID` | opens the thread | live |

Notably **absent** (no trigger, no client handling found): a "1-hour-before-plan" reminder. There
is no scheduled/cron Cloud Function in the codebase at all, so nothing currently generates
time-based reminder pushes — **this feature is planned/not started**, not partial. Similarly,
`GroupPlan` invites have **no push notification of any kind** (explicit code comment on
`GroupPlan.swift`/`UpcomingView.swift`: invitees only discover invites by opening the tab).

Badge count: the number shown comes from the server (computed inside `sendNotification` from the
Firestore `unreadCount` field it just incremented), not recomputed client-side from the push
payload itself; the client's own `UnreadState` separately recomputes and mirrors the same number
back to Firestore on every relevant snapshot (see §1.5/§8).

---

## 8. Local, on-device state

No custom Keychain wrapper exists in app code — session persistence is entirely delegated to
Firebase Auth's and `GIDSignIn`'s own internal Keychain handling (nothing to port 1:1 beyond
"persist the equivalent Android SDKs' own session tokens"). No `FileManager` cache-file writes
exist anywhere in the app's source. All local state is plain `UserDefaults.standard` scalars:

| Key | Type | Written | Cleared | Purpose |
|---|---|---|---|---|
| `hasRequestedNotificationPermission` | Bool | after the OS permission prompt is shown once | never | avoid re-prompting the OS dialog |
| `hasBeenAskedForNotifications` | Bool | after the in-app "Stay Connected!" soft-ask card is dismissed/actioned | never | separate from the key above — tracks the app's own soft-ask UI, not the OS dialog |
| `addedToCalendar.{provider}.{planID}` | Bool | when a plan is added to that calendar provider | on account deletion, for every plan ID returned by `deleteMyAccount` (§2.8) | per-device, per-provider "already added" flag (§6) |
| `simpaticoV2Position.{userID}` | Int | on every Simpatico Next/Skip | on completing the last question, and on account deletion | resume cursor for the question flow, since Firestore doesn't record skipped questions |

---

## 9. Design system

### Colors

Fixed (same Light/Dark, hardcoded, `AppColors.swift`): `appPrimary` = `#BF5700` (UT Burnt Orange),
`appNavy` = `#1B2A47` (UT Deep Navy).

Asset-catalog-driven (Light / Dark, both fully specified for every token):

| Token | Light | Dark |
|---|---|---|
| AppBackground | `#FAF6EE` | `#12151C` |
| Border | `#D9D9D9` | `#3A3F4A` |
| CardBackground | `#FFFFFF` | `#1E232C` |
| Danger | `#D97366` | `#E2857A` |
| PositiveCardBg | `#EDF7ED` | `#18221A` |
| PrimaryText | `#1B2A47` | `#F1EDE3` |
| PromptCreamBg | `#FCF2DB` | `#262014` |
| SecondaryText | `#808080` | `#A8A29B` |
| TextBody | `#666666` | `#B9B9B9` |
| TextFaint | `#A6A6A6` | `#838383` |
| TextHeavy | `#404040` | `#D9D9D9` |
| TextMedium | `#737373` | `#AEAEAE` |
| TextMuted | `#999999` | `#8E8E8E` |
| TextStrong | `#595959` | `#C4C4C4` |
| TextSubtle | `#B3B3B3` | `#787878` |
| TextTertiary | `#8C8C8C` | `#999999` |

Plus ~15 fixed status/badge accent colors with no Dark variant (positive-green, declined-red,
cancelled/counter/pending/prompt tints, icon-warm/info/safe, sign-in-button colors, Google-logo
colors) — see `AppColors.swift` for the full list; these are intentionally not theme-adaptive.
`AccentColor` is an empty/default colorset (no explicit tint set).

### Typography

**No reusable typography/type-scale file exists** — every screen calls
`.font(.system(size:weight:design:))` with literal, repeated values; every weighted call
consistently uses `design: .rounded`. The most common combinations (by frequency across the
codebase) are `15pt semibold rounded`, `14pt semibold rounded`, and `16pt medium rounded` — treat
these as the de facto default/emphasis/body sizes when inferring an Android type scale, since
there is no named token to port directly.

### Layout / components

- `FlowLayout.swift` — a custom SwiftUI `Layout` that wraps children left-to-right, dropping to a
  new line on overflow (CSS `flex-wrap` equivalent), used for activity/time-slot chip clouds.
  Android equivalent: a Compose `FlowRow`/custom flow layout.
- Reusable pieces referenced throughout §1: `SettingsCard`/`SettingsRow` (Settings), `GhostSlotCardView`
  (solid style on Today, dashed style on Upcoming), `AvatarRing` (photo+ring+initials fallback,
  used in Messages), `PlanProposalCard`, `PlanLocationField`.

### Custom tab bar

Fully custom (`CustomTabBar`, not `TabView`), sliding-indicator `HStack` of `CustomTabItem`s.
Exact order, label, and SF Symbol pair (unselected/selected):

| # | Label | Unselected icon | Selected icon |
|---|---|---|---|
| 1 | Matches | `person.2` | `person.2.fill` |
| 2 | Today | `calendar.circle` | `calendar.circle.fill` |
| 3 | Upcoming | `calendar.badge.clock` | `calendar.badge.clock` (same both states) |
| 4 | Simpatico | `face.smiling` | `face.smiling.fill` |
| 5 | Messages | `message` | `message.fill` |
| 6 | Settings | `gear.circle` | `gear.circle.fill` |

Only Messages ever shows a badge (a small red dot, not a number) — driven by
`unreadState.hasUnreadMessages`. No other tab is wired to show a badge.

### App icon and image assets

`AppIcon.appiconset` — a single multiplatform icon set (iOS 20–1024pt, macOS 16–512pt, watchOS
22–1024pt, all @1x/@2x/@3x as applicable per platform). No other named image assets beyond the
colorsets and app icon were found; the tab-bar/UI icons are all SF Symbols, not custom image
assets.

### User-facing strings

There is no centralized strings catalog / `Localizable.strings` — every UI string is an inline
Swift literal at its call site. Given the volume (hundreds of strings across ~30 screens), they
are not re-listed here; **§1 above enumerates every string verbatim, grouped by screen**, and is
the canonical source for Android string-resource extraction.

---

## 10. Dead code

Verified by exhaustive call-site grep (own file/preview/test-only references don't count), not
assumed from naming:

- **`PlansView.swift`, `PlanDetailView.swift`, `PlansViewModel.swift`** — the original standalone
  plan-detail/list screens. Confirmed dead by the code's own comment
  (`PlansView.swift:69-71`, dated 2026-09-19): "PlansView is unreachable from any tab, so it stays
  dead rather than being wired to the new ProposePlanSheet." Superseded by
  `PlanProposalCard`/`PinnedPlanCard`/`ReschedulePlanSheet` (embedded in Messages) and
  `GroupPlanDetailView` (Upcoming). **`Plan.swift` (the model) and `PlansService.swift` (the
  Firestore service) are live** — only the standalone screens built around them are dead.
- **`MatchesTabView`** (`MainTabView.swift:442`) — a full duplicate old implementation of the
  Matches screen sitting unused inside `MainTabView.swift`; only referenced by its own `#Preview`.
  The live `.matches` case renders `MatchesView()` instead.
- **`MatchingServiceExamples.swift`** — entire file body commented out; doesn't even compile as
  live code.
- **`Group.swift`, `GroupMember.swift`, `GroupsViewModel.swift`, `MemberRowView.swift`**, plus
  the tail ~420 lines of `FirestoreService.swift` (Group Operations / Group Member Management
  sections) — an older, unrelated "Groups" feature (organizer-run recurring activities with
  min/max participants, waitlists, confirmation deadlines) left behind by the "delete dead Groups"
  commit, which removed the UI but not the model/service layer. **Do not confuse with the live
  `GroupPlan` system** (different types, different Firestore collection, used by Upcoming).
  `MainTabView.swift`'s leftover `GroupTabIcon` struct is a related dead ornament.
- **`Event.swift`, `EventsView.swift`, `EventsViewModel.swift`, `EventDetailView.swift`,
  `EventDMHelper.swift`, `EventMessageThreadView.swift`** — a full festival/RSVP sub-feature,
  unreachable from any of the six live tabs. `EventDMHelper`'s apparent usages are inside a
  commented-out "Example Usage" block, not real code. One live remnant: `MessagingViewModel` still
  queries the legacy `eventThreads` read path and will render a stale event thread row if one
  happens to exist in Firestore — but nothing live can create a new one, and tapping such a row
  opens the wrong view (see §11).
- **`MessagesView.swift`** — an older, second Messages-tab implementation (sectioned
  "Individuals"/"Events" list); only its own `#Preview` references it. The live tab uses
  `MessagesListView`.
- **`EnhancedMessagingViewModel.swift`** — a full duplicate rewrite of thread/message logic
  against the same Firestore shape; zero instantiations anywhere. The live view model is
  `MessagingViewModel`.
- **`conversations`/`Conversation`/`ConversationMessage`** (`Avenue3/NotificationDataModels.swift`)
  — a `FirestoreService` extension with create/fetch/send methods for a `conversations` collection
  that nothing calls and that has no `firestore.rules` block. Do not port.
- **`FirestoreMessage`, `FirestoreMessageThread`** (`Message.swift`) — legacy Codable structs,
  declared and never referenced again anywhere, including their own file.
- **`PendingRequestRowView.swift`** — a join-request row (Approve/Deny) with no live caller
  anywhere in the app; only its own `#Preview` calls it. Likely a leftover from the same
  Groups/join-request feature removed above.
- **`SharedActivityPicker.swift`** — zero call sites anywhere in the repo.
- **`SimpaticoPlaceholderView`, `MatchInfoRow`** (both defined inside `MainTabView.swift`) — dead;
  the real Simpatico tab renders `SimpaticoView()`, and `MatchInfoRow` has no caller at all.
- **`Avenue3/ContentView.swift`** — the stock Xcode-template "Hello, world!" view; compiled into
  the target but never referenced by `RootView`/`MainTabView`/`Avenue3App`.
- **Not compiled at all** (outside the Xcode project's file references, confirmed via
  `project.pbxproj`): `/ATX Friends/AuthErrorMessage.swift` (a stray byte-identical duplicate of
  the real, compiled root-level `AuthErrorMessage.swift`) and `/ATX Friends.xcodeproj/
  SignInWithAppleButton.swift` (a stray file sitting inside the `.xcodeproj` bundle itself,
  declaring an unused `SignInWithAppleButtonRepresentable` — the real Apple Sign-In button lives
  in the properly-referenced `AppleSignInHelper.swift`).

---

## 11. Known bugs and oddities (noted, not fixed)

1. **`TodayPlanService.swift:266`** — the decode function reads
   `data["claimerReportedClaimer"]` into the `claimerReportedCreator` field, but the encoder
   (`:235`) only ever writes a key named `"claimerReportedCreator"` — that Firestore key never
   exists, so `claimerReportedCreator` always decodes to `nil` on the client regardless of what
   the `applyShowUpReport` Cloud Function actually wrote. (The Cloud Function itself writes the
   correct key server-side, per its own code; only the client's read-back path is wrong.)
2. **`MessageThreadView.swift`** message-list date header is hardcoded to the literal string
   `"Today"` for every message, regardless of actual date.
3. **`MessageThreadView.swift`** shows no loading indicator for message history (the view model's
   `isLoading` is never read) and no visible error banner for send/load failures (`errorMessage`
   is set but never displayed) — a failed send is only detectable by the draft text reappearing in
   the input box.
4. **Event threads opened via the live Messages tab are broken**: `MessagesListView` always opens
   threads with `MessageThreadView`, but that view's `.task` guard silently no-ops when
   `thread.match == nil` (true for all event threads) — no messages load, no listener attaches.
   The view's own source even contains a runtime warning print for exactly this case
   (`"⚠️ WARNING: MessageThreadView being used for EVENT thread!..."`), i.e. the code acknowledges
   this is stale/unintended wiring.
5. **Counter-proposed plans have no live re-confirmation path** (§5.5) — once a plan hits
   `counter` status there is no UI anywhere that can move it back to `confirmed`.
6. **`GoogleSignInHelper.signOut()` is never called** by `AuthViewModel.signOut()` — a user's
   Google Calendar session survives app sign-out.
7. ~~`AuthViewModel.deleteAccount()` only deleted the `users/{uid}` doc~~ — **fixed**: account
   deletion now runs server-side in `deleteMyAccount` and cascades to everything the user created
   (§2.8, §4). It deliberately leaves the abuse-report `reports` collection alone: reports are
   kept for one year, then deleted by the `purgeOldReports` scheduled function.
   `firestore.rules` now has a create-only `reports` block (android-16), so "Report a User"
   works on both platforms once the rules are deployed. Android also lists blocked users as
   reportable; iOS doesn't.
8. **`email` exists on the `FirebaseUser` Swift model but is absent from the general `userToFirestoreData` write path** — likely never
   actually persisted via normal profile saves despite being a model field.
9. **`groups`/`groupMembers` and `conversations` collections have no `firestore.rules` block**
   despite (dead) client code that reads/writes them — either production rules differ from the
   checked-in file, or this code would fail outright if ever exercised.
10. **`FirestoreService.fetchNearbyGroups` has geoHash prefix filtering disabled** with a
    `// TEMPORARY: Removed geoHash filtering for development` comment — moot since the whole
    Groups feature is dead, but the comment implies this was meant to be temporary.
11. **`User.toFirebaseUser()` (SwiftData → Firestore conversion) drops `locationSharingMode`,
    `notificationPreferences`, `blockedUsers`, etc.**, resetting them to defaults — a potential
    correctness gap if this conversion path is ever exercised live (UNCONFIRMED whether it
    currently is).
12. **Settings → Privacy & Safety → "Export My Data" description text** says it exports "Your
    name, friendship mode, and preferences" — `FirebaseUser` has no friendship-mode field; this
    copy is stale.
13. **Settings → Notifications → "Group Updates" toggle** ("Group join requests and membership
    changes") references the deleted Groups feature and has no corresponding live functionality.
14. **CLAUDE.md's stated profile system (exactly 3 activities, exactly 3 time slots, editable
    friendship mode) does not match the shipped code** — see §0 and §12.
15. **`PlanProposalCard`'s status pill hardcodes its own text/colors per status** rather than
    using `PlanStatus.displayName`/`.icon`, which exist but are unused by the live renderer.
16. Profile completion lets the user enter the main app (`isProfileComplete: true` is written)
    **before** their photo upload finishes — `photoURLs` can be briefly stale/empty in Firestore
    even though the profile is marked complete (§2.7).
17. `ASAuthorizationControllerPresentationContextProviding`'s `presentationAnchor`
    (`AppleSignInHelper.swift`) will `fatalError` if no window scene is found — an unhandled crash
    path, however unlikely.

---

## 12. Open questions for George

1. **Friendship mode (Individual / Couple / Couple with Kids)** — CLAUDE.md describes this as a
   core feature, changeable in Settings, but nothing in the shipped code implements it at all (no
   enum, no UI, no Firestore field). Was this intentionally descoped/removed before this round, or
   should Android build it fresh as part of parity?
2. **`groups`/`groupMembers` and `conversations` collections have no rules block** in the
   checked-in `firestore.rules`. Is the deployed production ruleset different from this file? If
   the checked-in file is authoritative, any (currently dead) client code touching those
   collections would be rejected outright — worth confirming before assuming Android should ignore
   them entirely.
3. **Is there a Storage security-rules file (`storage.rules`) deployed?** None was found in the
   repo; Storage-side authorization for `profile_photos/` is unverified from source alone.
4. ~~Does any server-side process perform the 30-day scheduled account deletion?~~ **Answered:**
   George chose immediate, permanent deletion with no grace period; the 30-day flow was removed
   and `deleteMyAccount` (§4) does the deletion. Still open: nothing sweeps orphaned Firebase Auth
   accounts from SSO signups abandoned without tapping Cancel (the app deletes them only when the
   user cancels or joins the waitlist, §2.5).
5. **Is the counter-proposed-plan re-confirmation gap (§5.5, §11.5) a known, accepted limitation**,
   or should Android's plan-status model include the re-confirm step iOS is missing?
6. **Should Android skip the entire dead-code surface listed in §10 outright**, or is any of it
   (Groups, Events, the standalone Plans screens) actually planned to come back in a future iOS
   round — in which case Android might want the data layer even without the UI?
