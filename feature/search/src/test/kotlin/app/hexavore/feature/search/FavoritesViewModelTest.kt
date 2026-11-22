package app.hexavore.feature.search

import app.hexavore.core.testing.InMemoryFavoriteDishes
import app.hexavore.core.testing.InMemoryFoodCatalog
import app.hexavore.domain.diary.FavoriteDish
import app.hexavore.domain.diary.FavoriteDishId
import app.hexavore.domain.usecase.ObserveFavoriteDishes
import app.hexavore.domain.usecase.RemoveFavoriteDish
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/** L'onglet des favoris : filtrer en tapant, supprimer d'un geste. */
@OptIn(ExperimentalCoroutinesApi::class)
internal class FavoritesViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val favoris = InMemoryFavoriteDishes(
        listOf(favori("a", "Petit-déj"), favori("b", "Dîner léger"), favori("c", "Goûter")),
    )

    @BeforeEach
    fun installer() = Dispatchers.setMain(dispatcher)

    @AfterEach
    fun retirer() = Dispatchers.resetMain()

    @Test
    fun `sans saisie, tous les favoris s'affichent`() = runTest(dispatcher) {
        val modele = modele()
        backgroundScope.launch { modele.uiState.collect { } }

        assertEquals(3, contenu(modele).favorites.size)
    }

    @Test
    fun `la saisie filtre sans tenir compte des accents ni de la casse`() = runTest(dispatcher) {
        val modele = modele()
        backgroundScope.launch { modele.uiState.collect { } }

        modele.onQueryChange("DINER")

        assertEquals(listOf(FavoriteDishId("b")), contenu(modele).favorites.map { it.id })
    }

    @Test
    fun `une saisie sans resultat le dit, une saisie vide non`() = runTest(dispatcher) {
        val modele = modele()
        backgroundScope.launch { modele.uiState.collect { } }

        modele.onQueryChange("pizza")
        assertTrue(contenu(modele).filteredOut)

        modele.onQueryChange("   ")
        assertFalse(contenu(modele).filteredOut)
    }

    @Test
    fun `supprimer un favori le retire de la liste`() = runTest(dispatcher) {
        val modele = modele()
        backgroundScope.launch { modele.uiState.collect { } }

        modele.onDelete(FavoriteDishId("a"))

        assertEquals(listOf("b", "c"), contenu(modele).favorites.map { it.id.value }.sorted())
    }

    @Test
    fun `une lecture qui echoue montre l'erreur`() = runTest(dispatcher) {
        favoris.failure = true
        val modele = modele()
        backgroundScope.launch { modele.uiState.collect { } }

        assertEquals(FavoritesUiState.Error, modele.uiState.value)
    }

    private fun modele() = FavoritesViewModel(
        ObserveFavoriteDishes(favoris, InMemoryFoodCatalog()),
        RemoveFavoriteDish(favoris),
    )

    private fun contenu(modele: FavoritesViewModel) = modele.uiState.value as FavoritesUiState.Content

    private fun favori(id: String, nom: String) = FavoriteDish(FavoriteDishId(id), nom, components = emptyList())
}
