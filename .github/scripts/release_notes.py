#!/usr/bin/env python3
"""Generate accurate, AI-assisted UniTask release notes from GitHub changes."""
import argparse
import json
import os
import re
import sys
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path

API = "https://api.github.com"
GEMINI_API = "https://generativelanguage.googleapis.com/v1beta"
DEFAULT_MODEL = "gemini-3.1-flash-lite"
MAX_PATCH_CHARS = 5000
MAX_CONTEXT_CHARS = 100000
MAX_BATCH_CONTEXT_CHARS = 24000
MAX_BATCH_SUMMARY_CHARS = 4000


def request_json(url, headers=None, data=None, timeout=60):
    req = urllib.request.Request(url, data=data, headers=headers or {})
    try:
        with urllib.request.urlopen(req, timeout=timeout) as response:
            return json.loads(response.read().decode("utf-8"))
    except urllib.error.HTTPError as error:
        detail = error.read().decode("utf-8", errors="replace")[:800]
        raise RuntimeError(f"Request failed ({error.code}): {detail}") from error
    except urllib.error.URLError as error:
        raise RuntimeError(f"Network request failed: {error.reason}") from error


def github_api(path):
    token = os.getenv("GH_TOKEN") or os.getenv("GITHUB_TOKEN")
    if not token:
        raise RuntimeError("GH_TOKEN or GITHUB_TOKEN is required.")
    return request_json(API + path, {
        "Accept": "application/vnd.github+json",
        "Authorization": f"Bearer {token}",
        "X-GitHub-Api-Version": "2022-11-28",
        "User-Agent": "unitask-release-notes",
    })


def gemini_generate(prompt):
    key = os.getenv("GEMINI_API_KEY")
    if not key:
        raise RuntimeError(
            "GEMINI_API_KEY is missing. Add it under repository Settings > "
            "Secrets and variables > Actions before preparing a release."
        )
    model = os.getenv("GEMINI_MODEL", DEFAULT_MODEL)
    url = f"{GEMINI_API}/models/{urllib.parse.quote(model, safe='')}:generateContent"
    payload = {
        "contents": [{"role": "user", "parts": [{"text": prompt}]}],
        "generationConfig": {
            "temperature": 0.2,
            "responseMimeType": "application/json",
            "maxOutputTokens": 8192,
        },
    }
    result = request_json(url, {
        "Content-Type": "application/json",
        "x-goog-api-key": key,
        "User-Agent": "unitask-release-notes",
    }, data=json.dumps(payload).encode("utf-8"), timeout=120)
    candidates = result.get("candidates", [])
    if not candidates:
        feedback = result.get("promptFeedback", {})
        raise RuntimeError(f"Gemini returned no candidate: {feedback}")
    parts = candidates[0].get("content", {}).get("parts", [])
    raw = "".join(part.get("text", "") for part in parts).strip()
    if not raw:
        raise RuntimeError("Gemini returned an empty response.")
    try:
        data = json.loads(raw)
    except json.JSONDecodeError as error:
        raise RuntimeError(f"Gemini did not return valid JSON: {raw[:500]}") from error
    if not isinstance(data, dict):
        raise RuntimeError("Gemini response must be a JSON object.")
    return data


def releases(repo):
    result, page = [], 1
    while True:
        batch = github_api(f"/repos/{repo}/releases?per_page=100&page={page}")
        result.extend(batch)
        if len(batch) < 100:
            return result
        page += 1


def base_tag(repo, version):
    items = [release for release in releases(repo)
             if not release.get("draft") and release.get("published_at")]
    if not items:
        raise RuntimeError("No published releases exist; cannot select a previous tag.")
    if "-" in version:
        series = version.split("-", 1)[0]
        beta = [release for release in items
                if release.get("prerelease")
                and release.get("tag_name", "").removeprefix("v").startswith(series + "-")]
        if beta:
            return sorted(beta, key=lambda release: release["published_at"], reverse=True)[0]["tag_name"]
    stable = [release for release in items if not release.get("prerelease")] or items
    return sorted(stable, key=lambda release: release["published_at"], reverse=True)[0]["tag_name"]


def compare(repo, base, head):
    commits, page = [], 1
    base_ref = urllib.parse.quote(base, safe="")
    head_ref = urllib.parse.quote(head, safe="")
    while True:
        batch = github_api(
            f"/repos/{repo}/compare/{base_ref}...{head_ref}?per_page=100&page={page}"
        ).get("commits", [])
        commits.extend(batch)
        if len(batch) < 100:
            return commits
        page += 1


