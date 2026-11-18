const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_operator_01";
const BOB_UID = "bob_operator_02";
const ADMIN_UID = "admin_boot_01";
const ADMIN_EMAIL = "mirchibiryani27625@gmail.com";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function pastTs(offsetMs = 5000) {
  return new Date(Date.now() - offsetMs);
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

// 1. Users & RBAC Privilege Escalation + Email Spoofing Tests
test("Users: owner create/update, no self-ADMIN escalation, bootstrapped verified admin can manage roles, unverified spoof fails", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("Users").doc(ALICE_UID).get());

  const aliceDb = testEnv.authenticatedContext(ALICE_UID, { email: "alice@example.com", email_verified: true }).firestore();
  const created = pastTs(10000);

  // Non-admin trying to self-assign ADMIN must fail
  await assertFails(
    aliceDb.collection("Users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      email: "alice@example.com",
      displayName: "Alice",
      role: "ADMIN",
      stationId: "NI-TURKI-01",
      isActive: true,
      photoUrl: "",
      lastLoginAt: created,
      createdAt: created,
      updatedAt: created,
    })
  );

  // Valid OPERATOR profile create succeeds
  await assertSucceeds(
    aliceDb.collection("Users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      email: "alice@example.com",
      displayName: "Alice Operator",
      role: "OPERATOR",
      stationId: "NI-TURKI-01",
      isActive: true,
      photoUrl: "",
      lastLoginAt: created,
      createdAt: created,
      updatedAt: created,
    })
  );

  // Owner cannot escalate own role on update
  await assertFails(
    aliceDb.collection("Users").doc(ALICE_UID).update({
      role: "ADMIN",
      updatedAt: pastTs(1000),
    })
  );

  // Valid owner profile login update succeeds
  await assertSucceeds(
    aliceDb.collection("Users").doc(ALICE_UID).update({
      displayName: "Alice Renamed",
      lastLoginAt: pastTs(1000),
      updatedAt: pastTs(1000),
    })
  );

  // Email spoofing with email_verified: false MUST fail admin operations
  const spoofDb = testEnv.authenticatedContext("spoof_uid", { email: ADMIN_EMAIL, email_verified: false }).firestore();
  await assertFails(spoofDb.collection("Users").get());
  await assertFails(
    spoofDb.collection("Users").doc(ALICE_UID).update({
      role: "ADMIN",
      isActive: true,
      updatedAt: pastTs(500),
    })
  );

  // Verified bootstrapped Admin can create ADMIN profile, list all users, and promote Alice
  const adminDb = testEnv.authenticatedContext(ADMIN_UID, { email: ADMIN_EMAIL, email_verified: true }).firestore();
  await assertSucceeds(
    adminDb.collection("Users").doc(ADMIN_UID).set({
      userId: ADMIN_UID,
      email: ADMIN_EMAIL,
      displayName: "Dairy Admin",
      role: "ADMIN",
      stationId: "NI-TURKI-01",
      isActive: true,
      photoUrl: "",
      lastLoginAt: created,
      createdAt: created,
      updatedAt: created,
    })
  );
  await assertSucceeds(adminDb.collection("Users").get());
  await assertSucceeds(
    adminDb.collection("Users").doc(ALICE_UID).update({
      role: "ADMIN",
      isActive: true,
      updatedAt: pastTs(500),
    })
  );
});

// 2. Farmers Collection Tests
test("Farmers: Kotlin create/update/query succeed, cross-user & ghost fields & bad docIds rejected", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("Farmers").get());

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const t = pastTs(8000);
  const docId = `${ALICE_UID}_F001`;

  // Invalid docId containing dots must fail
  await assertFails(
    aliceDb.collection("Farmers").doc(`${ALICE_UID}.F001`).set({
      userId: ALICE_UID,
      stationId: "NI-TURKI-01",
      farmerId: "F001",
      fullName: "Ramesh Kumar",
      mobileNumber: "9876543210",
      villageAddress: "Turki",
      milkCategory: "COW",
      photoUrl: "",
      bankName: "SBI",
      accountHolderName: "Ramesh Kumar",
      accountNumber: "1234567890",
      ifscCode: "SBIN0001234",
      createdAt: t,
      updatedAt: t,
    })
  );

  // Valid create
  await assertSucceeds(
    aliceDb.collection("Farmers").doc(docId).set({
      userId: ALICE_UID,
      stationId: "NI-TURKI-01",
      farmerId: "F001",
      fullName: "Ramesh Kumar",
      mobileNumber: "9876543210",
      villageAddress: "Turki",
      milkCategory: "COW",
      photoUrl: "",
      bankName: "SBI",
      accountHolderName: "Ramesh Kumar",
      accountNumber: "1234567890",
      ifscCode: "SBIN0001234",
      createdAt: t,
      updatedAt: t,
    })
  );

  // Filtered query succeeds, unfiltered fails
  await assertSucceeds(aliceDb.collection("Farmers").where("userId", "==", ALICE_UID).get());
  await assertFails(aliceDb.collection("Farmers").get());

  // Valid partial update
  await assertSucceeds(
    aliceDb.collection("Farmers").doc(docId).update({
      fullName: "Ramesh Kumar Updated",
      updatedAt: pastTs(1000),
    })
  );

  // Ghost field update rejected
  await assertFails(
    aliceDb.collection("Farmers").doc(docId).update({
      ghostField: "hack",
      updatedAt: pastTs(1000),
    })
  );

  // Cross-user isolation
  await assertFails(bobDb.collection("Farmers").doc(docId).get());
  await assertFails(bobDb.collection("Farmers").doc(docId).delete());
});

