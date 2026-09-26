# Releasing ATX Friends on Google Play

George's click-by-click path from nothing to a live Play Store listing. Nothing here has been
done yet: no Play Console account, no uploads. Menu names are as of September 2026; Google
renames things now and then, so if a button has moved, look for the same words nearby.

Companion docs:
- Store listing text and the screenshot guide: `docs/play-store-listing.md`
- Privacy policy draft (for a lawyer to review): `docs/privacy-policy.md`

---

## 0. Status and release blockers

| # | Item | Status | Blocks |
|---|---|---|---|
| 1 | **Upload key** | ✅ Exists at `~/Keys/atx-friends/atx-friends-upload.jks`, and `android/keystore.properties` points at it. Finish the backups in §3. | — |
| 2 | **Signed App Bundle** | ✅ `./gradlew bundleRelease` builds a bundle signed with the upload key (§4). | — |
| 3 | **Privacy policy URL.** Live at `https://atxfriends.app/privacy` (checked September 25, 2026). It's the shorter website version, which differs from the code in a few places; update it from `docs/privacy-policy.md` before production, because Play compares it with the Data safety form. | ⚠️ Update text | Production |
| 4 | **Account-deletion web page.** ✅ Live at `https://atxfriends.app/delete-account` (deployed September 25, 2026; source `website/delete-account.html`). Paste it into the Data safety form. | ✅ | — |
| 5 | **"Report a User."** Play's User Generated Content policy requires apps where people message each other to have an in-app way to report users. The code is done (android-16): a create-only `reports` rule, the Android screen, and `purgeOldReports`, which deletes reports after a year. **Still to do: deploy** (§0a). Until the rule is deployed, every report is rejected, on iPhone too. | ⚠️ Deploy | Production, and possibly the first closed-test review |
| 6 | **Messages are readable by any signed-in user.** `firestore.rules` allows `read: if isSignedIn()` on `messages` ("the matchID is the practical access control"). Anyone with a modified app could list every message. The same applies to `simpaticoAnswers`, and full `users` docs (including `fcmTokens` and `blockedUsers`) are readable by any signed-in user. Fix the messages rule to require `senderID` or `receiverID == request.auth.uid`, and change the clients' queries to match, before telling the public that only matches see their messages. | ❌ | An honest privacy policy; do before production |
| 7 | **Google sign-in on Play installs.** Play re-signs the app with Google's app-signing key. Its SHA-1 must be added to Firebase, or "Sign in with Google" fails for everyone who installs from Play (§5, step 5). | ❌ | Internal testing onward (Google sign-in only) |
| 8 | **Play reviewers aren't in Austin.** Sign-up is gated to 50 miles of Austin, so a reviewer who taps Sign Up lands on the waitlist. Give them a ready-made demo account (§7, App access). | ❌ | Closed test and production |

### 0a. Deploying the reports fix (blocker 5)

Do this after `android-16` is merged into `main`, from the main checkout, so you deploy the
rules exactly as they are on `main`:

```sh
cd ~/Desktop/Avenue3/Avenue3
git checkout main && git pull
(cd firestore-tests && npm test)        # rules tests; the last line must say "0 failed"
firebase deploy --only firestore:rules
firebase deploy --only functions:purgeOldReports
```

- `firestore:rules` replaces **all** live rules with the file on `main`. If you've ever edited
  rules in the Firebase Console, compare first: Console → Firestore → **Rules** tab.
- The first scheduled-function deploy may ask to enable the **Cloud Scheduler** API. Say yes.
  It's free at this size.
- **Check it worked:** on either phone, go to Settings → Privacy & Safety → Report a User and
  report a demo account. The report appears in Firebase Console → Firestore → `reports`.
- **Reviewing reports:** there's no admin screen. Check Firestore → `reports` every few days
  (sort by `timestamp`). For a real problem, stop the person signing in: Firebase Console →
  **Authentication** → find their account (the `reportedUserId` is their User UID) → ⋮ →
  **Disable account**. Play expects reports to be acted on.

