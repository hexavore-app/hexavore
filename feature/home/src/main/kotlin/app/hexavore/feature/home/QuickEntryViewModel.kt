package app.hexavore.feature.home

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.hexavore.domain.ai.AiError
import app.hexavore.domain.ai.RecognitionInput
import app.hexavore.domain.ai.RecognitionOutcome
import app.hexavore.domain.usecase.AnalyseMeal
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Ce que la barre du bas montre pendant qu'une phrase part.
 *
 * **Elle n'a pas d'état « rien » distinct** : pas d'analyse en cours, pas d'erreur,
 * c'est une barre au repos. Trois booléens auraient décrit la même chose en laissant
 * exister des combinaisons impossibles.
 *
 * Le texte tapé n'est pas ici. Il vit dans le champ, comme tout texte du projet
 * ([DraftTextField][app.hexavore.core.designsystem.component.DraftTextField]) : un
 * aller-retour par le modèle réécrirait ce qu'on est en train de taper.
 */
@Immutable
data class QuickEntryUiState(
    val analysing: Boolean = false,
    /** L'issue du dernier envoi, quand il a échoué. Effacée dès qu'on relance. */
    val error: AiError? = null,
    /**
     * `true` quand la proposition est déposée et que la validation doit s'ouvrir.
     *
     * Un drapeau plutôt qu'un événement, comme sur l'écran d'IA : la navigation est un
     * effet, et l'accueil le consomme une fois.
     */
    val proposed: Boolean = false,
)

/**
 * La phrase de la barre du bas, de la frappe au dépôt.
 *
 * **L'analyse part sans ouvrir d'écran** ([D131][decisions]), et c'est toute la raison
 * de cette classe : le chemin « décrire son repas » coûtait quatre gestes — ouvrir
 * l'écran d'IA, taper, analyser, valider — dont le premier et le troisième ne
 * décidaient rien. Il en coûte deux : taper, valider.
 *
 * **Rien n'est demandé avant d'envoyer.** L'avertissement de [docs/05][ia] ne concerne
 * que la photo — celui qui écrit une phrase sait exactement ce qu'il envoie —, donc la
 * barre n'a ni consentement à recueillir ni boîte à afficher avant de partir.
 *
 * **Annuler coupe vraiment**, comme sur l'écran d'IA : une requête abandonnée qu'on
 * laisse courir se paie quand même.
 *
 * [ia]: docs/05-ia.md
 * [decisions]: docs/11-decisions.md
 */
@HiltViewModel
internal class QuickEntryViewModel @Inject constructor(private val analyseMeal: AnalyseMeal) : ViewModel() {
    private val state = MutableStateFlow(QuickEntryUiState())
    val uiState: StateFlow<QuickEntryUiState> = state.asStateFlow()

    /** L'analyse en vol, gardée pour pouvoir la couper. */
    private var analysis: Job? = null

    /**
     * Envoie la phrase, sauf s'il n'y en a pas.
     *
     * Le blanc est écarté ici et pas seulement grisé au bouton : la touche d'envoi du
     * clavier arrive par le même chemin, et elle ne consulte pas l'état d'un bouton.
     */
    fun onSend(text: String) {
        val written = text.trim()
        if (written.isEmpty() || state.value.analysing) return

        state.update { it.copy(analysing = true, error = null) }
        analysis = viewModelScope.launch {
            // Rien n'entoure cet appel : une annulation doit traverser. `onCancel` a
            // deja remis la barre en etat, et l'attraper ici ferait revivre un etat
            // que l'utilisateur vient de quitter.
            val outcome = analyseMeal(RecognitionInput.Text(written))
            state.update {
                when (outcome) {
                    is RecognitionOutcome.Recognized -> it.copy(analysing = false, proposed = true)
                    is RecognitionOutcome.Failed -> it.copy(analysing = false, error = outcome.error)
                }
            }
        }
    }

    fun onCancel() {
        analysis?.cancel()
        analysis = null
        state.update { it.copy(analysing = false) }
    }

    /**
     * Après que l'accueil est parti vers la validation.
     *
     * Sans quoi revenir en arrière — le geste de celui qui renonce à valider —
     * repartirait aussitôt vers un dépôt que la validation a déjà vidé.
     */
    fun onNavigated() {
        state.update { it.copy(proposed = false) }
    }

    /** L'échec a été lu. La phrase, elle, est restée dans le champ. */
    fun onDismissError() {
        state.update { it.copy(error = null) }
    }
}