// 3, 4, 5. MilkCollection, MorningShift & EveningShift Tests
test("MilkCollection, MorningShift, EveningShift: exact Kotlin payloads & shift enforcement", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const t = pastTs(8000);
  const docId = `${ALICE_UID}_RCP-20261118-M-001`;

  const morningPayload = {
    userId: ALICE_UID,
    stationId: "NI-TURKI-01",
    receiptNumber: "RCP-20261118-M-001",
    date: "2026-11-18",
    shift: "MORNING",
    farmerId: "F001",
    farmerName: "Ramesh Kumar",
    milkCategory: "COW",
    quantity: 12.5,
    fat: 4.2,
    snf: 8.6,
    waterPercentage: 0.0,
    rate: 42.15,
    totalAmount: 526.88,
    machineAmount: 526.88,
    scannedImageUrl: "",
    ocrScanned: true,
    ocrConfidence: 96,
    machineNumber: "EV-01",
    actualTime: "07:15 AM",
    transactionId: "TXN-001",
    scanRecordId: "SCN-001",
    verifiedBy: "Admin Verified",
    month: 11,
    year: 2026,
    sourceType: "MACHINE_DISPLAY",
    verificationStatus: "ADMIN_VERIFIED",
    timestampMillis: 1763450000000,
    createdAt: t,
    updatedAt: t,
  };

  await assertSucceeds(aliceDb.collection("MilkCollection").doc(docId).set(morningPayload));
  await assertSucceeds(aliceDb.collection("MorningShift").doc(docId).set(morningPayload));
  // Wrong shift in EveningShift must fail
  await assertFails(aliceDb.collection("EveningShift").doc(docId).set(morningPayload));

  // Valid EveningShift entry
  const evDocId = `${ALICE_UID}_RCP-20261118-E-001`;
  const eveningPayload = { ...morningPayload, receiptNumber: "RCP-20261118-E-001", shift: "EVENING" };
  await assertSucceeds(aliceDb.collection("EveningShift").doc(evDocId).set(eveningPayload));

  // Update quantity & amount preserves createdAt/userId
  await assertSucceeds(
    aliceDb.collection("MilkCollection").doc(docId).update({
      quantity: 13.0,
      totalAmount: 547.95,
      updatedAt: pastTs(500),
    })
  );

  // Future timestamp rejected
  await assertFails(
    aliceDb.collection("MilkCollection").doc(docId).update({
      updatedAt: new Date(Date.now() + 60000),
    })
  );

  await assertSucceeds(aliceDb.collection("MilkCollection").where("userId", "==", ALICE_UID).get());
  await assertSucceeds(aliceDb.collection("MorningShift").where("userId", "==", ALICE_UID).get());
  await assertSucceeds(aliceDb.collection("EveningShift").where("userId", "==", ALICE_UID).get());
});