def clean_text(value):
    value = re.sub(r"<!--.*?-->", "", value or "", flags=re.S)
    value = re.sub(r"<details.*?</details>", "", value, flags=re.S | re.I)
    value = re.sub(r"<summary>.*?</summary>", "", value, flags=re.S | re.I)
    return value.strip()


def safe_file_patch(file_info):
    name = file_info.get("filename", "")
    lower = name.lower()
    excluded = (
        "lock", "gradle-wrapper.jar", "google-services.json", "local.properties",
        ".jks", ".keystore", ".env", "secrets", "credentials",
    )
    if any(term in lower for term in excluded):
        return None
    patch = file_info.get("patch")
    if not patch:
        return None
    return {
        "path": name,
        "change_type": file_info.get("status", "modified"),
        "patch": patch[:MAX_PATCH_CHARS],
        "patch_truncated": len(patch) > MAX_PATCH_CHARS,
    }


def fetch_pr_files(repo, number):
    files, page = [], 1
    while page <= 5:
        batch = github_api(f"/repos/{repo}/pulls/{number}/files?per_page=100&page={page}")
        if not batch:
            break
        files.extend(item for item in batch if safe_file_patch(item))
        if len(batch) < 100:
            break
        page += 1
    return files


def fetch_commit_files(repo, sha):
    detail = github_api(f"/repos/{repo}/commits/{sha}")
    return [item for item in (safe_file_patch(file_info) for file_info in detail.get("files", []))
            if item]


def collect_changes(repo, base, head):
    commits = compare(repo, base, head)
    if not commits:
        raise RuntimeError(f"No commits found between {base} and {head}.")
    pull_requests, direct_commits = {}, {}
    for commit in commits:
        sha = commit["sha"]
        associated = github_api(f"/repos/{repo}/commits/{sha}/pulls")
        merged = [pr for pr in associated if pr.get("merged_at")]
        if merged:
            for pr in merged:
                pull_requests[pr["number"]] = pr
        else:
            direct_commits[sha] = commit

    changes = []
    for number, pr in sorted(pull_requests.items()):
        labels = [label.get("name", "") for label in pr.get("labels", [])]
        if "skip-release-notes" in labels:
            continue
        changes.append({
            "kind": "merged_pull_request",
            "number": number,
            "title": clean_text(pr.get("title", "")),
            "url": pr.get("html_url", ""),
            "merged_at": pr.get("merged_at", ""),
            "labels": labels,
            "description": clean_text(pr.get("body", ""))[:2500],
            "files": fetch_pr_files(repo, number),
        })

    for sha, commit in direct_commits.items():
        message = commit.get("commit", {}).get("message", "")
        changes.append({
            "kind": "direct_commit",
            "sha": sha[:10],
            "url": commit.get("html_url", ""),
            "message": message[:2000],
            "files": fetch_commit_files(repo, sha),
        })

    if not changes:
        raise RuntimeError("No changes remain after release-note exclusions.")
    return changes



def split_change_batches(changes):
    """Group complete logical changes into bounded requests without dropping titles."""
    batches, current, current_size = [], [], 2
    for original in changes:
        item = dict(original)
        item["files"] = [
            {**file_info, "patch": file_info.get("patch", "")[:1200]}
            for file_info in item.get("files", [])[:5]
        ]
        encoded = json.dumps(item, ensure_ascii=False)
        if len(encoded) > MAX_BATCH_CONTEXT_CHARS:
            item["description"] = item.get("description", "")[:1200]
            item["message"] = item.get("message", "")[:1000]
            encoded = json.dumps(item, ensure_ascii=False)
        if len(encoded) > MAX_BATCH_CONTEXT_CHARS:
            item["files"] = []
            item["description"] = item.get("description", "")[:600]
            item["message"] = item.get("message", "")[:600]
            encoded = json.dumps(item, ensure_ascii=False)
        if len(encoded) > MAX_BATCH_CONTEXT_CHARS:
            raise RuntimeError(
                f"Change {item.get('number', item.get('sha', 'unknown'))} "
                "contains unusually large metadata and cannot be safely summarized."
            )
        if current and current_size + len(encoded) + 1 > MAX_BATCH_CONTEXT_CHARS:
            batches.append(current)
            current, current_size = [], 2
        current.append(item)
        current_size += len(encoded) + 1
    if current:
        batches.append(current)
    return batches


