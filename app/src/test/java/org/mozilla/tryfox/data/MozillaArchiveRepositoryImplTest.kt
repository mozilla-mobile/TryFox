package org.mozilla.tryfox.data

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.junit.MockitoJUnitRunner
import org.mockito.kotlin.whenever
import org.mozilla.tryfox.model.ParsedNightlyApk
import org.mozilla.tryfox.network.ApiService

@ExperimentalCoroutinesApi
@RunWith(MockitoJUnitRunner::class)
class MozillaArchiveRepositoryImplTest {

    @Mock
    private lateinit var mockApiService: ApiService

    private lateinit var repository: MozillaArchiveRepositoryImpl

    // Test constants for Fenix
    private val fenixAppName = "fenix"
    private val fenixVersion = "125.0a1"
    private val fenixDate1 = "2023-11-01-10-00-00"
    private val fenixAbi1 = "arm64-v8a"
    private val fenixFileName1 = "$fenixAppName-$fenixVersion.multi.android-$fenixAbi1.apk"
    private val fenixDirString1 = "$fenixDate1-$fenixAppName-$fenixVersion-android-$fenixAbi1/"
    private val fenixFullUrl1 = "${MozillaArchiveRepositoryImpl.fenixArchiveUrl}$fenixDirString1$fenixFileName1"

    private val fenixDate2 = "2023-11-01-10-00-00" // Same date, different ABI
    private val fenixAbi2 = "x86_64"
    private val fenixFileName2 = "$fenixAppName-$fenixVersion.multi.android-$fenixAbi2.apk"
    private val fenixDirString2 = "$fenixDate2-$fenixAppName-$fenixVersion-android-$fenixAbi2/"
    private val fenixFullUrl2 = "${MozillaArchiveRepositoryImpl.fenixArchiveUrl}$fenixDirString2$fenixFileName2"

    // Test constants for Focus
    private val focusAppName = "focus"
    private val focusVersion = "110.0a1"
    private val focusDate = "2023-10-30-05-30-00"
    private val focusAbi = "armeabi-v7a"
    private val focusFileName = "$focusAppName-$focusVersion.multi.android-$focusAbi.apk"
    private val focusDirString = "$focusDate-$focusAppName-$focusVersion-android-$focusAbi/"
    private val focusFullUrl = "${MozillaArchiveRepositoryImpl.focusArchiveUrl}$focusDirString$focusFileName"

    @Before
    fun setUp() {
        repository = MozillaArchiveRepositoryImpl(mockApiService)
    }

    private fun createMockHtmlResponse(vararg dirStrings: String): String {
        return dirStrings.joinToString(separator = "\n") {
            "<td>Dir</td><td><a href=\"$it\">$it</a></td>"
        }
    }

    @Test
    fun `getFenixNightlyBuilds success - single latest build`() = runTest {
        val mockHtml = createMockHtmlResponse(fenixDirString1)
        whenever(mockApiService.getHtmlPage(MozillaArchiveRepositoryImpl.fenixArchiveUrl)).thenReturn(mockHtml)

        val result = repository.getFenixNightlyBuilds()

        assertTrue(result is NetworkResult.Success)
        val apks = (result as NetworkResult.Success).data
        assertEquals(1, apks.size)
        val apk = apks[0]
        assertEquals(fenixDirString1, apk.originalString)
        assertEquals(fenixDate1, apk.rawDateString)
        assertEquals(fenixAppName, apk.appName)
        assertEquals(fenixVersion, apk.version)
        assertEquals(fenixAbi1, apk.abiName)
        assertEquals(fenixFullUrl1, apk.fullUrl)
        assertEquals(fenixFileName1, apk.fileName)
    }

