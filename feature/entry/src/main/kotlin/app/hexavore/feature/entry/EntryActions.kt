package app.hexavore.feature.entry

import androidx.compose.runtime.Immutable
import app.hexavore.domain.diary.DraftLineId
import app.hexavore.domain.diary.MealMoment

/**
 * Ce que l'écran de validation peut déclencher.
 *
 * Un objet plutôt que six paramètres : une composable sans état expose ses
 * événements en paramètres, mais des lambdas transmises de main en main sur trois
 * niveaux rendent chaque signature illisible et chaque ajout coûteux. Regroupées,
 * elles se transmettent d'un mot et ne changent plus la forme des fonctions qui les
 * traversent.
 *
 * [Immutable] parce qu'aucun de ces champs ne change après construction : Compose
 * peut alors sauter la recomposition des sous-arbres qui ne reçoivent que ça.
 */
@Immutable
internal data class EntryActions(
    val onLineEdit: (DraftLineId, LineEdit) -> Unit,
    /** Ouvre la recherche : la meme que celle de l'accueil, et le choix revient ici. */
    val onAddFood: () -> Unit,
    val onRemoveLine: (DraftLineId) -> Unit,
    val onSave: () -> Unit,
    /**
     * Met le plat en favori sous ce nom.
     *
     * Le nom vient de l'écran parce que c'est lui qui le fait saisir ; un nom déjà
     * pris revient dans l'état, pas en valeur de retour ([D62][decisions]).
     *
     * [decisions]: docs/11-decisions.md
     */
    /** Le titre du plat vient d'être écrit. */
    val onTitle: (String) -> Unit,
    /** Une pastille de moment a été touchée : elle remplace le titre. */
    val onMoment: (MealMoment) -> Unit,
    /** La boîte de nom s'ouvre : l'écran donne le titre, le modèle cherche un nom libre. */
    val onNaming: (String, (String, Int) -> String) -> Unit,
    /** La boîte de nom se referme sans rien enregistrer. */
    val onDismissNaming: () -> Unit,
    val onFavorite: (String) -> Unit,
    /** Éteindre l'étoile **supprime** le favori : c'est le seul chemin pour l'ôter. */
    val onUnfavorite: () -> Unit,
    val onRetry: () -> Unit,
    val onClose: () -> Unit,
)
