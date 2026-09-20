package app.hexavore.feature.entry

import app.hexavore.core.testing.FixedClock
import app.hexavore.core.testing.InMemoryFavoriteDishes
import app.hexavore.core.testing.InMemoryFoodCatalog
import app.hexavore.core.testing.InMemorySelectedDay
import app.hexavore.core.testing.SequentialIdGenerator
import app.hexavore.domain.diary.DraftLineId
import app.hexavore.domain.diary.EntryDraft
import app.hexavore.domain.diary.EntrySource
import app.hexavore.domain.diary.MealMoment
import app.hexavore.domain.diary.QuantityUnit
import app.hexavore.domain.food.Food
import app.hexavore.domain.food.FoodId
import app.hexavore.domain.food.FoodSource
import app.hexavore.domain.nutrition.Macro
import app.hexavore.domain.nutrition.NutrientValues
import app.hexavore.domain.usecase.CreateDraft
import app.hexavore.domain.usecase.FavoriteOutcome
import app.hexavore.domain.usecase.GetFavoriteDraft
import app.hexavore.domain.usecase.SaveFavoriteDish
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.time.LocalDate

/**
 * Le calcul d'énergie **tel que l'écran le fait vivre**.
 *
 * La règle est éprouvée dans le domaine ; ce qui se joue ici est le reste — que le
 * champ montre la nouvelle valeur, qu'elle cesse de suivre la quantité, et qu'elle
 * survive à la mise en favori. Les trois derniers défauts trouvés à l'usage avaient
 * tous la même forme : la donnée était juste, l'écran ne la montrait pas.
 *
 * Le dernier cas traverse volontairement la couture jusqu'au rejeu d'un favori, sur
 * le modèle de [ProposedDishSavingTest] : c'est là qu'une valeur corrigée s'était
 * déjà perdue une fois.
 */
class EnergyFromMacrosTest {
    private val ids = SequentialIdGenerator()

    @Test
    fun `accepter le calcul ecrit l energie arrondie`() {
        // La feta : 4 x 17 + 4 x 1 + 9 x 25 + 2 x 0, soit 297.
        val line = feta()

        val calculee = line.apply(LineEdit.AcceptEnergy)

        assertEquals("297", calculee.macros[Macro.CALORIES])
    }

    @Test
    fun `l energie calculee est marquee comme corrigee a la main`() {
        val calculee = feta().apply(LineEdit.AcceptEnergy)

        assertEquals(setOf(Macro.CALORIES), calculee.edited)
    }

    @Test
    fun `l energie calculee cesse de suivre la quantite`() {
        // C'est la consequence de la marque, et c'est la seule coherente : les macros
        // dont elle se deduit ne suivent deja plus.
        val calculee = feta(reference = NutrientValues(kcal = 264.0, protein = 17.0, fat = 25.0))
            .apply(LineEdit.AcceptEnergy)

        val doublee = calculee.apply(LineEdit.Quantity("200"))

        assertEquals("297", doublee.macros[Macro.CALORIES], "la reference aurait reecrit 528")
    }

    @Test
    fun `accepter le calcul fait revivre le champ`() {
        // **Sans ce compteur, rien ne se verrait.** Un champ de saisie ne relit sa
        // valeur initiale qu'a la premiere composition (D45) : le brouillon porterait
        // la nouvelle energie pendant que le champ afficherait l'ancienne.
        val line = feta()

        assertEquals(line.revision + 1, line.apply(LineEdit.AcceptEnergy).revision)
    }

    @Test
    fun `accepter le calcul rend la ligne enregistrable`() {
        val line = feta()

        assertEquals(MissingField.CALORIES, line.missing)
        assertNull(line.apply(LineEdit.AcceptEnergy).missing)
    }

    @Test
    fun `sans proposition, le geste ne change rien`() {
        // L'ecran ne montre pas la pastille dans ce cas, mais la ligne ne doit pas
        // dependre de l'ecran pour se proteger : une energie coherente reste telle
        // quelle, et le champ ne renait pas pour rien.
        val coherente = feta(kcal = "297")

        val inchangee = coherente.apply(LineEdit.AcceptEnergy)

        assertEquals(coherente, inchangee)
    }

    @Test
    fun `sans les trois valeurs exigees, il n y a rien a proposer`() {
        val incomplete = feta(protein = "")

        assertNull(incomplete.energyProposal)
        assertEquals(incomplete, incomplete.apply(LineEdit.AcceptEnergy))
    }

