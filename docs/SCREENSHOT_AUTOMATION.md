# Automated README screenshots

UniTask can refresh its README screenshots from the real Android app. The workflow is manual, uses a debug build and an Android emulator, and opens a pull request with the updated images and README. It does not publish an APK or modify `main`.

## One-time Supabase setup

The Group Chat screenshot uses the real group-chat screen and Supabase's normal security rules. Create two dedicated demo users in **Supabase Dashboard → Authentication → Users**:

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

## Demo data and cleanup

The emulator uses fictional courses, deadlines, timetable entries, notes and tasks, plus a few deleted items so the **Recently deleted** screen has something to show. The **Global search** screenshot searches for "design". Two extra screenshots show the dark theme and the Rose accent colour; the test switches the theme in code and switches back to the default afterwards. The group chat uses fictional messages from the two demo accounts, shown with the Pakistani display names Hamza Qureshi and Zainab Fatima (set at the top of `.github/scripts/seed_screenshot_demo.py`).

The workflow reserves the group username `unitask-screenshot-demo`. Before creating a fresh group, it removes the previous screenshot group's messages and member, then deletes that group. It does not touch other groups. The latest demo group and messages remain in Supabase after a run, until the next screenshot run replaces them or you delete the group from UniTask while signed in as its owner.

If the workflow fails during setup, check that both demo users exist, their email addresses are confirmed, and the four GitHub secrets are spelled exactly as shown above.

If the workflow captures the images but cannot open the generated pull request, check **GitHub → Settings → Actions → General → Workflow permissions** and enable **Allow GitHub Actions to create and approve pull requests**. The workflow needs this permission only to open the screenshot PR.
