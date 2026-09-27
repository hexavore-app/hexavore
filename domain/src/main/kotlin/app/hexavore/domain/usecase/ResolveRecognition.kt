package app.hexavore.domain.usecase

import app.hexavore.domain.ai.EstimationOutcome
import app.hexavore.domain.ai.NutritionEstimator
import app.hexavore.domain.ai.Recognition
import app.hexavore.domain.ai.RecognizedItem
import app.hexavore.domain.diary.DraftLine
import app.hexavore.domain.diary.EntryDraft
import app.hexavore.domain.diary.EntrySource
import app.hexavore.domain.diary.QuantityUnit
import app.hexavore.domain.diary.Suggestion
import app.hexavore.domain.food.Food
import app.hexavore.domain.language.ContentLanguage
import app.hexavore.domain.language.ContentLanguages
import app.hexavore.domain.nutrition.NutrientValues
import app.hexavore.domain.resolution.MatchVerdict
import app.hexavore.domain.resolution.convertToGrams

/**
 * Ce qu'une reconnaissance devient : un brouillon, ligne par ligne.
 *
 * **C'est la jonction que quatre livraisons attendaient.** Le contrat de
 * reconnaissance, la conversion des quantités, le score de décision et la recherche de
 * candidats existaient chacun avec ses tests et **aucun appelant** ; c'est ici qu'ils
 * se chaînent, et l'écran de validation reçoit un `EntryDraft` comme il en reçoit
 * depuis la tranche 2.
 *
 * L'ordre des étapes est celui de [docs/04][sources] : identifier l'aliment
 * **d'abord**, convertir la quantité **ensuite**. Il n'est pas interchangeable — la
 * portion nommée de la fiche l'emporte sur le forfait ([D73][decisions]), donc
 * convertir avant de savoir quelle fiche on vise reviendrait à appliquer le forfait à
 * tous les coups, et à se tromper d'un facteur six sur un bol de céréales.
 *
 * **Puis l'étape 4, et une seule fois pour toutes les lignes.** Ce que le catalogue
 * n'a pas rejoint part en un appel groupé au modèle, qui estime des macros pour 100 g.
 * Un appel par ligne aurait coûté cinq requêtes là où une suffit, et c'est
 * l'utilisateur qui paie.
 *
 * **Rien n'est écrit nulle part.** Résoudre est une lecture ; c'est l'enregistrement du
 * brouillon qui verse les fiches au catalogue — et une estimation n'en est pas une, ce
 * qui suffit à la tenir hors du catalogue sans règle supplémentaire.
 *
 * [sources]: docs/04-sources-de-donnees.md
 * [decisions]: docs/11-decisions.md
 */