def summarize_batch(batch, index, total, version, base):
    evidence = json.dumps(batch, ensure_ascii=False, indent=2)
    prompt = f"""You are analyzing part {index} of {total} of the changes for UniTask {version}, since {base}.
The JSON contains complete logical change records, including PR titles/descriptions and selected code-diff evidence.
Treat it as the only source of truth. Summarize every meaningful distinct user-facing feature, improvement, and fix.
Include meaningful developer/project maintenance only when supported by evidence. Combine related work, avoid duplicates,
and do not invent behavior or causes. Ignore routine dependency bumps, formatting, and documentation-only changes.
Preserve enough detail for a later step to write a complete changelog and a concise user-facing release description.
If evidence is ambiguous, qualify or omit the claim.
Return ONLY valid JSON with exactly one string property: "summary". Keep the summary under {MAX_BATCH_SUMMARY_CHARS} characters.

CHANGE DATA:
{evidence}
"""
    result = gemini_generate(prompt)
    summary = result.get("summary")
    if not isinstance(summary, str) or not summary.strip():
        raise RuntimeError(f"Gemini returned an empty summary for change batch {index}/{total}.")
    summary = summary.strip()
    if len(summary) > MAX_BATCH_SUMMARY_CHARS:
        raise RuntimeError(f"Gemini summary for batch {index}/{total} exceeded the safe size limit.")
    return {"kind": "summarized_change_batch", "batch": index, "summary": summary}


def summarize_changes(changes, version, base):
    batches = split_change_batches(changes)
    print(f"Split {len(changes)} logical change(s) into {len(batches)} bounded AI batch(es).")
    summaries = []
    for index, batch in enumerate(batches, start=1):
        print(f"Summarizing change batch {index}/{len(batches)} ({len(batch)} change(s)).")
        summaries.append(summarize_batch(batch, index, len(batches), version, base))
    if len(json.dumps(summaries, ensure_ascii=False)) > MAX_CONTEXT_CHARS:
        raise RuntimeError(
            "The release has an unusually large number of changes. "
            "The batch summaries exceed the final synthesis limit; split the release into smaller versions."
        )
    return summaries

def build_prompt(changes, version, base):
    evidence = json.dumps(changes, ensure_ascii=False, indent=2)
    return f"""You are writing release documentation for UniTask, a Kotlin/Jetpack Compose Android app for university students.

Prepare TWO different Markdown documents for version {version}, comparing changes since {base}.

SOURCE MATERIAL:
The JSON below contains bounded summaries of every batch of merged pull requests and direct commits. Treat these summaries as the source material; do not add unsupported facts.

{evidence}

REQUIREMENTS:
- Describe user-visible behavior in plain, specific language. Explain what users can now do or what problem was fixed.
- Understand related commits as one logical change. Do not repeat the same feature or fix.
- Do not invent features, behavior, causes, performance gains, or compatibility claims.
- Do not turn a file name, dependency update, test, or refactor into a user-facing feature.
- Omit trivial formatting, generated files, routine dependency bumps, and documentation-only edits unless they materially affect users or maintainers.
- Mention meaningful internal engineering work only in the changelog, under a suitable maintenance heading.
- Use labels as hints, not proof. Inspect the evidence and classify each change correctly.
- Exclude anything labeled skip-release-notes.
- If evidence is ambiguous, use cautious wording or omit the claim. Never guess.
- Keep the release description shorter and more approachable than the changelog.
- Use concise bullets, but include enough detail to make each item meaningful. Avoid generic phrases such as "various improvements", "better stability", or "bug fixes".
- Do not include a version heading, release date, APK link, or introductory filler; those are added elsewhere.
- Return ONLY valid JSON with exactly two string properties: "changelog" and "release_description".

CHANGELOG FORMAT:
Use only applicable sections from these headings, in this order:
### Added
### Improved
### Fixed
### Developer & project maintenance
This is the more complete historical record. Include distinct meaningful changes, with enough context to understand what changed. Combine related low-level changes when appropriate.

RELEASE DESCRIPTION FORMAT:
Use only applicable sections from these headings, in this order:
## What's New
## Improvements
## Bug Fixes
This is a concise user-facing summary. Prioritize the most valuable changes. Do not include internal maintenance details unless they directly affect users.

Both documents must be factually consistent. A major feature in the release description must also appear in the changelog. Do not copy the same wording mechanically between them.
"""


