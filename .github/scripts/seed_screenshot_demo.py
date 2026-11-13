#!/usr/bin/env python3
"""Prepare fictional Supabase group data for automated README screenshots."""

from __future__ import annotations

import json
import os
import re
import sys
import time
from urllib.error import HTTPError, URLError
from urllib.parse import quote, urlencode
from urllib.request import Request, urlopen
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
GROUP_USERNAME = "unitask-screenshot-demo"
GROUP_NAME = "UniTask Screenshot Demo"
# Fictional display names shown in the study group screenshot.
OWNER_DISPLAY_NAME = "Hamza Qureshi"
MEMBER_DISPLAY_NAME = "Zainab Fatima"


def project_config():
    """Read Supabase settings from env vars, falling back to the git-ignored .env file."""
    values = {}
    env_file = ROOT / ".env"
    if env_file.is_file():
        for line in env_file.read_text(encoding="utf-8").splitlines():
            line = line.strip()
            if not line or line.startswith("#") or "=" not in line:
                continue
            name, _, value = line.partition("=")
            values[name.strip()] = value.strip().strip('"').strip("'")
    for key in ("SUPABASE_URL", "SUPABASE_ANON_KEY"):
        value = os.environ.get(key) or values.get(key)
        if not value:
            raise RuntimeError(
                f"Could not read {key}: set it as an env var or in {env_file}"
            )
        values[key] = value
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



def verify_membership(base, anon, token, user_id, group_id, expected_role, account_label):
    # Match the app's membership query, including its embedded group details.
    query = urlencode({
        "select": "group_id,role,joined_at,groups(username,name)",
        "user_id": f"eq.{user_id}",
    })
    last_reason = "no matching membership row was returned"
    for attempt in range(5):
        rows = request(
            base, anon, "GET", f"/rest/v1/group_members?{query}", token
        ) or []
        membership = next(
            (item for item in rows if item.get("group_id") == group_id), None
        )
        if membership:
            if membership.get("role") != expected_role:
                last_reason = (
                    f"expected role {expected_role!r}, got {membership.get('role')!r}"
                )
            else:
                group = membership.get("groups")
                if isinstance(group, list):
                    group = group[0] if group else None
                if not isinstance(group, dict):
                    last_reason = "the related group details were not readable"
                elif (
                    group.get("username") != GROUP_USERNAME
                    or group.get("name") != GROUP_NAME
                ):
                    last_reason = "the related group details did not match the demo group"
                else:
                    print(
                        f"Verified {account_label} can read the demo group membership "
                        f"({expected_role}) using the app's membership query."
                    )
                    return
        if attempt < 4:
            time.sleep(2)

    raise RuntimeError(
        f"Demo group preflight failed for the {account_label} account: {last_reason}. "
        "The screenshot emulator was not started. Check the create_group/join_group "
        "results and Supabase group_members/groups SELECT policies."
    )

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
            "p_display_name": OWNER_DISPLAY_NAME,
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
            "p_display_name": MEMBER_DISPLAY_NAME,
        },
    )

    # Fail before spending time on an emulator if either account cannot read
    # the membership rows in the same shape the Android app requests.
    verify_membership(
        base, anon, owner_token, owner_id, group_id, "owner", "owner"
    )
    verify_membership(
        base, anon, member_token, member_id, group_id, "member", "member"
    )

    output = os.environ.get("GITHUB_OUTPUT")
    if output:
        with open(output, "a", encoding="utf-8") as stream:
            stream.write(f"group_id={group_id}\n")
    print("Prepared and verified the fictional UniTask Screenshot Demo group.")


if __name__ == "__main__":
    try:
        main()
    except Exception as error:
        print(f"::error::{error}", file=sys.stderr)
        raise
