package app.hexavore.domain.usecase

import app.hexavore.domain.profile.WeightEntry
import app.hexavore.domain.profile.WeightLog
import kotlinx.coroutines.flow.first

/**
 * Remet une pesée qu'on vient de retirer : l'annulation de [DeleteWeight].
 *
 * **Sans les bornes de [RecordWeight]**, et c'est voulu : la pesée a été acceptée
 * quand on l'a notée. La refuser maintenant — parce que la taille du profil a changé
 * entre-temps — ferait échouer en silence un « Annuler » qu'on vient de toucher.
 *
 * **Elle ne recouvre pas une pesée écrite entre-temps.** Si l'on a noté une nouvelle
 * pesée pour ce jour pendant que la barre était affichée, c'est elle qui compte : la
 * dernière du jour gagne, et la plus récente n'est pas celle qu'on vient d'annuler.
 *
 * @return `false` si une pesée du même jour a été notée entre-temps, et rien n'est
 *   alors écrit.
 */
class RestoreWeight(private val weights: WeightLog) {
    suspend operator fun invoke(entry: WeightEntry): Boolean {
        if (weights.observeHistory().first().any { it.date == entry.date }) return false
        weights.record(entry)
        return true
    }
}
