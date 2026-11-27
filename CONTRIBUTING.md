# Contributing

UniTask is a personal project, but contributions from the community are welcome!

Whether you want to fix a bug, improve an existing feature, or suggest something new, your contributions are appreciated. All pull requests will be reviewed before being accepted.

## Before contributing

- For major changes or new features, please open an issue first to discuss the idea.
- Keep changes focused and avoid unrelated modifications.
- Make sure your changes do not unintentionally break existing functionality.
- Include a clear description of what you changed and why.

## Submitting a pull request

1. Fork the repository.
2. Create a branch for your changes.
3. Make your changes and test them where applicable.
4. Submit a pull request against the `main` branch.
5. Describe your changes and mention any relevant issues.

Please be patient while your pull request is reviewed. I may request changes or provide feedback before accepting it. Not every submission is guaranteed to be merged.

## Translations

UniTask is available in English and Urdu. Text the person sees (screens, snackbar messages, notifications) belongs in `app/src/main/res/values/strings.xml`, with the Urdu version under the same name in `app/src/main/res/values-ur/strings.xml`. Please do not write user-facing text directly in the Kotlin code.

A unit test (`StringResourcesParityTest`) fails if a string name exists in one language file but not the other, so add both together. Code that checks text, such as unit tests, can keep an English default and let screens pass the translated text in.

## Report a bug

- In the app: **Settings, Report a problem**
- Or [open an issue](https://github.com/ahmad-maqsood/unitask/issues/new/choose) and pick **Bug report**
- Or use the [form](https://docs.google.com/forms/d/e/1FAIpQLSdJpx67ioTDiYOvbntGTh-5AGFb213DFMJNitEl12MYYZSYNw/viewform) (no GitHub account needed)

Please include the app version, your phone model, your Android version, and the steps that cause the problem.

## Suggest a feature or give feedback

- In the app: **Settings, Request a feature**
- Or [open an issue](https://github.com/ahmad-maqsood/unitask/issues/new/choose) and pick **Feedback or feature idea**

## Security problems

Do not use a public issue. See [SECURITY.md](SECURITY.md).
