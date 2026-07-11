# WorkManager APK Download Plan

## Goal

Move APK downloads out of `viewModelScope` and into a `WorkManager`-backed pipeline so downloads can continue when the app is backgrounded or the process is recreated, while keeping TryFox's existing internal cache model.

## Key Decisions

- Keep the APK cache in internal `filesDir/download-cache`.
- Use `WorkManager` rather than `DownloadManager`.
- Reuse the existing `DownloadFileRepository` for transfer logic.
- Persist download state so the UI can reconnect after process death.
- Treat installation as an explicit user action after download completion.

## Phase 1: Add Download Domain Owned By WorkManager

Create a dedicated download layer instead of pushing worker logic into the existing view models.

Files to add:

- `app/src/main/java/org/mozilla/tryfox/download/ApkDownloadWorker.kt`
- `app/src/main/java/org/mozilla/tryfox/download/ApkDownloadCoordinator.kt`
- `app/src/main/java/org/mozilla/tryfox/download/ApkDownloadStore.kt`
- `app/src/main/java/org/mozilla/tryfox/download/DefaultApkDownloadCoordinator.kt`
- `app/src/main/java/org/mozilla/tryfox/download/model/PersistedDownloadState.kt`

Responsibilities:

- `ApkDownloadWorker`: execute one APK download, run as foreground work, report progress.
- `ApkDownloadCoordinator`: app-facing API for `enqueue`, `cancel`, `observe`, `retry`.
- `ApkDownloadStore`: persistent state for reconnecting UI after process death.
- `PersistedDownloadState`: queued, running, succeeded, failed, canceled plus progress metadata.

Use the existing `DownloadFileRepository` instead of replacing it.

## Phase 2: Wire Persistence For Reconnectable State

Add a persistent store before moving UI off `viewModelScope`.

Preferred approach:

- Add a small SQLite table beside the existing history DB.

Reasoning:

- The repo already uses SQLite in `DefaultHistoryRepository`.
- This keeps query semantics straightforward.
- It avoids pushing restart-critical state into an in-memory-only model.

Suggested fields:

- `unique_key`
- `download_url`
- `cache_relative_path`
- `app_name`
- `file_name`
- `status`
- `bytes_downloaded`
- `total_bytes`
- `error_message`
- `work_id`
- `created_at`
- `updated_at`

This store should be the source of truth for logical download state, while file existence remains the source of truth for whether an APK is actually downloaded.

## Phase 3: Add WorkManager And DI Integration

Update build and app wiring.

Files to change:

- `app/build.gradle.kts`
- `app/src/main/java/org/mozilla/tryfox/TryFoxApplication.kt`
- `app/src/main/java/org/mozilla/tryfox/di/AppModule.kt`

Changes:

- Add `androidx.work:work-runtime-ktx`.
- Register `WorkManager` access in DI.
- Register the new coordinator and store.
- Add a custom `WorkerFactory` if needed so the worker can receive `DownloadFileRepository`, `CacheManager`, and the store via DI.

At this stage, do not touch the UI yet. Only make it possible to enqueue a worker end-to-end.

## Phase 4: Implement Foreground Download Worker

`ApkDownloadWorker` should:

- Resolve the output file from `cacheRelativePath` and the current cache root.
- Call `setForeground()` immediately.
- Reuse `DefaultDownloadFileRepository`.
- Update both `setProgress(...)` and `ApkDownloadStore` during transfer.
- Mark the final state in the store on success, failure, or cancellation.
- Call `cacheManager.checkCacheStatus()` on completion.
- Post a completion notification when the download succeeds.

Important detail:

- Do not attempt background auto-install.
- Completion should produce a notification or UI state that lets the user install explicitly.

## Phase 5: Move One Screen First, Then The Other

Start with history. It already has more explicit lifecycle and cancellation semantics than home.

### History flow

Refactor `HistoryViewModel` to replace:

- direct `viewModelScope.launch(ioDispatcher)` download execution
- `activeDownloads`
- `canceledDownloads`
- most in-memory progress bookkeeping

With:

- coordinator `enqueueDownload(entry)`
- coordinator `cancelDownload(uniqueKey)`
- derived UI state from:
  - history entries
  - persisted download states
  - file existence

Keep:

- delete and history cleanup logic
- cache resolution logic around `cacheRelativePath`

### Home flow

Refactor `HomeViewModel` to replace:

- `downloadNightlyApk()` direct repository call
- in-memory `InProgress` updates

With:

- coordinator start
- observed progress mapped by `apkInfo.uniqueKey`

### Install flow

Keep `IntentManager` unchanged.

- Installation should happen when the user taps a downloaded item.
- The completion notification can also deep link back into the app for install.

## Notification Work

Add a small notification helper.

Files to add:

- `app/src/main/java/org/mozilla/tryfox/download/DownloadNotificationFactory.kt`

Files to update:

- `app/src/main/AndroidManifest.xml`
- `app/src/main/res/values/strings.xml`

Needed behavior:

- progress notification while worker is active
- completion notification with pending intent back into app
- optional failure notification with retry action

## State Model Changes

Relevant files:

- `app/src/main/java/org/mozilla/tryfox/data/DownloadState.kt`
- `app/src/main/java/org/mozilla/tryfox/ui/models/HistoryItemUiModel.kt`
- `app/src/main/java/org/mozilla/tryfox/ui/models/ApkUiModel.kt`

Approach:

- Keep `DownloadState` as the UI contract.
- Add a mapper from persisted worker or store state to `DownloadState`.
- Let file existence win when a file is actually present.

This avoids unnecessary UI model churn.

## Testing Plan

Unit tests to add:

- worker success, failure, and cancellation behavior
- coordinator deduplication and cancellation
- state mapping from persisted store to `DownloadState`

Existing tests to extend:

- `app/src/test/java/org/mozilla/tryfox/data/repositories/DefaultDownloadFileRepositoryTest.kt`
- `app/src/test/java/org/mozilla/tryfox/ui/screens/HistoryViewModelTest.kt`
- `app/src/test/java/org/mozilla/tryfox/ui/screens/HomeViewModelTest.kt`

New tests to add:

- `app/src/test/java/org/mozilla/tryfox/download/ApkDownloadWorkerTest.kt`
- `app/src/test/java/org/mozilla/tryfox/download/DefaultApkDownloadCoordinatorTest.kt`

Instrumentation tests later:

- start download, background app, resume app, state reconnects
- completion notification leads to install path

## Recommended Implementation Order

1. Add WorkManager dependency and DI scaffolding.
2. Add persistent download store.
3. Implement `ApkDownloadWorker`.
4. Implement coordinator and unique work policies.
5. Move `HistoryViewModel`.
6. Add foreground and completion notifications.
7. Move `HomeViewModel`.
8. Remove obsolete in-memory download lifecycle code.

## Policies To Lock Before Coding

- Unique work name: use the current `uniqueKey`.
- Cache root: keep internal `filesDir/download-cache`.
- Success UX: downloaded and ready to install, not auto-install in background.
- Retry policy: manual retry first, automatic retry only for clearly transient network failures.
- Cancellation: cancel worker and delete `.part` and managed temp files, but never delete a completed APK.

## Main Risks

- Duplicate state sources if `DownloadState`, file existence, and worker progress are not unified carefully.
- Race conditions around cancel versus complete.
- Worker DI and test setup friction.
- UX inconsistency if some flows still auto-install while others move to explicit install.
