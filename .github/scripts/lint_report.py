#!/usr/bin/env python3
"""Turn the Android lint XML report into output that is easy to act on.

Usage:
    python3 .github/scripts/lint_report.py [path/to/lint-results-debug.xml]
    python3 .github/scripts/lint_report.py self-test

What it does:
  * Prints every problem in the job log as   file:line:column   with the code line,
    a caret under the exact spot, the lint rule id, why it matters and how to silence it.
  * Adds a red/yellow annotation on the exact line in the GitHub "Files changed" view.
  * Writes a full summary table to the job summary page (GITHUB_STEP_SUMMARY).
  * Exits with 1 if there is at least one Error or Fatal problem, otherwise 0.
    Warnings never fail the run.
"""
import contextlib
import io
import os
import sys
import tempfile
import xml.etree.ElementTree as ET
from collections import Counter

DEFAULT_REPORT = "app/build/reports/lint-results-debug.xml"
ERROR_LEVELS = ("Fatal", "Error")
ORDER = {"Fatal": 0, "Error": 1, "Warning": 2, "Informational": 3}
ANNOTATION_COMMAND = {"Fatal": "error", "Error": "error", "Warning": "warning", "Informational": "notice"}
EXPLANATION_LIMIT = 600


def rel_path(path):
    """Show paths relative to the project root, not /home/runner/work/..."""
    if not path:
        return "(no file)"
    try:
        rel = os.path.relpath(path, os.getcwd())
    except ValueError:
        return path
    return path if rel.startswith("..") else rel.replace(os.sep, "/")


def parse(report_path):
    root = ET.parse(report_path).getroot()
    problems = []
    # Older lint calls them <issue>, newer lint can call them <incident>.
    for node in list(root.iter("issue")) + list(root.iter("incident")):
        if node.get("id") is None:
            continue
        locations = node.findall("location")
        first = locations[0] if locations else None
        problems.append({
            "id": node.get("id"),
            "severity": node.get("severity", "Warning"),
            "category": node.get("category", ""),
            "message": (node.get("message") or "").strip(),
            "explanation": " ".join((node.get("explanation") or "").split()),
            "url": node.get("url") or "",
            "line1": node.get("errorLine1") or "",
            "line2": node.get("errorLine2") or "",
            "file": rel_path(first.get("file")) if first is not None else "(no file)",
            "line": int(first.get("line") or 0) if first is not None else 0,
            "column": int(first.get("column") or 0) if first is not None else 0,
            "also": [
                f"{rel_path(loc.get('file'))}:{loc.get('line') or '?'}" for loc in locations[1:]
            ],
        })
    problems.sort(key=lambda p: (ORDER.get(p["severity"], 9), p["file"], p["line"], p["id"]))
    return problems


def suppress_hint(problem):
    path = problem["file"]
    if path.endswith((".kt", ".java")):
        return f'@Suppress("{problem["id"]}") or @SuppressLint("{problem["id"]}") on the line or function'
    if path.endswith(".xml"):
        return f'tools:ignore="{problem["id"]}" on the element (add xmlns:tools="http://schemas.android.com/tools")'
    return f'lint {{ disable += "{problem["id"]}" }} in app/build.gradle.kts'


def escape_annotation(text):
    return text.replace("%", "%25").replace("\r", "%0D").replace("\n", "%0A")


def print_problem(problem):
    where = problem["file"]
    if problem["line"]:
        where += f":{problem['line']}"
        if problem["column"]:
            where += f":{problem['column']}"
    print(f"{problem['severity'].upper():<7} [{problem['id']}] {where}")
    print(f"    {problem['message']}")
    if problem["line1"]:
        print(f"    > {problem['line1'].strip()}")
        if problem["line2"].strip():
            print(f"      {problem['line2'].strip()}")
    for extra in problem["also"]:
        print(f"    also at {extra}")
    if problem["explanation"]:
        text = problem["explanation"]
        if len(text) > EXPLANATION_LIMIT:
            text = text[:EXPLANATION_LIMIT].rsplit(" ", 1)[0] + " ..."
        print(f"    why: {text}")
    if problem["url"]:
        print(f"    docs: {problem['url']}")
    print(f"    silence only if it is a false alarm: {suppress_hint(problem)}")
    print()


def annotate(problem):
    command = ANNOTATION_COMMAND.get(problem["severity"], "warning")
    props = []
    if problem["file"] != "(no file)":
        props.append(f"file={problem['file']}")
        if problem["line"]:
            props.append(f"line={problem['line']}")
            if problem["column"]:
                props.append(f"col={problem['column']}")
    props.append(f"title=Lint {problem['severity']}: {problem['id']}")
    message = problem["message"]
    if problem["line1"]:
        message += f"\n{problem['line1'].strip()}"
    print(f"::{command} {','.join(props)}::{escape_annotation(message)}")