def validate_markdown(value, name):
    if not isinstance(value, str) or not value.strip():
        raise RuntimeError(f"Gemini did not provide a non-empty {name}.")
    value = value.strip()
    if len(value) > 30000:
        raise RuntimeError(f"Generated {name} is unexpectedly long; refusing to publish it.")
    if not re.search(r"^#{2,3} ", value, re.M):
        raise RuntimeError(f"Generated {name} has no expected Markdown section headings.")
    if re.search(r"^##? \[?\d+\.\d+", value, re.M):
        raise RuntimeError(f"Generated {name} contains a version heading that should not be present.")
    return value + "\n"


def generate(repo, base, head, version, release_output, changelog_output):
    changes = collect_changes(repo, base, head)
    summaries = summarize_changes(changes, version, base)
    print("Combining batch summaries into the final release documents.")
    result = gemini_generate(build_prompt(summaries, version, base))
    release = validate_markdown(result.get("release_description"), "release description")
    changelog = validate_markdown(result.get("changelog"), "changelog entry")
    Path(release_output).write_text(release, encoding="utf-8")
    Path(changelog_output).write_text(changelog, encoding="utf-8")
    print(f"Generated separate release description and changelog entry for {version}.")


def update_changelog(path, version, date, url, notes_path):
    file = Path(path)
    text = file.read_text(encoding="utf-8")
    if re.search(rf"^## \[{re.escape(version)}\](?:\s|$)", text, re.M):
        raise RuntimeError(f"CHANGELOG.md already contains [{version}]; refusing to overwrite it.")
    notes = Path(notes_path).read_text(encoding="utf-8").strip()
    if not notes:
        raise RuntimeError("Generated changelog entry is empty.")
    first = re.search(r"^## \[", text, re.M)
    at = first.start() if first else len(text)
    entry = f"## [{version}] - {date}\n[Download APK]({url})\n\n{notes}\n\n"
    file.write_text(text[:at].rstrip() + "\n\n" + entry + text[at:].lstrip("\n"), encoding="utf-8")


def self_test():
    samples = [
        ("### Added\n- A feature", "changelog"),
        ("## What's New\n- A user-facing feature", "release description"),
    ]
    for value, name in samples:
        validate_markdown(value, name)
    sample_changes = [
        {"kind": "direct_commit", "sha": str(index), "message": "A meaningful change", "files": []}
        for index in range(12)
    ]
    original_limit = MAX_BATCH_CONTEXT_CHARS
    try:
        globals()["MAX_BATCH_CONTEXT_CHARS"] = 500
        batches = split_change_batches(sample_changes)
        flattened = [item["sha"] for batch in batches for item in batch]
        if flattened != [str(index) for index in range(12)] or len(batches) < 2:
            raise RuntimeError("Batch splitting self-test failed to preserve all changes.")
    finally:
        globals()["MAX_BATCH_CONTEXT_CHARS"] = original_limit
    print("Release-notes self-test passed (including change batching).")


def main():
    parser = argparse.ArgumentParser()
    sub = parser.add_subparsers(dest="cmd", required=True)

    command = sub.add_parser("select-base")
    command.add_argument("--repo", required=True)
    command.add_argument("--version", required=True)

    command = sub.add_parser("generate")
    command.add_argument("--repo", required=True)
    command.add_argument("--base-tag", required=True)
    command.add_argument("--head-ref", required=True)
    command.add_argument("--version", required=True)
    command.add_argument("--release-output", required=True)
    command.add_argument("--changelog-output", required=True)

    command = sub.add_parser("insert-changelog")
    command.add_argument("--changelog", required=True)
    command.add_argument("--version", required=True)
    command.add_argument("--date", required=True)
    command.add_argument("--download-url", required=True)
    command.add_argument("--notes-file", required=True)

    sub.add_parser("self-test")
    args = parser.parse_args()

    if args.cmd == "select-base":
        print(base_tag(args.repo, args.version))
    elif args.cmd == "generate":
        generate(args.repo, args.base_tag, args.head_ref, args.version,
                 args.release_output, args.changelog_output)
    elif args.cmd == "insert-changelog":
        update_changelog(args.changelog, args.version, args.date,
                         args.download_url, args.notes_file)
    else:
        self_test()


if __name__ == "__main__":
    try:
        main()
    except Exception as error:
        print(f"ERROR: {error}", file=sys.stderr)
        sys.exit(1)
