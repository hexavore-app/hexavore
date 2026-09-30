package app.hexavore.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.hexavore.domain.progress.Badge
import app.hexavore.domain.progress.Progress
import app.hexavore.domain.usecase.AdvanceProgress
import app.hexavore.domain.usecase.ObserveProgress
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * La progression, vue de l'accueil — **et c'est ici qu'elle avance**.
 *
 * ### Pourquoi l'accueil et pas l'écran de progression
 *
 * Parce que c'est l'écran qu'on ouvre. Figer un palier depuis un écran qu'on visite une
 * fois par semaine le figerait une fois par semaine ; la célébration arriverait sept
 * jours après le geste qui l'a méritée, et personne ne ferait le lien.
 *
 * ### La boucle qui se ferme d'elle-même
 *
 * Faire avancer écrit dans le dépôt, qui réémet, ce qui relance le calcul, qui fait
 * avancer de nouveau. Elle se termine en un tour parce que le second relevé n'a plus
 * rien à monter : le dépôt n'écrit que si un plancher monte, et un palier déjà rangé
 * ne se range pas deux fois ([D132][decisions]).
 *
 * [decisions]: docs/11-decisions.md
 */
@HiltViewModel
class HomeProgressViewModel @Inject constructor(
    observeProgress: ObserveProgress,
    private val advanceProgress: AdvanceProgress,
) : ViewModel() {
    /**
     * Ce qui vient de tomber, et qui attend d'être fêté.
     *
     * Une file et non un seul palier : un rattrapage — l'application rouverte après une
     * semaine — peut en faire tomber trois d'un coup, et n'en montrer qu'un ferait
     * disparaître les deux autres sans que rien ne les annonce.
     */
    private val toCelebrate = MutableStateFlow<List<Badge>>(emptyList())
    val celebrating: StateFlow<List<Badge>> = toCelebrate.asStateFlow()

    /**
     * Ce qui a **déjà été fêté**, et ne le sera plus.
     *
     * **La correction d'une animation qui se rejouait à chaque retour sur l'accueil**
     * ([D135][decisions]). Le flux s'arrête quand on ouvre l'écran de progression et
     * repart quand on revient ; sa première émission repasse alors par ici, et un
     * palier fêté dont l'animation n'était pas allée jusqu'au bout — ce qui est le cas
     * dès qu'on navigue pendant — revenait dans la file. La gerbe se rejouait, à chaque
     * aller-retour, pour un palier obtenu la semaine d'avant.
     *
     * Une mémoire de session suffit, et c'est exactement la bonne portée : un palier
     * déjà rangé ne peut plus retomber au lancement suivant, puisque le calcul le voit
     * dans le dépôt.
     *
     * [decisions]: docs/11-decisions.md
     */
    private val celebrated = mutableSetOf<Badge>()

    val uiState: StateFlow<Progress> = observeProgress()
        .onEach { progress ->
            val fallen = advanceProgress(progress).filterNot { it in celebrated }
            if (fallen.isEmpty()) return@onEach
            // Marque avant de montrer, et non apres : ce qui compte est qu'un palier ne
            // passe qu'une fois par ici, pas que son animation soit allee au bout.
            celebrated += fallen
            toCelebrate.value = toCelebrate.value + fallen
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MILLIS), Progress.NONE)

    /**
     * Le palier a été fêté.
     *
     * **Sur le premier de la file, jamais sur toute la file** : chacun a droit à son
     * animation, et les vider ensemble reviendrait à n'en montrer qu'un.
     */
    fun onCelebrated() {
        viewModelScope.launch { toCelebrate.value = toCelebrate.value.drop(1) }
    }

    private companion object {
        const val SUBSCRIPTION_TIMEOUT_MILLIS = 5_000L
    }
}
