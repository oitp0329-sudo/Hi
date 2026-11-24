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

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

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

test("Unauthenticated user cannot read or write any FileDude collection", async () => {
  const unauth = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauth.collection("users").doc(ALICE_UID).get());
  await assertFails(unauth.collection("users").doc(ALICE_UID).collection("walletDocuments").get());
  await assertFails(
    unauth.collection("users").doc(ALICE_UID).collection("walletDocuments").doc("doc_1").set({
      userId: ALICE_UID,
      title: "Passport",
      category: "PASSPORT",
      expiryDateMillis: 1900000000000,
      reminderOffsetsCsv: "90,30,7,1",
      renewed: false,
      createdAt: new Date(Date.now() - 1000),
      updatedAt: new Date(Date.now() - 1000),
    })
  );
});

test("Owner can create, update, and list walletDocuments; @DocumentId id field & ghost fields are rejected", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const created = new Date(Date.now() - 10000);

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("walletDocuments").doc("doc_1").set({
      userId: ALICE_UID,
      title: "Indian Passport",
      category: "PASSPORT",
      notes: "Renew before travel",
      expiryDateMillis: 1900000000000,
      reminderOffsetsCsv: "90,30,7,1",
      renewed: false,
      createdAt: created,
      updatedAt: created,
    })
  );

  // Partial update mirroring Kotlin map write
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("walletDocuments").doc("doc_1").update({
      renewed: true,
      lastRenewedAtMillis: Date.now() - 1000,
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // List subcollection succeeds for owner (including empty smartPresets)
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("walletDocuments").get()
  );
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("smartPresets").get()
  );

  // Reject ghost field / @DocumentId id field
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("walletDocuments").doc("doc_2").set({
      id: "doc_2",
      userId: ALICE_UID,
      title: "Bad Doc",
      category: "OTHER",
      expiryDateMillis: 1900000000000,
      reminderOffsetsCsv: "30,7",
      renewed: false,
      createdAt: created,
      updatedAt: created,
    })
  );

  // Reject future timestamp spoofing
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("walletDocuments").doc("doc_3").set({
      userId: ALICE_UID,
      title: "Future Spoof",
      category: "OTHER",
      expiryDateMillis: 1900000000000,
      reminderOffsetsCsv: "30,7",
      renewed: false,
      createdAt: new Date(Date.now() + 60000),
      updatedAt: created,
    })
  );
});

test("Cross-user isolation blocks Bob from reading or modifying Alice walletDocuments and smartPresets", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const created = new Date(Date.now() - 10000);

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("smartPresets").doc("preset_1").set({
      userId: ALICE_UID,
      name: "Passport 200KB",
      targetFormat: "JPG",
      maxSizeBytes: 204800,
      minWidthPx: 413,
      maxWidthPx: 600,
      minHeightPx: 531,
      maxHeightPx: 750,
      aspectRatio: "35:45",
      qualityPercent: 90,
      createdAt: created,
      updatedAt: created,
    })
  );

  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("smartPresets").get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("smartPresets").doc("preset_1").get());
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("smartPresets").doc("preset_1").update({
      name: "Hijacked",
      updatedAt: new Date(Date.now() - 1000),
    })
  );
});

test("User profile create, partial update, and feedback submission validate bounds", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const created = new Date(Date.now() - 10000);

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "Alice",
      email: "alice@example.com",
      planTier: "FREE",
      cloudSyncEnabled: true,
      createdAt: created,
      updatedAt: created,
    })
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).update({
      displayName: "Alice K.",
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Cannot mutate immutable createdAt
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      createdAt: new Date(Date.now() - 500),
      updatedAt: new Date(Date.now() - 500),
    })
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("feedback").doc("fb_1").set({
      userId: ALICE_UID,
      category: "FEATURE",
      subject: "Love Smart File Ready",
      message: "Works smoothly offline.",
      appVersion: "1.0",
      createdAt: created,
    })
  );
});
