package app.hexavore.core.database.di

import app.hexavore.core.database.HexavoreDatabase
import app.hexavore.core.database.dao.ProgressDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Le DAO de la progression.
 *
 * Sorti de [DatabaseModule] quand le seuil de fonctions a mordu une seconde fois --
 * apres [BackupDaoModule] -- et le decoupage suit ce que les choses sont : les autres
 * DAO servent ce que l'utilisateur a mange, celui-ci sert ce qu'il a traverse.
 */
@Module
@InstallIn(SingletonComponent::class)
object ProgressDaoModule {
    @Provides
    fun progressDao(database: HexavoreDatabase): ProgressDao = database.progressDao()
}
