package org.mozilla.tryfox.ui.screens

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.RegisterExtension
import org.junit.jupiter.api.io.TempDir
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import org.mozilla.tryfox.data.DownloadState
import org.mozilla.tryfox.data.FakeMozillaArchiveRepository
import org.mozilla.tryfox.data.FakeReferenceBrowserReleaseRepository
import org.mozilla.tryfox.data.FakeTryFoxReleaseRepository
import org.mozilla.tryfox.data.InstalledTryBuild
import org.mozilla.tryfox.data.MozillaPackageManager
import org.mozilla.tryfox.data.NetworkResult
import org.mozilla.tryfox.data.managers.FakeCacheManager
import org.mozilla.tryfox.data.managers.FakeIntentManager
import org.mozilla.tryfox.data.managers.FakeUserDataRepository
import org.mozilla.tryfox.data.repositories.CachedHomeApk
import org.mozilla.tryfox.data.repositories.CachedHomeApp
import org.mozilla.tryfox.data.repositories.DateAwareReleaseRepository
import org.mozilla.tryfox.data.repositories.FenixReleaseReleaseRepository
import org.mozilla.tryfox.data.repositories.FenixReleaseRepository
import org.mozilla.tryfox.data.repositories.FocusNightlyRepository
import org.mozilla.tryfox.data.repositories.FocusReleaseRepository
import org.mozilla.tryfox.data.repositories.HomeDataCacheRepository
import org.mozilla.tryfox.data.repositories.HomeDataSnapshot
import org.mozilla.tryfox.data.repositories.InstalledTryBuildRepository
import org.mozilla.tryfox.data.repositories.ReleaseRepository
import org.mozilla.tryfox.download.ApkDownloadCoordinator
import org.mozilla.tryfox.download.ApkDownloadRequest
import org.mozilla.tryfox.download.model.DownloadStatus
import org.mozilla.tryfox.download.model.PersistedDownloadState
import org.mozilla.tryfox.install.ApkInstallCoordinator
import org.mozilla.tryfox.model.AppState
import org.mozilla.tryfox.model.CacheManagementState
import org.mozilla.tryfox.model.HomeScreenLayout
import org.mozilla.tryfox.model.MozillaArchiveApk
import org.mozilla.tryfox.ui.models.AbiUiModel
import org.mozilla.tryfox.ui.models.ApkUiModel
import org.mozilla.tryfox.ui.models.ApksResult
import org.mozilla.tryfox.util.FENIX
import org.mozilla.tryfox.util.FENIX_DEBUG
import org.mozilla.tryfox.util.FENIX_DEBUG_PACKAGE
import org.mozilla.tryfox.util.FENIX_NIGHTLY_PACKAGE
import org.mozilla.tryfox.util.FENIX_RELEASE
import org.mozilla.tryfox.util.FOCUS
import org.mozilla.tryfox.util.FOCUS_RELEASE
import org.mozilla.tryfox.util.REFERENCE_BROWSER
import org.mozilla.tryfox.util.TRYFOX
import java.io.File

@ExperimentalCoroutinesApi
@ExtendWith(MockitoExtension::class)
@MockitoSettings(strictness = Strictness.LENIENT)
class HomeViewModelTest {

    @JvmField
    @RegisterExtension
    val mainCoroutineRule = MainCoroutineRule()

    private lateinit var viewModel: HomeViewModel
    private lateinit var fakeCacheManager: FakeCacheManager
    private lateinit var fakeDownloadCoordinator: FakeApkDownloadCoordinator
    private val intentManager = FakeIntentManager()
    private lateinit var installCoordinator: ApkInstallCoordinator

    @TempDir
    lateinit var tempCacheDir: File

    private val testFenixAppName = FENIX
    private val testFenixReleaseAppName = FENIX_RELEASE
    private val testFocusAppName = FOCUS
    private val testFocusReleaseAppName = FOCUS_RELEASE
    private val testReferenceBrowserAppName = REFERENCE_BROWSER
    private val testTryFoxAppName = TRYFOX
    private val testVersion = "125.0a1"
    private val testDateRaw = "2023-11-01-01-01-01"
    private val testAbi = "arm64-v8a"

    private fun createTestParsedNightlyApk(
        appName: String,
        dateRaw: String?,
        version: String,
        abi: String,
    ): MozillaArchiveApk {
        val fileName = if (appName == testReferenceBrowserAppName) {
            "target.$abi.apk"
        } else {
            "$appName-$version.multi.android-$abi.apk"
        }
        val originalString = if (appName == testReferenceBrowserAppName) {
            "$appName-latest-android-$abi/"
        } else {
            "$dateRaw-$appName-$version-android-$abi/"
        }
        val fullUrl = if (appName == testReferenceBrowserAppName) {
            "https://firefox-ci-tc.services.mozilla.com/api/index/v1/task/mobile.v2.$appName.nightly.latest.$abi/artifacts/public/target.$abi.apk"
        } else {
            "http://fake.url/$dateRaw-$appName-$version-android-$abi/$fileName"
        }

        return MozillaArchiveApk(
            originalString = originalString,
            rawDateString = if (appName == testReferenceBrowserAppName) null else dateRaw,
            appName = appName,
            version = if (appName == testReferenceBrowserAppName) "latest" else version,
            abiName = abi,
            fullUrl = fullUrl,
            fileName = fileName,
        )
    }

    private fun createTestApkUiModel(
        parsed: MozillaArchiveApk,
        downloadState: DownloadState = DownloadState.NotDownloaded,
    ): ApkUiModel {
        val dateFormatted = parsed.rawDateString?.formatApkDateForTest() ?: ""
        // Mirrors production: cache paths are keyed by the full build timestamp so same-day builds
        // don't collide; releases have a blank timestamp and fall back to the app name.
        val buildKey = parsed.rawDateString?.takeIf { it.isNotBlank() }

        val dirPath = if (buildKey == null) {
            parsed.appName
        } else {
            "${parsed.appName}${File.separator}$buildKey"
        }
        val apkDir = File(tempCacheDir, dirPath)

        val uniqueKeyPath = if (buildKey == null) {
            parsed.appName
        } else {
            "${parsed.appName}/$buildKey"
        }
        val uniqueKey = "$uniqueKeyPath/${parsed.fileName}"

        return ApkUiModel(
            originalString = parsed.originalString,
            date = dateFormatted,
            buildDate = parsed.rawDateString?.rawNightlyBuildDate(),
            appName = parsed.appName,
            version = parsed.version,
            abi = AbiUiModel(parsed.abiName, true),
            url = parsed.fullUrl,
            fileName = parsed.fileName,
            downloadState = downloadState,
            uniqueKey = uniqueKey,
            apkDir = apkDir,
        )
    }

