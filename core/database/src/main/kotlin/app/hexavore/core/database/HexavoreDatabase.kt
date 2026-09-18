package app.hexavore.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import app.hexavore.core.database.dao.BackupReadDao
import app.hexavore.core.database.dao.BackupWriteDao
import app.hexavore.core.database.dao.CalendarDao
import app.hexavore.core.database.dao.DiaryDao
import app.hexavore.core.database.dao.FavoriteDishDao
import app.hexavore.core.database.dao.FoodCitationsDao
import app.hexavore.core.database.dao.FoodDao
import app.hexavore.core.database.dao.FoodMarksDao
import app.hexavore.core.database.dao.GoalDao
import app.hexavore.core.database.dao.ProfileDao
import app.hexavore.core.database.entity.DishEntity
import app.hexavore.core.database.entity.FavoriteComponentEntity
import app.hexavore.core.database.entity.FavoriteDishEntity
import app.hexavore.core.database.entity.FoodEntity
import app.hexavore.core.database.entity.FoodEntryEntity
import app.hexavore.core.database.entity.GoalEntity
import app.hexavore.core.database.entity.ProfileEntity
import app.hexavore.core.database.entity.WeightEntryEntity

/**
 * La base locale de l'application.
 *
 * `exportSchema = true` n'est pas décoratif : c'est le schéma exporté qui sert de
 * référence au test de migration. Sans lui, une migration ne se vérifierait plus
 * qu'à l'œil, et une colonne oubliée ne se découvrirait que sur l'appareil de
 * quelqu'un.
 *
 * @see docs/07-modele-de-donnees.md
 */
@Database(
    entities = [
        DishEntity::class,
        FoodEntryEntity::class,
        FavoriteDishEntity::class,
        FavoriteComponentEntity::class,
        FoodEntity::class,
        ProfileEntity::class,
        WeightEntryEntity::class,
        GoalEntity::class,
    ],
    version = HexavoreDatabase.VERSION,
    exportSchema = true,
)
abstract class HexavoreDatabase : RoomDatabase() {
    abstract fun diaryDao(): DiaryDao

    abstract fun calendarDao(): CalendarDao

    abstract fun foodDao(): FoodDao

    abstract fun foodMarksDao(): FoodMarksDao

    abstract fun foodCitationsDao(): FoodCitationsDao

    abstract fun favoriteDishDao(): FavoriteDishDao

    abstract fun profileDao(): ProfileDao

    abstract fun goalDao(): GoalDao

    /** Toutes les tables d'un coup, pour la sauvegarde et la restauration. */
    abstract fun backupReadDao(): BackupReadDao

    abstract fun backupWriteDao(): BackupWriteDao

    companion object {
        const val VERSION = 7

        const val NAME = "hexavore.db"

        /**
         * La chaîne de migrations.
         *
         * Elle existait **avant** d'être utile, et c'était tout son intérêt : la
         * première vraie migration s'y ajoute au lieu d'inaugurer un mécanisme, et
         * elle est validée par un test déjà écrit contre un schéma déjà versionné.
         */
        val MIGRATIONS: List<Migration> =
            listOf(Migration1To2, Migration2To3, Migration3To4, Migration4To5, Migration5To6, Migration6To7)

        /**
         * Construit la base.
         *
         * **`fallbackToDestructiveMigration` n'apparaît nulle part**, et ne doit
         * jamais apparaître — pas même « le temps du développement ». Perdre le
         * journal alimentaire de quelqu'un est une faute non rattrapable, et
         * l'habitude prise en développement est celle qu'on emporte en production.
         * Une migration manquante doit faire planter le build de la migration, pas
         * effacer des données.
         */
        fun build(context: Context): HexavoreDatabase =
            Room.databaseBuilder(context, HexavoreDatabase::class.java, NAME)
                .apply { MIGRATIONS.forEach { addMigrations(it) } }
                .build()
    }
}
