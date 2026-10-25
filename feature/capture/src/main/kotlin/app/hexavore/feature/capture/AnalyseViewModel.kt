package app.hexavore.feature.capture

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.hexavore.core.designsystem.component.SlowAnalysis
import app.hexavore.core.ui.FormMemory
import app.hexavore.domain.ai.AiError
import app.hexavore.domain.ai.AiSettings
import app.hexavore.domain.ai.PhotoConsent
import app.hexavore.domain.ai.RecognitionInput
import app.hexavore.domain.ai.RecognitionOutcome
import app.hexavore.domain.usecase.AnalyseMeal
import app.hexavore.domain.usecase.AnalysisProgress
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.builtins.serializer
import javax.inject.Inject

/**
 * La photo réduite, dans un porteur à identité.
 *
 * **Pas un `ByteArray` nu dans l'état, et pas une `data class` autour.** Une
 * `data class` qui porte un tableau fabrique une égalité fausse — elle compare les
 * références —, donc un état qui ne se compare plus correctement et une recomposition
 * à chaque image. L'identité est justement ce qu'on veut ici : deux photos sont la
 * même quand c'est le même tableau. C'est le raisonnement de `RecognitionInput.Photo`
 * ([docs/05][ia]), appliqué à l'écran qui la produit.
 *
 * [ia]: docs/05-ia.md
 */
@Stable
internal class ReducedPhoto(val jpeg: ByteArray)

/**
 * Ce que l'écran d'IA montre.
 *
 * **Une photo, une phrase, ou les deux** ([D120][decisions]). Les deux modales
 * précédentes portaient deux états qui ne différaient que par ce qu'ils envoyaient ;
 * celui-ci porte les deux entrées, et c'est [analysable] qui dit quand il y a de quoi
 * analyser.
 *
 * **La photo et la phrase survivent à l'échec**, et c'est ce que [docs/02][parcours]
 * demande : une clé refusée ou un réseau absent ne doit jamais obliger à ressortir le
 * téléphone au-dessus d'une assiette qu'on est peut-être en train de manger, ni à
 * retaper une phrase.
 *
 * [parcours]: docs/02-parcours-et-ecrans.md
 * [decisions]: docs/11-decisions.md
 */
@Immutable
internal data class AnalyseUiState(
    val photo: ReducedPhoto? = null,
    /**
     * Ce qui est écrit sous l'image.
     *
     * **Un seul champ pour deux rôles**, et c'est ce que la fusion des deux modales
     * rend possible : sans photo il décrit le repas, avec photo il le précise —
     * « l'assiette fait 24 cm », « la sauce est allégée ». L'écran change son libellé ;
     * le modèle, lui, envoie la même chaîne au bon endroit.
     */
    val text: String = "",
    val analysing: Boolean = false,
    /**
     * `true` pendant la seconde tentative, quand la première s'est heurtée à un
     * fournisseur débordé.
     *
     * **Il faut le dire.** Une attente qui s'allonge sans explication se lit comme une
     * application figée, et celui qui referme l'écran perd sa photo. La phrase nomme la
     * cause — le service est saturé — et le fait qu'on s'en occupe ([D156][decisions]).
     *
     * Distinct de [analysing], qui reste vrai pendant toute l'opération : celui-ci ne
     * dit pas *ça travaille* mais *ça a échoué une fois et ça repart*.
     *
     * [decisions]: docs/11-decisions.md
     */
    val retrying: Boolean = false,
    /**
     * `true` quand le modèle a répondu et qu'il estime ce que le catalogue ne connaît pas.
     *
     * **Le second appel se voit désormais**, sur cet écran et non plus derrière celui de
     * validation, qui restait noir le temps qu'il revienne ([D176][decisions]). La phrase
     * dit que l'on a avancé : l'assiette est lue, ce sont ses valeurs qu'on attend.
     *
     * [decisions]: docs/11-decisions.md
     */
    val estimating: Boolean = false,
    /**
     * Qui fait attendre, quand l'analyse dure depuis plus de dix secondes ; `null` sinon.
     *
     * **Une note, pas une étape** ([D177][decisions]) : elle s'ajoute sous l'indicateur
     * sans changer sa phrase, parce qu'elle ne dit pas ce qu'on attend mais que c'est
     * normal d'attendre. Elle retombe avec l'attente.
     *
     * [decisions]: docs/11-decisions.md
     */
    val slow: SlowAnalysis? = null,
    /** L'issue du dernier essai, quand il a échoué. Effacée dès qu'on relance. */
    val error: AiError? = null,
    /**
     * `true` quand l'avertissement doit être montré avant d'envoyer quoi que ce soit.
     *
     * Distinct de [provider], qui n'est que le nom à écrire dedans : l'un dit s'il
     * faut demander, l'autre à qui la photo partira. Les fondre en une chaîne nulle
     * ferait porter deux sens au même champ, dont l'un — la chaîne vide — se lirait
     * mal.
     */
    val consentNeeded: Boolean = false,
    /**
     * Le fournisseur actif, tel que l'avertissement le nomme.
     *
     * Le nommer en fait une phrase **vérifiable** — « votre photo part chez Mistral »
     * se contredit tout seul si ce n'est pas vrai, là où « chez votre fournisseur » ne
     * se vérifie pas. Vide si rien n'est configuré, cas où l'écran dit autrement.
     */
    val provider: String = "",
    /**
     * `true` quand la proposition est déposée et que l'écran doit céder la place.
     *
     * Un drapeau plutôt qu'un événement : la navigation est un effet, et l'écran le
     * consomme une fois. Ce que l'analyse a produit n'est pas ici — il attend dans le
     * dépôt, parce qu'une route ne porte pas cinq lignes.
     */
    val analysed: Boolean = false,
) {
    /** Une photo **ou** une phrase : l'un des deux suffit, et rien ne suffit sans eux. */
    val analysable: Boolean get() = (photo != null || text.isNotBlank()) && !analysing
}

