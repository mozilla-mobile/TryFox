package org.mozilla.tryfox

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.mozilla.tryfox.data.MozillaArchiveRepositoryImpl
import org.mozilla.tryfox.data.MozillaArchiveHtmlParser
import org.mozilla.tryfox.data.NetworkResult
import org.mozilla.tryfox.data.ReleaseType
import org.mozilla.tryfox.model.MozillaArchiveApk
import org.mozilla.tryfox.network.MozillaArchivesApiService

class FenixReleaseTest {

    private val parser = MozillaArchiveHtmlParser()

    // Tests for getFenixReleaseBuilds

    @Test
    fun `test getFenixReleaseBuilds returns list of APKs for beta release`() = runBlocking {
        val mockApiService: MozillaArchivesApiService = mock()
        val releasesListHtml = loadHtmlResource("fenix-releases-page.html")
        val releaseDetailsHtml = loadHtmlResource("fenix-releases-145.html")

        whenever(mockApiService.getHtmlPage(MozillaArchiveRepositoryImpl.RELEASES_FENIX_BASE_URL))
            .thenReturn(releasesListHtml)
        whenever(mockApiService.getHtmlPage(MozillaArchiveRepositoryImpl.archiveUrlForRelease("146.0b5")))
            .thenReturn(releaseDetailsHtml)

        val repository = MozillaArchiveRepositoryImpl(mockApiService)
        val result = repository.getFenixReleaseBuilds(ReleaseType.Beta)

        assertTrue(result is NetworkResult.Success)
        if (result is NetworkResult.Success) {
            println("Fenix Release APKs: ${result.data}")
            assertEquals(4, result.data.size, "Should have 4 ABIs")
            assertTrue(result.data.any { it.abiName == "arm64-v8a" })
            assertTrue(result.data.any { it.abiName == "universal" })
        }
    }

    @Test
    fun `test getFenixReleaseBuilds returns list of APKs for stable release`() = runBlocking {
        val mockApiService: MozillaArchivesApiService = mock()
        val releasesListHtml = loadHtmlResource("fenix-releases-page.html")
        val releaseDetailsHtml = loadHtmlResource("fenix-releases-145.html")

        whenever(mockApiService.getHtmlPage(MozillaArchiveRepositoryImpl.RELEASES_FENIX_BASE_URL))
            .thenReturn(releasesListHtml)
        whenever(mockApiService.getHtmlPage(MozillaArchiveRepositoryImpl.archiveUrlForRelease("145.0.1")))
            .thenReturn(releaseDetailsHtml)

        val repository = MozillaArchiveRepositoryImpl(mockApiService)
        val result = repository.getFenixReleaseBuilds(ReleaseType.Release)

        assertTrue(result is NetworkResult.Success)
        if (result is NetworkResult.Success) {
            println("Fenix Stable Release APKs: ${result.data}")
            assertEquals(4, result.data.size, "Should have 4 ABIs")
            assertTrue(result.data.any { it.version == "145.0.1" })
        }
    }

    @Test
    fun `test constructed release APKs have correct URLs`() = runBlocking {
        val mockApiService: MozillaArchivesApiService = mock()
        val releasesListHtml = loadHtmlResource("fenix-releases-page.html")
        val releaseDetailsHtml = loadHtmlResource("fenix-releases-145.html")

        whenever(mockApiService.getHtmlPage(MozillaArchiveRepositoryImpl.RELEASES_FENIX_BASE_URL))
            .thenReturn(releasesListHtml)
        whenever(mockApiService.getHtmlPage(MozillaArchiveRepositoryImpl.archiveUrlForRelease("146.0b5")))
            .thenReturn(releaseDetailsHtml)

        val repository = MozillaArchiveRepositoryImpl(mockApiService)
        val result = repository.getFenixReleaseBuilds(ReleaseType.Beta)

        assertTrue(result is NetworkResult.Success)
        if (result is NetworkResult.Success) {
            val arm64Apk = result.data.find { it.abiName == "arm64-v8a" }
            assertNotNull(arm64Apk)
            assertTrue(arm64Apk!!.fullUrl.contains("fenix-146.0b5-android-arm64-v8a"))
            assertTrue(arm64Apk.fullUrl.contains("fenix-146.0b5.multi.android-arm64-v8a.apk"))
        }
    }

