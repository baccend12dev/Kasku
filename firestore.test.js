const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_kasku_01";
const BOB_UID = "bob_kasku_02";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function validTxPayload(uid, overrides = {}) {
  const now = new Date(Date.now() - 2000);
  return {
    userId: uid,
    title: "Makan Siang Warteg",
    amount: 35000,
    type: "EXPENSE",
    categoryId: "cat_makan",
    categoryName: "Makan & Minum",
    paymentMethod: "EWALLET",
    dateMillis: Date.now() - 5000,
    yearMonth: "2026-11",
    dayOfMonth: 18,
    createdAt: now,
    updatedAt: now,
    ...overrides,
  };
}

function validCategoryPayload(uid, overrides = {}) {
  const now = new Date(Date.now() - 2000);
  return {
    userId: uid,
    name: "Makan & Minum",
    type: "EXPENSE",
    iconKey: "restaurant",
    colorHex: "#EA580C",
    budgetLimit: 1500000,
    createdAt: now,
    updatedAt: now,
    ...overrides,
  };
}

function validMonthConfigPayload(uid, yearMonth = "2026-11", overrides = {}) {
  const now = new Date(Date.now() - 2000);
  return {
    userId: uid,
    yearMonth,
    openingBalance: 5000000,
    monthlyBudget: 3500000,
    createdAt: now,
    updatedAt: now,
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

// --- UNAUTHENTICATED DENIAL ---
test("Unauthenticated user cannot read or write any collection", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("transactions").get()
  );
  await assertFails(
    unauthDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("transactions")
      .doc("tx_1")
      .set(validTxPayload(ALICE_UID))
  );
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("categories").get()
  );
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("monthConfigs").get()
  );
});