/**
 * L'écran d'IA : de la photo ou de la phrase au dépôt des propositions.
 *
 * **Un seul modèle pour les deux entrées** ([D120][decisions]). Les deux précédents —
 * photo et texte — faisaient la même chose à l'envoi près : même reconnaissance, même
 * dépôt, mêmes erreurs, même sortie. Ce qui les distinguait tenait en une ligne, et
 * cette ligne est ici.
 *
 * **Il ne voit ni caméra, ni galerie, ni `Uri`.** L'écran lui remet un JPEG déjà
 * réduit ; d'où viennent ces octets ne le regarde pas, et c'est ce qui le garde
 * vérifiable sur la JVM alors que tout ce qui l'entoure demande un appareil. C'est la
 * division de [D66][decisions] pour le scan, appliquée telle quelle.
 *
 * **Annuler coupe vraiment.** [docs/02][parcours] l'écrit noir sur blanc, et c'est une
 * question d'argent autant que de patience : une requête abandonnée qu'on laisse
 * courir se paie quand même.
 *
 * [parcours]: docs/02-parcours-et-ecrans.md
 * [decisions]: docs/11-decisions.md
 */
@HiltViewModel
internal class AnalyseViewModel @Inject constructor(
    private val analyseMeal: AnalyseMeal,
    private val consent: PhotoConsent,
    private val settings: AiSettings,
    savedStateHandle: SavedStateHandle = SavedStateHandle(),
) : ViewModel() {
    /**
     * La phrase en cours, gardée à travers la mort du processus ([D166][decisions]).
     *
     * **Pas la photo** : quelques centaines de kilo-octets dépasseraient ce qu'Android garde
     * pour un écran, et le perdre ferait tout perdre. Une photo **en cours de prise**
     * survit déjà, par son adresse ([D163][decisions]) ; une photo déjà reçue se reprend.
     *
     * [decisions]: docs/11-decisions.md
     */
    private val memory = FormMemory(savedStateHandle, MEMORY_KEY, String.serializer())

    private val state = MutableStateFlow(AnalyseUiState(text = memory.recall().orEmpty()))
    val uiState: StateFlow<AnalyseUiState> = state.asStateFlow()

    /** L'analyse en vol, gardée pour pouvoir la couper. */
    private var analysis: Job? = null

    /**
     * Ce que l'analyse dit d'elle-même, écrit là où l'écran le lit.
     *
     * Une estimation qui commence met fin à la relance : si elle part, c'est que la
     * seconde tentative a abouti, et la phrase doit dire ce qu'on attend maintenant.
     */
    private val progress = object : AnalysisProgress {
        override suspend fun retrying() = state.update { it.copy(retrying = true) }

        override suspend fun estimating() = state.update { it.copy(retrying = false, estimating = true) }

        // Le fournisseur est relu au moment de le nommer : c'est celui de l'analyse en
        // vol, puisque la configuration ne change pas de fournisseur pendant un appel.
        override suspend fun slow() {
            val provider = runCatching { settings.current() }.getOrNull()?.provider
            state.update { it.copy(slow = SlowAnalysis.of(provider)) }
        }
    }

    fun onPhoto(jpeg: ByteArray) {
        // Une nouvelle photo efface l'echec de la precedente : ce qui s'affichait ne
        // se rapporte plus a ce qu'on regarde.
        state.update { it.copy(photo = ReducedPhoto(jpeg), error = null) }
    }

    /**
     * La photo est retirée, et le texte reste.
     *
     * **C'est ce qui permet de basculer d'un mode à l'autre sans quitter l'écran** :
     * une photo mal cadrée qu'on remplace par une phrase, ou l'inverse. Sans ce geste,
     * une photo prise par erreur obligerait à refermer l'écran pour envoyer du texte.
     */
    fun onRemovePhoto() {
        state.update { it.copy(photo = null, error = null) }
    }

    fun onText(text: String) {
        state.update { it.copy(text = text) }
        memory.keep(text)
    }

    /**
     * Lance l'analyse, ou demande d'abord l'accord.
     *
     * **L'accord ne concerne que la photo** : c'est elle qui expose une image de son
     * repas — et de ce qui l'entoure — à un tiers. Une phrase tapée part sans
     * avertissement, comme avant, parce que celui qui l'écrit sait exactement ce qu'il
     * envoie.
     *
     * Il est vérifié **ici** et non à l'ouverture de l'écran : c'est l'envoi qui
     * expose la photo, pas le fait de la prendre. Quelqu'un qui cadre, réfléchit et
     * referme n'a rien envoyé et n'avait donc rien à accepter.
     */
    fun onAnalyse() {
        val current = state.value
        if (!current.analysable) return

        analysis = viewModelScope.launch {
            if (current.photo != null && !consent.accepted()) {
                state.update { it.copy(consentNeeded = true, provider = providerName()) }
                return@launch
            }
            analyse(current)
        }
    }

    /** L'avertissement accepté : on enregistre, et on envoie dans la foulée. */
    fun onConsent() {
        val current = state.value
        if (current.photo == null) return

        state.update { it.copy(consentNeeded = false) }
        analysis = viewModelScope.launch {
            consent.accept()
            analyse(current)
        }
    }

    /** L'avertissement refusé : rien ne part, et la photo reste là. */
    fun onConsentDeclined() {
        state.update { it.copy(consentNeeded = false) }
    }

    /**
     * Coupe l'appel en vol.
     *
     * L'annulation traverse le `withContext` de l'adaptateur et ferme la connexion :
     * ce qui n'est pas parti n'est pas facturé, et ce qui est parti ne sera pas attendu.
     */
    fun onCancel() {
        analysis?.cancel()
        analysis = null
        state.update { it.settled() }
    }

    /**
     * Après que l'écran est parti vers la validation.
     *
     * Sans quoi revenir en arrière — le geste qui reprend une photo mal comprise ou
     * corrige une phrase — repartirait aussitôt vers une validation dont le dépôt est
     * déjà vide.
     */
    fun onNavigated() {
        state.update { it.copy(analysed = false) }
    }

    /**
     * Ce qui part, et sous quelle source.
     *
     * **La photo l'emporte quand il y en a une**, et le texte devient sa précision :
     * c'est le levier de justesse le moins coûteux qui existe — « l'assiette fait
     * 24 cm » — là où une phrase seule décrit tout le repas.
     */
    private suspend fun analyse(shown: AnalyseUiState) {
        state.update { it.settled().copy(analysing = true, error = null) }
        val written = shown.text.trim()

        // Rien n'entoure cet appel : une annulation doit traverser. `onCancel` a deja
        // remis l'ecran en etat, et l'attraper ici pour ecrire un echec ferait revivre
        // un etat que l'utilisateur vient de quitter.
        //
        // Le rangement de la photo, la resolution et le depot sont dans le cas d'usage,
        // depuis que la barre du bas envoie la meme chose sans ouvrir d'ecran (D131) ;
        // ce qu'il annonce en route passe par `progress` (D156, D176).
        val outcome = when (val photo = shown.photo) {
            null -> analyseMeal(RecognitionInput.Text(written), progress)
            else -> analyseMeal(RecognitionInput.Photo(photo.jpeg, written.ifBlank { null }), progress)
        }

        state.update { current ->
            when (outcome) {
                is RecognitionOutcome.Recognized -> current.settled().copy(analysed = true)
                is RecognitionOutcome.Failed -> current.settled().copy(error = outcome.error)
            }
        }
    }

    /** Le fournisseur actif, pour que l'avertissement le nomme. */
    private suspend fun providerName(): String =
        runCatching { settings.current() }.getOrNull()?.provider?.displayName.orEmpty()

    private companion object {
        const val MEMORY_KEY = "hexavore.analyse.text"
    }
}

/**
 * L'écran quand plus rien n'est en vol : ni analyse, ni relance, ni estimation, ni note.
 *
 * Un seul endroit pour ce que l'attente affiche, parce que tout retombe ensemble —
 * succès, échec ou annulation — et qu'en oublier un laisserait une phrase d'attente sous
 * un écran qui n'attend plus.
 */
private fun AnalyseUiState.settled(): AnalyseUiState =
    copy(analysing = false, retrying = false, estimating = false, slow = null)
