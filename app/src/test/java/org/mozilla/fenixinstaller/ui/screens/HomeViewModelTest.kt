package org.mozilla.fenixinstaller.ui.screens

import android.content.Context
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.junit.MockitoJUnitRunner
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.verify // Added for verify
import org.mockito.kotlin.whenever // Ensure this specific import is present for whenever
import org.mozilla.fenixinstaller.data.DownloadState
import org.mozilla.fenixinstaller.data.FenixRepository
import org.mozilla.fenixinstaller.data.MozillaArchiveRepository
import org.mozilla.fenixinstaller.data.MozillaPackageManager
import org.mozilla.fenixinstaller.data.NetworkResult
import org.mozilla.fenixinstaller.model.AppState
import org.mozilla.fenixinstaller.model.CacheManagementState
import org.mozilla.fenixinstaller.model.ParsedNightlyApk
import org.mozilla.fenixinstaller.ui.models.AbiUiModel
import org.mozilla.fenixinstaller.ui.models.ApkUiModel
import org.mozilla.fenixinstaller.ui.models.FocusApksState
import java.io.File
import java.nio.file.Files
import java.time.format.DateTimeFormatter

@ExperimentalCoroutinesApi
@RunWith(MockitoJUnitRunner::class)
class HomeViewModelTest {

    @get:Rule
    val mainCoroutineRule = MainCoroutineRule()

    private lateinit var viewModel: HomeViewModel

    @Mock
    private lateinit var mockContext: Context

    @Mock
    private lateinit var mockFenixRepository: FenixRepository // Still used for downloads

    @Mock
    private lateinit var mockMozillaArchiveRepository: MozillaArchiveRepository

    @Mock
    private lateinit var mockMozillaPackageManager: MozillaPackageManager

    private lateinit var tempCacheDir: File

    private val testFenixAppName = "fenix"
    private val testFocusAppName = "focus"
    private val testVersion = "125.0a1"
    private val testDateRaw = "2023-11-01-01-01-01"
    private val testDateFormatted = testDateRaw.formatApkDateForTest()
    private val testAbi = "arm64-v8a"

    private fun createTestParsedNightlyApk(appName: String, dateRaw: String, version: String, abi: String): ParsedNightlyApk {
        return ParsedNightlyApk(
            originalString = "$dateRaw-$appName-$version-android-$abi/",
            rawDateString = dateRaw,
            appName = appName,
            version = version,
            abiName = abi,
            fullUrl = "http://fake.url/$dateRaw-$appName-$version-android-$abi/$appName-$version.multi.android-$abi.apk",
            fileName = "$appName-$version.multi.android-$abi.apk"
        )
    }

    private fun createTestApkUiModel(parsed: ParsedNightlyApk, downloadState: DownloadState = DownloadState.NotDownloaded): ApkUiModel {
        val dateFormatted = parsed.rawDateString.formatApkDateForTest()
        return ApkUiModel(
            originalString = parsed.originalString,
            date = dateFormatted,
            appName = parsed.appName,
            version = parsed.version,
            abi = AbiUiModel(parsed.abiName, true), // Assume compatible for tests unless specified
            url = parsed.fullUrl,
            fileName = parsed.fileName,
            downloadState = downloadState,
            uniqueKey = "${parsed.appName}/${dateFormatted.substring(0, 10)}/${parsed.fileName}"
        )
    }

    @Before
    fun setUp() {
        tempCacheDir = Files.createTempDirectory("testCache").toFile()
        whenever(mockContext.cacheDir).thenReturn(tempCacheDir)
        whenever(mockMozillaPackageManager.fenix).thenReturn(null)
        whenever(mockMozillaPackageManager.focus).thenReturn(null)
        
        // Initialize viewModel here for tests that need it setup beforehand or rely on its init block if any
        // Most tests will call initialLoad which triggers internal state changes and repo calls
        viewModel = HomeViewModel(
            mozillaArchiveRepository = mockMozillaArchiveRepository,
            fenixRepository = mockFenixRepository,
            mozillaPackageManager = mockMozillaPackageManager,
            ioDispatcher = mainCoroutineRule.testDispatcher
        )
        viewModel.deviceSupportedAbisForTesting = listOf("arm64-v8a", "x86_64", "armeabi-v7a") // Set for consistent ABI compatibility
    }

    private fun String.formatApkDateForTest(): String {
        val inputFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd-HH-mm-ss")
        val outputFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
        return java.time.LocalDateTime.parse(this, inputFormatter).format(outputFormatter)
    }

    @After
    fun tearDown() {
        if (::tempCacheDir.isInitialized && tempCacheDir.exists()) {
            tempCacheDir.deleteRecursively()
        }
    }

