package app.hexavore.domain.usecase

import app.hexavore.domain.diary.DiaryRepository
import app.hexavore.domain.diary.Dish
import app.hexavore.domain.diary.EntrySource
import app.hexavore.domain.goal.Goal
import app.hexavore.domain.goal.Goals
import app.hexavore.domain.goal.activeOn
import app.hexavore.domain.nutrition.MacroTotals
import app.hexavore.domain.progress.Badge
import app.hexavore.domain.progress.Level
import app.hexavore.domain.progress.PerfectDay
import app.hexavore.domain.progress.Points
import app.hexavore.domain.progress.Progress
import app.hexavore.domain.progress.ProgressStore
import app.hexavore.domain.progress.StoredProgress
import app.hexavore.domain.progress.Streaks
import app.hexavore.domain.progress.Tally
import app.hexavore.domain.progress.loggingStreak
import app.hexavore.domain.progress.perfectStreak
import app.hexavore.domain.time.Clock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.LocalDate

/**
 * Où en est la progression, à cet instant.
 *
 * ### Presque tout se dérive, très peu se range
 *
 * Les séries, les points et le compte des plats **se recalculent depuis le journal** à
 * chaque changement. C'est ce qui rend la récompense immédiate : noter un plat fait
 * monter les points dans la seconde, sans qu'aucune écriture n'ait à réussir d'abord.
 *
 * Ce que le journal ne peut pas redire — les paliers déjà pris, et les meilleurs scores
 * atteints — vient de [ProgressStore], et la lecture garde **le plus grand des deux**
 * ([D132][decisions]). Un plat corrigé l'an dernier ne doit pas coûter un niveau ; sans
 * ce plancher, corriger une erreur serait puni.
 *
 * ### La fenêtre, et pourquoi elle ne ment pas
 *
 * Le journal n'est relu que sur [WINDOW_DAYS] jours. Au-delà, le calcul dérivé
 * sous-estime — une série de quatre cents jours n'entre pas dans la fenêtre — et c'est
 * précisément là que le plancher rangé reprend la main : il retient ce que la fenêtre a
 * vu quand elle le voyait encore. Lire trois ans de journal à chaque frappe pour un
 * chiffre affiché en haut de l'accueil coûterait bien plus que cette approximation.
 *
 * [decisions]: docs/11-decisions.md
 */
