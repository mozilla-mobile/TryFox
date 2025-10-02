package org.mozilla.tryfox.ui.screens

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.RegisterExtension
import org.junit.jupiter.api.io.TempDir
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.whenever
import org.mozilla.tryfox.data.IFenixRepository
import org.mozilla.tryfox.data.UserDataRepository
import org.mozilla.tryfox.data.managers.FakeCacheManager
import java.io.File

@ExperimentalCoroutinesApi
@ExtendWith(MockitoExtension::class)
class ProfileViewModelTest {
    @JvmField
    @RegisterExtension
    val mainCoroutineRule = MainCoroutineRule()

    private lateinit var viewModel: ProfileViewModel
    private lateinit var fakeCacheManager: FakeCacheManager

    @Mock
    private lateinit var mockFenixRepository: IFenixRepository

    @Mock
    private lateinit var mockUserDataRepository: UserDataRepository

    @TempDir
    lateinit var tempCacheDir: File

    @BeforeEach
    fun setUp() =
        runTest {
            fakeCacheManager = FakeCacheManager(tempCacheDir)

            // Mock the behavior of userDataRepository
            whenever(mockUserDataRepository.lastSearchedEmailFlow).thenReturn(flowOf("test@example.com"))

            viewModel =
                ProfileViewModel(
                    fenixRepository = mockFenixRepository,
                    userDataRepository = mockUserDataRepository,
                    cacheManager = fakeCacheManager,
                )
        }

    @AfterEach
    fun tearDown() {
        fakeCacheManager.reset()
    }

    @Test
    fun `init loads last searched email`() =
        runTest {
            advanceUntilIdle()
            assertEquals("test@example.com", viewModel.authorEmail.value)
        }

    @Test
    fun `searchByAuthor with blank email sets error`() =
        runTest {
            viewModel.updateAuthorEmail("")
            viewModel.searchByAuthor()
            advanceUntilIdle()
            assertNotNull(viewModel.errorMessage.value)
            assertTrue(viewModel.pushes.value.isEmpty())
        }

    @Test
    fun `clearAppCache calls cacheManager`() =
        runTest {
            viewModel.clearAppCache()
            advanceUntilIdle()
            assertTrue(fakeCacheManager.clearCacheCalled)
        }
}
