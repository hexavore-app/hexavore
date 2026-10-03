package app.hexavore.feature.entry

import app.hexavore.domain.usecase.CopyDishToDate
import app.hexavore.domain.usecase.DeleteDish
import javax.inject.Inject

/**
 * Les trois gestes de l'en-tête, ceux qui **sortent de l'écran**.
 *
 * **Un regroupement et non une couche de plus.** Recopier sur un autre jour, supprimer,
 * signaler une proposition fausse : les trois portent sur le plat entier, les trois
 * ferment l'écran ou ouvrent autre chose, et aucun ne touche à ce qu'on est en train de
 * modifier. C'est aussi pourquoi les trois demandent confirmation ([D143][decisions]).
 *
 * Passés un par un, ils poussaient le constructeur du `ViewModel` au-delà du seuil de
 * paramètres, et la réponse du projet est de regrouper selon ce que les choses sont
 * plutôt que de relever le seuil — c'est déjà ce qu'ont fait [DraftFavorites] et
 * [DraftReporting].
 *
 * [decisions]: docs/11-decisions.md
 */
class DraftFiling @Inject constructor(
    val copyTo: CopyDishToDate,
    val delete: DeleteDish,
    val reporting: DraftReporting,
)
