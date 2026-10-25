package app.hexavore.feature.home.tour

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.hexavore.domain.tour.TourSettings
import app.hexavore.domain.usecase.HideTourSample
import app.hexavore.domain.usecase.ShowTourSample
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Le déroulé du tour, et le ménage qu'il laisse derrière lui.
 *
 * ### Il commence par nettoyer
 *
 * Avant même de savoir s'il y a un tour à jouer, il reprend les plats d'exemple. Une
 * application fermée au milieu d'un tour les aurait sinon laissés dans un vrai journal,
 * et ils s'y seraient installés pour de bon ([D141][decisions]).
 *
 * ### Il revient tant qu'on n'y a pas répondu
 *
 * Le souvenir se pose **à la fin**, et seulement sur les trois fins qui sont une
 * réponse : « Passer », « J'ai compris » et « Configurer » ([D175][decisions]). Une
 * application fermée au milieu d'une bulle n'a rien décidé, et un tour qui disparaîtrait
 * là-dessus aurait été manqué par celui qu'il visait — la première fois est la seule où
 * il sert à quelque chose.
 *
 * [decisions]: docs/11-decisions.md
 */
@HiltViewModel
class TourViewModel @Inject constructor(
    private val settings: TourSettings,
    private val showSample: ShowTourSample,
    private val hideSample: HideTourSample,
    private val replay: TourReplay,
) : ViewModel() {
    private val current = MutableStateFlow<TourStep?>(null)

    /** L'étape affichée, ou `null` quand il n'y a pas de tour en cours. */
    val step: StateFlow<TourStep?> = current.asStateFlow()

    init {
        viewModelScope.launch {
            runCatching { hideSample() }

            val seen = runCatching { settings.observeSeen().first() }.getOrDefault(true)
            if (!seen || (replay.always && !reponduDansCeProcessus)) {
                runCatching { showSample() }
                current.value = TourStep.entries.first()
            }
        }
    }

    /** Passe à la suite, ou termine. */
    fun onNext(keyless: Boolean) {
        val suivante = current.value?.next(keyless)
        if (suivante == null) onFinish() else current.value = suivante
    }

    /**
     * Le tour est **fini** : passé ou vu en entier, il ne reviendra pas.
     *
     * Le souvenir se pose, et les plats d'exemple repartent : ils n'ont rien à faire
     * dans un vrai journal. Fermer l'application au milieu, seule fin qui ne réponde
     * pas, laisse la question ouverte : le tour se représente au lancement suivant.
     */
    fun onFinish() {
        current.value = null
        reponduDansCeProcessus = true
        viewModelScope.launch {
            runCatching { settings.markSeen() }
            runCatching { hideSample() }
        }
    }

    /**
     * Partir poser une clé **répond** au tour : il est fini, comme passé ([D175][decisions]).
     *
     * ~~Quelqu'un qui part poser sa clé n'a pas renoncé au tour, et le retrouvera au
     * retour~~ ([D141][decisions]). À l'usage, il le retrouvait surtout au lancement
     * suivant, depuis la première bulle et avec ses plats d'exemple — pour avoir suivi le
     * conseil de la dernière. « Configurer » est la réponse la plus nette qui soit.
     */
    fun onConfigure() = onFinish()

    private companion object {
        /**
         * Le tour a-t-il deja ete congedie depuis le demarrage de l'application ?
         *
         * **Le rejeu porte sur le lancement, pas sur l'ecran.** Sans cette memoire, le
         * tour reparaissait a chaque fois que l'accueil se recomposait -- au retour d'un
         * plat, des reglages, de la recherche --, et son voile ravalait l'appui suivant.
         * Relancer l'application dix fois reste ce qu'on voulait pouvoir faire ; revoir
         * la premiere bulle en revenant d'un ecran ne l'a jamais ete.
         *
         * Elle vit dans le processus et nulle part ailleurs : c'est une aide d'essai,
         * elle n'a rien a laisser sur le telephone, et le souvenir ecrit -- celui qui
         * compte -- continue de se poser comme avant.
         */
        @Volatile
        private var reponduDansCeProcessus = false
    }
}
