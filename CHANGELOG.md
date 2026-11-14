# Changelog

All notable changes to UniTask are documented in this file.

## [4.4.0] - 14-11-2026
[Download APK](https://github.com/CondaLabs/unitask/releases/tag/v4.4.0)

### Added
- Added manual text input for focus, break, and round values in the Pomodoro settings dialog with input validation.
- Added a "Latest release" row to the "About & help" settings section to directly open the GitHub release page.

### Improved
- Increased key stretching iterations for new encrypted backups from 120,000 to 600,000 to enhance brute-force resistance.
- Updated group invite links to point to the official Firebase-hosted website, while maintaining support for legacy GitHub Pages links.

### Fixed
- Resolved an issue where the update checker failed to follow redirects after the repository moved to a new organization; it now follows up to 5 redirects.

### Developer & project maintenance
- Migrated all repository links and references to the CondaLabs organization.
- Removed obsolete legacy code related to group chat cleanup and personal sync cancellation.
- Added a CODEOWNERS file to manage review requirements.
- Improved screenshot automation to capture additional screens and added a --readme-only mode for gallery layout updates.

## [4.3.0] - 13-11-2026
[Download APK](https://github.com/CondaLabs/unitask/releases/tag/v4.3.0)

### Improved
- Enhanced Pomodoro timer logic with improved state handling, persistence, and support for auto-starting alarms even when the app is closed.
- Expanded local backup files to include app settings, such as theme and timer preferences, for a more complete restoration experience.
- Updated the Notes feature to include find-in-note functionality and code highlighting.
- Refined search screen and notification handling for reminders and countdowns.

### Fixed
- Resolved a potential crash in the legacy chat cleanup process by restricting notification channel deletion to Android 8.0 (API 26) and newer.

### Developer & project maintenance
- Removed the group chat feature, including all related UI elements, view models, and background notification logic.
- Hardened the Android manifest with empty taskAffinity for the MainActivity to prevent task hijacking.
- Implemented network_security_config.xml to explicitly block cleartext HTTP traffic.
- Integrated automated mobile security scanning (mobsfscan) and OpenSSF Scorecard into CI/CD pipelines.
- Added GitHub Artifact Attestations for APKs to allow verification of release file provenance.
- Updated GitHub Actions workflows to use pinned commit hashes and restricted CI/CD permissions.

## [4.2.0] - 12-11-2026
[Download APK](https://github.com/CondaLabs/unitask/releases/tag/v4.2.0)

### Added
- New Pomodoro timer mode with dedicated focus and break rounds.
- Progress screen featuring completion history, daily summaries, and statistics.
- 'Find in Note' search functionality.
- Comprehensive onboarding experience with contextual tips and a 'Get Started' card.
- Local backup and restore flows, including completion-log support and Google Drive integration.
- Local crash-logging system and recovery screen for viewing and sharing error logs.

### Improved
- Redesigned study timer with a new dial component for full-screen usage.
- Enhanced note-taking with syntax highlighting for code blocks, improved Markdown styling, and pinch-to-zoom support.
- Refined Deadlines tab with real-time counts for Overdue, Today, and Tomorrow tasks.
- Streamlined task and group-task management workflows, including improved repeat rollover and deadline processing.
- Updated UI for course cards, detail views, lecture-slot dialogs, and reminder-chip selection.
- Improved background-access guidance and ongoing timer notification behavior.

### Fixed
- Resolved issues with group task synchronization, pending-save tracking, and conflict resolution.
- Fixed reliability issues with reminders, recurring tasks, and app visibility.
- Improved PDF export functionality for notes.

### Developer & project maintenance
- Updated Compose BOM to 2026.09.00 and removed forced version pins.
- Refactored large Kotlin files into smaller, focused modules for maintainability.
- Added a release build step to CI to detect R8 shrinking issues.
- Standardized date formatting across the app using a shared utility.
- Moved sensitive Supabase configuration to a git-ignored .env file.
- Updated backup rules to align with 'allowBackup=false'.

## [4.1.0] - 10-11-2026
[Download APK](https://github.com/CondaLabs/unitask/releases/tag/v4.1.0)

### Added
- New full-screen timer design featuring a watch-style dial, progress-tracking ticks, gradient arc, and inner orbit animation.
- Study history tracking with support for local and Google Drive backups.
- 'Keep screen on' feature for individual notes.
- 'Undo' action for deleted tasks in Course Detail and Deadlines screens.
- Haptic feedback for screen interactions, including toggles, checkboxes, and swipe actions.
- Global search functionality now includes a dedicated Settings section.
- Support for opening .md files shared from external file managers, including those identified as generic binary files.
- 'Reminder Guard' safety net to restore missing alarms after reboots or battery cleaning.

### Improved
- Timer notifications now feature round, icon-based buttons for Pause/Resume and Stop.
- Notes can now be shared directly from the list view via the three-dot menu.
- UI navigation redesign: 'Tools' menu renamed to 'More', featuring a new search icon and update notification badges.
- Markdown rendering improvements, including better dark mode visibility, clearer headings, and synchronized scroll positions for Edit/Preview modes.
- 'ShrinkToFitText' implementation scales text size instead of truncating with ellipses in task cards and priority badges.
- Timetable export feature moved to a dedicated card at the end of the list.
- Update pop-up now fetches and displays release notes directly from GitHub.

### Fixed
- Resolved issue where empty 'Deadlines and reminders' notification groups remained in the tray.
- Fixed search box overlapping with the status bar in edge-to-edge mode.
- Corrected layout issues on small screens (under 420dp) and prevented 'New code' button cutoff.
- Fixed note search result previews to display line breaks as spaces.
- Removed redundant, non-functional expand arrow from timer notifications.

### Developer & project maintenance
- Enabled predictive back gestures (`enableOnBackInvokedCallback`).
- Updated ProGuard rules to strip verbose logs from release builds.
- Updated database schema to version 15.
- Enhanced automated release notes workflow using Gemini and improved screenshot automation.

## [4.0.0] - 08-11-2026
[Download APK](https://github.com/CondaLabs/unitask/releases/tag/v4.0.0)

### Added
- **Study timer:** added a stopwatch and countdown with preset or custom durations, plus a fullscreen landscape display.
- **Timer notifications:** added an ongoing notification with Pause, Resume and Stop controls, including support for the lock screen.
- **Countdown alarm:** the app now sounds an alarm when a countdown finishes, even when the app is not open.
- **Global search:** search across app content and jump directly to settings pages.
- **Recently deleted and semester archive:** recover deleted content and keep older semester work organised.
- **Notes sharing and export:** share notes as text, Markdown or PDF without opening the editor.
- **Google Drive backup:** back up and restore personal app data using the app's private folder in Drive.

### Improved
- **Local-first personal data:** courses, tasks, timetable entries and notes are stored locally in Room rather than synced as personal data through Supabase.
- **Timer controls:** refined the notification buttons and made timer actions easier to reach while the phone is locked.
- **Group chat storage:** limited each group to its 500 newest messages to keep the database manageable.
- **App appearance:** updated the app icon and made the app-bar logo follow the selected theme.
- **Small-screen layouts and navigation:** improved tool descriptions and adjusted several screens for clearer presentation.

### Fixed
- Improved reliability in Drive backup and restore, note handling, and timer notification state updates.
- Fixed issues found in the app's automated UI and database tests.

### Developer & project maintenance
- Improved automated README screenshot capture and expanded the screenshot gallery.
- Refined lint and test workflows, including clearer lint failure output and more reliable test diagnostics.
- Added supporting website pages and automated Firebase Hosting deployment for the Google consent screen.

## [3.5.0] - 06-11-2026
[Download APK](https://github.com/CondaLabs/unitask/releases/tag/v3.5.0)

### Improved
- Improved note editing, recovery and synchronization, including safer handling of deleted notes and more efficient note updates.
- Improved group chat stability, including tab state handling and WebSocket connectivity.
- Made README screenshot capture more reliable and added automated screenshot generation.
- Updated the default notification sound.

### Fixed
- Fixed synchronization and private-data cleanup issues, note editor and date-picker problems, and incorrect class alerts appearing in the notification bell.
- Fixed a Compose layout crash and issues affecting group chat navigation and real-time updates.
- Prevented oversized imported files from being added as notes.
- Fixed screenshot and instrumentation test setup issues, including emulator configuration and test compilation.

### Developer & project maintenance
- Added and refined automated screenshot, lint and test workflows.
- Improved CI diagnostics and preserved useful test reports when screenshot tests fail.
- Updated the networking engine to support Supabase Realtime WebSockets and raised the compile SDK requirement for compatibility.

## [3.4.0] - 05-11-2026
[Download APK](https://github.com/CondaLabs/unitask/releases/tag/v3.4.0)

### Added
- Notes: markdown notes with an optional course tag, opened from the top bar.
- Editor with autosave and formatting toolbar.
- Edit and Preview with tables, links, checklists and images.
- Search, course filter, pin, sort.
- Recently deleted for 30 days.
- Share as text or .md file and import .md or .txt files.
- Open Markdown files from other apps with Open with or Share, read them as a formatted preview and save them to Notes.
- Images from the gallery stored on the phone.
- Notes included in local backups and Export my data.
- Cloud sync with conflict protection.

### Improved
- Course deletion keeps notes.
- High priority reminders, for assignments, My Tasks and group tasks, and their snoozes, now use alarm-clock alarms so phones do not delay or drop them.
- The high priority alarm hint now shows when adding assignments and group tasks too.
- Group invites are shared as a tappable web link that opens UniTask.
- Check for updates no longer fails with error 403 on shared networks.
- Repeating tasks: a missed daily or weekday task is now replaced by today's copy at midnight, so today's reminder rings. Before, it stayed overdue and the next reminder was skipped.
- Missed daily and weekday tasks are saved in the notification bell.
- The daily summary now moves repeating tasks to today first, so it matches your task list.
  
## [3.2.0] - 04-11-2026
[Download APK](https://github.com/CondaLabs/unitask/releases/tag/v3.2.0)

### Added
- **Task checklist editor:** add, edit, tick off and remove checklist items directly in the task dialog, with progress shown on task cards.
- **Calendar task completion:** swipe deadline cards in the calendar to mark tasks done or undo completion.
- **Task-card snoozing:** snooze a reminder for 30 minutes, 1 hour, 3 hours or tomorrow at 8:00 AM. The task card shows when the snooze ends, including snoozes started from a notification.
- **Release update prompt:** see an in-app prompt and an update-available card in the notification bell when a new stable release is available.
- **Check reminders:** a Settings diagnostic checks notification permission, reminder-channel status, exact alarms and battery restrictions. Each detected issue has a **Fix this** action, plus a **Send test reminder** option.
- **Missed reminder alerts:** receive one notification when a reminder from the previous 12 hours could not fire because the phone was off or its alarm was removed.
- **Delete when done:** enable automatic deletion for one-time My tasks after the day they are completed, while keeping Undo available that day.

### Improved
- **Repeating tasks:** monthly and yearly repeats keep their original day when moving through shorter months and leap years (for example, a task set for the 31st returns to the 31st when possible).
- **Automatic repeat rollover:** missed repeating tasks move forward to their next applicable date, and checklist items are reset for the new round.
- **Reminder reliability:** Android 13 and newer use the automatically granted `USE_EXACT_ALARM` permission; Android 12 continues to use the Alarms & reminders setting.
- **Reminder handling:** reminders are skipped for tasks completed on another device or deleted after scheduling. The notification tray keeps the newest reminder for each task without allowing an earlier snooze to replace a later reminder.
- **Snooze recovery:** scheduled snoozes are restored after a device restart and are cleared when their task or group is removed.
- **Synchronization:** improved queued group-message handling, task filtering, recovery of locally deleted tasks, account-switch cleanup and cancellation of background sync.
- **Course setup:** course instructor and colour can be omitted when creating a course; sensible defaults are supplied.

### Fixed
- Repeating-task checklists now start each new cycle with all items unticked.
- Reminder completion is saved before an active reminder is stopped, preventing a completed task from being treated as unfinished.
- Improved boot-time reminder scheduling and recovery of reminders that were missed while the app or phone was unavailable.
- Test reminders no longer appear in notification history, and notification-tray cleanup no longer removes the wrong reminder.
- Refreshed the visible chat date when messages change and improved handling of queued messages.

### Developer & project maintenance
- Added automated unit tests, lint checks, Room database migration tests and CodeQL code scanning.
- Added dependency review for pull requests and scheduled Dependabot updates.
- Added the Gradle wrapper and modernized GitHub Actions build, test and release workflows, including a choice between test builds and publishing a release.
- Updated Gradle and Android dependencies and improved build caching and workflow permissions.

## [3.1.0] - 01-11-2026
[Download APK](https://github.com/CondaLabs/unitask/releases/tag/v3.1.0)

### Added
- Today home screen widget displaying classes, upcoming tasks, and overdue task counts.
- QR code scanning for joining study groups.
- Notification history with unread counts and quick navigation.
- Daily summary notifications with notification history access.
- Six customizable accent color themes.

### Improved
- Swipe deadline cards to complete tasks, with undo support.
- Added 30-minute reminder snoozing and customizable notification sounds.
- Improved group chat error handling and message delivery.
- Improved notification navigation and reminder handling.
- Added due-today task tracking.
- Reorganized settings and navigation.
- Refreshed app icon and branding.
- Various performance, reliability, and UI improvements.

## [3.0.0] - 31-10-2026
[Download APK](https://github.com/CondaLabs/unitask/releases/tag/v3.0.0)

### Added
- Interactive task checklists with individual item completion.
- Bi-weekly timetables supporting Week A and Week B.
- Timetable calendar export using standard `.ics` files.
- Automatic timetable conflict detection.
- QR code and deep-link group invitations.
- Personal cloud data export in JSON format.
- Enhanced backup and restore functionality.
- Improved account data cleanup.

### Improved
- Redesigned settings with dedicated categories.
- Improved deadline organization, sorting, and task management.
- Improved offline group access and synchronization.
- More reliable cloud synchronization with protection against accidental data loss.
- Improved chat synchronization and message delivery.
- Improved reminder scheduling and notification handling.
- Better date, timezone, and daylight-saving handling.
- Optimized release builds with code minification and resource shrinking.
- Expanded automated test coverage.

### Fixed
- Various synchronization, backup, timetable, reminder, and task management issues.

## [3.0.0-beta4] - 31-10-2026
[Download APK](https://github.com/CondaLabs/unitask/releases/tag/v3.0.0-beta4)

### Added
- Interactive task checklists directly from task cards.
- Timetable calendar export to `.ics` files, including alternating-week schedules.
- Automatic timetable conflict detection.
- Week A / Week B timetable configuration and support across displays, reminders, and summaries.
- Reorganized settings into dedicated categories.

### Improved
- Better deadline grouping, sorting, and task organization.
- Paginated Supabase synchronization for larger datasets.
- Safer cloud sync to prevent temporary course-fetch failures from causing data loss.
- Improved duplicate-course handling while preserving associated tasks and lecture slots.
- Improved backup, restore, and account cleanup.
- More reliable QR code generation and scanning.
- Improved notification behavior when silencing alarms.
- Better Google Sign-In context handling.
- Improved timezone and daylight-saving date handling.
- More reliable synchronization scheduling and database migrations.
- Added extensive automated tests.
- Enabled code minification and resource shrinking for release builds.
- Removed unused networking dependencies.

### Fixed
- Potential data loss during temporary cloud synchronization failures.
- Course deduplication issues affecting associated tasks and lecture slots.
- Task deadline inconsistencies around daylight-saving transitions.
- QR code generation and decoding reliability.
- Unwanted vibration when silenced alarms were active.

## [3.0.0-beta3] - 31-10-2026
[Download APK](https://github.com/CondaLabs/unitask/releases/tag/v3.0.0-beta3)

### Added
- Bi-weekly timetables with configurable Week A / Week B scheduling.
- QR code scanning through the camera or images to join study groups.
- Deep-link invitations using `unitask://join` links.
- Automatic invite-code detection from the clipboard.
- Personal cloud data export as a downloadable JSON file.
- Expanded account cleanup covering cached authentication and group data, reminders, scheduled work, and attachments.
- Backup and restore support for task completion timestamps.
- Server-side account deletion functionality and supporting database changes.

### Improved
- Chat synchronization using per-group locking and in-flight send tracking.
- Batch task selection persistence and confirmation before bulk deletion.
- Back-button navigation and app exit behavior.
- Time picker support for the device's 12-hour or 24-hour format.
- Task reminder scheduling and cancellation.
- Semantic version comparison and timetable week-parity tests.

### Fixed
- Timetable `.ics` exports opening correctly in calendar applications.
- Update checker handling of beta and pre-release versions.
- Checklist parsing for uppercase and lowercase completion markers.
- Bulk task completion.
- Task attachments being lost when undoing deletion.
- Duplicate group chat messages and concurrent chat outbox update issues.
- Reminder alarms continuing beyond their intended duration.
- Overdue tasks missing from daily summaries.
- Daily summaries and timetables for classes ending after midnight.
- Missing completion timestamps during backup restoration.

## [3.0.0-beta2] - 31-10-2026
[Download APK](https://github.com/CondaLabs/unitask/releases/tag/v3.0.0-beta2)

### Improved
- **Groups Tab Stability:** Prevented temporary authentication states from clearing user sessions and cached groups when the screen is unlocked. Cached data remains visible while reconnecting.
- **Notifications:** Improved chat notifications with Android MessagingStyle, better permission handling, category grouping, primary color tinting, and clearer notification content.
- **Join Code Dialog:** Improved the layout of group invitation codes, with a larger monospace code and better-arranged Copy and New Code buttons.
- **Synchronization:** Faster sync feedback, immediate data refresh when returning to the app, reduced observer delay, and less unnecessary polling while the app is in the background.

## [3.0.0-beta] - 30-10-2026
[Download APK](https://github.com/CondaLabs/unitask/releases/tag/v3.0.0-beta)

### Added

**Deadlines & Tasks**
- Interactive calendar view for deadlines.
- Checklist subtasks with progress tracking.
- Bulk task selection, completion, and deletion.
- Ability to mark tasks as completed directly from notifications.
- Urgency-based deadline organization.

**Timetable & Courses**
- Bi-weekly timetables with Week A / Week B support.
- Visual free-time indicators between classes.
- Timetable export to standard `.ics` calendar files.
- Lecture clash detection with confirmation warnings.

**Study Groups & Chat**
- QR code generation and scanning for group invitations.
- Universal group invitation links.
- Group name and handle editing.
- Group ownership transfer.
- Persistent offline message queue with automatic synchronization.
- Real-time chat search.
- Floating chat date indicators.
- Pagination for loading earlier messages.

**Account & Privacy**
- Export personal cloud data in JSON format.
- Account and associated cloud data deletion.
- Improved Google account switching and cache isolation.
- Preserve local data when logging out.

### Improved
- Interactive task completion from notifications.
- Notification sound handling.
- Timetable layouts and database reliability.
- Built-in Feature Guide expanded to 20 guides.
- Overall synchronization, performance, and stability.

## [2.3.0-beta] - 30-10-2026
[Download APK](https://github.com/CondaLabs/unitask/releases/tag/v2.3.0-beta)

### Added
- Announcement inbox with notification bell and unread count.
- Background announcement checks and notifications.
- Local announcement caching for offline access.
- Support for announcements without requiring sign-in.
- Individual announcement removal and Clear All option.

### Fixed & Improved
- Fixed notification sound preferences on fresh installations.
- Improved background announcement handling.
- Prevented duplicate announcement notifications.

## [2.2.0] - 30-10-2026
[Download APK](https://github.com/CondaLabs/unitask/releases/tag/v2.2.0)

### Added
- Interactive Feature Guide with step-by-step instructions for major app features.

### Improved
- Improved task completion and undo feedback.
- Enhanced task swiping with clearer undo indicators.
- Added support for remotely managed feature instructions.
- General code cleanup and maintenance.

## [2.1.1] - 29-10-2026
[Download APK](https://github.com/CondaLabs/unitask/releases/tag/v2.1.1)

### Added
- Reply to group chat messages.
- Automatic repeating-task rollover.
- Offline group-task caching.
- Quick due-time selection options.

### Improved
- Group chat refresh, message loading, and scrolling.
- Offline synchronization and task completion updates.
- Authentication initialization and cached group loading.
- Task reminders and cloud synchronization.

### Fixed
- Duplicate group chat messages.
- Timestamp parsing issues.
- Cloud deletion and synchronization reliability.
- Database migration and reminder recovery issues.

### Backend
- Expanded Supabase integration and real-time synchronization.
- Improved database constraints, triggers, and identity validation.
- Documented database security policies and maintenance procedures.

## [2.1.0] - 29-10-2026 (Beta)
[Download APK](https://github.com/CondaLabs/unitask/releases/tag/v2.1.0)

### Added
- Supabase cloud integration for authentication and data synchronization.
- Real-time synchronization for courses, tasks, timetables, and group data.
- Offline-first group caching.
- Group chat message replies.

### Improved
- Optimistic updates for tasks and chat messages.
- Authentication and session handling.
- Database migrations and synchronization reliability.

## [2.0.0] - 28-10-2026
[Download APK](https://github.com/CondaLabs/unitask/releases/tag/v2.0.0)

### Added
- My Tasks for personal tasks and habits independent of university courses.
- Recurring personal tasks.
- 12-hour-before task reminders.
- Optional alarm-style alerts for high-priority tasks.
- In-app feature guide.
- Dismissible in-app announcements.
- Feature request form.
- Personal and group deadline filters.

### Cloud & Backup
- Migrated authentication, personal data, group collaboration, and real-time synchronization from Firebase to Supabase.
- Improved group synchronization and membership caching.
- Improved local backup and restore.
- Removed obsolete Firebase dependencies and configuration.

### Improved & Fixed
- Improved task creation, organization, and swipe actions.
- Improved authentication and synchronization reliability.
- Improved reminder handling.
- Various UI, accessibility, and performance improvements.

## [1.3.0] - 25-10-2026
[Download APK](https://github.com/CondaLabs/unitask/releases/tag/v1.3.0)

### Added
- Daily summary notifications.
- Custom notification sounds and previews.
- Group task deadline change and deletion notifications.
- In-app problem reporting.
- Background setup guidance in Settings.

### Improved
- Small-screen responsiveness and text wrapping.
- Reminder persistence and recovery.
- Attachment handling with a 25 MB size limit.
- Group task assignment validation.
- Firestore security-rule tests.

### Fixed
- Various notification, reminder, attachment, and layout issues.

### Other
- Added MIT License.
- Updated project documentation.

## [1.2.0] - 25-10-2026
[Download APK](https://github.com/CondaLabs/unitask/releases/tag/v1.2.0)

### Added
- Class reminders.
- Encrypted local database backups and restore.
- Group chat with unread message indicators.
- Background chat notifications.
- Group task assignments and custom reminders.
- Light and dark themes.
- In-app update checker.
- Group administration features.

### Improved & Fixed
- Chat scrolling and keyboard behavior.
- Course and lecture validation.
- Task ordering and reminder handling.
- Database synchronization and recovery.
- Background execution reliability.
- Various UI and stability issues.

## [1.1.0] - 24-10-2026
[Download APK](https://github.com/CondaLabs/unitask/releases/tag/v1.1.0)

### Added
- Group chat with real-time messaging.
- Multiple reminders for group tasks.
- Group task visibility in the Deadlines tab.
- Multiple group administrators and member management.

### Improved
- Responsive chat bubbles and task cards.
- Group task management.
- Chat scrolling and navigation.
- General interface improvements.

### Fixed
- Group task creation permission issues.
- Build configuration issues.

## [1.0.0] - 23-10-2026
[Download APK](https://github.com/CondaLabs/unitask/releases/tag/v1.0.0)

### Initial Release
- Task and deadline management with priority tags.
- Class timetable and schedule tracking.
- Study groups with invitation codes.
- Offline file attachments.
- Optional Google Sign-In and cloud synchronization.
