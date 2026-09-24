// One-time backfill: snaps every existing user's stored latitude/longitude to the
// same coarse ~0.7 mi grid (2 decimal places) that the app now writes at signup
// and via the "Share My Location" setting. Closes the gap where a user who never
// changes their sharing mode would otherwise keep their exact signup coordinate
// exposed to matches indefinitely (see firestore.rules: any signed-in user can
// read the full doc of any complete profile — Firestore has no field-level rules).
//
// Never deployed as a Cloud Function — this is a local admin script, run once.
//
// Usage:
//   cd functions
//   npm install   # if not already done
//   GOOGLE_APPLICATION_CREDENTIALS=/path/to/service-account.json node scripts/backfillCoarseLocations.js --dry-run
//   GOOGLE_APPLICATION_CREDENTIALS=/path/to/service-account.json node scripts/backfillCoarseLocations.js
//
// A service account JSON is required (Firebase Console → Project Settings →
// Service Accounts → Generate new private key) — `firebase login`'s own session
// does not by itself provide Application Default Credentials for a script like
// this. --dry-run prints what would change without writing anything; run it first.

const admin = require("firebase-admin");

const isDryRun = process.argv.includes("--dry-run");

admin.initializeApp({
  credential: admin.credential.applicationDefault(),
});

const db = admin.firestore();

function snap(value) {
  return Math.round(value * 100) / 100;
}

async function main() {
  const snapshot = await db.collection("users").get();

  let scanned = 0;
  let changed = 0;
  let skippedNoCoords = 0;
  let skippedAlreadyCoarse = 0;

  let batch = db.batch();
  let opsInBatch = 0;

  for (const doc of snapshot.docs) {
    scanned++;
    const data = doc.data();
    const latitude = data.latitude;
    const longitude = data.longitude;

    if (typeof latitude !== "number" || typeof longitude !== "number" || (latitude === 0 && longitude === 0)) {
      skippedNoCoords++;
      continue;
    }

    const snappedLat = snap(latitude);
    const snappedLng = snap(longitude);

    if (snappedLat === latitude && snappedLng === longitude) {
      skippedAlreadyCoarse++;
      continue;
    }

    changed++;
    console.log(`[backfill] uid=${doc.id} (${latitude}, ${longitude}) -> (${snappedLat}, ${snappedLng})`);

    if (!isDryRun) {
      batch.update(doc.ref, {
        latitude: snappedLat,
        longitude: snappedLng,
        location: new admin.firestore.GeoPoint(snappedLat, snappedLng),
      });
      opsInBatch++;
      if (opsInBatch === 500) {
        await batch.commit();
        batch = db.batch();
        opsInBatch = 0;
      }
    }
  }

  if (!isDryRun && opsInBatch > 0) {
    await batch.commit();
  }

  console.log("");
  console.log(`[backfill] ${isDryRun ? "DRY RUN — " : ""}scanned=${scanned} changed=${changed} skippedNoCoords=${skippedNoCoords} skippedAlreadyCoarse=${skippedAlreadyCoarse}`);
}

main()
  .then(() => process.exit(0))
  .catch((error) => {
    console.error("[backfill] failed:", error);
    process.exit(1);
  });
