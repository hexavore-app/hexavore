package app.hexavore.feature.home

import app.hexavore.domain.goal.AdjustmentSuggestion
import app.hexavore.domain.usecase.AdjustmentResponse
import app.hexavore.domain.usecase.RespondToAdjustment
import app.hexavore.domain.usecase.SuggestGoalAdjustment
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * La carte d'adaptation hebdomadaire : ce qu'elle propose, et ce qu'on lui répond.
 *
 * **Un objet plutôt que deux paramètres**, et pour la raison que le projet a déjà
 * retenue trois fois — le seuil de paramètres a mordu quand le style d'affichage est
 * arrivé, et la réponse est de regrouper selon ce que les choses **sont** plutôt que de
 * relever le seuil ([D85][decisions], puis `DishGestures`, puis `DraftComposition`).
 *
 * Ce que ces deux-là ont en commun n'est pas d'être deux : c'est d'être **une
 * conversation** — une proposition, une réponse — là où le reste du modèle lit une
 * journée ou écrit un plat. Une troisième pièce de cette conversation viendrait ici ;
 * une lecture du journal, non.
 *
 * [decisions]: docs/11-decisions.md
 */
class DayAdjustment @Inject constructor(
    private val suggest: SuggestGoalAdjustment,
    // `respondTo` et non `respond` : une propriete qui porte le nom de la methode qui
    // l'appelle fait que la methode s'appelle elle-meme, et le compilateur l'accepte.
    private val respondTo: RespondToAdjustment,
) {
    fun suggestion(): Flow<AdjustmentSuggestion?> = suggest()

    suspend fun respond(response: AdjustmentResponse, shown: AdjustmentSuggestion) = respondTo(response, shown)
}
