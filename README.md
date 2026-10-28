# UniTask 🎓

[![Release](https://img.shields.io/github/v/release/ahmad-maqsood/unitask?style=for-the-badge&color=4F46E5)](https://github.com/ahmad-maqsood/unitask/releases/latest)
[![Platform](https://img.shields.io/badge/Platform-Android-3DDC84?style=for-the-badge&logo=android&logoColor=white)](https://www.android.com/)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.2.10-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white)](https://kotlinlang.org/)
[![License](https://img.shields.io/badge/License-MIT-blue.svg?style=for-the-badge)](LICENSE)

UniTask is an offline-first Android app for university students to manage assignments, keep track of class schedules, receive reminders, organize courses, and collaborate with classmates in shared study groups.

> 💡 **Built with AI assistance:** A personal project designed and directed to solve everyday university planning needs without unnecessary bloat or ads.

---

## 📥 Download & Install

Download the latest stable APK from the [GitHub Releases page](https://github.com/ahmad-maqsood/unitask/releases/latest).

1. Open [**Latest Releases**](https://github.com/ahmad-maqsood/unitask/releases/latest).
2. Download `unitask.apk`.
3. Open the downloaded APK on your Android device and allow installation from your browser or file manager if prompted.

**Already installed?** Open UniTask Settings and select **Check for update**. The app checks the latest GitHub release and opens the release page if a newer version is available.

---

## 🆕 Latest Release Highlights

### UniTask v2.0.0 — Changes since v1.3.0

> The Android project is currently configured as version **2.0.0**. These notes summarize changes in the repository since the v1.3.0 release.

#### New Features
- **My Tasks:** A dedicated area for personal to-dos and habits that are not tied to a university course. Create, edit, complete, and delete personal tasks.
- **Recurring tasks:** Set repeat rules for personal tasks; completing a recurring task schedules its next occurrence.
- **More reminder choices:** Added a 12-hours-before reminder option and an optional alarm-style alert for high-priority tasks.
- **Feature guide:** Browse an in-app feature guide that can refresh from the cloud and still has cached/built-in content available offline.
- **Announcements:** View dismissible in-app announcements; dismissed announcements are remembered on the device.
- **Feature requests:** Open a prefilled feature-request form from Settings.
- **Richer deadline filtering:** Filter tasks by source (personal or group), alongside the existing search, status, course, and sorting controls.
- **Better class context:** Class highlights can show related pending group tasks, and group tasks can display matching course details.

#### Cloud & Data Improvements
- **Migrated cloud services to Supabase:** Authentication, personal data sync, group collaboration, and realtime updates now use Supabase instead of Firebase/Firestore.
- **More dependable group sync:** Added cached group memberships for faster startup, refreshes after realtime subscriptions connect, and improved handling of pending changes.
- **Safer task updates:** Improved optimistic updates, temporary-task handling, reminder scheduling, and creation of the special My Tasks course.
- **Improved local backup compatibility:** Backups preserve recurring-task rules and report personal tasks separately; restore matching is improved to avoid duplicate recurring tasks.
- **Security/configuration cleanup:** Removed Firebase/Firestore configuration and dependencies, added a Supabase keep-alive workflow, and ensured signing-key files are ignored by Git.

#### UI, Usability & Reliability
- Improved the personal-task editor with quick entry, due-date grouping, swipe actions with undo, and clearer date/time validation.
- Improved task-card accessibility and bottom navigation styling.
- Completed tasks are kept after active tasks while preserving the selected sort order.
- Improved group/task loading, authentication initialization, realtime refresh, and stale optimistic-state handling.
- Added validation to prevent using the reserved My Tasks course code for a regular course.
- My Tasks reminders use clearer task titles without an unnecessary course-code prefix.

#### Previously released in v1.3.0
- Daily summary notifications for today's classes and task deadlines, with a configurable delivery time.
- Group-task notifications for deadline changes and deletions, excluding your own edits.
- Custom notification sounds, including built-in sounds, the system default, and sounds selected from the device.
- In-app problem reporting with app/device details prefilled.
- Background setup guidance available again from Settings.
- Responsive small-screen layouts, improved reminder persistence/recovery, a 25 MB attachment limit, attachment cleanup, improved cloud-deletion error reporting, stronger group-task validation/security tests, and the MIT License.

**Full comparison:** [Compare v1.3.0 with the current main branch](https://github.com/ahmad-maqsood/unitask/compare/v1.3.0...main)

**APK release description draft:** See [APK_RELEASE_NOTES.md](APK_RELEASE_NOTES.md).

---

## ✨ Features

### 📅 Deadlines & Assignments

- **Task management:** Add a title, priority (`Low`, `Medium`, `High`), due date, and exact submission time.\n- **Personal tasks:** Keep to-dos and habits that are not tied to a course in the dedicated My Tasks area.\n- **Recurring tasks:** Set a repeat rule for personal tasks and automatically schedule the next occurrence when a repeating task is completed.
- **Course linking:** Associate assignments with courses and identify them using course color indicators.
- **Clear task status:** Track work as pending or done.
- **Offline attachments:** Attach lecture PDFs, syllabi, and rubrics to tasks for local access.
- **Group tasks in Deadlines:** Shared tasks can appear alongside personal deadlines, with group and creator information.
- **Multiple reminders:** Choose supported reminder times for tasks instead of relying on a single default alert.
- **Group task change alerts:** Receive notifications when a shared task's deadline changes or the task is deleted. Your own edits do not trigger these alerts.
- **Daily summary:** Optionally schedule a daily notification showing the day's classes and task deadlines, at a time you choose.

### ⏰ Class Timetable & Reminders

- **Weekly timetable:** View recurring lectures by day, including lecture times, rooms, and instructors.
- **Current and next class:** A dashboard highlight shows the ongoing class or counts down to the next lecture.
- **Class-start alerts:** Receive a notification when a scheduled class begins.
- **10-minute early reminder:** Get an advance alert before class starts.
- **Daily overview:** The optional daily summary brings together today's classes and task deadlines.
- **Course progress:** Review course details, completion progress, instructors, and pending workload.
- **Schedule validation:** Lecture times and course information are validated to help prevent conflicting or invalid entries.

### 👥 Collaborative Study Groups

- **Create or join groups:** Use a unique handle (`@handle`) and an invite code to collaborate with classmates.
- **Shared tasks:** Create group project tasks and synchronize them across group members.
- **Assign tasks to members:** Tag individual members or use `@all`. Assigned tasks appear in the relevant members' Deadlines tabs, while group task announcements remain visible to the group.
- **Group roles and management:** Owners can manage members, appoint admins, change roles, and remove members.
- **Personal checklists:** Track your own progress on shared tasks without changing another member's completion status.
- **Group chat:** Exchange text messages in each group's Chat tab, with live updates while the chat is open.
- **Unread message indicators:** See when a group or chat has unread messages.
- **Background chat notifications:** The app checks for new messages in the background and can notify you when a new message arrives while you are not viewing the chat. Delivery may depend on Android background restrictions.
- **Configurable group reminders:** Select reminder times for group tasks; reminders are scheduled locally on each member's device.
- **Task-change notifications:** Get notified about deadline changes and deletions made to shared tasks, without receiving an alert for your own edits.
- **Chat controls:** Long-press a message to delete your own message, or use owner/admin permissions where applicable. The chat opens at the latest messages and avoids unnecessary jumps when you are reading older messages.

### 🔔 Notifications & Alarms

- **Configurable deadline alerts:** Choose from supported intervals including at the deadline, 1 or 2 hours before, 12 hours before, and 1 or 2 days before, depending on the task reminder options.\n- **High-priority alarm mode:** Optionally make reminders for high-priority tasks ring like an alarm.
- **Custom notification sounds:** Choose from built-in UniTask sounds, your phone's default, or a sound selected from your device; preview it before choosing.
- **Daily summary reminders:** Enable or disable a daily overview and choose when it should arrive.
- **Reliable scheduling:** Local Android alarms are used for deadline, class, and daily summary reminders.
- **Restart recovery:** Scheduled reminders are restored after device restarts or app updates, with improved reminder-state persistence.
- **Background setup review:** Reopen the background setup guidance from Settings to review autostart and battery settings. The app can indicate when setup is already configured.
- **Background reliability guidance:** Settings can help you configure autostart and battery restrictions on devices that limit background work.

### 💾 Local Backup & Preferences

- **Encrypted local backup:** Export and import an encrypted local database backup using Android's system file picker.
- **Light and dark themes:** Choose light mode, dark mode, or follow the system where supported.
- **Update checker:** Check GitHub Releases from inside the app.\n- **Announcements and feature guide:** See dismissible in-app announcements and browse a feature guide that can use cached content offline.\n- **Feature requests:** Open the feature-request form from Settings.

### 📱 Layout & Usability

- **Small-screen support:** Improved layouts for narrow screens and long text to reduce overflow and make content easier to read.
- **Scrollable settings:** Settings content can be accessed on smaller displays.
- **Responsive timetable controls:** Weekday selection can scroll horizontally when screen width is limited.
- **Chat layout improvements:** Improved keyboard, message layout, and scrolling behavior.

---

## ⚙️ Important Notification Settings

Some manufacturers apply aggressive battery management that can delay or suppress background reminders. If alerts do not arrive on time, check your device's settings:

1. **Autostart:** Allow UniTask to start automatically, if your device provides this option.
2. **Battery restrictions:** Set UniTask to unrestricted or **No restrictions** where available.
3. **Background activity:** Allow background activity and keep the app available in Recent Apps if your device requires it.
4. **Alarms and reminders:** Allow exact alarms if Android requests this permission.
5. **Notifications:** Make sure UniTask notifications are enabled in Android settings.

You can reopen the background setup guidance from the app's Settings to review these options. The exact menu names vary by Android version and phone manufacturer.

---

## 🛠️ Architecture & Tech Stack

- **UI:** Kotlin, Jetpack Compose, and Material 3.
- **Local storage:** Room database (SQLite) with schema migrations.
- **Cloud services:** Supabase Authentication (Google sign-in) and Supabase Postgres for cloud backup, group synchronization, and group chat.
- **Scheduling:** Android `AlarmManager` for scheduled reminders and `WorkManager` for background synchronization and message checks.
- **Platform:** Minimum SDK 24 (Android 7.0); target SDK 36 (Android 16).

---

## ⚠️ Known Limitations

- Background chat notifications use background synchronization/polling and may be affected by Android battery restrictions or manufacturer-specific task management.
- Group chat is text-only. Messages cannot be edited, and the chat displays a limited recent message history.
- Class and task reminders depend on notification permissions and Android's alarm/background execution settings.
- Cloud-based group features require an internet connection; core personal task and timetable workflows are designed to work offline.

---

## 🐞 Report a Problem

No GitHub account needed. In the app, open **Settings → Report a problem**, or use the form directly:
[UniTask: Report a problem](https://docs.google.com/forms/d/e/1FAIpQLSdJpx67ioTDiYOvbntGTh-5AGFb213DFMJNitEl12MYYZSYNw/viewform)

The app fills in only its version, your Android version and your phone model. It never sends your name, email, tasks or group data.

---

## 🔒 Privacy & Permissions

- **Offline-first:** Core personal tasks, courses, and timetable data are stored locally and can be used without signing in.
- **Optional sign-in:** Google sign-in is used for cloud synchronization and collaborative features.
- **Local backup:** Backup files are encrypted; store exported files somewhere safe and only import backups you trust.
- **Notifications:** `POST_NOTIFICATIONS` allows deadline, class, daily summary, and chat alerts.
- **Exact alarms:** `SCHEDULE_EXACT_ALARM` supports precise reminder scheduling where required by Android.
- **Restart recovery:** `RECEIVE_BOOT_COMPLETED` allows scheduled reminders to be restored after a reboot.
- **Network access:** Used for update checks and Supabase-backed cloud features.
- **Problem reports:** Tapping "Report a problem" opens a Google Form in your browser. Only the app version, Android version and phone model are prefilled, and you choose what else to submit.

---

## 👨‍💻 Developer & Build Setup

Clone the repository:

```bash
git clone https://github.com/ahmad-maqsood/unitask.git
cd unitask
```

1. Create a Supabase project, add SUPABASE_URL and SUPABASE_ANON_KEY, and run the SQL setup scripts.
2. Configure your release signing key using the project's expected Gradle properties/environment setup. Do not commit signing keys or secrets.
3. Build the release APK:

   ```bash
   ./gradlew assembleRelease
   ```

4. The APK is generated at `app/build/outputs/apk/release/unitask.apk`.

### Publishing a release

1. Update `versionName` and `versionCode` in the app's Gradle configuration.
2. Build and test the release APK on a device.
3. Create a GitHub Release and attach the APK as `unitask.apk`.
4. Add concise release notes covering features, improvements, and bug fixes.

---

## 📄 License

UniTask is distributed under the MIT License. See [LICENSE](LICENSE) for details.