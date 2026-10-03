package app.hexavore.feature.settings

import app.hexavore.core.testing.InMemoryAiCredentials
import app.hexavore.core.testing.InMemoryDebugSettings
import app.hexavore.domain.ai.AiProvider
import app.hexavore.domain.ai.ApiKey
import app.hexavore.domain.ai.ProviderCredentials
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Les deux facons d'analyser, et la case qui se grise sans s'eteindre.
 *
 * **La regle qui casserait en silence** : basculer sur un fournisseur sans outillage
 * doit griser la case, pas la decocher. L'eteindre ferait perdre un choix que personne
 * n'a defait, et il ne reviendrait pas en rebranchant le fournisseur precedent.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal class AnalysisModesViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val debug = InMemoryDebugSettings()
    private val credentials = InMemoryAiCredentials()

    @BeforeEach
    fun installer() = Dispatchers.setMain(dispatcher)

    @AfterEach
    fun retirer() = Dispatchers.resetMain()

    @Test
    fun `le mode debug s allume et se lit`() = runTest {
        val viewModel = viewModel()

        viewModel.onDebug(enabled = true)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.debug)
    }

    @Test
    fun `sans fournisseur actif, l outillage n est pas disponible`() = runTest {
        assertFalse(viewModel().uiState.value.toolingAvailable)
    }

    @Test
    fun `un fournisseur outille rend la case disponible`() = runTest {
        credentials.save(AiProvider.ANTHROPIC, CLE)
        advanceUntilIdle()

        assertTrue(viewModel().uiState.value.toolingAvailable)
    }

    private fun viewModel() = AnalysisModesViewModel(debug, credentials)

    private companion object {
        val CLE = ProviderCredentials(ApiKey("sk-de-test"), model = "un-modele", baseUrl = "https://exemple/")
    }
}
