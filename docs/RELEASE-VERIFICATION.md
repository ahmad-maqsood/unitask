# Verifying UniTask release APKs

Starting with releases produced by the updated release workflow, UniTask publishes a GitHub Artifact Attestation and a corresponding `.intoto.jsonl` bundle alongside the APK.

These records help verify that an APK's SHA-256 digest is linked to a build performed by this repository's GitHub Actions workflow. They do not prove that the application is free of vulnerabilities.

## Verify an APK online

1. Download the APK from the official [UniTask GitHub Releases](https://github.com/CondaLabs/unitask/releases) page.
2. Install the [GitHub CLI](https://cli.github.com/).
3. Run the following command, replacing the path with the downloaded APK:

```bash
gh attestation verify UniTask-VERSION.apk --repo CondaLabs/unitask --signer-workflow CondaLabs/unitask/.github/workflows/release-apk.yml
```

A successful verification means the attestation's signature, artifact digest, repository identity, and workflow identity passed GitHub CLI verification. A failed verification means you should not treat that APK as verified.

## Verify using the downloaded provenance file

Each supported release also includes a file ending in `.intoto.jsonl`. To verify with that local bundle:

```bash
gh attestation verify UniTask-VERSION.apk \
  --repo CondaLabs/unitask \
  --bundle UniTask-VERSION.apk.intoto.jsonl \
  --signer-workflow CondaLabs/unitask/.github/workflows/release-apk.yml
```

## Important notes

- Download APKs and provenance files from the same official GitHub Release.
- Keep the APK and its matching provenance file together; a provenance file for a different APK will not verify.
- GitHub Artifact Attestations establish build provenance and artifact integrity. They are not a malware scan or a guarantee that the application is safe.
- Releases published before this workflow change do not receive fabricated provenance. Their original build evidence may not be available, so they remain unverified by this process.
