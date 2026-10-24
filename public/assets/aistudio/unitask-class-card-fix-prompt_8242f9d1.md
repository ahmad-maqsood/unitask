# UniTask: Fix the Class Schedule cards (Deadlines tab)

You are editing an existing, working Android app (Kotlin, Jetpack Compose, Material 3, Compose BOM 2024.09.00). The app is already installed on real users' phones. This task is a small, visual-only fix. Read the whole prompt before changing anything.

---

## 1. Hard rules (the app must keep working for current users)

1. Change **only** `app/src/main/java/com/example/ui/screens/CurrentNextClassCard.kt`. Touch `DeadlinesTab.kt` only if step 4 below truly cannot be done without it, and then change only the `CurrentNextClassCard(...)` call or the `LazyRow` around it.
2. Do NOT change: `MainViewModel.kt`, `HighlightedClassInfo` (same fields, same names, same types), `TimeFormatter.kt`, any Room entity or DAO, `AppDatabase.kt`, Firestore code or rules, `AndroidManifest.xml`, `build.gradle.kts` (including `versionName` and `versionCode`), `libs.versions.toml`, or any file under `data/`, `sync/`, `reminder/`, `util/`.
3. Do NOT add dependencies, new files, new permissions, or new string resources.
4. Keep every existing `testTag` exactly as is: `highlighted_class_card` and `expand_class_tasks_button`.
5. Keep the function signature of `CurrentNextClassCard(classInfo, onTaskClick, modifier)` unchanged.
6. Keep the existing behaviour: tapping the pending assignments strip expands and collapses the list, tapping an assignment calls `onTaskClick`, the banner is green (`ActiveClassGreen`) when `isActiveNow` is true and the course colour otherwise, and the card background logic stays the same.
7. Do not rename or reword the banner status texts (`"CURRENT CLASS IN PROGRESS"` and `"NEXT UPCOMING CLASS"`) and do not change what `classInfo.timeLabel` contains. Only change how they are laid out.
8. No refactors, no formatting-only changes elsewhere, no "while I'm here" improvements. If you see another problem, mention it in your final message and do not fix it.

---

## 2. The two problems (from a real phone screenshot, about 360dp wide)

### Problem A: the time text is jammed into the status tag

The banner at the top of each card is one `Row` with `SpaceBetween`: the status tag (dot + `"CURRENT CLASS IN PROGRESS"` / `"NEXT UPCOMING CLASS"`) on the left and `classInfo.timeLabel` on the right. Each card is only `fillParentMaxWidth(0.9f)` wide, so there is not enough room. The result:

* the tag text gets squeezed and clipped (it shows as `N° CLASS` or `NEXT UPCOMING C...`),
* the time label (`Monday at 02:00 PM`, `Starts in 45 min`, `Ends in 12 min`) wraps onto two lines and visually collides with the tag,
* it looks messy and unreadable.

`timeLabel` can be as long as `Wednesday at 02:00 PM`, so shrinking fonts is not a fix. The layout must separate the two.

### Problem B: cards in the same row have different heights

The cards sit in a horizontal `LazyRow`. Right now the card heights differ because:

* a card with pending assignments shows the extra "N assignments pending for this course" strip and a card without pending assignments does not,
* a long course name wraps to two lines on one card and one line on another,
* `classroom` and `instructor` are in one `Row`, so on one card the instructor name wraps to two lines and on another it does not, and either can be blank.

The result: one card looks large and the next looks small.

---

## 3. Required result

### 3.1 Banner (fixes Problem A)

Replace the single-line `Row` banner with a banner that has **two stacked lines**, both always present:

* **Line 1:** the white dot + the status tag text. `maxLines = 1`, `softWrap = false`, `overflow = TextOverflow.Ellipsis`, and the text must be allowed the full banner width (use `Modifier.weight(1f, fill = false)` or equivalent so the dot stays visible).
* **Line 2:** `classInfo.timeLabel`, `maxLines = 1`, `overflow = TextOverflow.Ellipsis`, `labelMedium` or `labelSmall` with `FontWeight.SemiBold`, `Color.White.copy(alpha = 0.92f)`. Indent it so it starts under the tag text, not under the dot (dot is 8.dp wide with 6.dp spacing, so start padding of 14.dp). A small `Icons.Default.Schedule` icon (already imported in the file, 12.dp, white) before the time text is welcome but optional.
* Banner padding: `horizontal = 16.dp`, `vertical = 8.dp` (same as now), with `Arrangement.spacedBy(2.dp)` between the two lines.
* Both lines are always rendered, so every banner has the same height.

