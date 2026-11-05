#!/usr/bin/env python3
"""Build release notes from merged pull requests and direct commits."""
import argparse, json, os, re, sys, urllib.error, urllib.parse, urllib.request
from pathlib import Path

API = "https://api.github.com"
CATS = ("New Features", "Improvements", "Bug Fixes", "Other Changes")
LABELS = {"feature": CATS[0], "improvement": CATS[1], "bug": CATS[2], "maintenance": CATS[3]}
PREFIXES = [
    (r"^(feat|feature|add)(\([^)]+\))?(!)?:", CATS[0]),
    (r"^(fix|bugfix|bug)(\([^)]+\))?(!)?:", CATS[2]),
    (r"^(improve|enhance|perf|performance|refactor|optimize)(\([^)]+\))?(!)?:", CATS[1]),
    (r"^(docs?|chore|build|ci|test|deps?|maintenance|release)(\([^)]+\))?(!)?:", CATS[3]),
]

def api(path):
    token = os.getenv("GH_TOKEN") or os.getenv("GITHUB_TOKEN")
    if not token:
        raise RuntimeError("GH_TOKEN or GITHUB_TOKEN is required.")
    req = urllib.request.Request(API + path, headers={
        "Accept": "application/vnd.github+json", "Authorization": f"Bearer {token}",
        "X-GitHub-Api-Version": "2022-11-28", "User-Agent": "unitask-release-notes"})
    try:
        with urllib.request.urlopen(req, timeout=30) as response:
            return json.loads(response.read().decode("utf-8"))
    except urllib.error.HTTPError as e:
        detail = e.read().decode("utf-8", errors="replace")[:400]
        raise RuntimeError(f"GitHub API error {e.code} for {path}: {detail}") from e

def releases(repo):
    result, page = [], 1
    while True:
        batch = api(f"/repos/{repo}/releases?per_page=100&page={page}")
        result.extend(batch)
        if len(batch) < 100:
            return result
        page += 1

def base_tag(repo, version):
    items = [r for r in releases(repo) if not r.get("draft") and r.get("published_at")]
    if not items:
        raise RuntimeError("No published releases exist; cannot select a previous tag.")
    if "-" in version:
        series = version.split("-", 1)[0]
        beta = [r for r in items if r.get("prerelease") and r.get("tag_name", "").removeprefix("v").startswith(series + "-")]
        if beta:
            return sorted(beta, key=lambda r: r["published_at"], reverse=True)[0]["tag_name"]
    stable = [r for r in items if not r.get("prerelease")] or items
    return sorted(stable, key=lambda r: r["published_at"], reverse=True)[0]["tag_name"]

def category(message):
    subject = message.strip().splitlines()[0] if message.strip() else ""
    for pattern, name in PREFIXES:
        if re.match(pattern, subject, re.I):
            return name
    return CATS[3]

def clean(text):
    text = re.sub(r"<!--.*?-->", "", text, flags=re.S)
    text = re.sub(r"^\s{0,3}#{1,6}\s*", "", text, flags=re.M)
    text = re.sub(r"^\s*[-*+]\s*", "", text, flags=re.M)
    text = re.sub(r"\[([^\]]+)\]\([^)]+\)", r"\1", text)
    text = re.sub(r"<[^>]+>", "", text)
    return re.sub(r"\s+", " ", text).strip()

def description(body, title):
    for line in body.splitlines():
        value = clean(line)
        if not value or value.startswith("---") or value.lower().startswith(title.lower()):
            continue
        return value[:180].rstrip()
    return ""

def pr_category(pr):
    labels = {x.get("name", "").lower() for x in pr.get("labels", [])}
    if "skip-release-notes" in labels:
        return None
    for key in ("bug", "feature", "improvement", "maintenance"):
        if key in labels:
            return LABELS[key]
    return category(pr.get("title", ""))

def compare(repo, base, head):
    commits, page = [], 1
    b, h = urllib.parse.quote(base, safe=""), urllib.parse.quote(head, safe="")
    while True:
        batch = api(f"/repos/{repo}/compare/{b}...{h}?per_page=100&page={page}").get("commits", [])
        commits.extend(batch)
        if len(batch) < 100:
            return commits
        page += 1

