package app.hexavore.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.hexavore.core.designsystem.component.diagnostic
import app.hexavore.core.designsystem.component.messageRes
import app.hexavore.domain.ai.AiConfiguration
import app.hexavore.domain.ai.AiCredentials
import app.hexavore.domain.ai.AiProbe
import app.hexavore.domain.ai.AiProvider
import app.hexavore.domain.ai.AiSetup
import app.hexavore.domain.ai.AiUsageEntry
import app.hexavore.domain.ai.AiUsageLog
import app.hexavore.domain.ai.ApiKey
import app.hexavore.domain.ai.ProbeOutcome
import app.hexavore.domain.ai.ProviderCredentials
import app.hexavore.domain.ai.ProviderStatus
import app.hexavore.domain.notice.KeyRejection
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * L'écran des fournisseurs d'IA.
 *
 * **Le formulaire n'est pas dans les réglages enregistrés**, et c'est la distinction
 * qui structure cette classe : ce qu'on tape est un brouillon, ce qui est rangé est
 * une configuration. Les confondre aurait fait qu'une frappe atteigne le trousseau, et
 * qu'un test porte sur autre chose que ce qu'on lit à l'écran.
 *
 * **Tester n'enregistre pas, et enregistrer ne teste pas.** Ce sont deux gestes parce
 * qu'ils répondent à deux questions : « est-ce que ça marche » et « garde ça ». Les
 * lier obligerait à écrire une clé fausse pour découvrir qu'elle est fausse, ou à
 * repayer un appel à chaque correction de modèle.
 */
/**
 * Les fournisseurs proposés, le gratuit d'abord.
 *
 * **Deux et non six.** Le code des six existe et passe ses tests ; ce qui manque aux
 * quatre autres est un appel réel avec une vraie clé, et leur statut le dit déjà. Les
 * afficher quand même, grisés, revenait à poser six questions à quelqu'un qui n'en
 * cherchait aucune — et à noyer le seul qui soit gratuit ([D138][decisions]).
 *
 * L'ordre n'est pas celui de l'énumération : le gratuit passe devant, parce que c'est
 * la seule information qui décide vraiment.
 *
 * [decisions]: docs/11-decisions.md
 */
private val OFFERED = AiProvider.entries
    .filter { it.status != ProviderStatus.SUSPENDED }
    .sortedByDescending { it.free }

@HiltViewModel
class AiSettingsViewModel @Inject constructor(
    private val credentials: AiCredentials,
    private val rejection: KeyRejection,
    private val probe: AiProbe,
    usageLog: AiUsageLog,
) : ViewModel() {
    private val editor = MutableStateFlow(Editor())

    /**
     * **`Eagerly` et non `WhileSubscribed`.** Cet objet vit exactement le temps de
     * l ecran ; suspendre la collecte cinq secondes apres que la vue s en detache
     * n economise rien et rend `value` dependant de l existence d un abonne -- une
     * subtilite qui se paie au premier test comme au premier bug d affichage.
     */
    private val setup: StateFlow<AiSetup> =
        credentials.observe().stateIn(viewModelScope, SharingStarted.Eagerly, AiSetup())

    /**
     * Le compteur, observé et non relu.
     *
     * L'écran reste ouvert pendant qu'on appuie sur « Tester » : un compteur qui ne
     * bougerait qu'à la réouverture laisserait croire que l'essai n'a rien coûté.
     */
    private val usage: StateFlow<List<UsageRow>> = usageLog
        .observe()
        .map { entries -> entries.map { it.toRow() } }
        .catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val uiState: StateFlow<AiSettingsUiState> =
        combine(setup, editor, usage) { stored, edited, counted ->
            AiSettingsUiState(
                // **Les fournisseurs en reserve ne s'affichent plus** (D138). Les
                // montrer griss avec « bientot » donnait six choix a faire a qui n'en
                // cherchait pas un : il en reste deux, le gratuit d'abord.
                rows = OFFERED.map {
                    ProviderRow(
                        provider = it,
                        configured = it in stored.credentials,
                        active = it == stored.active,
                        suspended = false,
                    )
                },
                open = edited.open,
                form = edited.form,
                probe = edited.probe,
                usage = counted,
                // « Utilise » et non « Utiliser » : le fournisseur ouvert sert deja, et
                // rien n'a bouge depuis. La comparaison porte sur le formulaire prerempli
                // -- actif seul dirait « Utilise » sous une cle qu'on vient de modifier.
                inUse = edited.open != null &&
                    edited.open == stored.active &&
                    edited.form.sameValuesAs(stored.formFor(edited.open)),
            )
        }.stateIn(viewModelScope, SharingStarted.Eagerly, AiSettingsUiState())

    /**
     * Ouvre un fournisseur, **rempli de ce qui est enregistré**.
     *
     * Rouvrir le même le referme : c'est le geste attendu d'un panneau dépliant, et
     * cela évite un second bouton dont personne ne chercherait la place.
     */
    fun onOpen(provider: AiProvider) {
        if (editor.value.open == provider) {
            editor.value = Editor()
        } else {
            editor.value = Editor(open = provider, form = setup.value.formFor(provider))
        }
    }

    fun onKey(value: String) = editForm { it.copy(apiKey = value) }

    fun onModel(value: String) = editForm { it.copy(model = value) }

    fun onBaseUrl(value: String) = editForm { it.copy(baseUrl = value) }

    fun onReveal() = editForm { it.copy(revealed = !it.revealed) }

    /**
     * Un appel réel avec **ce qui est dans le formulaire**.
     *
     * L'issue reste affichée jusqu'à la frappe suivante : un résultat qui s'efface
     * seul oblige à retester pour se rappeler ce qu'il disait.
     */
    fun onTest() {
        val provider = editor.value.open ?: return
        val form = editor.value.form
        if (!form.complete) return

        editor.value = editor.value.copy(probe = ProbeState.Running)
        viewModelScope.launch {
            editor.value = editor.value.copy(probe = probe.probe(form.configurationFor(provider)).toState())
        }
    }

    fun onSave() {
        val provider = editor.value.open ?: return
        val form = editor.value.form
        if (!form.complete) return

        viewModelScope.launch {
            credentials.save(provider, form.credentials())
            // La cle qui avait ete refusee n'est plus celle-la : la pastille n'a plus
            // rien a designer. Elle se rallumera d'elle-meme si la neuve est refusee
            // aussi -- ce qui est une information, et non un souvenir.
            rejection.clear()
            // **Le formulaire reste ouvert**, contrairement a avant. Il se refermait
            // pour ne pas inviter a corriger ce qu'on venait d'ecrire ; mais le bouton
            // dit maintenant « Utilise », et une confirmation doit se lire la ou le
            // doigt a appuye. Une carte qui se replie emporte sa reponse avec elle.
            editor.value = editor.value.copy(probe = ProbeState.Idle)
        }
    }

    fun onForget() {
        val provider = editor.value.open ?: return
        viewModelScope.launch {
            credentials.forget(provider)
            editor.value = Editor()
        }
    }

    /**
     * Toute frappe efface l'issue du dernier essai.
     *
     * Sans cela, un « clé valide » resterait affiché sous une clé qu'on vient de
     * modifier — la pire forme d'information périmée, puisqu'elle a l'air fraîche.
     */
    private fun editForm(change: (ProviderForm) -> ProviderForm) {
        editor.value = editor.value.copy(form = change(editor.value.form), probe = ProbeState.Idle)
    }
}

