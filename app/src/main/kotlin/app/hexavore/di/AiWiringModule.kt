package app.hexavore.di

import android.util.Log
import app.hexavore.BuildConfig
import app.hexavore.domain.ai.FoodRecognizer
import app.hexavore.domain.ai.InMemoryPendingRecognition
import app.hexavore.domain.ai.PendingRecognition
import app.hexavore.domain.usecase.AnalyseMeal
import app.hexavore.domain.usecase.StageDishPhoto
import app.hexavore.integration.ai.NetworkLog
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Ce que l'IA attend de l'application, et qu'aucun module ne peut savoir seul.
 *
 * @see docs/05-ia.md
 */
@Module
@InstallIn(SingletonComponent::class)
internal object AiWiringModule {
    /**
     * Le journal réseau, **et le fait qu'il n'existe qu'en `debug`**.
     *
     * [docs/05][ia] veut que les journaux détaillés soient compilés dans la seule
     * variante `debug`. La variante est une propriété de l'application, pas du module
     * d'intégration : c'est donc ici que le choix se fait, et
     * `NetworkLog.Silent` est ce que reçoit une release.
     *
     * L'intercepteur de redaction s'applique quand même dans les deux cas. Il ne
     * protège rien tant que rien ne journalise — mais le jour où quelqu'un branche un
     * journal ici, il n'a pas à se souvenir d'expurger quoi que ce soit.
     *
     * [ia]: docs/05-ia.md
     */
    /**
     * Le dépôt d'une proposition, **en un seul exemplaire**.
     *
     * Deux instances seraient deux dépôts, et l'écran de validation en interrogerait
     * un que personne ne remplit — la forme exacte du défaut de [D52][decisions], où
     * deux `SavedStateHandle` distincts passaient pour le même. Le `@Singleton` est ce
     * qui fait tenir la remise.
     *
     * [decisions]: docs/11-decisions.md
     */
    @Provides
    @Singleton
    fun pendingRecognition(): PendingRecognition = InMemoryPendingRecognition()

    /**
     * L'analyse, **partagee par les deux endroits qui la declenchent**.
     *
     * L'ecran d'IA et la barre du bas envoient la meme chose au meme port ; le cas
     * d'usage est ce qui les empeche de diverger ([D131][decisions]).
     *
     * [decisions]: docs/11-decisions.md
     */
    @Provides
    fun analyseMeal(recognizer: FoodRecognizer, pending: PendingRecognition, stagePhoto: StageDishPhoto): AnalyseMeal =
        AnalyseMeal(recognizer, pending, stagePhoto)

    @Provides
    @Singleton
    fun networkLog(): NetworkLog = if (BuildConfig.DEBUG) NetworkLog { Log.d(NETWORK_TAG, it) } else NetworkLog.Silent
}

private const val NETWORK_TAG = "HexavoreNet"
