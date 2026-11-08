# Changelog

All notable changes to UniTask are documented in this file.

## [4.0.0] - 08-11-2026
[Download APK](https://github.com/ahmad-maqsood/unitask/releases/tag/v4.0.0)

### New Features
- [add the pause icon for the timer notification](https://github.com/ahmad-maqsood/unitask/commit/80c56392a0058bedf54d7810ce54623a92725b21)
- [add the play (resume) icon for the timer notification](https://github.com/ahmad-maqsood/unitask/commit/50efb2ee7d297281a290eb5e96bed6b5c76927b0)
- [add the stop icon for the timer notification](https://github.com/ahmad-maqsood/unitask/commit/06c93ca189f41b8c7bac19acf37075b1d5d0e78b)
- [show icons on the timer notification buttons](https://github.com/ahmad-maqsood/unitask/commit/8983498e083ae629bacc128769b85bab982e8fe4)
- [add a hook for the running-timer notification](https://github.com/ahmad-maqsood/unitask/commit/19d814285a3de6ecf01525ba5af9bde5ed41883d)
- [show an ongoing notification while the timer runs](https://github.com/ahmad-maqsood/unitask/commit/6da326401fd531a893a884276736e6038d663633)
- [handle Pause, Resume and Stop from the timer notification](https://github.com/ahmad-maqsood/unitask/commit/f7c1ea392c7d6bb217fe7e91999247565ff045fe)
- [keep the running-timer notification in sync](https://github.com/ahmad-maqsood/unitask/commit/6b1c030677f8f8241b0d6ec89da7b4f603d9bde5)
- [register the timer notification action receiver](https://github.com/ahmad-maqsood/unitask/commit/7624cf87209b3bbdc269b097416ec90959cc3b38)
- [feat: Tools menu, timer, countdown and notes export](https://github.com/ahmad-maqsood/unitask/pull/48) — What's new

### Improvements
- [improve: timer lock screen controls, countdown alarm, and notes limits](https://github.com/ahmad-maqsood/unitask/pull/58) — This pull request introduces three focused improvements across the Timer and Notes features:
- [make the app bar logo follow the theme](https://github.com/ahmad-maqsood/unitask/commit/25503fecefd670c1c35fe7f68c4063770572b78c)
- [show full tool descriptions on two lines](https://github.com/ahmad-maqsood/unitask/commit/0b7ba8bd59859bfd77132bdf09b0d79a24b1673c)
- [refactor: transition to local-first personal storage and remove Supabase personal sync](https://github.com/ahmad-maqsood/unitask/pull/56) — This PR transitions UniTask to a local-first storage model for personal data (courses, tasks, lecture timetable slots, and notes). All personal data now resides securely on the dev

### Bug Fixes
- [feat: add Google Drive backup and restore](https://github.com/ahmad-maqsood/unitask/pull/50) — What
- [removed nested folder](https://github.com/ahmad-maqsood/unitask/commit/99039f08319ef1f1ddf034fe9e699cab17c70bd2)

### Other Changes
- [docs: refresh automated README screenshots](https://github.com/ahmad-maqsood/unitask/pull/59) — Summary
- [improve README with highlights, build steps and a fixed diagram](https://github.com/ahmad-maqsood/unitask/commit/186784f142704dccdc61c7f1a9bc68094b1a3d32)
- [stop closing the database in DriveRestoreOfferCardTest teardown](https://github.com/ahmad-maqsood/unitask/commit/e20a2a4c1abbbfdd9ce8db51d28f32b7ba934e7c)
- [add Global search and Recently deleted screenshots, use Pakistani chat names](https://github.com/ahmad-maqsood/unitask/commit/edbbf18fbed5ea17861b0bcedf5e1e4eef6e673e)
- [update: bump app version to 4.0.0](https://github.com/ahmad-maqsood/unitask/commit/79621ca3427e724e3e4f2574d5d94ab21948c3cd)
- [feats: recently deleted, semester archive, and global search](https://github.com/ahmad-maqsood/unitask/pull/57) — Added three major capabilities to UniTask:
- [add manual lines of code count workflow](https://github.com/ahmad-maqsood/unitask/commit/367525e3f4f83a65b5390af80ab3579d2fdf66b8)
- [Keep lint self-test output out of CI results](https://github.com/ahmad-maqsood/unitask/commit/e1076af76b171ff3ef762e93757289267ee167e3)
- [Remove dependency review workflow](https://github.com/ahmad-maqsood/unitask/commit/3fbb1eb89e92d4830b38d590bf7186c603daccef)
- [make lint failures easy to read](https://github.com/ahmad-maqsood/unitask/commit/79c5704abb862f94ab5f83255a57ebc291afe60b)
- [remove smoke tests](https://github.com/ahmad-maqsood/unitask/commit/d078fd7a6749cb5b398ecc70fd2750bf08e0f7fc)
- [Replace app icon with the Sunrise U design](https://github.com/ahmad-maqsood/unitask/pull/55) — Summary
- [Limit group chat to 500 messages per group in Supabase](https://github.com/ahmad-maqsood/unitask/pull/54) — Summary
- [Redesign running timer notification with round icon buttons](https://github.com/ahmad-maqsood/unitask/pull/53) — Summary
- [Fix Drive restore test Compose teardown](https://github.com/ahmad-maqsood/unitask/pull/52) — Summary
- [Fix Drive restore offer test database cleanup](https://github.com/ahmad-maqsood/unitask/pull/51) — Summary
- [cover the running-timer notification updates](https://github.com/ahmad-maqsood/unitask/commit/6c9b41251ad4d4ef7bdd46ac58f0f8cfc09dcfb8)
- [tag the Deadlines list so tests can scroll it](https://github.com/ahmad-maqsood/unitask/commit/6cb55fb64a1f16727e39b1c1a49c072cc2306151)
- [fix five failing smoke tests](https://github.com/ahmad-maqsood/unitask/commit/51d4563e1bfcbc5089bd2d14f614d81f19722738)
- [print failing smoke test names and stack traces](https://github.com/ahmad-maqsood/unitask/commit/00d280fe440fdd90d46e2e7aab194fb1a52f31de)
- [capture the study timer in README screenshots](https://github.com/ahmad-maqsood/unitask/commit/4b2dfaae662f6a5aebbea1114450753e50b4d3d1)
- [export the timer screenshot from the emulator](https://github.com/ahmad-maqsood/unitask/commit/1ff360d01a867a594fc91a39ca247689effe5863)
- [add the study timer to the README screenshot gallery](https://github.com/ahmad-maqsood/unitask/commit/07e21a13ce6df31ada78db3f44f87427f5aa45c5)
- [Fix SmokeFlowsTest compile errors](https://github.com/ahmad-maqsood/unitask/commit/3244039a3e077168b8374ac4576bb1edca09babf)
- [msg: explain that Drive access is limited to UniTask's own folder](https://github.com/ahmad-maqsood/unitask/commit/56e794de283758106748c3bd4ad05daea4446298)
- [add website pages for Google consent screen](https://github.com/ahmad-maqsood/unitask/commit/51a9f2ea12b8ff7fae5feee3e8917e7123111caa)
- [add workflow to deploy the website to Firebase Hosting](https://github.com/ahmad-maqsood/unitask/commit/8171b279b67ca5bf767c7112f65b3e8fc0145387)
- [docs: remove What's new section from README](https://github.com/ahmad-maqsood/unitask/pull/49) — Summary
- [Remove standalone setup guide and its documentation references](https://github.com/ahmad-maqsood/unitask/pull/47) — Summary
- [Merge branch 'main' of https://github.com/ahmad-maqsood/unitask](https://github.com/ahmad-maqsood/unitask/commit/4266a630a90fe69610f998954661f722e20d102f)
- [Add v3.5.0 section and move 3.4.0 to earlier changes](https://github.com/ahmad-maqsood/unitask/commit/1cdcd9d2ffcd56ba416133e3c5a03ea359b9d48e)
- [docs: add CHANGELOG entry for v3.5.0](https://github.com/ahmad-maqsood/unitask/pull/46) — Automatically generated release notes for v3.5.0. Review all entries, especially **Other Changes**, before merging. The matching GitHub Release is a draft and has not been publishe

## [3.5.0] - 06-11-2026
[Download APK](https://github.com/ahmad-maqsood/unitask/releases/tag/v3.5.0)

### Bug Fixes
- [fixes: sync safety, private data cleanup, notes editor, date picker, tests, and no class alerts in the bell](https://github.com/ahmad-maqsood/unitask/pull/45) — What this changes
- [fix: make README screenshot export reliable](https://github.com/ahmad-maqsood/unitask/pull/42) — Summary
- [fix: resolve compose flowrow crash, group chat tab reset, and realtime websocket](https://github.com/ahmad-maqsood/unitask/pull/41)
- [fix: diagnose screenshot demo group loading](https://github.com/ahmad-maqsood/unitask/pull/40) — Summary
- [Refuse imported files that are too long for a note](https://github.com/ahmad-maqsood/unitask/commit/4bfdb250734bf03ec67e84a11680e2122de95077)
- [generate debug keystore with valid keytool command](https://github.com/ahmad-maqsood/unitask/commit/11a319806a1f9a311666bca82aa398a78c712afa)
- [fix: resolve Compose screenshot test compilation](https://github.com/ahmad-maqsood/unitask/pull/38) — Summary
- [pass screenshot test arguments safely](https://github.com/ahmad-maqsood/unitask/commit/a83b76ded8b16e195115ee0d40e1a3fb686c0fc3)
- [fix: use valid Android emulator device profile](https://github.com/ahmad-maqsood/unitask/pull/35) — Summary

### Other Changes
- [update: bump versioncode to 18 and versionname to 3.5.0](https://github.com/ahmad-maqsood/unitask/commit/92656b1dc69b9225420c75b48c21fd6808d8af82)
- [docs: add automatically captured README screenshots](https://github.com/ahmad-maqsood/unitask/pull/44) — Summary
- [docs: refresh automated README screenshots](https://github.com/ahmad-maqsood/unitask/pull/43) — Summary
- [change audio of default sound_2](https://github.com/ahmad-maqsood/unitask/commit/c14324bc59ada76bb0170784fe6051f611d89b95)
- [include unmerged semantics for screenshot selectors](https://github.com/ahmad-maqsood/unitask/commit/eb956e5ea421394705e26ff36f6ba85ad90481cb)
- [return to home before capturing settings](https://github.com/ahmad-maqsood/unitask/commit/ec2eaa431ae8b9010e03e24803473b1d019f87e1)
- [Don't purge trashed notes whose cloud copy still exists](https://github.com/ahmad-maqsood/unitask/commit/8c5f67c0b79dc3c549d2015dd6b95e3a92f72dab)
- [Stop note watchers when the editor closes](https://github.com/ahmad-maqsood/unitask/commit/25cc979f63088b6a1aac81bed628561a59004930)
- [Raise compileSdk to 37 for the OkHttp engine](https://github.com/ahmad-maqsood/unitask/commit/5df24c0333b7643617d7a2e85bf4219ad2e1bb1d)
- [Fix editor state bugs, trash handling and note sync issues](https://github.com/ahmad-maqsood/unitask/pull/39) — Summary
- [Use Ktor OkHttp engine so Supabase Realtime can use WebSockets](https://github.com/ahmad-maqsood/unitask/commit/68cfaaa99d98c9f35e9ce25342eb2f5818b7edda)
- [Use Ktor OkHttp engine so Supabase Realtime can use WebSockets](https://github.com/ahmad-maqsood/unitask/commit/2da93954dd4c9e7483bf75929b1d5c83d4ebb6c2)
- [preserve instrumentation reports when screenshot tests fail](https://github.com/ahmad-maqsood/unitask/commit/f1c57793a666cdb315d34271f246e6c6323f03e5)
- [start adb before launching screenshot emulator](https://github.com/ahmad-maqsood/unitask/commit/b54f16a0ee85a1d2c5fab439f3fcd0e29933e1a6)
- [enable KVM acceleration for screenshot emulator](https://github.com/ahmad-maqsood/unitask/commit/f902a205a49a2b361646662f1d01ac18722c099d)
- [Add docIdsNeedingBody helper for lighter note sync](https://github.com/ahmad-maqsood/unitask/commit/47efe010f7446204190e13821d9f3f92c2ae2ef2)
- [Bring a trashed note back when new text is saved into it](https://github.com/ahmad-maqsood/unitask/commit/f8b91dc8c1a71e474bc32eac5f39b645858e3ecf)
- [Add observeNoteById query to NoteDao](https://github.com/ahmad-maqsood/unitask/commit/ececd57d54b7bc45a92474082eb3c62cb3c3bfbd)
- [create debug keystore before prebuilding screenshots](https://github.com/ahmad-maqsood/unitask/commit/f65660981619c87bb5325f9b0b413f1859b375eb)
- [reduce emulator resource pressure during screenshot tests](https://github.com/ahmad-maqsood/unitask/commit/40185c3732ccfd4f332cc900bc156650851d5179)
- [give screenshot emulator explicit memory](https://github.com/ahmad-maqsood/unitask/commit/65268bca601ddfa7be61c3178107269fef0fb3d4)
- [ci: run screenshot capture as standalone bash script](https://github.com/ahmad-maqsood/unitask/pull/37) — Summary
- [avoid flaky emulator animation setup](https://github.com/ahmad-maqsood/unitask/commit/285ebb27e2c9db4a29ed968864a1cd4c47637570)
- [use lighter Android emulator for screenshot workflow](https://github.com/ahmad-maqsood/unitask/commit/10d28c257828b40ad5c3cc72de34e89adf666c0e)
- [ci: generate debug keystore for screenshot workflow](https://github.com/ahmad-maqsood/unitask/pull/36) — Summary
- [ci: automate README screenshots from the Android app](https://github.com/ahmad-maqsood/unitask/pull/34) — Summary
- [Fix invalid YAML in release APK workflow](https://github.com/ahmad-maqsood/unitask/pull/33) — Summary
- [Fix release APK workflow registration](https://github.com/ahmad-maqsood/unitask/pull/32) — Summary
- [Add automatic release summaries and changelog PRs](https://github.com/ahmad-maqsood/unitask/pull/31) — Summary

## [3.4.0] - 05-11-2026
[Download APK](https://github.com/ahmad-maqsood/unitask/releases/tag/v3.4.0)

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
[Download APK](https://github.com/ahmad-maqsood/unitask/releases/tag/v3.2.0)

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
[Download APK](https://github.com/ahmad-maqsood/unitask/releases/tag/v3.1.0)

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
[Download APK](https://github.com/ahmad-maqsood/unitask/releases/tag/v3.0.0)

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
[Download APK](https://github.com/ahmad-maqsood/unitask/releases/tag/v3.0.0-beta4)

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
[Download APK](https://github.com/ahmad-maqsood/unitask/releases/tag/v3.0.0-beta3)

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
[Download APK](https://github.com/ahmad-maqsood/unitask/releases/tag/v3.0.0-beta2)

### Improved
- **Groups Tab Stability:** Prevented temporary authentication states from clearing user sessions and cached groups when the screen is unlocked. Cached data remains visible while reconnecting.
- **Notifications:** Improved chat notifications with Android MessagingStyle, better permission handling, category grouping, primary color tinting, and clearer notification content.
- **Join Code Dialog:** Improved the layout of group invitation codes, with a larger monospace code and better-arranged Copy and New Code buttons.
- **Synchronization:** Faster sync feedback, immediate data refresh when returning to the app, reduced observer delay, and less unnecessary polling while the app is in the background.

## [3.0.0-beta] - 30-10-2026
[Download APK](https://github.com/ahmad-maqsood/unitask/releases/tag/v3.0.0-beta)

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
[Download APK](https://github.com/ahmad-maqsood/unitask/releases/tag/v2.3.0-beta)

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
[Download APK](https://github.com/ahmad-maqsood/unitask/releases/tag/v2.2.0)

### Added
- Interactive Feature Guide with step-by-step instructions for major app features.

### Improved
- Improved task completion and undo feedback.
- Enhanced task swiping with clearer undo indicators.
- Added support for remotely managed feature instructions.
- General code cleanup and maintenance.

## [2.1.1] - 29-10-2026
[Download APK](https://github.com/ahmad-maqsood/unitask/releases/tag/v2.1.1)

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
[Download APK](https://github.com/ahmad-maqsood/unitask/releases/tag/v2.1.0)

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
[Download APK](https://github.com/ahmad-maqsood/unitask/releases/tag/v2.0.0)

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
[Download APK](https://github.com/ahmad-maqsood/unitask/releases/tag/v1.3.0)

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
[Download APK](https://github.com/ahmad-maqsood/unitask/releases/tag/v1.2.0)

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
[Download APK](https://github.com/ahmad-maqsood/unitask/releases/tag/v1.1.0)

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
[Download APK](https://github.com/ahmad-maqsood/unitask/releases/tag/v1.0.0)

### Initial Release
- Task and deadline management with priority tags.
- Class timetable and schedule tracking.
- Study groups with invitation codes.
- Offline file attachments.
- Optional Google Sign-In and cloud synchronization.
