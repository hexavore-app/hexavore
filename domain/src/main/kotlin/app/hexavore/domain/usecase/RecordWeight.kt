package app.hexavore.domain.usecase

import app.hexavore.domain.profile.BodyBounds
import app.hexavore.domain.profile.Profiles
import app.hexavore.domain.profile.WeightEntry
import app.hexavore.domain.profile.WeightLog
import app.hexavore.domain.time.Clock
import kotlinx.coroutines.flow.first

/**
 * Noter une pesée.
 *
 * **Deux refus.** Une date à venir — [docs/02][parcours] interdit la saisie dans le
 * futur, ici comme dans le journal alimentaire — et un poids que ce corps ne peut pas
 * peser.
 *
 * ~~Aucune borne haute ni basse : un seuil inventé refuserait un jour la pesée de
 * quelqu'un de réel.~~ Le raisonnement oubliait l'autre moitié : sans seuil, 999 kg
 * entraient dans la courbe, puis dans le calcul de l'objectif, qui lit la dernière pesée.
 * Les bornes sont celles de [BodyBounds] et de l'**impossible ou presque**
 * ([D160][decisions]) — une faute de frappe, pas un corps rare —, jugées sur la taille du
 * profil quand il y en a une : c'est la même règle que l'onboarding et le profil, et une
 * pesée que le profil refuserait ne s'écrit pas ici non plus.
 *
 * Un refus n'est pas une erreur à afficher : l'écran désactive son bouton bien avant, et
 * dit pourquoi. C'est le filet, pour que la règle vive dans le domaine et non dans un
 * composant.
 *
 * [parcours]: docs/02-parcours-et-ecrans.md
 * [decisions]: docs/11-decisions.md
 */
class RecordWeight(private val weights: WeightLog, private val profiles: Profiles, private val clock: Clock) {
    /** @return `false` si la pesée est refusée, et rien n'est alors écrit. */
    suspend operator fun invoke(entry: WeightEntry): Boolean {
        // Un profil illisible ne bloque pas la pesee : on la juge alors sur les bornes
        // de toutes les tailles, plutot que de la refuser pour une raison qui n'est pas
        // la sienne.
        val heightCm = runCatching { profiles.observeProfile().first()?.heightCm }.getOrNull()
        if (entry.date.isAfter(clock.today()) || entry.weightKg !in BodyBounds.weightsFor(heightCm)) return false
        weights.record(entry)
        return true
    }
}
