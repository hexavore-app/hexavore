package app.hexavore.feature.weight

import app.hexavore.domain.usecase.DeleteWeight
import app.hexavore.domain.usecase.RecordWeight
import app.hexavore.domain.usecase.RestoreWeight
import javax.inject.Inject

/**
 * Les trois gestes qui écrivent dans le journal de poids : noter, retirer, rendre.
 *
 * **Un objet plutôt que trois paramètres**, comme `DishGestures` sur l'accueil : le
 * seuil de huit paramètres aurait mordu au premier geste ajouté, et la réponse est de
 * regrouper selon ce que les choses sont plutôt que de relever le seuil. Ce qu'ils ont
 * en commun est d'écrire ; les lectures — la courbe, les unités, la taille — restent à
 * part.
 */
internal class WeighingGestures @Inject constructor(
    val record: RecordWeight,
    val delete: DeleteWeight,
    val restore: RestoreWeight,
)