    @Test
    fun `initialLoad when no data then homeScreenState is InitialLoading`() = runTest {
        // Viewmodel is already initialized in setUp
        assertTrue("Initial HomeScreenState should be InitialLoading", viewModel.homeScreenState.value is HomeScreenState.InitialLoading)
    }

    @Test
    fun `initialLoad success should update HomeScreenState to Loaded with data`() = runTest {
        val fenixParsed = createTestParsedNightlyApk(testFenixAppName, testDateRaw, testVersion, testAbi)
        val focusParsed = createTestParsedNightlyApk(testFocusAppName, testDateRaw, "126.0a1", "x86_64")
        whenever(mockMozillaArchiveRepository.getFenixNightlyBuilds()).thenReturn(NetworkResult.Success(listOf(fenixParsed)))
        whenever(mockMozillaArchiveRepository.getFocusNightlyBuilds()).thenReturn(NetworkResult.Success(listOf(focusParsed)))
        
        viewModel.initialLoad(mockContext)
        advanceUntilIdle()

        val state = viewModel.homeScreenState.value
        assertTrue("HomeScreenState should be Loaded", state is HomeScreenState.Loaded)
        val loadedState = state as HomeScreenState.Loaded

        assertTrue("Fenix builds should be Success", loadedState.fenixBuildsState is FocusApksState.Success)
        assertEquals(1, (loadedState.fenixBuildsState as FocusApksState.Success).apks.size)
        assertTrue("Focus builds should be Success", loadedState.focusBuildsState is FocusApksState.Success)
        assertEquals(1, (loadedState.focusBuildsState as FocusApksState.Success).apks.size)
        assertEquals(CacheManagementState.IdleEmpty, loadedState.cacheManagementState) // No files initially
    }
    
    @Test
    fun `initialLoad with empty cache should result in IdleEmpty cache state`() = runTest {
        whenever(mockMozillaArchiveRepository.getFenixNightlyBuilds()).thenReturn(NetworkResult.Success(emptyList()))
        whenever(mockMozillaArchiveRepository.getFocusNightlyBuilds()).thenReturn(NetworkResult.Success(emptyList()))

        viewModel.initialLoad(mockContext)
        advanceUntilIdle()

        val state = viewModel.homeScreenState.value as? HomeScreenState.Loaded
        assertNotNull("State should be Loaded", state)
        assertEquals("Cache state should be IdleEmpty when cache is empty", CacheManagementState.IdleEmpty, state!!.cacheManagementState)
    }

    @Test
    fun `initialLoad with fenix cache populated should result in IdleNonEmpty cache state`() = runTest {
        val fenixParsed = createTestParsedNightlyApk(testFenixAppName, testDateRaw, testVersion, testAbi)
        val fenixApkUi = createTestApkUiModel(fenixParsed)
        val fenixCacheDir = File(tempCacheDir, "${fenixApkUi.appName}/${fenixApkUi.date.substring(0, 10)}")
        fenixCacheDir.mkdirs()
        File(fenixCacheDir, fenixApkUi.fileName).createNewFile()

        whenever(mockMozillaArchiveRepository.getFenixNightlyBuilds()).thenReturn(NetworkResult.Success(listOf(fenixParsed)))
        whenever(mockMozillaArchiveRepository.getFocusNightlyBuilds()).thenReturn(NetworkResult.Success(emptyList()))

        viewModel.initialLoad(mockContext)
        advanceUntilIdle()

        val state = viewModel.homeScreenState.value as? HomeScreenState.Loaded
        assertNotNull("State should be Loaded", state)
        assertEquals("Cache state should be IdleNonEmpty", CacheManagementState.IdleNonEmpty, state!!.cacheManagementState)
    }

    @Test
    fun `clearAppCache should clear cache and update states to NotDownloaded and IdleEmpty`() = runTest {
        val fenixParsed = createTestParsedNightlyApk(testFenixAppName, testDateRaw, testVersion, testAbi)
        val fenixApkUi = createTestApkUiModel(fenixParsed)
        val fenixCacheDir = File(tempCacheDir, "${fenixApkUi.appName}/${fenixApkUi.date.substring(0, 10)}")
        fenixCacheDir.mkdirs()
        File(fenixCacheDir, fenixApkUi.fileName).createNewFile().also { assertTrue(it) }
        assertTrue(File(fenixCacheDir, fenixApkUi.fileName).exists())

        whenever(mockMozillaArchiveRepository.getFenixNightlyBuilds()).thenReturn(NetworkResult.Success(listOf(fenixParsed)))
        whenever(mockMozillaArchiveRepository.getFocusNightlyBuilds()).thenReturn(NetworkResult.Success(emptyList()))

        viewModel.initialLoad(mockContext) // Load with populated cache
        advanceUntilIdle()

        var loadedState = viewModel.homeScreenState.value as HomeScreenState.Loaded
        assertEquals(CacheManagementState.IdleNonEmpty, loadedState.cacheManagementState)
        assertTrue((loadedState.fenixBuildsState as FocusApksState.Success).apks.first().downloadState is DownloadState.Downloaded)

        viewModel.clearAppCache(mockContext)
        advanceUntilIdle()

        loadedState = viewModel.homeScreenState.value as HomeScreenState.Loaded
        assertEquals(CacheManagementState.IdleEmpty, loadedState.cacheManagementState)
        assertFalse("Fenix cache subdirectory should be deleted", fenixCacheDir.exists())
        assertTrue((loadedState.fenixBuildsState as FocusApksState.Success).apks.first().downloadState is DownloadState.NotDownloaded)
    }

