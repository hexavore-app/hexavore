package app.hexavore.core.testing

import app.hexavore.domain.progress.Badge
import app.hexavore.domain.progress.ProgressStore
import app.hexavore.domain.progress.StoredProgress
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalDate

/**
 * Ce que la progression a figé, en mémoire.
 *
 * **Les planchers ne descendent pas, ici comme en base.** C'est la règle du port et non
 * un détail de l'adaptateur : un faux qui écraserait les valeurs laisserait passer une
 * implémentation qui les écrase aussi, et le défaut ne se verrait que sur un appareil
 * où quelqu'un vient de perdre un niveau en corrigeant un vieux plat.
 *
 * **Un palier déjà rangé garde sa date.** Même raison : c'est ce qui empêche l'écran
 * d'annoncer que la série de cent jours a été obtenue ce matin.
 */
class InMemoryProgressStore(initial: StoredProgress = StoredProgress()) : ProgressStore {
    private val state = MutableStateFlow(initial)

    /** Ce qui est rangé, pour qu'un cas l'affirme sans passer par le flux. */
    val stored: StoredProgress get() = state.value

    /** Le nombre d'écritures effectives, pour vérifier qu'un relevé inutile n'écrit rien. */
    var writes: Int = 0
        private set

    override fun observe(): Flow<StoredProgress> = state.asStateFlow()

    override suspend fun raise(points: Long, bestStreak: Int, bestPerfectStreak: Int) {
        val raised = state.value.copy(
            points = maxOf(points, state.value.points),
            bestStreak = maxOf(bestStreak, state.value.bestStreak),
            bestPerfectStreak = maxOf(bestPerfectStreak, state.value.bestPerfectStreak),
        )
        if (raised == state.value) return
        writes++
        state.value = raised
    }

    override suspend fun unlock(badge: Badge, on: LocalDate) {
        if (badge in state.value.unlocked) return
        writes++
        state.value = state.value.copy(unlocked = state.value.unlocked + (badge to on))
    }
}
