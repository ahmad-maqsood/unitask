# Verifying UniTask release APKs

Starting with releases produced by the updated release workflow, UniTask publishes a GitHub Artifact Attestation and a corresponding `.intoto.jsonl` bundle alongside the APK.

These records help verify that an APK's SHA-256 digest is linked to a build performed by this repository's GitHub Actions workflow. They do not prove that the application is free of vulnerabilities.

## Verify an APK online

1. Download the APK from the official [UniTask GitHub Releases](https://github.com/ahmad-maqsood/unitask/releases) page.
2. Install the [GitHub CLI](https://cli.github.com/).
3. Run the following command, replacing the path with the downloaded APK:

```bash
gh attestation verify UniTask-VERSION.apk --repo ahmad-maqsood/unitask --signer-workflow ahmad-maqsood/unitask/.github/workflows/release-apk.yml
```

A successful verification means the attestation's signature, artifact digest, repository identity, and workflow identity passed GitHub CLI verification. A failed verification means you should not treat that APK as verified.

## Verify using the downloaded provenance file

Each supported release also includes a file ending in `.intoto.jsonl`. To verify with that local bundle:

```bash
gh attestation verify UniTask-VERSION.apk \
  --repo ahmad-maqsood/unitask \
  --bundle UniTask-VERSION.apk.intoto.jsonl \
  --signer-workflow ahmad-maqsood/unitask/.github/workflows/release-apk.yml
```

## Check the signing key

Every UniTask release is signed with the same key, and the release workflow refuses to publish an APK signed with any other key. You can check this yourself with `apksigner`, which is part of the Android SDK build-tools:

```bash
apksigner verify --print-certs UniTask-VERSION.apk
```

The `certificate SHA-256 digest` line should read:

```
f000bcf3c12e6ed4764b30765f6d58e083f163b1c2ba997f35f6a03e3468b818
```

## Preparing a release (maintainers)

1. Open **Actions**, then **Build release APK**, and choose **Run workflow** on the `main` branch.
2. Enter the version, for example `4.8.0` or `4.8.0-beta1`, and tick **Prepare a draft release**. The workflow sets `versionName` to it and works out the next `versionCode`, so you do not edit `app/build.gradle.kts` by hand.
3. Review the draft release and the `chore: release` pull request, which holds the version bump and the changelog entry. Merge the pull request, then publish the draft.

Merge that pull request before preparing the next release. For a test APK, leave the box unticked. With the version left empty, the numbers already in the file are used.

If a run stops after pushing the `release/changelog-v...` branch but before opening its pull request, delete that branch (and any draft release it made) before running again.

The release tag points at the version-bump commit. The provenance records the commit the run started from, and the workflow's only source change on top of it is that version bump.

## Important notes

- Download APKs and provenance files from the same official GitHub Release.
- Keep the APK and its matching provenance file together; a provenance file for a different APK will not verify.
- GitHub Artifact Attestations establish build provenance and artifact integrity. They are not a malware scan or a guarantee that the application is safe.
- Releases published before this workflow change do not receive fabricated provenance. Their original build evidence may not be available, so they remain unverified by this process.
