package app.hexavore.domain.usecase

import app.hexavore.domain.profile.WeightLog
import java.time.LocalDate

/**
 * Retire la pesée d'un jour ([D165][decisions]).
 *
 * **Tout de suite, et non au bout de la barre d'annulation**, pour la raison de
 * [RestoreWeight] et de `RestoreDish` : une suppression différée pendant cinq secondes
 * disparaîtrait avec un processus tué, et l'on retrouverait une pesée qu'on croyait
 * retirée.
 *
 * La courbe, la pente de l'adaptation et le poids du calcul la lisent tous dans le
 * journal : aucun ne garde de copie à défaire. Un objectif déjà enregistré, lui, ne
 * bouge pas — il a été calculé un jour donné, sur ce que l'on savait ce jour-là.
 *
 * [decisions]: docs/11-decisions.md
 */
class DeleteWeight(private val weights: WeightLog) {
    suspend operator fun invoke(date: LocalDate) = weights.delete(date)
}