Two more things aren't blockers but are worth doing first:
- The feature graphic (1024 × 500) still needs designing. See `docs/play-store-listing.md`.
- Sign in with Apple doesn't exist on Android, so iPhone users who signed up with Apple can't
  sign in on Android. Mention it in the testers' welcome message.

---

## 1. Timeline (realistic)

| Week | What happens | Waiting on |
|---|---|---|
| **0** | Create the Play Console account and start identity verification (§2). Meanwhile, fix blockers 3–6, set up the demo account, and take screenshots. | Google: **1–7 days** for verification (longer if they ask for another document) |
| **1** | Create the app, upload to **internal testing**, add the Firebase SHA-1s, and try it yourself plus 2–3 friends (§5–6). Fill in App content (§7) and the store listing. | Nothing. Internal testing has no review and is live in minutes. |
| **1–2** | Start the **closed test**, upload the bundle, and send it for review (§8). | Google: **1–3 days** for the first review (up to 7) |
| **2–4** | **14 consecutive days** with at least 12 opted-in testers. | The calendar |
| **4** | **Apply for production access** (§9). | Google: **up to 7 days** |
| **5** | Production release and its first review (§10). | Google: **1–3 days** (up to 7 for a new app) |

**Plan for about 5 to 6 weeks** from creating the account to being live, if nothing is rejected.
A rejection at any step usually costs 3–7 days.

---

## 2. Create the Play Console developer account

1. Go to <https://play.google.com/console/signup> and sign in with the Google account that should
   own the app long-term. Use one you'll never lose access to; moving an app to another account
   later is slow.
2. Choose **Yourself** (a personal account). An **Organization** account needs a D-U-N-S number
   and a registered business. Pick it only if ATX Friends is (or will soon be) an LLC. An
   organization account also skips the 12-tester closed-test requirement.
3. Pay the one-time **$25** registration fee.
4. Complete **identity verification**: legal name, address, and a government ID. It can take a
   few days. Your legal name must match the ID exactly.
5. Set the **developer name**, which is shown publicly under the app name. "ATX Friends" or your
   own name both work. A personal account also shows your **country** publicly.
6. Verify a **contact email** and **phone number**. Google also asks you to confirm you have an
   Android device, using the **Play Console** app on the Pixel.

> **New personal accounts must run a closed test first.** Personal accounts created after
> November 13, 2023 can't publish to production until they've run a closed test with at least
> **12 testers who stay opted in for 14 days in a row** (§8).

---

## 3. The upload key (already made; back it up)

The key exists at `~/Keys/atx-friends/atx-friends-upload.jks`. Its alias and passwords are in
`android/keystore.properties`, which is git-ignored and public-ignored. Its fingerprints are
public and safe to share:

```
SHA-1:   4F:ED:95:3E:6B:A9:6D:12:59:44:3F:80:AD:1B:3A:FE:11:BB:F8:AF
SHA-256: 3B:58:CF:CD:9D:C8:6F:6D:56:CA:85:E7:0A:2F:73:6B:E3:32:C3:30:2D:99:83:99:DC:73:16:D8:92:76:7F:2D
Owner:   CN=George Anthony Pazdral II, O=ATX Friends, L=Austin, ST=TX, C=US (valid until 2054)
```

**Why it matters:** if the upload key is lost, you can't ship updates until Google resets it.
That means a support request, proof you own the account, and a wait of days. If it's *stolen*,
someone could upload builds as you.

### Back it up (do all three, if you haven't)

1. **Password manager:** store both passwords, plus the alias, in one entry named "ATX Friends
   upload key". Attach the `.jks` file if your manager supports attachments (1Password and
   Bitwarden do).
2. **An encrypted off-machine copy:** put the `.jks` on an encrypted USB drive, or in an
   encrypted disk image in iCloud Drive: Disk Utility → File → New Image → Image from Folder →
   pick `~/Keys/atx-friends` → Encryption: 256-bit AES.
3. **Test the backup:** restore the file to a temporary folder, point `keystore.properties` at
   it, and run `./gradlew bundleRelease`. If it builds, the backup works. Point the file back
   afterwards.

