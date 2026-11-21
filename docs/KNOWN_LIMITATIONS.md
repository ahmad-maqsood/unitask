# Known limitations (technical details)

The short list of limitations that matter most to users is in the [README](../README.md#known-limitations). This page has the finer details.

## Backup

- The Drive backup is a snapshot, not live sync between phones.
- Only one backup (two rotating copies) is kept.
- Restoring merges data and does not remove anything, but app settings from the backup replace the ones on the phone.
- Note pictures larger than 20 MB and attachments larger than 25 MB are skipped.
- Android permissions (notifications, alarms, battery) are not part of a backup, the app asks for them again.
- A phone ringtone chosen as notification sound is restored as the system sound.
- The backup file made in Settings (Backup file) includes app settings and note pictures, but not attachments.
- The Drive backup is not encrypted by UniTask and is stored in the user's own Google Drive.
- Automatic backup runs about 30 seconds after a change, once Google Drive is connected and the network rule (Wi-Fi only by default) is met. A change made while the phone is offline is uploaded when the connection returns.
- If the newest backup is 7 days old (or there never was one), a reminder card appears on the Deadlines tab.
- Note images stay on the phone and are included in backups.

## Reminders and widget

- Reminders depend on notification permissions and the phone's battery settings (see [Reliable notifications](../README.md#reliable-notifications)).
- A group reminder that was missed while the phone was off is listed in the **Missed reminder** notification only if the task is in the copy of your groups saved on the phone and is still open there. Open the Groups tab once with internet to refresh that copy.
- Snoozes and the **Delete when done** setting belong to the phone on which they were set. **Delete when done** is kept in backups.
- The home-screen widget is read-only. Tapping a row opens the application, but tasks cannot be completed from the widget.

## Notes

- Notes are limited to 300,000 characters and 50 images.

## Language

- UniTask is available in English and Urdu. Choosing a language inside the app needs Android 13 or newer; older phones follow the phone language.
- The "Learn to use the app" guide (Settings) is written in English only, and so are the notification history entries saved before the language was changed.
- The "Due ..." line of a reminder is written when the reminder is set, so after changing the language it keeps the old language until the reminder is set again (for example when the task is edited).