    private fun createTestParsedReleaseApk(
        version: String,
        appName: String = testFenixReleaseAppName,
        abi: String = testAbi,
    ): MozillaArchiveApk {
        val fileName = "$appName-$version.multi.android-$abi.apk"
        return MozillaArchiveApk(
            originalString = "$appName-$version-android-$abi/",
            rawDateString = "",
            appName = appName,
            version = version,
            abiName = abi,
            fullUrl = "https://archive.mozilla.org/pub/$appName/releases/$version/android/$appName-$version-android-$abi/$fileName",
            fileName = fileName,
        )
    }

    @BeforeEach
    fun setUp() {
        fakeCacheManager = FakeCacheManager(tempCacheDir)
        fakeDownloadCoordinator = FakeApkDownloadCoordinator()
        installCoordinator = mock()
        whenever(installCoordinator.states).thenReturn(MutableStateFlow(emptyMap()))
        viewModel = createViewModel()
    }

    private fun createViewModel(
        releaseRepositories: List<ReleaseRepository> = emptyList(),
        mozillaPackageManager: MozillaPackageManager = FakeMozillaPackageManager(),
        userDataRepository: FakeUserDataRepository? = null,
        homeDataCacheRepository: HomeDataCacheRepository = FakeHomeDataCacheRepository(),
        installedTryBuildRepository: InstalledTryBuildRepository = FakeInstalledTryBuildRepository(),
    ) = HomeViewModel(
        releaseRepositories = releaseRepositories,
        downloadCoordinator = fakeDownloadCoordinator,
        mozillaPackageManager = mozillaPackageManager,
        cacheManager = fakeCacheManager,
        intentManager = intentManager,
        installCoordinator = installCoordinator,
        ioDispatcher = mainCoroutineRule.testDispatcher,
        userDataRepository = userDataRepository,
        homeDataCacheRepository = homeDataCacheRepository,
        installedTryBuildRepository = installedTryBuildRepository,
        supportedAbis = listOf("arm64-v8a", "x86_64", "armeabi-v7a"),
    )

    @Test
    fun `layout preference updates the loaded home state`() = runTest {
        val userDataRepository = FakeUserDataRepository()
        val viewModel = createViewModel(userDataRepository = userDataRepository)

        viewModel.initialLoad()
        advanceUntilIdle()
        userDataRepository.saveHomeScreenLayout(HomeScreenLayout.OneCardPerFlavor)
        advanceUntilIdle()

        val state = viewModel.homeScreenState.value as HomeScreenState.Loaded
        assertEquals(HomeScreenLayout.OneCardPerFlavor, state.homeScreenLayout)
    }

    private class FakeHomeDataCacheRepository(
        var snapshot: HomeDataSnapshot? = null,
    ) : HomeDataCacheRepository {
        val writes = mutableListOf<HomeDataSnapshot>()

        override suspend fun read(): HomeDataSnapshot? = snapshot

        override suspend fun write(snapshot: HomeDataSnapshot) {
            writes += snapshot
            this.snapshot = snapshot
        }
    }

    private class FakeInstalledTryBuildRepository(
        build: InstalledTryBuild? = null,
    ) : InstalledTryBuildRepository {
        private val state = MutableStateFlow(build)
        override val installedTryBuild = state.asStateFlow()

        override suspend fun save(build: InstalledTryBuild) {
            state.value = build
        }
    }

    @Test
    fun `shows Try build provenance only when Fenix Debug version matches`() = runTest {
        val build = InstalledTryBuild(
            packageName = FENIX_DEBUG_PACKAGE,
            project = "try",
            revision = "abc123",
            commitMessage = "Bug 123: debug build",
            versionName = "145.0a1",
            versionCode = 42L,
        )
        val packageManager = FakeMozillaPackageManager(
            mapOf(
                FENIX_DEBUG_PACKAGE to AppState(
                    "Firefox Debug",
                    FENIX_DEBUG_PACKAGE,
                    "145.0a1",
                    1L,
                    versionCode = 42L,
                ),
            ),
        )
        val viewModel = createViewModel(
            mozillaPackageManager = packageManager,
            installedTryBuildRepository = FakeInstalledTryBuildRepository(build),
        )

        viewModel.initialLoad()
        advanceUntilIdle()

        val apps = (viewModel.homeScreenState.value as HomeScreenState.Loaded).apps
        assertEquals(build, apps.getValue(FENIX_DEBUG).installedTryBuild)

        val mismatchedPackageManager = FakeMozillaPackageManager(
            mapOf(
                FENIX_DEBUG_PACKAGE to AppState(
                    "Firefox Debug",
                    FENIX_DEBUG_PACKAGE,
                    "145.0a1",
                    1L,
                    versionCode = 43L,
                ),
            ),
        )
        val mismatchedViewModel = createViewModel(
            mozillaPackageManager = mismatchedPackageManager,
            installedTryBuildRepository = FakeInstalledTryBuildRepository(build),
        )
        mismatchedViewModel.initialLoad()
        advanceUntilIdle()

        val mismatchedApps = (mismatchedViewModel.homeScreenState.value as HomeScreenState.Loaded).apps
        assertNull(mismatchedApps.getValue(FENIX_DEBUG).installedTryBuild)
    }

    private class CountingReleaseRepository(
        override val appName: String,
        private val result: NetworkResult<List<MozillaArchiveApk>>,
    ) : ReleaseRepository {
        var calls = 0

        override suspend fun getLatestReleases(): NetworkResult<List<MozillaArchiveApk>> {
            calls += 1
            return result
        }
    }

