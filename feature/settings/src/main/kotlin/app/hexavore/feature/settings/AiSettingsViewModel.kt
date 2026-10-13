package app.hexavore.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.hexavore.core.designsystem.component.diagnostic
import app.hexavore.core.designsystem.component.messageRes
import app.hexavore.domain.ai.AcceptedKey
import app.hexavore.domain.ai.AiConfiguration
import app.hexavore.domain.ai.AiCredentials
import app.hexavore.domain.ai.AiProbe
import app.hexavore.domain.ai.AiProvider
import app.hexavore.domain.ai.AiSetup
import app.hexavore.domain.ai.AiUsageEntry
import app.hexavore.domain.ai.AiUsageLog
import app.hexavore.domain.ai.ApiKey
import app.hexavore.domain.ai.KeyAcceptance
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
 * **Valider teste puis enregistre, dans cet ordre, et s'arrête au premier refus.**
 * C'étaient deux boutons — « Tester » et « Utiliser » —, et les deux se sont révélés
 * des pièges : tester sans enregistrer laissait quelqu'un sur un « clé valide » en
 * croyant avoir fini, et enregistrer sans tester laissait découvrir le refus devant une
 * assiette. Un seul geste, et rien n'est rangé que le fournisseur n'ait accepté
 * ([D156][decisions]).
 *
 * [decisions]: docs/11-decisions.md
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
 * la seule information qui décide vraiment — et parce que **c'est lui qu'on déplie à
 * l'arrivée** ([D156][decisions]).
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
    private val acceptance: KeyAcceptance,
    usageLog: AiUsageLog,
) : ViewModel() {
    private val editor = MutableStateFlow(Editor())

    /**
     * Ce qui est enregistré, **ou `null` tant que le trousseau n'a pas répondu**.
     *
     * La valeur nulle n'est pas une commodité : c'est elle qui empêche le formulaire de
     * s'ouvrir sur un champ vide. Le champ de saisie du projet ne lit sa valeur initiale
     * qu'à sa première composition ([DraftTextField][app.hexavore.core.designsystem.component.DraftTextField],
     * et pour de bonnes raisons) ; or le fournisseur recommandé est désormais déplié
     * d'office, donc le champ se composerait avant la lecture du trousseau et garderait
     * la clé vide d'une personne qui en a une ([D156][decisions]).
     *
     * **`Eagerly` et non `WhileSubscribed`.** Cet objet vit exactement le temps de
     * l ecran ; suspendre la collecte cinq secondes apres que la vue s en detache
     * n economise rien et rend `value` dependant de l existence d un abonne -- une
     * subtilite qui se paie au premier test comme au premier bug d affichage.
     *
     * [decisions]: docs/11-decisions.md
     */
    private val setup: StateFlow<AiSetup?> =
        credentials.observe().stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /**
     * Le compteur, observé et non relu.
     *
     * L'écran reste ouvert pendant qu'on valide : un compteur qui ne bougerait qu'à la
     * réouverture laisserait croire que la vérification n'a rien coûté.
     */
    private val usage: StateFlow<List<UsageRow>> = usageLog
        .observe()
        .map { entries -> entries.map { it.toRow() } }
        .catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val uiState: StateFlow<AiSettingsUiState> =
        combine(setup, editor, usage) { stored, edited, counted ->
            // **Rien ne s'affiche avant la lecture du trousseau**, cartes comprises :
            // un ecran qui annonce « aucune cle » puis se corrige dit deux choses
            // contradictoires en un clin d'oeil, et la premiere est celle qui inquiete.
            if (stored == null) return@combine AiSettingsUiState()

            val form = edited.formFor(stored)
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
                form = form,
                check = edited.check,
                usage = counted,
                // « Utilise » et non « Valider la cle » : le fournisseur ouvert sert
                // deja, et rien n'a bouge depuis. La comparaison porte sur le formulaire
                // prerempli -- actif seul dirait « Utilise » sous une cle qu'on vient de
                // modifier.
                inUse = edited.open != null &&
                    edited.open == stored.active &&
                    form.sameValuesAs(stored.formFor(edited.open)),
                validated = edited.validated,
            )
        }.stateIn(viewModelScope, SharingStarted.Eagerly, AiSettingsUiState())

    /**
     * Ouvre un fournisseur, **rempli de ce qui est enregistré**.
     *
     * Rouvrir le même le referme : c'est le geste attendu d'un panneau dépliant, et
     * cela évite un second bouton dont personne ne chercherait la place.
     */
    fun onOpen(provider: AiProvider) {
        editor.value = if (editor.value.open == provider) Editor(open = null) else Editor(open = provider)
    }

    fun onKey(value: String) = editForm { it.copy(apiKey = value) }

    fun onModel(value: String) = editForm { it.copy(model = value) }

    fun onBaseUrl(value: String) = editForm { it.copy(baseUrl = value) }

    fun onReveal() = editForm { it.copy(revealed = !it.revealed) }

    /**
     * Un appel réel avec **ce qui est dans le formulaire**, puis l'enregistrement.
     *
     * ### Pourquoi l'ordre ne s'inverse pas
     *
     * Enregistrer d'abord rendrait le fournisseur actif avec une clé que personne n'a
     * éprouvée : les boutons d'analyse s'allumeraient, et le refus se découvrirait
     * devant une assiette. C'est le pire moment pour l'apprendre, et c'était la raison
     * d'être du bouton « Tester » — qu'on ne demande plus d'appuyer, puisque la
     * validation le fait ([D156][decisions]).
     *
     * ### Ce qu'un refus laisse derrière lui
     *
     * **Rien.** Ni clé rangée, ni fournisseur activé : l'écran garde le formulaire tel
     * quel, affiche ce que le fournisseur a répondu, et on reste là. Une clé mal
     * recopiée se corrige à l'endroit où elle est fausse.
     *
     * [decisions]: docs/11-decisions.md
     */
    fun onValidate() {
        // Les trois conditions en une : pas de fournisseur ouvert, pas de trousseau lu,
        // ou un formulaire incomplet -- et dans les trois cas, rien ne part au reseau.
        val provider = editor.value.open
        val form = setup.value?.let { editor.value.formFor(it) }
        if (provider == null || form == null || !form.complete) return

        editor.value = editor.value.copy(check = KeyCheck.Running)
        viewModelScope.launch {
            when (val outcome = probe.probe(form.configurationFor(provider))) {
                is ProbeOutcome.Reachable -> keep(provider, form, outcome.vision)
                is ProbeOutcome.Failed ->
                    editor.value = editor.value.copy(
                        check = KeyCheck.Refused(outcome.error.messageRes, outcome.error.diagnostic),
                    )
            }
        }
    }

    /**
     * L'écran s'est refermé : il n'y a plus rien à consommer.
     *
     * Sans cela, revenir sur l'écran — le geste de quelqu'un qui veut vérifier ce qu'il
     * vient d'enregistrer — le refermerait aussitôt.
     */
    fun onValidationShown() {
        editor.value = editor.value.copy(validated = false)
    }

    /**
     * Range la clé que le fournisseur vient d'accepter, et annonce la nouvelle.
     *
     * **La nouvelle part avant que l'écran ne se referme**, et elle part ailleurs que
     * dans son état : cet écran ne survit pas à son propre succès. C'est l'écran sur
     * lequel on revient — l'accueil le plus souvent, le hub parfois — qui la dira
     * ([D157][decisions]).
     *
     * La pastille de refus est éteinte au passage : la clé qui avait été refusée n'est
     * plus celle-là, et la pastille n'a plus rien à désigner. Elle se rallumera d'elle-
     * même si la neuve est refusée en analyse — ce qui est une information, et non un
     * souvenir.
     *
     * [decisions]: docs/11-decisions.md
     */
    private suspend fun keep(provider: AiProvider, form: ProviderForm, vision: Boolean) {
        credentials.save(provider, form.credentials())
        rejection.clear()
        acceptance.announce(AcceptedKey(vision))
        editor.value = editor.value.copy(check = KeyCheck.Idle, validated = true)
    }

    /**
     * Toute frappe efface le refus du dernier essai.
     *
     * Sans cela, un « clé refusée » resterait affiché sous une clé qu'on vient de
     * corriger — la pire forme d'information périmée, puisqu'elle a l'air fraîche.
     */
    private fun editForm(change: (ProviderForm) -> ProviderForm) {
        val current = editor.value
        val stored = setup.value ?: return
        editor.value = current.copy(draft = change(current.formFor(stored)), check = KeyCheck.Idle)
    }
}

