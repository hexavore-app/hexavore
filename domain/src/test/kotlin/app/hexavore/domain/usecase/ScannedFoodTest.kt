package app.hexavore.domain.usecase

import app.hexavore.core.testing.FixedClock
import app.hexavore.core.testing.InMemoryBarcodeLookup
import app.hexavore.core.testing.InMemoryDiaryRepository
import app.hexavore.core.testing.InMemoryFavoriteDishes
import app.hexavore.core.testing.InMemoryFoodCatalog
import app.hexavore.core.testing.InMemorySelectedDay
import app.hexavore.core.testing.SequentialIdGenerator
import app.hexavore.domain.ai.EstimationOutcome
import app.hexavore.domain.ai.InMemoryPendingRecognition
import app.hexavore.domain.diary.EntrySource
import app.hexavore.domain.food.Barcode
import app.hexavore.domain.food.CustomFoodDraft
import app.hexavore.domain.food.Food
import app.hexavore.domain.food.FoodId
import app.hexavore.domain.food.FoodSource
import app.hexavore.domain.nutrition.NutrientValues
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.time.Instant

/**
 * Les deux issues d'un scan, côté domaine : le produit existe, ou il faut le créer.
 *
 * La seconde est la moitié qu'on oublie de vérifier. « Un produit absent d'Open Food
 * Facts se crée à la main **en conservant son code-barres** » est un critère de fin de
 * tranche, et il ne tient que si la fiche créée se retrouve ensuite par ce code.
 */
class ScannedFoodTest {
    @Test
    fun `un aliment cree apres un scan infructueux se retrouve par son code-barres`() = runTest {
        // Le parcours entier : le service ne connait pas le code, on tape la fiche, et
        // le prochain scan doit tomber dessus -- sans reseau, cette fois.
        val catalogue = InMemoryFoodCatalog()
        val save = SaveCustomFood(catalogue, SequentialIdGenerator())

        val id = save(CustomFoodDraft(name = "Pain de mamie", per100g = NutrientValues(kcal = 250.0), barcode = CODE))

        val retrouve = InMemoryBarcodeLookup(catalogue).byBarcode(CODE)
        assertNotNull(retrouve, "l aliment cree n est pas scannable")
        assertEquals(id, retrouve?.id)
        assertEquals(FoodSource.CUSTOM, retrouve?.source)
    }

    @Test
    fun `un aliment cree sans scan n a aucune reference`() = runTest {
        // Le chemin ordinaire, depuis la recherche : `null` est exact, et l'index
        // unique sur (source, source_ref) laisse alors cohabiter autant de fiches
        // personnelles que voulu.
        val catalogue = InMemoryFoodCatalog()
        val save = SaveCustomFood(catalogue, SequentialIdGenerator())

        val id = save(CustomFoodDraft(name = "Pates de mamie", per100g = NutrientValues(kcal = 350.0)))

        assertNull(catalogue.byId(id)?.sourceRef)
    }

    @Test
    fun `un produit scanne ouvre un brouillon marque code-barres`() = runTest {
        // La pastille du plat, et elle ne se reecrit jamais : corriger une quantite ne
        // doit pas faire passer un scan pour une saisie manuelle (D32).
        val catalogue = InMemoryFoodCatalog(initial = listOf(JUS))

        val brouillon = openDraft(catalogue)(DraftOrigin.Scanned(JUS.id))

        assertEquals(EntrySource.BARCODE, brouillon?.source)
        assertEquals(JUS.name, brouillon?.lines?.single()?.name)
    }

    @Test
    fun `un produit scanne qui a disparu n ouvre rien`() = runTest {
        // La ou une saisie neuve rendrait un brouillon vierge : celui-la porterait une
        // pastille « code-barres » sur un plat que personne n'a scanne.
        val brouillon = openDraft(InMemoryFoodCatalog())(DraftOrigin.Scanned(FoodId("partie")))

        assertNull(brouillon)
    }

    @Test
    fun `une saisie neuve reste marquee saisie manuelle`() = runTest {
        // Le pendant du precedent : le meme cas d'usage, la meme charge -- un
        // identifiant de fiche -- et deux sources differentes. C'est la paire qui rend
        // la distinction visible si elle venait a se perdre.
        val catalogue = InMemoryFoodCatalog(initial = listOf(JUS))

        val brouillon = openDraft(catalogue)(DraftOrigin.New(JUS.id))

        assertEquals(EntrySource.MANUAL, brouillon?.source)
    }

    // --- Decor -------------------------------------------------------------------

    private fun openDraft(catalogue: InMemoryFoodCatalog): OpenDraft {
        val ids = SequentialIdGenerator()
        val clock = FixedClock(MAINTENANT)
        val create = CreateDraft(clock, ids, InMemorySelectedDay(clock.today()))
        return OpenDraft(
            dishes = GetDishDraft(InMemoryDiaryRepository(), ids, clock),
            favorites = GetFavoriteDraft(InMemoryFavoriteDishes(), catalogue, create, ids),
            create = create,
            foods = catalogue,
            pending = InMemoryPendingRecognition(),
            resolve = ResolveRecognition(
                ResolveFoodLabel(catalogue),
                CreateDraft(clock, ids, InMemorySelectedDay(clock.today())),
                // Aucun repli : ces cas ne parlent pas de l'etape 4, et un estimateur
                // qui repondrait remplirait des lignes qu'ils veulent vides.
                estimate = { EstimationOutcome.Estimated(emptyList()) },
            ),
        )
    }

    private companion object {
        val CODE: Barcode = requireNotNull(Barcode.of("5449000000996"))
        val MAINTENANT: Instant = Instant.parse("2026-08-12T09:00:00Z")

        val JUS = Food(
            id = FoodId("f-jus"),
            source = FoodSource.OFF,
            sourceRef = CODE.value,
            name = "Boisson gazeuse",
            per100g = NutrientValues(kcal = 42.0),
            defaultServingG = 330.0,
            isLiquid = true,
        )
    }
}
