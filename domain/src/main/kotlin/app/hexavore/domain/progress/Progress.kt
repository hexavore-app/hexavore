package app.hexavore.domain.progress

import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/**
 * Où en est la progression, tout entière.
 *
 * Un seul objet plutôt que quatre flux : l'accueil en montre trois lignes, l'écran
 * dédié en montre tout, et les deux doivent parler du même instant — une série lue
 * avant un déblocage et un niveau lu après raconteraient deux histoires.
 */
data class Progress(
    val points: Long,
    val level: Level,
    val streaks: Streaks,
    val tally: Tally,
    /** Les paliers débloqués, avec le jour où ils l'ont été. */
    val unlocked: Map<Badge, LocalDate>,
    /**
     * Le prochain palier à tomber, ou `null` quand ils sont tous pris.
     *
     * **Calculé ici et non à l'écran** : « ce qu'il reste à faire » est la seule
     * information qui fait revenir demain, et deux écrans qui la dériveraient chacun
     * finiraient par désigner deux paliers différents.
     */
    val next: Badge?,
) {
    /** Ce palier est-il pris ? */
    operator fun contains(badge: Badge): Boolean = badge in unlocked

    companion object {
        val NONE = Progress(
            points = 0,
            level = Level.of(0),
            streaks = Streaks.NONE,
            tally = Tally(),
            unlocked = emptyMap(),
            next = Badge.STREAK_3,
        )
    }
}

/**
 * Ce que la progression garde, et que le journal ne peut pas redire.
 *
 * **Trois nombres et une liste de dates.** Tout le reste — la série en cours, les
 * points du jour, le nombre de plats — se dérive du journal, qui est la seule source
 * de vérité de ce qui a été mangé. Ce qui est rangé ici est d'une autre nature : c'est
 * ce qu'on a **traversé**, et qu'aucun recalcul ne retrouverait après une correction.
 *
 * **Les nombres sont des planchers, pas des totaux.** Ils ne descendent jamais : un
 * plat supprimé l'an dernier ne doit pas coûter un niveau, sans quoi corriger une
 * erreur serait puni et plus personne ne corrigerait rien. La lecture prend donc
 * toujours le plus grand des deux — ce que le journal donne, et ce qui est rangé.
 *
 * @see docs/07-modele-de-donnees.md
 */
interface ProgressStore {
    fun observe(): Flow<StoredProgress>

    /**
     * Relève les planchers, et n'écrit que s'ils montent.
     *
     * L'implémentation compare avant d'écrire : un flux qui se réémettrait à chaque
     * relevé ferait boucler l'écran qui l'observe et qui le déclenche.
     */
    suspend fun raise(points: Long, bestStreak: Int, bestPerfectStreak: Int)

    /** Range un palier avec sa date. Un palier déjà pris ne change pas de date. */
    suspend fun unlock(badge: Badge, on: LocalDate)
}

/** Ce que le stockage rend. */
data class StoredProgress(
    val points: Long = 0,
    val bestStreak: Int = 0,
    val bestPerfectStreak: Int = 0,
    val unlocked: Map<Badge, LocalDate> = emptyMap(),
)
