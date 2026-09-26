# ATX Friends: Google Play store listing (draft)

Everything George pastes into **Play Console → Grow users → Store presence → Main store
listing**, plus the screenshot plan. Nothing here has been uploaded.

Every feature named below exists in the Android app on `main` as of September 25, 2026. If a
feature is removed or held back before submission, remove it from the description too. Google
rejects listings that describe things the app doesn't do.

---

## Text fields

### App name (30 characters max)

```
ATX Friends
```

### Short description (80 characters max, this one is 72)

```
Make friends in Austin who like what you like and are free when you are.
```

A second option (76 characters), if you'd rather lead with "not dating":

```
Free friend-finding for Austin. Not dating. Match on plans, then go meet up.
```

### Full description (4,000 characters max, this one is about 2,430)

```
ATX Friends is a free app for making friends in Austin. Not dates. Friends.

Here's how it works. You make a simple profile: three photos, the activities you're into (hiking, board games, tacos, live music, whatever it is), and the times you're usually free, like Tuesday evenings or Sunday afternoons. When someone near you likes at least one of the same things and is free at least one of the same times, you'll show up in each other's Matches.

Then you each say Yay or Nay. If you both say Yay, a conversation opens and you can plan something real: an activity, a place, and a time that works. If either of you says Nay, that's the end of it, and nobody gets notified.

WHAT'S IN THE APP
• Matches: people near you who share an activity and a free time
• Today: open plans for the next 24 hours that you can join
• Upcoming: invite a few people at once to a group plan
• Simpatico: a few optional questions (how you like to hang out, what "on time" means to you) so you can see where you line up with someone
• Messages: chat with your mutual matches, and propose, confirm, or reschedule plans right in the conversation
• Settings: search radius, notifications, blocking, exporting your data, and deleting your account

WHAT IT ISN'T
It's not a dating app. There's no swiping for romance and no "likes."

It's also not trying to keep you on your phone. There are no streaks, no badges, and no endless feed. The whole point is to get you off the app and into a room with people. If you're opening it less because you're busy hanging out, it's working.

FREE, NO SUBSCRIPTION
Loneliness shouldn't be a premium tier. ATX Friends is free, with no subscription and no ads.

AUSTIN ONLY
ATX Friends is for people in and around Austin, TX. When you sign up, the app checks your location once, and you need to be within about 50 miles of downtown. If you're farther out, you can leave your email for the waitlist. It's one city on purpose: a friendship app only works when there are enough people close enough to actually meet.

YOUR PRIVACY
Your location is rounded to roughly a 0.7-mile grid before it's saved. Matches see a rough distance, never where you are. The app never tracks your location in the background. You can block anyone at any time, and deleting your account in Settings is immediate and permanent.

ATX Friends is made in Austin by an independent developer, and the code is open source.

For adults 18 and over.
```

**Why each claim is safe to make:**

| Claim | Where it's true in the code |
|---|---|
| Free, no ads | No billing library and no ad SDK in `android/app/build.gradle.kts` |
| 50 miles of downtown, waitlist | `domain/location/AustinGate.kt`, `SignupRepository.joinWaitlist` |
| Rounded to about a 0.7-mile grid | `domain/location/CoarseLocation.kt`; the manifest asks only for coarse location |
| Never in the background | No background-location permission in `AndroidManifest.xml` |
| Nay isn't notified | Only `onNewMutualMatch` sends a match push (`functions/src/index.ts`) |
| Deleting is immediate | `deleteMyAccount` Cloud Function, called from Settings |
| Export your data | Settings → Privacy & Safety → Export My Data |
| Open source | `LICENSE` (AGPL-3.0) and `ETHOS.md` |

**Avoid in the listing:** the words "dating," "match with singles," or anything flirty; "#1,"
"best," or other rankings (Play policy forbids them); user counts or testimonials; emoji in the
app name.

### Category, tags, and contact details

| Field | Value |
|---|---|
| App or game | App |
| Category | **Social** |
| Tags (up to 5) | Social, Community, Local events, Meetups. Pick from Play's list, and skip anything dating-related. |
| Contact email (public, required) | `{{SUPPORT_EMAIL}}`. The website uses `gap512@protonmail.com` today. See "Questions for George." |
| Phone (public, optional) | Leave blank. Anything you enter shows on the listing. |
| Website (optional) | `https://atxfriends.app` |
| Privacy policy (required) | `https://atxfriends.app/privacy` (draft: `docs/privacy-policy.md`) |

### Graphics

| Asset | Requirement | Plan |
|---|---|---|
| App icon | 512 × 512 PNG, 32-bit, 1 MB or smaller, full-bleed square (Play rounds the corners) | `sips -z 512 512 Avenue3/Assets.xcassets/AppIcon.appiconset/icon-ios-1024x1024.png --out ~/Desktop/atx-friends-512.png` |
| Feature graphic | 1024 × 500 PNG or JPEG, no transparency | Burnt orange `#BF5700` with the ATX Friends wordmark and one line, such as "Find your people in Austin." Keep the text centered; Play sometimes crops the edges. |
| Phone screenshots | 2 to 8 | See the screenshot guide below. |
| Video | Optional | Skip for now. |

---

## Screenshot guide

### Play's size rules

- PNG or JPEG, 8 MB or less each, no transparency.
- **2 to 8** phone screenshots. Aim for **6**. Google recommends at least 4 at 1080 px or
  wider if you want to be eligible for store features.
- Each side between **320 and 3,840 px**, and **the long side no more than twice the short
  side**.

**The catch:** modern Pixels take screenshots at about 20:9 (for example 1080 × 2400), which
is more than 2:1. If Play Console rejects them, pad each one to 9:16 on your Mac. The command
keeps the whole screen and adds navy bars on the sides:

