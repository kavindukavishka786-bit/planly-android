# PLANLY Android

A native Kotlin / Jetpack Compose task planner with a warm cream, teal and pastel interface inspired by the supplied wellness UI reference. Light mode uses dark text and explicit content colors; dark mode is optional, with a System choice. Glass styling uses restrained gradients, borders and depth rather than a costly refraction shader.

Version 0.2.0 adds a lightweight vector garden welcome card, pastel week tiles, Focus and AI quick-access cards, task search, compact navigation, animated completion colors, list placement, daily progress and eased page/detail transitions. Existing offline tasks and settings retain their storage format. Text contrast across the pastel surfaces is at least 5.22:1 in light mode and 5.61:1 in dark mode.

Commits containing `[ui-preview]` also capture real Android emulator screenshots in the **PLANLY-UI-preview** workflow artifact. The screenshots use a fresh install without personal content. You can request them again using **Run workflow**.

## First version

- Today dashboard with progress and one-tap task completion.
- Calendar week picker and task timeline.
- Create, edit and delete tasks; projects, priorities, notes and time estimates.
- Persistent offline task storage. The first launch starts empty, without invented personal data.
- Task reminders, Mark done and Snooze notification actions, quiet hours, and restart rescheduling.
- Optional Android precise-reminder permission, with an inexact fallback. Notifications require the user's permission.
- Focus timer with saved state and an absolute end time that survives rotation and backgrounding. Focus completion notices are in-app; there is no focus background notification in this version.
- User-selectable Light / Dark / System theme, display name and glass highlights.
- Gemini Flash-Lite task breakdown through an authenticated HTTPS service, with review before adding tasks.

## Build an APK using GitHub, including from a phone

1. Create a repository named `planly-android` in your chosen GitHub account. Initialize it with a README so it has a `main` branch.
2. Add the project contents to the repository root. `.github/workflows/android.yml` must remain inside `.github/workflows`.
3. In GitHub, open **Actions → Build PLANLY APK**. A push to `main` starts a build; you can also use **Run workflow**.
4. The workflow runs backend tests, Android unit tests and lint, then assembles a development APK.
5. When it succeeds, open the workflow run and download **PLANLY-debug-APK** under **Artifacts**. Extract its ZIP and install `app-debug.apk` on an Android 8+ phone.

This APK uses a development signing key. A locally built APK and the first GitHub-built APK can use different keys and require reinstalling to switch between them. Reinstalling deletes offline tasks. The workflow caches its key for convenient subsequent updates; cache eviction can change it. Use a dedicated private release signing key before distributing a production app. No Gemini API key or access token is needed to build the APK.

The project intentionally uses the Gradle installed by `gradle/actions/setup-gradle`, pinned to 8.11.1. With an Android SDK, JDK 17 and Gradle 8.11.1 installed locally:

```sh
gradle testDebugUnitTest lintDebug assembleDebug
```

## AI service setup

The Android app never contains the Gemini API key. `backend/` is a small Node 22.9+ service; no npm dependencies are required. It accepts either private `.env` configuration or environment variables from your host.

1. Copy `backend/.env.example` to `backend/.env` privately.
2. Set `GEMINI_API_KEY` on the server, and set `APP_ACCESS_TOKEN` to a strong random value of at least 32 characters. Do not commit `.env` or share the Gemini key in chat.
3. The default model is `gemini-3.5-flash-lite`; `GEMINI_MODEL` can select another supported Flash-Lite model.
4. Run `npm test`, then `npm start` inside `backend/`.
5. Deploy the service behind HTTPS on your chosen Node host. The app rejects plain HTTP and redirects when sending credentials.
6. In **PLANLY → Settings**, turn on AI assistance, enter the HTTPS server origin and its access token, and save.

### Fix an AI setup error

If the server access token is shorter than 32 characters, the server refuses AI requests. Use a random 32 to 512 character token containing only printable ASCII without spaces. Set the exact same token in `APP_ACCESS_TOKEN` on the server and in PLANLY Settings. The app checks these requirements before saving or making an AI request.

On Render, select **Environment → Edit**, update the private values, then **Save, rebuild, and deploy**. An environment change does not affect an already-running process until deployment. Keep `GEMINI_API_KEY` private on the server. If a screenshot or public file exposes a key or token, replace it privately in the provider console and deployment settings.

Open `/health` to check the service: `status: "ok"` means it is running, and `aiReady: true` means its required environment values satisfy validation. This does not verify the Gemini key, quota, or provider response; create a plan in the app to test those. Server startup logs identify missing or invalid settings without printing their values. A Render free service can take about a minute to wake; retry after it is running.

The access token is encrypted with an Android Keystore key. AI sends only the entered goal, never the offline task database. Provider request storage is disabled with `store: false`; normal provider terms and processing still apply. The response must satisfy a bounded schema and server-side validation. AI cannot send messages, execute code or modify tasks without the user's approval.

The included service is a personal first-version backend with a private access token, in-memory rate limits and a daily request ceiling. Before opening AI access to many users, replace shared-token access with individual authenticated sessions, durable quotas and deployment-specific access control. Provider usage and hosting may have costs; neither is configured or deployed by the source archive.

## Reminder behavior

Turn on task notifications in Settings, grant Android's permission, and schedule a future task with a reminder. Standard reminders can be delayed by Android battery management. The separate precise-reminder option opens the system's Alarms & reminders screen. Quiet hours defer delivery to their end; equal start and end disables the quiet interval. Device force-stop prevents alarms until the app is opened again. Overdue tasks do not replay stale alarms on startup.

## Validation and current limits

- `backend/app.test.mjs` verifies authentication, invalid inputs, provider payload privacy, malformed outputs, quotas and error handling using mocked Gemini responses.
- `TaskRulesTest.kt` verifies title/duration validation and quiet-hour behavior across midnight.
- Local verification on this version passed Android compilation, all seven Android unit tests, lint with no errors, and all eight backend checks. A development APK was assembled. These checks do not replace phone testing.
- The GitHub workflow provides the full Android compile, unit-test, lint and APK checks.
- No production backend, account sync, shared projects, repeating tasks or published release is included yet.
- Device reminder delivery, notification permission flows and actual Gemini responses still need end-to-end testing on your phone and configured service.

## Official references

- [Android Gradle plugin 8.9 / Gradle 8.11.1 / JDK 17 compatibility](https://developer.android.com/build/releases/agp-8-9-0-release-notes)
- [Compose Compiler Gradle plugin](https://developer.android.com/develop/ui/compose/setup-compose-dependencies-and-compiler)
- [Android alarm scheduling](https://developer.android.com/develop/background-work/services/alarms)
- [Gemini 3.5 Flash-Lite](https://ai.google.dev/gemini-api/docs/models/gemini-3.5-flash-lite)
- [Gemini Interactions API](https://ai.google.dev/api/interactions-api)
