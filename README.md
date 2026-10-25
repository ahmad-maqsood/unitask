# UniTask 🎓

[![Release](https://img.shields.io/github/v/release/ahmad-maqsood/unitask?style=for-the-badge&color=4F46E5)](https://github.com/ahmad-maqsood/unitask/releases/latest)
[![Platform](https://img.shields.io/badge/Platform-Android-3DDC84?style=for-the-badge&logo=android&logoColor=white)](https://www.android.com/)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.2.10-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white)](https://kotlinlang.org/)
[![License](https://img.shields.io/badge/License-MIT-blue.svg?style=for-the-badge)](LICENSE)

An offline-first Android app designed for university students to manage tasks and assignments, track course schedules, set deadline alarms, and collaborate with study groups in real time.

> 💡 **Built with AI Assistance**: Designed and directed as a personal project, co-engineered with Google AI Studio and Claude. Built to solve real-world university schedule management without bloat or ads.

---

## 📥 Download & Install

Get the latest stable release APK directly on your Android device:

[![Download APK](https://img.shields.io/badge/Download_APK-Direct_Release-brightgreen?style=for-the-badge&logo=android)](https://github.com/ahmad-maqsood/unitask/releases/latest)

1. Head to [**Releases**](https://github.com/ahmad-maqsood/unitask/releases/latest).
2. Download `unitask.apk`.
3. Open the downloaded file on your Android device and allow installation from your browser/file manager.

**Already installed?** Open the settings (gear icon) in the app and tap **Check for update**. The app compares its own version with the newest GitHub release and opens the download page when a newer one exists.

---

## ✨ Features

### 📅 Deadlines & Assignments
- **Comprehensive Task Manager**: Set priority (`Low`, `Medium`, `High`), due date, and exact submission time.
- **Course Linking**: Tag tasks directly to your enrolled courses with matching color indicators.
- **Status Toggles**: Mark assignments as pending or completed with instant visual feedback.
- **Offline File Attachments**: Attach lecture PDFs, syllabus documents, and rubrics directly to tasks for offline viewing.

### ⏰ Class Timetable & Schedule Tracker
- **Weekly Schedule**: Visual day-by-day timetable showing recurring lectures, lecture halls, and instructors.
- **Current & Next Class Highlights**: An active banner on the dashboard shows ongoing classes and calculates countdowns to your next lecture.
- **Course Dashboard**: Track overall completion progress, instructors, and pending workloads per subject.

### 👥 Collaborative Study Groups
- **Unique Handle & Join Code**: Create private study groups with unique usernames (`@handle`) and 8-character invite codes.
- **Shared Group Tasks**: Add group project milestones and deadlines that automatically synchronize across every member's app.
- **Multi-Admin Controls**: Group owners can appoint multiple admins, transfer permissions, or remove members.
- **Personal Checklists**: Track your own completion status for shared group tasks without affecting other members.
- **Group Chat**: Every group has a Chat tab. Messages show up live for all members, with your own on the right and others on the left with their name and time. Long-press a message to delete it (your own, or any message if you are the owner or an admin). The chat opens at the newest message and only jumps down for a new message when you are already at the bottom or you sent it.
- **Group Tasks in Your Deadlines**: Tasks added to a group also appear in your Deadlines tab, labelled with the group name and who added them.
- **Group Reminders for Everyone**: Pick several reminders per group task (for example 2 days, 1 day and 2 hours before) and every member gets them.
- **Tidy Task Cards**: Long course names and long titles wrap cleanly. The join code card scrolls out of the way, and the chat gets the full screen while you type.

### 🔔 Smart Reminders & Alarms
- **Multiple Triggers**: Configure alerts at deadline, 1 hour before, 2 hours before, 1 day before, or 2 days before.
- **Device Reboot Resilience**: Built-in broadcast receivers automatically reschedule all active alarms after device reboots or app updates.
- **Zero Server Dependency**: Alarms trigger directly via Android's local `AlarmManager`, keeping battery usage minimal.

### 🎨 App & Updates
- **Theme**: Choose light, dark or follow the system.
- **In-app Update Check**: A button in the settings looks at the latest GitHub release and tells you when a newer version is out.

---

## ⚙️ Important Settings

Due to aggressive battery management in some phones like Xiaomi HyperOS, background alarms may be delayed or silenced by default. Follow these quick steps to ensure 100% on-time deadline notifications:

1. **Autostart**: Go to `Settings` > `Apps` > `Manage apps` > `UniTask` > Toggle **Autostart** ON.
2. **Battery Saver**: In the same menu, set `Battery saver` to **No restrictions**.
3. **Lock in Memory**: Open the App Switcher (Recent Apps), long-press the `UniTask` preview card, and tap the **Lock icon** 🔒.
4. **Alarms & Reminders**: Ensure `Allow alarms and reminders` is enabled under Special App Access settings.

---

## 🛠️ Architecture & Tech Stack

- **UI**: 100% Jetpack Compose with Material 3 design and dynamic dark mode.
- **Local Storage**: Room Database (SQLite) with structured migrations for local persistence.
- **Cloud Infrastructure**: Firebase Authentication (Google Sign-In), Cloud Firestore for real-time group sync, group chat and optional personal backups, all on the free Spark plan.
- **Scheduling**: Android `AlarmManager` for exact deadline delivery and `WorkManager` for background synchronization.
- **Platform Specs**: Minimum SDK 24 (Android 7.0), Target SDK 36 (Android 16).

---

## ⚠️ Known Limits

- **Group chat has no push notifications.** New messages appear while the app is open. Alerts for a closed app would need a server (Firebase Cloud Functions, paid plan).
- **Chat is text only**, shows the newest 100 messages, and sent messages cannot be edited.

---

## 🔒 Privacy & Permissions

- **100% Usable Offline**: All assignments, timetable schedules, and course notes work completely offline without needing an account.
- **Optional Google Sign-In**: Used strictly to sync personal data or join collaborative groups.
- **Permissions**:
  - `POST_NOTIFICATIONS`: To show assignment reminder alerts.
  - `SCHEDULE_EXACT_ALARM`: To ring reminders precisely at your selected intervals.
  - `RECEIVE_BOOT_COMPLETED`: To restore active deadline reminders after phone restarts.

---

## 👨‍💻 Developer & Build Setup

To clone and compile your own release APK:

```bash
git clone https://github.com/ahmad-maqsood/unitask.git
cd unitask
```

1. Add your `google-services.json` to the `app/` directory.
2. Publish the rules in `firestore.rules` to your Firestore database (they include the group chat rules).
3. Place your signing keystore (`my-upload-key.jks`) in the project root.
4. Build the release APK with Gradle:
   ```bash
   ./gradlew assembleRelease
   ```
5. Output binary will be generated at `app/build/outputs/apk/release/unitask.apk`.

### Publishing a release (so the in-app update check works)

1. Raise `versionName` and `versionCode` in `app/build.gradle.kts` (for example `"1.1.0"` and `2`).
2. Build the APK.
3. On GitHub, create a release whose **tag** matches the `versionName` with a `v` in front (for example `v1.1.0`) and attach the APK.

The update check reads only the tag. The release title and the APK file name do not matter to it.

---

Suggestions are welcome.
