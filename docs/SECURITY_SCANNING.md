# Mobile security scanning

UniTask uses two complementary automated tools. They provide useful security checks, but they are not a substitute for a professional penetration test or a guarantee that the app is secure.

## 1. Source-code scanning with mobsfscan

The GitHub Actions workflow at `.github/workflows/mobsfscan.yml` runs [mobsfscan](https://github.com/MobSF/mobsfscan) against the Android source code when relevant files change on a pull request or on `main`. It can also be started manually from **Actions → Mobile security scan (mobsfscan) → Run workflow**.

The workflow uploads its SARIF report to the repository's **Security → Code scanning** area. Findings are informational initially; review each result before deciding whether it is a real issue, a false positive or an accepted risk.

## 2. APK static analysis with MobSF

The [MobSF project](https://github.com/MobSF/Mobile-Security-Framework-MobSF) provides a static analyser for Android APK files. Its maintainers also provide an online static-analysis instance at [mobsf.live](https://www.mobsf.live/).

1. Download the APK from the [latest UniTask release](https://github.com/CondaLabs/unitask/releases/latest).
2. Open [mobsf.live](https://www.mobsf.live/) and follow its current upload and scan instructions.
3. Upload the APK and start its static analysis.
4. Review the report, paying attention to high-risk findings, exported components, permissions, network security configuration, certificate settings and embedded secrets.
5. Save the report and the APK version / SHA-256 hash so the result can be tied to the exact build that was analysed.

**Privacy:** Uploading an APK sends that binary to a third-party service. Only submit a release APK that you are comfortable sharing with the service. Do not upload builds containing private test data, unreleased secrets or confidential material. Check the service's current terms and retention policy before uploading.

## 3. How to interpret results

- A scanner finding is a lead to investigate, not automatically a confirmed vulnerability.
- Check the affected file, line, manifest entry or configuration and understand the context.
- Record confirmed issues, false positives and accepted risks; fix confirmed issues and scan again.
- Keep the report with the exact app version and APK hash.
- Do not describe UniTask as independently audited or certified based only on automated scans.

## Current review status

The repository's automated source-code scanning workflow is being introduced. APK analysis should be performed for a release and its report reviewed before claiming that the release has passed MobSF analysis. No independent human security audit is claimed.