**Never** email the key, commit it, or put it anywhere unencrypted in cloud storage. Before any
`git add`, this must print a `.gitignore` line:

```sh
git check-ignore -v android/keystore.properties
```

---

## 4. Build the release bundle

```sh
cd ~/Desktop/Avenue3/Avenue3/android
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew bundleRelease
```

The file to upload is **`android/app/build/outputs/bundle/release/app-release.aab`** (about
9.7 MB).

**Check it's signed with the upload key.** The SHA-1 must match §3:

```sh
"$JAVA_HOME/bin/keytool" -printcert -jarfile app/build/outputs/bundle/release/app-release.aab | grep SHA1
```

- If `keystore.properties` is missing, the build still succeeds, but the bundle is **unsigned**
  and Play rejects it. The command above prints nothing in that case.
- **Debug-only UI is not in release builds.** R8 removes the two `BuildConfig.DEBUG` blocks: the
  Firebase project line at the bottom of Settings, and the "DEBUG INFO" panel in Notifications.
  Checked on the September 25, 2026 build: neither string is in the bundle's code.
- **Before every upload after the first,** raise `versionCode` in `android/app/build.gradle.kts`
  by 1. Play rejects a `versionCode` it has already seen. Change `versionName` only when the iOS
  marketing version changes, so the two stay in step.
- R8's deobfuscation mapping ships inside the bundle, so crash reports in Play Console show real
  class names. Nothing extra to upload.

---

## 5. Create the app and turn on Play App Signing

1. Play Console → **Create app**.
2. **App name:** `ATX Friends`. **Default language:** English (United States). **App or game:**
   App. **Free or paid:** **Free**. This can never be changed to paid.
3. Tick the declarations (Developer Program Policies, US export laws) → **Create app**.
4. **Play App Signing** (mandatory for new apps). The first time you create a release (§6), Play
   asks how to sign the app. Choose **Use a Google-generated key** (the default). Google keeps
   the key that users' phones see, and you keep signing uploads with your upload key. If the
   upload key is ever lost, Google can reset it (Setup → App signing → Request upload key
   reset).
5. **Register the signing fingerprints with Firebase** (blocker 7). Without this, "Sign in with
   Google" fails on every install from Play.
   1. After the first upload: Play Console → **Test and release → Setup → App signing**. Copy the
      **App signing key certificate** SHA-1 and SHA-256 fingerprints.
   2. Open <https://console.firebase.google.com> → project **avenue3-73bb4** → ⚙️ **Project
      settings** → **General** → scroll to **Your apps** → the Android app
      `com.georgeappdev.atxfriends` → **Add fingerprint**. Paste the SHA-1, then add the SHA-256
      the same way.
   3. Also add the **upload key** SHA-1 from §3 if it isn't there already. It signs release
      builds you install directly from Android Studio.
   4. Download the new **google-services.json** from the same page and replace
      `android/app/google-services.json`. Commit it on a branch (it's committed on purpose; the
      public repo excludes it). Rebuild before the next upload.
6. The package name `com.georgeappdev.atxfriends` is locked in by the first upload and can never
   change.

---

## 6. Internal testing (you plus 2 or 3 friends)

No review, live in minutes, up to 100 testers. Use it to catch anything obviously broken before
the 14-day clock starts.

1. **Test and release → Testing → Internal testing → Testers tab → Create email list.** Name it
   "Core". Add your own Gmail and 2–3 friends with Android phones. **Save.**
2. **Releases tab → Create new release.** Accept Play App Signing (§5, step 4).
3. Upload `app-release.aab`. Release name: `1.0 (1)`. Release notes: `First internal build.`
4. **Next → Save and publish.**
5. On the **Testers** tab, copy the **opt-in link** and send it to the testers. Each one opens
   it while signed in to their tester Gmail → **Become a tester** → installs from the Play link.
