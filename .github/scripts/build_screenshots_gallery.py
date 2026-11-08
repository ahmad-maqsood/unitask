#!/usr/bin/env python3
"""Generate the README screenshot gallery from a complete emulator capture."""

from __future__ import annotations

import re
from pathlib import Path
from PIL import Image

ROOT = Path(__file__).resolve().parents[2]
SCREENS = [
    ("deadlines", "Deadlines", "Upcoming work and daily overview"),
    ("timetable", "Timetable", "Weekly class schedule"),
    ("courses", "Courses", "Course list and colour coding"),
    ("groups", "Study groups", "Shared study spaces"),
    ("group_chat", "Group chat", "A real conversation with fictional demo messages"),
    ("my_tasks", "My tasks", "Personal to-do list"),
    ("timer", "Study timer", "Stopwatch and countdown for study sessions"),
    ("global_search", "Global search", "Find tasks, notes and courses in one place"),
    ("notes", "Notes", "Pinned and course-linked notes"),
    ("note_preview", "Note preview", "Formatted Markdown preview"),
    ("note_editor", "Note editor", "Markdown editing and formatting tools"),
    ("recently_deleted", "Recently deleted", "Restore deleted tasks, notes and classes for 30 days"),
    ("settings_menu", "Settings", "Settings overview"),
    ("settings_general", "General settings", "Theme and app preferences"),
    ("settings_notifications", "Notifications", "Reminder controls"),
    ("settings_backup", "Backup and data", "Backup and data options"),
    ("settings_about", "About and help", "App information and support"),
]

# Shown in their own "Themes" table below the main gallery.
THEME_SCREENS = [
    ("theme_dark", "Dark theme", "Deadlines in dark mode"),
    ("theme_accent", "Accent colours", "Six accent colours, shown here in Rose"),
]
ALL_SCREENS = SCREENS + THEME_SCREENS


def main():
    directory = ROOT / "docs/screenshots"
    missing = [f"{name}.png" for name, _, _ in ALL_SCREENS
               if not (directory / f"{name}.png").is_file()]
    if missing:
        raise SystemExit("Capture is incomplete; missing: " + ", ".join(missing))

    gradle = (ROOT / "app/build.gradle.kts").read_text(encoding="utf-8")
    version_match = re.search(r'versionName\s*=\s*"([^"]+)"', gradle)
    version = version_match.group(1) if version_match else "current"

    # Compress emulator PNGs to high-quality JPEGs to keep repository growth modest.
    for name, _, _ in ALL_SCREENS:
        source = directory / f"{name}.png"
        target = directory / f"{name}.jpg"
        with Image.open(source) as image:
            image.convert("RGB").save(
                target, "JPEG", quality=88, optimize=True, progressive=True
            )

    def build_row(items):
        cells = []
        for name, title, description in items:
            cells.append(
                f'    <td align="center"><a href="docs/screenshots/{name}.jpg">'
                f'<img src="docs/screenshots/{name}.jpg" width="220" alt="{title}" /></a>'
                f'<br/><sub><b>{title}</b><br/>{description}</sub></td>'
            )
        return "  <tr>\n" + "\n".join(cells) + "\n  </tr>"

    # Full rows of 3 go in one table. Any leftover screens (for example the last two when
    # there are 17 screens) get their own centered table, so there is no empty cell on the right.
    def build_table(items):
        full_count = len(items) - len(items) % 3
        full_rows = [build_row(items[i:i + 3]) for i in range(0, full_count, 3)]
        parts = []
        if full_rows:
            parts.append('<table align="center">\n' + "\n".join(full_rows) + "\n</table>")
        leftover = items[full_count:]
        if leftover:
            parts.append('<table align="center">\n' + build_row(leftover) + "\n</table>")
        return "\n\n".join(parts)

    table = build_table(SCREENS)
    themes_table = build_table(THEME_SCREENS)
    links = "Each screen is also available separately: " + " · ".join(
        f'[**{title}**](docs/screenshots/{name}.jpg)'
        for name, title, _ in ALL_SCREENS
    ) + "."

    section = (
        "## Screenshots\n\n"
        + table
        + "\n\n### Themes\n\nUniTask follows the system theme or can be set to light or dark, with six accent colours chosen in Settings.\n\n"
        + themes_table
        + f'\n\n<p align="center"><sub>Automatically captured from UniTask {version} '
          "using fictional demo data on an Android emulator.</sub></p>\n\n"
        + links + "\n\n"
    )

    readme_path = ROOT / "README.md"
    readme = readme_path.read_text(encoding="utf-8")
    start = readme.find("## Screenshots")
    end = readme.find("\n## Installation", start)
    if start < 0 or end < 0:
        raise SystemExit("Could not locate the README Screenshots section.")
    readme_path.write_text(readme[:start] + section + readme[end + 1:], encoding="utf-8")

    for old in directory.glob("*.jpeg"):
        old.unlink()
    for old in directory.glob("*.png"):
        old.unlink()

    print(f"Updated the README screenshot gallery for UniTask {version}.")


if __name__ == "__main__":
    main()
