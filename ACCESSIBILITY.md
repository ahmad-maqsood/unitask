# Accessibility

UniTask aims to be practical and comfortable to use for as many students as possible. It is an Android application built with Kotlin, Jetpack Compose and Material 3.

Accessibility can vary by screen, Android version, device manufacturer and the accessibility settings in use. This document describes the project's current position; it is not a claim of full accessibility or formal conformance with WCAG or another accessibility standard.

## Supported environment

UniTask requires Android 7.0 (API 24) or newer. Android provides system accessibility features and services such as TalkBack, magnification, display and font size adjustments, and Switch Access. Their availability is determined by the device and Android version.

The presence of these system features does not mean every UniTask screen or interaction has been verified with them. Support may vary across the app.

## Testing and known limitations

- The project has not undergone a formal accessibility audit.
- Not all screens and user flows have been systematically tested with TalkBack, Switch Access, magnification or increased font/display sizes.
- There is currently no complete, verified list of accessibility limitations. Accessibility barriers that have not yet been reported or identified may still exist.

These points may change as the app is tested and improved. If you encounter a barrier, please report it so it can be investigated.

## Reporting an accessibility issue

You can report an accessibility problem in either of these ways:

- **In the app:** Open **Settings → Report a problem**.
- **On GitHub:** [Open an issue](https://github.com/CondaLabs/unitask/issues/new/choose).

If possible, include:

- Your device model and Android version.
- The UniTask version.
- The screen and action where the problem occurs.
- The accessibility service or setting you were using (for example, TalkBack or increased font size).
- What you expected to happen and what actually happened.
- Steps to reproduce the issue, or a screenshot or screen recording if it helps explain the problem.

Please avoid including passwords, private messages or other personal information in a report.

## Improving accessibility

Accessibility feedback and practical suggestions are welcome. The project is maintained independently, so fixes and responses may take time. See [CONTRIBUTING.md](CONTRIBUTING.md) if you would like to contribute.