### 3.2 Equal collapsed height for every card (fixes Problem B)

Make the collapsed height deterministic, so it is identical for every card, regardless of data. Do it by structure, not by hard-coding one pixel height (the app supports different phone sizes and font scales):

1. **Title:** `course?.name ?: "Lecture"` gets `minLines = 2`, `maxLines = 2`, `overflow = TextOverflow.Ellipsis`. A short name still reserves two lines, a long name is cut with an ellipsis. (`minLines` exists in the Compose version used here.)
2. **Code + time range row:** keep as is, but make the time range text `maxLines = 1`, `softWrap = false`, and let it shrink first (`Modifier.weight(1f, fill = false)`), so this row is always exactly one line.
3. **Classroom and instructor:** change from one side-by-side `Row` to **two separate lines stacked vertically**, each with `Modifier.fillMaxWidth().heightIn(min = 20.dp)`, each `maxLines = 1`, `overflow = TextOverflow.Ellipsis`. If a value is blank, still render an empty `Spacer(Modifier.height(20.dp))` in its place, so the height is reserved. Keep the same icons (`Icons.Default.Place`, `Icons.Default.Person`), tint and text style as now.
4. **Pending assignments strip is always shown:**
   * if `classInfo.pendingTasks.isNotEmpty()`: exactly the current expandable strip (same label text, same click, same animation, same `expand_class_tasks_button` tag);
   * if empty: show a non-clickable strip with the **same shape, same padding and same height** as the expandable one, with an `Icons.Default.CheckCircle` icon (add the import `androidx.compose.material.icons.filled.CheckCircle`, it is part of the already used material-icons set) and the text `"No pending assignments"`. Do NOT attach the `expand_class_tasks_button` tag to this one, and do not make it clickable.
   * To keep both strips the same height, give the content row of both a `Modifier.heightIn(min = 24.dp)`.
5. Keep the vertical spacing between sections the same on all cards (use the same `Spacer` heights, no conditional spacers that change the height).

After this, any two collapsed cards must have the same height.

### 3.3 Expanded behaviour

When the user expands one card's assignment list, only that card may grow taller. The other cards keep their collapsed height. Do not try to force equal height in the expanded state, and make sure the `LazyRow` does not clip the expanded card (it must keep wrapping its content height, no fixed height on the `LazyRow`).

---

## 4. Do not use these approaches

* Do not wrap the `LazyRow` in `IntrinsicSize.Max` / `Modifier.height(IntrinsicSize.Max)`. Lazy layouts do not support intrinsic measurement and it can crash.
* Do not hard-code a single `dp` height for the card.
* Do not shrink the font size to make text fit.
* Do not change the card width (`fillParentMaxWidth(0.9f)` stays, so the next card still peeks in from the right as a hint that the row scrolls).

---

## 5. Before you finish, verify (and fix anything that fails)

1. The project compiles: no unresolved imports, no unused-but-broken code, no changed public signatures.
2. `git diff` style review: only `CurrentNextClassCard.kt` changed (plus possibly one line in `DeadlinesTab.kt`, and say so if so).
3. Walk through these 6 cases mentally and confirm the layout holds for each, with the banner two lines and the same collapsed height:
   * active class, 2 pending assignments
   * upcoming class today, `timeLabel` = `"Starts in 45 min"`
   * upcoming class on another day, `timeLabel` = `"Wednesday at 02:00 PM"`
   * no pending assignments
   * blank classroom and blank instructor
   * very long course name, and a very long instructor name
4. Both light and dark themes still read well (white text on the green and on the course colour banner as before).
5. Large system font size (font scale 1.3): text may truncate with an ellipsis but must never overlap or push the card out of shape.

## 6. Final message format

Reply with: (1) the full updated `CurrentNextClassCard.kt`, (2) a 3-line summary of what changed, (3) a list of anything you noticed but deliberately did not change.
