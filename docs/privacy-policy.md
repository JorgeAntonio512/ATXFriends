> **DRAFT: NOT FOR PUBLICATION YET.** This is a working draft for George to have a lawyer
> review before it goes live at `https://atxfriends.app/privacy`. It was written from what the
> iOS and Android code, Firestore rules, and Cloud Functions actually do as of September 25,
> 2026. It is not legal advice.
>
> Before publishing:
> 1. Replace every `{{…}}` placeholder.
> 2. Deploy the `reports` rule and the `purgeOldReports` function (`docs/android-play-release.md`,
>    blocker 5). The policy promises reporting works and reports are deleted after a year.
> 3. Ship the Firestore rules fix for messages described in `docs/android-play-release.md`
>    (blocker 6). Until then, "Who can see what" below isn't fully true.
> 4. Make sure `website/privacy.html` says the same thing. Play compares the policy with the
>    Data safety form.
> 5. Delete this box.

# ATX Friends Privacy Policy

**Effective {{EFFECTIVE_DATE}}**

**The short version:** we collect what the app needs to match you with people nearby and help
you meet them. We don't sell your data, we don't show ads, and we don't use advertising or
analytics trackers. When you delete your account, it's deleted right away.

## Who we are

ATX Friends is a friendship app for adults in and around Austin, Texas, available on iPhone and
Android. It's run by {{LEGAL_NAME}}, an independent developer in Austin ("we," "us"). If you have
questions about this policy or your data, email {{CONTACT_EMAIL}}.

## What we collect, and why

### Your account

- **Email address and password**, if you sign up with email. Your password is handled by
  Google Firebase Authentication. We never see or store it.
- **Sign-in details from Apple or Google**, if you sign in with one of them: a unique account
  ID, plus the name and email address they share with us. On iPhone, Apple may give us a
  private relay email address instead of your real one.
- **Your display name** (first name), which other people on ATX Friends see.

*Why:* to create your account, sign you in, and show people who you are.

### Your profile

- **Three profile photos.** The app re-saves each photo as a smaller JPEG before uploading it,
  which removes hidden photo details such as where the photo was taken.
- **Your activities** (3 main plus up to 7 extra) and **the days and times you're usually
  free.**
- **Your search radius** and **notification settings.**
- **Simpatico answers**, if you answer the optional compatibility questions. They cover how you
  like to hang out, such as plans, budget, punctuality, and drinking.
- **Activities you add to the shared list.** If you type an activity that isn't on the list,
  its name is added to the list everyone picks from. It isn't linked to your account.

*Why:* matching is based on shared activities and overlapping free time, and your profile is
what other people see when deciding whether to say Yay.

### Your location

- **At sign-up**, the app checks your location once to confirm you're within 50 miles of
  Austin, and saves an **approximate** location to your account. Before any location is saved,
  it's rounded to a grid of about 0.7 miles, so we never store your exact position.
