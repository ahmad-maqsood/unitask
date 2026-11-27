#!/usr/bin/env python3
"""Set versionName and versionCode in app/build.gradle.kts for a release.

versionName is the version you type. versionCode is worked out: Android only installs an update
whose versionCode is higher, so it becomes one more than the highest code in this checkout or in
any release tag. If the file already holds this version with a code above every tag, it is kept.
"""
import argparse
import re
import subprocess
import sys
from pathlib import Path

GRADLE_FILE = "app/build.gradle.kts"
VERSION_PATTERN = re.compile(r"(\d+)\.(\d+)\.(\d+)(-[0-9A-Za-z]+(\.[0-9A-Za-z]+)*)?")
NAME_PATTERN = re.compile(r'(\bversionName\s*=\s*")([^"]+)(")')
CODE_PATTERN = re.compile(r"(\bversionCode\s*=\s*)(\d+)")


def clean_version(raw):
    version = raw.strip().removeprefix("v")
    if not VERSION_PATTERN.fullmatch(version):
        raise RuntimeError(f"'{raw}' is not a version. Use a form like 4.8.0 or 4.8.0-beta1.")
    return version


def read_version(text):
    name, code = NAME_PATTERN.search(text), CODE_PATTERN.search(text)
    if not name or not code:
        raise RuntimeError(f"Could not read versionName or versionCode from {GRADLE_FILE}.")
    return name.group(2), int(code.group(2))


def number_parts(version):
    match = VERSION_PATTERN.match(version)
    return tuple(int(match.group(i)) for i in (1, 2, 3)) if match else (0, 0, 0)


def next_code(new_name, current_name, current_code, highest_tag_code):
    if new_name == current_name and current_code > highest_tag_code:
        return current_code
    return max(current_code, highest_tag_code) + 1


def apply_version(text, new_name, highest_tag_code):
    current_name, current_code = read_version(text)
    if number_parts(new_name) < number_parts(current_name):
        raise RuntimeError(f"Version {new_name} is older than the current {current_name}.")
    code = next_code(new_name, current_name, current_code, highest_tag_code)
    text = NAME_PATTERN.sub(lambda m: m.group(1) + new_name + m.group(3), text, count=1)
    text = CODE_PATTERN.sub(lambda m: m.group(1) + str(code), text, count=1)
    return text, code


def git(*args):
    return subprocess.run(["git", *args], capture_output=True, text=True, check=True).stdout


def highest_tag_code():
    highest = 0
    for tag in git("tag", "--list", "v*").split():
        try:
            match = CODE_PATTERN.search(git("show", f"{tag}:{GRADLE_FILE}"))
        except subprocess.CalledProcessError:
            continue  # very old tags may not have this file
        if match:
            highest = max(highest, int(match.group(2)))
    return highest


def self_test():
    sample = '    versionCode = 27\n    versionName = "4.7.0"\n'
    checks = [
        apply_version(sample, "4.8.0", 27) == ('    versionCode = 28\n    versionName = "4.8.0"\n', 28),
        apply_version(sample, "4.8.0", 30)[1] == 31,  # a tag is ahead of this file
        apply_version(sample, "4.7.0", 26)[1] == 27,  # already bumped, so kept
        apply_version(sample, "4.7.0", 27)[1] == 28,  # already released, so raised
        apply_version(sample, "4.8.0-beta1", 27)[1] == 28,
        clean_version(" v4.8.0 ") == "4.8.0",
    ]
    for bad in ("4.8", "4.8.0; echo hi", '4.8.0"', ""):
        try:
            clean_version(bad)
            checks.append(False)
        except RuntimeError:
            checks.append(True)
    try:
        apply_version(sample, "4.6.0", 27)
        checks.append(False)
    except RuntimeError:
        checks.append(True)
    if not all(checks):
        raise RuntimeError(f"Version-bump self-test failed: {checks}")
    print("Version-bump self-test passed.")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--version")
    parser.add_argument("--self-test", action="store_true")
    args = parser.parse_args()
    if args.self_test:
        self_test()
        return
    name = clean_version(args.version or "")
    path = Path(GRADLE_FILE)
    text, code = apply_version(path.read_text(encoding="utf-8"), name, highest_tag_code())
    path.write_text(text, encoding="utf-8")
    print(f"Set versionName {name}, versionCode {code}.")


if __name__ == "__main__":
    try:
        main()
    except Exception as error:
        print(f"::error::{error}", file=sys.stderr)
        sys.exit(1)
