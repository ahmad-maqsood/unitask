# Security Policy

## Reporting a vulnerability

Please do not open a public issue for a security problem.

Use GitHub's private reporting instead:

1. Open the [Security tab](https://github.com/CondaLabs/unitask/security) of this repository.
2. Click **Report a vulnerability**.
3. Describe what you found and how to reproduce it.

Only the maintainer can see the report.

## What counts

- Reading or changing another user's tasks or groups
- Bypassing Row Level Security or the server functions in `supabase/schema.sql`
- Leaking personal data, or account deletion and export not working as described
- Any secret (keystore, service key) exposed in the repository

The Supabase anon key in the app is public by design and carries no extra access, so finding it is not a vulnerability.

## What to expect

UniTask is a student project maintained by one person. I will try to reply within 7 days and fix confirmed issues in the next release. I cannot promise a fixed timeline or a bounty.

## Supported versions

Only the latest release receives fixes.
