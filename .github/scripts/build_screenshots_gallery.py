#!/usr/bin/env python3
"""Generate the README screenshot gallery from an emulator capture.

Normal run (used by the "Refresh README screenshots" workflow):
    python3 .github/scripts/build_screenshots_gallery.py
  Needs every required screenshot as docs/screenshots/<name>.png. Optional screenshots are
  included when their PNG exists and left out when it does not. PNGs are turned into JPEGs.

Layout only (no images are converted or deleted):
    python3 .github/scripts/build_screenshots_gallery.py --readme-only
  Rebuilds the README section from the .jpg files already in docs/screenshots/. Use it after
  changing the layout below. Screens whose image does not exist yet are left out.

Keep the names here in sync with ReadmeScreenshotTest.kt and capture_readme_screenshots.sh.
"""

from __future__ import annotations

import re
import sys
from pathlib import Path
from PIL import Image

ROOT = Path(__file__).resolve().parents[2]
COLUMNS = 3
IMAGE_WIDTH = 220

# (file name without extension, title, description)
Screen = tuple[str, str, str]

# Each section becomes a heading in the README. Settings are collapsed to keep the page short.
# (heading, intro sentence, screens, collapsed)
SECTIONS: list[tuple[str, str, list[Screen], bool]] = [
    ("Plan your work", "Deadlines, classes and courses, plus personal tasks that need no account.", [
        ("deadlines", "Deadlines", "Upcoming work and daily overview"),
        ("add_task", "New assignment", "Add a deadline with priority, reminders and files"),
        ("my_tasks", "My tasks", "Personal to-do list"),
        ("timetable", "Timetable", "Weekly class schedule"),
        ("courses", "Courses", "Course list and colour coding"),
        ("course_detail", "Course details", "Every task and class for one course"),
    ], False),
    ("Study tools", "Everything in the More menu, from the timer to your weekly progress.", [
        ("tools_menu", "More menu", "Notes, timer, progress, search and more"),
        ("timer", "Study timer", "Stopwatch and countdown for study sessions"),
        ("timer_pomodoro", "Pomodoro", "Focus and break rounds"),
        ("timer_options", "Pomodoro options", "Alarm or vibrate, and auto-start the next round"),
        ("progress", "Progress", "Tasks finished this week and your streak"),
    ], False),
    ("Notes", "Markdown notes with a live preview, linked to your courses.", [
        ("notes", "Notes", "Pinned and course-linked notes"),
        ("note_preview", "Note preview", "Formatted Markdown preview"),
        ("note_editor", "Note editor", "Markdown editing and formatting tools"),
    ], False),
    ("Groups, search and trash", "Share work with classmates, find anything fast and undo a deletion.", [
        ("groups", "Study groups", "Shared study spaces"),
        ("global_search", "Global search", "Find tasks, notes and courses in one place"),
        ("recently_deleted", "Recently deleted", "Restore deleted tasks, notes and classes for 30 days"),
    ], False),
    ("Settings", "Reminders, backup and account options, one section at a time.", [
        ("settings_menu", "Settings", "Settings overview"),
        ("settings_general", "General settings", "Theme and app preferences"),
        ("settings_notifications", "Notifications", "Reminder controls"),
        ("settings_backup", "Backup and data", "Backup and data options"),
        ("settings_about", "About and help", "App information and support"),
    ], True),
    ("Themes", "UniTask follows the system theme or can be set to light or dark, with ten accent colours chosen in Settings.", [
        ("theme_dark", "Dark theme", "Deadlines in dark mode"),
        ("theme_accent", "Accent colours", "Ten accent colours, shown here in Rose"),
    ], False),
]

# The capture is only complete when these exist. Every other screen is optional: the emulator
# test skips a failed extra step instead of failing the whole run.
OPTIONAL_SCREENS = {
    "add_task", "course_detail", "tools_menu", "timer_pomodoro", "timer_options", "progress",
}

ALL_SCREENS: list[Screen] = [screen for _, _, screens, _ in SECTIONS for screen in screens]


def slug(heading: str) -> str:
    """GitHub's heading anchor: lower case, spaces to hyphens, punctuation dropped."""
    return re.sub(r"[^a-z0-9\- ]", "", heading.lower()).replace(" ", "-")