    private class BlockingDateReleaseRepository(
        override val appName: String,
        private val latestResult: NetworkResult<List<MozillaArchiveApk>>,
        private val dateResult: NetworkResult<List<MozillaArchiveApk>>,
    ) : DateAwareReleaseRepository {
        val latestStarted = CompletableDeferred<Unit>()
        val unblockLatest = CompletableDeferred<Unit>()
        var latestCalls = 0

        override suspend fun getLatestReleases(): NetworkResult<List<MozillaArchiveApk>> {
            latestCalls += 1
            latestStarted.complete(Unit)
            unblockLatest.await()
            return latestResult
        }

        override suspend fun getReleases(date: LocalDate?): NetworkResult<List<MozillaArchiveApk>> = dateResult
    }

    private class FakeApkDownloadCoordinator : ApkDownloadCoordinator {
        private val _downloads = MutableStateFlow<Map<String, PersistedDownloadState>>(emptyMap())
        val enqueuedRequests = mutableListOf<ApkDownloadRequest>()

        override val downloads = _downloads.asStateFlow()

        override fun enqueue(request: ApkDownloadRequest): String {
            enqueuedRequests += request
            updateState(
                request.uniqueKey,
                request.toPersistedState(
                    status = DownloadStatus.QUEUED,
                    workId = request.uniqueKey,
                ),
            )
            return request.uniqueKey
        }

        override fun retry(request: ApkDownloadRequest): String = enqueue(request)

        override fun cancel(uniqueKey: String) {
            _downloads.value[uniqueKey]?.let { current ->
                updateState(
                    uniqueKey,
                    current.copy(
                        status = DownloadStatus.CANCELED,
                        updatedAt = System.currentTimeMillis(),
                    ),
                )
            }
        }

        override fun observe(uniqueKey: String) = downloads.map { it[uniqueKey] }

        fun emit(state: PersistedDownloadState) {
            updateState(state.uniqueKey, state)
        }

        private fun updateState(uniqueKey: String, state: PersistedDownloadState) {
            _downloads.value = _downloads.value + (uniqueKey to state)
        }

        private fun ApkDownloadRequest.toPersistedState(
            status: DownloadStatus,
            workId: String? = null,
        ): PersistedDownloadState =
            PersistedDownloadState(
                uniqueKey = uniqueKey,
                downloadUrl = downloadUrl,
                outputPath = outputPath,
                appName = appName,
                fileName = fileName,
                cacheRelativePath = cacheRelativePath,
                status = status,
                workId = workId,
            )
    }

    private fun String.formatApkDateForTest(): String = formatNightlyBuildDate()

    @AfterEach
    fun tearDown() {
        fakeCacheManager.reset()
    }

    @Test
    fun `initialLoad when no data then homeScreenState is InitialLoading before load completes`() =
        runTest {
            assertTrue(
                viewModel.homeScreenState.value is HomeScreenState.InitialLoading,
                "Initial HomeScreenState should be InitialLoading",
            )
        }

    @Test
    fun `initialLoad hydrates cached data and retains it when refresh fails`() = runTest {
        val cachedApk = createTestParsedNightlyApk(testFenixAppName, testDateRaw, testVersion, testAbi)
        val cache = FakeHomeDataCacheRepository(
            HomeDataSnapshot(
                version = HomeDataSnapshot.CURRENT_VERSION,
                apps = listOf(
                    CachedHomeApp(
                        appName = testFenixAppName,
                        apks = listOf(
                            CachedHomeApk(
                                cachedApk.originalString,
                                cachedApk.rawDateString,
                                cachedApk.appName,
                                cachedApk.version,
                                cachedApk.abiName,
                                cachedApk.fullUrl,
                                cachedApk.fileName,
                            ),
                        ),
                    ),
                ),
            ),
        )
        val repository = CountingReleaseRepository(
            testFenixAppName,
            NetworkResult.Error("offline"),
        )
        viewModel = createViewModel(listOf(repository), homeDataCacheRepository = cache)

        viewModel.initialLoad()
        advanceUntilIdle()

        val state = viewModel.homeScreenState.value as HomeScreenState.Loaded
        assertTrue(state.apps[testFenixAppName]?.apks is ApksResult.Success)
        assertEquals(1, repository.calls)
        assertEquals(1, cache.writes.size)
    }

    @Test
    fun `initialLoad is idempotent after home view model has loaded`() = runTest {
        val repository = CountingReleaseRepository(
            testFenixAppName,
            NetworkResult.Success(emptyList()),
        )
        viewModel = createViewModel(listOf(repository))

        viewModel.initialLoad()
        advanceUntilIdle()
        viewModel.initialLoad()
        advanceUntilIdle()

        assertEquals(1, repository.calls)
    }

    @Test
    fun `refresh rereads package state after installed app is removed outside TryFox`() = runTest {
        val packageManager = FakeMozillaPackageManager(
            mapOf(
                FENIX_NIGHTLY_PACKAGE to AppState(
                    "Fenix",
                    FENIX_NIGHTLY_PACKAGE,
                    "125.0a1",
                    1L,
                ),
            ),
        )
        val repository = CountingReleaseRepository(
            testFenixAppName,
            NetworkResult.Success(emptyList()),
        )
        viewModel = createViewModel(
            releaseRepositories = listOf(repository),
            mozillaPackageManager = packageManager,
        )

        viewModel.initialLoad()
        advanceUntilIdle()
        assertEquals(
            "125.0a1",
            (viewModel.homeScreenState.value as HomeScreenState.Loaded).apps.getValue(FENIX).installedVersion,
        )

        packageManager.setAppState(AppState("Fenix", FENIX_NIGHTLY_PACKAGE, null, null))
        viewModel.refreshInstalledAppStates()
        advanceUntilIdle()

        assertNull(
            (viewModel.homeScreenState.value as HomeScreenState.Loaded).apps.getValue(FENIX).installedVersion,
        )
        assertFalse(viewModel.isRefreshing.value)
    }

