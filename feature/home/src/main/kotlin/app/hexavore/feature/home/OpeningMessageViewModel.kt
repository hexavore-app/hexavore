package app.hexavore.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.hexavore.domain.opening.OpeningAnswer
import app.hexavore.domain.opening.OpeningMessage
import app.hexavore.domain.usecase.OpeningMessages
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Le message de cette ouverture, et ce qu'on y répond ([D179][decisions]).
 *
 * **L'accueil prévient, le cas d'usage décide.** Ce modèle ne sait ni ce qu'est une
 * ouverture ni quand un message est dû : il dit quand l'accueil paraît et s'efface, et
 * il montre ce qu'on lui rend.
 *
 * **Un message à l'écran y reste** tant qu'on n'y a pas répondu : une rotation fait
 * repasser l'accueil au premier plan, et ce passage-là n'est pas une ouverture — il ne
 * doit ni effacer la boîte, ni en appeler une seconde.
 *
 * **Un échec ne se voit pas.** Un message manqué n'est rien ; une ouverture qui ferait
 * tomber l'accueil pour lui serait tout.
 *
 * [decisions]: docs/11-decisions.md
 */
@HiltViewModel
internal class OpeningMessageViewModel @Inject constructor(private val messages: OpeningMessages) : ViewModel() {
    private val shown = MutableStateFlow<OpeningMessage?>(null)
    val message: StateFlow<OpeningMessage?> = shown.asStateFlow()

    fun onStarted() {
        viewModelScope.launch {
            val next = runCatching { messages.onHomeStarted() }.getOrNull()
            if (shown.value == null) shown.value = next
        }
    }

    fun onStopped() {
        viewModelScope.launch { runCatching { messages.onHomeStopped() } }
    }

    /** La boîte se referme d'abord : la réponse s'enregistre ensuite, sans la faire attendre. */
    fun onAnswer(answer: OpeningAnswer) {
        shown.value = null
        viewModelScope.launch { runCatching { messages.answer(answer) } }
    }
}