def build_row(items: list[Screen]) -> str:
    cells = []
    for name, title, description in items:
        cells.append(
            f'    <td align="center" valign="top"><a href="docs/screenshots/{name}.jpg">'
            f'<img src="docs/screenshots/{name}.jpg" width="{IMAGE_WIDTH}" alt="{title}" /></a>'
            f'<br/><sub><b>{title}</b><br/>{description}</sub></td>'
        )
    return "  <tr>\n" + "\n".join(cells) + "\n  </tr>"


def build_table(items: list[Screen]) -> str:
    """Full rows go in one table. Leftover screens get their own centred table, so the last
    row has no empty cells on the right."""
    full_count = len(items) - len(items) % COLUMNS
    parts = []
    if full_count:
        rows = [build_row(items[i:i + COLUMNS]) for i in range(0, full_count, COLUMNS)]
        parts.append('<table align="center">\n' + "\n".join(rows) + "\n</table>")
    leftover = items[full_count:]
    if leftover:
        parts.append('<table align="center">\n' + build_row(leftover) + "\n</table>")
    return "\n\n".join(parts)


def build_section(version: str, available: set[str]) -> str:
    shown = [(h, i, [s for s in screens if s[0] in available], c) for h, i, screens, c in SECTIONS]
    shown = [entry for entry in shown if entry[2]]

    out = ["## Screenshots\n"]
    out.append("Jump to: " + " · ".join(f"[{h}](#{slug(h)})" for h, _, _, _ in shown) + "\n")
    for heading, intro, screens, collapsed in shown:
        out.append(f"### {heading}\n")
        out.append(intro + "\n")
        table = build_table(screens)
        if collapsed:
            # A blank line after <summary> and before </details> is needed for GitHub to render
            # the table inside the collapsed block.
            out.append(f"<details>\n<summary>Show the {len(screens)} {heading.lower()} screens</summary>\n\n"
                       f"{table}\n\n</details>\n")
        else:
            out.append(table + "\n")

    out.append(
        f'<p align="center"><sub>Automatically captured from UniTask {version} '
        "using fictional demo data on an Android emulator.</sub></p>\n"
    )
    out.append(
        "Each screen is also available separately: "
        + " · ".join(f"[**{title}**](docs/screenshots/{name}.jpg)"
                     for name, title, _ in ALL_SCREENS if name in available)
        + ".\n"
    )
    return "\n".join(out) + "\n"


def main() -> None:
    readme_only = "--readme-only" in sys.argv[1:]
    directory = ROOT / "docs/screenshots"

    gradle = (ROOT / "app/build.gradle.kts").read_text(encoding="utf-8")
    version_match = re.search(r'versionName\s*=\s*"([^"]+)"', gradle)
    version = version_match.group(1) if version_match else "current"

    if readme_only:
        available = {name for name, _, _ in ALL_SCREENS if (directory / f"{name}.jpg").is_file()}
    else:
        missing = [f"{name}.png" for name, _, _ in ALL_SCREENS
                   if name not in OPTIONAL_SCREENS and not (directory / f"{name}.png").is_file()]
        if missing:
            raise SystemExit("Capture is incomplete; missing: " + ", ".join(missing))
        available = {name for name, _, _ in ALL_SCREENS if (directory / f"{name}.png").is_file()}
        skipped = [name for name, _, _ in ALL_SCREENS if name not in available]
        if skipped:
            print("Optional screenshots not captured, left out of the gallery: " + ", ".join(skipped))

        # Compress emulator PNGs to high-quality JPEGs to keep repository growth modest.
        for name in available:
            with Image.open(directory / f"{name}.png") as image:
                image.convert("RGB").save(
                    directory / f"{name}.jpg", "JPEG", quality=88, optimize=True, progressive=True
                )

    readme_path = ROOT / "README.md"
    readme = readme_path.read_text(encoding="utf-8")
    start = readme.find("## Screenshots")
    end = readme.find("\n## Installation", start)
    if start < 0 or end < 0:
        raise SystemExit("Could not locate the README Screenshots section.")
    readme_path.write_text(
        readme[:start] + build_section(version, available) + readme[end + 1:], encoding="utf-8"
    )

    if not readme_only:
        for old in directory.glob("*.jpeg"):
            old.unlink()
        for old in directory.glob("*.png"):
            old.unlink()

    print(f"Updated the README screenshot gallery for UniTask {version} "
          f"({len(available)} screens{', layout only' if readme_only else ''}).")


if __name__ == "__main__":
    main()