    // Tests for parseFenixReleasesFromHtml

    @Test
    fun `test parseFenixReleasesFromHtml with releases-page html returns latest version for both Beta and Release types`() {
        val htmlContent = loadHtmlResource("fenix-releases-page.html")

        // Test with ReleaseType.Beta - should return latest beta only
        val resultBeta = parser.parseFenixReleasesFromHtml(htmlContent, ReleaseType.Beta)
        println("Latest Fenix Beta Release: $resultBeta")
        assertEquals("146.0b5", resultBeta)

        // Test with ReleaseType.Release - should return latest stable release only
        val resultRelease = parser.parseFenixReleasesFromHtml(htmlContent, ReleaseType.Release)
        println("Latest stable Fenix Release: $resultRelease")
        assertEquals("145.0.1", resultRelease)
    }

    // Tests for parseFenixReleaseAbisFromHtml

    @Test
    fun `test parseFenixReleaseAbisFromHtml extracts all ABIs from release HTML`() {
        val htmlContent = loadHtmlResource("fenix-releases-145.html")
        
        val result = parser.parseFenixReleaseAbisFromHtml(htmlContent, "fenix")
        
        println("Extracted ABIs: $result")
        assertEquals(4, result.size, "Should extract 4 ABIs")
        assertTrue(result.contains("arm64-v8a"), "Should contain arm64-v8a")
        assertTrue(result.contains("armeabi-v7a"), "Should contain armeabi-v7a")
        assertTrue(result.contains("x86_64"), "Should contain x86_64")
        assertTrue(result.contains("universal"), "Should contain universal (no ABI suffix)")
    }

    @Test
    fun `test parseFenixReleaseAbisFromHtml extracts in correct order`() {
        val htmlContent = loadHtmlResource("fenix-releases-145.html")
        
        val result = parser.parseFenixReleaseAbisFromHtml(htmlContent, "fenix")
        
        // The order should match the order in the HTML
        val expected = listOf("arm64-v8a", "armeabi-v7a", "x86_64", "universal")
        assertEquals(expected, result, "ABIs should be in the order they appear in HTML")
    }

    @Test
    fun `test parseFenixReleaseAbisFromHtml with different app name`() {
        // This test demonstrates the function works with different app names
        val htmlContent = loadHtmlResource("fenix-releases-145.html")
        
        // Using "fenix" app name (correct one)
        val result = parser.parseFenixReleaseAbisFromHtml(htmlContent, "fenix")
        assertEquals(4, result.size, "Should extract 4 ABIs with correct app name")
        
        // Using "focus" app name (shouldn't match fenix entries)
        val resultFocus = parser.parseFenixReleaseAbisFromHtml(htmlContent, "focus")
        assertEquals(0, resultFocus.size, "Should extract 0 ABIs with wrong app name")
    }

    @Test
    fun `test parseFenixReleaseAbisFromHtml empty HTML returns empty list`() {
        val htmlContent = "<html><body></body></html>"
        
        val result = parser.parseFenixReleaseAbisFromHtml(htmlContent, "fenix")
        
        assertEquals(0, result.size, "Should return empty list for HTML with no matching entries")
    }

    @Test
    fun `test parseFenixReleaseAbisFromHtml identifies universal ABI correctly`() {
        val htmlContent = loadHtmlResource("fenix-releases-145.html")
        
        val result = parser.parseFenixReleaseAbisFromHtml(htmlContent, "fenix")
        
        // The last entry should be "universal" (fenix-145.0-android/)
        assertTrue(result.last() == "universal", "Last ABI should be universal")
    }

    @Test
    fun `test parseFenixReleaseAbisFromHtml extracts all ABIs from beta release HTML`() {
        val htmlContent = loadHtmlResource("fenix-releases-146b5.html")
        
        val result = parser.parseFenixReleaseAbisFromHtml(htmlContent, "fenix")
        
        println("Extracted ABIs from beta release: $result")
        assertEquals(4, result.size, "Should extract 4 ABIs from beta release")
        assertTrue(result.contains("arm64-v8a"), "Should contain arm64-v8a")
        assertTrue(result.contains("armeabi-v7a"), "Should contain armeabi-v7a")
        assertTrue(result.contains("x86_64"), "Should contain x86_64")
        assertTrue(result.contains("universal"), "Should contain universal (no ABI suffix)")
    }

