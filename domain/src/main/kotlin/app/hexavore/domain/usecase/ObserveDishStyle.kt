package app.hexavore.domain.usecase

import app.hexavore.domain.appearance.AppearanceSettings
import app.hexavore.domain.appearance.DishDisplayStyle
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

/**
 * Le style d'affichage des plats.
 *
 * **Une question, un endroit.** L'accueil la pose pour dessiner ses plats, les réglages
 * pour cocher la bonne case. Chacun lisant le magasin pour son compte, chacun aurait
 * décidé ce que vaut un réglage illisible — et l'un des deux aurait fini par décider
 * autrement, donc par afficher une liste dans un style que la case ne montre pas.
 * C'est le raisonnement d'`ObserveUnitSystem`, appliqué une seconde fois
 * ([D111][decisions]).
 *
 * **Un fichier illisible rend le simplifié**, qui est le défaut d'une installation
 * neuve. Le détaillé serait le pire des deux replis : il est plus long à lire, et un
 * réglage qu'on croit avoir mis se retrouverait ignoré sans un mot.
 *
 * [decisions]: docs/11-decisions.md
 */
class ObserveDishStyle(private val settings: AppearanceSettings) {
    operator fun invoke(): Flow<DishDisplayStyle> = settings
        .observeDishStyle()
        .map { it }
        .catch { emit(DishDisplayStyle.SIMPLE) }
}
