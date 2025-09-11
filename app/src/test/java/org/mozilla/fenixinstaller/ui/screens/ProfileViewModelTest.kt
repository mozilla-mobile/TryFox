package org.mozilla.fenixinstaller.ui.screens

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.junit.MockitoJUnitRunner
import org.mockito.kotlin.whenever
import org.mozilla.fenixinstaller.data.IFenixRepository
import org.mozilla.fenixinstaller.data.UserDataRepository
import org.mozilla.fenixinstaller.data.managers.FakeCacheManager
import java.io.File
import java.nio.file.Files

@ExperimentalCoroutinesApi
@RunWith(MockitoJUnitRunner::class)
class ProfileViewModelTest {

    @get:Rule
    val mainCoroutineRule = MainCoroutineRule()

    private lateinit var viewModel: ProfileViewModel
    private lateinit var fakeCacheManager: FakeCacheManager

    @Mock
    private lateinit var mockFenixRepository: IFenixRepository

    @Mock
    private lateinit var mockUserDataRepository: UserDataRepository

    private lateinit var tempCacheDir: File

    @Before
    fun setUp() = runTest {
        tempCacheDir = Files.createTempDirectory("testCache").toFile()
        fakeCacheManager = FakeCacheManager(tempCacheDir)

        // Mock the behavior of userDataRepository
        whenever(mockUserDataRepository.lastSearchedEmailFlow).thenReturn(flowOf("test@example.com"))

        viewModel = ProfileViewModel(
            fenixRepository = mockFenixRepository,
            userDataRepository = mockUserDataRepository,
            cacheManager = fakeCacheManager
        )
    }

    @After
    fun tearDown() {
        if (::tempCacheDir.isInitialized && tempCacheDir.exists()) {
            tempCacheDir.deleteRecursively()
        }
        fakeCacheManager.reset()
    }

    @Test
    fun `init loads last searched email`() = runTest {
        advanceUntilIdle()
        assertEquals("test@example.com", viewModel.authorEmail.value)
    }

    @Test
    fun `searchByAuthor with blank email sets error`() = runTest {
        viewModel.updateAuthorEmail("")
        viewModel.searchByAuthor()
        advanceUntilIdle()
        assertNotNull(viewModel.errorMessage.value)
        assertTrue(viewModel.pushes.value.isEmpty())
    }

    @Test
    fun `clearAppCache calls cacheManager`() = runTest {
        viewModel.clearAppCache()
        advanceUntilIdle()
        assertTrue(fakeCacheManager.clearCacheCalled)
    }
}
