package org.mozilla.tryfox.ui.screens

import app.cash.turbine.test
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.io.TempDir
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mozilla.tryfox.data.IFenixRepository
import org.mozilla.tryfox.data.UserDataRepository
import org.mozilla.tryfox.data.managers.FakeCacheManager
import java.io.File

@ExperimentalCoroutinesApi
@ExtendWith(MockitoExtension::class)
class ProfileViewModelTest {

    private lateinit var viewModel: ProfileViewModel
    private lateinit var cacheManager: FakeCacheManager

    @Mock
    private lateinit var fenixRepository: IFenixRepository

    @Mock
    private lateinit var userDataRepository: UserDataRepository

    @TempDir
    lateinit var tempCacheDir: File

    @BeforeEach
    fun setUp() = runTest {
        cacheManager = FakeCacheManager(tempCacheDir)

        viewModel = ProfileViewModel(
            fenixRepository = fenixRepository,
            userDataRepository = userDataRepository,
            cacheManager = cacheManager,
        )
    }

    @AfterEach
    fun tearDown() {
        cacheManager.reset()
    }

    @Test
    fun `updateAuthorEmail should update the authorEmail state`() = runTest {
        // Given
        val viewModel = ProfileViewModel(fenixRepository, userDataRepository, cacheManager)
        val newEmail = "test@example.com"

        viewModel.authorEmail.test {
            assertEquals("", awaitItem()) // Consume initial value

            // When
            viewModel.updateAuthorEmail(newEmail)

            // Then
            assertEquals(newEmail, awaitItem())
        }
    }
}