    @Test
    fun `test parseFenixReleaseAbisFromHtml handles beta markers correctly`() {
        val htmlContent = loadHtmlResource("fenix-releases-146b5.html")
        
        val result = parser.parseFenixReleaseAbisFromHtml(htmlContent, "fenix")
        
        // Verify the order matches the HTML
        val expected = listOf("arm64-v8a", "armeabi-v7a", "x86_64", "universal")
        assertEquals(expected, result, "ABIs should be extracted correctly from beta release with version markers like 146.0b5")
    }

    @Test
    fun `test parseFenixReleaseAbisFromHtml identifies universal ABI in beta release`() {
        val htmlContent = loadHtmlResource("fenix-releases-146b5.html")
        
        val result = parser.parseFenixReleaseAbisFromHtml(htmlContent, "fenix")
        
        // The last entry should be "universal" (fenix-146.0b5-android/)
        assertTrue(result.last() == "universal", "Last ABI in beta release should be universal")
    }

    @Test
    fun `test parseFenixReleaseAbisFromHtml extracts specific ABI from beta pattern`() {
        val htmlContent = loadHtmlResource("fenix-releases-146b5.html")
        
        val result = parser.parseFenixReleaseAbisFromHtml(htmlContent, "fenix")
        
        // Verify specific ABIs are correctly extracted from beta version format
        assertTrue(result.contains("arm64-v8a"), "fenix-146.0b5-android-arm64-v8a/ should extract arm64-v8a")
        assertTrue(result.contains("armeabi-v7a"), "fenix-146.0b5-android-armeabi-v7a/ should extract armeabi-v7a")
        assertTrue(result.contains("x86_64"), "fenix-146.0b5-android-x86_64/ should extract x86_64")
    }

    // Tests for compareReleaseVersions

    @Test
    fun `compareReleaseVersions - equal versions return 0`() {
        val result = parser.compareReleaseVersions("145.0", "145.0")
        assertEquals(0, result, "Equal versions should return 0")
    }

    @Test
    fun `compareReleaseVersions - first version greater by major version`() {
        val result = parser.compareReleaseVersions("146.0", "145.0")
        assertTrue(result > 0, "146.0 should be greater than 145.0")
    }

    @Test
    fun `compareReleaseVersions - first version smaller by major version`() {
        val result = parser.compareReleaseVersions("145.0", "146.0")
        assertTrue(result < 0, "145.0 should be smaller than 146.0")
    }

    @Test
    fun `compareReleaseVersions - first version greater by minor version`() {
        val result = parser.compareReleaseVersions("145.1", "145.0")
        assertTrue(result > 0, "145.1 should be greater than 145.0")
    }

    @Test
    fun `compareReleaseVersions - first version smaller by minor version`() {
        val result = parser.compareReleaseVersions("145.0", "145.1")
        assertTrue(result < 0, "145.0 should be smaller than 145.1")
    }

    @Test
    fun `compareReleaseVersions - first version greater by patch version`() {
        val result = parser.compareReleaseVersions("145.0.1", "145.0")
        assertTrue(result > 0, "145.0.1 should be greater than 145.0")
    }

    @Test
    fun `compareReleaseVersions - first version smaller by patch version`() {
        val result = parser.compareReleaseVersions("145.0", "145.0.1")
        assertTrue(result < 0, "145.0 should be smaller than 145.0.1")
    }

    @Test
    fun `compareReleaseVersions - beta versions - first greater`() {
        val result = parser.compareReleaseVersions("145.0b5", "145.0b1")
        assertTrue(result > 0, "145.0b5 should be greater than 145.0b1")
    }

    @Test
    fun `compareReleaseVersions - beta versions - first smaller`() {
        val result = parser.compareReleaseVersions("145.0b1", "145.0b5")
        assertTrue(result < 0, "145.0b1 should be smaller than 145.0b5")
    }

    @Test
    fun `compareReleaseVersions - beta versions equal`() {
        val result = parser.compareReleaseVersions("145.0b3", "145.0b3")
        assertEquals(0, result, "Equal beta versions should return 0")
    }