// --- TRANSACTIONS CRUD & SECURITY ---
test("Owner can create, query by yearMonth, partially update, and delete transactions", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const txCol = aliceDb
    .collection("users")
    .doc(ALICE_UID)
    .collection("transactions");

  // Create without optional note
  await assertSucceeds(txCol.doc("tx_1").set(validTxPayload(ALICE_UID)));

  // Create with optional note
  await assertSucceeds(
    txCol
      .doc("tx_2")
      .set(validTxPayload(ALICE_UID, { title: "Kopi Susu", amount: 28000, note: "Dengan es" }))
  );

  // Query matching Kotlin repository: whereEqualTo("yearMonth", yearMonth)
  await assertSucceeds(txCol.where("yearMonth", "==", "2026-11").get());
  // Full user-scoped list query (even empty or unfiltered under users/{uid})
  await assertSucceeds(txCol.get());

  // Partial update preserving createdAt and userId
  await assertSucceeds(
    txCol.doc("tx_1").update({
      title: "Makan Siang Padang",
      amount: 42000,
      note: "Tambah es teh",
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Delete succeeds for owner
  await assertSucceeds(txCol.doc("tx_2").delete());
});

test("Transactions reject invalid payloads, ghost fields, @DocumentId key, future timestamps, and immutable mutations", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const txCol = aliceDb
    .collection("users")
    .doc(ALICE_UID)
    .collection("transactions");

  // Reject @DocumentId 'id' field in write payload
  await assertFails(
    txCol.doc("tx_bad_id").set(validTxPayload(ALICE_UID, { id: "tx_bad_id" }))
  );

  // Reject ghost field
  await assertFails(
    txCol.doc("tx_ghost").set(validTxPayload(ALICE_UID, { isAdmin: true }))
  );

  // Reject zero or negative amount
  await assertFails(
    txCol.doc("tx_zero").set(validTxPayload(ALICE_UID, { amount: 0 }))
  );

  // Reject future timestamp
  await assertFails(
    txCol
      .doc("tx_future")
      .set(validTxPayload(ALICE_UID, { createdAt: new Date(Date.now() + 60000) }))
  );

  // Reject invalid yearMonth format
  await assertFails(
    txCol.doc("tx_bad_ym").set(validTxPayload(ALICE_UID, { yearMonth: "2026/11" }))
  );

  // Reject invalid paymentMethod
  await assertFails(
    txCol.doc("tx_bad_pm").set(validTxPayload(ALICE_UID, { paymentMethod: "CRYPTO" }))
  );

  // Seed valid doc then attempt mutating immutable createdAt or userId
  await assertSucceeds(txCol.doc("tx_valid").set(validTxPayload(ALICE_UID)));
  await assertFails(
    txCol.doc("tx_valid").update({
      createdAt: new Date(Date.now() - 500),
      updatedAt: new Date(Date.now() - 500),
    })
  );
  await assertFails(
    txCol.doc("tx_valid").update({
      userId: BOB_UID,
      updatedAt: new Date(Date.now() - 500),
    })
  );
});

// --- CATEGORIES CRUD & SECURITY ---
test("Owner can manage categories and invalid category writes fail", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const catCol = aliceDb
    .collection("users")
    .doc(ALICE_UID)
    .collection("categories");

  await assertSucceeds(catCol.doc("cat_makan").set(validCategoryPayload(ALICE_UID)));
  await assertSucceeds(catCol.get());

  await assertSucceeds(
    catCol.doc("cat_makan").update({
      name: "Kuliner & Jajan",
      budgetLimit: 2000000,
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Invalid color hex
  await assertFails(
    catCol.doc("cat_bad_color").set(validCategoryPayload(ALICE_UID, { colorHex: "red" }))
  );

  // Negative budgetLimit
  await assertFails(
    catCol.doc("cat_bad_budget").set(validCategoryPayload(ALICE_UID, { budgetLimit: -100 }))
  );

  await assertSucceeds(catCol.doc("cat_makan").delete());
});

// --- MONTH CONFIGS CRUD & SECURITY ---
test("Owner can create and update monthConfig keyed by YYYY-MM", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const cfgCol = aliceDb
    .collection("users")
    .doc(ALICE_UID)
    .collection("monthConfigs");

  await assertSucceeds(
    cfgCol.doc("2026-11").set(validMonthConfigPayload(ALICE_UID, "2026-11"))
  );
  await assertSucceeds(cfgCol.doc("2026-11").get());

  await assertSucceeds(
    cfgCol.doc("2026-11").update({
      openingBalance: 7500000,
      monthlyBudget: 4000000,
      weeklyBudget: 300000,
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Reject negative weeklyBudget
  await assertFails(
    cfgCol.doc("2026-11").update({
      weeklyBudget: -50000,
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Reject mismatched yearMonth vs docId
  await assertFails(
    cfgCol.doc("2026-12").set(validMonthConfigPayload(ALICE_UID, "2026-11"))
  );
});

// --- CROSS-USER ISOLATION ---
test("Cross-user access is strictly denied across all collections", async () => {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    const adminDb = context.firestore();
    await adminDb
      .collection("users")
      .doc(BOB_UID)
      .collection("transactions")
      .doc("bob_tx")
      .set(validTxPayload(BOB_UID));
    await adminDb
      .collection("users")
      .doc(BOB_UID)
      .collection("categories")
      .doc("bob_cat")
      .set(validCategoryPayload(BOB_UID));
    await adminDb
      .collection("users")
      .doc(BOB_UID)
      .collection("monthConfigs")
      .doc("2026-11")
      .set(validMonthConfigPayload(BOB_UID, "2026-11"));
    await adminDb
      .collection("users")
      .doc(BOB_UID)
      .collection("savingGoals")
      .doc("bob_goal")
      .set({
        userId: BOB_UID,
        name: "Dana Darurat",
        targetAmount: 10000000,
        currentAmount: 2500000,
        targetDateMillis: Date.now() + 86400000 * 180,
        iconKey: "savings",
        colorHex: "#0E6B4E",
        createdAt: new Date(Date.now() - 2000),
        updatedAt: new Date(Date.now() - 2000),
      });
    await adminDb
      .collection("users")
      .doc(BOB_UID)
      .collection("recurringTemplates")
      .doc("bob_rec")
      .set({
        userId: BOB_UID,
        title: "Bayar Kos",
        amount: 1500000,
        type: "EXPENSE",
        categoryId: "cat_tagihan",
        categoryName: "Tagihan & Utilitas",
        paymentMethod: "TRANSFER",
        dayOfMonth: 1,
        active: true,
        createdAt: new Date(Date.now() - 2000),
        updatedAt: new Date(Date.now() - 2000),
      });
  });

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertFails(
    aliceDb.collection("users").doc(BOB_UID).collection("transactions").get()
  );
  await assertFails(
    aliceDb.collection("users").doc(BOB_UID).collection("transactions").doc("bob_tx").get()
  );
  await assertFails(
    aliceDb.collection("users").doc(BOB_UID).collection("transactions").doc("bob_tx").delete()
  );
  await assertFails(
    aliceDb.collection("users").doc(BOB_UID).collection("categories").get()
  );
  await assertFails(
    aliceDb.collection("users").doc(BOB_UID).collection("monthConfigs").doc("2026-11").get()
  );
  await assertFails(
    aliceDb.collection("users").doc(BOB_UID).collection("savingGoals").get()
  );
  await assertFails(
    aliceDb.collection("users").doc(BOB_UID).collection("recurringTemplates").get()
  );
});

// --- SAVING GOALS CRUD & VALIDATION ---
test("Owner can create, top-up, update, and delete savingGoals while invalid payloads fail", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const goalCol = aliceDb
    .collection("users")
    .doc(ALICE_UID)
    .collection("savingGoals");

  const now = new Date(Date.now() - 2000);
  await assertSucceeds(
    goalCol.doc("goal_1").set({
      userId: ALICE_UID,
      name: "Dana Darurat",
      targetAmount: 15000000,
      currentAmount: 3000000,
      targetDateMillis: Date.now() + 86400000 * 365,
      iconKey: "savings",
      colorHex: "#0E6B4E",
      note: "6 bulan pengeluaran",
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertSucceeds(goalCol.get());

  // Top-up partial update
  await assertSucceeds(
    goalCol.doc("goal_1").update({
      currentAmount: 4500000,
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Invalid: targetAmount = 0
  await assertFails(
    goalCol.doc("goal_bad").set({
      userId: ALICE_UID,
      name: "Invalid",
      targetAmount: 0,
      currentAmount: 0,
      targetDateMillis: Date.now() + 86400000,
      iconKey: "savings",
      colorHex: "#0E6B4E",
      createdAt: now,
      updatedAt: now,
    })
  );

  // Reject ghost field
  await assertFails(
    goalCol.doc("goal_1").update({
      isVip: true,
      updatedAt: new Date(Date.now() - 500),
    })
  );

  await assertSucceeds(goalCol.doc("goal_1").delete());
});

// --- RECURRING TEMPLATES CRUD & VALIDATION ---
test("Owner can create, toggle active, mark lastAppliedYearMonth, and delete recurringTemplates", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const recCol = aliceDb
    .collection("users")
    .doc(ALICE_UID)
    .collection("recurringTemplates");

  const now = new Date(Date.now() - 2000);
  await assertSucceeds(
    recCol.doc("rec_gaji").set({
      userId: ALICE_UID,
      title: "Gaji Bulanan",
      amount: 8500000,
      type: "INCOME",
      categoryId: "cat_gaji",
      categoryName: "Gaji",
      paymentMethod: "TRANSFER",
      dayOfMonth: 25,
      active: true,
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertSucceeds(recCol.get());

  // Mark applied for 2026-11
  await assertSucceeds(
    recCol.doc("rec_gaji").update({
      lastAppliedYearMonth: "2026-11",
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Toggle inactive
  await assertSucceeds(
    recCol.doc("rec_gaji").update({
      active: false,
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Invalid dayOfMonth = 35
  await assertFails(
    recCol.doc("rec_bad_day").set({
      userId: ALICE_UID,
      title: "Internet",
      amount: 350000,
      type: "EXPENSE",
      categoryId: "cat_tagihan",
      categoryName: "Tagihan & Utilitas",
      paymentMethod: "TRANSFER",
      dayOfMonth: 35,
      active: true,
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertSucceeds(recCol.doc("rec_gaji").delete());
});
