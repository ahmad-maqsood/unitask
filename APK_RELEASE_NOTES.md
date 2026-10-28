# UniTask APK Release Notes — v2.0.0

This file is the draft description for the next APK release. The project is configured as version **2.0.0**; update this file if the final APK differs from the current main branch.

## What's New Since v1.3.0

### New Features
- Added **My Tasks**, a dedicated space for personal to-dos and habits that are not linked to university courses.
- Added **recurring personal tasks** that schedule their next occurrence when completed.
- Added a **12-hour-before reminder** option.
- Added an optional **alarm-style alert for high-priority tasks**.
- Added an in-app **feature guide** with cloud refresh and cached/offline fallback.
- Added dismissible **in-app announcements**.
- Added a **feature request form** accessible from Settings.
- Added personal/group source filters to the Deadlines screen.
- Added related group-task information to class highlights and clearer course details on group tasks.

### Cloud & Backup
- Migrated sign-in, personal-data synchronization, group collaboration, and realtime updates from Firebase/Firestore to **Supabase**.
- Improved group sync startup, membership caching, realtime refresh, and handling of pending task changes.
- Improved local backup and restore for recurring tasks, including duplicate-restore prevention.
- Removed obsolete Firebase/Firestore configuration and dependencies.
- Added a Supabase keep-alive workflow and excluded signing-key files from Git tracking.

### UI Improvements & Bug Fixes
- Improved My Tasks with quick entry, due-date grouping, swipe actions with undo, and clearer date/time validation.
- Improved task-card accessibility and navigation styling.
- Kept completed tasks after active tasks without overriding the selected sort order.
- Improved authentication initialization, group loading, realtime synchronization, and reminder reliability.
- Prevented temporary unsaved group tasks from triggering reminders or announcements.
- Prevented regular courses from using the reserved My Tasks course code.
- Improved personal-task reminder wording by removing the unnecessary course-code prefix.

## Previous Release — v1.3.0
- Daily summaries for today's classes and task deadlines, with a configurable time.
- Notifications for shared-task deadline changes and deletions, excluding your own edits.
- Custom notification sounds: built-in UniTask sounds, system default, or a sound selected from your device.
- In-app problem reporting with app version, Android version, and device model prefilled.
- Background setup guidance accessible again from Settings.
- Improved small-screen responsiveness and long-text handling.
- Improved reminder persistence and recovery after launches, reboots, updates, and system time changes.
- Added a 25 MB attachment limit, stale attachment cleanup, and clearer cloud-deletion error reporting.
- Improved group-task assignment validation and Firestore security-rule tests.
- Added the MIT License.

## Short Description (Optional)

UniTask 2.0.0 adds personal and recurring tasks, stronger reminders, improved group synchronization, a Supabase cloud migration, and a more polished task-management experience.
