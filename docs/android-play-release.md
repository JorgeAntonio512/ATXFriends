# Releasing ATX Friends on Google Play

A step-by-step guide for George. Nothing here has been done yet: no accounts, no uploads. Menu
names are as of September 2026; Google renames things now and then, so if a button has moved,
look for the same words nearby.

---

## 0. Release blockers (check these first)

These have to be sorted out before a production release. Most of them don't block internal or
closed testing.

| # | Blocker | Blocks |
|---|---|---|
| 1 | **The upload key doesn't exist yet.** Create it (§2) before building anything for Play. | Everything |
| 2 | **Android has no sign-up.** Android users can only sign in to an account made on an iPhone. Play's reviewers need a working account, so give them demo login details (§8, "App access"). Real Android-only users can't join until sign-up is ported. | Production (in practice) |
| 3 | **Privacy policy URL.** `website/privacy.html` exists in the main checkout but isn't committed or deployed. Play needs a public URL (§6). | Closed test and production |
| 4 | **Account-deletion web link.** Play requires a web page where people can ask for their account to be deleted without reinstalling the app (§5, last question). | Production |
| 5 | **Privacy policy mentions Apple and Google sign-in.** Android only has email and password today. That's fine (the policy covers both apps), but re-read it once Android's features settle. | Nothing |

---

## 1. Create the Play Console developer account

1. Go to <https://play.google.com/console/signup> and sign in with the Google account that should
   own the app long-term. Use one you'll never lose access to; moving an app to another account
   later is slow.
2. Choose **Yourself** (a personal account). An **Organization** account needs a D-U-N-S number
   and a registered business. Pick it only if ATX Friends is (or will soon be) an LLC.
3. Pay the one-time **$25** registration fee.
4. Complete **identity verification**: legal name, address, and a government ID. It can take a
   few days.
5. Set the **developer name**. This is shown publicly on the store listing under the app name.
6. Verify a **contact email and phone number**. Google also asks you to confirm you have an
   Android device, using the Play Console app on the Pixel.

> **New personal accounts must run a closed test first.** Personal accounts created after
> November 13, 2023 can't publish to production until they've run a closed test with at least
> **12 testers who stay opted in for 14 days in a row** (§4). Plan for about three weeks between
> your first closed-test upload and a production launch.

---

## 2. Create the upload key (one time, and back it up)

Google re-signs the app for users with its own **app signing key** (Play App Signing, which is
mandatory for new apps). You sign each upload with an **upload key** that only you hold.

**Why it matters:** if the upload key is lost, you can't ship updates until Google resets it.
That means a support request, proof you own the account, and a wait of days. If the key is
*stolen*, someone could upload builds as you. Treat it like the keys to the house.

### Create it

1. Open the project in Android Studio (the `android/` folder).
2. **Build → Generate Signed App Bundle or APK… → Android App Bundle → Next.**
3. Under **Key store path**, click **Create new…**
   - **Key store path:** `~/Keys/atx-friends/atx-friends-upload.jks`. Create the
     `Keys/atx-friends` folder first. Keep the key **outside the repo**, never inside `android/`.
   - **Password:** make a long, random password and save it in your password manager
     immediately.
   - **Alias:** `upload`
   - **Key password:** the same as the store password is fine (Android Studio suggests it).
   - **Validity (years):** `30` or more, so it never expires while the app is alive.
   - **Certificate:** your name, plus "ATX Friends" as the organization. Other fields are optional.
4. Click **OK**. Then **Cancel** the rest of the wizard. You only needed the key.

### Point the build at it

Create `android/keystore.properties`. This file is git-ignored and public-ignored; never commit
it.

```properties
storeFile=/Users/Jamespazdral/Keys/atx-friends/atx-friends-upload.jks
storePassword=THE_PASSWORD
keyAlias=upload
keyPassword=THE_PASSWORD
```

Check it's ignored before you ever run `git add`:

```sh
cd ~/Desktop/Avenue3/Avenue3
git check-ignore -v android/keystore.properties   # must print a .gitignore line
```

### Back it up (do all three)

1. **Password manager:** store both passwords, plus the alias `upload`, in one entry named
   "ATX Friends upload key". Attach the `.jks` file to that entry if your manager supports
   attachments (1Password and Bitwarden do).
2. **An encrypted off-machine copy:** put the `.jks` on an encrypted USB drive, or in an
   encrypted disk image in iCloud Drive. To make the disk image: Disk Utility → File → New Image →
   Image from Folder → pick `~/Keys/atx-friends` → Encryption: 256-bit AES.
3. **Test the backup:** restore the file from backup to a temporary folder, point
   `keystore.properties` at it, and run `./gradlew bundleRelease`. If that builds, the backup
   works. Point the file back at the original afterwards.

**Never** email the key, commit it, or put it anywhere unencrypted in cloud storage.

---

## 3. Build the release bundle

```sh
cd ~/Desktop/Avenue3/Avenue3/android
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew bundleRelease
```