// 6, 7, 8, 9, 10, 11, 12 Remaining Collections Tests
test("MilkRates, Payments, Expenses, DailyReports, MonthlyReports, Settings, Notifications parity & isolation", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const t = pastTs(6000);

  // 6. MilkRates
  const rateId = `${ALICE_UID}_COW`;
  await assertSucceeds(
    aliceDb.collection("MilkRates").doc(rateId).set({
      userId: ALICE_UID,
      stationId: "NI-TURKI-01",
      category: "COW",
      baseRate: 38.0,
      baseFat: 3.5,
      fatMultiplier: 5.5,
      baseSnf: 8.5,
      snfMultiplier: 3.0,
      waterDeductionPerPercent: 0.5,
      createdAt: t,
      updatedAt: t,
    })
  );
  await assertSucceeds(aliceDb.collection("MilkRates").where("userId", "==", ALICE_UID).get());

  // 7. Payments
  const payId = `${ALICE_UID}_PAY-20261118-101`;
  await assertSucceeds(
    aliceDb.collection("Payments").doc(payId).set({
      userId: ALICE_UID,
      stationId: "NI-TURKI-01",
      transactionId: "PAY-20261118-101",
      farmerId: "F001",
      farmerName: "Ramesh Kumar",
      paymentAmount: 500.0,
      paymentDate: "2026-11-18",
      paymentMode: "UPI",
      paymentType: "WEEKLY",
      note: "Weekly payout",
      previousBalance: 1200.0,
      finalBalance: 700.0,
      timestampMillis: 1763450000000,
      createdAt: t,
      updatedAt: t,
    })
  );
  await assertFails(bobDb.collection("Payments").doc(payId).get());

  // 8. Expenses
  const expId = `${ALICE_UID}_EXP-20261118-201`;
  await assertSucceeds(
    aliceDb.collection("Expenses").doc(expId).set({
      userId: ALICE_UID,
      stationId: "NI-TURKI-01",
      expenseId: "EXP-20261118-201",
      title: "BMC Chiller Diesel",
      category: "MAINTENANCE",
      amount: 950.0,
      date: "2026-11-18",
      paymentMode: "CASH",
      note: "Generator backup",
      recordedBy: "Alice Operator",
      timestampMillis: 1763450000000,
      createdAt: t,
      updatedAt: t,
    })
  );
  await assertSucceeds(aliceDb.collection("Expenses").where("userId", "==", ALICE_UID).get());

  // 9. DailyReports
  const dailyId = `${ALICE_UID}_2026-11-18`;
  await assertSucceeds(
    aliceDb.collection("DailyReports").doc(dailyId).set({
      userId: ALICE_UID,
      stationId: "NI-TURKI-01",
      date: "2026-11-18",
      totalWeight: 45.5,
      totalAmount: 1920.0,
      morningWeight: 25.5,
      eveningWeight: 20.0,
      avgFat: 4.3,
      avgSnf: 8.6,
      entriesCount: 4,
      totalPaid: 500.0,
      totalExpenses: 950.0,
      createdAt: t,
      updatedAt: t,
    })
  );

  // 10. MonthlyReports
  const monthlyId = `${ALICE_UID}_2026-11`;
  await assertSucceeds(
    aliceDb.collection("MonthlyReports").doc(monthlyId).set({
      userId: ALICE_UID,
      stationId: "NI-TURKI-01",
      yearMonth: "2026-11",
      year: 2026,
      month: 11,
      totalWeight: 620.0,
      totalAmount: 26500.0,
      cowWeight: 400.0,
      buffaloWeight: 220.0,
      entriesCount: 52,
      totalPaid: 12000.0,
      totalExpenses: 1900.0,
      createdAt: t,
      updatedAt: t,
    })
  );

  // 11. Settings
  const setId = `${ALICE_UID}_NI-TURKI-01`;
  await assertSucceeds(
    aliceDb.collection("Settings").doc(setId).set({
      userId: ALICE_UID,
      stationId: "NI-TURKI-01",
      dairyName: "दुध केंद्र",
      centerName: "नमस्ते इंडिया दूध संग्रहण केंद्र, तुर्की",
      address: "तुर्की, मुजफ्फरपुर",
      contactNumber: "9876543210",
      morningStartHour: 4,
      morningStartMinute: 0,
      morningEndHour: 11,
      morningEndMinute: 59,
      eveningStartHour: 12,
      eveningStartMinute: 0,
      eveningEndHour: 23,
      eveningEndMinute: 59,
      amountToleranceRs: 0.5,
      createdAt: t,
      updatedAt: t,
    })
  );

  // 12. Notifications
  const ntfId = `${ALICE_UID}_NTF-1001`;
  await assertSucceeds(
    aliceDb.collection("Notifications").doc(ntfId).set({
      userId: ALICE_UID,
      stationId: "NI-TURKI-01",
      notificationId: "NTF-1001",
      title: "नया भुगतान दर्ज",
      message: "Ramesh Kumar को ₹500.00 भुगतान किया गया।",
      type: "PAYMENT",
      read: false,
      timestampMillis: 1763450000000,
      createdAt: t,
      updatedAt: t,
    })
  );
  await assertSucceeds(
    aliceDb.collection("Notifications").doc(ntfId).update({
      read: true,
      updatedAt: pastTs(500),
    })
  );
  await assertSucceeds(aliceDb.collection("Notifications").where("userId", "==", ALICE_UID).get());

  // RBAC: Operator (ALICE_UID) CANNOT update MilkRates/Settings or delete master/payment records
  await assertFails(
    aliceDb.collection("MilkRates").doc(rateId).update({
      baseRate: 45.0,
      updatedAt: pastTs(400),
    })
  );
  await assertFails(
    aliceDb.collection("Settings").doc(setId).update({
      centerName: "Tampered Center",
      updatedAt: pastTs(400),
    })
  );
  await assertFails(aliceDb.collection("Payments").doc(payId).delete());
  await assertFails(aliceDb.collection("Expenses").doc(expId).delete());

  // Verified Admin CAN update MilkRates, Settings, and delete test records
  const adminDb = testEnv.authenticatedContext(ADMIN_UID, { email: ADMIN_EMAIL, email_verified: true }).firestore();
  await assertSucceeds(
    adminDb.collection("MilkRates").doc(rateId).update({
      baseRate: 41.0,
      updatedAt: pastTs(300),
    })
  );
  await assertSucceeds(
    adminDb.collection("Settings").doc(setId).update({
      centerName: "नमस्ते इंडिया दूध संग्रहण केंद्र (सत्यापित)",
      updatedAt: pastTs(300),
    })
  );
  await assertSucceeds(adminDb.collection("Expenses").doc(expId).delete());
});

