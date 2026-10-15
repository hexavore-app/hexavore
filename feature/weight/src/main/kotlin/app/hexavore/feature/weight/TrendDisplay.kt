package app.hexavore.feature.weight

import app.hexavore.domain.usecase.ObserveWeightTrendShown
import app.hexavore.domain.usecase.ShowWeightTrend
import javax.inject.Inject

/**
 * La case sous la courbe : ce qu'elle a retenu, et ce qui la change.
 *
 * **Un porteur plutôt que deux paramètres** sur un modèle qui en compte déjà six : les
 * deux vont ensemble — une lecture sans son écriture ne ferait pas une case — et c'est
 * la forme que l'accueil a prise pour la présentation de ses plats ([D159][decisions]).
 *
 * [decisions]: docs/11-decisions.md
 */
internal class TrendDisplay @Inject constructor(val observe: ObserveWeightTrendShown, val show: ShowWeightTrend)
