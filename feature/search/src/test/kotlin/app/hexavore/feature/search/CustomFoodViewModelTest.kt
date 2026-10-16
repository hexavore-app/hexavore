package app.hexavore.feature.search

import androidx.lifecycle.SavedStateHandle
import app.hexavore.core.testing.InMemoryFoodCatalog
import app.hexavore.core.testing.SequentialIdGenerator
import app.hexavore.domain.food.ContributionOutcome
import app.hexavore.domain.food.ContributionSettings
import app.hexavore.domain.food.ContributionSetup
import app.hexavore.domain.food.OffAccount
import app.hexavore.domain.nutrition.Macro
import app.hexavore.domain.usecase.OfferContribution
import app.hexavore.domain.usecase.SaveCustomFood
import app.hexavore.domain.usecase.SendContribution
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * L'aliment personnel à travers la mort du processus ([D166][decisions]).
 *
 * Le `SavedStateHandle` partagé entre deux `ViewModel` tient le rôle de ce qu'Android
 * range avec l'écran et lui rend à sa recréation. Vérifié à l'émulateur avant la
 * correction : seul le nom, venu de la route, revenait ([D163][decisions]).
 *
 * [decisions]: docs/11-decisions.md
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal class CustomFoodViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val catalogue = InMemoryFoodCatalog()

    @BeforeEach
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterEach
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `la fiche en cours survit a la mort du processus`() = runTest(dispatcher) {
        val handle = route(name = "pates de mamie")
        val avant = viewModel(handle)
        avant.onNameChange("Pâtes de mamie")
        avant.onBrandChange("Maison")
        avant.onServingChange("250")
        avant.onMacroChange(Macro.CALORIES, "15,")

        val apres = viewModel(handle).uiState.value as CustomFoodUiState.Editing

        assertEquals("Pâtes de mamie", apres.form.name, "ce qu'on a tape l'emporte sur la route")
        assertEquals("Maison", apres.form.brand)
        assertEquals("250", apres.form.serving)
        assertEquals("15,", apres.form.macros[Macro.CALORIES], "la virgule pendante revient")
    }

    @Test
    fun `le code-barres vient toujours de la route`() = runTest(dispatcher) {
        val handle = route(barcode = "3017620422003")
        viewModel(handle).onNameChange("Pate a tartiner")

        val apres = viewModel(handle).uiState.value as CustomFoodUiState.Editing

        assertEquals("3017620422003", apres.form.barcode?.value)
    }

    @Test
    fun `une fiche enregistree ne revient pas`() = runTest(dispatcher) {
        val handle = route()
        val avant = viewModel(handle)
        avant.onNameChange("Pates de mamie")
        avant.onMacroChange(Macro.CALORIES, "158")

        avant.onSave()

        assertFalse(handle.contains("hexavore.customfood.memory"))
    }

    private fun route(name: String = "", barcode: String? = null) =
        SavedStateHandle(listOfNotNull("name" to name, barcode?.let { "barcode" to it }).toMap())

    private fun viewModel(handle: SavedStateHandle) = CustomFoodViewModel(
        savedStateHandle = handle,
        saveCustomFood = SaveCustomFood(catalogue, SequentialIdGenerator()),
        offerContribution = OfferContribution(catalogue, SANS_COMPTE),
        sendContribution = SendContribution({ _, _ -> ContributionOutcome.Unreachable }, SANS_COMPTE),
    )

    private companion object {
        /** Aucun compte Open Food Facts : la contribution ne se propose pas. */
        val SANS_COMPTE = object : ContributionSettings {
            override fun observe(): Flow<ContributionSetup> = flowOf(ContributionSetup())

            override suspend fun save(account: OffAccount) = Unit

            override suspend fun forget() = Unit

            override suspend fun useSandbox(sandbox: Boolean) = Unit
        }
    }
}
