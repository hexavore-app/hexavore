package app.hexavore.domain.food

import app.hexavore.domain.nutrition.NutrientValues
import app.hexavore.domain.resolution.MatchVerdict
import app.hexavore.domain.resolution.verdictFor
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * L'ordre des résultats, éprouvé sur les cas que [docs/04][sources] cite comme
 * ceux qui rendent une recherche inutilisable.
 *
 * C'est la seule partie de la recherche dont la justesse ne se voit pas à l'œil sur
 * un écran : une liste mal triée reste une liste plausible.
 *
 * [sources]: docs/04-sources-de-donnees.md
 */
class FoodRankingTest {
    @Test
    fun `pomme rend la pomme avant la pomme de terre`() {
        // Le cas nomme par docs/04. Les deux contiennent « pomme » exactement une
        // fois : c'est la longueur du nom qui les departage, pas la frequence.
        val resultats = FoodRanking.sort(listOf(POMME_DE_TERRE, POMME), "pomme")

        assertEquals(listOf(POMME, POMME_DE_TERRE), resultats)
    }

    @Test
    fun `un accent tape ou omis donne le meme ordre`() {
        assertEquals(
            FoodRanking.sort(listOf(POMME_DE_TERRE, POMME), "pomme"),
            FoodRanking.sort(listOf(POMME_DE_TERRE, POMME), "Pommes"),
        )
    }

    @Test
    fun `ce que l utilisateur mange vraiment passe devant`() {
        // Meme nom, meme longueur : seul l'usage les separe.
        val jamais = ciqual("Riz blanc, cuit, sans sel ajouté", useCount = 0)
        val souvent = ciqual("Riz blanc, cru, sans sel ajouté", useCount = 12)

        assertEquals(listOf(souvent, jamais), FoodRanking.sort(listOf(jamais, souvent), "riz"))
    }

    @Test
    fun `un aliment personnel passe devant une ligne de la table`() {
        val personnel = Food(
            id = FoodId("perso"),
            source = FoodSource.CUSTOM,
            name = "Pâtes de mamie",
            per100g = NutrientValues(kcal = 158.0),
        )
        val reference = ciqual("Pâtes de blé, cuites")

        assertEquals(listOf(personnel, reference), FoodRanking.sort(listOf(reference, personnel), "pates"))
    }

    @Test
    fun `un produit de marque est delibérement devalorise`() {
        // « riz » doit tomber sur le riz de la table, pas sur un paquet scanne il y
        // a trois mois.
        val marque = Food(
            id = FoodId("off"),
            source = FoodSource.OFF,
            name = "Riz long grain",
            per100g = NutrientValues(kcal = 350.0),
        )
        val reference = ciqual("Riz blanc, cru")

        assertEquals(listOf(reference, marque), FoodRanking.sort(listOf(marque, reference), "riz"))
    }

    @Test
    fun `un nom qui est exactement la requete gagne`() {
        val exact = ciqual("Miel")
        val compose = ciqual("Miel de sapin")

        assertEquals(listOf(exact, compose), FoodRanking.sort(listOf(compose, exact), "miel"))
    }

    @Test
    fun `a egalite l ordre est alphabetique et non celui de la base`() {
        // Sans ce depart, deux lectures de la meme requete pourraient rendre deux
        // ordres differents, et la liste sauterait sous le doigt.
        val a = ciqual("Riz complet, cru")
        val b = ciqual("Riz basmati, cru")

        assertEquals(FoodRanking.sort(listOf(a, b), "riz"), FoodRanking.sort(listOf(b, a), "riz"))
    }

    // --- Le mot entier (D163) ------------------------------------------------------

    @Test
    fun `lait rend du lait et non de la laitue`() {
        // « lait » commence « Laitue » : un prefixe de lettres passait devant tous les
        // laits, et l'IA, qui ecrit « lait », y tombait.
        val laitue = ciqual("Laitue, crue")
        val lait = ciqual("Lait demi-écrémé, UHT")

        assertEquals(listOf(lait, laitue), FoodRanking.sort(listOf(laitue, lait), "lait"))
    }

