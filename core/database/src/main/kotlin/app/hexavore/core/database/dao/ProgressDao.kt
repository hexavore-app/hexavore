package app.hexavore.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import app.hexavore.core.database.entity.ProgressEntity
import app.hexavore.core.database.entity.UnlockedBadgeEntity
import kotlinx.coroutines.flow.Flow

/**
 * La progression : ses trois planchers, et les paliers franchis.
 *
 * @see docs/07-modele-de-donnees.md
 */
@Dao
interface ProgressDao {
    @Query("SELECT * FROM progress WHERE id = :id")
    fun observeProgress(id: String = ProgressEntity.SINGLETON): Flow<ProgressEntity?>

    @Query("SELECT * FROM unlocked_badge ORDER BY unlocked_on ASC")
    fun observeBadges(): Flow<List<UnlockedBadgeEntity>>

    @Upsert
    suspend fun upsert(progress: ProgressEntity)

    /**
     * Range un palier, et **ne touche pas à celui qui y est déjà**.
     *
     * `IGNORE` et non `REPLACE` : la date d'un palier est celle du jour où il est tombé.
     * Un `REPLACE` la réécrirait à chaque relevé, et l'écran finirait par annoncer que
     * la série de cent jours a été obtenue ce matin.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun unlock(badge: UnlockedBadgeEntity)
}