    @Test
    fun `refresh waits for an in-flight load before starting another request`() = runTest {
        val repository = BlockingDateReleaseRepository(
            testFenixAppName,
            NetworkResult.Success(emptyList()),
            NetworkResult.Success(emptyList()),
        )
        viewModel = createViewModel(listOf(repository))

        viewModel.initialLoad()
        runCurrent()
        assertTrue(repository.latestStarted.isCompleted)
        viewModel.refreshData()
        runCurrent()

        assertEquals(1, repository.latestCalls)
        assertTrue(viewModel.isRefreshing.value)

        repository.unblockLatest.complete(Unit)
        advanceUntilIdle()

        assertEquals(2, repository.latestCalls)
        assertFalse(viewModel.isRefreshing.value)
    }

    @Test
    fun `refresh retains the selected home app flavor`() = runTest {
        val repository = CountingReleaseRepository(
            testFenixAppName,
            NetworkResult.Success(emptyList()),
        )
        viewModel = createViewModel(listOf(repository))

        viewModel.initialLoad()
        advanceUntilIdle()
        viewModel.selectHomeAppFlavor(HomeAppFamily.Fenix, FENIX_RELEASE)

        viewModel.refreshData()
        advanceUntilIdle()

        val state = viewModel.homeScreenState.value as HomeScreenState.Loaded
        assertEquals(FENIX_RELEASE, state.selectedAppNames[HomeAppFamily.Fenix])
    }

    @Test
    fun `date selection is retained when cache refresh finishes later`() = runTest {
        val cachedApk = createTestParsedNightlyApk(testFenixAppName, testDateRaw, testVersion, testAbi)
        val selectedApk = createTestParsedNightlyApk(
            testFenixAppName,
            "2023-10-30-01-01-01",
            testVersion,
            testAbi,
        )
        val cache = FakeHomeDataCacheRepository(
            HomeDataSnapshot(
                version = HomeDataSnapshot.CURRENT_VERSION,
                apps = listOf(
                    CachedHomeApp(
                        appName = testFenixAppName,
                        apks = listOf(
                            CachedHomeApk(
                                cachedApk.originalString,
                                cachedApk.rawDateString,
                                cachedApk.appName,
                                cachedApk.version,
                                cachedApk.abiName,
                                cachedApk.fullUrl,
                                cachedApk.fileName,
                            ),
                        ),
                    ),
                ),
            ),
        )
        val repository = BlockingDateReleaseRepository(
            testFenixAppName,
            NetworkResult.Success(listOf(cachedApk)),
            NetworkResult.Success(listOf(selectedApk)),
        )
        viewModel = createViewModel(listOf(repository), homeDataCacheRepository = cache)
        val selectedDate = LocalDate(2023, 10, 30)

        viewModel.initialLoad()
        runCurrent()
        assertTrue(repository.latestStarted.isCompleted)

        viewModel.onDateSelected(testFenixAppName, selectedDate)
        runCurrent()
        repository.unblockLatest.complete(Unit)
        advanceUntilIdle()

        val state = viewModel.homeScreenState.value as HomeScreenState.Loaded
        assertEquals(selectedDate, state.apps[testFenixAppName]?.userPickedDate)
        assertEquals(
            selectedApk.rawDateString?.formatApkDateForTest(),
            (state.apps[testFenixAppName]?.apks as? ApksResult.Success)?.apks?.single()?.date,
        )
    }

    @Test
    fun `initialLoad success should update HomeScreenState to Loaded with data`() = runTest {
        val fenixParsed =
            createTestParsedNightlyApk(testFenixAppName, testDateRaw, testVersion, testAbi)
        val focusParsed =
            createTestParsedNightlyApk(testFocusAppName, testDateRaw, "126.0a1", "x86_64")
        val focusReleaseParsed =
            createTestParsedReleaseApk("126.0.1", appName = testFocusReleaseAppName, abi = "x86_64")
        val rbParsed = createTestParsedNightlyApk(
            testReferenceBrowserAppName,
            testDateRaw,
            "latest",
            "armeabi-v7a",
        )
        val tryFoxParsed =
            createTestParsedNightlyApk(testTryFoxAppName, null, "1.0.0", "armeabi-v7a")

        val releaseRepositories = listOf(
            FenixReleaseRepository(FakeMozillaArchiveRepository(fenixBuilds = NetworkResult.Success(listOf(fenixParsed)))),
            FocusNightlyRepository(FakeMozillaArchiveRepository(focusBuilds = NetworkResult.Success(listOf(focusParsed)))),
            FocusReleaseRepository(
                FakeMozillaArchiveRepository(
                    focusReleaseVersions = NetworkResult.Success(listOf("126.0.1")),
                    focusReleasesByVersion = mapOf("126.0.1" to NetworkResult.Success(listOf(focusReleaseParsed))),
                ),
            ),
            FakeReferenceBrowserReleaseRepository(releases = NetworkResult.Success(listOf(rbParsed))),
            FakeTryFoxReleaseRepository(releases = NetworkResult.Success(listOf(tryFoxParsed))),
        )

        viewModel = createViewModel(
            releaseRepositories = releaseRepositories,
        )

        fakeCacheManager.setCacheState(CacheManagementState.IdleEmpty)

        viewModel.initialLoad()
        advanceUntilIdle()

        val state = viewModel.homeScreenState.value
        assertTrue(state is HomeScreenState.Loaded, "HomeScreenState should be Loaded")
        val loadedState = state as HomeScreenState.Loaded

        val fenixApp = loadedState.apps[FENIX]
        assertNotNull(fenixApp)
        assertTrue(fenixApp!!.apks is ApksResult.Success, "Fenix builds should be Success")
        assertEquals(1, (fenixApp.apks as ApksResult.Success).apks.size)

        val focusApp = loadedState.apps[FOCUS]
        assertNotNull(focusApp)
        assertTrue(focusApp!!.apks is ApksResult.Success, "Focus builds should be Success")
        assertEquals(1, (focusApp.apks as ApksResult.Success).apks.size)

        val focusReleaseApp = loadedState.apps[FOCUS_RELEASE]
        assertNotNull(focusReleaseApp)
        assertTrue(focusReleaseApp!!.apks is ApksResult.Success, "Focus Release builds should be Success")
        assertEquals(1, (focusReleaseApp.apks as ApksResult.Success).apks.size)
        assertEquals("126.0.1", focusReleaseApp.selectedReleaseVersion)

        val rbApp = loadedState.apps[REFERENCE_BROWSER]
        assertNotNull(rbApp)
        assertTrue(rbApp!!.apks is ApksResult.Success, "Reference Browser builds should be Success")
        assertEquals(1, (rbApp.apks as ApksResult.Success).apks.size)

        val tryFoxApp = loadedState.apps[TRYFOX]
        assertNull(tryFoxApp, "TryFox app should be null when loading")

        assertEquals(CacheManagementState.IdleEmpty, loadedState.cacheManagementState)
        assertTrue(fakeCacheManager.checkCacheStatusCalled)
    }