- **Share My Location**, if you turn it on in Settings, updates that approximate location
  either once, when you tap "Update now," or each time you open the app (at most every 15
  minutes, and only if you've moved about half a mile).
- The app **never** reads your location in the background. On Android it asks only for
  approximate location.

*Why:* to find people within your search radius and to show matches a rough distance ("about 3
miles away"). The app shows other people a distance, never a position on a map.

### What you do in the app

- **Yay and Nay decisions** and the matches they create.
- **Messages** you send to your matches.
- **Plans:** one-on-one plans (activity, place, proposed times, and responses), open plans you
  post to the Today tab, and group plans you host or are invited to. If you pick a place from
  search results, its name and map coordinates are saved with the plan.
- **"They showed up!" reports** after a plan, which add to a simple count on each person's
  profile.
- **People you block.**
- **Reports** you make about another user: who you reported, the reason, any comments you add,
  and when. Only we can see reports; the person you report is never told. We keep them for one
  year.

*Why:* to run the app. Messages and plans go to the people they're meant for; show-up reports,
blocks, and reports keep things safe and friendly.

### Your device

- **A notification token.** When you're signed in, the app saves a token from Google Firebase
  Cloud Messaging (and, on iPhone, Apple Push Notification service) to your account, so we can
  send the notifications you've turned on: new matches, messages, plan requests,
  confirmations, and group plan updates. The token is removed from your account when you sign
  out.

### If you're outside Austin

- **Waitlist email.** If the sign-up location check says you're outside the Austin area, you can
  leave your email address so we can tell you if ATX Friends reaches you. We store only the
  email address and when you submitted it. No account is created.

### What we don't collect

- No background location tracking, and never your precise location.
- No contacts, no photo library access beyond the photos you pick, and no microphone.
- No advertising IDs, no ads, and no analytics or crash-reporting tools.
- **Calendars:** on Android, "Add to Calendar" opens your phone's calendar app with the event
  filled in; ATX Friends never reads your calendar. On iPhone, you can add a plan to Apple
  Calendar, Google Calendar, or Outlook. For Google and Outlook, you sign in to that service on
  your phone and the app creates that one event. Your calendar login isn't stored on our
  servers, and we don't read your calendar. (Outlook asks you to allow "read and write" access
  because Microsoft has no add-only option; ATX Friends only ever adds the event.)

## Who can see what

- **Other ATX Friends members near you** can see your profile (name, photos, activities, free
  times, Simpatico summary, and show-up count) so the app can find matches. Their app also
  receives your *rounded* location to work out a distance, but it only ever shows a rough
  distance.
- **Your matches** can see your messages to them and the plans you make together.
- **Open plans on the Today tab** can be seen by anyone signed in to ATX Friends until
  someone joins them.
- **Group plans** can be seen by the host and everyone invited.
- **Nobody** is told when you say Nay, block them, or report them.

## Who else handles your data

We don't sell your personal information, and we don't share it with anyone for advertising.
These companies process data for us, only to run the app:

| Company | What they do | What they handle |
|---|---|---|
| **Google (Firebase and Google Cloud)** | Hosts the database, photo storage, sign-in, server code, and notifications | Everything described above. The database is stored in Google's United States multi-region (`nam5`), and our server code runs in Iowa, USA. |
| **Google** | "Sign in with Google" (both apps); Google Calendar (iPhone, only if you choose it) | Sign-in details; the one calendar event you add |
| **Apple** | "Sign in with Apple," push notifications to iPhones, and place search when you make a plan on iPhone | Sign-in details; notification token; the text you type into place search |
| **Microsoft** | Outlook Calendar (iPhone, only if you choose it) | The one calendar event you add |

When you tap "Get Directions" or "Add to Calendar," your phone's maps or calendar app takes
over, and its own privacy policy applies.

We may disclose information if the law requires it, or if we believe it's necessary to protect
someone's safety.

## How we protect it

All data travels between the app and our servers over encrypted connections (TLS), and Google
encrypts it at rest. Database rules limit what each signed-in person can read and change.
Your password is never stored by us. No system is perfectly secure, but we work to keep your
data safe, and if a breach affects you we'll tell you as the law requires.

## How long we keep it, and deleting your account

We keep your data while you have an account.

**Delete your account anytime** in **Settings → Privacy & Safety → Delete Account**. It takes
effect immediately and can't be undone. We delete:

- your profile, photos, and sign-in account,
- your matches, **and every message in them**, so your conversations disappear for the other
  person too,
- your one-on-one plans and your Today plans,
- group plans you host (you're removed from group plans you were only invited to),
- your Simpatico answers,
- "They showed up!" reports you made or received.

Things that are **not** deleted:

- activity names you added to the shared list, because they aren't linked to you,
- events you added to your own calendar, which you can remove in your calendar app,
- a waitlist email, which isn't linked to an account (email us and we'll remove it),
- **reports** you made about someone, or that someone made about you. We keep these for **one
  year** after they're filed, for safety, and then delete them automatically,
- **backups and logs.** For recovery, our database keeps a rolling 7-day history and daily
  backups that are kept for 98 days. Server logs are kept for about 30 days. A deleted account
  can remain in these until they expire, **up to 98 days**. We never restore deleted accounts
  from them.

**Can't open the app?** Email {{CONTACT_EMAIL}} from the address on your account, or see
https://atxfriends.app/delete-account. We'll delete your account within 30 days.

## Your choices

- **See your data:** Settings → Privacy & Safety → **Export My Data** saves a copy of your
  profile, matches, messages, and plans as a text file.
- **Change it:** edit your name, photos, activities, times, and radius in Settings.
- **Location:** turn Share My Location off at any time. You can also turn off location access
  for ATX Friends in your phone's settings.
- **Notifications:** turn each kind on or off in Settings → Notifications, or turn them all off
  in your phone's settings.
- **Delete everything:** see above.

Depending on where you live, you may have other rights over your data. To use them, email
{{CONTACT_EMAIL}}.

## Age

ATX Friends is for adults **18 and older**. We don't knowingly collect information from anyone
younger. If you think a minor has made an account, email {{CONTACT_EMAIL}} and we'll remove it.

## Open source

The app's code is public, so anyone can check how it handles data. Other people may build their
own apps from that code. Those apps are run by them, not us, and this policy doesn't cover them.

## Changes to this policy

If we change this policy in a way that matters, we'll update the date at the top and let you
know in the app before the change takes effect.

## Contact

{{LEGAL_NAME}}, Austin, Texas<br>
{{CONTACT_EMAIL}}