/** Le brouillon de l'écran : quel fournisseur est ouvert, ce qu'on y a tapé, et l'essai. */
private data class Editor(
    val open: AiProvider? = null,
    val form: ProviderForm = ProviderForm(),
    val probe: ProbeState = ProbeState.Idle,
)

/**
 * Le formulaire d'un fournisseur, prérempli.
 *
 * Une clé absente donne des champs vides et **l'URL par défaut du fournisseur** :
 * c'est la valeur juste dans tous les cas sauf le relais, et celui qui monte un relais
 * saura la remplacer. La laisser vide aurait fait d'un champ obligatoire une énigme.
 */
private fun AiSetup.formFor(provider: AiProvider): ProviderForm {
    val stored = credentials[provider]
    return ProviderForm(
        apiKey = stored?.apiKey?.value.orEmpty(),
        model = stored?.model ?: provider.suggestedModels.firstOrNull().orEmpty(),
        baseUrl = stored?.baseUrl ?: provider.defaultBaseUrl,
    )
}

/**
 * Les trois valeurs saisies sont-elles celles d'un autre formulaire ?
 *
 * **La révélation ne compte pas.** Montrer sa clé ne la modifie pas, et un
 * `equals` de structure ferait passer le bouton de « Utilisé » à « Utiliser » parce
 * qu'on a appuyé sur l'œil.
 */
private fun ProviderForm.sameValuesAs(other: ProviderForm): Boolean =
    apiKey == other.apiKey && model == other.model && baseUrl == other.baseUrl

private fun ProviderForm.credentials() =
    ProviderCredentials(apiKey = ApiKey(apiKey.trim()), model = model.trim(), baseUrl = baseUrl.trim())

private fun ProviderForm.configurationFor(provider: AiProvider) = AiConfiguration(
    provider = provider,
    apiKey = ApiKey(apiKey.trim()),
    model = model.trim(),
    baseUrl = baseUrl.trim(),
)

private fun ProbeOutcome.toState(): ProbeState = when (this) {
    is ProbeOutcome.Reachable -> ProbeState.Succeeded(vision)
    is ProbeOutcome.Failed -> ProbeState.Failed(error.messageRes, error.diagnostic)
}

/** Un compte du domaine, prêt à s'afficher : les appels, et les jetons additionnés. */
private fun AiUsageEntry.toRow() = UsageRow(
    provider = provider,
    model = model,
    calls = calls,
    tokens = input + output,
)