6. Do §5 step 5 (the Firebase fingerprints) now. Then check on a copy installed from Play:
   - [ ] Sign up with email from within 50 miles of Austin, and finish profile setup
   - [ ] Sign in with Google
   - [ ] Sign in with an existing iPhone-made account
   - [ ] Matches, Yay/Nay, a mutual match, messages, and a plan
   - [ ] A push notification arrives
   - [ ] Settings → Privacy & Safety → Export My Data, then Delete Account (on a throwaway account)
   - [ ] No debug text at the bottom of Settings or in Notifications

---

## 7. App content (fill in while internal testing runs)

Play Console → **Policy and programs → App content** (sometimes shown as **Monitor and improve →
Policy → App content**). Every section must be complete before a closed test can go to review.

### Privacy policy
Paste `https://atxfriends.app/privacy`. It must be a public web page: not a PDF or a Google Doc,
and not behind a login. Open it in a private browser window to check. Keep it consistent with
the Data safety answers; reviewers compare them.

### App access
Choose **All or some functionality is restricted** → **Add instructions**:
- **Name:** Demo account
- **Username and password:** a fully set-up demo account (for example the "Sam" account in
  `docs/play-store-listing.md`).
- **Instructions:** "Sign in with Email using these details. New sign-ups are limited to people
  within 50 miles of Austin, TX, so reviewers elsewhere will see a waitlist. The demo account
  already has a profile and matches."

Keep this account working for as long as the app is on Play. Reviewers use it for every update.

### Ads
**No, my app doesn't contain ads.**

### Content rating
**Start questionnaire.**
1. **Email:** your contact email. **Category:** the social or communication category (not Game).
2. Expected answers:
   - Violence, sexuality, language, controlled substances, gambling: **No** to all. Simpatico's
     "Drinking: I…" question asks about a preference; it doesn't show or encourage alcohol. If
     the questionnaire asks specifically about references to alcohol, answer honestly: Yes, a
     mention in a preferences question.
   - **Users can interact or exchange content** (messages, photos): **Yes**.
   - **Shares users' location with other users:** **Yes**. Matches see a distance derived from
     location, never a position. Answering No risks a rating correction later.
   - **Digital purchases:** **No**.
3. Submit. The rating is issued immediately, usually with a "Users Interact" notice.

### Target audience and content
- **Target age groups:** **18 and over** only.
- "Could your store listing unintentionally appeal to children?" **No.**
- This keeps the app out of the Families program requirements.

### Data safety
See **Appendix A** for every answer.

### Other declarations
| Section | Answer |
|---|---|
| Government app | No |
| Financial features | My app doesn't provide any financial features |
| Health apps | My app doesn't have any health features |
| News app | No |
| Advertising ID | **No**. The app doesn't use the advertising ID; the merged release manifest has no `AD_ID` permission. |
| Dating | Not a dating app. Don't pick any dating category or tag anywhere. |

### Store listing
**Grow users → Store presence → Main store listing.** Paste everything from
`docs/play-store-listing.md`.

---

## 8. Closed test: 12+ testers for 14 days

Required for new personal accounts before production.