def write_summary(problems, counts):
    path = os.environ.get("GITHUB_STEP_SUMMARY")
    if not path:
        return
    lines = ["## Android lint results", ""]
    if not problems:
        lines.append("No lint problems found.")
    else:
        lines.append("| Fatal | Error | Warning | Informational |")
        lines.append("|---:|---:|---:|---:|")
        lines.append(f"| {counts['Fatal']} | {counts['Error']} | {counts['Warning']} | {counts['Informational']} |")
        lines.append("")
        lines.append("| Level | Rule | Location | Problem |")
        lines.append("|---|---|---|---|")
        for p in problems:
            where = p["file"] + (f":{p['line']}" if p["line"] else "")
            message = p["message"].replace("|", "\\|").replace("\n", " ")
            lines.append(f"| {p['severity']} | `{p['id']}` | `{where}` | {message} |")
        lines.append("")
        lines.append("Open the `test-and-lint-reports` artifact for the full HTML report.")
    with open(path, "a", encoding="utf-8") as handle:
        handle.write("\n".join(lines) + "\n")


def report(report_path):
    if not os.path.exists(report_path):
        print(f"::warning::Lint report not found at {report_path}. Lint probably crashed "
              "before finishing; read the 'Run Android lint' step above for the reason.")
        return 0
    problems = parse(report_path)
    counts = Counter(p["severity"] for p in problems)
    for level in ORDER:
        counts.setdefault(level, 0)
    failing = [p for p in problems if p["severity"] in ERROR_LEVELS]

    print("=" * 72)
    print(f"LINT: {counts['Fatal']} fatal, {counts['Error']} errors, "
          f"{counts['Warning']} warnings, {counts['Informational']} informational")
    print("=" * 72)
    print()
    if failing:
        print(f"::group::{len(failing)} problem(s) that FAIL the build (fix these first)")
        for problem in failing:
            print_problem(problem)
        print("::endgroup::")
    others = [p for p in problems if p["severity"] not in ERROR_LEVELS]
    if others:
        print(f"::group::{len(others)} warning(s) (do not fail the build)")
        for problem in others:
            print_problem(problem)
        print("::endgroup::")
    for problem in problems:
        annotate(problem)
    write_summary(problems, counts)
    if failing:
        print(f"Lint failed: {len(failing)} error(s). Each one is listed above with its file and line.")
        return 1
    print("Lint passed.")
    return 0


SAMPLE = """<?xml version="1.0" encoding="UTF-8"?>
<issues format="6" by="lint 8.7.0">
  <issue id="MissingClass" severity="Error" message="Class referenced in the manifest, `com.example.Missing`, was not found"
         category="Correctness" explanation="If a class is referenced in the manifest or in a layout file, it must also exist in the project."
         url="https://developer.android.com/studio/write/lint" errorLine1="        &lt;receiver android:name=&quot;.Missing&quot; />" errorLine2="                 ~~~~~~~~">
    <location file="{root}/app/src/main/AndroidManifest.xml" line="45" column="18"/>
  </issue>
  <issue id="UnusedResources" severity="Warning" message="The resource `R.string.old` appears to be unused"
         category="Performance" explanation="Unused resources make applications larger.">
    <location file="{root}/app/src/main/res/values/strings.xml" line="3" column="13"/>
  </issue>
</issues>
"""


def self_test():
    # Stay silent: no fake annotations, no fake summary table in the real CI run.
    summary_file = os.environ.pop("GITHUB_STEP_SUMMARY", None)
    try:
        with tempfile.TemporaryDirectory() as tmp, contextlib.redirect_stdout(io.StringIO()):
            sample = os.path.join(tmp, "lint.xml")
            with open(sample, "w", encoding="utf-8") as handle:
                handle.write(SAMPLE.format(root=os.getcwd()))
            problems = parse(sample)
            assert [p["id"] for p in problems] == ["MissingClass", "UnusedResources"], problems
            assert problems[0]["file"] == "app/src/main/AndroidManifest.xml", problems[0]["file"]
            assert (problems[0]["line"], problems[0]["column"]) == (45, 18)
            assert report(sample) == 1, "an Error must fail the run"
            assert report(os.path.join(tmp, "missing.xml")) == 0, "a missing report must not crash"
    finally:
        if summary_file is not None:
            os.environ["GITHUB_STEP_SUMMARY"] = summary_file
    print("lint_report self-test passed")


if __name__ == "__main__":
    argument = sys.argv[1] if len(sys.argv) > 1 else DEFAULT_REPORT
    if argument == "self-test":
        self_test()
    else:
        sys.exit(report(argument))