    @Test
    fun `initialLoad with multiple builds on same day should only show latest`() = runTest {
        val olderFenixParsed = createTestParsedNightlyApk(
            testFenixAppName,
            "2023-11-01-01-01-01",
            "125.0a1",
            testAbi,
        )
        val newerFenixParsed = createTestParsedNightlyApk(
            testFenixAppName,
            "2023-11-01-14-01-01",
            "125.0a1",
            testAbi,
        )
        val releaseRepositories = listOf(
            FenixReleaseRepository(FakeMozillaArchiveRepository(fenixBuilds = NetworkResult.Success(listOf(olderFenixParsed, newerFenixParsed)))),
        )
        viewModel = createViewModel(releaseRepositories = releaseRepositories)
        fakeCacheManager.setCacheState(CacheManagementState.IdleEmpty)

        viewModel.initialLoad()
        advanceUntilIdle()

        val state = viewModel.homeScreenState.value as HomeScreenState.Loaded
        val fenixApp = state.apps[FENIX]
        assertNotNull(fenixApp)
        val fenixApksResult = fenixApp!!.apks as ApksResult.Success
        assertEquals(1, fenixApksResult.apks.size)
        assertEquals(
            newerFenixParsed.rawDateString?.formatApkDateForTest(),
            fenixApksResult.apks.first().date,
        )
    }

    @Test
    fun `initialLoad with Fenix Release versions should select latest and expose picker options`() = runTest {
        val latestReleaseApk = createTestParsedReleaseApk(version = "145.0.1")
        val releaseRepositories = listOf(
            FenixReleaseReleaseRepository(
                FakeMozillaArchiveRepository(
                    fenixReleaseVersions = NetworkResult.Success(listOf("145.0.1", "145.0", "144.0.2")),
                    fenixReleasesByVersion = mapOf(
                        "145.0.1" to NetworkResult.Success(listOf(latestReleaseApk)),
                    ),
                ),
            ),
        )

        viewModel = createViewModel(releaseRepositories = releaseRepositories)
        fakeCacheManager.setCacheState(CacheManagementState.IdleEmpty)

        viewModel.initialLoad()
        advanceUntilIdle()

        val state = viewModel.homeScreenState.value as HomeScreenState.Loaded
        val fenixReleaseApp = state.apps[FENIX_RELEASE]

        assertNotNull(fenixReleaseApp)
        assertEquals("145.0.1", fenixReleaseApp!!.selectedReleaseVersion)
        assertEquals(listOf("145.0.1", "145.0", "144.0.2"), fenixReleaseApp.availableReleaseVersions)
        assertTrue(fenixReleaseApp.apks is ApksResult.Success)
        assertEquals("145.0.1", (fenixReleaseApp.apks as ApksResult.Success).apks.first().version)
    }

    @Test
    fun `onReleaseVersionSelected should reload Fenix Release APKs for selected version`() = runTest {
        val latestReleaseApk = createTestParsedReleaseApk(version = "145.0.1")
        val olderReleaseApk = createTestParsedReleaseApk(version = "144.0.2")
        val releaseRepositories = listOf(
            FenixReleaseReleaseRepository(
                FakeMozillaArchiveRepository(
                    fenixReleaseVersions = NetworkResult.Success(listOf("145.0.1", "144.0.2")),
                    fenixReleasesByVersion = mapOf(
                        "145.0.1" to NetworkResult.Success(listOf(latestReleaseApk)),
                        "144.0.2" to NetworkResult.Success(listOf(olderReleaseApk)),
                    ),
                ),
            ),
        )

        viewModel = createViewModel(releaseRepositories = releaseRepositories)
        fakeCacheManager.setCacheState(CacheManagementState.IdleEmpty)

        viewModel.initialLoad()
        advanceUntilIdle()
        viewModel.onReleaseVersionSelected(FENIX_RELEASE, "144.0.2")
        advanceUntilIdle()

        val state = viewModel.homeScreenState.value as HomeScreenState.Loaded
        val fenixReleaseApp = state.apps[FENIX_RELEASE]

        assertNotNull(fenixReleaseApp)
        assertEquals("144.0.2", fenixReleaseApp!!.selectedReleaseVersion)
        assertEquals("144.0.2", (fenixReleaseApp.apks as ApksResult.Success).apks.first().version)
    }

    @Test
    fun `onReleaseVersionSelected should reload Focus APKs for selected version`() = runTest {
        val latestReleaseApk = createTestParsedReleaseApk(version = "147.0.1", appName = testFocusReleaseAppName)
        val olderReleaseApk = createTestParsedReleaseApk(version = "146.0.1", appName = testFocusReleaseAppName)
        val releaseRepositories = listOf(
            FocusReleaseRepository(
                FakeMozillaArchiveRepository(
                    focusReleaseVersions = NetworkResult.Success(listOf("147.0.1", "146.0.1")),
                    focusReleasesByVersion = mapOf(
                        "147.0.1" to NetworkResult.Success(listOf(latestReleaseApk)),
                        "146.0.1" to NetworkResult.Success(listOf(olderReleaseApk)),
                    ),
                ),
            ),
        )

        viewModel = createViewModel(releaseRepositories = releaseRepositories)
        fakeCacheManager.setCacheState(CacheManagementState.IdleEmpty)

        viewModel.initialLoad()
        advanceUntilIdle()
        viewModel.onReleaseVersionSelected(FOCUS_RELEASE, "146.0.1")
        advanceUntilIdle()

        val state = viewModel.homeScreenState.value as HomeScreenState.Loaded
        val focusApp = state.apps[FOCUS_RELEASE]

        assertNotNull(focusApp)
        assertEquals("146.0.1", focusApp!!.selectedReleaseVersion)
        assertEquals("146.0.1", (focusApp.apks as ApksResult.Success).apks.first().version)
    }

