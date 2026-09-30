package app.hexavore.data.settings.di

import android.content.SharedPreferences
import app.hexavore.data.settings.ErasablePreferences
import app.hexavore.data.settings.FlowBackedStores
import app.hexavore.data.settings.StoredAdjustmentSettings
import app.hexavore.data.settings.StoredAiCredentials
import app.hexavore.data.settings.StoredContributionSettings
import app.hexavore.data.settings.StoredDebugSettings
import app.hexavore.data.settings.StoredDeepAnalysisSettings
import app.hexavore.data.settings.StoredNoticeSettings
import app.hexavore.data.settings.StoredReminderSettings
import app.hexavore.domain.backup.StoredPreferences
import app.hexavore.domain.concurrency.DispatcherProvider
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Named
import javax.inject.Qualifier
import javax.inject.Singleton

/**
 * Les fichiers de préférences que l'effacement vide.
 *
 * Un qualificatif parce qu'une `List<SharedPreferences>` sans nom serait ambiguë le
 * jour où une seconde liste existerait — et parce que celle-ci **est** une chose : la
 * réponse à « qu'est-ce qui disparaît ? », qu'un lecteur cherchera sous ce nom.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
internal annotation class ErasableFiles

/**
 * Ce qu'« Effacer toutes mes données » doit vider, hors du journal.
 *
 * Un module à lui plutôt qu'une méthode ajoutée à l'un des trois autres : il est le
 * seul à les connaître **tous**, et le loger dans l'un d'eux aurait fait dépendre les
 * réglages d'IA de l'existence d'un compte de contribution.
 *
 * **La liste des fichiers est écrite ici**, à l'endroit le plus visible qui existe. Un
 * quatrième devra s'y ajouter à la main ; c'est le prix de ne pas dépendre d'une
 * découverte automatique qui, elle, se tairait en cas d'oubli.
 *
 * @see docs/09-donnees-et-sauvegarde.md
 */
@Module
@InstallIn(SingletonComponent::class)
internal object ErasureModule {
    /**
     * **La liste des fichiers, et rien d'autre.**
     *
     * Sortie du fournisseur principal quand le seuil de paramètres a mordu, et le
     * découpage suit ce que les choses sont : ici les rangements, là les magasins qui
     * portent un flux. Un quatrième fichier s'ajoute d'une ligne, au seul endroit qui
     * les connaisse tous.
     */
    @Provides
    @Singleton
    @ErasableFiles
    fun erasableFiles(
        @Named(AI_PREFERENCES) ai: SharedPreferences,
        @Named(ADJUSTMENT_PREFERENCES) adjustment: SharedPreferences,
        @Named(CONTRIBUTION_PREFERENCES) contribution: SharedPreferences,
        @Named(NOTICE_PREFERENCES) notices: SharedPreferences,
        // `@JvmSuppressWildcards` : sans lui, Kotlin genere `List<? extends
        // SharedPreferences>` au point d'injection et `List<SharedPreferences>` a la
        // fourniture -- deux types differents pour Dagger, qui ne trouve alors rien.
    ): List<@JvmSuppressWildcards SharedPreferences> = listOf(ai, adjustment, contribution, notices)

    /**
     * Les sept magasins qui portent un flux, reunis.
     *
     * Sortis du fournisseur principal quand le seuil de parametres a mordu, et le
     * decoupage suit ce que les choses sont : ici ceux qui doivent etre prevenus,
     * la les fichiers qu'on vide.
     */
    @Provides
    @Singleton
    fun flowBackedStores(
        credentials: StoredAiCredentials,
        contribution: StoredContributionSettings,
        adjustment: StoredAdjustmentSettings,
        notices: StoredNoticeSettings,
        debug: StoredDebugSettings,
        deep: StoredDeepAnalysisSettings,
        reminders: StoredReminderSettings,
    ) = FlowBackedStores(credentials, contribution, adjustment, notices, debug, deep, reminders)

    @Provides
    @Singleton
    fun storedPreferences(
        stores: FlowBackedStores,
        @ErasableFiles files: List<@JvmSuppressWildcards SharedPreferences>,
        dispatchers: DispatcherProvider,
    ): StoredPreferences = ErasablePreferences(stores, files, dispatchers)
}
