package app.hexavore.feature.home

import app.hexavore.domain.appearance.DishDisplayStyle
import app.hexavore.domain.diary.DishId
import app.hexavore.domain.diary.PhotoFile
import app.hexavore.domain.usecase.ObserveDishPhotos
import app.hexavore.domain.usecase.ObserveDishStyle
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * Comment l'accueil montre ses plats : le style retenu, et leurs photos.
 *
 * **Né quand le seuil de paramètres a mordu**, et le découpage suit ce que les choses
 * sont plutôt qu'un compte, comme [DishGestures] et [DayAdjustment] avant lui. Ces deux
 * flux répondent à la même question — de quoi une ligne de plat est faite — et aucun des
 * deux ne décide de ce que la journée contient.
 *
 * Aucune règle ici : les deux lectures passent telles quelles. C'est un regroupement de
 * dépendances, et il ne prétend pas à autre chose.
 */
class DishPresentation @Inject constructor(
    private val observeDishStyle: ObserveDishStyle,
    private val observeDishPhotos: ObserveDishPhotos,
) {
    fun style(): Flow<DishDisplayStyle> = observeDishStyle()

    fun photos(): Flow<Map<DishId, PhotoFile>> = observeDishPhotos()
}
