package app.hexavore.domain.usecase

import app.hexavore.core.testing.InMemoryFoodCatalog
import app.hexavore.domain.food.Food
import app.hexavore.domain.food.FoodId
import app.hexavore.domain.food.FoodSource
import app.hexavore.domain.nutrition.NutrientValues
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Ce que le catalogue propose au modèle.
 *
 * **Ce jeu ne vérifie aucun choix, et c'est le point.** L'outil cherche et présente ;
 * décider est le travail du modèle. Un cas qui affirmerait « abricot rend d'abord
 * l'abricot » remettrait ici le score qu'on est justement en train de retirer.
 *
 * Ce qui se vérifie est donc la **présentation** : que chaque libellé reçoive sa
 * liste, qu'une fiche non désignable n'y figure pas, et que le repli au singulier
 * fonctionne comme dans la résolution interne.
 */
class LookUpCandidatesTest {
    @Test
    fun `chaque libelle recoit sa liste, dans l ordre`() = runTest {
        // Le modele envoie cinq mots d'un coup : sans le libelle sur chaque groupe, il
        // devrait relier les listes par leur position.
        val outil = outil(fiche("abricot", "Abricot, pulpe, cru"), fiche("semoule", "Semoule de blé dur, cuite"))

        val groupes = outil.candidatesFor(listOf("abricot", "semoule"))

        assertEquals(listOf("abricot", "semoule"), groupes.map { it.label })
    }

    @Test
    fun `un libelle sans candidat rend une liste vide`() = runTest {
        // Ce n'est pas une erreur, c'est la reponse : c'est elle qui autorise le
        // modele a inventer des macros.
        val groupes = outil().candidatesFor(listOf("coriandre"))

        assertEquals(emptyList<Any>(), groupes.single().candidates)
    }

    @Test
    fun `une fiche sans reference n est pas proposee`() = runTest {
        // Le modele designe son choix par cette chaine : une fiche sans reference
        // serait proposee sans pouvoir etre choisie.
        val outil = outil(fiche(reference = null, name = "Ma recette de grand-mere"))

        assertEquals(emptyList<Any>(), outil.candidatesFor(listOf("recette")).single().candidates)
    }

    @Test
    fun `le candidat porte sa fiche entiere`() = runTest {
        // C'est ce qui evite un port de relecture : la fiche est deja la quand le
        // modele rend sa reponse, avec ses portions et son identifiant.
        val fiche = fiche("abricot", "Abricot, pulpe, cru")

        val candidat = outil(fiche).candidatesFor(listOf("abricot")).single().candidates.single()

        assertEquals(fiche.id, candidat.food.id)
        assertEquals("abricot", candidat.reference)
        assertEquals(fiche.name, candidat.name)
    }

    @Test
    fun `un pluriel se rattrape au singulier`() = runTest {
        // Le meme ordre que la resolution interne : le brut d'abord, parce que l'index
        // de l'ANSES garde ses pluriels et que « haricots verts » s'y trouve tel quel.
        val candidats = outil(fiche("carotte", "Carotte, cuite"))
            .candidatesFor(listOf("carottes"))
            .single()
            .candidates

        assertTrue(candidats.isNotEmpty(), "« carottes » doit retrouver « carotte »")
    }

    @Test
    fun `la liste est bornee a six candidats`() = runTest {
        // Chaque candidat coute un nom, un rayon et six nombres, multiplies par le
        // nombre de libelles d'une assiette.
        val fiches = List(12) { fiche("pain-$it", "Pain numero $it") }

        val candidats = outil(*fiches.toTypedArray()).candidatesFor(listOf("pain")).single().candidates

        assertEquals(6, candidats.size, "or ${candidats.size} candidats")
    }

    private fun outil(vararg fiches: Food) = LookUpCandidates(InMemoryFoodCatalog(initial = fiches.toList()), FRANCAIS)

    private fun fiche(reference: String?, name: String) = Food(
        id = FoodId("id-${reference ?: name}"),
        source = FoodSource.CIQUAL,
        sourceRef = reference,
        name = name,
        per100g = NutrientValues(kcal = 48.0, protein = 0.9, carbs = 9.0, sugars = 8.6, fat = 0.3, fiber = 2.0),
    )
}
