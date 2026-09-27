package app.hexavore.data.settings.di

import android.content.Context
import android.content.SharedPreferences
import app.hexavore.data.settings.StoredAppearanceSettings
import app.hexavore.data.settings.StoredLanguageSettings
import app.hexavore.domain.appearance.AppearanceSettings
import app.hexavore.domain.concurrency.DispatcherProvider
import app.hexavore.domain.language.ContentLanguages
import app.hexavore.domain.language.LanguageSettings
import app.hexavore.domain.language.SystemLanguages
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
     * La langue, dans le même fichier de préférences que le thème.
     *
     * **Le même fichier, et un magasin à part.** Ce qui est écrit là n'est qu'un cahier de
     * rappel pour le prochain démarrage ; la vérité est celle que la plateforme applique.
     * Mais c'est bien une préférence d'appareil, au même titre que le thème : elle n'a pas
     * à traverser une restauration de sauvegarde, et elle n'a pas à disparaître avec les
     * clés d'IA.
     *
     * `@Singleton` compte ici : le magasin porte un flux d'état, et deux instances
     * auraient chacune le sien — régler la langue sur l'une ne se verrait pas sur l'autre.
     */
    @Provides
    @Singleton
    fun languageStore(
        @ApplicationContext context: Context,
        @Named(APPEARANCE_PREFERENCES) preferences: SharedPreferences,
        dispatchers: DispatcherProvider,
        systemLanguages: SystemLanguages,
    ): StoredLanguageSettings = StoredLanguageSettings(context, preferences, dispatchers, systemLanguages)

    @Provides
    fun languageSettings(store: StoredLanguageSettings): LanguageSettings = store

    /**
     * La langue en vigueur, servie par le même objet que le réglage.
     *
     * **Deux ports pour un magasin, et c'est voulu.** Ce que l'écran d'apparence règle est
     * une intention ; ce que le catalogue, les prompts et Open Food Facts demandent est le
     * résultat. Les faire dépendre du même type aurait donné au catalogue le droit
     * d'écrire un réglage, et à l'écran celui de court-circuiter la détection.
     */
    @Provides
    fun contentLanguages(store: StoredLanguageSettings): ContentLanguages = store

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
