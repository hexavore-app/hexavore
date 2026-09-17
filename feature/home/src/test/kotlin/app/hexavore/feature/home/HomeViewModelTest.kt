package app.hexavore.feature.home

import app.hexavore.core.testing.FixedClock
import app.hexavore.core.testing.InMemoryAdjustmentSettings
import app.hexavore.core.testing.InMemoryAiCredentials
import app.hexavore.core.testing.InMemoryDiaryRepository
import app.hexavore.core.testing.InMemoryFavoriteDishes
import app.hexavore.core.testing.InMemoryGoals
import app.hexavore.core.testing.InMemoryProfiles
import app.hexavore.core.testing.InMemorySelectedDay
import app.hexavore.core.testing.InMemoryWeightLog
import app.hexavore.core.testing.SampleDiary
import app.hexavore.core.testing.SequentialIdGenerator
import app.hexavore.domain.ai.AiProvider
import app.hexavore.domain.ai.ApiKey
import app.hexavore.domain.ai.ProviderCredentials
import app.hexavore.domain.concurrency.DispatcherProvider
import app.hexavore.domain.diary.EntrySource
import app.hexavore.domain.nutrition.Macro
import app.hexavore.domain.usecase.DeleteDish
import app.hexavore.domain.usecase.GetDaySummary
import app.hexavore.domain.usecase.GetDishDraft
import app.hexavore.domain.usecase.RemoveFavoriteDish
import app.hexavore.domain.usecase.RespondToAdjustment
import app.hexavore.domain.usecase.RestoreDish
import app.hexavore.domain.usecase.SaveFavoriteDish
import app.hexavore.domain.usecase.SuggestGoalAdjustment
import app.hexavore.domain.usecase.ToggleDishFavorite
import app.hexavore.domain.usecase.UpdateDish
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
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
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val jour = LocalDate.of(2026, 3, 15)

    /**
     * `viewModelScope` s'exécute sur `Dispatchers.Main`, qui n'existe pas dans une
     * JVM nue. On le remplace ici plutôt que de le contourner : le ViewModel doit
     * être testé tel qu'il tourne.
     */
    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `expose le resume de la journee de l'horloge`() = runTest(dispatcher) {
        val clock = FixedClock.atNoon(jour)
        val diary = InMemoryDiaryRepository(SampleDiary.day(jour))

        val state = viewModel(diary, clock).uiState.filterIsInstance<HomeUiState.Content>().first()

        assertEquals(jour, state.summary.date)
        assertEquals(3, state.summary.dishes.size)
        assertTrue(state.summary.logged)
    }

    @Test
    fun `chaque plat porte ses six apports`() = runTest(dispatcher) {
        val clock = FixedClock.atNoon(jour)
        val diary = InMemoryDiaryRepository(SampleDiary.day(jour))

        val state = viewModel(diary, clock).uiState.filterIsInstance<HomeUiState.Content>().first()

        val premier = state.summary.dishes.first()
        assertTrue(
            premier.totals[Macro.PROTEIN].value > 0.0,
            "un plat doit exposer ses proteines, pas seulement ses calories",
        )
        assertEquals(EntrySource.MANUAL, premier.dish.source)
    }

    @Test
    fun `un total ampute reste signale jusqu'a l'ecran`() = runTest(dispatcher) {
        val clock = FixedClock.atNoon(jour)
        val diary = InMemoryDiaryRepository(SampleDiary.day(jour))

        val state = viewModel(diary, clock).uiState.filterIsInstance<HomeUiState.Content>().first()

        assertFalse(
            state.summary.totals[Macro.FIBER].complete,
            "la sauce du jeu de demonstration n'a pas de valeur de fibres",
        )
        assertTrue(state.summary.totals[Macro.PROTEIN].complete)
    }

    @Test
    fun `une journee sans saisie n'est pas une journee a zero`() = runTest(dispatcher) {
        val clock = FixedClock.atNoon(jour)

        val state = viewModel(InMemoryDiaryRepository(), clock).uiState
            .filterIsInstance<HomeUiState.Content>()
            .first()

        assertFalse(state.summary.logged)
        assertEquals(0.0, state.summary.totals[Macro.CALORIES].value)
    }

    @Test
    fun `une lecture qui echoue ne se lit pas comme une journee vide`() = runTest(dispatcher) {
        val diary = InMemoryDiaryRepository(SampleDiary.day(jour))
        diary.failure = IllegalStateException("base illisible")

        val state = viewModel(diary, FixedClock.atNoon(jour)).uiState
            .filterIsInstance<HomeUiState.Error>()
            .first()

        assertEquals(
            HomeUiState.Error,
            state,
            "un echec de lecture doit se dire, pas s'afficher comme une journee sans saisie",
        )
    }

    @Test
    fun `reessayer relit le journal`() = runTest(dispatcher) {
        val diary = InMemoryDiaryRepository(SampleDiary.day(jour))
        diary.failure = IllegalStateException("base illisible")
        val viewModel = viewModel(diary, FixedClock.atNoon(jour))
        viewModel.uiState.filterIsInstance<HomeUiState.Error>().first()

        diary.failure = null
        viewModel.retry()

        val state = viewModel.uiState.filterIsInstance<HomeUiState.Content>().first()
        assertEquals(3, state.summary.dishes.size)
    }

    @Test
    fun `annuler une suppression remet la journee comme avant`() = runTest(dispatcher) {
        val diary = InMemoryDiaryRepository(SampleDiary.day(jour))
        val viewModel = viewModel(diary, FixedClock.atNoon(jour))
        val avant = viewModel.uiState.filterIsInstance<HomeUiState.Content>().first().summary
        val plat = avant.dishes.first().dish

        viewModel.onDeleteDish(plat)
        viewModel.onUndo()

        val apres = viewModel.uiState.filterIsInstance<HomeUiState.Content>().first().summary
        assertEquals(avant.totals, apres.totals)
        assertEquals(avant.dishes.size, apres.dishes.size)
    }

    @Test
    fun `un echec de suppression ne propose pas d annulation`() = runTest(dispatcher) {
        // Rien n'a ete supprime : proposer d'annuler laisserait croire le contraire.
        val diary = InMemoryDiaryRepository(SampleDiary.day(jour))
        val viewModel = viewModel(diary, FixedClock.atNoon(jour))
        val plat = viewModel.uiState.filterIsInstance<HomeUiState.Content>().first().summary.dishes.first().dish
        diary.failure = IllegalStateException("base illisible")

        viewModel.onDeleteDish(plat)

        assertNull(viewModel.pendingUndo.value)
    }

    @Test
    fun `supprimer un plat retire ses lignes ensemble, et reste annulable`() = runTest(dispatcher) {
        // L'appui long porte sur le plat, donc l'action aussi : les n lignes partent
        // d'un coup. La barre reste offerte -- la confirmation, demandee par l'ecran,
        // evite l'accident ; la barre rattrape le regret.
        val diary = InMemoryDiaryRepository(SampleDiary.day(jour))
        val viewModel = viewModel(diary, FixedClock.atNoon(jour))
        val plat = viewModel.uiState.filterIsInstance<HomeUiState.Content>().first().summary.dishes.first().dish

        viewModel.onDeleteDish(plat)
        advanceUntilIdle()

        assertEquals(2, diary.dishes.size, "le plat entier devait partir")
        assertEquals(plat, viewModel.pendingUndo.value, "et rester rattrapable")

        viewModel.onUndo()
        advanceUntilIdle()

        assertEquals(3, diary.dishes.size, "annuler remet le plat et ses lignes")
    }

    @Test
    fun `un echec de suppression du plat ne propose rien a annuler`() = runTest(dispatcher) {
        // Rien n'a ete supprime : proposer « Annuler » laisserait croire le contraire.
        val diary = InMemoryDiaryRepository(SampleDiary.day(jour))
        val viewModel = viewModel(diary, FixedClock.atNoon(jour))
        val plat = viewModel.uiState.filterIsInstance<HomeUiState.Content>().first().summary.dishes.first().dish
        diary.failure = IllegalStateException("base illisible")

        viewModel.onDeleteDish(plat)
        advanceUntilIdle()

        assertNull(viewModel.pendingUndo.value)
    }

    @Test
    fun `sans cle, le mode Decrire s annonce indisponible`() = runTest(dispatcher) {
        // D73 : visible et grise plutot que cache. L'accueil n'a besoin de rien de
        // plus que ce booleen -- ni du fournisseur, ni du modele.
        val viewModel = viewModel(InMemoryDiaryRepository(), FixedClock.atNoon(jour))

        assertFalse(viewModel.aiConfigured.first())
    }

    @Test
    fun `une cle enregistree ouvre le mode Decrire`() = runTest(dispatcher) {
        cles.save(
            AiProvider.ANTHROPIC,
            ProviderCredentials(ApiKey("sk-ant-de-test"), model = "claude-opus-5", baseUrl = "https://exemple/"),
        )
        val viewModel = viewModel(InMemoryDiaryRepository(), FixedClock.atNoon(jour))

        assertTrue(viewModel.aiConfigured.first())
    }

    private fun viewModel(diary: InMemoryDiaryRepository, clock: FixedClock) = HomeViewModel(
        getDaySummary = GetDaySummary(diary, InMemoryGoals(listOf(InMemoryGoals.maintenance(jour))), clock),
        dispatchers = TestDispatchers(dispatcher),
        credentials = cles,
        // L'adaptation hebdomadaire se tait ici : rien n'est pese, donc il n'y a ni
        // pente ni cap annonce. Elle a ses propres cas dans :domain.
        suggestGoalAdjustment = SuggestGoalAdjustment(
            weights = InMemoryWeightLog(),
            diary = diary,
            goals = InMemoryGoals(),
            profiles = InMemoryProfiles(),
            settings = InMemoryAdjustmentSettings(),
            clock = clock,
        ),
        respondToAdjustment = RespondToAdjustment(
            goals = InMemoryGoals(),
            settings = InMemoryAdjustmentSettings(),
            ids = SequentialIdGenerator("objectif"),
            clock = clock,
        ),
        gestures = DishGestures(
            deleteDish = DeleteDish(diary),
            restoreDish = RestoreDish(diary),
            toggleFavorite = ToggleDishFavorite(
                drafts = GetDishDraft(diary, SequentialIdGenerator("ligne")),
                update = UpdateDish(diary, SequentialIdGenerator("ligne")),
                save = SaveFavoriteDish(favoris, SequentialIdGenerator("fav")),
                remove = RemoveFavoriteDish(favoris),
            ),
        ),
        // Sans jour choisi : l'accueil lit aujourd'hui, et c'est le `null` qu'une
        // pastille du passe remplace par sa date.
        selected = InMemorySelectedDay(clock.today()),
    )

    private val favoris = InMemoryFavoriteDishes()
    private val cles = InMemoryAiCredentials()

    /** Tout sur le dispatcher de test : aucun vrai pool de threads dans un test. */
    private class TestDispatchers(private val dispatcher: CoroutineDispatcher) : DispatcherProvider {
        override val main: CoroutineDispatcher = dispatcher
        override val default: CoroutineDispatcher = dispatcher
        override val io: CoroutineDispatcher = dispatcher
    }
}
