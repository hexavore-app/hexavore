package app.hexavore.data.progress

import app.hexavore.core.database.dao.ProgressDao
import app.hexavore.core.database.entity.ProgressEntity
import app.hexavore.core.database.entity.UnlockedBadgeEntity
import app.hexavore.domain.concurrency.DispatcherProvider
import app.hexavore.domain.progress.Badge
import app.hexavore.domain.progress.ProgressStore
import app.hexavore.domain.progress.StoredProgress
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * La progression, adossée à Room.
 *
 * **Deux tables lues ensemble** : les planchers ne disent rien sans les paliers, et les
 * paliers ne se comprennent pas sans les planchers qui les ont fait tomber. Un appelant
 * qui les lirait séparément verrait, le temps d'une émission, un palier franchi sans
 * les points qu'il rapporte.
 *
 * **Une ligne absente vaut trois zéros**, et c'est le cas normal d'une installation
 * neuve — ou d'une base migrée, que [Migration7To8][migration] laisse volontairement
 * vide. Aucun chemin ne crée cette ligne « pour qu'elle existe » : elle apparaît au
 * premier relevé qui a quelque chose à figer.
 *
 * [migration]: app.hexavore.core.database.HexavoreDatabase
 */
@Singleton
class RoomProgressStore @Inject constructor(
    private val progress: ProgressDao,
    private val dispatchers: DispatcherProvider,
) : ProgressStore {
    override fun observe(): Flow<StoredProgress> =
        combine(progress.observeProgress(), progress.observeBadges()) { row, badges ->
            StoredProgress(
                points = row?.points ?: 0,
                bestStreak = row?.bestStreak ?: 0,
                bestPerfectStreak = row?.bestPerfectStreak ?: 0,
                unlocked = badges.mapNotNull { it.toDomain() }.toMap(),
            )
        }.flowOn(dispatchers.io)

    /**
     * Relève les planchers, **et n'écrit que s'ils montent**.
     *
     * La comparaison est ici et non chez l'appelant, pour deux raisons qui vont
     * ensemble : c'est ici qu'on sait ce qui est écrit, et une écriture inutile
     * réémettrait le flux — donc relancerait le calcul de celui qui vient de le
     * demander, indéfiniment.
     *
     * Aucune transaction : la ligne est unique, l'écriture est un seul `upsert`, et il
     * n'y a rien à tenir ensemble. La lecture qui la précède peut être doublée par un
     * relevé concurrent ; au pire, le même plancher est écrit deux fois avec la même
     * valeur.
     */
    override suspend fun raise(points: Long, bestStreak: Int, bestPerfectStreak: Int) = withContext(dispatchers.io) {
        val current = progress.observeProgress().first()
        val raised = ProgressEntity(
            points = maxOf(points, current?.points ?: 0),
            bestStreak = maxOf(bestStreak, current?.bestStreak ?: 0),
            bestPerfectStreak = maxOf(bestPerfectStreak, current?.bestPerfectStreak ?: 0),
        )
        if (raised.points == current?.points &&
            raised.bestStreak == current.bestStreak &&
            raised.bestPerfectStreak == current.bestPerfectStreak
        ) {
            return@withContext
        }
        progress.upsert(raised)
    }

    override suspend fun unlock(badge: Badge, on: LocalDate) = withContext(dispatchers.io) {
        progress.unlock(UnlockedBadgeEntity(badge = badge.name, unlockedOn = on.toString()))
    }
}

/**
 * Une ligne de palier, relue.
 *
 * **`null` quand le nom n'existe plus dans le code**, et la ligne reste en base : un
 * palier retiré d'une version laisse sa date tranquille, qu'une restauration sur une
 * version plus ancienne retrouvera. La lecture prudente est la même règle que celle des
 * sources d'entrée disparues.
 */
private fun UnlockedBadgeEntity.toDomain(): Pair<Badge, LocalDate>? {
    val known = Badge.entries.firstOrNull { it.name == badge } ?: return null
    return runCatching { known to LocalDate.parse(unlockedOn) }.getOrNull()
}
