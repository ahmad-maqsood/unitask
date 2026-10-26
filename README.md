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

### UniTask v1.3.0

#### New Features
- **Daily summary notifications:** Schedule a daily notification showing today's classes and task deadlines, and choose the delivery time.
- **Group task change alerts:** Receive notifications when a shared task's deadline changes or the task is deleted. Your own edits do not trigger these alerts.
- **Custom notification sounds:** Choose from built-in UniTask sounds, your Android system default, or a sound selected from your device. Preview sounds before choosing.
- **In-app problem reporting:** Open the problem-report form from Settings. The form is prefilled with the app version, Android version, and device model.
- **Review background setup:** Reopen autostart and battery-optimization guidance from Settings.

#### Improvements
- Improved responsiveness on narrow screens and handling of long text to reduce clipping and overflow.
- Improved reminder persistence and recovery after app launches, device reboots, app updates, and system time changes.
- Clarified background activity, autostart, and battery-optimization guidance.
- Improved notification sound preference handling and notification channel cleanup.
- Improved attachment cleanup and cloud-deletion error reporting.
- Expanded group-task assignment validation and Firestore security-rule tests.

#### Bug Fixes & Reliability
- Prevented group-task change notifications from triggering for your own edits.
- Improved restoration and rescheduling of reminders.
- Improved attachment handling by enforcing a 25 MB attachment limit and cleaning up stale orphaned files.
- Improved validation of assigned group tasks and related Firestore security rules.

#### Repository
- Added the MIT License.
- Updated and clarified the README, including problem-reporting and privacy information.

**Full changelog:** [Compare v1.2.0 with v1.3.0](https://github.com/ahmad-maqsood/unitask/compare/v1.2.0...v1.3.0)

---

## ✨ Features

### 📅 Deadlines & Assignments

- **Task management:** Add a title, priority (`Low`, `Medium`, `High`), due date, and exact submission time.
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

- **Configurable deadline alerts:** Use supported intervals such as at the deadline, 1 or 2 hours before, and 1 or 2 days before, depending on the task reminder options.
- **Custom notification sounds:** Choose from built-in UniTask sounds, your phone's default, or a sound selected from your device; preview it before choosing.
- **Daily summary reminders:** Enable or disable a daily overview and choose when it should arrive.
- **Reliable scheduling:** Local Android alarms are used for deadline, class, and daily summary reminders.
- **Restart recovery:** Scheduled reminders are restored after device restarts or app updates, with improved reminder-state persistence.
- **Background setup review:** Reopen the background setup guidance from Settings to review autostart and battery settings. The app can indicate when setup is already configured.
- **Background reliability guidance:** Settings can help you configure autostart and battery restrictions on devices that limit background work.

### 💾 Local Backup & Preferences

- **Encrypted local backup:** Export and import an encrypted local database backup using Android's system file picker.
- **Light and dark themes:** Choose light mode, dark mode, or follow the system where supported.
- **Update checker:** Check GitHub Releases from inside the app.

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
- **Cloud services:** Firebase Authentication and Cloud Firestore for optional account features, group synchronization, and group chat.
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
- **Network access:** Used for update checks and optional Firebase-backed features.
- **Problem reports:** Tapping "Report a problem" opens a Google Form in your browser. Only the app version, Android version and phone model are prefilled, and you choose what else to submit.

---

## 👨‍💻 Developer & Build Setup

Clone the repository:

```bash
git clone https://github.com/ahmad-maqsood/unitask.git
cd unitask
```

1. Add your Firebase `google-services.json` file to the `app/` directory for Firebase-enabled builds.
2. Publish the rules in `firestore.rules` to your Firestore database.
3. Configure your release signing key using the project's expected Gradle properties/environment setup. Do not commit signing keys or Firebase secrets.
4. Build the release APK:

   ```bash
   ./gradlew assembleRelease
   ```

5. The APK is generated at `app/build/outputs/apk/release/unitask.apk`.

### Publishing a release

1. Update `versionName` and `versionCode` in the app's Gradle configuration.
2. Build and test the release APK on a device.
3. Create a GitHub Release and attach the APK as `unitask.apk`.
4. Add concise release notes covering features, improvements, and bug fixes.

---

## 📄 License

UniTask is distributed under the MIT License. See [LICENSE](LICENSE) for details.