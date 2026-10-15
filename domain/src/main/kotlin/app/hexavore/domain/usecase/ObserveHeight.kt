package app.hexavore.domain.usecase

import app.hexavore.domain.profile.Profiles
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

/**
 * La taille du profil : c'est sur elle qu'une pesée se juge ([D160][decisions]).
 *
 * Le journal de poids pose la même question que l'onboarding — ce poids va-t-il avec
 * cette taille ? —, et il la pose avant d'écrire, pour dire pourquoi son bouton reste
 * éteint plutôt que de laisser [RecordWeight] refuser en silence.
 *
 * `null` sans profil, ou quand il ne se lit pas : la pesée se juge alors sur les bornes de
 * toutes les tailles. Ne pas connaître la taille n'est pas une raison de refuser un écran.
 *
 * [decisions]: docs/11-decisions.md
 */
class ObserveHeight(private val profiles: Profiles) {
    operator fun invoke(): Flow<Double?> = profiles
        .observeProfile()
        .map { it?.heightCm }
        .catch { emit(null) }
}
