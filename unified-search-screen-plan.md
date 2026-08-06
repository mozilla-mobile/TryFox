# Unified search screen implementation plan

## Goal and scope

Replace the separate Profile and Treeherder APK screens with one `Search builds` screen. It must let a user select a Treeherder project (default: `try`), enter either an author email address or a revision, choose the correct request from that input, and show downloadable APKs grouped under each matching push.

The screen will show, for every result push:

- the commit message;
- a formatted push time and author;
- one row for every compatible APK-producing job, prefixed by the existing app icon; and
- only the APK variant best suited to this device, with the existing download/install state and actions.

The Profile destination and its Home-screen icon will be removed. The existing revision and author deep links will continue to open the new screen and immediately search.

## Current implementation to consolidate

- `TryFoxMainScreen` / `TryFoxViewModel` already own the project + revision search, revision push metadata, artifact fetching, cache state, downloads, history writes, and installs.
- `ProfileScreen` / `ProfileViewModel` separately implement author search, then repeat much of the artifact/download/history logic.
- `TreeherderApiService.getPushByAuthor` is hard-coded to `project/try/push/`; it needs to use the selected project, just as revision lookup does.
- `AppDeepLinkParser` already recognizes both revision links (`revision`) and author links (`author`). `AppDeepLinkRouteMapper` currently maps author links to the Profile route.

## Implementation steps

1. Introduce a unified search model and query classification.

   - Add a small, unit-testable query type in the screen package (for example `SearchQuery.Email` and `SearchQuery.Revision`) plus a classifier that trims the input.
   - Classify a syntactically valid email address as `Email`; treat a non-empty value without an `@` as a revision. Treat malformed `@` input as invalid and expose a clear validation error instead of issuing either request.
   - Keep `try` as the initial selected project. Retain project selection for both search types, because author lookup will become project-scoped.
   - Represent UI state as a single screen state: selected project, query text/type, loading/error state, and a list of push results. Each push result contains its comment, author, timestamp, revision, and compatible job/APK rows.

2. Consolidate the two ViewModels into one search ViewModel.

   - Create `UnifiedSearchViewModel` by extracting the reusable cache, artifact-fetching, download, install, and history code from the two existing ViewModels. Prefer moving shared behavior into private helpers or a dedicated search/result loader instead of copying one ViewModel into the other.
   - On search, dispatch `TreeherderRepository.getPushByRevision(selectedProject, revision)` for revisions and `getPushesByAuthor(selectedProject, email)` for emails. Preserve the Profile screen's bounded author-result behavior (currently 10 pushes) unless product requirements change.
   - Convert every returned push to the same result shape: derive its first Bug comment when present (otherwise the first revision comment), retain its author and push timestamp, then load signed Android APK jobs and artifacts for that push.
   - Reuse the existing preferred-job/fallback-job selection and pagination behavior from `TryFoxViewModel`; apply it per returned push for author searches. Preserve partial-result error reporting if a later jobs page fails.
   - During artifact mapping, select exactly one APK per job. Determine compatibility from the device ABI list and choose the compatible artifact matching the first ABI in `Build.SUPPORTED_ABIS` order; do not surface other compatible or incompatible variants. Jobs with no compatible APK are omitted from the displayed list.
   - Preserve cache refresh, WorkManager download state observation, automatic install behavior, and history upsert/update behavior for the selected artifact. Keep cache clearing disabled while any selected artifact is downloading.
   - Delete `ProfileViewModel` only after the unified ViewModel has equivalent author-search and download/install coverage.

3. Replace the Compose UI with the approved layout.

   - Rename or replace `TryFoxMainScreen` / `TreeherderApksScreen.kt` with a unified `SearchScreen` and move generic composables out only where it reduces duplication.
   - Keep the existing top app bar, back action, cache-clear action, and project dropdown. Default the dropdown to `try`.
   - Replace the revision-only field with a single `Email or revision` field. It should visibly indicate the detected type (email or revision), search on the keyboard action and the search button, and be enabled only for non-blank valid input while not loading.
   - Remove the `Find a push` and explanatory text from the search card.
   - Render each push as one card with the commit message, a formatted timestamp (reuse the existing push-time formatting/chip utility where appropriate), and author. Beneath it, render one compact row per selected compatible job: `AppIcon`, the job name, and the existing `DownloadButton`. Do not show ABI chips, task IDs, unsupported variants, or a second variant chooser.
   - Use stable keys based on the push identifier/revision and job task ID. Keep distinct empty, no-results, loading, and recoverable-error states for both query types.
   - Update/add string resources for neutral search copy and result states; remove Profile-only strings only after no source/test uses remain. Update accessibility labels and Compose test tags to be query-neutral.
   - Delete `ProfileScreen.kt` after migrating any useful private UI pieces (notably the compatible-APK row) to the unified screen.