    @Test
    fun `initialLoad with empty cache should result in IdleEmpty cache state from CacheManager`() =
        runTest {
            viewModel = createViewModel()
            fakeCacheManager.setCacheState(CacheManagementState.IdleEmpty)

            viewModel.initialLoad()
            advanceUntilIdle()

            val state = viewModel.homeScreenState.value as? HomeScreenState.Loaded
            assertNotNull(state, "State should be Loaded")
            assertEquals(
                CacheManagementState.IdleEmpty,
                state!!.cacheManagementState,
                "Cache state should be IdleEmpty",
            )
            assertTrue(fakeCacheManager.checkCacheStatusCalled)
        }

    @Test
    fun `initialLoad with fenix cache populated should result in IdleNonEmpty from CacheManager`() =
        runTest {
            val fenixParsed =
                createTestParsedNightlyApk(testFenixAppName, testDateRaw, testVersion, testAbi)
            val fenixApkUi = createTestApkUiModel(fenixParsed)
            fenixApkUi.apkDir.mkdirs()
            File(fenixApkUi.apkDir, fenixApkUi.fileName).createNewFile()

            val releaseRepositories = listOf(
                FenixReleaseRepository(FakeMozillaArchiveRepository(fenixBuilds = NetworkResult.Success(listOf(fenixParsed)))),
            )
            viewModel = createViewModel(releaseRepositories = releaseRepositories)
            fakeCacheManager.setCacheState(CacheManagementState.IdleNonEmpty)

            viewModel.initialLoad()
            advanceUntilIdle()

            val state = viewModel.homeScreenState.value as? HomeScreenState.Loaded
            assertNotNull(state, "State should be Loaded")
            assertEquals(
                CacheManagementState.IdleNonEmpty,
                state!!.cacheManagementState,
                "Cache state should be IdleNonEmpty",
            )
            assertTrue(fakeCacheManager.checkCacheStatusCalled)
            val fenixApks = (state.apps[FENIX]!!.apks as? ApksResult.Success)?.apks
            assertTrue(fenixApks?.first()?.downloadState is DownloadState.Downloaded)
        }

    @Test
    fun `clearAppCache should call CacheManager and update states to NotDownloaded`() = runTest {
        val fenixParsed =
            createTestParsedNightlyApk(testFenixAppName, testDateRaw, testVersion, testAbi)
        val rbParsed =
            createTestParsedNightlyApk(testReferenceBrowserAppName, "", "latest", testAbi)

        val fenixApkUiForCache = createTestApkUiModel(fenixParsed)
        fenixApkUiForCache.apkDir.mkdirs()
        val cachedFenixFile = File(fenixApkUiForCache.apkDir, fenixApkUiForCache.fileName)
        cachedFenixFile.createNewFile()
        assertTrue(cachedFenixFile.exists(), "Cache file for Fenix should exist before test action")

        val rbApkUiForCache = createTestApkUiModel(rbParsed)
        val rbCacheActualDir = File(tempCacheDir, rbApkUiForCache.appName)
        rbCacheActualDir.mkdirs()
        val cachedRbFile = File(rbCacheActualDir, rbApkUiForCache.fileName)
        cachedRbFile.createNewFile()
        assertTrue(cachedRbFile.exists(), "Cache file for RB should exist before test action")

        fakeCacheManager.setCacheState(CacheManagementState.IdleNonEmpty)

        val releaseRepositories = listOf(
            FenixReleaseRepository(FakeMozillaArchiveRepository(fenixBuilds = NetworkResult.Success(listOf(fenixParsed)))),
            FakeReferenceBrowserReleaseRepository(releases = NetworkResult.Success(listOf(rbParsed))),
        )
        viewModel = createViewModel(releaseRepositories = releaseRepositories)

        viewModel.initialLoad()
        advanceUntilIdle()

        var loadedState = viewModel.homeScreenState.value as HomeScreenState.Loaded
        val fenixSuccessStatePre = loadedState.apps[FENIX]!!.apks as ApksResult.Success
        assertFalse(fenixSuccessStatePre.apks.isEmpty(), "Fenix APK list should not be empty")
        assertTrue(
            fenixSuccessStatePre.apks.first().downloadState is DownloadState.Downloaded,
            "Fenix APK download state should be Downloaded",
        )
        val rbSuccessStatePre = loadedState.apps[REFERENCE_BROWSER]!!.apks as ApksResult.Success
        assertFalse(rbSuccessStatePre.apks.isEmpty(), "RB APK list should not be empty")
        assertTrue(
            rbSuccessStatePre.apks.first().downloadState is DownloadState.Downloaded,
            "RB APK download state should be Downloaded",
        )

        assertEquals(
            CacheManagementState.IdleNonEmpty,
            loadedState.cacheManagementState,
            "Cache state should be IdleNonEmpty initially",
        )

        viewModel.clearAppCache()
        advanceUntilIdle()

        assertTrue(fakeCacheManager.clearCacheCalled)
        loadedState = viewModel.homeScreenState.value as HomeScreenState.Loaded
        assertEquals(
            CacheManagementState.IdleEmpty,
            loadedState.cacheManagementState,
            "Cache state should be IdleEmpty after clear",
        )

        val fenixStateAfterClear = loadedState.apps[FENIX]!!.apks as ApksResult.Success
        assertFalse(
            fenixStateAfterClear.apks.isEmpty(),
            "Fenix APK list should not be empty after clear",
        )
        assertTrue(
            fenixStateAfterClear.apks.first().downloadState is DownloadState.NotDownloaded,
            "Fenix APK download state should be NotDownloaded after clear",
        )

        val rbStateAfterClear = loadedState.apps[REFERENCE_BROWSER]!!.apks as ApksResult.Success
        assertFalse(rbStateAfterClear.apks.isEmpty(), "RB APK list should not be empty after clear")
        assertTrue(
            rbStateAfterClear.apks.first().downloadState is DownloadState.NotDownloaded,
            "RB APK download state should be NotDownloaded after clear",
        )
    }

