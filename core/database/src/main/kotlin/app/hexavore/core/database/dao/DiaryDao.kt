package app.hexavore.core.database.dao

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Transaction
import androidx.room.Upsert
import app.hexavore.core.database.entity.DishEntity
import app.hexavore.core.database.entity.FoodEntryEntity
import kotlinx.coroutines.flow.Flow

/** Un plat et ses lignes, tels que Room sait les assembler en une lecture. */
data class DishWithEntries(
    @Embedded val dish: DishEntity,
    @Relation(parentColumn = "id", entityColumn = "dish_id")
    val entries: List<FoodEntryEntity>,
)

/** La date de première écriture d'une ligne, pour ne pas la perdre à la réécriture. */
data class EntryCreation(@ColumnInfo(name = "id") val id: String, @ColumnInfo(name = "created_at") val createdAt: Long)

/**
 * Accès au journal alimentaire.
 *
 * Les lectures rendent un [Flow] : Room notifie sur invalidation, donc aucun écran
 * n'a à se rafraîchir lui-même. Les écritures sont `suspend` — un port d'écriture
 * qui rendrait un flux mélangerait commande et observation.
 *
 * **Aucun `SUM` ici.** Agréger en SQL traiterait `NULL` comme absent, ce qui est
 * correct arithmétiquement mais perd l'information qu'une valeur manquait. Les
 * lignes remontent donc entières, et c'est le domaine qui totalise en retenant
 * quels totaux sont minorés.
 *
 * @see docs/11-decisions.md — D29
 */
@Dao
interface DiaryDao {
    @Transaction
    @Query("SELECT * FROM dish WHERE date = :date ORDER BY logged_at ASC")
    fun observeDay(date: String): Flow<List<DishWithEntries>>

    @Transaction
    @Query("SELECT * FROM dish WHERE id = :id")
    suspend fun dish(id: String): DishWithEntries?

    /**
     * Écrit un plat et remplace entièrement ses lignes, en une transaction.
     *
     * Un seul verbe pour créer, modifier et restaurer : les trois demandent la même
     * chose de la base. Trois méthodes auraient donné trois occasions d'écrire
     * trois transactions légèrement différentes.
     *
     * **Les dates de première écriture sont préservées**, celle du plat comme celles
     * de ses lignes. Sans cela, `created_at` deviendrait égal à `updated_at` dès la
     * première correction, et cesserait de dire quoi que ce soit — or c'est lui qui
     * rendra une fusion de sauvegardes possible ([07][modele]).
     *
     * Les lignes sont effacées puis réinsérées plutôt que rapprochées une à une :
     * une ligne supprimée à l'écran doit disparaître, et un rapprochement laisserait
     * la question ouverte pour chaque cas de figure.
     *
     * [modele]: docs/07-modele-de-donnees.md
     */
    @Transaction
    suspend fun saveDish(dish: DishEntity, entries: List<FoodEntryEntity>) {
        val dishCreatedAt = dishCreatedAt(dish.id) ?: dish.createdAt
        val entryCreatedAt = entryCreationTimes(dish.id).associate { it.id to it.createdAt }

        upsertDish(dish.copy(createdAt = dishCreatedAt))
        deleteEntriesOfDish(dish.id)
        insertEntries(entries.map { it.copy(createdAt = entryCreatedAt[it.id] ?: it.createdAt) })
    }

    @Query("DELETE FROM dish WHERE id = :id")
    suspend fun deleteDish(id: String)

    // --- Details de la transaction d'ecriture --------------------------------
    //
    // Ces cinq methodes n'existent que pour saveDish. Room impose qu'elles soient
    // publiques -- une interface n'a pas d'autre visibilite -- mais rien d'autre
    // n'a de raison de les appeler : chacune prise isolement laisse la base dans un
    // etat que le domaine ne sait pas decrire.

    @Upsert
    suspend fun upsertDish(dish: DishEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertEntries(entries: List<FoodEntryEntity>)

    @Query("DELETE FROM food_entry WHERE dish_id = :dishId")
    suspend fun deleteEntriesOfDish(dishId: String)

    @Query("SELECT created_at FROM dish WHERE id = :id")
    suspend fun dishCreatedAt(id: String): Long?

    @Query("SELECT id, created_at FROM food_entry WHERE dish_id = :dishId")
    suspend fun entryCreationTimes(dishId: String): List<EntryCreation>
}
