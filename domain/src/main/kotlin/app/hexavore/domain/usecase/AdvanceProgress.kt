package app.hexavore.domain.usecase

import app.hexavore.domain.progress.Badge
import app.hexavore.domain.progress.Progress
import app.hexavore.domain.progress.ProgressStore
import app.hexavore.domain.time.Clock

/**
 * Fige ce qui vient d'être atteint, et dit ce qui vient de tomber.
 *
 * **Séparé de [ObserveProgress] parce que lire et écrire ne sont pas la même chose.**
 * Un flux qui écrirait en passant se déclencherait lui-même : l'écriture ferait
 * réémettre le dépôt, le calcul repartirait, et rien ne dirait où la boucle s'arrête.
 * Ici, l'écran observe d'un côté et fait avancer de l'autre — et la boucle se ferme
 * d'elle-même, parce que rien n'est écrit une seconde fois.
 *
 * **Ce qui est renvoyé est ce qui vient de changer**, et il sert à une seule chose :
 * célébrer. Un palier déjà pris ne revient pas dans la liste, donc l'animation ne se
 * rejoue pas à chaque recomposition ni au retour sur l'écran.
 *
 * **L'ordre compte** : les paliers d'abord, les planchers ensuite. Un palier rapporte
 * des points ([Badge.reward]) ; les relever avant de le ranger écrirait un total qui
 * ignore ce que le déblocage vient d'ajouter, et le prochain calcul le rattraperait en
 * faisant remonter le niveau une seconde fois.
 */
class AdvanceProgress(private val store: ProgressStore, private val clock: Clock) {
    /**
     * @param progress ce que [ObserveProgress] vient de calculer.
     * @return les paliers franchis à l'instant, vides le reste du temps — c'est-à-dire
     *   presque toujours.
     */
    suspend operator fun invoke(progress: Progress): List<Badge> {
        val today = clock.today()
        val fallen = Badge.entries.filter { it !in progress.unlocked && progress.tally.reaches(it) }

        fallen.forEach { store.unlock(it, today) }
        store.raise(
            points = progress.points + fallen.sumOf { it.reward.toLong() },
            bestStreak = progress.tally.bestStreak,
            bestPerfectStreak = progress.tally.bestPerfectStreak,
        )
        return fallen
    }
}
