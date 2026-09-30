package app.hexavore.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import app.hexavore.core.database.entity.DishEntity
import app.hexavore.core.database.entity.FavoriteComponentEntity
import app.hexavore.core.database.entity.FavoriteDishEntity
import app.hexavore.core.database.entity.FoodEntity
import app.hexavore.core.database.entity.FoodEntryEntity
import app.hexavore.core.database.entity.GoalEntity
import app.hexavore.core.database.entity.ProfileEntity
import app.hexavore.core.database.entity.ProgressEntity
import app.hexavore.core.database.entity.UnlockedBadgeEntity
import app.hexavore.core.database.entity.WeightEntryEntity

/**
 * Toutes les tables d'un coup, en lecture.
 *
 * **Des DAO à part**, et non des méthodes ajoutées aux cinq autres. Ceux-là sont taillés
 * pour ce que les écrans demandent — une journée, une plage, un objectif courant ; ici
 * on veut tout, sans filtre.
 *
 * **Deux interfaces et non une**, parce que lire et écrire sont deux choses : la
 * sauvegarde n'appelle que la première, la restauration les deux, dans une transaction
 * que `RoomSnapshotStore` ouvre lui-même. Vider, en revanche, n'est pas une troisième
 * interface — c'est un balayage de tables, et [eraseUserData][app.hexavore.core.database.eraseUserData]
 * le dit en six lignes là où huit méthodes Room l'auraient dit en trente.
 *
 * @see docs/09-donnees-et-sauvegarde.md
 */
@Dao
interface BackupReadDao {
    @Query("SELECT * FROM profile WHERE id = :id")
    suspend fun profile(id: String = ProfileEntity.SINGLETON): ProfileEntity?

    @Query("SELECT * FROM goal ORDER BY started_at ASC")
    suspend fun goals(): List<GoalEntity>

    @Query("SELECT * FROM weight_entry ORDER BY date ASC")
    suspend fun weights(): List<WeightEntryEntity>

    @Transaction
    @Query("SELECT * FROM dish ORDER BY date ASC, logged_at ASC")
    suspend fun dishes(): List<DishWithEntries>

    @Query("SELECT * FROM food ORDER BY name ASC")
    suspend fun foods(): List<FoodEntity>

    @Transaction
    @Query("SELECT * FROM favorite_dish ORDER BY name ASC")
    suspend fun favorites(): List<FavoriteWithComponents>

    @Query("SELECT * FROM progress WHERE id = :id")
    suspend fun progress(id: String = ProgressEntity.SINGLETON): ProgressEntity?

    @Query("SELECT * FROM unlocked_badge ORDER BY unlocked_on ASC")
    suspend fun badges(): List<UnlockedBadgeEntity>
}

/**
 * Les dix insertions d'une restauration.
 *
 * **L'ordre d'appel suit les clés étrangères** : les aliments avant les lignes qui les
 * citent, les plats avant leurs lignes, les favoris avant leurs composants. Il est tenu
 * par l'appelant, qui ouvre la transaction — pas ici : une méthode par défaut ne peut
 * appeler que celles de sa propre interface, et rassembler les vingt-quatre pour cela
 * aurait fait une interface qu'on ne relit plus.
 */
@Dao
interface BackupWriteDao {
    @Insert
    suspend fun insertProfile(profile: ProfileEntity)

    @Insert
    suspend fun insertGoals(goals: List<GoalEntity>)

    @Insert
    suspend fun insertWeights(weights: List<WeightEntryEntity>)

    @Insert
    suspend fun insertFoods(foods: List<FoodEntity>)

    @Insert
    suspend fun insertDishes(dishes: List<DishEntity>)

    @Insert
    suspend fun insertEntries(entries: List<FoodEntryEntity>)

    @Insert
    suspend fun insertFavorites(favorites: List<FavoriteDishEntity>)

    @Insert
    suspend fun insertComponents(components: List<FavoriteComponentEntity>)

    @Insert
    suspend fun insertProgress(progress: ProgressEntity)

    @Insert
    suspend fun insertBadges(badges: List<UnlockedBadgeEntity>)
}