/**
 * Le brouillon de l'écran : quel fournisseur est ouvert, ce qu'on y a tapé, et l'essai.
 *
 * **[draft] est nul tant que personne n'a tapé**, et c'est ce qui permet au formulaire
 * d'être prérempli sans que le modèle attende les réglages pour s'ouvrir : la valeur
 * affichée est alors celle qui est enregistrée, relue à chaque recomposition. Un
 * brouillon initialisé à vide aurait affiché un champ vide le temps que le trousseau
 * réponde, puis l'aurait gardé vide — un formulaire qui efface la clé de quelqu'un
 * parce qu'il s'est ouvert trop vite.
 *
 * **[open] démarre sur le fournisseur qu'on met en avant** ([D156][decisions]) : c'est
 * lui qu'on vient configurer dans la quasi-totalité des cas, et le déplier d'office
 * retire un appui qu'il fallait deviner.
 *
 * [decisions]: docs/11-decisions.md
 */
private data class Editor(
    val open: AiProvider? = OFFERED.firstOrNull(),
    val draft: ProviderForm? = null,
    val check: KeyCheck = KeyCheck.Idle,
    val validated: Boolean = false,
) {
    /** Ce qu'on a tapé, ou ce qui est rangé pour le fournisseur ouvert. */
    fun formFor(stored: AiSetup): ProviderForm = draft ?: open?.let(stored::formFor) ?: ProviderForm()
}

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
 * `equals` de structure ferait passer le bouton de « Utilisé » à « Valider la clé »
 * parce qu'on a appuyé sur l'œil.
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

/** Un compte du domaine, prêt à s'afficher : les appels, et les jetons additionnés. */
private fun AiUsageEntry.toRow() = UsageRow(
    provider = provider,
    model = model,
    calls = calls,
    tokens = input + output,
)
