#!/usr/bin/env python3
"""Generate the README screenshot gallery from a complete emulator capture."""

from __future__ import annotations

import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SCREENS = [
    ("deadlines", "Deadlines", "Upcoming work and daily overview"),
    ("timetable", "Timetable", "Weekly class schedule"),
    ("courses", "Courses", "Course list and colour coding"),
    ("groups", "Study groups", "Shared study spaces"),
    ("group_chat", "Group chat", "A real conversation with fictional demo messages"),
    ("my_tasks", "My tasks", "Personal to-do list"),
    ("notes", "Notes", "Pinned and course-linked notes"),
    ("settings_menu", "Settings", "Settings overview"),
    ("settings_general", "General settings", "Theme and app preferences"),
    ("settings_notifications", "Notifications", "Reminder controls"),
    ("settings_backup", "Backup and data", "Backup and data options"),
    ("settings_about", "About and help", "App information and support"),
]


def main():
    directory = ROOT / "docs/screenshots"
    missing = [f"{name}.png" for name, _, _ in SCREENS
               if not (directory / f"{name}.png").is_file()]
    if missing:
        raise SystemExit("Capture is incomplete; missing: " + ", ".join(missing))

    gradle = (ROOT / "app/build.gradle.kts").read_text(encoding="utf-8")
    version_match = re.search(r'versionName\s*=\s*"([^"]+)"', gradle)
    version = version_match.group(1) if version_match else "current"

    rows = []
    for offset in range(0, len(SCREENS), 3):
        cells = []
        for name, title, description in SCREENS[offset:offset + 3]:
            cells.append(
                f'    <td align="center"><a href="docs/screenshots/{name}.png">'
                f'<img src="docs/screenshots/{name}.png" width="220" alt="{title}" /></a>'
                f'<br/><sub><b>{title}</b><br/>{description}</sub></td>'
            )
        while len(cells) < 3:
            cells.append("    <td></td>")
        rows.append("  <tr>\n" + "\n".join(cells) + "\n  </tr>")

    table = '<table align="center">\n' + "\n".join(rows) + "\n</table>"
    links = "Each screen is also available separately: " + " · ".join(
        f'[**{title}**](docs/screenshots/{name}.png)'
        for name, title, _ in SCREENS
    ) + "."

    section = (
        "## Screenshots\n\n"
        + table
        + f'\n\n<p align="center"><sub>Automatically captured from UniTask {version} '
          "using fictional demo data on an Android emulator.</sub></p>\n\n"
        + links + "\n"
    )

    readme_path = ROOT / "README.md"
    readme = readme_path.read_text(encoding="utf-8")
    start = readme.find("## Screenshots")
    end = readme.find("\n## Installation", start)
    if start < 0 or end < 0:
        raise SystemExit("Could not locate the README Screenshots section.")
    readme_path.write_text(readme[:start] + section + readme[end + 1:], encoding="utf-8")

    # Remove the former hand-maintained JPEG gallery after all PNGs are verified.
    for pattern in ("*.jpeg", "*.jpg"):
        for old in directory.glob(pattern):
            old.unlink()

    print(f"Updated the README screenshot gallery for UniTask {version}.")


if __name__ == "__main__":
    main()
