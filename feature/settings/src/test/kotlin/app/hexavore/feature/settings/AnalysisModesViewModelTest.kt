package app.hexavore.feature.settings

import app.hexavore.core.testing.InMemoryAiCredentials
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
 * Ce que la section d'analyse approfondie annonce.
 *
 * **Ce n'est pas un reglage** : l'analyse approfondie se fait des qu'elle est possible,
 * et l'ecran ne fait que dire laquelle des deux voies sera prise (D142). Ce qui
 * s'eprouve ici est donc une lecture -- le fournisseur actif sait-il appeler des outils
 * -- et non une bascule.
 *
 * Le mode de mise au point vivait dans le meme modele ; il est parti avec l'ecran qu'il
 * ouvrait (D156).
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal class AnalysisModesViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val credentials = InMemoryAiCredentials()

    @BeforeEach
    fun installer() = Dispatchers.setMain(dispatcher)

    @AfterEach
    fun retirer() = Dispatchers.resetMain()

    @Test
    fun `sans fournisseur actif, l outillage n est pas disponible`() = runTest {
        assertFalse(viewModel().uiState.value.toolingAvailable)
    }

    @Test
    fun `un fournisseur outille rend l analyse approfondie disponible`() = runTest {
        credentials.save(AiProvider.ANTHROPIC, CLE)
        advanceUntilIdle()

        assertTrue(viewModel().uiState.value.toolingAvailable)
    }

    private fun viewModel() = AnalysisModesViewModel(credentials)

    private companion object {
        val CLE = ProviderCredentials(ApiKey("sk-de-test"), model = "un-modele", baseUrl = "https://exemple/")
    }
}
