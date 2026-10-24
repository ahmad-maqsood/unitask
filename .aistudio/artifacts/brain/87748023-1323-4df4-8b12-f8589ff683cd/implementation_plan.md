# Error Diagnosis & Targeted Fix Plan

Identifies current runtime and backend errors across Firestore security rules, missing Firebase configuration, and broadcast receiver declarations, with a small-sized change estimate to fix them cleanly without touching unrelated features.

### User Review & Critical Decisions

> [!IMPORTANT]
> The following plan addresses only the identified errors and requests your confirmation before applying any modifications.

- **Confirmed Decision 1: Restore `app/google-services.json` in `.gitignore`**:
  Restore `app/google-services.json` so that Google Sign-In and Cloud Backup have the Web Client ID, while verifying that `**/google-services.json` and `app/google-services.json` remain ignored by git so credentials are never committed.
- **Confirmed Decision 2: Align Firestore Security Rules**:
  Update `firestore.rules` and deploy them via `deploy_firebase` to permit:
  1. `reminderOptions` field in `isValidGroupTask` for group tasks.
  2. Member role management (`setMemberRole`) on `/groups/{groupId}/members/{uid}` by owners.
  3. Group chat collection `/groups/{groupId}/messages/{messageId}` read/write for group members and deletion for message authors and group staff.
  4. Member removal (`removeMember`) by group owners and admins.
- **Confirmed Decision 3: BootReceiver Manifest Export**:
  Update `BootReceiver` in `AndroidManifest.xml` to `android:exported="true"` so the Android OS can deliver `BOOT_COMPLETED` broadcasts after phone restarts to restore scheduled alarms.

---

### 1. Overview & Identified Errors

| Issue | Severity | Impact | Estimated Change Size |
|---|---|---|---|
| **Group Task `reminderOptions` rejected in Firestore** | High | Saving/editing any group task with reminder options fails with `PERMISSION_DENIED` because `isValidGroupTask` in `firestore.rules` uses strict `keys().hasOnly(...)` without `reminderOptions`. | Small (~5 lines in `firestore.rules`) |
| **Group Member Role updates & removals blocked** | High | Promoting a member to Admin or demoting fails with `PERMISSION_DENIED` because `members/{uid}` has `allow update: if false;`. Member removal by admins is also blocked. | Small (~10 lines in `firestore.rules`) |
| **Group Chat messages collection lacks rules** | High | Sending or reading group chat messages fails with `PERMISSION_DENIED` because `/groups/{groupId}/messages/{messageId}` has no match rule and falls into default deny. | Small (~20 lines in `firestore.rules`) |
| **Deleted `app/google-services.json`** | Medium-High | Google Sign-In cannot resolve `default_web_client_id`, breaking sign-in and cloud sync on fresh starts. Clean Gradle builds will fail without this file. | Small (1 restored config file + gitignore entry) |
| **`BootReceiver` `android:exported="false"`** | Medium | On physical device reboot, Android OS cannot deliver `android.intent.action.BOOT_COMPLETED` to unexported receiver, preventing deadline alarms from being restored. | Minimal (1 line in `AndroidManifest.xml`) |

**Overall Change Size Estimate:** Small (~40 lines across 3 files: `firestore.rules`, `.gitignore`, and `AndroidManifest.xml`, plus restoring `app/google-services.json`). No UI or business logic rewrite is required.

---

### 2. User Experience & Visual Design

- **Zero UI Disruption**: No changes to user-facing layouts, screens, tabs, or styling.
- **Immediate Reliability**:
  - Group task creation and editing will succeed in the cloud with custom reminder intervals.
  - Group member admin promotion and member management will execute without permission errors.
  - Group chat messages will send, stream in real-time, and delete with proper authorization.
  - Deadline alarms will reliably trigger and survive device restarts.
  - Google Sign-In and Cloud Backup will work without "configuration missing" errors.

---

### 3. Key Product Decisions & Trade-Offs

- **Security Rules Hardening**:
  - `reminderOptions` in group tasks will be validated as an optional string with maximum size 100 characters.
  - Role updates on `groups/{groupId}/members/{uid}` will be strictly limited to group owners (`isGroupOwner(groupId)`), allowing updates only to the `role` field between `'admin'` and `'member'`.
  - Group chat messages in `groups/{groupId}/messages/{messageId}` will require `isGroupMember(groupId)`, message text size $\le 500$, `senderId == request.auth.uid`, and server timestamp for `createdAt`.
- **Git Protection**:
  - `.gitignore` will explicitly include `**/google-services.json` and `app/google-services.json` to ensure the restored credentials cannot be committed or pushed to public repositories.

---

### 4. Technical Architecture & Data Strategy

```
┌────────────────────────────────────────────────────────┐
│                   UniTask Client                       │
│  - Task & Group Deadline Alarms (AlarmManager)         │
│  - Google Sign-In (CredentialManager with client_id)   │
│  - Real-time Group Chat & Task Sync (Firestore SDK)    │
└───────────────▲────────────────────────▲───────────────┘
                │                        │
         [BOOT_COMPLETED]         [Firestore Rules]
                │                        │
┌───────────────┴────────┐ ┌─────────────┴───────────────┐
│ Android OS BootReceiver│ │     Cloud Firestore         │
│ (android:exported=true)│ │  - groups/{id}/tasks        │
│                        │ │  - groups/{id}/members      │
│                        │ │  - groups/{id}/messages     │
└────────────────────────┘ └─────────────────────────────┘
```

- **File Modifications Required**:
  1. `/app/google-services.json`: Restore client configuration.
  2. `/.gitignore`: Verify/add `app/google-services.json` and `**/google-services.json`.
  3. `/firestore.rules`: Add rules for `reminderOptions` field in group tasks, `role` updates on members by owner, and `messages` subcollection.
  4. `/app/src/main/AndroidManifest.xml`: Change `BootReceiver` attribute to `android:exported="true"`.
- **Verification Plan**:
  1. Deploy updated rules via `deploy_firebase`.
  2. Execute `compile_applet` to confirm build integrity.
  3. Execute unit tests with `gradle :app:testDebugUnitTest`.
