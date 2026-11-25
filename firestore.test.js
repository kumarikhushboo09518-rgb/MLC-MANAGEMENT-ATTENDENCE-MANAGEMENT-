const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_teacher_1";
const BOB_UID = "bob_teacher_2";

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

function pastTime(offsetMs = 5000) {
  return new Date(Date.now() - offsetMs);
}

test("Unauthenticated user cannot read or write any collection", async () => {
  const unauth = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauth.collection("users").doc(ALICE_UID).get());
  await assertFails(unauth.collection("users").doc(ALICE_UID).collection("students").get());
  await assertFails(
    unauth.collection("users").doc(ALICE_UID).collection("students").doc("stu_1").set({
      userId: ALICE_UID,
      studentCode: "MLC-1001",
      fullName: "Rahul Kumar",
      active: true,
      createdAt: pastTime(),
      updatedAt: pastTime(),
    })
  );
});

test("UserAccount: owner create, partial update, reject ghost fields & cross-user access", async () => {
  const alice = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bob = testEnv.authenticatedContext(BOB_UID).firestore();
  const created = pastTime(10000);

  await assertSucceeds(
    alice.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      username: "teacher_alice",
      passwordHash: "a1b2c3d4e5f6g7h8i9j0",
      passwordSalt: "salt12345678",
      recoveryHash: "r1e2c3o4v5e6r7y8h9a0",
      recoverySalt: "rsalt12345678",
      rememberMe: true,
      createdAt: created,
      updatedAt: created,
    })
  );

  await assertSucceeds(
    alice.collection("users").doc(ALICE_UID).update({
      rememberMe: false,
      updatedAt: pastTime(1000),
    })
  );

  // Reject ghost field
  await assertFails(
    alice.collection("users").doc(ALICE_UID).update({
      isAdmin: true,
      updatedAt: pastTime(500),
    })
  );

  // Reject immutable createdAt modification
  await assertFails(
    alice.collection("users").doc(ALICE_UID).update({
      createdAt: pastTime(500),
      updatedAt: pastTime(500),
    })
  );

  // Reject cross-user read/write
  await assertFails(bob.collection("users").doc(ALICE_UID).get());
  await assertFails(bob.collection("users").doc(ALICE_UID).update({ username: "hijack", updatedAt: pastTime(500) }));
});

test("MLC Profile & App Settings: valid create/update and bounds enforcement", async () => {
  const alice = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bob = testEnv.authenticatedContext(BOB_UID).firestore();
  const created = pastTime(10000);

  await assertSucceeds(
    alice.collection("users").doc(ALICE_UID).collection("mlcProfiles").doc("default").set({
      userId: ALICE_UID,
      teacherName: "Anjali Sharma",
      mlcName: "Malabar MLC North",
      mlcLocation: "Sector 4, Kozhikode",
      createdAt: created,
      updatedAt: created,
    })
  );

  await assertSucceeds(
    alice.collection("users").doc(ALICE_UID).collection("mlcProfiles").doc("default").update({
      mlcLocation: "Sector 5, Kozhikode",
      updatedAt: pastTime(1000),
    })
  );

  await assertSucceeds(
    alice.collection("users").doc(ALICE_UID).collection("appSettings").doc("preferences").set({
      userId: ALICE_UID,
      languageCode: "hi",
      defaultFineAmount: 5,
      sundayHolidayEnabled: true,
      notificationsEnabled: true,
      createdAt: created,
      updatedAt: created,
    })
  );

  // Reject invalid language code
  await assertFails(
    alice.collection("users").doc(ALICE_UID).collection("appSettings").doc("preferences").update({
      languageCode: "fr",
      updatedAt: pastTime(500),
    })
  );

  // Reject cross-user read
  await assertFails(bob.collection("users").doc(ALICE_UID).collection("mlcProfiles").get());
  await assertFails(bob.collection("users").doc(ALICE_UID).collection("appSettings").get());
});

