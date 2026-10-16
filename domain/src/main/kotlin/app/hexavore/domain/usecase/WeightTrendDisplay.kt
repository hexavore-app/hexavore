package app.hexavore.domain.usecase

import app.hexavore.domain.appearance.AppearanceSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch

/**
 * La tendance est-elle tracée sur la courbe de poids ?
 *
 * **Un fichier illisible vaut « oui »**, le défaut : c'est la tendance qui porte
 * l'information de la courbe, et une préférence qu'on ne sait pas relire ne doit pas
 * l'éteindre en silence ([D159][decisions]).
 *
 * [decisions]: docs/11-decisions.md
 */
class ObserveWeightTrendShown(private val settings: AppearanceSettings) {
    operator fun invoke(): Flow<Boolean> = settings.observeWeightTrend().catch { emit(true) }
}

/** La case sous la courbe : tracer la tendance, ou ne montrer que les pesées. */
class ShowWeightTrend(private val settings: AppearanceSettings) {
    suspend operator fun invoke(shown: Boolean) = settings.setWeightTrend(shown)
}
