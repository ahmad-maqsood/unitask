# Automated README screenshots

UniTask can refresh its README screenshots from the real Android app. The workflow is manual, uses a debug build and an Android emulator, and opens a pull request with the updated images and README. It does not publish an APK or modify `main`.

## One-time Supabase setup

The Study groups screenshot uses the real Groups screen and Supabase's normal security rules. Create two dedicated demo users in **Supabase Dashboard → Authentication → Users**:

1. Create a user for the group owner, for example `unitask-screenshot-owner@example.com`.
2. Create a second user for the group member, for example `unitask-screenshot-member@example.com`.
3. Set a strong, unique password for each account and make sure both users are confirmed.
4. Keep these accounts dedicated to screenshots. The workflow signs in as both users and creates a fictional group called **UniTask Screenshot Demo**.

Add these four repository secrets under **GitHub → Settings → Secrets and variables → Actions → New repository secret**:

| Secret name | Value |
| --- | --- |
| `SCREENSHOT_DEMO_OWNER_EMAIL` | Owner demo account email |
| `SCREENSHOT_DEMO_OWNER_PASSWORD` | Owner demo account password |
| `SCREENSHOT_DEMO_MEMBER_EMAIL` | Member demo account email |
| `SCREENSHOT_DEMO_MEMBER_PASSWORD` | Member demo account password |

The workflow reads the project's existing Supabase URL and public anon key from the app build configuration. **Do not add a service-role key** and never put passwords in repository files.

## Run it

After the automation pull request has been merged:

1. Open the repository's **Actions** tab.
2. Select **Refresh README screenshots**.
3. Choose **Run workflow** and confirm.
4. Wait for the emulator capture to finish. The workflow creates a pull request containing the refreshed screenshots and README gallery.
5. Review the screenshots and PR, then merge it when you're happy.

This workflow is separate from APK releases. It only runs when you manually start it.

## What is captured

There are 18 required screenshots and 6 optional extras (the new-assignment screen, course details, the More menu, Pomodoro mode and its options, and Progress). The extras are wrapped in `optionalShot(...)` in `ReadmeScreenshotTest.kt`: if one of those steps fails on the emulator, the failure is logged and the run carries on, and the gallery simply leaves that screen out. A missing required screenshot still fails the run.

The README gallery is grouped into sections (Plan your work, Study tools, Notes, Groups search and trash, Settings, Themes). To add a screenshot, change three places so they agree:

1. `ReadmeScreenshotTest.kt`: add a `capture("name")` call (inside `optionalShot` if it is an extra).
2. `capture_readme_screenshots.sh`: add the name to `expected` (required) or `optional`.
3. `build_screenshots_gallery.py`: add it to a section in `SECTIONS`, and to `OPTIONAL_SCREENS` if it is an extra.

To change only the gallery layout without running the emulator, run `python3 .github/scripts/build_screenshots_gallery.py --readme-only`. It rebuilds the README section from the images already in `docs/screenshots/`.

## Demo data and cleanup

The emulator uses fictional courses, deadlines, timetable entries, notes and tasks, plus a few deleted items so the **Recently deleted** screen has something to show. The **Global search** screenshot searches for "design". Two extra screenshots show the dark theme and the Rose accent colour; the test switches the theme in code and switches back to the default afterwards. The demo group uses the two demo accounts, shown with the Pakistani display names Hamza Qureshi and Zainab Fatima (set at the top of `.github/scripts/seed_screenshot_demo.py`).

The workflow reserves the group username `unitask-screenshot-demo`. Before creating a fresh group, it removes the previous screenshot group's members, then deletes that group. It does not touch other groups. The latest demo group remains in Supabase after a run, until the next screenshot run replaces them or you delete the group from UniTask while signed in as its owner.

If the workflow fails during setup, check that both demo users exist, their email addresses are confirmed, and the four GitHub secrets are spelled exactly as shown above.

## If the emulator fails

The capture runs on a GitHub-hosted Android emulator, which can run out of memory or fail to start. The workflow already protects against this:

- Before the emulator starts, it stops the Gradle and Kotlin build daemons and frees memory, because they keep gigabytes of RAM after the build and can make the emulator die mid-run.
- The emulator runs with 3072 MB of RAM and a 5-minute boot timeout, so a dead emulator fails quickly instead of looping.
- Each screenshot is copied out with a timeout and up to 3 retries.
- If the capture fails, the log ends with a **diagnostics** block showing memory use, whether the emulator process is still running and any out-of-memory kills. Check it first to tell a dead emulator from a real test failure.

Do not pin `emulator-build` in `.github/workflows/readme-screenshots.yml`. Old builds (for example 33.1.24) never connect to adb on the runner, so the boot check loops with "device not found" until the timeout. Leave the setting out so the workflow uses the current emulator.

If the capture still fails, download the **emulator-crash-data** artifact from the failed run and check the diagnostics block in the step log.

If the workflow captures the images but cannot open the generated pull request, check **GitHub → Settings → Actions → General → Workflow permissions** and enable **Allow GitHub Actions to create and approve pull requests**. The workflow needs this permission only to open the screenshot PR.
