# Changelog

All notable changes to UniTask are documented in this file.

## [3.1.0] - 2026-11-01

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

## [3.0.0] - 2026-10-31

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

## [2.3.0] - 2026-10-30 (Beta)

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

## [2.2.0] - 2026-10-30

### Added
- Interactive Feature Guide with step-by-step instructions for major app features.

### Improved
- Improved task completion and undo feedback.
- Enhanced task swiping with clearer undo indicators.
- Added support for remotely managed feature instructions.
- General code cleanup and maintenance.

## [2.1.1] - 2026-10-29

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

## [2.1.0] - 2026-10-29 (Beta)

### Added
- Supabase cloud integration for authentication and data synchronization.
- Real-time synchronization for courses, tasks, timetables, and group data.
- Offline-first group caching.
- Group chat message replies.

### Improved
- Optimistic updates for tasks and chat messages.
- Authentication and session handling.
- Database migrations and synchronization reliability.

## [2.0.0] - 2026-10-28

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

## [1.3.0] - 2026-10-25

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

## [1.2.0] - 2026-10-25

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

## [1.1.0] - 2026-10-24

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

## [1.0.0] - 2026-10-23

### Initial Release
- Task and deadline management with priority tags.
- Class timetable and schedule tracking.
- Study groups with invitation codes.
- Offline file attachments.
- Optional Google Sign-In and cloud synchronization.

---

[Unreleased]

Changes made after the latest release will be documented here.
