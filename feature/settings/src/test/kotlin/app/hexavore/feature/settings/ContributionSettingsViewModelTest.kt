package app.hexavore.feature.settings

import app.hexavore.domain.food.ContributionSettings
import app.hexavore.domain.food.ContributionSetup
import app.hexavore.domain.food.OffAccount
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/** Le compte Open Food Facts : le donner, l'oublier, et choisir le bac à sable. */
@OptIn(ExperimentalCoroutinesApi::class)
internal class ContributionSettingsViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val settings = MemoryContribution()

    @BeforeEach
    fun installer() = Dispatchers.setMain(dispatcher)

    @AfterEach
    fun retirer() = Dispatchers.resetMain()

    @Test
    fun `un compte complet s'enregistre, identifiant nettoye`() = runTest(dispatcher) {
        val modele = ContributionSettingsViewModel(settings)
        backgroundScope.launch { modele.uiState.collect { } }

        modele.onSave("  charlie  ", "secret")

        assertEquals(OffAccount("charlie", "secret"), settings.state.value.account)
        assertEquals("charlie", modele.uiState.value.userId)
        assertTrue(modele.uiState.value.connected)
    }

    @Test
    fun `un compte incomplet n'ecrit rien`() = runTest(dispatcher) {
        val modele = ContributionSettingsViewModel(settings)

        modele.onSave("charlie", " ")
        modele.onSave("   ", "secret")

        assertNull(settings.state.value.account)
    }

    @Test
    fun `oublier le compte deconnecte`() = runTest(dispatcher) {
        settings.save(OffAccount("charlie", "secret"))
        val modele = ContributionSettingsViewModel(settings)
        backgroundScope.launch { modele.uiState.collect { } }

        modele.onForget()

        assertFalse(modele.uiState.value.connected)
        assertEquals("", modele.uiState.value.userId)
    }

    @Test
    fun `le bac a sable se choisit`() = runTest(dispatcher) {
        val modele = ContributionSettingsViewModel(settings)
        backgroundScope.launch { modele.uiState.collect { } }

        modele.onSandboxChange(true)

        assertTrue(modele.uiState.value.sandbox)
    }

    private class MemoryContribution : ContributionSettings {
        val state = MutableStateFlow(ContributionSetup())

        override fun observe(): Flow<ContributionSetup> = state

        override suspend fun save(account: OffAccount) {
            state.value = state.value.copy(account = account)
        }

        override suspend fun forget() {
            state.value = state.value.copy(account = null)
        }

        override suspend fun useSandbox(sandbox: Boolean) {
            state.value = state.value.copy(sandbox = sandbox)
        }
    }
}
