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
- Snoozes and the **Delete when done** setting belong to the phone on which they were set. **Delete when done** is kept in backups.
- The home-screen widget is read-only. Tapping a row opens the application, but tasks cannot be completed from the widget.

## Notes

- Notes are limited to 300,000 characters and 50 images.
