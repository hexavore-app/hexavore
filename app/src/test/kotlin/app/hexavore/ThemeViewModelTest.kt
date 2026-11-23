package app.hexavore

import app.hexavore.core.testing.InMemoryAppearanceSettings
import app.hexavore.domain.appearance.ThemeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/** Le thème de toute l'application, et le système quand on ne sait pas le lire. */
@OptIn(ExperimentalCoroutinesApi::class)
internal class ThemeViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()

    @BeforeEach
    fun installer() = Dispatchers.setMain(dispatcher)

    @AfterEach
    fun retirer() = Dispatchers.resetMain()

    @Test
    fun `le theme choisi s'applique`() = runTest(dispatcher) {
        assertEquals(ThemeMode.DARK, ThemeViewModel(InMemoryAppearanceSettings(ThemeMode.DARK)).mode.value)
    }

    @Test
    fun `un reglage illisible suit le systeme`() = runTest(dispatcher) {
        val illisible = InMemoryAppearanceSettings(ThemeMode.DARK, failure = true)

        assertEquals(ThemeMode.SYSTEM, ThemeViewModel(illisible).mode.value)
    }
}
