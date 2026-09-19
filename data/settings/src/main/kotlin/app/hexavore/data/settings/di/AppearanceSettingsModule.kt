package app.hexavore.data.settings.di

import android.content.Context
import android.content.SharedPreferences
import app.hexavore.data.settings.StoredAppearanceSettings
import app.hexavore.domain.appearance.AppearanceSettings
import app.hexavore.domain.concurrency.DispatcherProvider
import app.hexavore.domain.usecase.ObserveDishStyle
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Named
import javax.inject.Singleton

/**
 * L'apparence de l'application, dans son propre fichier.
 *
 * **Pas celui des réglages d'IA**, contrairement aux deux façons d'analyser : celles-là
 * se rapportent aux clés et doivent disparaître avec elles. Un thème ne se rapporte à
 * rien qu'on efface, et le ranger là l'aurait fait sauter au premier effacement de clés
 * — sans que rien ne l'annonce.
 *
 * **Pas non plus dans le profil**, où vit pourtant le système d'unités. Les unités sont
 * une propriété de la personne et voyagent dans la sauvegarde ; le thème est une
 * propriété de l'appareil et n'a pas à traverser une restauration.
 */
@Module
@InstallIn(SingletonComponent::class)
internal object AppearanceSettingsModule {
    @Provides
    @Singleton
    @Named(APPEARANCE_PREFERENCES)
    fun preferences(@ApplicationContext context: Context): SharedPreferences =
        context.getSharedPreferences(APPEARANCE_PREFERENCES_FILE, Context.MODE_PRIVATE)

    @Provides
    @Singleton
    fun appearance(
        @Named(APPEARANCE_PREFERENCES) preferences: SharedPreferences,
        dispatchers: DispatcherProvider,
    ): AppearanceSettings = StoredAppearanceSettings(preferences, dispatchers)

    /**
     * Le style d'affichage des plats, lu par deux écrans.
     *
     * Un cas d'usage et non deux lectures du port : l'accueil dessine ses plats,
     * les réglages cochent la case, et chacun aurait décidé pour son compte ce que
     * vaut un fichier illisible ([D111][decisions]).
     *
     * [decisions]: docs/11-decisions.md
     */
    @Provides
    fun observeDishStyle(settings: AppearanceSettings): ObserveDishStyle = ObserveDishStyle(settings)
}

private const val APPEARANCE_PREFERENCES_FILE = "appearance_settings"

internal const val APPEARANCE_PREFERENCES = "appearance"
