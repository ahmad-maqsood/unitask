const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");
const firebaseCompat = require("firebase/compat/app");
const firebase = firebaseCompat.default || firebaseCompat;
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

// ---------------------------------------------------------------------------
// Group tasks tests
// ---------------------------------------------------------------------------

const GROUP_ID = "group_test_1";
const CHARLIE_UID = "charlie_789";

function getServerTimestamp() {
  return firebase.firestore.FieldValue.serverTimestamp;
}

async function setupTestGroup({ adminUid, memberUid, otherMemberUid }) {
  const serverTimestamp = getServerTimestamp();
  await testEnv.withSecurityRulesDisabled(async (context) => {
    const db = context.firestore();
    await db.collection("groups").doc(GROUP_ID).set({
      ownerId: "owner_user",
      name: "Test Group",
      username: "test_group",
      joinCode: "12345678",
      createdAt: serverTimestamp(),
    });
    if (adminUid) {
      await db.collection("groups").doc(GROUP_ID).collection("members").doc(adminUid).set({
        uid: adminUid,
        displayName: "Admin User",
        role: "admin",
        joinedAt: serverTimestamp(),
      });
    }
    if (memberUid) {
      await db.collection("groups").doc(GROUP_ID).collection("members").doc(memberUid).set({
        uid: memberUid,
        displayName: "Member User",
        role: "member",
        joinedAt: serverTimestamp(),
      });
    }
    if (otherMemberUid) {
      await db.collection("groups").doc(GROUP_ID).collection("members").doc(otherMemberUid).set({
        uid: otherMemberUid,
        displayName: "Other Member",
        role: "member",
        joinedAt: serverTimestamp(),
      });
    }
  });
}

test("Group tasks: A member can create a group task with a valid assignedTo list", async () => {
  await setupTestGroup({ memberUid: ALICE_UID });
  const serverTimestamp = getServerTimestamp();
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertSucceeds(
    aliceDb.collection("groups").doc(GROUP_ID).collection("tasks").doc("task1").set({
      title: "Group Task 1",
      dueDateEpochMillis: 1729500000000,
      dueTimeHour: 14,
      dueTimeMinute: 0,
      priority: "HIGH",
      createdBy: ALICE_UID,
      createdByName: "Alice",
      createdAt: serverTimestamp(),
      updatedAt: serverTimestamp(),
      assignedTo: [ALICE_UID]
    })
  );
});

test("Group tasks: A member cannot create a group task with an empty assignedTo list", async () => {
  await setupTestGroup({ memberUid: ALICE_UID });
  const serverTimestamp = getServerTimestamp();
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertFails(
    aliceDb.collection("groups").doc(GROUP_ID).collection("tasks").doc("task_empty_assign").set({
      title: "Group Task Empty Assign",
      dueDateEpochMillis: 1729500000000,
      dueTimeHour: 14,
      dueTimeMinute: 0,
      priority: "HIGH",
      createdBy: ALICE_UID,
      createdByName: "Alice",
      createdAt: serverTimestamp(),
      updatedAt: serverTimestamp(),
      assignedTo: []
    })
  );
});

test("Group tasks: A member cannot create a group task with a disallowed field", async () => {
  await setupTestGroup({ memberUid: ALICE_UID });
  const serverTimestamp = getServerTimestamp();
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertFails(
    aliceDb.collection("groups").doc(GROUP_ID).collection("tasks").doc("task_disallowed_field").set({
      title: "Group Task Disallowed",
      dueDateEpochMillis: 1729500000000,
      dueTimeHour: 14,
      dueTimeMinute: 0,
      priority: "MEDIUM",
      createdBy: ALICE_UID,
      createdByName: "Alice",
      createdAt: serverTimestamp(),
      updatedAt: serverTimestamp(),
      assignedTo: [ALICE_UID],
      foo: 1
    })
  );
});

test("Group tasks: A member cannot create a group task whose createdBy is someone else", async () => {
  await setupTestGroup({ memberUid: ALICE_UID, otherMemberUid: BOB_UID });
  const serverTimestamp = getServerTimestamp();
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertFails(
    aliceDb.collection("groups").doc(GROUP_ID).collection("tasks").doc("task_fake_creator").set({
      title: "Group Task Fake Creator",
      dueDateEpochMillis: 1729500000000,
      dueTimeHour: 14,
      dueTimeMinute: 0,
      priority: "MEDIUM",
      createdBy: BOB_UID,
      createdByName: "Bob",
      createdAt: serverTimestamp(),
      updatedAt: serverTimestamp(),
      assignedTo: [ALICE_UID]
    })
  );
});

test("Group tasks: Non-member cannot read or create group tasks", async () => {
  await setupTestGroup({ memberUid: ALICE_UID });
  const serverTimestamp = getServerTimestamp();
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await context.firestore().collection("groups").doc(GROUP_ID).collection("tasks").doc("task1").set({
      title: "Existing Task",
      dueDateEpochMillis: 1729500000000,
      dueTimeHour: 10,
      dueTimeMinute: 0,
      priority: "LOW",
      createdBy: ALICE_UID,
      createdByName: "Alice",
      createdAt: serverTimestamp(),
      updatedAt: serverTimestamp()
    });
  });

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertFails(
    bobDb.collection("groups").doc(GROUP_ID).collection("tasks").doc("task1").get()
  );
  await assertFails(
    bobDb.collection("groups").doc(GROUP_ID).collection("tasks").doc("task_bob").set({
      title: "Bob Task",
      dueDateEpochMillis: 1729500000000,
      dueTimeHour: 14,
      dueTimeMinute: 0,
      priority: "MEDIUM",
      createdBy: BOB_UID,
      createdByName: "Bob",
      createdAt: serverTimestamp(),
      updatedAt: serverTimestamp(),
      assignedTo: [BOB_UID]
    })
  );
});

test("Group tasks: Non-creator non-admin member cannot delete task, but admin can", async () => {
  await setupTestGroup({ memberUid: ALICE_UID, otherMemberUid: BOB_UID, adminUid: CHARLIE_UID });
  const serverTimestamp = getServerTimestamp();
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await context.firestore().collection("groups").doc(GROUP_ID).collection("tasks").doc("task1").set({
      title: "Alice Task",
      dueDateEpochMillis: 1729500000000,
      dueTimeHour: 10,
      dueTimeMinute: 0,
      priority: "LOW",
      createdBy: ALICE_UID,
      createdByName: "Alice",
      createdAt: serverTimestamp(),
      updatedAt: serverTimestamp()
    });
  });

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertFails(
    bobDb.collection("groups").doc(GROUP_ID).collection("tasks").doc("task1").delete()
  );

  const charlieDb = testEnv.authenticatedContext(CHARLIE_UID).firestore();
  await assertSucceeds(
    charlieDb.collection("groups").doc(GROUP_ID).collection("tasks").doc("task1").delete()
  );
});


test("Group tasks: A member cannot create a group task with a fixed date in createdAt", async () => {
  await setupTestGroup({ memberUid: ALICE_UID });
  const serverTimestamp = getServerTimestamp();
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertFails(
    aliceDb.collection("groups").doc(GROUP_ID).collection("tasks").doc("task_fixed_date").set({
      title: "Group Task Fixed Date",
      dueDateEpochMillis: 1729500000000,
      dueTimeHour: 14,
      dueTimeMinute: 0,
      priority: "MEDIUM",
      createdBy: ALICE_UID,
      createdByName: "Alice",
      createdAt: new Date(),
      updatedAt: serverTimestamp(),
      assignedTo: [ALICE_UID]
    })
  );
});