    @Test
    fun `downloadNightlyApk success sequence`() = runTest {
        val fenixParsed =
            createTestParsedNightlyApk(testFenixAppName, testDateRaw, testVersion, testAbi)
        val apkToDownload = createTestApkUiModel(fenixParsed, DownloadState.NotDownloaded)
        val expectedApkFile = File(apkToDownload.apkDir, apkToDownload.fileName)

        val releaseRepositories = listOf(
            FenixReleaseRepository(FakeMozillaArchiveRepository(fenixBuilds = NetworkResult.Success(listOf(fenixParsed)))),
        )
        viewModel = createViewModel(releaseRepositories = releaseRepositories)
        fakeCacheManager.setCacheState(CacheManagementState.IdleEmpty)

        viewModel.initialLoad()
        advanceUntilIdle()
        assertTrue(viewModel.homeScreenState.value is HomeScreenState.Loaded)
        val initialLoadedState = viewModel.homeScreenState.value as HomeScreenState.Loaded
        assertTrue(initialLoadedState.apps[FENIX]!!.apks is ApksResult.Success)

        viewModel.downloadNightlyApk(apkToDownload)
        advanceUntilIdle()

        assertEquals(1, fakeDownloadCoordinator.enqueuedRequests.size)
        val enqueuedRequest = fakeDownloadCoordinator.enqueuedRequests.first()
        assertEquals(apkToDownload.uniqueKey, enqueuedRequest.uniqueKey)
        assertEquals("Fenix Nightly $testVersion", enqueuedRequest.notificationTitle)

        var loadedState = viewModel.homeScreenState.value as HomeScreenState.Loaded
        var fenixBuildsState = loadedState.apps[FENIX]!!.apks as ApksResult.Success
        var downloadedApkInfo =
            fenixBuildsState.apks.find { it.uniqueKey == apkToDownload.uniqueKey }

        assertNotNull(downloadedApkInfo, "Queued APK info should not be null")
        assertTrue(
            downloadedApkInfo!!.downloadState is DownloadState.InProgress,
            "DownloadState should be InProgress while work is queued",
        )

        expectedApkFile.parentFile?.mkdirs()
        expectedApkFile.writeText("downloaded apk")
        fakeDownloadCoordinator.emit(
            PersistedDownloadState(
                uniqueKey = apkToDownload.uniqueKey,
                downloadUrl = apkToDownload.url,
                outputPath = expectedApkFile.absolutePath,
                appName = apkToDownload.appName,
                fileName = apkToDownload.fileName,
                cacheRelativePath = null,
                status = DownloadStatus.SUCCEEDED,
                bytesDownloaded = expectedApkFile.length(),
                totalBytes = expectedApkFile.length(),
                workId = enqueuedRequest.uniqueKey,
            ),
        )
        advanceUntilIdle()

        loadedState = viewModel.homeScreenState.value as HomeScreenState.Loaded
        fenixBuildsState = loadedState.apps[FENIX]!!.apks as ApksResult.Success
        downloadedApkInfo =
            fenixBuildsState.apks.find { it.uniqueKey == apkToDownload.uniqueKey }

        assertNotNull(downloadedApkInfo, "Downloaded APK info should not be null")
        assertTrue(
            downloadedApkInfo!!.downloadState is DownloadState.Downloaded,
            "DownloadState should be Downloaded",
        )
        assertEquals(
            expectedApkFile.path,
            (downloadedApkInfo.downloadState as DownloadState.Downloaded).file.path,
        )
        assertTrue(fakeCacheManager.checkCacheStatusCalled)
        assertFalse(
            loadedState.isDownloadingAnyFile,
            "isDownloadingAnyFile should be false after success",
        )
    }

    @Test
    fun `downloadNightlyApk failure sequence`() = runTest {
        val fenixParsed =
            createTestParsedNightlyApk(testFenixAppName, testDateRaw, testVersion, testAbi)
        val apkToDownload = createTestApkUiModel(fenixParsed, DownloadState.NotDownloaded)
        val expectedApkFile = File(apkToDownload.apkDir, apkToDownload.fileName)
        val downloadErrorMessage = "Download Canceled"

        val releaseRepositories = listOf(
            FenixReleaseRepository(FakeMozillaArchiveRepository(fenixBuilds = NetworkResult.Success(listOf(fenixParsed)))),
        )
        viewModel = createViewModel(releaseRepositories = releaseRepositories)
        fakeCacheManager.setCacheState(CacheManagementState.IdleEmpty)
        viewModel.initialLoad()
        advanceUntilIdle()
        assertTrue(viewModel.homeScreenState.value is HomeScreenState.Loaded)
        val initialLoadedState = viewModel.homeScreenState.value as HomeScreenState.Loaded
        assertTrue(initialLoadedState.apps[FENIX]!!.apks is ApksResult.Success)

        viewModel.downloadNightlyApk(apkToDownload)
        advanceUntilIdle()

        assertEquals(1, fakeDownloadCoordinator.enqueuedRequests.size)
        fakeDownloadCoordinator.emit(
            PersistedDownloadState(
                uniqueKey = apkToDownload.uniqueKey,
                downloadUrl = apkToDownload.url,
                outputPath = expectedApkFile.absolutePath,
                appName = apkToDownload.appName,
                fileName = apkToDownload.fileName,
                cacheRelativePath = null,
                status = DownloadStatus.FAILED,
                errorMessage = downloadErrorMessage,
                workId = fakeDownloadCoordinator.enqueuedRequests.first().uniqueKey,
            ),
        )
        advanceUntilIdle()

        val loadedState = viewModel.homeScreenState.value as HomeScreenState.Loaded
        val fenixBuildsState = loadedState.apps[FENIX]!!.apks as ApksResult.Success
        val failedApkInfo = fenixBuildsState.apks.find { it.uniqueKey == apkToDownload.uniqueKey }

        assertNotNull(failedApkInfo, "Failed APK info should not be null")
        assertTrue(
            failedApkInfo!!.downloadState is DownloadState.DownloadFailed,
            "DownloadState should be DownloadFailed",
        )
        assertEquals(
            downloadErrorMessage,
            (failedApkInfo.downloadState as DownloadState.DownloadFailed).message,
        )
        assertTrue(fakeCacheManager.checkCacheStatusCalled)
        assertFalse(
            loadedState.isDownloadingAnyFile,
            "isDownloadingAnyFile should be false after failure",
        )
    }