    @Test
    fun `compareReleaseVersions - same major minor, different beta numbers`() {
        val result = parser.compareReleaseVersions("146.0b2", "146.0b10")
        assertTrue(result < 0, "146.0b2 should be less than 146.0b10")
    }

    @Test
    fun `compareReleaseVersions - major version difference dominates beta vs beta`() {
        val result = parser.compareReleaseVersions("146.0b1", "145.0b9")
        assertTrue(result > 0, "146.0b1 should be greater than 145.0b9")
    }

    @Test
    fun `compareReleaseVersions - comparing beta versions with version dash separator`() {
        val result = parser.compareReleaseVersions("145.0b5", "145.0-b1")
        assertTrue(result > 0, "145.0b5 should be greater than 145.0-b1 (5 > 1)")
    }

    @Test
    fun `compareReleaseVersions - three part version comparison`() {
        val result = parser.compareReleaseVersions("145.0.2", "145.0.1")
        assertTrue(result > 0, "145.0.2 should be greater than 145.0.1")
    }

    @Test
    fun `compareReleaseVersions - complex version strings with multiple separators`() {
        val result = parser.compareReleaseVersions("124.0b9", "126.0b5")
        assertTrue(result < 0, "124.0b9 should be less than 126.0b5")
    }

    @Test
    fun `compareReleaseVersions - major version difference takes precedence`() {
        val result = parser.compareReleaseVersions("146.0b1", "145.0.9")
        assertTrue(result > 0, "146.0b1 should be greater than 145.0.9")
    }

    @Test
    fun `compareReleaseVersions - minor version difference when major equal`() {
        val result = parser.compareReleaseVersions("145.1b5", "145.0.9")
        assertTrue(result > 0, "145.1b5 should be greater than 145.0.9")
    }

    @Test
    fun `compareReleaseVersions - patch version comparison with beta`() {
        val result = parser.compareReleaseVersions("145.0.2b5", "145.0.1b9")
        assertTrue(result > 0, "145.0.2b5 should be greater than 145.0.1b9")
    }

    @Test
    fun `compareReleaseVersions - beta numbers compared when base versions equal`() {
        val result = parser.compareReleaseVersions("145.0b5", "145.0b3")
        assertTrue(result > 0, "145.0b5 should be greater than 145.0b3")
    }

    @Test
    fun `compareReleaseVersions - all parts equal returns 0`() {
        val result = parser.compareReleaseVersions("145.0.1b5", "145.0.1b5")
        assertEquals(0, result, "Identical complex versions should return 0")
    }

    @Test
    fun `compareReleaseVersions - higher beta minor number beats lower with zero`() {
        val result = parser.compareReleaseVersions("145.0b10", "145.0b9")
        assertTrue(result > 0, "145.0b10 should be greater than 145.0b9")
    }

    @Test
    fun `compareReleaseVersions - sequence preserves transitive ordering`() {
        // Verify that comparison is transitive: if a > b and b > c, then a > c
        val v1 = parser.compareReleaseVersions("146.0", "145.1")
        val v2 = parser.compareReleaseVersions("145.1", "145.0")
        val v3 = parser.compareReleaseVersions("146.0", "145.0")
        
        assertTrue(v1 > 0, "146.0 > 145.1")
        assertTrue(v2 > 0, "145.1 > 145.0")
        assertTrue(v3 > 0, "146.0 > 145.0")
    }

    @Test
    fun `compareReleaseVersions - versions with different structures`() {
        val result = parser.compareReleaseVersions("145.0.1", "145.0b5")
        // This comparison depends on how the function splits the string
        // "145.0.1" splits to [145, 0, 1]
        // "145.0b5" splits to [145, 0, 5]
        // So 1 < 5, result should be negative
        assertTrue(result < 0, "145.0.1 should be less than 145.0b5 based on numeric parts")
    }

    // Helper method

    private fun loadHtmlResource(resourceName: String): String {
        return this::class.java.classLoader?.getResource(resourceName)?.readText()
            ?: throw IllegalArgumentException("Resource not found: $resourceName")
    }

    private fun assertNotNull(value: Any?) {
        assertTrue(value != null, "Value should not be null")
    }
}
