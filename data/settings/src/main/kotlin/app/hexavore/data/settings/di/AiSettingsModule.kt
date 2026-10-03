package app.hexavore.data.settings.di

import android.content.Context
import android.content.SharedPreferences
import app.hexavore.data.settings.KeystoreCipher
import app.hexavore.data.settings.SecretCipher
import app.hexavore.data.settings.StoredAiCredentials
import app.hexavore.data.settings.StoredAiUsage
import app.hexavore.data.settings.StoredPhotoConsent
import app.hexavore.domain.ai.AiCredentials
import app.hexavore.domain.ai.AiSettings
import app.hexavore.domain.ai.AiUsageLog
import app.hexavore.domain.ai.PhotoConsent
import app.hexavore.domain.concurrency.DispatcherProvider
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Named
import javax.inject.Singleton

/**
 * Ce que ce module lie : les quatre ports de l'IA, et rien qui sorte d'ici.
 *
 * Le chiffrement et le fichier de préférences restent **internes** : exposer l'un ou
 * l'autre ferait de ce module le rangement à secrets de tout le projet, et le premier
 * appelant pressé y aurait mis autre chose.
 *
 * @see docs/06-architecture.md
 */
@Module
@InstallIn(SingletonComponent::class)
internal object AiSettingsModule {
    @Provides
    @Singleton
    @Named(AI_PREFERENCES)
    fun preferences(@ApplicationContext context: Context): SharedPreferences =
        context.getSharedPreferences(AI_PREFERENCES_FILE, Context.MODE_PRIVATE)

    @Provides
    @Singleton
    fun cipher(): SecretCipher = KeystoreCipher()

    @Provides
    @Singleton
    fun stored(
        @Named(AI_PREFERENCES) preferences: SharedPreferences,
        cipher: SecretCipher,
        dispatchers: DispatcherProvider,
    ): StoredAiCredentials = StoredAiCredentials(preferences, cipher, dispatchers)

    @Provides
    fun credentials(stored: StoredAiCredentials): AiCredentials = stored

    @Provides
    fun settings(stored: StoredAiCredentials): AiSettings = stored

    /**
     * Le consentement photo, dans le même fichier que les clés.
     *
     * Effacer ses réglages d'IA doit effacer l'accord avec : quelqu'un qui repart de
     * zéro n'a rien accepté.
     */
    @Provides
    @Singleton
    fun photoConsent(
        @Named(AI_PREFERENCES) preferences: SharedPreferences,
        dispatchers: DispatcherProvider,
    ): PhotoConsent = StoredPhotoConsent(preferences, dispatchers)

    /**
     * Le compteur d'usage, dans le même fichier lui aussi.
     *
     * Effacer ses réglages d'IA remet le compteur à zéro, et c'est cohérent : il ne
     * compte que ce que ces clés-là ont dépensé.
     */
    @Provides
    @Singleton
    fun usage(@Named(AI_PREFERENCES) preferences: SharedPreferences, dispatchers: DispatcherProvider): AiUsageLog =
        StoredAiUsage(preferences, dispatchers)
}

/**
 * Un fichier à part, et non les préférences par défaut de l'application.
 *
 * Deux raisons se renforcent : les règles d'extraction de données peuvent l'exclure
 * nommément des sauvegardes ([docs/05][ia] veut qu'une clé ne soit jamais sauvegardée),
 * et un effacement des réglages d'IA ne touche à rien d'autre.
 *
 * [ia]: docs/05-ia.md
 */
private const val AI_PREFERENCES_FILE = "ai_settings"

/**
 * Le qualifiant de ce fichier de préférences.
 *
 * `internal` et non `private` : `ErasureModule` les injecte tous les trois pour les
 * vider d'un geste, et lui passer des noms plutôt que des instances l'aurait obligé à
 * les réécrire — un fichier renommé ici et pas là ne survivrait à l'effacement qu'en
 * silence.
 */
internal const val AI_PREFERENCES = "ai"