    @Test
    fun `getFenixNightlyBuilds success - multiple builds on latest date`() = runTest {
        val olderDateDir = "2023-10-31-00-00-00-$fenixAppName-$fenixVersion-android-$fenixAbi1/"
        val mockHtml = createMockHtmlResponse(fenixDirString1, fenixDirString2, olderDateDir)
        whenever(mockApiService.getHtmlPage(MozillaArchiveRepositoryImpl.fenixArchiveUrl)).thenReturn(mockHtml)

        val result = repository.getFenixNightlyBuilds()

        assertTrue(result is NetworkResult.Success)
        val apks = (result as NetworkResult.Success).data
        assertEquals(2, apks.size)

        val expectedApks = listOf(
            ParsedNightlyApk(fenixDirString1, fenixDate1, fenixAppName, fenixVersion, fenixAbi1, fenixFullUrl1, fenixFileName1),
            ParsedNightlyApk(fenixDirString2, fenixDate2, fenixAppName, fenixVersion, fenixAbi2, fenixFullUrl2, fenixFileName2)
        )
        assertTrue(apks.containsAll(expectedApks) && expectedApks.containsAll(apks))
    }

     @Test
    fun `getFenixNightlyBuilds success - sorts by date correctly`() = runTest {
        val olderDateDir = "2023-10-31-23-59-59-$fenixAppName-$fenixVersion-android-$fenixAbi1/"
        val latestDateDir = fenixDirString1
        val middleDateDir = "2023-11-01-09-00-00-$fenixAppName-$fenixVersion-android-x86/"

        val mockHtml = createMockHtmlResponse(olderDateDir, latestDateDir, middleDateDir)
        whenever(mockApiService.getHtmlPage(MozillaArchiveRepositoryImpl.fenixArchiveUrl)).thenReturn(mockHtml)

        val result = repository.getFenixNightlyBuilds()
        assertTrue(result is NetworkResult.Success)
        val apks = (result as NetworkResult.Success).data
        assertEquals(1, apks.size)
        assertEquals(latestDateDir, apks[0].originalString)
    }

    @Test
    fun `getFenixNightlyBuilds success - no builds found`() = runTest {
        val mockHtml = "<td>Some other HTML</td>"
        whenever(mockApiService.getHtmlPage(MozillaArchiveRepositoryImpl.fenixArchiveUrl)).thenReturn(mockHtml)

        val result = repository.getFenixNightlyBuilds()

        assertTrue(result is NetworkResult.Success)
        assertTrue((result as NetworkResult.Success).data.isEmpty())
    }

    @Test
    fun `getFenixNightlyBuilds network error`() = runTest {
        val errorMessage = "Network error"
        whenever(mockApiService.getHtmlPage(MozillaArchiveRepositoryImpl.fenixArchiveUrl)).thenThrow(RuntimeException(errorMessage))

        val result = repository.getFenixNightlyBuilds()

        assertTrue(result is NetworkResult.Error)
        assertEquals("Failed to fetch or parse fenix builds: $errorMessage", (result as NetworkResult.Error).message)
    }

    @Test
    fun `getFocusNightlyBuilds success - single latest build`() = runTest {
        val mockHtml = createMockHtmlResponse(focusDirString)
        whenever(mockApiService.getHtmlPage(MozillaArchiveRepositoryImpl.focusArchiveUrl)).thenReturn(mockHtml)

        val result = repository.getFocusNightlyBuilds()

        assertTrue(result is NetworkResult.Success)
        val apks = (result as NetworkResult.Success).data
        assertEquals(1, apks.size)
        val apk = apks[0]
        assertEquals(focusDirString, apk.originalString)
        assertEquals(focusDate, apk.rawDateString)
        assertEquals(focusAppName, apk.appName)
        assertEquals(focusVersion, apk.version)
        assertEquals(focusAbi, apk.abiName)
        assertEquals(focusFullUrl, apk.fullUrl)
        assertEquals(focusFileName, apk.fileName)
    }

    @Test
    fun `getFocusNightlyBuilds network error`() = runTest {
        val errorMessage = "Network error for Focus"
        whenever(mockApiService.getHtmlPage(MozillaArchiveRepositoryImpl.focusArchiveUrl)).thenThrow(RuntimeException(errorMessage))

        val result = repository.getFocusNightlyBuilds()

        assertTrue(result is NetworkResult.Error)
        assertEquals("Failed to fetch or parse focus builds: $errorMessage", (result as NetworkResult.Error).message)
    }
}