    @Test
    fun `fetchLatestFenixNightlyBuilds success should update FenixBuildsState`() = runTest {
        val fenixParsed = createTestParsedNightlyApk(testFenixAppName, testDateRaw, testVersion, testAbi)
        val mockFenixAppState = AppState(name = testFenixAppName, packageName = "org.mozilla.fenix", version = "123", installDateMillis = 0L)

        whenever(mockMozillaArchiveRepository.getFenixNightlyBuilds()).thenReturn(NetworkResult.Success(listOf(fenixParsed)))
        whenever(mockMozillaPackageManager.fenix).thenReturn(mockFenixAppState)
        // Initial load to set up the Loaded state, focus can be empty
        whenever(mockMozillaArchiveRepository.getFocusNightlyBuilds()).thenReturn(NetworkResult.Success(emptyList()))
        viewModel.initialLoad(mockContext)
        advanceUntilIdle()

        // Trigger the specific fetch we want to test
        viewModel.fetchLatestFenixNightlyBuilds(mockContext)
        advanceUntilIdle()

        val state = viewModel.homeScreenState.value as HomeScreenState.Loaded
        assertTrue("FenixBuildsState should be Success", state.fenixBuildsState is FocusApksState.Success)
        val fenixApks = (state.fenixBuildsState as FocusApksState.Success).apks
        assertEquals(1, fenixApks.size)
        assertEquals(testFenixAppName, fenixApks.first().appName)
        assertEquals(mockFenixAppState, state.fenixAppInfo)
    }

    @Test
    fun `fetchLatestFenixNightlyBuilds error should update FenixBuildsState to Error`() = runTest {
        val errorMessage = "Network failed horribly for Fenix"
        val mockFenixAppState = AppState(name = testFenixAppName, packageName = "org.mozilla.fenix", version = "123", installDateMillis = 0L)

        whenever(mockMozillaArchiveRepository.getFenixNightlyBuilds()).thenReturn(NetworkResult.Error(errorMessage))
        whenever(mockMozillaPackageManager.fenix).thenReturn(mockFenixAppState)
        // Initial load to set up the Loaded state, focus can be empty
        whenever(mockMozillaArchiveRepository.getFocusNightlyBuilds()).thenReturn(NetworkResult.Success(emptyList()))
        viewModel.initialLoad(mockContext)
        advanceUntilIdle()

        // Trigger the specific fetch
        viewModel.fetchLatestFenixNightlyBuilds(mockContext)
        advanceUntilIdle()

        val state = viewModel.homeScreenState.value as HomeScreenState.Loaded
        assertTrue("FenixBuildsState should be Error", state.fenixBuildsState is FocusApksState.Error)
        assertEquals("Error fetching Fenix nightly builds: $errorMessage", (state.fenixBuildsState as FocusApksState.Error).message)
        assertEquals(mockFenixAppState, state.fenixAppInfo)
    }
    