The file to upload is `android/app/build/outputs/bundle/release/app-release.aab`.

- **Before every upload after the first,** raise `versionCode` in `android/app/build.gradle.kts`
  by 1. Play rejects a bundle whose `versionCode` it has already seen. Change `versionName` only
  when the iOS marketing version changes, so the two stay in step.
- R8's deobfuscation mapping ships inside the bundle automatically, so crash reports in Play
  Console show real class names. Nothing extra to upload.
- If `keystore.properties` is missing, the build still succeeds, but the bundle is **unsigned**
  and Play will reject it.

---

## 4. Testing tracks

### 4a. Create the app

1. Play Console → **Create app**.
2. **App name:** `ATX Friends`. **Default language:** English (United States). **App or game:**
   App. **Free or paid:** Free (this can't be changed to paid later).
3. Tick the declarations (developer program policies, US export laws) → **Create app**.
4. The package name `com.georgeappdev.atxfriends` is locked in by the first upload and can never
   change.

### 4b. Internal testing (start here)

This track is fast and needs no review. Up to 100 testers.

1. **Test and release → Testing → Internal testing → Testers tab → Create email list.** Add your
   own Gmail and anyone helping. Save.
2. **Releases tab → Create new release.** When asked about **Play App Signing**, accept the
   default (Google generates and holds the app signing key).
3. Upload `app-release.aab`. Release name: `1.0 (1)`. Release notes: "First internal build."
4. **Next → Save and publish.**
5. On the **Testers** tab, copy the **opt-in link**. Open it on the Pixel, while signed in to a
   tester Gmail, → **Become a tester** → install from the Play Store link.

### 4c. Closed test: 12+ testers for 14 days (required for new personal accounts)

1. **Testing → Closed testing → Create track** (or use the default "Closed testing - Alpha").
2. **Testers:** the easiest route is a **Google Group**. Create one at groups.google.com (for
   example `atx-friends-testers`), set "Who can join" to *Invited users only*, and add the group
   email as the tester list. Alternatively, use an email list.
3. **Recruit about 15–20 people**, not exactly 12. Some always forget to opt in, or uninstall.
   Each tester must:
   - use an **Android** phone signed in with the Gmail that's on the list,
   - open the **opt-in link** and tap **Become a tester**,
   - install the app from Play, and **stay opted in for the full 14 days**. Opening the app now
     and then helps. Google looks for real engagement.
   - Each tester also needs an **ATX Friends account**. Until Android has sign-up, create one for
     them on an iPhone, or have them sign up on iPhone first.
4. Upload the same (or a newer) bundle to this track → **Send for review.** The first closed-test
   review can take a few days.
5. The **14-day clock** starts once 12 testers are opted in. The Dashboard shows progress.
6. When it's done: **Dashboard → Apply for production access.** Google asks about the test (how
   testers were recruited, what feedback you got, what changed). Answer honestly and concretely.
   Approval usually takes up to about a week.

### 4d. Production

**Production → Create new release** → upload → roll out. Consider a **staged rollout** (for
example 20%) for the first version, then raise it to 100%.

---

## 5. Data safety form (draft)

**App content → Data safety.** This draft is based on what the **Android app actually does
today** (spec §3 plus the Android code). Firebase acts as a *service provider*, so it doesn't
count as "sharing". Things users deliberately show each other, such as their profile to matches
and messages to the other person, don't count as "sharing" either.

**Overview questions**

| Question | Answer |
|---|---|
| Does your app collect or share any of the required user data types? | **Yes** |
| Is all user data encrypted in transit? | **Yes** (Firebase uses TLS for everything) |
| Do you provide a way for users to request that their data is deleted? | **Yes**: Settings → Privacy & Safety → Delete Account (the `deleteMyAccount` Cloud Function), plus the web link below |

**Data types.** For each row: Collected = Yes, Shared = **No**, Processed ephemerally = No.

| Category → type | Required or optional | Purposes | What it is |
|---|---|---|---|
| Personal info → **Name** | Required | App functionality, Account management | `displayName` |
| Personal info → **Email address** | Required | Account management | Firebase Auth sign-in |
| Personal info → **User IDs** | Required | App functionality, Account management | Firebase Auth UID |
| Location → **Approximate location** | Optional | App functionality | Rounded to about a 0.7-mile grid before it's saved; only with Share My Location on (Android only requests coarse location). **Becomes Required** once Android gets sign-up, because iOS saves a location at signup. |
| Photos and videos → **Photos** | Required | App functionality | The 3 profile photos (Firebase Storage) |
| Messages → **Other in-app messages** | Optional | App functionality | Messages and plan proposals between matches |
| App activity → **Other user-generated content** | Required | App functionality | Activities, availability (day/slot picks), Yay/Nay decisions, plans, open Today plans, group plans, Simpatico answers, show-up reports, blocked users |
| Device or other IDs → **Device or other IDs** | Optional | App functionality | **Only once Android push notifications ship** (the FCM token). Today Android doesn't register one, so leave this off until then. |

