package app.hexavore.domain.progress

import java.time.LocalDate

/**
 * Une suite de jours ininterrompue.
 *
 * Deux séries courent en parallèle, et elles ne mesurent pas la même chose : l'une dit
 * qu'on **note**, l'autre qu'on **tient**. La première est celle qui combat l'abandon —
 * c'est la raison d'être du projet ([01][perimetre]) — la seconde récompense la
 * journée qui est restée dans sa fourchette ([PerfectDay]).
 *
 * [perimetre]: docs/01-perimetre.md
 */
data class Streaks(
    /** Jours consécutifs portant au moins un plat. */
    val logging: Int,
    /** Jours consécutifs **clos** et parfaits. */
    val perfect: Int,
) {
    companion object {
        val NONE = Streaks(logging = 0, perfect = 0)
    }
}

/**
 * La série de saisie, **aujourd'hui compris s'il est noté**.
 *
 * **Une journée en cours ne casse rien.** À huit heures du matin, personne n'a encore
 * noté son petit-déjeuner : compter à rebours depuis aujourd'hui remettrait la série à
 * zéro chaque nuit, et le seul chiffre que cette application montre en permanence
 * annoncerait un échec tous les matins. Le décompte part donc d'aujourd'hui quand il
 * est noté, et de la veille sinon.
 *
 * C'est la même règle que la pastille de [Notice.YESTERDAY_EMPTY][notice], vue de
 * l'autre côté : hier est le dernier jour sur lequel on peut porter un jugement.
 *
 * [notice]: app.hexavore.domain.notice.Notice
 */
fun loggingStreak(logged: Set<LocalDate>, today: LocalDate): Int {
    val start = if (today in logged) today else today.minusDays(1)
    return runLengthBefore(logged, start)
}

/**
 * La série parfaite, **sur les jours clos uniquement**.
 *
 * Une journée en cours n'est pas parfaite : elle n'est pas finie. Juger le jour courant
 * ferait passer chaque matin par « parfaite » — rien de noté, donc rien au-dessus du
 * plafond — puis par « ratée » au premier repas un peu large, et la série clignoterait
 * toute la journée. Elle se compte donc depuis hier, et le jour en cours s'affiche
 * comme ce qu'il est : en jeu.
 */
fun perfectStreak(perfect: Set<LocalDate>, today: LocalDate): Int = runLengthBefore(perfect, today.minusDays(1))

/**
 * Combien de jours consécutifs se terminent en [last], celui-ci compris.
 *
 * Remonte tant que la veille y est. Zéro dès que [last] n'y est pas : une série qui
 * sauterait un trou pour reprendre plus loin ne serait pas une série.
 */
private fun runLengthBefore(days: Set<LocalDate>, last: LocalDate): Int {
    var count = 0
    var cursor = last
    while (cursor in days) {
        count++
        cursor = cursor.minusDays(1)
    }
    return count
}