    @Test
    fun `getDateValidator should return correct validator for each app`() {
        val fenixValidator = viewModel.getDateValidator(FENIX)
        val focusValidator = viewModel.getDateValidator(FOCUS)
        val rbValidator = viewModel.getDateValidator(REFERENCE_BROWSER)

        val futureDate = LocalDate(2099, 1, 1)
        assertFalse(fenixValidator(futureDate))
        assertFalse(focusValidator(futureDate))
        assertFalse(rbValidator(futureDate))

        val fenixInvalidDate = LocalDate(2021, 12, 20)
        val fenixValidDate = LocalDate(2021, 12, 21)
        assertFalse(fenixValidator(fenixInvalidDate))
        assertTrue(fenixValidator(fenixValidDate))

        val focusInvalidDate = LocalDate(2023, 7, 12)
        val focusValidDate = LocalDate(2023, 7, 13)
        assertFalse(focusValidator(focusInvalidDate))
        assertTrue(focusValidator(focusValidDate))

        val rbValidDate = LocalDate(2022, 1, 1)
        assertTrue(rbValidator(rbValidDate))
    }

    @Test
    fun `TryFox update card is shown when new version is available`() = runTest {
        val fakePackageManager = FakeMozillaPackageManager(
            mapOf(
                "org.mozilla.tryfox" to AppState("TryFox", "org.mozilla.tryfox", "0.0.1", null),
            ),
        )
        val tryFoxParsed =
            createTestParsedNightlyApk(testTryFoxAppName, null, "v0.0.2", "universal")
        val fakeTryFoxReleaseRepository = FakeTryFoxReleaseRepository(NetworkResult.Success(listOf(tryFoxParsed)))
        viewModel = createViewModel(
            releaseRepositories = listOf(fakeTryFoxReleaseRepository),
            mozillaPackageManager = fakePackageManager,
        )

        viewModel.initialLoad()
        advanceUntilIdle()

        val state = viewModel.homeScreenState.value as HomeScreenState.Loaded
        assertNotNull(state.tryfoxApp)
        assertEquals("v0.0.2", (state.tryfoxApp!!.apks as ApksResult.Success).apks.first().version)
    }

    @Test
    fun `TryFox update card is not shown when version is current`() = runTest {
        val fakePackageManager = FakeMozillaPackageManager(
            mapOf(
                "org.mozilla.tryfox" to AppState("TryFox", "org.mozilla.tryfox", "v0.0.2", null),
            ),
        )
        val tryFoxParsed =
            createTestParsedNightlyApk(testTryFoxAppName, null, "v0.0.2", "universal")
        val fakeTryFoxReleaseRepository = FakeTryFoxReleaseRepository(NetworkResult.Success(listOf(tryFoxParsed)))
        viewModel = createViewModel(
            releaseRepositories = listOf(fakeTryFoxReleaseRepository),
            mozillaPackageManager = fakePackageManager,
        )

        viewModel.initialLoad()
        advanceUntilIdle()

        val state = viewModel.homeScreenState.value as HomeScreenState.Loaded
        assertNull(state.tryfoxApp)
    }

    @Test
    fun `dismissTryFoxCard should remove the TryFox app from state`() = runTest {
        val fakePackageManager = FakeMozillaPackageManager(
            mapOf(
                "org.mozilla.tryfox" to AppState("TryFox", "org.mozilla.tryfox", "0.0.1", null),
            ),
        )
        val tryFoxParsed =
            createTestParsedNightlyApk(testTryFoxAppName, null, "v0.0.2", "universal")
        val fakeTryFoxReleaseRepository = FakeTryFoxReleaseRepository(NetworkResult.Success(listOf(tryFoxParsed)))
        viewModel = createViewModel(
            releaseRepositories = listOf(fakeTryFoxReleaseRepository),
            mozillaPackageManager = fakePackageManager,
        )

        viewModel.initialLoad()
        advanceUntilIdle()

        var state = viewModel.homeScreenState.value as HomeScreenState.Loaded
        assertNotNull(state.tryfoxApp)

        viewModel.dismissTryFoxCard()
        advanceUntilIdle()

        state = viewModel.homeScreenState.value as HomeScreenState.Loaded
        assertNull(state.tryfoxApp)
    }

    @Test
    fun `uninstallApp should call intentManager`() {
        val packageName = "org.mozilla.fenix"
        viewModel.uninstallApp(packageName)
        assertTrue(intentManager.wasUninstallApkCalled)
    }

    @Test
    fun `installHomeApk delegates to the PackageInstaller coordinator`() {
        val apk = createTestApkUiModel(
            createTestParsedNightlyApk(testFenixAppName, testDateRaw, testVersion, testAbi),
        )

        viewModel.installHomeApk(apk)

        verify(installCoordinator).install(eq(apk.uniqueKey), eq(File(apk.apkDir, apk.fileName)), isNull())
    }

    @Test
    fun `installApk delegates downloaded files to the PackageInstaller coordinator`() {
        val apk = File(tempCacheDir, "downloaded.apk")

        viewModel.installApk(apk)

        verify(installCoordinator).install(eq(apk.absolutePath), eq(apk), isNull())
    }
}
