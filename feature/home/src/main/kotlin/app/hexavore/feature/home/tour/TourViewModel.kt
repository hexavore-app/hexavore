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
 * Le souvenir se pose **à la fin**, et seulement sur les deux fins qui sont une
 * réponse : « Passer » et « J'ai compris ». Une application fermée au milieu d'une
 * bulle n'a rien décidé, et un tour qui disparaîtrait là-dessus aurait été manqué par
 * celui qu'il visait — la première fois est la seule où il sert à quelque chose.
 *
 * [decisions]: docs/11-decisions.md
 */
@HiltViewModel
class TourViewModel @Inject constructor(
    private val settings: TourSettings,
    private val showSample: ShowTourSample,
    private val hideSample: HideTourSample,
) : ViewModel() {
    private val current = MutableStateFlow<TourStep?>(null)

    /** L'étape affichée, ou `null` quand il n'y a pas de tour en cours. */
    val step: StateFlow<TourStep?> = current.asStateFlow()

    init {
        viewModelScope.launch {
            runCatching { hideSample() }

            val seen = runCatching { settings.observeSeen().first() }.getOrDefault(true)
            if (!seen || REJOUE_TOUJOURS) {
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
     * Ce sont les deux seules fins qui comptent comme une réponse. Tout le reste —
     * fermer l'application au milieu, partir poser une clé — laisse la question
     * ouverte, et le tour se represente au lancement suivant.
     */
    fun onFinish() = close(remember = true)

    /**
     * Le tour s'efface pour laisser la place aux réglages, **sans être fini**.
     *
     * Quelqu'un qui part poser sa clé n'a pas renoncé au tour : il est allé faire ce
     * que le tour lui demandait. Il le retrouvera au retour, et la barre y sera
     * déverrouillée — ce qui est précisément ce qu'il était venu voir.
     */
    fun onLeaveForSettings() = close(remember = false)

    /**
     * Range le tour, et reprend les plats d'exemple dans tous les cas.
     *
     * **Les plats partent même quand le souvenir reste ouvert** : ils n'ont rien à
     * faire dans un vrai journal, et le tour saura les reposer s'il revient.
     */
    private fun close(remember: Boolean) {
        current.value = null
        viewModelScope.launch {
            if (remember) runCatching { settings.markSeen() }
            runCatching { hideSample() }
        }
    }

    private companion object {
        /**
         * **À remettre à `false` avant publication.**
         *
         * Le tour se rejoue à chaque ouverture, le souvenir étant ignoré : c'est la
         * seule façon de le regarder dix fois de suite sans effacer les données de
         * l'application entre chaque essai. Rien d'autre ne change — le souvenir
         * s'écrit toujours, et il reprendra son rôle dès que cette constante
         * repassera à `false`.
         */
        const val REJOUE_TOUJOURS = true
    }
}
