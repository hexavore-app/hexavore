package app.hexavore.data.settings.di

import android.content.SharedPreferences
import app.hexavore.data.settings.StoredReminderSettings
import app.hexavore.domain.concurrency.DispatcherProvider
import app.hexavore.domain.reminder.ReminderSettings
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Named
import javax.inject.Singleton

/**
 * Les quatre rappels : lesquels sonnent, et à quelle heure.
 *
 * **Le fichier des pastilles**, et non un neuvième fichier. Les deux répondent à la
 * même question de l'utilisateur — *de quoi l'application a-t-elle le droit de me
 * parler* — et l'écran des réglages les montre ensemble. Les séparer aurait fait deux
 * endroits à vider et deux à sauvegarder pour un seul réglage aux yeux de qui le règle.
 */
@Module
@InstallIn(SingletonComponent::class)
internal object ReminderModule {
    @Provides
    @Singleton
    fun stored(
        @Named(NOTICE_PREFERENCES) preferences: SharedPreferences,
        dispatchers: DispatcherProvider,
    ): StoredReminderSettings = StoredReminderSettings(preferences, dispatchers)

    @Provides
    fun reminders(stored: StoredReminderSettings): ReminderSettings = stored
}
