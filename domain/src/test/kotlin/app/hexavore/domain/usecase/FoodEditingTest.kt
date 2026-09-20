package app.hexavore.domain.usecase

import app.hexavore.core.testing.FixedClock
import app.hexavore.core.testing.InMemoryDiaryRepository
import app.hexavore.core.testing.InMemoryFavoriteDishes
import app.hexavore.core.testing.InMemoryFoodCatalog
import app.hexavore.core.testing.InMemoryGoals
import app.hexavore.core.testing.SequentialIdGenerator
import app.hexavore.domain.diary.DraftLine
import app.hexavore.domain.diary.DraftLineId
import app.hexavore.domain.diary.EntryDraft
import app.hexavore.domain.diary.EntrySource
import app.hexavore.domain.diary.MealMoment
import app.hexavore.domain.food.CustomFoodDraft
import app.hexavore.domain.food.Food
import app.hexavore.domain.food.FoodId
import app.hexavore.domain.food.FoodSource
import app.hexavore.domain.nutrition.NutrientValues
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate

/**
 * **Modifier un aliment ne change pas les entrées déjà enregistrées.**
 *
 * C'est [D05][decisions], et c'est le critère de fin de tranche que la tranche 2
 * n'avait pas pu éprouver : sans table `food`, aucun aliment n'existait pour être
 * modifié. La forme partielle testée alors — rouvrir un plat rend les valeurs figées
 * — est ici complétée par la vraie : on modifie la fiche, et le journal ne bouge pas.
 *
 * Un journal alimentaire est un registre d'événements. Sans cette règle, un fabricant
 * qui reformule son produit réécrirait un journal vieux de six mois, et supprimer un
 * aliment amputerait l'historique.
 *
 * [decisions]: docs/11-decisions.md
 */
class FoodEditingTest {
    private val diary = InMemoryDiaryRepository()
    private val favoris = InMemoryFavoriteDishes()
    private val catalogue = InMemoryFoodCatalog(listOf(PATES))
    private val clock = FixedClock.atNoon(JOUR)
    private val ids = SequentialIdGenerator()

    private val logDish = LogDish(diary, catalogue, favoris, clock, ids)
    private val getDaySummary = GetDaySummary(diary, InMemoryGoals(listOf(InMemoryGoals.maintenance(JOUR))), clock)
    private val getDishDraft = GetDishDraft(diary, ids, clock)
    private val saveCustomFood = SaveCustomFood(catalogue, ids)

    @Test
    fun `corriger une fiche ne reecrit pas le journal`() = runTest {
        val dishId = logDish(brouillonDe(PATES))

        // La fiche etait fausse de moitie : on la corrige.
        saveCustomFood(
            CustomFoodDraft(
                id = PATES.id,
                name = PATES.name,
                per100g = NutrientValues(kcal = 320.0, protein = 12.0, fiber = 3.0),
            ),
        )

        val plat = getDaySummary(JOUR).first().dishes.single { it.dish.id == dishId }
        val ligne = plat.dish.entries.single()
        assertEquals(158.0, ligne.macros.kcal, "l entree porte les valeurs du jour ou elle a ete ecrite")
        assertEquals(1.4, ligne.macros.fiber)
    }

    @Test
    fun `supprimer une fiche n ampute pas l historique`() = runTest {
        val dishId = logDish(brouillonDe(PATES))

        catalogue.delete(PATES.id)

        val plat = getDaySummary(JOUR).first().dishes.single { it.dish.id == dishId }
        val ligne = plat.dish.entries.single()
        assertEquals("Pâtes de mamie", ligne.displayName, "le nom est fige, il survit a la fiche")
        assertEquals(158.0, ligne.macros.kcal)
    }

    @Test
    fun `rouvrir un plat rend les valeurs figees, pas celles de la fiche`() = runTest {
        val dishId = logDish(brouillonDe(PATES))
        saveCustomFood(CustomFoodDraft(id = PATES.id, name = PATES.name, per100g = NutrientValues(kcal = 320.0)))

        val brouillon = getDishDraft(dishId)!!

        assertEquals(158.0, brouillon.lines.single().values.kcal)
    }

    @Test
    fun `enregistrer un plat verse sa fiche au catalogue et note l usage`() = runTest {
        // Un aliment de la table de l'ANSES n'entre au catalogue que le jour ou il
        // est vraiment mange : copier 3 484 lignes a l'installation gonflerait la
        // base et la recherche avec 99 % de contenu jamais utilise.
        val catalogueVide = InMemoryFoodCatalog()
        val logDish = LogDish(diary, catalogueVide, favoris, clock, ids)

        logDish(brouillonDe(PATES))

        val fiche = catalogueVide.all.single()
        assertEquals(PATES.id, fiche.id)
        assertEquals(clock.now(), fiche.lastUsedAt)
        assertEquals(1, fiche.useCount)
    }

    @Test
    fun `un plat tape a la main ne verse rien au catalogue`() = runTest {
        val catalogueVide = InMemoryFoodCatalog()
        val logDish = LogDish(diary, catalogueVide, favoris, clock, ids)

        logDish(
            EntryDraft(
                date = JOUR,
                source = EntrySource.MANUAL,
                moment = MealMoment.LUNCH,
                lines = listOf(
                    DraftLine(
                        id = DraftLineId("l1"),
                        name = "Reste d hier",
                        quantity = 200.0,
                        values = NutrientValues(kcal = 300.0),
                    ),
                ),
            ),
        )

        assertTrue(catalogueVide.all.isEmpty())
    }

    @Test
    fun `une fiche deja connue n est pas ecrasee par un plat qui la cite`() = runTest {
        // Le brouillon ouvert depuis dix minutes porte encore l'ancienne version.
        // L'enregistrer ne doit pas defaire la correction faite entre-temps.
        saveCustomFood(CustomFoodDraft(id = PATES.id, name = PATES.name, per100g = NutrientValues(kcal = 320.0)))

        logDish(brouillonDe(PATES))

        assertEquals(320.0, catalogue.all.single { it.id == PATES.id }.per100g.kcal)
    }

    @Test
    fun `une fiche sans energie n est pas enregistrable`() = runTest {
        val incomplete = CustomFoodDraft(name = "Sauce de tata", per100g = NutrientValues(protein = 2.0))

        val echec = runCatching { saveCustomFood(incomplete) }

        assertTrue(echec.isFailure)
    }

    @Test
    fun `une marque vide est une absence de marque`() = runTest {
        val id = saveCustomFood(
            CustomFoodDraft(name = "  Pâtes de mamie  ", brand = "   ", per100g = NutrientValues(kcal = 300.0)),
        )

        val fiche = catalogue.all.single { it.id == id }
        assertNull(fiche.brand)
        assertEquals("Pâtes de mamie", fiche.name, "les blancs de bordure ne font pas partie du nom")
    }

    private fun brouillonDe(food: Food) = EntryDraft(
        date = JOUR,
        source = EntrySource.MANUAL,
        lines = listOf(DraftLine.of(DraftLineId("l1"), food)),
        moment = MealMoment.LUNCH,
    )

    private companion object {
        val JOUR: LocalDate = LocalDate.of(2026, 3, 15)

        /** 100 g de pâtes déclarées à 158 kcal — la fiche telle qu'elle était ce jour-là. */
        val PATES = Food(
            id = FoodId("f-pates"),
            source = FoodSource.CUSTOM,
            name = "Pâtes de mamie",
            per100g = NutrientValues(kcal = 158.0, protein = 5.8, fiber = 1.4),
        )
    }
}
