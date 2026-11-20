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

function validProfile(uid) {
  const now = new Date(Date.now() - 1000);
  return {
    userId: uid,
    displayName: "Alice Student",
    email: "alice@example.com",
    learningLevel: "Intermediate",
    learningStyle: "Visual",
    dailyGoalMinutes: 30,
    preferredModel: "gemini-3.8-flash",
    memoryNotes: ["Prefers visual graphs for calculus"],
    focusSubjects: ["Mathematics", "Physics"],
    streakDays: 3,
    totalMinutesStudied: 120,
    createdAt: now,
    updatedAt: now,
  };
}

function validConversation() {
  const now = new Date(Date.now() - 1000);
  return {
    title: "Quadratic Equations",
    subject: "Mathematics",
    topic: "Vertex Form & Roots",
    boardType: "MATH",
    boardElements: [
      {
        id: "el_1",
        kind: "FORMULA",
        text: "y = a(x - h)^2 + k",
        x: 0.2,
        y: 0.25,
        colorHex: "#F4B860",
      },
    ],
    lastMessagePreview: "Let's explore the vertex!",
    messageCount: 1,
    starred: false,
    archived: false,
    createdAt: now,
    updatedAt: now,
  };
}

test("Unauthenticated users cannot read or write any user collections", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).set(validProfile(ALICE_UID)));
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("conversations").get()
  );
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("quizzes").get()
  );
});

test("User profile: owner create, partial updates, PII list isolation, and cross-user denial", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set(validProfile(ALICE_UID))
  );

  // Partial update on preferences
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).update({
      learningStyle: "Socratic",
      dailyGoalMinutes: 45,
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Partial update on AI memory notes
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).update({
      memoryNotes: ["Prefers visual graphs", "Strong in algebra"],
      updatedAt: new Date(Date.now() - 400),
    })
  );

  // Reject ghost field / shadow update
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      isAdmin: true,
      updatedAt: new Date(Date.now() - 300),
    })
  );

  // Reject future timestamp
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      dailyGoalMinutes: 60,
      updatedAt: new Date(Date.now() + 60000),
    })
  );

  // Reject list on /users (PII protection)
  await assertFails(aliceDb.collection("users").get());

  // Cross-user read & write denied
  await assertFails(bobDb.collection("users").doc(ALICE_UID).get());
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).update({ displayName: "Hijacked" })
  );
});

test("Conversations & Messages: create, chalkboard update, parent gate, immutable messages", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const convRef = aliceDb
    .collection("users")
    .doc(ALICE_UID)
    .collection("conversations")
    .doc("chat_1");

  // Reject orphan message before parent conversation exists
  await assertFails(
    convRef.collection("messages").doc("msg_0").set({
      role: "user",
      text: "Hello",
      groundingSources: [],
      hasBoardAction: false,
      hasImageAttachment: false,
      createdAt: new Date(Date.now() - 1000),
    })
  );

  await assertSucceeds(convRef.set(validConversation()));

  // Update chalkboard elements
  await assertSucceeds(
    convRef.update({
      boardType: "GRAPH",
      boardElements: [
        { id: "el_2", kind: "LABEL", text: "Parabola", x: 0.5, y: 0.4, colorHex: "#5CE0D2" },
      ],
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Reject @DocumentId 'id' inside doc body
  await assertFails(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("conversations")
      .doc("chat_2")
      .set({ id: "chat_2", ...validConversation() })
  );

  // Valid message creation
  await assertSucceeds(
    convRef.collection("messages").doc("msg_1").set({
      role: "model",
      text: "Here is the parabola on your chalkboard.",
      groundingSources: ["https://example.org/parabola"],
      hasBoardAction: true,
      hasImageAttachment: false,
      createdAt: new Date(Date.now() - 400),
    })
  );

  // Immutable messages cannot be updated
  await assertFails(
    convRef.collection("messages").doc("msg_1").update({ text: "Tampered" })
  );

  // Filtered/subcollection list query succeeds for owner, fails for Bob
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("conversations").get()
  );
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("conversations").get()
  );
});

test("Quizzes, Flashcards, Resources, and Progress: full CRUD and validation bounds", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const now = new Date(Date.now() - 1000);

  // Quiz
  const quizRef = aliceDb.collection("users").doc(ALICE_UID).collection("quizzes").doc("q_1");
  await assertSucceeds(
    quizRef.set({
      title: "Kinematics Check",
      subject: "Physics",
      topic: "Projectile Motion",
      difficulty: "Medium",
      questions: [
        {
          prompt: "What is horizontal acceleration in ideal projectile motion?",
          type: "MCQ",
          options: ["0 m/s^2", "9.8 m/s^2", "-9.8 m/s^2", "4.9 m/s^2"],
          correctIndex: 0,
          expectedAnswer: "0 m/s^2",
          explanation: "Gravity acts only vertically.",
        },
      ],
      completed: false,
      score: 0,
      correctCount: 0,
      totalCount: 1,
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertSucceeds(
    quizRef.update({
      completed: true,
      score: 100,
      correctCount: 1,
      updatedAt: new Date(Date.now() - 300),
    })
  );

  // Flashcards
  const deckRef = aliceDb.collection("users").doc(ALICE_UID).collection("flashcards").doc("d_1");
  await assertSucceeds(
    deckRef.set({
      title: "Cell Organelles",
      subject: "Biology",
      topic: "Eukaryotic Cells",
      cards: [
        { id: "c1", front: "Mitochondria", back: "ATP synthesis powerhouse", hint: "Energy", mastered: false },
      ],
      masteredCount: 0,
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertSucceeds(
    deckRef.update({
      cards: [
        { id: "c1", front: "Mitochondria", back: "ATP synthesis powerhouse", hint: "Energy", mastered: true },
      ],
      masteredCount: 1,
      updatedAt: new Date(Date.now() - 200),
    })
  );

  // Study resource
  const resRef = aliceDb.collection("users").doc(ALICE_UID).collection("resources").doc("r_1");
  await assertSucceeds(
    resRef.set({
      title: "Thermodynamics Guide",
      subject: "Physics",
      resourceType: "STUDY_GUIDE",
      content: "# First Law\nEnergy is conserved.",
      summary: "Summary of the First Law",
      createdAt: now,
      updatedAt: now,
    })
  );

  // Progress
  const progRef = aliceDb.collection("users").doc(ALICE_UID).collection("progress").doc("p_1");
  await assertSucceeds(
    progRef.set({
      subject: "Physics",
      topicId: "kinematics",
      topicTitle: "Projectile Motion",
      masteryPercent: 85,
      minutesSpent: 25,
      status: "IN_PROGRESS",
      createdAt: now,
      updatedAt: now,
    })
  );

  // Cross-user isolation
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("quizzes").get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("flashcards").get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("resources").get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("progress").get());
});
