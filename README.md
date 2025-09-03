# Fenix Installer

A simple Android app to search, download, and install Fenix (Firefox for Android), Focus, and other Mozilla Android project APKs from CI builds.

## Features

-   **Multi-Project Support**: Select from projects:
    -   `try` (default)
    -   `mozilla-central` (displayed as "central")
    -   `mozilla-beta` (displayed as "beta")
    -   `mozilla-release` (displayed as "release")
-   **Search by Revision**: Enter a full revision hash to find associated builds for the selected project.
-   **Automated CI Data Fetching**:
    1.  Queries Treeherder API for push details using the selected project and revision.
    2.  Displays relevant push comment from the revision (often a Bugzilla link).
    3.  Fetches all jobs associated with the push.
    4.  Filters for relevant, signed, non-test build jobs (jobs containing "B" and "s", excluding "t" in their symbols).
    5.  For each job, retrieves its artifacts from Taskcluster.
-   **Job and Artifact Display**:
    -   Lists build jobs with their app icon (e.g., Fenix, Focus), job name, job symbol, and Task ID.
    -   For each job, lists APK artifacts.
    -   Highlights APKs compatible with your device's ABI.
    -   Provides an expandable section for unsupported APKs.
    -   Shows artifact name, ABI (with a warning icon for unsupported ABIs), and expiration date.
-   **Download and Install**:
    -   Download APKs directly within the app with progress indication.
    -   Install downloaded APKs using the system package installer.
-   **Deep Link Integration**:
    -   Open the app and automatically load a specific build using a Treeherder deep link:
        `https://treeherder.mozilla.org/jobs?repo=<PROJECT>&revision=<REVISION>`
        (e.g., `https://treeherder.mozilla.org/jobs?repo=try&revision=c2f3f652a3a063cb7933c2781038a25974cd09ec`)
        The app will select the correct project and load the revision.

## Usage

1.  Open the app.
2.  Select the desired `Project` from the dropdown (e.g., "try", "central").
3.  Enter a full `Revision` hash in the text field.
4.  Tap the "Search" icon.
5.  View the push comment (if any) and the list of jobs found.
6.  For each job, inspect the list of APKs:
    -   Compatible APKs are listed first.
    -   Tap "Download" to fetch an APK. Progress will be shown on the button.
    -   Once downloaded, the button changes to "Install". Tap it to install the APK.
    -   Unsupported APKs can be viewed by expanding their section.
7.  Alternatively, click a Treeherder link (formatted as described in Deep Link Integration) to open the app directly to that build.

## API Endpoints Used

-   **Get Push Details by Revision**:
    `https://treeherder.mozilla.org/api/project/<PROJECT>/push/?revision=<REVISION>`
-   **Get Jobs for Push**:
    `https://treeherder.mozilla.org/api/jobs/?push_id=<PUSH_ID>`
-   **Get Artifacts for Task**:
    `https://firefox-ci-tc.services.mozilla.com/api/queue/v1/task/<TASK_ID>/runs/0/artifacts`
-   **Download Artifact**:
    Uses the full HTTPS URL provided in the artifact details (e.g., `https://firefox-ci-tc.services.mozilla.com/api/queue/v1/task/<TASK_ID>/runs/0/artifacts/public/apk/<ARTIFACT_NAME>.apk`).

## Build

```bash
./gradlew assembleDebug
```

## Architecture

-   **Jetpack Compose** for UI (Material 3 theming).
-   **ViewModel** with Compose state (`StateFlow`, `mutableStateOf`) for UI logic and data holding.
-   **Kotlin Coroutines** for asynchronous operations (fetching data, downloading files).
-   **Retrofit** for type-safe HTTP requests to Treeherder and Taskcluster APIs.
-   **Kotlinx Serialization** for efficient JSON parsing.
-   **OkHttp** as the underlying HTTP client for Retrofit (with a `HttpLoggingInterceptor` for debugging network traffic).
-   **FileProvider** for securely sharing downloaded APKs with the system package installer.

## Requirements

-   Android API 26+ (Android 8.0 Oreo)
-   Internet permission (included in `AndroidManifest.xml`).
