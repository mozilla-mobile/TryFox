package org.mozilla.tryfox.ui.screens

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.format
import kotlinx.datetime.format.FormatStringsInDatetimeFormats
import kotlinx.datetime.format.byUnicodePattern
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
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import org.mozilla.tryfox.data.DownloadFileRepository
import org.mozilla.tryfox.data.DownloadState
import org.mozilla.tryfox.data.FakeMozillaArchiveRepository
import org.mozilla.tryfox.data.FakeReferenceBrowserReleaseRepository
import org.mozilla.tryfox.data.FakeTryFoxReleaseRepository
import org.mozilla.tryfox.data.FenixReleaseRepository
import org.mozilla.tryfox.data.FocusReleaseRepository
import org.mozilla.tryfox.data.MozillaPackageManager
import org.mozilla.tryfox.data.NetworkResult
import org.mozilla.tryfox.data.ReleaseRepository
import org.mozilla.tryfox.data.managers.FakeCacheManager
import org.mozilla.tryfox.data.managers.FakeIntentManager
import org.mozilla.tryfox.model.AppState
import org.mozilla.tryfox.model.CacheManagementState
import org.mozilla.tryfox.model.MozillaArchiveApk
import org.mozilla.tryfox.ui.models.AbiUiModel
import org.mozilla.tryfox.ui.models.ApkUiModel
import org.mozilla.tryfox.ui.models.ApksResult
import org.mozilla.tryfox.util.FENIX
import org.mozilla.tryfox.util.FOCUS
import org.mozilla.tryfox.util.REFERENCE_BROWSER
import org.mozilla.tryfox.util.TRYFOX
import java.io.File

@ExperimentalCoroutinesApi
@ExtendWith(MockitoExtension::class)
@MockitoSettings(strictness = Strictness.LENIENT)
@OptIn(FormatStringsInDatetimeFormats::class)
class HomeViewModelTest {

    @JvmField
    @RegisterExtension
    val mainCoroutineRule = MainCoroutineRule()

    private lateinit var viewModel: HomeViewModel
    private lateinit var fakeCacheManager: FakeCacheManager

    @Mock
    private lateinit var downloadFileRepository: DownloadFileRepository
    private val intentManager = FakeIntentManager()

    @TempDir
    lateinit var tempCacheDir: File

    private val testFenixAppName = FENIX
    private val testFocusAppName = FOCUS
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
        val datePart = if (dateFormatted.length >= 10) dateFormatted.substring(0, 10) else ""

        val dirPath = if (datePart.isBlank()) {
            parsed.appName
        } else {
            "${parsed.appName}${File.separator}$datePart"
        }
        val apkDir = File(tempCacheDir, dirPath)

        val uniqueKeyPath = if (datePart.isBlank()) {
            parsed.appName
        } else {
            "${parsed.appName}/$datePart"
        }
        val uniqueKey = "$uniqueKeyPath/${parsed.fileName}"

        return ApkUiModel(
            originalString = parsed.originalString,
            date = dateFormatted,
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

    @BeforeEach
    fun setUp() {
        fakeCacheManager = FakeCacheManager(tempCacheDir)
        viewModel = createViewModel()
    }

    private fun createViewModel(
        releaseRepositories: List<ReleaseRepository> = emptyList(),
        mozillaPackageManager: MozillaPackageManager = FakeMozillaPackageManager(),
    ) = HomeViewModel(
        releaseRepositories = releaseRepositories,
        downloadFileRepository = downloadFileRepository,
        mozillaPackageManager = mozillaPackageManager,
        cacheManager = fakeCacheManager,
        intentManager = intentManager,
        ioDispatcher = mainCoroutineRule.testDispatcher,
        supportedAbis = listOf("arm64-v8a", "x86_64", "armeabi-v7a"),
    )

    private fun String.formatApkDateForTest(): String {
        return try {
            val inputFormat = LocalDateTime.Format { byUnicodePattern("yyyy-MM-dd-HH-mm-ss") }
            val outputFormat = LocalDateTime.Format { byUnicodePattern("yyyy-MM-dd HH:mm") }
            LocalDateTime.parse(this, inputFormat).format(outputFormat)
        } catch (e: Exception) {
            this
        }
    }

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
    fun `initialLoad success should update HomeScreenState to Loaded with data`() = runTest {
        val fenixParsed =
            createTestParsedNightlyApk(testFenixAppName, testDateRaw, testVersion, testAbi)
        val focusParsed =
            createTestParsedNightlyApk(testFocusAppName, testDateRaw, "126.0a1", "x86_64")
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
            FocusReleaseRepository(FakeMozillaArchiveRepository(focusBuilds = NetworkResult.Success(listOf(focusParsed)))),
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
            val fenixCacheSubDir =
                File(tempCacheDir, "${fenixApkUi.appName}/${fenixApkUi.date.take(10)}")
            fenixCacheSubDir.mkdirs()
            File(fenixCacheSubDir, fenixApkUi.fileName).createNewFile()

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
        val fenixCacheActualDir =
            File(tempCacheDir, "${fenixApkUiForCache.appName}/${fenixApkUiForCache.date.take(10)}")
        fenixCacheActualDir.mkdirs()
        val cachedFenixFile = File(fenixCacheActualDir, fenixApkUiForCache.fileName)
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
        val expectedApkDir =
            File(tempCacheDir, "${apkToDownload.appName}/${apkToDownload.date.take(10)}")
        val expectedApkFile = File(expectedApkDir, apkToDownload.fileName)

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

        whenever(
            downloadFileRepository.downloadFile(eq(apkToDownload.url), eq(expectedApkFile), any()),
        ).thenAnswer { invocation ->
            val onProgress = invocation.arguments[2] as (Long, Long) -> Unit
            onProgress(50L, 100L)
            val parentDir = expectedApkFile.parentFile
            if (parentDir != null && !parentDir.exists()) {
                parentDir.mkdirs()
            }
            expectedApkFile.createNewFile()
            NetworkResult.Success(expectedApkFile)
        }

        viewModel.downloadNightlyApk(apkToDownload)
        advanceUntilIdle()

        val loadedState = viewModel.homeScreenState.value as HomeScreenState.Loaded
        val fenixBuildsState = loadedState.apps[FENIX]!!.apks as ApksResult.Success
        val downloadedApkInfo =
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
        assertTrue(intentManager.wasInstallApkCalled)
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
        val expectedApkDir =
            File(tempCacheDir, "${apkToDownload.appName}/${apkToDownload.date.substring(0, 10)}")
        val expectedApkFile = File(expectedApkDir, apkToDownload.fileName)
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

        whenever(
            downloadFileRepository.downloadFile(eq(apkToDownload.url), eq(expectedApkFile), any()),
        ).thenAnswer { NetworkResult.Error(downloadErrorMessage) }

        viewModel.downloadNightlyApk(apkToDownload)
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
}
