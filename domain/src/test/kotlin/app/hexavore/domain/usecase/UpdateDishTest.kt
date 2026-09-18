package app.hexavore.domain.usecase

import app.hexavore.core.testing.FixedClock
import app.hexavore.core.testing.InMemoryDiaryRepository
import app.hexavore.core.testing.InMemoryFavoriteDishes
import app.hexavore.core.testing.InMemoryFoodCatalog
import app.hexavore.core.testing.SequentialIdGenerator
import app.hexavore.domain.diary.Dish
import app.hexavore.domain.diary.DishId
import app.hexavore.domain.diary.EntryDraft
import app.hexavore.domain.diary.EntrySource
import app.hexavore.domain.diary.JOUR
import app.hexavore.domain.diary.MealMoment
import app.hexavore.domain.diary.brouillon
import app.hexavore.domain.diary.ligne
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class UpdateDishTest {
    private val diary = InMemoryDiaryRepository()
    private val favoris = InMemoryFavoriteDishes()
    private val catalogue = InMemoryFoodCatalog()
    private val clock = FixedClock.atNoon(JOUR)
    private val ids = SequentialIdGenerator()

    private val logDish = LogDish(diary, catalogue, favoris, clock, ids)
    private val getDishDraft = GetDishDraft(diary, ids, clock)
    private val updateDish = UpdateDish(diary, ids)

    @Test
    fun `modifier un plat ne change pas sa source`() = runTest {
        // D32. Corriger une quantite sur une proposition de l'IA ne doit pas la
        // faire passer pour une saisie manuelle : ce serait perdre la seule trace
        // de ce qui a ete devine. Le brouillon reenvoie MANUAL, exprès.
        val id = logDish(brouillon(ligne("a"), source = EntrySource.PHOTO_AI))
        val relu = getDishDraft(id)!!

        updateDish(relu.copy(source = EntrySource.MANUAL, lines = listOf(relu.lines.single().copy(quantity = 200.0))))

        assertEquals(EntrySource.PHOTO_AI, diary.dishes.single().source)
    }

    @Test
    fun `modifier un plat ne change pas son heure`() = runTest {
        // Sans quoi un plat corrige sauterait en bas de la journee a chaque
        // relecture, alors que l'heure dit quand il a ete mange.
        val id = logDish(brouillon(ligne("a")))
        val heure = diary.dishes.single().loggedAt
        clock.instant = clock.instant.plusSeconds(HEURE_EN_SECONDES)

        updateDish(getDishDraft(id)!!.let { it.copy(lines = listOf(it.lines.single().copy(name = "Riz complet"))) })

        assertEquals(heure, diary.dishes.single().loggedAt)
    }

    @Test
    fun `le titre et le moment se corrigent, eux`() = runTest {
        // L'en-tete est justement ce qu'on vient corriger, a la difference de la
        // source et de l'heure : renommer un plat est une correction, pas une
        // reecriture de son origine.
        val id = logDish(brouillon(ligne("a"), moment = MealMoment.LUNCH))
        val relu = getDishDraft(id)!!

        updateDish(relu.copy(title = "Poke bowl", moment = MealMoment.DINNER))

        val plat = diary.dishes.single()
        assertEquals("Poke bowl", plat.title)
        assertEquals(MealMoment.DINNER, plat.moment)
    }

    @Test
    fun `un plat relu rend le titre et le moment qui ont ete ecrits`() = runTest {
        // Sans cela, rouvrir un plat nomme « Poke bowl » proposerait « Dejeuner »
        // dans son champ, et le simple fait d'enregistrer effacerait le nom.
        val id = logDish(brouillon(ligne("a"), title = "Poke bowl", moment = MealMoment.DINNER))

        val relu = getDishDraft(id)!!

        assertEquals("Poke bowl", relu.title)
        assertEquals(MealMoment.DINNER, relu.moment)
    }

    @Test
    fun `un plat sans moment enregistre rend celui de son heure`() = runTest {
        // Les plats d'avant les moments : leur heure decide, et le champ de l'ecran
        // montre alors le meme nom que l'accueil.
        diary.save(
            Dish(
                id = DishId("ancien"),
                date = JOUR,
                source = EntrySource.MANUAL,
                loggedAt = clock.now(),
                entries = emptyList(),
            ),
        )

        assertEquals(MealMoment.LUNCH, getDishDraft(DishId("ancien"))!!.moment)
    }

    @Test
    fun `une ligne relue est reecrite et non dupliquee`() = runTest {
        val id = logDish(brouillon(ligne("a", nom = "Riz")))
        val relu = getDishDraft(id)!!
        val entryId = relu.lines.single().entryId
        assertNotNull(entryId)

        updateDish(relu.copy(lines = listOf(relu.lines.single().copy(name = "Riz complet"))))

        val lignes = diary.dishes.single().entries
        assertEquals(1, lignes.size)
        assertEquals(entryId, lignes.single().id)
        assertEquals("Riz complet", lignes.single().displayName)
    }

    @Test
    fun `une ligne ajoutee a la relecture recoit un identifiant neuf`() = runTest {
        val id = logDish(brouillon(ligne("a")))
        val relu = getDishDraft(id)!!

        updateDish(relu.copy(lines = relu.lines + ligne("neuve", nom = "Salade")))

        val lignes = diary.dishes.single().entries
        assertEquals(2, lignes.size)
        assertEquals(2, lignes.map { it.id }.toSet().size, "deux lignes ne peuvent pas partager un identifiant")
    }

    @Test
    fun `modifier une ligne laisse les autres telles qu elles ont ete figees`() = runTest {
        // D05 : une entree de journal fige ses valeurs. Rouvrir un plat pour
        // corriger une ligne ne doit pas etre l'occasion d'en reecrire une autre.
        val id = logDish(brouillon(ligne("a", nom = "Riz"), ligne("b", nom = "Sauce", fibres = null)))
        val relu = getDishDraft(id)!!
        val sauceAvant = diary.dishes.single().entries.last()

        val corrige = relu.lines.map { if (it.name == "Riz") it.copy(quantity = 300.0) else it }
        updateDish(relu.copy(lines = corrige))

        assertEquals(sauceAvant, diary.dishes.single().entries.last())
    }

    @Test
    fun `vider un plat de ses lignes le supprime`() = runTest {
        // Retirer les lignes une a une jusqu'a la derniere est une facon naturelle de
        // dire « ce plat n'a pas eu lieu ». Le balayage de l'accueil le permettait
        // deja par `DeleteEntry` ; l'ecran de validation opposait un refus.
        val id = logDish(brouillon(ligne("a"), ligne("b")))
        val relu = getDishDraft(id)!!

        updateDish(relu.copy(lines = emptyList()))

        assertTrue(diary.dishes.isEmpty(), "le plat vide devait disparaitre, pas rester a zero calorie")
    }

    @Test
    fun `un plat vide est enregistrable, une saisie neuve vide ne l est pas`() = runTest {
        // La difference tient a ce qu'il y a a supprimer. Sans elle, « Enregistrer »
        // deviendrait actif sur un ecran de saisie ou l'on n'a encore rien tape.
        val id = logDish(brouillon(ligne("a")))
        val relu = getDishDraft(id)!!

        assertTrue(relu.copy(lines = emptyList()).saveable, "un plat relu et vide se supprime")
        assertTrue(relu.copy(lines = emptyList()).emptying)
        assertFalse(brouillon().saveable, "une saisie neuve vide n a rien a ecrire")
    }

    @Test
    fun `vider un plat deja disparu ne leve pas`() = runTest {
        // Supprimer ce qui n'existe plus n'a rien a verifier, et lever ici forcerait
        // l'ecran a traiter en echec un etat qui est exactement celui qu'il visait.
        val id = logDish(brouillon(ligne("a")))
        val relu = getDishDraft(id)!!
        diary.deleteDish(id)

        updateDish(relu.copy(lines = emptyList()))

        assertTrue(diary.dishes.isEmpty())
    }

    @Test
    fun `refuse un brouillon qui ne designe aucun plat`() = runTest {
        assertThrows<IllegalArgumentException> { updateDish(brouillon(ligne("a"))) }
    }

    @Test
    fun `echoue si le plat a disparu entre temps`() = runTest {
        val id = logDish(brouillon(ligne("a")))
        val relu: EntryDraft = getDishDraft(id)!!
        diary.deleteDish(id)

        assertThrows<IllegalStateException> { updateDish(relu) }
    }

    private companion object {
        const val HEURE_EN_SECONDES = 3600L
    }
}