    @Test
    fun `une energie calculee survit a la mise en favori et au rejeu`() = runTest {
        // **La couture.** Un favori est un modele vivant : une ligne qui cite une
        // fiche se reconstruit depuis la fiche courante au rejeu (D62). Or la fiche
        // de la feta n'a toujours pas d'energie -- c'est pour ca qu'on l'a calculee.
        // Sans le deliement que `edited` declenche, le rejeu la reprendrait vide.
        val favoris = InMemoryFavoriteDishes()
        val catalogue = InMemoryFoodCatalog(initial = listOf(FETA))
        val ligne = feta(foodId = FETA.id).apply(LineEdit.AcceptEnergy)

        val enregistre = SaveFavoriteDish(favoris, ids)(brouillon(ligne), "Salade grecque")
        val rejoue = GetFavoriteDraft(
            favoris,
            catalogue,
            CreateDraft(FixedClock.atNoon(JOUR), ids, InMemorySelectedDay(JOUR)),
            ids,
        )(
            (enregistre as FavoriteOutcome.Saved).id,
        )

        assertNotNull(rejoue)
        assertEquals(297.0, rejoue!!.lines.single().values.kcal)
    }

    @Test
    fun `sans calcul, la ligne suit toujours sa fiche au rejeu`() {
        // La contrepartie, et elle est voulue : le deliement ne doit pas s'etendre a
        // une ligne que personne n'a corrigee, sans quoi D62 ne vaudrait plus rien.
        val intacte = feta(foodId = FETA.id)

        assertEquals(FETA.id, intacte.toDraftLine().foodId)
        assertNotEquals(emptySet<Macro>(), intacte.apply(LineEdit.AcceptEnergy).edited)
    }

    @Test
    fun `corriger un champ efface sa marque d estimation`() {
        // La valeur est celle de l'utilisateur desormais : le contour en pointilles
        // designerait un chiffre que personne n'a devine.
        val estimee = feta().copy(estimated = setOf(Macro.CALORIES, Macro.FIBER))

        val corrigee = estimee.apply(LineEdit.MacroValue(Macro.CALORIES, "300"))

        assertEquals(setOf(Macro.FIBER), corrigee.estimated, "et seulement la sienne")
    }

    @Test
    fun `accepter le calcul efface la marque de l energie`() {
        // Meme regle par un autre geste : si l'energie venait d'une fiche completee,
        // elle n'en vient plus.
        val estimee = feta().copy(estimated = setOf(Macro.CALORIES))

        assertEquals(emptySet<Macro>(), estimee.apply(LineEdit.AcceptEnergy).estimated)
    }

    @Test
    fun `la marque traverse le formulaire sans se perdre`() {
        // La couture que le formulaire a deja perdue une fois : un brouillon portait
        // la fiche, l'ecran la perdait, et l'enregistrement citait un aliment absent
        // (D85). Le meme aller-retour, pour la provenance.
        val ligne = feta().copy(estimated = setOf(Macro.CALORIES))

        assertEquals(setOf(Macro.CALORIES), EntryFormLine.of(ligne.toDraftLine()).estimated)
    }

    private fun brouillon(line: EntryFormLine) = EntryDraft(
        date = JOUR,
        source = EntrySource.MANUAL,
        lines = listOf(line.toDraftLine()),
        moment = MealMoment.LUNCH,
    )

    /**
     * Une ligne de feta : ses protéines et ses lipides sont mesurés, **son énergie
     * ne l'est pas**. C'est une fiche réelle de l'ANSES, et le cas que cette règle
     * vient débloquer.
     */
    private fun feta(
        kcal: String = "",
        protein: String = "17",
        foodId: FoodId? = null,
        reference: NutrientValues? = null,
    ) = EntryFormLine(
        id = DraftLineId("l1"),
        foodId = foodId,
        name = "Feta",
        quantity = "100",
        unit = QuantityUnit.Gram,
        macros = mapOf(
            Macro.CALORIES to kcal,
            Macro.PROTEIN to protein,
            Macro.CARBS to "1",
            Macro.FAT to "25",
            Macro.FIBER to "0",
        ),
        reference = reference,
    )

    private companion object {
        val JOUR: LocalDate = LocalDate.of(2026, 8, 21)

        val FETA = Food(
            id = FoodId("f-feta"),
            source = FoodSource.CIQUAL,
            name = "Feta",
            // Sans energie, comme dans la table : c'est tout le probleme.
            per100g = NutrientValues(protein = 17.0, carbs = 1.0, fat = 25.0, fiber = 0.0),
        )
    }
}
