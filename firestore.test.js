const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_uid_123";
const BOB_UID = "bob_uid_456";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function validSettingsPayload(uid, now = new Date(Date.now() - 2000)) {
  return {
    userId: uid,
    displayName: "Алиса",
    onboardingCompleted: true,
    dailyMidnightEnabled: true,
    maxRetryAttempts: 10,
    retryDelaySeconds: 3,
    twilightPreference: "EARLIEST_AUTO",
    alarmOffsetMinutes: 0,
    alarmLabel: "Самые ранние сумерки",
    locationMode: "MANUAL",
    latitude: 55.7558,
    longitude: 37.6173,
    locationName: "Москва · Россия",
    createdAt: now,
    updatedAt: now,
  };
}

function validCheckPayload(uid, now = new Date(Date.now() - 2000)) {
  return {
    userId: uid,
    dateLabel: "23.11.2026",
    locationName: "Москва · Россия",
    latitude: 55.7558,
    longitude: 37.6173,
    attemptsUsed: 2,
    maxAttempts: 10,
    succeeded: true,
    alarmSet: true,
    scheduledMidnight: true,
    twilightTime: "06:05",
    twilightStage: "Астрономические сумерки",
    astronomicalTime: "06:05",
    nauticalTime: "06:50",
    civilTime: "07:36",
    sunriseTime: "08:18",
    message: "Будильник поставлен на 06:05",
    createdAt: now,
    updatedAt: now,
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

test("Unauthenticated user cannot read or write user settings or checks", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).set(validSettingsPayload(ALICE_UID))
  );
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("twilightChecks").get()
  );
  await assertFails(
    unauthDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("twilightChecks")
      .doc("chk_1")
      .set(validCheckPayload(ALICE_UID))
  );
});

test("Owner can create and partially update UserSettings while preserving createdAt", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const created = new Date(Date.now() - 10000);

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set(validSettingsPayload(ALICE_UID, created))
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).update({
      maxRetryAttempts: 15,
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Reject mutating immutable createdAt
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      createdAt: new Date(Date.now() - 500),
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Reject shadow/ghost field injection
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      isAdmin: true,
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Reject out-of-range retry count
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      maxRetryAttempts: 99,
      updatedAt: new Date(Date.now() - 500),
    })
  );
});

test("Cross-user access to settings and twilightChecks is denied", async () => {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const adminDb = ctx.firestore();
    await adminDb.collection("users").doc(BOB_UID).set(validSettingsPayload(BOB_UID));
    await adminDb
      .collection("users")
      .doc(BOB_UID)
      .collection("twilightChecks")
      .doc("bob_chk")
      .set(validCheckPayload(BOB_UID));
  });

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertFails(aliceDb.collection("users").doc(BOB_UID).get());
  await assertFails(
    aliceDb.collection("users").doc(BOB_UID).update({ maxRetryAttempts: 5 })
  );
  await assertFails(
    aliceDb.collection("users").doc(BOB_UID).collection("twilightChecks").get()
  );
  await assertFails(
    aliceDb
      .collection("users")
      .doc(BOB_UID)
      .collection("twilightChecks")
      .doc("bob_chk")
      .delete()
  );
});

test("Owner can list, create, and delete twilightChecks; @DocumentId id field rejected", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const checksRef = aliceDb.collection("users").doc(ALICE_UID).collection("twilightChecks");

  await assertSucceeds(checksRef.doc("chk_1").set(validCheckPayload(ALICE_UID)));
  await assertSucceeds(checksRef.get());
  await assertSucceeds(checksRef.doc("chk_1").delete());

  // Reject redundant @DocumentId `id` field
  const badPayload = { ...validCheckPayload(ALICE_UID), id: "chk_2" };
  await assertFails(checksRef.doc("chk_2").set(badPayload));

  // Reject future tampered timestamp
  const futurePayload = validCheckPayload(ALICE_UID, new Date(Date.now() + 60000));
  await assertFails(checksRef.doc("chk_3").set(futurePayload));
});
