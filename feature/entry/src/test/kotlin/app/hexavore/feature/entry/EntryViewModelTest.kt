package app.hexavore.feature.entry

import androidx.lifecycle.SavedStateHandle
import app.hexavore.core.testing.FixedClock
import app.hexavore.core.testing.InMemoryDiaryRepository
import app.hexavore.core.testing.InMemoryDishPhotos
import app.hexavore.core.testing.InMemoryFavoriteDishes
import app.hexavore.core.testing.InMemoryFoodCatalog
import app.hexavore.core.testing.InMemoryGoals
import app.hexavore.core.testing.InMemoryProfiles
import app.hexavore.core.testing.InMemorySelectedDay
import app.hexavore.core.testing.SequentialIdGenerator
import app.hexavore.domain.ai.EstimatedUnit
import app.hexavore.domain.ai.EstimationOutcome
import app.hexavore.domain.ai.InMemoryPendingRecognition
import app.hexavore.domain.ai.Recognition
import app.hexavore.domain.ai.RecognizedItem
import app.hexavore.domain.diary.Dish
import app.hexavore.domain.diary.DishId
import app.hexavore.domain.diary.DraftLineId
import app.hexavore.domain.diary.EntryId
import app.hexavore.domain.diary.EntrySource
import app.hexavore.domain.diary.FoodEntry
import app.hexavore.domain.diary.MealMoment
import app.hexavore.domain.food.Food
import app.hexavore.domain.food.FoodId
import app.hexavore.domain.food.FoodServing
import app.hexavore.domain.food.FoodSource
import app.hexavore.domain.nutrition.Macro
import app.hexavore.domain.nutrition.Macros
import app.hexavore.domain.nutrition.NutrientValues
import app.hexavore.domain.usecase.AddFoodLine
import app.hexavore.domain.usecase.AttachDishPhoto
import app.hexavore.domain.usecase.CreateDraft
import app.hexavore.domain.usecase.GetDaySummary
import app.hexavore.domain.usecase.GetDishDraft
import app.hexavore.domain.usecase.GetFavoriteDraft
import app.hexavore.domain.usecase.LogDish
import app.hexavore.domain.usecase.ObserveUnitSystem
import app.hexavore.domain.usecase.OpenDraft
import app.hexavore.domain.usecase.OpenDraftPhoto
import app.hexavore.domain.usecase.ProposeFavoriteName
import app.hexavore.domain.usecase.RemoveFavoriteDish
import app.hexavore.domain.usecase.ResolveFoodLabel
import app.hexavore.domain.usecase.ResolveRecognition
import app.hexavore.domain.usecase.SaveDraft
import app.hexavore.domain.usecase.SaveFavoriteDish
import app.hexavore.domain.usecase.UpdateDish
import app.hexavore.domain.usecase.UpdateFavoriteDish
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class EntryViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val jour = LocalDate.of(2026, 3, 15)
    private val clock = FixedClock.atNoon(jour)
    private val diary = InMemoryDiaryRepository()
    private val catalogue = InMemoryFoodCatalog()
    private val ids = SequentialIdGenerator()

    /**
     * Une seule fabrique, donc un seul jour regarde.
     *
     * Trois instances distinctes pouvaient se contredire sans que rien ne le dise --
     * et c'est exactement la forme du defaut que ce correctif repare ailleurs.
     */
    private val create = CreateDraft(clock, ids, InMemorySelectedDay(clock.today()))

    /** Un objectif de maintien : le restant se compte par rapport à lui. */
    private val objectif = InMemoryGoals.maintenance(jour)
    private val goals = InMemoryGoals(listOf(objectif))

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `une saisie neuve ouvre sur une ligne vide et non enregistrable`() = runTest(dispatcher) {
        val state = viewModel().content()

        assertEquals(1, state.form.lines.size)
        assertFalse(state.saveable, "une ligne vide n'est pas une saisie")
        assertEquals(EntrySource.MANUAL, state.form.source)
        assertEquals(jour, state.form.date, "la journee vient de l'horloge, jamais de LocalDate.now()")
    }

    @Test
    fun `l ecran accepte plusieurs lignes des le depart`() = runTest(dispatcher) {
        // Le piege central du projet : ecrit pour une ligne, cet ecran serait a
        // reecrire a chaque nouveau mode de saisie.
        val viewModel = viewModel()

        ajouterAliment(viewModel, RIZ)
        ajouterAliment(viewModel, POULET)
        advanceUntilIdle()

        assertEquals(3, viewModel.content().form.lines.size)
    }

    @Test
    fun `un aliment choisi arrive prerempli, avec sa reference`() = runTest(dispatcher) {
        // Le defaut : la ligne s ajoutait vide, ou pas du tout. Elle doit porter le
        // nom, la quantite et les valeurs de la fiche -- et sa reference, sans quoi
        // la quantite ne recalculerait rien.
        val viewModel = viewModel()

        ajouterAliment(viewModel, RIZ)
        advanceUntilIdle()

        val ajoutee = viewModel.content().form.lines.last()
        assertEquals("Riz blanc, cuit", ajoutee.name)
        assertEquals("155", ajoutee.macros[Macro.CALORIES])
        assertEquals(RIZ.per100g, ajoutee.reference)
    }

    @Test
    fun `les valeurs s affichent en grammes entiers`() = runTest(dispatcher) {
        // Personne ne compte les demi-grammes, et ce qui est affiche est ce qui sera
        // enregistre : l arrondi a lieu a l aller, pas seulement a l ecran.
        val viewModel = viewModel()

        ajouterAliment(viewModel, POMME)
        advanceUntilIdle()

        val ajoutee = viewModel.content().form.lines.last()
        assertEquals("81", ajoutee.macros[Macro.CALORIES], "54 kcal pour 100 g, sur 150 g")
        assertEquals("2", ajoutee.macros[Macro.FIBER], "1,4 g pour 100 g, sur 150 g")
    }

    @Test
    fun `changer la quantite recalcule les valeurs affichees`() = runTest(dispatcher) {
        val viewModel = viewModel()
        ajouterAliment(viewModel, RIZ)
        advanceUntilIdle()
        val ligne = viewModel.content().form.lines.last().id

        viewModel.onLineEdit(ligne, LineEdit.Quantity("200"))

        assertEquals("310", viewModel.content().form.lines.last().macros[Macro.CALORIES])
    }

    @Test
    fun `une ligne complete rend la saisie enregistrable`() = runTest(dispatcher) {
        val viewModel = viewModel()
        val ligne = viewModel.content().form.lines.single().id

        viewModel.onLineEdit(ligne, LineEdit.Name("Riz"))
        viewModel.onLineEdit(ligne, LineEdit.Quantity("150"))
        viewModel.onLineEdit(ligne, LineEdit.MacroValue(Macro.CALORIES, "195"))

        assertTrue(viewModel.content().saveable)
    }

    @Test
    fun `une seule ligne incomplete suffit a bloquer l enregistrement`() = runTest(dispatcher) {
        // Enregistrer silencieusement une ligne a moitie remplie serait ecrire une
        // donnee que personne n'a saisie.
        val viewModel = viewModel()
        val premiere = viewModel.content().form.lines.single().id
        remplir(viewModel, premiere)

        viewModel.onLineEdit(premiere, LineEdit.Quantity(""))

        assertFalse(viewModel.content().saveable)
    }

    @Test
    fun `enregistrer ecrit un plat avec toutes ses lignes`() = runTest(dispatcher) {
        val viewModel = viewModel()
        remplir(viewModel, viewModel.content().form.lines.single().id)
        ajouterAliment(viewModel, POULET)
        advanceUntilIdle()
        remplir(viewModel, viewModel.content().form.lines.last().id, nom = "Poulet")

        viewModel.onSave()

        assertEquals(EntryUiState.Saved, viewModel.uiState.value)
        assertEquals(listOf("Riz", "Poulet"), diary.dishes.single().entries.map { it.displayName })
    }

    @Test
    fun `sans titre ecrit, le plat s enregistre avec le moment de l heure`() = runTest(dispatcher) {
        // L'horloge des cas est a midi : le champ montre « Dejeuner », rien n'est
        // tape, et rien ne s'ecrit -- ce qui est propose n'est pas ce qui est saisi.
        val viewModel = viewModel()
        remplir(viewModel, viewModel.content().form.lines.single().id)

        viewModel.onSave()

        val plat = diary.dishes.single()
        assertNull(plat.title, "un titre que personne n'a tape ne s'ecrit pas")
        assertEquals(MealMoment.LUNCH, plat.moment)
    }

    @Test
    fun `un titre tape s enregistre`() = runTest(dispatcher) {
        val viewModel = viewModel()
        remplir(viewModel, viewModel.content().form.lines.single().id)

        viewModel.onTitle("Poke bowl")
        viewModel.onSave()

        assertEquals("Poke bowl", diary.dishes.single().title)
    }

    @Test
    fun `une pastille de moment efface le titre ecrit`() = runTest(dispatcher) {
        // Sans cela, le nom affiche ne changerait pas et la pastille semblerait sans
        // effet : c'est le geste de quelqu'un qui dit « c'etait le diner ».
        val viewModel = viewModel()
        remplir(viewModel, viewModel.content().form.lines.single().id)
        viewModel.onTitle("Poke bowl")

        viewModel.onMoment(MealMoment.DINNER)
        viewModel.onSave()

        val plat = diary.dishes.single()
        assertNull(plat.title)
        assertEquals(MealMoment.DINNER, plat.moment)
    }

    @Test
    fun `la boite de nom propose le titre du plat`() = runTest(dispatcher) {
        // **L'ecran reste observe pendant le geste**, et ce n'est pas un detail : un
        // nom propose qui n'entrerait pas dans le `combine` ne recalculerait pas
        // l'etat, et la boite ne s'ouvrirait jamais. Relire l'etat par une nouvelle
        // collecte masquerait exactement ce defaut -- elle relance le flux, qui lit
        // alors la valeur courante.
        val viewModel = viewModel()
        remplir(viewModel, viewModel.content().form.lines.single().id)
        val observation = backgroundScope.launch { viewModel.uiState.collect { } }

        viewModel.favorite.propose("Déjeuner") { nom, rang -> "$nom $rang" }
        advanceUntilIdle()

        assertEquals("Déjeuner", (viewModel.uiState.value as EntryUiState.Content).favoriteProposal)
        observation.cancel()
    }

    @Test
    fun `un nom deja pris fait proposer le rang suivant`() = runTest(dispatcher) {
        val viewModel = viewModel()
        remplir(viewModel, viewModel.content().form.lines.single().id)
        viewModel.favorite.propose("Déjeuner") { nom, rang -> "$nom $rang" }
        advanceUntilIdle()
        viewModel.favorite.save("Déjeuner")
        advanceUntilIdle()

        val second = viewModel()
        remplir(second, second.content().form.lines.single().id)
        second.favorite.propose("Déjeuner") { nom, rang -> "$nom $rang" }
        advanceUntilIdle()

        assertEquals("Déjeuner 2", second.content().favoriteProposal)
    }

    @Test
    fun `la boite se referme quand le favori est enregistre`() = runTest(dispatcher) {
        // Sur l'ecriture aboutie et sur elle seule : c'est le seul signal fiable.
        val viewModel = viewModel()
        remplir(viewModel, viewModel.content().form.lines.single().id)
        val observation = backgroundScope.launch { viewModel.uiState.collect { } }
        viewModel.favorite.propose("Déjeuner") { nom, rang -> "$nom $rang" }
        advanceUntilIdle()

        viewModel.favorite.save("Déjeuner")
        advanceUntilIdle()

        val etat = viewModel.uiState.value as EntryUiState.Content
        assertNull(etat.favoriteProposal, "la boite doit se refermer")
        assertTrue(etat.favorite, "et l'etoile doit s'allumer")
        observation.cancel()
    }

    @Test
    fun `un nom deja pris laisse la boite ouverte et le dit`() = runTest(dispatcher) {
        // D62 : un nom pris est une reponse, pas une panne. La boite reste ouverte
        // avec le nom refuse dedans, ce qui permet de le corriger plutot que de tout
        // retaper.
        val premier = viewModel()
        remplir(premier, premier.content().form.lines.single().id)
        premier.favorite.save("Déjeuner")
        advanceUntilIdle()

        val second = viewModel()
        remplir(second, second.content().form.lines.single().id)
        val observation = backgroundScope.launch { second.uiState.collect { } }
        second.favorite.propose("Déjeuner") { nom, rang -> "$nom $rang" }
        advanceUntilIdle()
        // L'utilisateur efface la proposition et insiste avec le nom deja pris.
        second.favorite.save("Déjeuner")
        advanceUntilIdle()

        val etat = second.uiState.value as EntryUiState.Content
        assertTrue(etat.favoriteNameTaken, "le refus doit se dire")
        assertEquals("Déjeuner 2", etat.favoriteProposal, "la boite reste ouverte")
        observation.cancel()
    }

    @Test
    fun `un champ de macro laisse vide ressort inconnu dans le journal`() = runTest(dispatcher) {
        val viewModel = viewModel()
        remplir(viewModel, viewModel.content().form.lines.single().id)

        viewModel.onSave()

        assertNull(
            diary.dishes.single().entries.single().macros.fiber,
            "un champ vide veut dire inconnu, et il doit le rester jusqu'au journal",
        )
    }

    @Test
    fun `supprimer une ligne la retire du brouillon`() = runTest(dispatcher) {
        val viewModel = viewModel()
        ajouterAliment(viewModel, RIZ)
        advanceUntilIdle()
        val aSupprimer = viewModel.content().form.lines.first().id

        viewModel.onRemoveLine(aSupprimer)

        assertEquals(1, viewModel.content().form.lines.size)
    }

    @Test
    fun `le restant tient compte de ce qui est deja note`() = runTest(dispatcher) {
        diary.setContent(listOf(platDeja(kcal = 600.0)))
        val viewModel = viewModel()
        remplir(viewModel, viewModel.content().form.lines.single().id, kcal = "500")

        val impact = viewModel.content().impact!!

        assertEquals(500.0, impact.draftKcal)
        assertEquals(objectif.daily.kcal - 1100.0, impact.remainingKcal)
    }

    @Test
    fun `un echec d ecriture conserve la saisie`() = runTest(dispatcher) {
        val viewModel = viewModel()
        remplir(viewModel, viewModel.content().form.lines.single().id)
        diary.failure = IllegalStateException("disque plein")

        viewModel.onSave()

        val state = viewModel.uiState.value
        assertTrue(state is EntryUiState.Error)
        assertEquals("Riz", (state as EntryUiState.Error).form.lines.single().name)
    }

    @Test
    fun `reessayer apres un echec rend la saisie enregistrable`() = runTest(dispatcher) {
        val viewModel = viewModel()
        remplir(viewModel, viewModel.content().form.lines.single().id)
        diary.failure = IllegalStateException("disque plein")
        viewModel.onSave()

        diary.failure = null
        viewModel.onRetry()
        viewModel.onSave()

        assertEquals(EntryUiState.Saved, viewModel.uiState.value)
        assertEquals(1, diary.dishes.size)
    }

    @Test
    fun `une proposition ouvre autant de lignes que le modele en a rendues`() = runTest(dispatcher) {
        // Le chemin que quatre livraisons attendaient, vu depuis l'ecran : la modale
        // depose, la route ne porte qu'un drapeau, et l'ecran recoit un brouillon
        // comme il en recoit depuis la tranche 2.
        runBlocking { catalogue.save(RIZ) }
        pending.offer(Recognition(listOf(item("riz", 1.0), item("tofu fume", 80.0))), EntrySource.TEXT_AI)

        val state = viewModel(proposal = true).content()

        assertEquals(listOf(RIZ.name, "tofu fume"), state.form.lines.map { it.name })
        assertEquals(EntrySource.TEXT_AI, state.form.source)
        // La marque traverse le formulaire : sans elle, une supposition s'afficherait
        // avec la meme autorite qu'un aliment choisi.
        assertNotNull(state.form.lines.first().suggestion, "une ligne proposee doit le dire")
    }

    @Test
    fun `une proposition deja reprise ne se rejoue pas`() = runTest(dispatcher) {
        // Revenir sur la validation par le bouton « retour » ne doit pas ressusciter
        // un plat qu'on vient d'enregistrer, ni le dedoubler.
        pending.offer(Recognition(listOf(item("riz", 1.0))), EntrySource.TEXT_AI)
        viewModel(proposal = true).content()

        val etat = viewModel(proposal = true).uiState
            .filterIsInstance<EntryUiState.Unavailable>()
            .first()

        assertEquals(EntryUiState.Unavailable, etat)
    }

    @Test
    fun `un plat introuvable se dit au lieu de s inventer`() = runTest(dispatcher) {
        val etat = viewModel(dishId = "plat-disparu").uiState
            .filterIsInstance<EntryUiState.Unavailable>()
            .first()

        assertEquals(EntryUiState.Unavailable, etat)
    }

    @Test
    fun `rouvrir un plat rend ses lignes telles qu elles ont ete figees`() = runTest(dispatcher) {
        val neuf = viewModel()
        remplir(neuf, neuf.content().form.lines.single().id, nom = "Riz", kcal = "195")
        neuf.onSave()
        val platId = diary.dishes.single().id

        val relu = viewModel(dishId = platId.value).content()

        val ligne = relu.form.lines.single()
        assertEquals("Riz", ligne.name)
        assertEquals("150", ligne.quantity)
        assertEquals("195", ligne.macros[Macro.CALORIES])
        assertEquals(EntrySource.MANUAL, relu.form.source)
    }

    // --- Decor ---------------------------------------------------------------

    private fun platDeja(kcal: Double): Dish {
        val id = DishId("plat-deja")
        return Dish(
            id = id,
            date = jour,
            source = EntrySource.MANUAL,
            loggedAt = clock.now(),
            entries = listOf(
                FoodEntry(
                    id = EntryId("ligne-deja"),
                    dishId = id,
                    displayName = "Deja note",
                    quantity = 100.0,
                    unit = "g",
                    grams = 100.0,
                    macros = Macros.caloriesOnly(kcal),
                ),
            ),
        )
    }

    private fun remplir(viewModel: EntryViewModel, id: DraftLineId, nom: String = "Riz", kcal: String = "195") {
        viewModel.onLineEdit(id, LineEdit.Name(nom))
        viewModel.onLineEdit(id, LineEdit.Quantity("150"))
        viewModel.onLineEdit(id, LineEdit.MacroValue(Macro.CALORIES, kcal))
    }

    private suspend fun EntryViewModel.content(): EntryUiState.Content =
        uiState.filterIsInstance<EntryUiState.Content>().first()

    @Test
    fun `un produit scanne ouvre un plat marque code-barres`() = runTest {
        // L'argument de route est lu par le `ViewModel`, et c'est la moitie que le
        // domaine ne couvre pas : `ScannedFoodTest` eprouve la regle, celui-ci
        // eprouve qu'elle est **atteinte**. C'est exactement la ou D52 avait fait
        // perdre une saisie -- un argument que personne ne lisait.
        runBlocking { catalogue.save(RIZ) }

        val etat = viewModel(scannedFoodId = RIZ.id.value).content()

        assertEquals(EntrySource.BARCODE, etat.form.source)
        assertEquals(listOf(RIZ.name), etat.form.lines.map { it.name })
    }

    /**
     * Ce que fait l'écran quand la recherche lui rend une fiche.
     *
     * C'est le `ViewModel` qu'on appelle et non un `SavedStateHandle` qu'on remplit :
     * celui d'un `ViewModel` et celui d'une entrée de pile sont deux objets
     * différents, et le premier n'a jamais vu ce que le second recevait. Un test qui
     * écrivait dans le handle passait, pendant que l'écran ne faisait rien.
     */
    private fun ajouterAliment(viewModel: EntryViewModel, food: Food = RIZ) {
        runBlocking { catalogue.save(food) }
        viewModel.onFoodPicked(food.id)
    }

    private fun viewModel(
        dishId: String? = null,
        favoriteId: String? = null,
        scannedFoodId: String? = null,
        proposal: Boolean = false,
    ) = EntryViewModel(
        savedStateHandle = SavedStateHandle(
            listOfNotNull(
                dishId?.let { "dishId" to it },
                favoriteId?.let { "favoriteId" to it },
                scannedFoodId?.let { "scannedFoodId" to it },
                "proposal" to proposal,
            ).toMap(),
        ),
        composition = DraftComposition(
            openDraft = OpenDraft(
                dishes = GetDishDraft(diary, ids, clock),
                favorites = GetFavoriteDraft(favoris, catalogue, create, ids),
                create = create,
                foods = catalogue,
                pending = pending,
                resolve = ResolveRecognition(
                    ResolveFoodLabel(catalogue),
                    create,
                    // Aucun repli : ces cas ne parlent pas de l'etape 4, et un estimateur
                    // qui repondrait remplirait des lignes qu'ils veulent vides.
                    estimate = { EstimationOutcome.Estimated(emptyList()) },
                ),
            ),
            addFoodLine = AddFoodLine(catalogue, create),
            observeUnitSystem = ObserveUnitSystem(profils),
            openDraftPhoto = OpenDraftPhoto(photos),
            attachDishPhoto = AttachDishPhoto(photos),
        ),
        getDaySummary = GetDaySummary(diary, goals, clock),
        saveDraft = SaveDraft(LogDish(diary, catalogue, favoris, clock, ids), UpdateDish(diary, ids)),
        favorites = DraftFavorites(
            saveFavoriteDish = SaveFavoriteDish(favoris, ids),
            removeFavoriteDish = RemoveFavoriteDish(favoris),
            proposeFavoriteName = ProposeFavoriteName(favoris),
            updateFavoriteDish = UpdateFavoriteDish(favoris, diary),
        ),
        clock = clock,
    )

    private val profils = InMemoryProfiles()
    private val photos = InMemoryDishPhotos()

    private val favoris = InMemoryFavoriteDishes()

    /** Le dépôt des propositions, partagé entre l'écran qui dépose et celui qui reprend. */
    private val pending = InMemoryPendingRecognition()

    private fun item(label: String, quantity: Double) =
        RecognizedItem(label = label, quantity = quantity, unit = EstimatedUnit.G, confidence = 0.9f)

    private companion object {
        val RIZ = Food(
            id = FoodId("f-riz"),
            source = FoodSource.CIQUAL,
            sourceRef = "9104",
            name = "Riz blanc, cuit",
            per100g = NutrientValues(kcal = 155.0),
        )

        val POMME = Food(
            id = FoodId("f-pomme"),
            source = FoodSource.CIQUAL,
            sourceRef = "13039",
            name = "Pomme, chair et peau, crue",
            per100g = NutrientValues(kcal = 54.0, fiber = 1.4),
            servings = listOf(FoodServing("1 pomme moyenne", grams = 150.0, isDefault = true)),
        )

        val POULET = Food(
            id = FoodId("f-poulet"),
            source = FoodSource.CIQUAL,
            sourceRef = "36005",
            name = "Poulet roti",
            per100g = NutrientValues(kcal = 200.0),
        )
    }
}