class ResolveRecognition(
    private val resolve: ResolveFoodLabel,
    private val create: CreateDraft,
    private val estimate: NutritionEstimator,
    private val languages: ContentLanguages,
) {
    suspend operator fun invoke(recognition: Recognition, source: EntrySource): EntryDraft {
        // Lue une fois pour toute la reconnaissance, et passee aux lignes : les cinq
        // lignes d'un plat viennent du meme appel et de la meme table, donc de la meme
        // langue. La relire par ligne laisserait un plat moitie francais.
        val language = languages.current()
        val resolved = recognition.items.map { resolveLine(it, language) }
        return create(source, resolved.completedByEstimate())
    }

    /**
     * Une ligne, telle que la résolution la rend.
     *
     * **Un libellé non résolu donne quand même une ligne**, avec son nom et sa
     * quantité. L'écarter silencieusement ferait disparaître un aliment que
     * l'utilisateur a bel et bien mangé — et il ne saurait pas lequel.
     */
    private suspend fun resolveLine(item: RecognizedItem, language: ContentLanguage): DraftLine {
        // **Le choix du modèle l'emporte, et ne se relit pas.** Il a vu l'assiette, il
        // a écrit le libellé, et on lui a montré ce que le catalogue propose : c'est
        // mieux informé qu'un score de ressemblance de chaînes. Rechercher malgré tout
        // pour comparer ferait deux juges qui se contredisent, et il faudrait alors
        // décider lequel a tort — ce que rien ne permet de faire.
        item.chosen?.let { return chosenLine(item, it, language) }

        val match = resolve(item.label)
        val converted = convertToGrams(item.quantity, item.unit, language, match.food, estimated = item.grams)
        val line = match.food?.let(create::line) ?: create.line().copy(name = item.label)

        return line
            .measured(converted.grams, QuantityUnit.Gram)
            .copy(
                suggestion = Suggestion(
                    confidence = item.confidence,
                    verdict = match.verdict,
                    alternatives = match.alternatives,
                    estimated = converted.guessed,
                ),
            )
    }

    /**
     * La ligne d'une fiche que le modèle a désignée.
     *
     * **Le verdict est `AUTOMATIC`, et sans alternatives.** Les valeurs viennent de la
     * table de l'ANSES et non du modèle : ce n'est pas une estimation, et le marqueur
     * pointillé mentirait. Proposer des alternatives reviendrait à signaler chaque
     * ligne en permanence — et un signal permanent ne signale plus rien.
     *
     * La confiance affichée reste **celle du modèle sur son identification**, qui est
     * ce que l'écran montre ligne par ligne. Elle ne devient pas 1 sous prétexte qu'il
     * a choisi dans une liste : il a pu choisir le moins mauvais.
     */
    private fun chosenLine(item: RecognizedItem, food: Food, language: ContentLanguage): DraftLine {
        val converted = convertToGrams(item.quantity, item.unit, language, food, estimated = item.grams)
        return create.line(food)
            .measured(converted.grams, QuantityUnit.Gram)
            .copy(
                suggestion = Suggestion(
                    confidence = item.confidence,
                    verdict = MatchVerdict.AUTOMATIC,
                    alternatives = emptyList(),
                    estimated = converted.guessed,
                ),
            )
    }

    /**
     * Les lignes que le catalogue n'a pas rejointes, complétées par le modèle.
     *
     * **Un seul appel, et aucun quand tout est résolu** — le cas courant. Une liste
     * vide ne part jamais sur le réseau : elle ne rendrait rien et se paierait.
     *
     * Un échec ne fait rien tomber : les lignes restent telles quelles, sans valeurs,
     * et l'écran de validation dit déjà qu'une ligne sans énergie n'est pas
     * enregistrable. C'est ce que [docs/04][sources] veut dire par « présentée à zéro »
     * — à ceci près qu'un champ vide vaut **inconnu** dans ce projet, jamais zéro : un
     * zéro affiché serait une affirmation que personne n'a faite.
     *
     * [sources]: docs/04-sources-de-donnees.md
     */
    private suspend fun List<DraftLine>.completedByEstimate(): List<DraftLine> {
        val unresolved = filter { it.suggestion?.verdict == MatchVerdict.NONE }
        if (unresolved.isEmpty()) return this

        val outcome = estimate.estimate(unresolved.map { it.name })
        val estimates = (outcome as? EstimationOutcome.Estimated)
            ?.foods
            ?.associate { it.label to it.per100g }
            .orEmpty()

        return map { line -> estimates[line.name]?.let { line.estimatedFrom(it) } ?: line }
    }

    /**
     * La ligne, remplie depuis une estimation.
     *
     * [DraftLine.reference] est posée **avant** le recalcul : c'est elle qui permet à
     * la quantité de rejouer la règle de trois, exactement comme pour une fiche. Sans
     * elle, corriger « 120 g » en « 150 g » laisserait les valeurs d'origine, et
     * personne ne comprendrait pourquoi.
     *
     * Aucun `foodId`, aucune fiche : c'est ce qui tient l'estimation hors du catalogue,
     * sans qu'aucune règle n'ait à s'en souvenir à l'enregistrement.
     */
    private fun DraftLine.estimatedFrom(per100g: NutrientValues): DraftLine = copy(reference = per100g)
        .measured(quantity, unit)
        .copy(suggestion = suggestion?.copy(estimatedMacros = true))
}
