package app.hexavore.feature.home

import app.hexavore.domain.usecase.DeleteDish
import app.hexavore.domain.usecase.RestoreDish
import app.hexavore.domain.usecase.ToggleDishFavorite
import javax.inject.Inject

/**
 * Les trois gestes qu'on pose sur un plat déjà noté.
 *
 * **Un objet plutôt que quatre paramètres**, et pour la raison que le projet a déjà
 * retenue deux fois : le seuil de huit paramètres a mordu quand la journée relue est
 * arrivée, et la réponse est de regrouper selon ce que les choses **sont** plutôt que
 * de relever le seuil ([D85][decisions], où `DraftFavorites` est né de la même façon).
 *
 * Ce qu'ils ont en commun n'est pas d'être trois : c'est de porter sur un plat
 * **écrit**, par opposition à tout ce que le modèle fait d'autre — lire une journée,
 * savoir s'il y a une clé d'IA. Un cinquième geste sur un plat viendra ici ; une
 * lecture, non.
 *
 * [decisions]: docs/11-decisions.md
 */
class DishGestures @Inject constructor(
    val deleteDish: DeleteDish,
    val restoreDish: RestoreDish,
    val toggleFavorite: ToggleDishFavorite,
)
