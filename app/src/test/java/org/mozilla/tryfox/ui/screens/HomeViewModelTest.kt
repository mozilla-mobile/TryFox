package org.mozilla.tryfox.ui.screens

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.format
import kotlinx.datetime.format.FormatStringsInDatetimeFormats
import kotlinx.datetime.format.byUnicodePattern
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
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
import org.mozilla.tryfox.data.DownloadState
import org.mozilla.tryfox.data.IFenixRepository
import org.mozilla.tryfox.data.MozillaArchiveRepository
import org.mozilla.tryfox.data.MozillaPackageManager
import org.mozilla.tryfox.data.NetworkResult
import org.mozilla.tryfox.data.managers.FakeCacheManager
import org.mozilla.tryfox.model.CacheManagementState
import org.mozilla.tryfox.model.ParsedNightlyApk
import org.mozilla.tryfox.ui.models.AbiUiModel
import org.mozilla.tryfox.ui.models.ApkUiModel
import org.mozilla.tryfox.ui.models.ApksState
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
    private lateinit var mockFenixRepository: IFenixRepository

    @Mock
    private lateinit var mockMozillaArchiveRepository: MozillaArchiveRepository

    @Mock
    private lateinit var mockMozillaPackageManager: MozillaPackageManager

    @TempDir
    lateinit var tempCacheDir: File

    private val testFenixAppName = "fenix"
    private val testFocusAppName = "focus"
    private val testReferenceBrowserAppName = "reference-browser"
    private val testVersion = "125.0a1"
    private val testDateRaw = "2023-11-01-01-01-01"
    private val testAbi = "arm64-v8a"

    // Helper to create ParsedNightlyApk for testing
    private fun createTestParsedNightlyApk(appName: String, dateRaw: String, version: String, abi: String): ParsedNightlyApk {
        val fileName = if (appName == testReferenceBrowserAppName) {
            "target.$abi.apk" // Reference browser has a different file name structure
        } else {
            "$appName-$version.multi.android-$abi.apk"
        }
        val originalString = if (appName == testReferenceBrowserAppName) {
            "$dateRaw-$appName-latest-android-$abi/" // Mimic archive structure for consistency
        } else {
            "$dateRaw-$appName-$version-android-$abi/"
        }
        val fullUrl = if (appName == testReferenceBrowserAppName) {
            "https://firefox-ci-tc.services.mozilla.com/api/index/v1/task/mobile.v2.$appName.nightly.latest.$abi/artifacts/public/target.$abi.apk"
        } else {
            "http://fake.url/$dateRaw-$appName-$version-android-$abi/$fileName"
        }

        return ParsedNightlyApk(
            originalString = originalString,
            rawDateString = dateRaw,
            appName = appName,
            version = if (appName == testReferenceBrowserAppName) "latest" else version,
            abiName = abi,
            fullUrl = fullUrl,
            fileName = fileName
        )
    }

    private fun createTestApkUiModel(parsed: ParsedNightlyApk, downloadState: DownloadState = DownloadState.NotDownloaded): ApkUiModel {
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
            abi = AbiUiModel(parsed.abiName, true), // Assume compatible for tests
            url = parsed.fullUrl,
            fileName = parsed.fileName,
            downloadState = downloadState,
            uniqueKey = uniqueKey,
            apkDir = apkDir
        )
    }

    @BeforeEach
    fun setUp() = runTest { // Wrap setUp in runTest
        // tempCacheDir is now managed by @TempDir
        whenever(mockMozillaPackageManager.fenix).thenReturn(null)
        whenever(mockMozillaPackageManager.focus).thenReturn(null)
        whenever(mockMozillaPackageManager.referenceBrowser).thenReturn(null) // Added for Reference Browser

        // Default empty success for repository calls to avoid nulls if not specifically mocked in a test
        whenever(mockMozillaArchiveRepository.getFenixNightlyBuilds()).thenReturn(NetworkResult.Success(emptyList()))
        whenever(mockMozillaArchiveRepository.getFocusNightlyBuilds()).thenReturn(NetworkResult.Success(emptyList()))
        whenever(mockMozillaArchiveRepository.getReferenceBrowserNightlyBuilds()).thenReturn(NetworkResult.Success(emptyList())) // Added

        fakeCacheManager = FakeCacheManager(tempCacheDir)

        viewModel = HomeViewModel(
            mozillaArchiveRepository = mockMozillaArchiveRepository,
            fenixRepository = mockFenixRepository,
            mozillaPackageManager = mockMozillaPackageManager,
            cacheManager = fakeCacheManager,
            ioDispatcher = mainCoroutineRule.testDispatcher
        )
        viewModel.deviceSupportedAbisForTesting = listOf("arm64-v8a", "x86_64", "armeabi-v7a")
    }

    private fun String.formatApkDateForTest(): String {
        return try {
            val inputFormat = LocalDateTime.Format { byUnicodePattern("yyyy-MM-dd-HH-mm-ss") }
            val outputFormat = LocalDateTime.Format { byUnicodePattern("yyyy-MM-dd HH:mm") }
            LocalDateTime.parse(this, inputFormat).format(outputFormat)
        } catch (e: Exception) {
            this // Return original if parsing fails (e.g. already formatted)
        }
    }

    @AfterEach
    fun tearDown() {
        // tempCacheDir is cleaned up automatically by @TempDir
        fakeCacheManager.reset()
    }

    @Test
    fun `initialLoad when no data then homeScreenState is InitialLoading before load completes`() = runTest {
        assertTrue(viewModel.homeScreenState.value is HomeScreenState.InitialLoading, "Initial HomeScreenState should be InitialLoading")
    }

    @Test
    fun `initialLoad success should update HomeScreenState to Loaded with data`() = runTest {
        val fenixParsed = createTestParsedNightlyApk(testFenixAppName, testDateRaw, testVersion, testAbi)
        val focusParsed = createTestParsedNightlyApk(testFocusAppName, testDateRaw, "126.0a1", "x86_64")
        val rbParsed = createTestParsedNightlyApk(testReferenceBrowserAppName, testDateRaw, "latest", "armeabi-v7a")
        whenever(mockMozillaArchiveRepository.getFenixNightlyBuilds()).thenReturn(NetworkResult.Success(listOf(fenixParsed)))
        whenever(mockMozillaArchiveRepository.getFocusNightlyBuilds()).thenReturn(NetworkResult.Success(listOf(focusParsed)))
        whenever(mockMozillaArchiveRepository.getReferenceBrowserNightlyBuilds()).thenReturn(NetworkResult.Success(listOf(rbParsed))) // Mocked for RB
        fakeCacheManager.setCacheState(CacheManagementState.IdleEmpty) // Start with empty cache

        viewModel.initialLoad()
        advanceUntilIdle()

        val state = viewModel.homeScreenState.value
        assertTrue(state is HomeScreenState.Loaded, "HomeScreenState should be Loaded")
        val loadedState = state as HomeScreenState.Loaded

        assertTrue(loadedState.fenixBuildsState is ApksState.Success, "Fenix builds should be Success")
        assertEquals(1, (loadedState.fenixBuildsState as ApksState.Success).apks.size)
        assertTrue(loadedState.focusBuildsState is ApksState.Success, "Focus builds should be Success")
        assertEquals(1, (loadedState.focusBuildsState as ApksState.Success).apks.size)
        assertTrue(loadedState.referenceBrowserBuildsState is ApksState.Success, "Reference Browser builds should be Success") // Added for RB
        assertEquals(1, (loadedState.referenceBrowserBuildsState as ApksState.Success).apks.size) // Added for RB
        assertEquals(CacheManagementState.IdleEmpty, loadedState.cacheManagementState)
        assertTrue(fakeCacheManager.checkCacheStatusCalled)
    }

    @Test
    fun `initialLoad with empty cache should result in IdleEmpty cache state from CacheManager`() = runTest {
        // Repositories already mocked to return empty lists in setUp
        fakeCacheManager.setCacheState(CacheManagementState.IdleEmpty)

        viewModel.initialLoad()
        advanceUntilIdle()

        val state = viewModel.homeScreenState.value as? HomeScreenState.Loaded
        assertNotNull(state, "State should be Loaded")
        assertEquals(CacheManagementState.IdleEmpty, state!!.cacheManagementState, "Cache state should be IdleEmpty")
        assertTrue(fakeCacheManager.checkCacheStatusCalled)
        // Check that all build states are Success with empty lists
        assertTrue((state.fenixBuildsState as? ApksState.Success)?.apks?.isEmpty() ?: false)
        assertTrue((state.focusBuildsState as? ApksState.Success)?.apks?.isEmpty() ?: false)
        assertTrue((state.referenceBrowserBuildsState as? ApksState.Success)?.apks?.isEmpty() ?: false)
    }

    @Test
    fun `initialLoad with fenix cache populated should result in IdleNonEmpty cache state from CacheManager`() = runTest {
        val fenixParsed = createTestParsedNightlyApk(testFenixAppName, testDateRaw, testVersion, testAbi)
        val fenixApkUi = createTestApkUiModel(fenixParsed)
        val fenixCacheSubDir = File(tempCacheDir, "${fenixApkUi.appName}/${fenixApkUi.date.substring(0, 10)}")
        fenixCacheSubDir.mkdirs()
        File(fenixCacheSubDir, fenixApkUi.fileName).createNewFile()

        whenever(mockMozillaArchiveRepository.getFenixNightlyBuilds()).thenReturn(NetworkResult.Success(listOf(fenixParsed)))
        // Focus and RB will return empty lists by default from setUp
        fakeCacheManager.setCacheState(CacheManagementState.IdleNonEmpty)

        viewModel.initialLoad()
        advanceUntilIdle()

        val state = viewModel.homeScreenState.value as? HomeScreenState.Loaded
        assertNotNull(state, "State should be Loaded")
        assertEquals(CacheManagementState.IdleNonEmpty, state!!.cacheManagementState, "Cache state should be IdleNonEmpty")
        assertTrue(fakeCacheManager.checkCacheStatusCalled)
        val fenixApks = (state.fenixBuildsState as? ApksState.Success)?.apks
        assertTrue(fenixApks?.first()?.downloadState is DownloadState.Downloaded)
    }

    @Test
    fun `clearAppCache should call CacheManager and update states to NotDownloaded`() = runTest {
        val fenixParsed = createTestParsedNightlyApk(testFenixAppName, testDateRaw, testVersion, testAbi)
        val rbParsed = createTestParsedNightlyApk(testReferenceBrowserAppName, testDateRaw, "latest", testAbi)
        
        // Setup cache for Fenix
        val fenixApkUiForCache = createTestApkUiModel(fenixParsed)
        val fenixCacheActualDir = File(tempCacheDir, "${fenixApkUiForCache.appName}/${fenixApkUiForCache.date.substring(0, 10)}")
        fenixCacheActualDir.mkdirs()
        val cachedFenixFile = File(fenixCacheActualDir, fenixApkUiForCache.fileName)
        cachedFenixFile.createNewFile()
        assertTrue(cachedFenixFile.exists(), "Cache file for Fenix should exist before test action")

        // Setup cache for Reference Browser
        val rbApkUiForCache = createTestApkUiModel(rbParsed)
        val rbCacheActualDir = File(tempCacheDir, "${rbApkUiForCache.appName}/${rbApkUiForCache.date.substring(0, 10)}")
        rbCacheActualDir.mkdirs()
        val cachedRbFile = File(rbCacheActualDir, rbApkUiForCache.fileName)
        cachedRbFile.createNewFile()
        assertTrue(cachedRbFile.exists(), "Cache file for RB should exist before test action")

        fakeCacheManager.setCacheState(CacheManagementState.IdleNonEmpty)

        whenever(mockMozillaArchiveRepository.getFenixNightlyBuilds()).thenReturn(NetworkResult.Success(listOf(fenixParsed)))
        whenever(mockMozillaArchiveRepository.getReferenceBrowserNightlyBuilds()).thenReturn(NetworkResult.Success(listOf(rbParsed)))
        // Focus will return empty list by default

        viewModel.initialLoad()
        advanceUntilIdle()

        var loadedState = viewModel.homeScreenState.value as HomeScreenState.Loaded
        // Fenix assertions pre-clear
        val fenixSuccessStatePre = loadedState.fenixBuildsState as ApksState.Success
        assertFalse(fenixSuccessStatePre.apks.isEmpty(), "Fenix APK list should not be empty")
        assertTrue(fenixSuccessStatePre.apks.first().downloadState is DownloadState.Downloaded, "Fenix APK download state should be Downloaded")
        // RB assertions pre-clear
        val rbSuccessStatePre = loadedState.referenceBrowserBuildsState as ApksState.Success
        assertFalse(rbSuccessStatePre.apks.isEmpty(), "RB APK list should not be empty")
        assertTrue(rbSuccessStatePre.apks.first().downloadState is DownloadState.Downloaded, "RB APK download state should be Downloaded")

        assertEquals(CacheManagementState.IdleNonEmpty, loadedState.cacheManagementState, "Cache state should be IdleNonEmpty initially")

        viewModel.clearAppCache()
        advanceUntilIdle()

        assertTrue(fakeCacheManager.clearCacheCalled)
        loadedState = viewModel.homeScreenState.value as HomeScreenState.Loaded
        assertEquals(CacheManagementState.IdleEmpty, loadedState.cacheManagementState, "Cache state should be IdleEmpty after clear")
        
        val fenixStateAfterClear = loadedState.fenixBuildsState as ApksState.Success
        assertFalse(fenixStateAfterClear.apks.isEmpty(), "Fenix APK list should not be empty after clear")
        assertTrue(fenixStateAfterClear.apks.first().downloadState is DownloadState.NotDownloaded, "Fenix APK download state should be NotDownloaded after clear")

        val rbStateAfterClear = loadedState.referenceBrowserBuildsState as ApksState.Success
        assertFalse(rbStateAfterClear.apks.isEmpty(), "RB APK list should not be empty after clear")
        assertTrue(rbStateAfterClear.apks.first().downloadState is DownloadState.NotDownloaded, "RB APK download state should be NotDownloaded after clear")
    }

    @Test
    fun `downloadNightlyApk success sequence`() = runTest {
        val fenixParsed = createTestParsedNightlyApk(testFenixAppName, testDateRaw, testVersion, testAbi)
        val apkToDownload = createTestApkUiModel(fenixParsed, DownloadState.NotDownloaded)
        val expectedApkDir = File(tempCacheDir, "${apkToDownload.appName}/${apkToDownload.date.substring(0, 10)}")
        val expectedApkFile = File(expectedApkDir, apkToDownload.fileName)

        whenever(mockMozillaArchiveRepository.getFenixNightlyBuilds()).thenReturn(NetworkResult.Success(listOf(fenixParsed)))
        // Focus and RB will return empty by default from setUp, ensuring initialLoad completes successfully.
        fakeCacheManager.setCacheState(CacheManagementState.IdleEmpty)

        viewModel.initialLoad()
        advanceUntilIdle()
        // Ensure initial state is loaded before proceeding
        assertTrue(viewModel.homeScreenState.value is HomeScreenState.Loaded)
        val initialLoadedState = viewModel.homeScreenState.value as HomeScreenState.Loaded
        assertTrue(initialLoadedState.fenixBuildsState is ApksState.Success)

        whenever(
            mockFenixRepository.downloadArtifact(eq(apkToDownload.url), eq(expectedApkFile), any())
        ).thenAnswer { invocation ->
            val onProgress = invocation.arguments[2] as (Long, Long) -> Unit
            onProgress(50L, 100L) // Simulate some progress
            val parentDir = expectedApkFile.parentFile
            if (parentDir != null && !parentDir.exists()) { parentDir.mkdirs() }
            expectedApkFile.createNewFile()
            NetworkResult.Success(expectedApkFile)
        }

        var installLambdaCalledWith: File? = null
        viewModel.onInstallApk = { installLambdaCalledWith = it }

        viewModel.downloadNightlyApk(apkToDownload)
        advanceUntilIdle()

        val loadedState = viewModel.homeScreenState.value as HomeScreenState.Loaded
        val fenixBuildsState = loadedState.fenixBuildsState as ApksState.Success
        val downloadedApkInfo = fenixBuildsState.apks.find { it.uniqueKey == apkToDownload.uniqueKey }

        assertNotNull(downloadedApkInfo, "Downloaded APK info should not be null")
        assertTrue(downloadedApkInfo!!.downloadState is DownloadState.Downloaded, "DownloadState should be Downloaded")
        assertEquals(expectedApkFile.path, (downloadedApkInfo.downloadState as DownloadState.Downloaded).file.path)
        assertTrue(fakeCacheManager.checkCacheStatusCalled) 
        assertEquals(expectedApkFile, installLambdaCalledWith)
        assertFalse(loadedState.isDownloadingAnyFile, "isDownloadingAnyFile should be false after success")
    }

    @Test
    fun `downloadNightlyApk failure sequence`() = runTest {
        val fenixParsed = createTestParsedNightlyApk(testFenixAppName, testDateRaw, testVersion, testAbi)
        val apkToDownload = createTestApkUiModel(fenixParsed, DownloadState.NotDownloaded)
        val expectedApkDir = File(tempCacheDir, "${apkToDownload.appName}/${apkToDownload.date.substring(0, 10)}")
        val expectedApkFile = File(expectedApkDir, apkToDownload.fileName)
        val downloadErrorMessage = "Download Canceled"

        whenever(mockMozillaArchiveRepository.getFenixNightlyBuilds()).thenReturn(NetworkResult.Success(listOf(fenixParsed)))
        // Focus and RB will return empty by default from setUp
        fakeCacheManager.setCacheState(CacheManagementState.IdleEmpty)
        viewModel.initialLoad()
        advanceUntilIdle()
        // Ensure initial state is loaded
        assertTrue(viewModel.homeScreenState.value is HomeScreenState.Loaded)
        val initialLoadedState = viewModel.homeScreenState.value as HomeScreenState.Loaded
        assertTrue(initialLoadedState.fenixBuildsState is ApksState.Success)

        whenever(
            mockFenixRepository.downloadArtifact(eq(apkToDownload.url), eq(expectedApkFile), any())
        ).thenAnswer { NetworkResult.Error(downloadErrorMessage) }

        viewModel.downloadNightlyApk(apkToDownload)
        advanceUntilIdle()

        val loadedState = viewModel.homeScreenState.value as HomeScreenState.Loaded
        val fenixBuildsState = loadedState.fenixBuildsState as ApksState.Success
        val failedApkInfo = fenixBuildsState.apks.find { it.uniqueKey == apkToDownload.uniqueKey }

        assertNotNull(failedApkInfo, "Failed APK info should not be null")
        assertTrue(failedApkInfo!!.downloadState is DownloadState.DownloadFailed, "DownloadState should be DownloadFailed")
        assertEquals(downloadErrorMessage, (failedApkInfo.downloadState as DownloadState.DownloadFailed).errorMessage)
        assertTrue(fakeCacheManager.checkCacheStatusCalled) 
        assertFalse(loadedState.isDownloadingAnyFile, "isDownloadingAnyFile should be false after failure")
    }
}
