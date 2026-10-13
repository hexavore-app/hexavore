package app.hexavore.data.settings.di

import android.content.SharedPreferences
import app.hexavore.data.settings.StoredDeepAnalysisSettings
import app.hexavore.domain.ai.DeepAnalysisSettings
import app.hexavore.domain.concurrency.DispatcherProvider
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Named
import javax.inject.Singleton

/**
 * Comment on analyse : en profondeur, et en gardant trace.
 *
 * **Sorti de [AiSettingsModule] quand son seuil de fonctions a mordu**, et le decoupage
 * suit ce que les choses sont : la-bas les identifiants -- une cle, un modele, une URL,
 * un consentement --, ici deux facons d'analyser qui ne dependent d'aucun fournisseur
 * en particulier.
 *
 * **Le meme fichier de preferences, pourtant.** Ce sont des reglages d'IA : effacer ses
 * cles doit les remettre a zero, et un fichier a part les aurait laisses survivre a la
 * disparition de ce a quoi ils se rapportent.
 */
@Module
@InstallIn(SingletonComponent::class)
internal object AnalysisModesModule {
    /**
     * L'analyse approfondie : le modele interroge le catalogue avant de conclure.
     *
     * Le type concret est expose a cote de son port, comme pour les cles : c'est
     * `StoredAiCredentials` qui a besoin de la lecture non suspendue, au moment de
     * construire la configuration d'un appel.
     */
    @Provides
    @Singleton
    fun storedDeep(
        @Named(AI_PREFERENCES) preferences: SharedPreferences,
        dispatchers: DispatcherProvider,
    ): StoredDeepAnalysisSettings = StoredDeepAnalysisSettings(preferences, dispatchers)

    @Provides
    fun deepAnalysis(stored: StoredDeepAnalysisSettings): DeepAnalysisSettings = stored
}
