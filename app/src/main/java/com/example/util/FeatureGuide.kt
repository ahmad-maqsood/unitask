package com.example.util

/** One entry in "Learn to use the app". Edit the list below to add or change a feature. */
data class AppFeature(
    val title: String = "",
    val description: String = "",
    val icon: String = "star",
    val sortOrder: Int = 0,
    val howTo: String = ""
)

/**
 * The feature list shown in Settings > Learn to use the app.
 * It lives in the code, so it always matches the version of the app the person has.
 * Entries are shown in order of sortOrder (the number before the steps).
 */
object FeatureGuide {

    val all: List<AppFeature> = listOf(
        AppFeature(
            "Deadlines & Urgency", "Track assignments and tasks grouped by urgency: Overdue, Today, Tomorrow, Upcoming, and Completed.", "assignment", 10,
            "1. Open the Deadlines tab.\n2. Tap + Add Assignment.\n3. Fill in the title, course, due date, time, priority and reminders.\n4. Tasks group automatically by urgency when sorted by date."
        ),
        AppFeature(
            "Calendar View", "Toggle between list view and a monthly calendar view to visualize deadlines.", "calendar", 15,
            "1. Open the Deadlines tab.\n2. Tap the List/Calendar view toggle icon in the top bar.\n3. Navigate months with < and >.\n4. Tap any date with a dot to see the deadlines due on that day."
        ),
        AppFeature(
            "Subtasks & Checklists", "Break tasks into manageable checklist items with progress tracking.", "assignment", 20,
            "1. In the task dialog, tap + Add Checklist Item.\n2. Enter items using '- [ ] item' markdown format.\n3. Save to see a subtask counter chip on the task card."
        ),
        AppFeature(
            "Batch Operations", "Select multiple tasks to mark them as done or delete them together.", "assignment", 25,
            "1. Open the Deadlines tab.\n2. Tap the checkbox icon in the top bar to enter Batch Mode.\n3. Tap individual tasks or 'Select All'.\n4. Use the bottom bar to mark done or delete."
        ),
        AppFeature(
            "Complete from Notifications", "Mark tasks as done directly from lock screen or tray notifications.", "notifications", 30,
            "1. When a deadline reminder notification arrives, look at the action buttons.\n2. Tap 'Mark as Done' on the notification without opening the app."
        ),
        AppFeature(
            "Snooze a Reminder", "Get one extra reminder later, from the notification or from the task card.", "notifications", 32,
            "1. On a reminder notification, tap 'Snooze 30m' to get it again in 30 minutes.\n2. Or open the three dot menu on a task card and tap 'Snooze reminder'.\n3. Pick 30 minutes, 1 hour, 3 hours or tomorrow 8:00 AM. The card shows 'Snoozed until ...'.\n4. Your other reminders still ring. To change or cancel it, tap the 'Snoozed until' line on the card."
        ),
        AppFeature(
            "Timetable & Schedule", "Your weekly university lecture schedule organized by day.", "schedule", 40,
            "1. Open the Timetable tab.\n2. Select a day of the week.\n3. Tap + Add Class and enter course, time, classroom, and instructor."
        ),
        AppFeature(
            "Bi-Weekly Timetable (Week A/B)", "Support for classes that alternate weekly.", "schedule", 45,
            "1. When adding a class, select recurrence: Every Week, Week A, or Week B.\n2. Use the filter chips below the days (All, Week A, Week B) to view your active week."
        ),
        AppFeature(
            "Break & Free Time Indicators", "Visual indicators show gaps between classes to help plan study sessions.", "schedule", 50,
            "1. Open the Timetable tab on days with multiple classes.\n2. Breaks of 15+ minutes automatically show a coffee pill (e.g. '☕ 1h 30m free until next class')."
        ),
        AppFeature(
            "Export Timetable (.ics)", "Export your schedule directly into Google Calendar or Outlook.", "schedule", 55,
            "1. Open the Timetable tab.\n2. Tap 'Export (.ics)' in the top bar.\n3. Choose your calendar app from the share sheet to import all recurring lectures."
        ),
        AppFeature(
            "Timetable Conflict Alerts", "Automatic warnings before saving overlapping classes.", "schedule", 60,
            "1. If an added or edited lecture overlaps an existing class on that day, UniTask warns you.\n2. Review the conflict warning dialog to avoid double booking."
        ),
        AppFeature(
            "Courses", "Keep your subjects, instructors, and colors in one place.", "school", 70,
            "1. Open the Courses tab.\n2. Tap Add Course and enter code, name, and instructor.\n3. All tasks and lecture slots inherit course color tags."
        ),
        AppFeature(
            "Study Groups & QR Codes", "Collaborate on shared tasks and chat with classmates.", "groups", 80,
            "1. Sign in with Google and open the Groups tab.\n2. Tap the QR icon in group details to show your invite QR code and link.\n3. To join, tap 'Join Group' and scan a QR screenshot or paste an invite link."
        ),
        AppFeature(
            "Group Settings & Ownership", "Edit group name, regenerate codes, or transfer ownership.", "groups", 85,
            "1. As owner, tap the Settings icon to edit name/handle or regenerate invite code.\n2. In the Members tab, tap the three dots next to a member to transfer ownership."
        ),
        AppFeature(
            "Group Chat & Search", "Real-time messaging with search, replies, and date headers.", "chat", 100,
            "1. Open the Chat tab in any group.\n2. Tap the search icon to filter messages by text or sender.\n3. A sticky date header floats at the top while scrolling."
        ),
        AppFeature(
            "Offline Outbox & Chat History", "Send messages offline and scroll back through chat history.", "chat", 105,
            "1. Messages sent offline are queued in an outbox and sent automatically when reconnected.\n2. Scroll to the top and tap 'Load earlier messages' to view older chat history."
        ),
        AppFeature(
            "Cloud Backup & Multi-Account", "Sign in with Google to back up your data across devices.", "backup", 120,
            "1. Open Settings and tap Cloud backup (or Sign in to back up data).\n2. Sign in with Google to sync courses, timetable, and tasks.\n3. Switching accounts cleanly isolates and clears stale local records."
        ),
        AppFeature(
            "Data Export & GDPR Purge", "Export JSON backup or permanently purge your cloud account.", "backup", 125,
            "1. Open Settings and tap Cloud backup (or Sign in to back up data).\n2. Tap 'Export My Data (JSON)' to share a full backup.\n3. Tap 'Delete Account & Cloud Data' to permanently erase cloud records and reset."
        ),
        AppFeature(
            "Announcements Bell", "Official university updates and exam tips, fully offline-ready.", "notifications", 130,
            "1. Tap the notification bell in the top bar to read official announcements.\n2. All data is cached locally in Room database on the phone for 100% offline access."
        ),
        AppFeature(
            "Repeating Tasks", "Tasks that come back daily, weekly, monthly or yearly. A task set on the 31st goes to the last day of shorter months, then back to the 31st.", "assignment", 22,
            "1. In the task dialog, open the Repeat option.\n2. Pick Every day, Every weekday, Every week, Every 2 weeks, Every month or Every year.\n3. Tick the task Done. It stays Done until midnight, then moves to its next date.\n4. Checklist items are unticked again when it moves."
        ),
        AppFeature(
            "Swipe to Complete", "Swipe a deadline card to mark it done, with Undo.", "assignment", 26,
            "1. Open the Deadlines tab.\n2. Swipe a task card.\n3. Tap Undo on the bar at the bottom if it was a mistake."
        ),
        AppFeature(
            "Home Screen Widget", "A Today widget with your classes, upcoming tasks and overdue count.", "sun", 90,
            "1. Long-press an empty spot on your home screen.\n2. Open Widgets and find UniTask.\n3. Drag the Today widget onto the screen."
        ),
        AppFeature(
            "Daily Summary", "One notification each morning with your day.", "notifications", 31,
            "1. Open Settings, then Notifications & reminders.\n2. Turn on Daily summary and pick the time."
        ),
        AppFeature(
            "Notification History", "See reminders you missed, with unread counts.", "notifications", 33,
            "1. Tap the bell in the top bar.\n2. Open an item to jump to the task."
        ),
        AppFeature(
            "Check Reminders", "Find out why reminders are late or missing, and send yourself a test.", "notifications", 34,
            "1. Open Settings, then Notifications & reminders.\n2. Tap Check reminders.\n3. Tap Fix this next to any line with a warning.\n4. Tap Send test reminder, lock the screen and wait about 10 seconds.\n5. If a reminder was due while your phone was off, you get one 'Missed reminder' notification when it starts again."
        ),
        AppFeature(
            "Delete When Done", "Let finished one-time tasks in My tasks remove themselves.", "assignment", 23,
            "1. In My tasks, add a task or edit one.\n2. Leave Repeat on 'Does not repeat'.\n3. Switch on 'Delete when done' and save.\n4. Tick the task done. It is deleted after that day, so you can still undo it the same day."
        ),
        AppFeature(
            "Accent Colours", "Choose one of six accent colours for the app.", "star", 95,
            "1. Open Settings, then General.\n2. Tap an Accent colour swatch."
        ),
        AppFeature(
            "Notes", "Capture lecture notes, ideas, and revision summaries with course tags and search.", "assignment", 135,
            "1. Tap the Tools icon in the top bar, then tap Notes.\n2. Tap New note (or the + button) to create a note.\n3. Tap Course to pick a course for the note.\n4. Type in the search bar or tap a course chip to filter notes.\n5. Tap the sort icon to sort by Last edited, Date created, or Title A to Z.\n6. Tap the three dots menu on a note card to Pin or Delete it.\n7. Open the top-right menu and choose Recently deleted to view or restore deleted notes.\n8. Use Save to device in the note menu to keep a note as a file in any folder you choose.\n9. Share or Save to device can also make a PDF that looks like the preview, with headings, lists, tables and images."
        ),
        AppFeature(
            "Markdown in Notes", "Format text with headings, checklists, tables, links and gallery images.", "assignment", 140,
            "1. While editing a note, tap the toolbar buttons above the keyboard for headings, bold, italic, lists, quotes, inline code, links and tables.\n2. Tap the image icon on the toolbar to add photos from your gallery (up to 10 images per note).\n3. Tap the Preview icon in the top bar to see how your note renders.\n4. Interactive checklists can be ticked or unticked directly in Preview.\n5. Tap the Edit icon in the top bar to return to editing."
        ),
        AppFeature(
            "Share, Backup and Sync for Notes", "Share notes, import files, back up notes locally and sync with your cloud account.", "backup", 145,
            "1. In the note editor, open the three dots menu, tap Share, and choose a file type.\n2. In the notes list, open the top-right menu and tap Import .md or .txt file.\n3. Open Markdown files from other apps via Open with and Share, and save the preview to Notes.\n4. Notes are included automatically in local backups and in Export My Data under Settings.\n5. When you are signed in, notes sync to the cloud with conflict protection.\n6. Note images stay saved on your phone and are kept in local backups, but are not uploaded to the cloud."
        ),
        AppFeature(
            "Timer and Countdown", "Track study time with a stopwatch or a countdown, in full screen if you like.", "schedule", 150,
            "1. Tap the Tools icon in the top bar, then tap Timer.\n2. Choose Stopwatch to count up, or Countdown to count down.\n3. For a countdown pick 15, 25, 45 or 60 minutes, or tap Custom to type your own.\n4. Tap Start. Tap Pause to stop counting and Resume to continue. Paused time is not counted.\n5. Tap the full screen icon to show only the time in landscape. The bulb icon keeps the screen on; tap it to turn that off. Tap the exit icon or press Back to return.\n6. You can close the screen while it runs. A dot on the Tools icon shows that a timer is active.\n7. When a countdown ends you get a notification, even if the app is closed."
        )
    ).sortedBy { it.sortOrder }
}