**Not collected:** financial info, health, contacts, calendar (adding to a calendar hands a
prefilled event to the phone's calendar app, and the app never reads the calendar), web
history, audio, files, installed apps, search history, and crash logs or diagnostics (no
Crashlytics or Analytics in the Android app).

**Account deletion URL:** Play asks for a **web link** where users can request deletion. Add a
short section or page to the website, such as `/delete-account`: "Delete your account in
Settings → Privacy & Safety → Delete Account, or email us from the address on your account and
we'll delete it within 30 days." Then paste that URL here.

> Update this form whenever the Android app starts collecting something new. Push tokens and
> sign-up location are the two already on the horizon.

---

## 6. Privacy policy link

- Play requires a privacy policy URL in **App content → Privacy policy**, and on the store
  listing.
- It must be a **public web page**: not a PDF, not a Google Doc, not behind a login, not
  geo-blocked. It must name the developer (matching your developer name, or clearly the same
  person) and describe what's collected.
- `website/privacy.html` in the main checkout fits the bill once it's committed and deployed
  (for example via Firebase Hosting, then `https://<your-domain>/privacy`). Open the URL in a
  private browser window to check it loads without signing in.
- Keep the policy and the Data safety form consistent. Reviewers compare them.

---

## 7. Content rating

**App content → Content rating → Start questionnaire.**

1. **Email:** your contact email. **Category:** *Social or communication*. (The questionnaire
   asks this in its own words; pick the social / user-interaction category, not "Game".)
2. Answer truthfully. Expected answers:
   - Violence, sexuality, language, controlled substances, gambling: **No** to all.
   - **Users can interact or exchange content** (messages, photos): **Yes**.
   - **Shares users' location with other users:** the app shows matches a *rough distance*,
     never a position. The safest answer is **Yes** (it's derived from location). Answering No
     risks a rating correction later.
   - **Digital purchases:** **No**.
3. Submit. The result is issued immediately, typically with a "Users Interact" notice.

**Target audience** (App content → Target audience and content): choose **18 and over only**,
matching the privacy policy. Answer **No** to "Could your store listing unintentionally appeal to
children?" That keeps the app out of the Families program requirements.

**Other App content declarations**

| Section | Answer |
|---|---|
| Ads | **No**, the app contains no ads |
| App access | **All or some functionality is restricted** → add a demo account (email and password of an iPhone-created, fully set-up test account) with the note: "Sign in with these details. New accounts can't be created on Android yet." |
| Government app / Financial features / Health / News | **No** |
| Dating | Not a dating app. Don't pick any dating-related category or tag. |

---

## 8. Store listing

**Grow users → Store presence → Main store listing.**

| Field | Limit | Draft |
|---|---|---|
| App name | 30 chars | `ATX Friends` |
| Short description | 80 chars | `Meet people in Austin who like what you like and are free when you are.` |
| Full description | 4,000 chars | See below |
| App icon | 512×512 PNG, 32-bit, ≤1 MB, full-bleed square (Play rounds the corners) | Make it from the iOS icon: `sips -z 512 512 Avenue3/Assets.xcassets/AppIcon.appiconset/icon-ios-1024x1024.png --out ~/Desktop/atx-friends-512.png` |
| Feature graphic | 1024×500 PNG/JPG | Burnt orange (`#BF5700`) with the ATX wordmark and "Find Your People". Needs making. |
| Phone screenshots | 2–8; 9:16; each side 320–3840 px | Take them on the Pixel (Power + Volume Down): Matches, Today, Messages thread, Simpatico, Settings. Use test accounts only, no real users' photos. |
| App category | | **Social** |
| Tags | up to 5 | Social, Community, Local events (avoid anything dating-related) |
| Contact email | required | Your support email |
| Website | optional | The hosted site |
| Privacy policy | required | Same URL as §6 |

**Full description (draft):**

> ATX Friends helps people in Austin build real friendships. It's not a dating app: no swiping,
> no romance, just neighbors who want to hang out.
>
> Build a simple profile: three photos, the activities you love (hiking, board games, tacos,
> anything), and the times you're usually free. When someone nearby shares at least one activity
> and one free time with you, you'll both see each other in Matches. Say Yay or Nay. If you both
> say Yay, you're connected and can start planning.
>
> • Match on shared activities and overlapping free time, within a radius you choose
> • Propose a plan, a time and a place, right in the conversation
> • Today: see open plans for the next 24 hours and grab one
> • Upcoming: invite a few people at once to a group plan
> • Simpatico: optional questions that show how well you'd click
> • Your location is rounded before it's saved, and matches only ever see a rough distance
> • Block anyone, anytime. Delete your account in Settings, and it's gone for good.
>
> ATX Friends is for adults 18+ in and around Austin, TX.

Review this wording against the current feature set before submitting; drop any feature that
isn't live on Android yet.
