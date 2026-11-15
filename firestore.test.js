const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_123";
const BOB_UID = "bob_456";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function serverTimestamp() {
  return new Date(Date.now() - 1000);
}

function validProfile(uid) {
  return {
    userId: uid,
    callsign: "صقر-1",
    totalFlights: 0,
    successfulLandings: 0,
    highestScore: 0,
    highestUnlockedTier: 1,
    tiltControlEnabled: false,
    soundEnabled: true,
    createdAt: serverTimestamp(),
    updatedAt: serverTimestamp(),
  };
}

function validFlightLog(uid, overrides = {}) {
  return {
    userId: uid,
    missionId: "moon_tranquility",
    missionName: "بحر السكون — القمر",
    success: true,
    score: 2450,
    ratingStars: 3,
    touchdownVerticalSpeed: 1.4,
    touchdownHorizontalSpeed: 0.6,
    touchdownTiltDeg: 2.5,
    fuelRemainingPercent: 48.2,
    flightDurationSec: 34.5,
    createdAt: serverTimestamp(),
    ...overrides,
  };
}

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

test("Unauthenticated user: cannot read or write profiles or flight logs", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("flight_logs").get()
  );
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).set(validProfile(ALICE_UID))
  );
});

test("Authenticated owner: can create and update their own PilotProfile", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const profileRef = aliceDb.collection("users").doc(ALICE_UID);
  await assertSucceeds(profileRef.set(validProfile(ALICE_UID)));

  await assertSucceeds(
    profileRef.update({
      totalFlights: 1,
      successfulLandings: 1,
      highestScore: 2450,
      highestUnlockedTier: 2,
      updatedAt: serverTimestamp(),
    })
  );
});

test("Cross-user isolation: Bob cannot read, list, or write Alice's profile or logs", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set(validProfile(ALICE_UID))
  );
  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("flight_logs")
      .doc("log_01")
      .set(validFlightLog(ALICE_UID))
  );

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertFails(bobDb.collection("users").doc(ALICE_UID).get());
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("flight_logs").get()
  );
  await assertFails(
    bobDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("flight_logs")
      .doc("log_02")
      .set(validFlightLog(BOB_UID))
  );
});

test("Shadow update & identity spoofing: ghost fields and userId spoofing fail", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const profileRef = aliceDb.collection("users").doc(ALICE_UID);

  // Ghost field on create
  await assertFails(
    profileRef.set({
      ...validProfile(ALICE_UID),
      isAdmin: true,
    })
  );

  // Spoofed userId
  await assertFails(
    profileRef.set({
      ...validProfile(BOB_UID),
    })
  );

  // Valid create, then attempt to mutate immutable createdAt
  await assertSucceeds(profileRef.set(validProfile(ALICE_UID)));
  await assertFails(
    profileRef.update({
      createdAt: serverTimestamp(),
      updatedAt: serverTimestamp(),
    })
  );
});

test("FlightLog validation: requires parent profile, valid missionId, and rejects updates", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const logRef = aliceDb
    .collection("users")
    .doc(ALICE_UID)
    .collection("flight_logs")
    .doc("log_01");

  // Fails before parent profile exists
  await assertFails(logRef.set(validFlightLog(ALICE_UID)));

  // Create parent profile
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set(validProfile(ALICE_UID))
  );

  // Fails with invalid missionId
  await assertFails(
    logRef.set(validFlightLog(ALICE_UID, { missionId: "pluto_hack" }))
  );

  // Fails with out-of-bounds stars
  await assertFails(
    logRef.set(validFlightLog(ALICE_UID, { ratingStars: 99 }))
  );

  // Succeeds with valid payload
  await assertSucceeds(logRef.set(validFlightLog(ALICE_UID)));

  // List own logs succeeds
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("flight_logs").get()
  );

  // Immutable: update fails
  await assertFails(logRef.update({ score: 99999 }));

  // Owner delete succeeds
  await assertSucceeds(logRef.delete());
});
