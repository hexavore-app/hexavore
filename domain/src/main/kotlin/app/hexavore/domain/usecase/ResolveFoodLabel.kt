package app.hexavore.domain.usecase

import app.hexavore.domain.food.Food
import app.hexavore.domain.food.FoodSearch
import app.hexavore.domain.language.ContentLanguages
import app.hexavore.domain.resolution.LabelMatch
import app.hexavore.domain.resolution.depluralise
import app.hexavore.domain.resolution.matchFor
import app.hexavore.domain.resolution.normaliseLabel
import kotlinx.coroutines.flow.first

/**
 * Ce qu'un libellé reconnu désigne dans le catalogue : le brut d'abord, le singulier
 * en rattrapage.
 *
 * **L'ordre est la fonctionnalité**, comme il l'était pour `LookupBarcode`. L'index
 * de l'ANSES garde ses pluriels — 32 % de ses 3 484 libellés en portent un — et les
 * deux recherches comparent des mots entiers ou des sous-chaînes entières :
 * dépluraliser d'emblée ferait perdre « haricots verts », que la requête telle qu'elle
 * vient trouve. Le second essai ne coûte donc rien dans le cas courant, où il n'a pas
 * lieu, et ne peut rien dégrader dans l'autre, où la première requête n'avait rien
 * rendu ([D74][decisions], [D75][decisions]).
 *
 * **Ce cas d'usage n'est pas encore le `NutritionResolver` de [docs/04][sources]**,
 * et ne porte pas son nom pour cette raison : il en tient les étapes 1 à 3 — normaliser,
 * chercher des candidats, décider — et laisse la quatrième, le repli IA groupé, à la
 * livraison qui aura un fournisseur à appeler. Un nom qui promet quatre étapes pour
 * trois est une documentation fausse dans le code lui-même.
 *
 * **Une fiche de l'ANSES rendue ici peut porter un identifiant provisoire**, comme
 * n'importe quel résultat de recherche : c'est `FoodStore.place` qui la rend
 * désignable par une entrée de journal, et c'est l'écran de validation qui l'appellera
 * ([D51][decisions]). Rien n'est écrit ici — résoudre est une lecture.
 *
 * [decisions]: docs/11-decisions.md
 * [sources]: docs/04-sources-de-donnees.md
 */
class ResolveFoodLabel(private val foods: FoodSearch, private val languages: ContentLanguages) {
    suspend operator fun invoke(label: String): LabelMatch {
        // Relue a chaque appel : un changement de langue ne recree pas les `ViewModel`
        // qui portent ce cas d'usage, et une langue retenue a la construction ferait
        // retirer des articles francais d'un libelle anglais.
        val language = languages.current()
        val normalised = normaliseLabel(label, language)
        val direct = candidates(normalised)
        if (direct.isNotEmpty()) return matchFor(direct, normalised)

        val singular = depluralise(normalised, language)
        // Un second essai identique au premier rendrait la meme chose : la
        // depluralisation n'a pas toujours de quoi mordre.
        val retried = if (singular == normalised) emptyList() else candidates(singular)
        return matchFor(retried, singular)
    }

    /**
     * Un instantané de la recherche, là où l'écran en prend le flux.
     *
     * Une résolution répond à une question posée une fois, sur un libellé qui ne
     * changera plus ; il n'y a personne pour regarder ses résultats se rafraîchir.
     *
     * La limite est large pour que **la troncature du port ne décide de rien** :
     * quatre candidats suffiraient à la décision, mais le port tronque selon son
     * propre classement, qui n'est pas garanti être celui-ci.
     */
    private suspend fun candidates(query: String): List<Food> = foods.search(query, limit = CANDIDATE_LIMIT).first()
}

private const val CANDIDATE_LIMIT = 20