    @Test
    fun `downloadNightlyApk success sequence`() = runTest {
        val fenixParsed = createTestParsedNightlyApk(testFenixAppName, testDateRaw, testVersion, testAbi)
        val apkToDownload = createTestApkUiModel(fenixParsed, DownloadState.NotDownloaded)
        val expectedApkDir = File(tempCacheDir, "${apkToDownload.appName}/${apkToDownload.date.substring(0, 10)}")
        val expectedApkFile = File(expectedApkDir, apkToDownload.fileName)

        // Setup for initialLoad to get the APK into the list
        whenever(mockMozillaArchiveRepository.getFenixNightlyBuilds()).thenReturn(NetworkResult.Success(listOf(fenixParsed)))
        whenever(mockMozillaArchiveRepository.getFocusNightlyBuilds()).thenReturn(NetworkResult.Success(emptyList()))
        viewModel.initialLoad(mockContext)
        advanceUntilIdle()

        var capturedProgress: Float? = null
        var onProgressCalledCount = 0
        whenever(
            mockFenixRepository.downloadArtifact(
                eq(apkToDownload.url),
                eq(expectedApkFile),
                any()
            )
        ).thenAnswer { invocation ->
            @Suppress("UNCHECKED_CAST")
            val onProgress = invocation.arguments[2] as (Long, Long) -> Unit
            onProgress(0, 100) // Initial progress
            onProgressCalledCount++
            onProgress(50, 100) 
            onProgressCalledCount++
            capturedProgress = 0.5f
            expectedApkFile.parentFile.mkdirs()
            expectedApkFile.createNewFile()
            NetworkResult.Success(expectedApkFile)
        }

        var installLambdaCalledWith: File? = null
        viewModel.onInstallApk = { installLambdaCalledWith = it }

        viewModel.downloadNightlyApk(apkToDownload, mockContext)
        advanceUntilIdle()

        val loadedState = viewModel.homeScreenState.value as HomeScreenState.Loaded
        val fenixBuildsState = loadedState.fenixBuildsState as FocusApksState.Success
        val downloadedApkInfo = fenixBuildsState.apks.find { it.uniqueKey == apkToDownload.uniqueKey }

        assertNotNull("Downloaded APK info should not be null", downloadedApkInfo)
        assertTrue("onProgress should have been called at least for progress update", onProgressCalledCount >= 1)
        assertEquals("Final progress should have been 0.5f", 0.5f, capturedProgress)
        assertTrue("DownloadState should be Downloaded", downloadedApkInfo!!.downloadState is DownloadState.Downloaded)
        assertEquals(expectedApkFile.path, (downloadedApkInfo.downloadState as DownloadState.Downloaded).file.path)
        assertTrue("Cache file should exist", expectedApkFile.exists())
        assertEquals(CacheManagementState.IdleNonEmpty, loadedState.cacheManagementState)
        assertEquals("onInstallApk should be called with the correct file", expectedApkFile, installLambdaCalledWith)
        assertTrue("isDownloadingAnyFile should be false after success", !loadedState.isDownloadingAnyFile)
    }

    @Test
    fun `downloadNightlyApk failure sequence`() = runTest {
        val fenixParsed = createTestParsedNightlyApk(testFenixAppName, testDateRaw, testVersion, testAbi)
        val apkToDownload = createTestApkUiModel(fenixParsed, DownloadState.NotDownloaded)
        val expectedApkDir = File(tempCacheDir, "${apkToDownload.appName}/${apkToDownload.date.substring(0, 10)}")
        val expectedApkFile = File(expectedApkDir, apkToDownload.fileName)
        val downloadErrorMessage = "Download Canceled"

        whenever(mockMozillaArchiveRepository.getFenixNightlyBuilds()).thenReturn(NetworkResult.Success(listOf(fenixParsed)))
        whenever(mockMozillaArchiveRepository.getFocusNightlyBuilds()).thenReturn(NetworkResult.Success(emptyList()))
        viewModel.initialLoad(mockContext)
        advanceUntilIdle()

        whenever(
            mockFenixRepository.downloadArtifact(
                eq(apkToDownload.url),
                eq(expectedApkFile),
                any()
            )
        ).thenAnswer { invocation ->
             @Suppress("UNCHECKED_CAST")
            val onProgress = invocation.arguments[2] as (Long, Long) -> Unit
            onProgress(20,100)
            NetworkResult.Error(downloadErrorMessage)
        }

        viewModel.downloadNightlyApk(apkToDownload, mockContext)
        advanceUntilIdle()

        val loadedState = viewModel.homeScreenState.value as HomeScreenState.Loaded
        val fenixBuildsState = loadedState.fenixBuildsState as FocusApksState.Success
        val failedApkInfo = fenixBuildsState.apks.find { it.uniqueKey == apkToDownload.uniqueKey }

        assertNotNull("Failed APK info should not be null", failedApkInfo)
        assertTrue("DownloadState should be DownloadFailed", failedApkInfo!!.downloadState is DownloadState.DownloadFailed)
        assertEquals(downloadErrorMessage, (failedApkInfo.downloadState as DownloadState.DownloadFailed).errorMessage)
        assertFalse("Cache file should not exist after failed download", expectedApkFile.exists())
        // Cache state will be IdleEmpty if no other files were present. If others, IdleNonEmpty.
        // Assuming this test focuses on a clean cache dir, so check for IdleEmpty.
        assertEquals(CacheManagementState.IdleEmpty, loadedState.cacheManagementState)
         assertTrue("isDownloadingAnyFile should be false after failure", !loadedState.isDownloadingAnyFile)
    }
}