### Set up the track
1. **Test and release → Testing → Closed testing → Create track.** Name it `friends`.
2. **Countries/regions:** **United States** only.
3. **Testers:** use a **Google Group**, so people can be added without editing the track:
   1. Go to <https://groups.google.com> → **Create group**. Name: `ATX Friends Testers`, with an
      email like `atx-friends-testers@googlegroups.com` (or whatever's free).
   2. **Who can search:** only members. **Who can join:** invited users only. **Who can view
      conversations and post:** only managers. Create it.
   3. **Members → Add members** (add them directly; no invitation needed), using each tester's
      Gmail.
   4. Back in Play: Closed testing → `friends` → **Testers** tab → **Google Groups** → paste the
      group email → **Save**.
4. **Feedback channel** (on the Testers tab): your support email.
5. **Releases → Create new release** → upload the bundle (or promote the internal one) → release
   notes → **Send for review**. The first review can take a few days.

### Recruit about 20 people, not 12
Some people always forget to opt in or uninstall, and **anyone who drops out breaks their own 14
days**. With 20, you can lose 8 and still pass.

Each tester needs:
- an **Android** phone signed in with the **Gmail** on the list,
- to **stay opted in and keep the app installed for 14 days in a row**,
- to open the app a few times a week. Google looks for real use, and it's also the only way
  you'll get useful feedback,
- an **ATX Friends account**. They can sign up in the app if they're within 50 miles of Austin.
  Testers elsewhere can use a spare demo account you set up for them.

Where to find them: friends and family with Android phones, iPhone beta testers who also have
an Android phone, a local Austin community group, or coworkers. Don't pay a "testing service."
Google treats those as low-quality testing, and they don't help the app.

**Message to send** (edit to taste):

> Hey! I'm getting ATX Friends ready for the Google Play Store, and Google needs 12+ people to
> test it for two weeks before I can launch. Would you help?
>
> 1. Tell me the Gmail address on your Android phone.
> 2. Once I've added you, open this link on your phone and tap **Become a tester**:
>    {{OPT-IN LINK}}
> 3. Install ATX Friends from the Play Store link on that page and sign up (or sign in if you
>    already have an account; Sign in with Apple isn't on Android yet).
> 4. **Keep it installed for 14 days** and open it a few times. Uninstalling resets the clock.
>
> Tell me anything confusing or broken. That's the whole point. Thank you!

### Track it
Keep a simple sheet (Google Sheets or Numbers) with these columns:

| Name | Gmail | Added to group | Opted in (date) | Installed | Has account | Day 7 check-in | Day 14 done | Feedback |
|---|---|---|---|---|---|---|---|---|

- Play Console shows the number of opted-in testers on the **Closed testing → Testers** tab, and
  the 12-for-14-days progress on the **Dashboard**. Check it every couple of days.
- On **day 3**, nudge anyone who hasn't opted in. On **day 7**, send everyone a short check-in
  ("anything weird so far?"). Log feedback in the sheet; you'll need it for §9.
- If you ship a fix during the test, upload it to the same closed track with a higher
  `versionCode`. Testers update normally, and the clock keeps running.

---

## 9. Apply for production access

When the Dashboard shows the requirement is met: **Dashboard → Apply for production** (or the
**Production** page → **Apply**). Google asks roughly:

- **How did you recruit testers?** Be concrete: "Friends, family, and people from my Austin
  community who use Android. About 20 invited; N stayed for 14 days."
- **How easy was it to recruit them?** Answer honestly.
- **How did testers use the app?** Real use: made profiles, matched, messaged, made plans.
- **What feedback did you get, and what did you change?** List 2–4 real things from the sheet,
  with the versions that fixed them.
- **Who is the app for, and what value does it provide?** "Adults in Austin who want to make
  friends. It matches people on shared activities and overlapping free time, then helps them
  plan a real meetup. It's free, with no ads and no subscription."
- **How many installs do you expect in the first year?** A realistic guess.
- **Is the app ready for production?** Yes, with what changed during testing.

Approval usually takes up to about a week. If Google says the testing wasn't enough, run the
closed test longer with more active testers and apply again.

---

## 10. Production release

1. **Test and release → Production → Countries/regions → Add countries:** **United States**
   only. The app works only in Austin, so this keeps reviews and support local.
2. **Create new release** → promote the release from the closed track, or upload a new bundle.
3. Release notes: `ATX Friends is here: find friends in Austin who like what you like.`
4. **Staged rollout:** start at **20%**. If crashes and reviews look fine after 2–3 days, raise it
   to 100% (Production → Releases → Manage rollout).
5. **Send for review.** The first production review is usually 1–3 days, occasionally up to 7.
6. When it's live, open the listing on your phone and check the text, the screenshots, and the
   privacy link.

After launch, **Monitor and improve → Android vitals** shows crashes and freezes (ANRs), and
**Ratings and reviews** lets you reply to reviews.

---

## Appendix A. Data safety form

**Policy and programs → App content → Data safety.** These answers are based on what the
**Android app and backend actually do** as of September 25, 2026 (the Android code,
`firestore.rules`, and `functions/src/index.ts`). The form covers only the Android app; iOS is
declared to Apple separately.

Google's definitions decide the answers:
- **Collected** means sent off the device (to Firebase, here).
- **Shared** means sent to a *third party*. Firebase and Google Cloud are **service providers**,
  so sending data to them isn't "sharing." Data people deliberately show each other (profiles
  to nearby members, messages to a match) is a **user-initiated transfer** the user expects,
  which Google also excludes from "sharing."
- **Required** means the user can't use the app without it being collected. **Optional** means
  the user chooses.

Items marked ⚠️ are ones I'm not certain about. See the list after the tables.

### Section 1: Data collection and security

| Question | Answer |
|---|---|
| Does your app collect or share any of the required user data types? | **Yes** |
| Is all of the user data collected by your app encrypted in transit? | **Yes.** All traffic is Firebase SDK calls and Firebase Storage photo URLs over HTTPS/TLS. The app makes no plain-HTTP requests. |
| Which methods of account creation does your app support? | **Username and password** (email), and **OAuth** (Sign in with Google) |
| Delete account URL | `https://atxfriends.app/delete-account` (source: `website/delete-account.html`; text in Appendix B) |
| Do you provide a way for users to request that some or all of their data is deleted, without requiring them to delete their account? | **No** ⚠️. Users can edit their profile, but there's no way to request deletion short of deleting the account. |
| Has your app's data collection been independently validated against a global security standard (MASA)? | **No** |
| Is your app committed to following the Play Families Policy? | **No** (18+ only) |

### Section 2: Data types

For every row: **Shared = No** and **Processed ephemerally = No**. No data is used for
advertising, marketing, analytics, personalization, or fraud prevention.

| Category → data type | Collected | Required or optional | Purposes | What it is, in the code |
|---|---|---|---|---|
| **Location → Approximate location** | Yes | **Required** | App functionality | Sign-up saves a coarse location snapped to about a 0.7-mile grid (`SignupWrites.newUser`, `CoarseLocation.snap`). Share My Location updates it (`LocationSharing`). Only `ACCESS_COARSE_LOCATION` is requested. |
| Location → Precise location | **No** | | | Never requested |
| **Personal info → Name** | Yes | Required | App functionality, Account management | `displayName` |
| **Personal info → Email address** | Yes | Required | Account management; **Developer communications** ⚠️ | The Firebase Auth sign-in email (not stored in Firestore). Also the **waitlist** email (`waitlistSignups`) from people outside Austin, who don't create an account. |
| **Personal info → User IDs** | Yes | Required | App functionality, Account management | The Firebase Auth UID, plus the Google account ID when someone signs in with Google |
| Personal info → Address, phone number, race and ethnicity, political or religious beliefs, sexual orientation, other info | **No** | | | |
| Financial info (all types) | **No** | | | |
| Health and fitness (all types) | **No** ⚠️ | | | See the "Drinking" note below |
| **Messages → Other in-app messages** | Yes | Optional | App functionality | `messages` between mutual matches, including plan proposals |
| Messages → Emails, SMS or MMS | **No** | | | |
| **Photos and videos → Photos** | Yes | Required | App functionality | The 3 profile photos, re-encoded as JPEG (which drops EXIF data) and stored in Firebase Storage |
| Photos and videos → Videos | **No** | | | |
| Audio (all types) | **No** | | | |
| Files and docs | **No** | | | Export My Data writes a text file on the phone and hands it to the share sheet. Nothing is uploaded. |
| Calendar → Calendar events | **No** | | | "Add to Calendar" opens the phone's calendar app with a prefilled event and needs no calendar permission |
| Contacts | **No** | | | |
| **App activity → Other user-generated content** | Yes | Required | App functionality | Activities (including new activity names added to the shared list), day and slot availability, search radius, plans (1:1, Today, group), Simpatico answers |
| **App activity → Other actions** ⚠️ | Yes | Optional | App functionality | Yay/Nay decisions, blocks, "They showed up!" reports |
| App activity → App interactions, In-app search history, Installed apps | **No** | | | The activity search box and the place field aren't stored |
| Web browsing | **No** | | | |
| App info and performance → Crash logs, Diagnostics, Other | **No** ⚠️ | | | No Crashlytics, Analytics, or Performance Monitoring in `build.gradle.kts` |
| **Device or other IDs** | Yes | **Required** ⚠️ | App functionality | The FCM registration token, stored in `users/{uid}.fcmTokens` on every sign-in (`PushTokens.onSignedIn`), whether or not notifications are allowed |

### ⚠️ Answers I'm not sure about

1. **Email → "Developer communications."** The waitlist email is kept so you can tell people when
   the app reaches them. That's Google's "Developer communications" purpose. If you'll never
   email the waitlist, drop that purpose (and consider dropping the waitlist).
2. **Device or other IDs: Required or Optional.** The app saves the FCM token on every sign-in,
   even if notifications are denied, so users can't avoid it, which makes it **Required**. If
   you'd rather mark it Optional, change the app to store the token only when notification
   permission is granted.
3. **What the Firebase SDKs collect on their own.** Google's Firebase data disclosure page
   (<https://firebase.google.com/docs/android/play-data-disclosure>) lists what each SDK
   collects. Check Auth, Firestore, Storage, Functions, Messaging, and Installations against the
   tables above. In particular, Firebase Installations collects a **Firebase installation ID**
   (covered by "Device or other IDs"), and some SDKs may send **IP addresses** or limited
   **diagnostics**. If the page lists anything not in the tables, add it.
4. **"Other actions" or "Other user-generated content."** Yay/Nay decisions are closest to
   "likes," which Google puts under **Other actions**. Some developers put them under
   user-generated content instead. Either is defensible, as long as they're declared.
5. **Profiles shown to nearby members count as "not shared."** Google excludes user-initiated
   transfers the user expects. But the profile is visible to *all* signed-in members (not only
   matches), and the full `users` doc (with the rounded location and `fcmTokens`) is readable by
   any signed-in user (blocker 6). The profile part is expected; the tokens aren't. Fixing the
   rules makes the "No" cleaner.
6. **Simpatico "Drinking: I…"** is treated as a lifestyle preference, not health information. A
   cautious lawyer might disagree.
7. **Reports.** "Report a User" stores reports in `reports`. They fall under **Other
   user-generated content** (already declared) with the purpose **App functionality**. Consider
   adding **Fraud prevention, security, and compliance**.

Settled, not uncertain: reports are kept for one year (`purgeOldReports`), and deleted accounts
can remain in backups for up to 98 days (point-in-time recovery 7 days, daily backups 98 days,
per the Firestore console). The privacy policy and Appendix B say both.

Update this form whenever the Android app starts collecting something new.

---

## Appendix B. Account deletion web page

Play requires a public URL (no login) that names the app or developer, explains how to delete an
account, and says what's deleted, what's kept, and for how long. Put this at
`https://atxfriends.app/delete-account` (with Firebase Hosting's `cleanUrls`, or as
`delete-account.html`), and paste the URL into the Data safety form.

> **Delete your ATX Friends account**
>
> **In the app (fastest):** open ATX Friends → **Settings** → **Privacy & Safety** → **Delete
> Account** → confirm. Your account is deleted right away. This works the same on iPhone and
> Android.
>
> **Can't open the app?** Email {{CONTACT_EMAIL}} from the email address on your account, with
> the subject "Delete my account." We'll delete it within 30 days and reply when
> it's done.
>
> **What's deleted:** your profile, name, and photos; your sign-in account; your matches and all
> the messages in them (they disappear for the other person too); your plans and Today plans;
> group plans you host (you're removed from ones you were invited to); your Simpatico answers;
> and show-up reports you made or received.
>
> **What's kept:**
> - activity names you added to the shared activity list (they aren't linked to you),
> - reports you made about someone, or that someone made about you, for **one year** after
>   they were filed, for safety. They're then deleted automatically.
> - database backups (up to 98 days) and server logs (about 30 days). Your deleted account
>   disappears from these as they expire, and we never restore a deleted account from them.
>
> Waitlist emails aren't linked to an account. To remove one, email us.
>
> ATX Friends is made by {{LEGAL_NAME}}, Austin, Texas.