test("Students, Attendance, Fines, Payments: Kotlin-mirrored CRUD & immutability", async () => {
  const alice = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bob = testEnv.authenticatedContext(BOB_UID).firestore();
  const created = pastTime(10000);

  // Create student (NO @DocumentId `id` field)
  await assertSucceeds(
    alice.collection("users").doc(ALICE_UID).collection("students").doc("stu_1").set({
      userId: ALICE_UID,
      studentCode: "MLC-1001",
      fullName: "Rahul Verma",
      parentName: "Suresh Verma",
      active: true,
      createdAt: created,
      updatedAt: created,
    })
  );

  // Reject @DocumentId `id` key in payload
  await assertFails(
    alice.collection("users").doc(ALICE_UID).collection("students").doc("stu_2").set({
      id: "stu_2",
      userId: ALICE_UID,
      studentCode: "MLC-1002",
      fullName: "Bad Payload",
      active: true,
      createdAt: created,
      updatedAt: created,
    })
  );

  // Reject future timestamp
  await assertFails(
    alice.collection("users").doc(ALICE_UID).collection("students").doc("stu_3").set({
      userId: ALICE_UID,
      studentCode: "MLC-1003",
      fullName: "Future Time",
      active: true,
      createdAt: new Date(Date.now() + 60000),
      updatedAt: created,
    })
  );

  // Attendance record (ABSENT)
  await assertSucceeds(
    alice.collection("users").doc(ALICE_UID).collection("attendanceRecords").doc("2026-11-23_stu_1").set({
      userId: ALICE_UID,
      studentId: "stu_1",
      studentName: "Rahul Verma",
      dateKey: "2026-11-23",
      status: "ABSENT",
      createdAt: created,
      updatedAt: created,
    })
  );

  // Fine record (₹5)
  await assertSucceeds(
    alice.collection("users").doc(ALICE_UID).collection("fineRecords").doc("fine_2026-11-23_stu_1").set({
      userId: ALICE_UID,
      studentId: "stu_1",
      studentName: "Rahul Verma",
      attendanceDate: "2026-11-23",
      amount: 5,
      paidAmount: 0,
      status: "UNPAID",
      createdAt: created,
      updatedAt: created,
    })
  );

  // Correction ABSENT -> PRESENT and fine reversed
  await assertSucceeds(
    alice.collection("users").doc(ALICE_UID).collection("attendanceRecords").doc("2026-11-23_stu_1").update({
      status: "PRESENT",
      updatedAt: pastTime(1000),
    })
  );
  await assertSucceeds(
    alice.collection("users").doc(ALICE_UID).collection("fineRecords").doc("fine_2026-11-23_stu_1").update({
      amount: 0,
      status: "REVERSED",
      updatedAt: pastTime(1000),
    })
  );

  // Payment record create succeeds, update & delete strictly denied
  await assertSucceeds(
    alice.collection("users").doc(ALICE_UID).collection("paymentRecords").doc("pay_1").set({
      userId: ALICE_UID,
      studentId: "stu_1",
      studentName: "Rahul Verma",
      amountPaid: 5,
      remainingAfter: 0,
      paymentDateKey: "2026-11-23",
      paymentTimeText: "06:30 PM",
      createdAt: created,
      updatedAt: created,
    })
  );
  await assertFails(
    alice.collection("users").doc(ALICE_UID).collection("paymentRecords").doc("pay_1").update({
      amountPaid: 100,
      updatedAt: pastTime(500),
    })
  );
  await assertFails(
    alice.collection("users").doc(ALICE_UID).collection("paymentRecords").doc("pay_1").delete()
  );

  // List queries succeed for owner, fail for another teacher
  await assertSucceeds(alice.collection("users").doc(ALICE_UID).collection("students").get());
  await assertSucceeds(alice.collection("users").doc(ALICE_UID).collection("attendanceRecords").get());
  await assertSucceeds(alice.collection("users").doc(ALICE_UID).collection("fineRecords").get());
  await assertSucceeds(alice.collection("users").doc(ALICE_UID).collection("paymentRecords").get());

  await assertFails(bob.collection("users").doc(ALICE_UID).collection("students").get());
  await assertFails(bob.collection("users").doc(ALICE_UID).collection("fineRecords").get());
});
