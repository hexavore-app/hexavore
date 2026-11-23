package app.hexavore.feature.settings

import app.hexavore.core.testing.InMemoryFavoriteDishes
import app.hexavore.core.testing.InMemoryFoodCatalog
import app.hexavore.domain.diary.FavoriteDish
import app.hexavore.domain.diary.FavoriteDishId
import app.hexavore.domain.usecase.ObserveFavoriteDishes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/** La liste des favoris, dans les réglages. */
@OptIn(ExperimentalCoroutinesApi::class)
internal class FavoritesSettingsViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()

    @BeforeEach
    fun installer() = Dispatchers.setMain(dispatcher)

    @AfterEach
    fun retirer() = Dispatchers.resetMain()

    @Test
    fun `rien ne s'affiche avant la premiere lecture`() = runTest(dispatcher) {
        val modele = modele(InMemoryFavoriteDishes())

        assertEquals(FavoritesUiState.Loading, modele.uiState.value)
    }

    @Test
    fun `les favoris enregistres s'affichent`() = runTest(dispatcher) {
        val favori = FavoriteDish(FavoriteDishId("f"), "Petit-déj", components = emptyList())
        val modele = modele(InMemoryFavoriteDishes(listOf(favori)))
        backgroundScope.launch { modele.uiState.collect { } }

        val affiches = (modele.uiState.value as FavoritesUiState.Loaded).favorites
        assertEquals(listOf(FavoriteDishId("f")), affiches.map { it.id })
    }

    @Test
    fun `une lecture qui echoue laisse une liste vide`() = runTest(dispatcher) {
        val modele = modele(InMemoryFavoriteDishes(failure = true))
        backgroundScope.launch { modele.uiState.collect { } }

        assertEquals(FavoritesUiState.Loaded(emptyList()), modele.uiState.value)
    }

    private fun modele(favoris: InMemoryFavoriteDishes) =
        FavoritesSettingsViewModel(ObserveFavoriteDishes(favoris, InMemoryFoodCatalog()))
}
