package app.hexavore.feature.home

import app.hexavore.core.testing.InMemoryReminderSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/** La permission de notifier se demande une fois, et une seule. */
@OptIn(ExperimentalCoroutinesApi::class)
internal class NotificationPermissionViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val settings = InMemoryReminderSettings()

    @BeforeEach
    fun installer() = Dispatchers.setMain(dispatcher)

    @AfterEach
    fun retirer() = Dispatchers.resetMain()

    @Test
    fun `jamais demandee, elle se demande`() = runTest(dispatcher) {
        assertTrue(NotificationPermissionViewModel(settings).pending.value)
    }

    @Test
    fun `demandee une fois, elle ne se redemande plus`() = runTest(dispatcher) {
        NotificationPermissionViewModel(settings).onAsked()

        assertTrue(settings.asked)
        assertFalse(NotificationPermissionViewModel(settings).pending.value, "au lancement suivant")
    }
}