    @Test
    fun `le mot entier passe devant ses seules lettres`() {
        val laitue = ciqual("Laitue, crue")
        val cafe = ciqual("Café au lait")

        assertEquals(listOf(cafe, laitue), FoodRanking.sort(listOf(laitue, cafe), "lait"))
    }

    @Test
    fun `pendant qu on ecrit, un nom qui commence par les lettres passe devant`() {
        // « lai » n'est encore aucun mot. Le nom qui commence par ces lettres reste
        // devant celui ou elles n'arrivent qu'au second mot, meme un peu plus long : sans
        // ce palier, la brievete seule mettrait le petit-lait devant la laitue.
        val laitue = ciqual("Laitue romaine, crue")
        val petitLait = ciqual("Petit-lait")

        assertEquals(listOf(laitue, petitLait), FoodRanking.sort(listOf(petitLait, laitue), "lai"))
    }

    // --- La confiance, et ce qu'elle décide ---------------------------------------

    @Test
    fun `la confiance conserve exactement l ordre du score`() {
        // La propriete qui rend la consolidation gratuite : l'ecran de recherche trie
        // par score, le resolveur decide par confiance, et les deux ne peuvent pas se
        // contredire tant que la transformation est strictement croissante. Sans
        // elle, un candidat classe premier pourrait etre celui que le resolveur
        // refuse.
        val candidats = listOf(
            POMME_DE_TERRE,
            POMME,
            ciqual("Compote de pommes"),
            ciqual("Jus de pomme", useCount = 3),
        )

        assertEquals(
            candidats.sortedByDescending { FoodRanking.score(it, "pomme") },
            candidats.sortedByDescending { FoodRanking.confidence(it, "pomme") },
        )
    }

    @Test
    fun `un nom exact se remplit tout seul`() {
        assertEquals(MatchVerdict.AUTOMATIC, verdictFor(FoodRanking.confidence(ciqual("Miel"), "miel")))
    }

    @Test
    fun `un prefixe seul demande une relecture`() {
        val riz = ciqual("Riz blanc, cuit")

        assertEquals(MatchVerdict.REVIEW, verdictFor(FoodRanking.confidence(riz, "riz")))
    }

    @Test
    fun `le meme prefixe sur un aliment deja mange se remplit tout seul`() {
        // Le sens du x1,5 de docs/04 : une ressemblance moyenne sur un aliment
        // familier vaut mieux que la meme sur un inconnu, et le poids doit pouvoir
        // faire franchir le seuil. Meme fiche, meme requete que le cas precedent :
        // seul l'usage les separe.
        val riz = ciqual("Riz blanc, cuit", useCount = 12)

        assertEquals(MatchVerdict.AUTOMATIC, verdictFor(FoodRanking.confidence(riz, "riz")))
    }

    @Test
    fun `un mot perdu dans un long libelle ne donne aucune correspondance`() {
        // « pomme » ne doit pas se resoudre en silence sur une compote de pommes et
        // de poires : le mot y est, mais pas comme mot, et le libelle est long.
        val compote = ciqual("Compote de pommes et de poires, allégée en sucres, préemballée")

        assertEquals(MatchVerdict.NONE, verdictFor(FoodRanking.confidence(compote, "pomme")))
    }

    private fun ciqual(name: String, useCount: Int = 0) = Food(
        id = FoodId(name),
        source = FoodSource.CIQUAL,
        sourceRef = name,
        name = name,
        per100g = NutrientValues(kcal = 100.0),
        useCount = useCount,
    )

    private companion object {
        val POMME = Food(
            id = FoodId("pomme"),
            source = FoodSource.CIQUAL,
            name = "Pomme, chair et peau, crue",
            per100g = NutrientValues(kcal = 54.0),
        )

        val POMME_DE_TERRE = Food(
            id = FoodId("patate"),
            source = FoodSource.CIQUAL,
            name = "Pomme de terre à chair farineuse, crue",
            per100g = NutrientValues(kcal = 80.0),
        )
    }
}