```sh
mkdir -p ~/Desktop/play-shots
for f in ~/Desktop/pixel-shots/*.png; do
  sips --resampleHeight 1920 "$f" --out /tmp/shot.png
  sips --padToHeightWidth 1920 1080 --padColor 1B2A47 /tmp/shot.png --out ~/Desktop/play-shots/"$(basename "$f")"
done
```

### Which screens, in order

Use **light mode** for all six so the set looks consistent. It's the warmer look, and it's
what most people will see first. Dark mode is fully supported, so if you'd like, make
screenshot 6 a dark version of the Matches tab to show it off.

| # | Screen | State to capture | What it shows people |
|---|---|---|---|
| 1 | **Matches** | Two or three pending matches, with photos, shared activities, and shared times visible on the cards. | The core idea: shared interests plus shared free time. |
| 2 | **Match detail** (tap a match) | Scrolled to show the photos, "You both like …", and overlapping day and slot chips, with the Yay and Nay buttons visible. | That the choice is simple and friendly. |
| 3 | **Messages → a thread** | A short, friendly exchange ("Tacos at Veracruz Thursday?") and a **confirmed plan card** pinned at the top. | That matches turn into plans. |
| 4 | **Today** | One or two open plans from demo accounts, plus your own open slot. | Spontaneous, same-day plans. |
| 5 | **Upcoming** | A group plan with two or three invitees on the week strip. | Groups, not just one-on-one. |
| 6 | **Simpatico** | A question mid-answer, or the finished summary. | Compatibility that isn't romantic. |

Optional 7th: **Settings → Privacy & Safety** (Block, Export My Data, Delete Account). It
backs up the privacy promises in the description.

**Before each capture:**
- Clear notifications from the status bar. Ideally, use demo mode (below) so the clock reads
  9:41, the battery is full, and signal is full.
- Font size and display size at the system defaults.
- To capture: **Power + Volume Down**. Screenshots land in Photos → Screenshots. AirDrop
  doesn't work from a Pixel, so use Google Photos, Nearby Share to a Chromebook, or a USB cable
  with **Android File Transfer** / `adb pull`.

**Optional: a clean status bar with demo mode.** This needs USB debugging on (Settings → About
phone → tap **Build number** 7 times → Developer options → **USB debugging**). Then, with the
Pixel plugged in:

```sh
adb shell settings put global sysui_demo_allowed 1
adb shell am broadcast -a com.android.systemui.demo -e command enter
adb shell am broadcast -a com.android.systemui.demo -e command clock -e hhmm 0941
adb shell am broadcast -a com.android.systemui.demo -e command battery -e level 100 -e plugged false
adb shell am broadcast -a com.android.systemui.demo -e command network -e wifi show -e level 4
adb shell am broadcast -a com.android.systemui.demo -e command notifications -e visible false
# …take the screenshots…
adb shell am broadcast -a com.android.systemui.demo -e command exit
```

### Test accounts for realistic screenshots (no real users)

The app talks to the live Firebase project (`avenue3-73bb4`); there's no separate test
database. That means demo accounts are visible to real Austin users while they exist, and
real users could show up in the demo account's Matches. So:

1. **Pick a quiet hour.** Create the accounts, take the screenshots, and delete the extra
   accounts the same day (Settings → Privacy & Safety → Delete Account on each one).
2. **Before every capture, check that no real person's name or photo is on screen.** If one is,
   Nay them from the demo account or wait until they're gone. Never publish a screenshot with a
   real user in it.

**The accounts** (use Gmail "plus" addresses so they all reach you, for example
`yourgmail+demo1@gmail.com`):

| Account | First name | Activities (3 Main + a few Extra) | Free times |
|---|---|---|---|
| **Demo (you hold the phone)** | Sam | Main: Hiking, Tacos, Board Games. Extra: Live Music, Coffee | Tue Evening, Thu Evening, Sat Wake Up, Sun Afternoon |
| Friend A | Priya | Main: Tacos, Live Music, Trivia | Thu Evening, Fri Night, Sun Afternoon |
| Friend B | Marcus | Main: Hiking, Climbing, Coffee | Sat Wake Up, Sun Wake Up, Tue Evening |
| Friend C | Lena | Main: Board Games, Pickleball, Brunch | Sun Afternoon, Wed Evening, Sat Afternoon |

- **Names:** made-up first names only. Don't use anyone's real name, including friends who
  lend photos.
- **Photos (3 per account):** from friends who have said yes in writing to their photos
  appearing in the Play listing, or from a stock site whose license allows commercial use (the
  Unsplash and Pexels licenses do). Mix faces with activity shots: a trailhead, a board game
  table, a taco plate. No AI-generated people, and no photos from anyone's real ATX Friends
  profile.
- **Sign-up location:** each account has to pass the Austin gate, so sign up from Austin.
- **Build the scenes:**
  1. Sign up all four. You can use the iPhone for the friend accounts.
  2. **Screens 1 and 2:** leave Priya and Lena undecided, so they're pending in Sam's Matches.
  3. **Screen 3:** Marcus and Sam both say Yay. From Sam, propose "Hike at Barton Creek
     Greenbelt" for Saturday Wake Up; accept it as Marcus. Trade two or three friendly
     messages.
  4. **Screen 4:** as Priya, post an open Today plan ("Tacos + trivia tonight"); as Sam, add
     an open slot.
  5. **Screen 5:** as Sam, invite Marcus (and Priya, if she's a mutual match by then) to a
     group plan later in the week.
  6. **Screen 6:** as Sam, answer the Simpatico questions.
- **Afterwards:** delete Priya, Marcus, and Lena. Keep Sam only if you use it as the
  reviewer demo account (see `docs/android-play-release.md`, "App access").
