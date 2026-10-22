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

test("Unauthenticated user: cannot read courses or tasks", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("courses").get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("tasks").get());
});

test("Cross-user access: Bob cannot read Alice's courses or tasks", async () => {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await context.firestore().collection("users").doc(ALICE_UID).collection("courses").doc("cs101").set({
      userId: ALICE_UID,
      code: "CS 101",
      name: "Intro to Computer Science"
    });
  });

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("courses").doc("cs101").get());
});

test("Authenticated user: Alice can create and read her own courses", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("courses").doc("cs101").set({
      userId: ALICE_UID,
      code: "CS 101",
      name: "Intro to Computer Science",
      instructor: "Dr. Grace Hopper",
      colorHex: "#4F46E5"
    })
  );

  const doc = await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("courses").doc("cs101").get()
  );
  if (!doc.exists) throw new Error("Document should exist");
});

test("Authenticated user: Alice can create and read her own tasks", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("tasks").doc("task1").set({
      userId: ALICE_UID,
      title: "Homework 1",
      courseCode: "CS 101",
      dueDateEpochMillis: 1729500000000,
      priority: "HIGH",
      status: "PENDING"
    })
  );

  const doc = await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("tasks").doc("task1").get()
  );
  if (!doc.exists) throw new Error("Document should exist");
});

test("Validation: Alice cannot write task with invalid priority or missing title", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("tasks").doc("bad_task").set({
      userId: ALICE_UID,
      title: "",
      dueDateEpochMillis: 1729500000000,
      priority: "URGENT", // Not allowed
      status: "PENDING"
    })
  );
});
