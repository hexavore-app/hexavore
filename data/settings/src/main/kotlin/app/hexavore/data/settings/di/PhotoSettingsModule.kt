package app.hexavore.data.settings.di

import android.content.Context
import android.content.SharedPreferences
import app.hexavore.data.settings.StoredPhotoSettings
import app.hexavore.domain.concurrency.DispatcherProvider
import app.hexavore.domain.diary.PhotoSettings
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Named
import javax.inject.Singleton

/**
 * Le réglage des photos, dans son propre fichier.
 *
 * **Pas celui des réglages d'IA**, alors que c'est l'analyse par photo qui en produit le
 * plus : le scan en produit aussi, et effacer ses clés d'IA n'a aucune raison de faire
 * réapparaître des photos qu'on avait décidé de ne plus garder.
 *
 * **Pas dans la liste des fichiers qu'« Effacer toutes mes données » vide**, pour la
 * même raison que l'apparence : c'est un réglage d'appareil, et le geste efface des
 * données. Les photos, elles, sont des données, et `EraseEverything` les emporte.
 */
@Module
@InstallIn(SingletonComponent::class)
internal object PhotoSettingsModule {
    @Provides
    @Singleton
    @Named(PHOTO_PREFERENCES)
    fun preferences(@ApplicationContext context: Context): SharedPreferences =
        context.getSharedPreferences(PHOTO_PREFERENCES_FILE, Context.MODE_PRIVATE)

    @Provides
    @Singleton
    fun photoSettings(
        @Named(PHOTO_PREFERENCES) preferences: SharedPreferences,
        dispatchers: DispatcherProvider,
    ): PhotoSettings = StoredPhotoSettings(preferences, dispatchers)
}

private const val PHOTO_PREFERENCES_FILE = "photo_settings"

internal const val PHOTO_PREFERENCES = "photos"
