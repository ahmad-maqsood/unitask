#!/usr/bin/env python3
"""Prepare fictional Supabase group data for automated README screenshots."""

from __future__ import annotations

import json
import os
import re
import sys
from datetime import datetime, timedelta, timezone
from urllib.error import HTTPError, URLError
from urllib.parse import quote
from urllib.request import Request, urlopen
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
GROUP_USERNAME = "unitask-screenshot-demo"
GROUP_NAME = "UniTask Screenshot Demo"


def project_config():
    source = (ROOT / "app/build.gradle.kts").read_text(encoding="utf-8")
    values = {}
    for key in ("SUPABASE_URL", "SUPABASE_ANON_KEY"):
        match = re.search(
            r'buildConfigField\("String",\s*"' + key + r'",\s*"\\"([^"]+)\\""',
            source,
        )
        if not match:
            raise RuntimeError(f"Could not read {key} from app/build.gradle.kts")
        values[key] = match.group(1)
    return values["SUPABASE_URL"].rstrip("/"), values["SUPABASE_ANON_KEY"]


def request(base, anon, method, path, token=None, payload=None):
    headers = {"apikey": anon, "Content-Type": "application/json", "Accept": "application/json"}
    if token:
        headers["Authorization"] = f"Bearer {token}"
    if method in ("POST", "PATCH", "DELETE"):
        headers["Prefer"] = "return=representation"
    data = None if payload is None else json.dumps(payload).encode("utf-8")
    req = Request(base + path, data=data, headers=headers, method=method)
    try:
        with urlopen(req, timeout=30) as response:
            raw = response.read()
            return json.loads(raw.decode("utf-8")) if raw else None
    except HTTPError as exc:
        detail = exc.read().decode("utf-8", errors="replace")
        raise RuntimeError(f"Supabase request failed ({exc.code}): {detail[:500]}") from None
    except URLError as exc:
        raise RuntimeError(f"Could not reach Supabase: {exc.reason}") from None


def sign_in(base, anon, email, password):
    result = request(
        base, anon, "POST", "/auth/v1/token?grant_type=password",
        payload={"email": email, "password": password},
    )
    if not result or not result.get("access_token") or not result.get("user", {}).get("id"):
        raise RuntimeError("Supabase sign-in did not return a valid demo session.")
    return result["access_token"], result["user"]["id"]


def main():
    names = (
        "SCREENSHOT_DEMO_OWNER_EMAIL",
        "SCREENSHOT_DEMO_OWNER_PASSWORD",
        "SCREENSHOT_DEMO_MEMBER_EMAIL",
        "SCREENSHOT_DEMO_MEMBER_PASSWORD",
    )
    missing = [name for name in names if not os.environ.get(name)]
    if missing:
        raise RuntimeError("Missing GitHub Actions secrets: " + ", ".join(missing))

    base, anon = project_config()
    owner_token, owner_id = sign_in(
        base, anon, os.environ[names[0]], os.environ[names[1]]
    )
    member_token, member_id = sign_in(
        base, anon, os.environ[names[2]], os.environ[names[3]]
    )

    # Look up only the dedicated demo username. Never touch any other group.
    old_groups = request(
        base, anon, "GET",
        "/rest/v1/groups?select=id&username=eq." + quote(GROUP_USERNAME, safe=""),
        owner_token,
    ) or []
    for old in old_groups:
        gid = quote(old["id"], safe="")
        request(base, anon, "DELETE", f"/rest/v1/group_messages?group_id=eq.{gid}", owner_token)
        request(
            base, anon, "DELETE",
            f"/rest/v1/group_members?group_id=eq.{gid}&role=neq.owner",
            owner_token,
        )
        request(base, anon, "DELETE", f"/rest/v1/groups?id=eq.{gid}", owner_token)

    created = request(
        base, anon, "POST", "/rest/v1/rpc/create_group", owner_token,
        {
            "p_username": GROUP_USERNAME,
            "p_name": GROUP_NAME,
            "p_display_name": "Alex Morgan",
        },
    )
    row = created[0] if isinstance(created, list) else created
    if not row or not row.get("group_id") or not row.get("join_code"):
        raise RuntimeError("create_group() did not return a group ID and join code.")
    group_id = row["group_id"]

    request(
        base, anon, "POST", "/rest/v1/rpc/join_group", member_token,
        {
            "p_username": GROUP_USERNAME,
            "p_join_code": row["join_code"],
            "p_display_name": "Jamie Chen",
        },
    )

    messages = [
        ("owner", "Hey everyone! Welcome to our study group."),
        ("member", "Thanks! I added our study plan for this week."),
        ("owner", "The Data Structures assignment is due on Friday."),
        ("member", "I'll finish the linked-list exercises tonight."),
        ("owner", "Can someone share the lecture notes after class?"),
        ("member", "Of course — I have a summary ready to share."),
        ("owner", "Let's review the database questions together tomorrow."),
        ("member", "Sounds good. I'll bring the practice questions."),
        ("owner", "Please add any topics you want to cover."),
        ("member", "SQL joins and normalisation would be helpful!"),
        ("owner", "Great, those are on the agenda."),
        ("member", "See you in the library!"),
    ]
    now = datetime.now(timezone.utc)
    for index, (sender, message) in enumerate(messages):
        owner_message = sender == "owner"
        request(
            base, anon, "POST", "/rest/v1/group_messages",
            owner_token if owner_message else member_token,
            {
                "group_id": group_id,
                "sender_id": owner_id if owner_message else member_id,
                "sender_name": "Alex Morgan" if owner_message else "Jamie Chen",
                "text": message,
                "created_at": (
                    now - timedelta(minutes=(len(messages) - index) * 3)
                ).isoformat(),
            },
        )

    output = os.environ.get("GITHUB_OUTPUT")
    if output:
        with open(output, "a", encoding="utf-8") as stream:
            stream.write(f"group_id={group_id}\n")
    print("Prepared the fictional UniTask Screenshot Demo group and chat messages.")


if __name__ == "__main__":
    try:
        main()
    except Exception as error:
        print(f"::error::{error}", file=sys.stderr)
        raise
