package app.hexavore.data.settings.di

import android.content.Context
import android.content.SharedPreferences
import app.hexavore.data.settings.StoredKeyRejection
import app.hexavore.data.settings.StoredNoticeSettings
import app.hexavore.data.settings.StoredTourSettings
import app.hexavore.domain.concurrency.DispatcherProvider
import app.hexavore.domain.notice.KeyRejection
import app.hexavore.domain.notice.NoticeSettings
import app.hexavore.domain.tour.TourSettings
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Named
import javax.inject.Singleton

/**
 * Les pastilles : lesquelles sont allumées, et le souvenir d'une clé refusée.
 *
 * **Deux fichiers différents, et la frontière a une raison.** Les réglages de pastilles
 * survivent à un effacement des clés — ce sont des préférences d'affichage, pas des
 * secrets — tandis que le souvenir d'un refus porte sur une clé précise et doit partir
 * avec elle. Il vit donc dans le fichier des réglages d'IA.
 *
 * @see docs/02-parcours-et-ecrans.md
 */
@Module
@InstallIn(SingletonComponent::class)
internal object NoticeModule {
    @Provides
    @Singleton
    @Named(NOTICE_PREFERENCES)
    fun preferences(@ApplicationContext context: Context): SharedPreferences =
        context.getSharedPreferences(NOTICE_PREFERENCES_FILE, Context.MODE_PRIVATE)

    @Provides
    @Singleton
    fun stored(
        @Named(NOTICE_PREFERENCES) preferences: SharedPreferences,
        dispatchers: DispatcherProvider,
    ): StoredNoticeSettings = StoredNoticeSettings(preferences, dispatchers)

    @Provides
    fun notices(stored: StoredNoticeSettings): NoticeSettings = stored

    /**
     * Le souvenir du tour guidé, dans le même fichier que les pastilles.
     *
     * Ce sont deux choses de même nature : l'état de ce que l'écran a déjà dit. Ni
     * l'un ni l'autre ne doit partir avec une clé d'IA.
     */
    @Provides
    @Singleton
    fun tourStore(
        @Named(NOTICE_PREFERENCES) preferences: SharedPreferences,
        dispatchers: DispatcherProvider,
    ): StoredTourSettings = StoredTourSettings(preferences, dispatchers)

    @Provides
    fun tour(stored: StoredTourSettings): TourSettings = stored

    @Provides
    @Singleton
    fun rejection(
        @Named(AI_PREFERENCES) preferences: SharedPreferences,
        dispatchers: DispatcherProvider,
    ): KeyRejection = StoredKeyRejection(preferences, dispatchers)
}

private const val NOTICE_PREFERENCES_FILE = "notice_settings"

/**
 * Le qualifiant du fichier des pastilles.
 *
 * `internal` comme ses trois voisins : `ErasureModule` a besoin de le vider avec les
 * autres, et lui passer un nom plutôt qu'une instance ferait un second endroit à tenir
 * d'accord.
 */
internal const val NOTICE_PREFERENCES = "notice"