class ObserveProgress(
    private val diary: DiaryRepository,
    private val goals: Goals,
    private val store: ProgressStore,
    private val clock: Clock,
) {
    operator fun invoke(): Flow<Progress> {
        val today = clock.today()
        return combine(
            diary.observeRange(today.minusDays(WINDOW_DAYS), today),
            goals.observeAll(),
            store.observe(),
        ) { dishes, allGoals, stored ->
            progressOf(dishes, allGoals, stored, today)
        }
    }

    private fun progressOf(
        dishes: List<Dish>,
        allGoals: List<Goal>,
        stored: StoredProgress,
        today: LocalDate,
    ): Progress {
        val byDay = dishes.groupBy { it.date }
        val logged = byDay.keys
        val perfect = byDay.filterKeys { it.isBefore(today) }.filterValues { isPerfect(it, allGoals) }.keys

        val streaks = Streaks(
            logging = loggingStreak(logged, today),
            perfect = perfectStreak(perfect, today),
        )
        // Le plus grand des deux, partout : la fenetre voit un passe recent, le
        // plancher se souvient du reste.
        val tally = Tally(
            bestStreak = maxOf(streaks.logging, stored.bestStreak),
            dishes = dishes.size,
            bestPerfectStreak = maxOf(streaks.perfect, stored.bestPerfectStreak),
            discovered = discoveries(dishes) + stored.unlocked.keys,
        )
        val points = maxOf(pointsOf(dishes, logged, perfect, today, stored.unlocked.keys), stored.points)

        return Progress(
            points = points,
            level = Level.of(points),
            streaks = streaks,
            tally = tally,
            unlocked = stored.unlocked,
            next = nextBadge(tally, stored.unlocked.keys),
        )
    }

    /**
     * Une journée close est-elle restée dans sa fourchette ?
     *
     * L'objectif consulté est **celui qui valait ce jour-là** ([D04][decisions]), avec
     * sa stratégie : c'est elle qui décide de quel côté la fourchette penche, et juger
     * une journée de perte sur la fourchette d'une prise de masse dirait l'inverse de la
     * vérité.
     *
     * [decisions]: docs/11-decisions.md
     */
    private fun isPerfect(ofDay: List<Dish>, allGoals: List<Goal>): Boolean {
        val goal = allGoals.activeOn(ofDay.first().date) ?: return false
        val totals = MacroTotals.of(ofDay.flatMap { it.entries }.map { it.macros })
        return PerfectDay.of(totals, goal.daily, goal.strategy)
    }

    /**
     * Ce que le journal vaut, en points.
     *
     * **Le jour en cours compte ses plats mais pas sa journée** : une journée n'est
     * close qu'une fois passée, et payer d'avance ferait monter puis redescendre le
     * total si le dernier repas n'était finalement pas noté.
     */
    private fun pointsOf(
        dishes: List<Dish>,
        logged: Set<LocalDate>,
        perfect: Set<LocalDate>,
        today: LocalDate,
        unlocked: Set<Badge>,
    ): Long {
        val closedDays = logged.count { it.isBefore(today) }
        return dishes.size.toLong() * Points.PER_DISH +
            closedDays.toLong() * Points.PER_LOGGED_DAY +
            perfect.size.toLong() * Points.PER_PERFECT_DAY +
            unlocked.sumOf { it.reward.toLong() }
    }

    /**
     * Les modes de saisie déjà essayés, lus dans les plats eux-mêmes.
     *
     * Rien à ranger : l'origine d'un plat est un fait qui ne se réécrit jamais
     * ([D32][decisions]), donc le journal sait déjà ce qui a été essayé. Un favori se
     * reconnaît à son lien, et non à sa source — un plat rejoué porte `FAVORITE`, mais
     * c'est celui qu'on a **enregistré** qui prouve qu'on sait le faire.
     *
     * [decisions]: docs/11-decisions.md
     */
    private fun discoveries(dishes: List<Dish>): Set<Badge> = buildSet {
        if (dishes.any { it.source == EntrySource.BARCODE }) add(Badge.FIRST_SCAN)
        if (dishes.any { it.source == EntrySource.PHOTO_AI }) add(Badge.FIRST_PHOTO)
        if (dishes.any { it.source == EntrySource.TEXT_AI }) add(Badge.FIRST_TEXT)
        if (dishes.any { it.favoriteId != null }) add(Badge.FIRST_FAVORITE)
    }

    /**
     * Le prochain palier à tomber.
     *
     * **Le plus proche de ce qu'on fait déjà**, et non le premier de la liste : celui
     * dont il manque le moins, rapporté à son seuil. Quelqu'un qui note tous les jours
     * sans jamais tenir sa fourchette doit voir arriver sa série, pas un palier de
     * justesse qu'il n'approche pas.
     *
     * La découverte en est exclue : « essayez l'appareil photo » n'est pas un objectif
     * qui se prépare, et l'annoncer comme le prochain palier reviendrait à réclamer une
     * dépense d'IA.
     */
    private fun nextBadge(tally: Tally, unlocked: Set<Badge>): Badge? = Badge.entries
        .filter { it !in unlocked && it.family != Badge.Family.DISCOVERY }
        .minByOrNull { badge ->
            val done = when (badge.family) {
                Badge.Family.STREAK -> tally.bestStreak
                Badge.Family.VOLUME -> tally.dishes
                Badge.Family.PERFECT -> tally.bestPerfectStreak
                Badge.Family.DISCOVERY -> 0
            }
            (badge.threshold - done).coerceAtLeast(0).toDouble() / badge.threshold
        }

    private companion object {
        /**
         * La profondeur de relecture du journal.
         *
         * Un peu plus d'un an : assez pour que le palier des 365 jours se constate sur
         * le journal seul, et assez court pour qu'une lecture reste une lecture.
         */
        const val WINDOW_DAYS = 400L
    }
}
