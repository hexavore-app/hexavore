package app.hexavore.domain.usecase

import app.hexavore.domain.ai.CatalogueTool
import app.hexavore.domain.ai.FoodCandidate
import app.hexavore.domain.ai.LabelCandidates
import app.hexavore.domain.ai.TOOL_CANDIDATES
import app.hexavore.domain.food.Food
import app.hexavore.domain.food.FoodSearch
import app.hexavore.domain.language.ContentLanguages
import app.hexavore.domain.resolution.depluralise
import app.hexavore.domain.resolution.normaliseLabel
import kotlinx.coroutines.flow.first

/**
 * Ce que le catalogue propose au modèle, libellé par libellé.
 *
 * **La même recherche que la résolution interne, moins la décision.** Normaliser, puis
 * réessayer au singulier si le brut ne rend rien : l'ordre est celui de
 * [ResolveFoodLabel], et pour la même raison — l'index de l'ANSES garde ses pluriels,
 * donc dépluraliser d'emblée ferait perdre « haricots verts ». Ce qui disparaît ici est
 * l'étape d'après : aucun score, aucun verdict, aucun choix. **Choisir est le travail
 * du modèle**, et c'est tout l'objet de l'outil.
 *
 * **Une fiche sans référence est écartée.** Le modèle désigne son choix par cette
 * chaîne ; une fiche qui n'en a pas — un aliment personnel sans origine — serait
 * proposée sans pouvoir être choisie, et le modèle passerait un tour à la nommer.
 *
 * **Rien n'est écrit.** Comme la résolution, c'est une lecture : c'est l'enregistrement
 * du brouillon qui verse une fiche au catalogue.
 */
class LookUpCandidates(private val foods: FoodSearch, private val languages: ContentLanguages) : CatalogueTool {
    override suspend fun candidatesFor(labels: List<String>): List<LabelCandidates> =
        labels.map { label -> LabelCandidates(label = label, candidates = search(label)) }

    private suspend fun search(label: String): List<FoodCandidate> {
        val language = languages.current()
        val normalised = normaliseLabel(label, language)
        val direct = candidates(normalised)
        if (direct.isNotEmpty()) return direct

        val singular = depluralise(normalised, language)
        // Un second essai identique au premier rendrait la meme chose : la
        // depluralisation n'a pas toujours de quoi mordre.
        return if (singular == normalised) emptyList() else candidates(singular)
    }

    private suspend fun candidates(query: String): List<FoodCandidate> = foods
        .search(query, limit = TOOL_CANDIDATES)
        .first()
        .mapNotNull { it.asCandidate() }
}

/**
 * Une fiche, réduite à ce qui aide à choisir — ou `null` si elle ne peut pas être
 * désignée.
 *
 * Le nom **long** et non le titre court : le raccourci existe pour tenir dans une liste
 * à l'écran, et ce qui aide un modèle à distinguer deux fiches voisines est précisément
 * ce que le raccourci enlève — « cru », « en conserve, égoutté », « sans matière
 * grasse ».
 */
private fun Food.asCandidate(): FoodCandidate? = sourceRef?.let { FoodCandidate(reference = it, food = this) }