4. Make author lookup project-aware at the repository boundary.

   - Change `TreeherderApiService.getPushByAuthor` to use `@GET("project/{project}/push/")` and a `@Path project` argument with its existing `full`, `count`, and `author` query parameters.
   - Change `TreeherderRepository.getPushesByAuthor` and `DefaultTreeherderRepository` to accept and forward `project`.
   - Update every production fake and test implementation of `TreeherderRepository`. Add an assertion in the unified ViewModel tests that an email search forwards the selected project.

5. Simplify navigation and preserve deep links.

   - Replace the Home screen's two callbacks (`onNavigateToTreeherder` and `onNavigateToProfile`) with one `onNavigateToSearch`; remove `AccountCircle`, the Profile icon button, and `home_profile_button_description`. Keep the Search icon, now described as the unified build search.
   - Keep `treeherder_search` as the canonical destination and keep the existing `treeherder_search/{project}/{revision}` path shape. Rename its argument concept from `revision` to `query` internally, so the exact same route can preload either an email or a revision without changing existing revision route strings.
   - Change `AppDeepLinkRouteMapper` so an author destination maps to `AppRoutes.createTreeherderSearchRoute(project = "try", query = email)`. Revision destinations keep their project and current route output. URL encoding remains required for emails and unusual revisions.
   - Keep `AppDeepLinkParser` support for all existing accepted link forms:
     - `https://treeherder.mozilla.org/jobs?repo=…&revision=…`
     - `tryfox://jobs?repo=…&revision=…`
     - the corresponding `author=…` forms, defaulting the project to `try` when no `repo` is present.
   - Retain a temporary `profile_by_email?email=…` NavHost compatibility alias that opens `SearchScreen` with project `try` and the email prefilled/searched. It is not exposed in Home and can be removed in a later release after any persisted/internal route consumers have migrated. Remove the plain `profile` destination rather than retaining an empty screen.
   - Update `HistoryScreen` and `ReceiveFromDesktopScreen` navigation callbacks to use the unified route helper; their revision deep links should continue to auto-search.

6. Remove dead code and update DI.

   - Register `UnifiedSearchViewModel` in `AppModule` with the dependencies currently split between `TryFoxViewModel` and `ProfileViewModel`, including `UserDataRepository` only if the product still wants to remember the last email. If retained, load it only as a prefill; do not auto-search on a normal Home navigation.
   - Remove the `ProfileViewModel` Koin registration, `ProfileScreen` import/route, and any no-longer-used Profile-only composables/models/strings.
   - Rename Treeherder-only identifiers where that makes the unified responsibility clearer, while leaving storage/cache names and persisted history schema unchanged for compatibility.
   - Do not change the Android intent filters: they already admit both Treeherder HTTPS and `tryfox://jobs` links.

## Test plan

### Unit tests

- Add classifier tests for valid email, revision, whitespace trimming, blank input, and malformed input containing `@`.
- Replace/expand `ProfileViewModelTest` with `UnifiedSearchViewModelTest`:
  - email search calls the author endpoint with the selected project and builds multiple push cards;
  - revision search calls the revision endpoint with the selected project and builds one push card;
  - each card retains the expected comment, author, and timestamp;
  - jobs without a compatible APK are excluded;
  - when multiple APK ABIs are available, the selected artifact follows the supplied device ABI preference order and only that artifact is exposed;
  - signed-job preference, fallback, pagination, de-duplication, partial errors, cache/download state, history write, and install behavior continue to pass (migrate the relevant existing `TryFoxViewModelTest` and `ProfileViewModelTest` cases rather than losing them);
  - an email/revision deep-link initialization triggers the correct request exactly once.
- Update repository/service tests and fakes for the new `getPushesByAuthor(project, author)` signature.
- Update `AppDeepLinkParserTest` to assert author links still parse, including percent-encoded addresses and default/missing repo behavior.
- Update `AppDeepLinkRouteMapperTest` to assert revision route outputs are unchanged and author links now encode to the unified Treeherder-search route. Add a route test for an email containing `+`.

### Compose and instrumentation tests

- Replace `ProfileScreenTest` and `TreeherderApksScreenTest` with `UnifiedSearchScreenTest` coverage for:
  - `try` shown as the default project;
  - typing an email/revision changes the detected search type and invokes the correct ViewModel action;
  - the revision deep-link/loading state and result header still behave correctly;
  - an email search renders multiple push cards with message, formatted time, author, app icon, job name, and one compatible APK action per job;
  - unsupported and duplicate ABI variants are absent from the UI;
  - download transitions from Download to Downloading to Install and triggers installation as before;
  - no-results and invalid-query error states are accessible.
- Update `MainActivityDeeplinkTest` to cover opening both a revision URI and an author URI into the unified screen, including back-navigation behavior. Keep the existing QR-scanned deep-link path covered.
- Update Home screen tests (or add them if absent) to assert there is one unified Search action and no Profile action.

### Verification commands

1. `./gradlew :app:detekt`
2. `./gradlew :app:ktlintCheck`
3. `./gradlew :app:testDebugUnitTest`
4. `./gradlew :app:connectedDebugAndroidTest` on an emulator/device when available

Review the mockup in `doc/unified-search-screen.html` alongside the final Compose screen; update the mockup only if the implementation reveals a needed design decision.