def generate(repo, base, head, output):
    commits = compare(repo, base, head)
    if not commits:
        raise RuntimeError(f"No commits between {base} and {head}.")
    sections = {name: [] for name in CATS}
    seen, cache, direct = set(), {}, 0
    for commit in commits:
        sha = commit["sha"]
        if sha not in cache:
            cache[sha] = api(f"/repos/{repo}/commits/{sha}/pulls")
        prs = [p for p in cache[sha] if p.get("merged_at")]
        if prs:
            for pr in prs:
                number = pr["number"]
                if number in seen:
                    continue
                seen.add(number)
                group = pr_category(pr)
                if group is None:
                    continue
                title = clean(pr.get("title") or f"Pull request #{number}")
                url = pr.get("html_url") or f"https://github.com/{repo}/pull/{number}"
                desc = description(pr.get("body") or "", title)
                item = f"[{title}]({url})" + (f" — {desc}" if desc else "")
                sections[group].append((pr.get("merged_at", ""), item))
            continue
        subject = commit.get("commit", {}).get("message", "").splitlines()[0].strip()
        if not subject:
            continue
        group = category(subject)
        subject = re.sub(r"^(feat|feature|add|fix|bugfix|bug|improve|enhance|perf|performance|refactor|optimize|docs?|chore|build|ci|test|deps?|maintenance|release)(\([^)]+\))?(!)?:\s*", "", subject, flags=re.I)
        url = commit.get("html_url") or f"https://github.com/{repo}/commit/{sha}"
        when = commit.get("commit", {}).get("author", {}).get("date", "")
        sections[group].append((when, f"[{subject}]({url})"))
        direct += 1
    rendered, total = [], 0
    for name in CATS:
        items = sorted(sections[name], key=lambda x: x[0], reverse=True)
        if items:
            total += len(items)
            rendered.append(f"### {name}\n" + "\n".join(f"- {item}" for _, item in items))
    if not rendered:
        raise RuntimeError("No release-note entries remain after exclusions.")
    Path(output).write_text("\n\n".join(rendered) + "\n", encoding="utf-8")
    print(f"Generated {total} entries from {len(seen)} merged PR(s) and {direct} direct commit(s).")
    print("Unclear direct commits are kept under Other Changes for review.")

def update_changelog(path, version, date, url, notes_path):
    file = Path(path)
    text = file.read_text(encoding="utf-8")
    if re.search(rf"^## \[{re.escape(version)}\](?:\s|$)", text, re.M):
        raise RuntimeError(f"CHANGELOG.md already contains [{version}]; refusing to overwrite it.")
    notes = Path(notes_path).read_text(encoding="utf-8").strip()
    if not notes:
        raise RuntimeError("Generated release notes are empty.")
    first = re.search(r"^## \[", text, re.M)
    at = first.start() if first else len(text)
    entry = f"## [{version}] - {date}\n[Download APK]({url})\n\n{notes}\n\n"
    file.write_text(text[:at].rstrip() + "\n\n" + entry + text[at:].lstrip("\n"), encoding="utf-8")

def self_test():
    tests = [
        ("feat: add notes", CATS[0]), ("fix(reminder): restore alarm", CATS[2]),
        ("improve: faster sync", CATS[1]), ("chore: update libraries", CATS[3]),
        ("unclear subject", CATS[3])]
    for message, expected in tests:
        if category(message) != expected:
            raise RuntimeError(f"Self-test failed for: {message}")
    print("Release-notes self-test passed.")

def main():
    p = argparse.ArgumentParser()
    s = p.add_subparsers(dest="cmd", required=True)
    x = s.add_parser("select-base"); x.add_argument("--repo", required=True); x.add_argument("--version", required=True)
    x = s.add_parser("generate"); x.add_argument("--repo", required=True); x.add_argument("--base-tag", required=True); x.add_argument("--head-ref", required=True); x.add_argument("--output", required=True)
    x = s.add_parser("insert-changelog"); x.add_argument("--changelog", required=True); x.add_argument("--version", required=True); x.add_argument("--date", required=True); x.add_argument("--download-url", required=True); x.add_argument("--notes-file", required=True)
    s.add_parser("self-test")
    a = p.parse_args()
    if a.cmd == "select-base": print(base_tag(a.repo, a.version))
    elif a.cmd == "generate": generate(a.repo, a.base_tag, a.head_ref, a.output)
    elif a.cmd == "insert-changelog": update_changelog(a.changelog, a.version, a.date, a.download_url, a.notes_file)
    else: self_test()

if __name__ == "__main__":
    try: main()
    except Exception as e:
        print(f"ERROR: {e}", file=sys.stderr)
        sys.exit(1)
