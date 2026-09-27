package app.hexavore.feature.entry

import app.hexavore.core.testing.FixedClock
import app.hexavore.core.testing.FixedLanguage
import app.hexavore.core.testing.InMemoryDiaryRepository
import app.hexavore.core.testing.InMemoryFavoriteDishes
import app.hexavore.core.testing.InMemoryFoodCatalog
import app.hexavore.core.testing.InMemorySelectedDay
import app.hexavore.core.testing.SequentialIdGenerator
import app.hexavore.domain.ai.EstimatedUnit
import app.hexavore.domain.ai.EstimationOutcome
import app.hexavore.domain.ai.Recognition
import app.hexavore.domain.ai.RecognizedItem
import app.hexavore.domain.diary.EntrySource
import app.hexavore.domain.food.Food
import app.hexavore.domain.food.FoodId
import app.hexavore.domain.food.FoodSource
import app.hexavore.domain.nutrition.Macro
import app.hexavore.domain.nutrition.NutrientValues
import app.hexavore.domain.usecase.CreateDraft
import app.hexavore.domain.usecase.LogDish
import app.hexavore.domain.usecase.ResolveFoodLabel
import app.hexavore.domain.usecase.ResolveRecognition
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate

/**
 * Le chemin complet d'un plat proposé : reconnaître, résoudre, **passer par l'écran**,
 * enregistrer.
 *
 * **C'est le test qui manquait**, et son absence a coûté deux diagnostics. Chaque
 * morceau était éprouvé de son côté — la résolution rendait bien des lignes avec leur
 * fiche, l'enregistrement versait bien les fiches qu'on lui donnait — et le défaut
 * vivait exactement dans la couture : le formulaire de l'écran ne portait pas la fiche,
 * donc le brouillon qui ressortait n'en avait plus aucune à verser, et l'entrée citait
 * une fiche absente du catalogue.
 *
 * Il traverse volontairement `EntryForm`, qui n'est pas du domaine : c'est là que la
 * chose se perdait, et un test qui l'aurait contourné serait resté vert.
 */
class ProposedDishSavingTest {
    private val ids = SequentialIdGenerator()
    private val clock = FixedClock.atNoon(JOUR)

    /**
     * La table de l'ANSES, **non copiée dans le catalogue** : c'est ce que fait le
     * vrai, et c'est ce qui rend les identifiants provisoires possibles. Un faux qui
     * rangerait ses fiches d'emblée n'aurait jamais montré le défaut.
     */
    private val catalogue = InMemoryFoodCatalog(reference = listOf(RIZ, POULET))
    private val diary = InMemoryDiaryRepository()
    private val favoris = InMemoryFavoriteDishes()

    @Test
    fun `un plat propose s enregistre, et sa fiche entre au catalogue`() = runTest {
        val brouillon = resolved(item("riz"))

        // Le passage par l'ecran : c'est ici que la fiche se perdait.
        val apresEcran = EntryForm.of(brouillon).toDraft()
        LogDish(diary, catalogue, favoris, clock, ids)(apresEcran)

        val entree = diary.dishes.single().entries.single()
        assertNotNull(entree.foodId, "une ligne resolue cite la fiche dont elle vient")
        assertNotNull(catalogue.byId(entree.foodId!!), "et cette fiche doit exister : la base l'exige")
    }

    @Test
    fun `plusieurs lignes proposees s enregistrent ensemble`() = runTest {
        // Le cas rapporte : l'IA rend un repas de plusieurs aliments, et rien ne
        // s'enregistre.
        val brouillon = resolved(item("riz"), item("poulet"))

        val apresEcran = EntryForm.of(brouillon).toDraft()
        LogDish(diary, catalogue, favoris, clock, ids)(apresEcran)

        val entrees = diary.dishes.single().entries
        assertEquals(2, entrees.size)
        assertTrue(entrees.all { entree -> catalogue.all.any { it.id == entree.foodId } })
    }

    @Test
    fun `la fiche versee porte l identifiant que la ligne citait`() = runTest {
        // Les deux doivent coincider, et c'est **tout** ce que la base demande. Le
        // reste -- reference source, valeurs, portions -- n'entre pas dans la
        // contrainte ; l'identifiant, si.
        val brouillon = resolved(item("riz"))
        val cite = brouillon.lines.single().foodId

        LogDish(diary, catalogue, favoris, clock, ids)(EntryForm.of(brouillon).toDraft())

        assertEquals(cite, diary.dishes.single().entries.single().foodId)
        assertTrue(catalogue.all.any { it.id == cite }, "la fiche citee doit avoir ete versee")
    }

    @Test
    fun `une ligne que le catalogue ne connait pas s enregistre sans fiche`() = runTest {
        // Le repli de l'etape 4 : la ligne porte ses valeurs et aucune fiche. Elle ne
        // doit rien citer, et surtout pas echouer.
        val brouillon = resolved(item("tofu fume au sesame"))
        val complete = EntryForm.of(brouillon).let { form ->
            form.copy(lines = form.lines.map { it.copy(macros = it.macros + (KCAL to "180")) })
        }

        LogDish(diary, catalogue, favoris, clock, ids)(complete.toDraft())

        assertEquals(null, diary.dishes.single().entries.single().foodId)
    }

    private suspend fun resolved(vararg items: RecognizedItem) = ResolveRecognition(
        resolve = ResolveFoodLabel(catalogue, FixedLanguage()),
        create = CreateDraft(clock, ids, InMemorySelectedDay(FixedClock.atNoon(JOUR).today())),
        estimate = { EstimationOutcome.Estimated(emptyList()) },
        languages = FixedLanguage(),
    )(Recognition(items.toList()), EntrySource.TEXT_AI)

    private fun item(label: String) =
        RecognizedItem(label = label, quantity = 100.0, unit = EstimatedUnit.G, confidence = 0.9f)

    private companion object {
        val JOUR: LocalDate = LocalDate.of(2026, 3, 14)
        val KCAL = Macro.CALORIES

        val RIZ = ciqual("f-riz", "Riz blanc, cuit", "9104")
        val POULET = ciqual("f-poulet", "Poulet, blanc, cuit", "36001")

        fun ciqual(id: String, name: String, ref: String) = Food(
            id = FoodId(id),
            source = FoodSource.CIQUAL,
            sourceRef = ref,
            name = name,
            per100g = NutrientValues(kcal = 130.0, protein = 2.5, carbs = 28.0, sugars = 0.1, fat = 0.3, fiber = 0.5),
        )
    }
}
