package app.hexavore.feature.settings

import androidx.annotation.StringRes
import app.hexavore.domain.ai.AiProvider

/**
 * Ce que l'écran des fournisseurs d'IA montre.
 *
 * **Une liste, alors qu'il n'y a qu'un fournisseur.** L'écran est écrit contre
 * l'énumération et non contre Anthropic : le jour où le deuxième arrive, il apparaît
 * sans qu'une ligne d'affichage bouge. Un écran écrit pour un seul aurait demandé
 * d'être généralisé, et [docs/12][plan] rappelle que ce genre de généralisation se
 * fait trois fois.
 *
 * **Un seul formulaire ouvert à la fois** ([open]). Six formulaires dépliés feraient
 * un écran où l'on ne trouve plus le sien, et l'état « lequel est ouvert » est une
 * information de moins à tenir que six états d'ouverture.
 *
 * [plan]: docs/12-plan-de-developpement.md
 */
data class AiSettingsUiState(
    val rows: List<ProviderRow> = emptyList(),
    /**
     * Le fournisseur déplié, **gratuit d'abord et ouvert d'office**.
     *
     * Tout était replié, et il fallait deviner qu'une carte se déplie avant de voir
     * qu'il y a une clé à coller. Aucun testeur ne l'a deviné : c'est le premier des
     * trois murs que [D156][decisions] retire. Celui qu'on met en avant est donc ouvert
     * dès l'arrivée, et l'autre reste une carte qu'on déplie si on la cherche.
     *
     * [decisions]: docs/11-decisions.md
     */
    val open: AiProvider? = null,
    val form: ProviderForm = ProviderForm(),
    /** Où en est le bouton « Valider la clé ». */
    val check: KeyCheck = KeyCheck.Idle,
    /**
     * Ce que les analyses ont consommé, regroupé par fournisseur.
     *
     * En bas de l'écran, comme [docs/02][parcours] le prévoit, et **sous** la liste
     * des clés : on vient ici pour brancher un fournisseur, on repart en sachant ce
     * qu'il a coûté.
     *
     * [parcours]: docs/02-parcours-et-ecrans.md
     */
    val usage: List<UsageRow> = emptyList(),
    /**
     * Le fournisseur ouvert est **celui qui sert**, et son formulaire n'a pas bougé
     * depuis l'enregistrement.
     *
     * C'est ce que le bouton lit pour dire « Utilisé » plutôt que « Valider la clé ».
     * Les deux conditions comptent : actif seul dirait « Utilisé » sous une clé qu'on
     * vient de modifier sans l'enregistrer, ce qui est exactement le contraire de la
     * vérité.
     */
    val inUse: Boolean = false,
    /**
     * `true` quand la clé vient d'être acceptée **et** enregistrée : l'écran se referme.
     *
     * Un drapeau plutôt qu'un événement, comme `analysed` sur l'écran d'IA : la
     * navigation est un effet, et la route le consomme une fois.
     *
     * **La nouvelle n'est pas ici.** Elle est partie par `KeyAcceptance`, vers l'écran
     * sur lequel on revient — qui n'est pas toujours le même, et que cet écran-ci n'a
     * pas à connaître ([D157][decisions]).
     *
     * [decisions]: docs/11-decisions.md
     */
    val validated: Boolean = false,
)

/**
 * Une ligne de compteur : un modèle, et ce qu'il a consommé.
 *
 * **Pas de montant.** L'application portait une table de tarifs et affichait une
 * estimation datée ; elle est retirée. Un prix relevé un jour donné vieillit sans
 * prévenir, personne ne le corrige, et une facture approximative affichée avec
 * l'autorité d'un chiffre est pire qu'aucune facture — seule celle du fournisseur
 * fait foi, et lui la donne exactement.
 *
 * Ce qui reste est ce que l'application sait de première main : combien d'appels sont
 * partis, et combien de jetons ils ont consommés.
 */
data class UsageRow(val provider: AiProvider, val model: String, val calls: Int, val tokens: Int)

/** Une ligne de la liste : qui, et où il en est. */
data class ProviderRow(
    val provider: AiProvider,
    val configured: Boolean,
    val active: Boolean,
    /**
     * `true` quand ce fournisseur est écrit mais pas encore éprouvé sur un vrai compte.
     *
     * Sa carte s'affiche quand même, en retrait et sans formulaire : le cacher
     * laisserait croire qu'il n'existe pas, et le proposer ferait payer à quelqu'un la
     * découverte d'un défaut que personne n'a cherché.
     */
    val suspended: Boolean = false,
)

/**
 * Le formulaire ouvert.
 *
 * [revealed] tient la révélation temporaire que [docs/05][ia] demande : le champ est
 * masqué, et un œil le découvre le temps de vérifier une clé qu'on vient de coller.
 * Il retombe à `false` dès qu'on change de fournisseur — une clé révélée qui le reste
 * finirait par l'être devant quelqu'un.
 *
 * [ia]: docs/05-ia.md
 */
data class ProviderForm(
    val apiKey: String = "",
    val model: String = "",
    val baseUrl: String = "",
    val revealed: Boolean = false,
) {
    /**
     * Rien d'incomplet ne part au réseau.
     *
     * Le modèle et l'URL comptent autant que la clé : un modèle vide rend un `404`
     * que l'utilisateur lirait comme une clé refusée. Les deux sont désormais
     * préremplis et rangés sous « Réglages avancés » ([D156][decisions]), donc cette
     * condition ne barre en pratique que la clé — mais elle reste écrite, parce que
     * c'est elle qui tient la promesse.
     *
     * [decisions]: docs/11-decisions.md
     */
    val complete: Boolean get() = apiKey.isNotBlank() && model.isNotBlank() && baseUrl.isNotBlank()
}

/**
 * Où en est la validation de la clé.
 *
 * **Un seul geste là où il y en avait deux.** L'écran portait « Tester » et
 * « Utiliser », et c'était un piège : tester sans enregistrer laissait quelqu'un sur
 * un « clé valide » en croyant avoir fini, et enregistrer sans tester laissait
 * découvrir le refus devant une assiette. Valider fait les deux dans cet ordre, et
 * n'enregistre que ce que le fournisseur a accepté ([D156][decisions]).
 *
 * Il n'y a donc **pas d'état « réussi »** : une clé acceptée est une clé enregistrée,
 * et l'écran cède la place. Ce qui reste à montrer ici est l'attente, et le refus.
 *
 * [decisions]: docs/11-decisions.md
 */
sealed interface KeyCheck {
    data object Idle : KeyCheck

    /** L'appel est parti. Le bouton ne se rappuie pas, et l'écran le montre. */
    data object Running : KeyCheck

    /**
     * Le fournisseur n'a pas accepté, et **rien n'a été enregistré**.
     *
     * Affiché sous le champ et non dans une boîte : la clé reste visible pendant qu'on
     * la corrige, et le message s'efface à la frappe suivante. Une boîte à fermer
     * cacherait le champ qu'elle demande de corriger.
     *
     * @param detail ce que le fournisseur a répondu, quand l'issue ne suffit pas à le
     *   diagnostiquer. Affiché **ici et nulle part ailleurs** : cet écran est un
     *   instrument de diagnostic, et « le service est indisponible » n'y aide personne
     *   — ni celui qui configure, ni celui qui doit corriger.
     */
    data class Refused(@StringRes val messageRes: Int, val detail: String? = null) : KeyCheck
}

/** Les gestes de l'écran, rassemblés pour que la composable n'en prenne qu'un. */
data class AiSettingsActions(
    val onOpen: (AiProvider) -> Unit,
    val onKey: (String) -> Unit,
    val onModel: (String) -> Unit,
    val onBaseUrl: (String) -> Unit,
    val onReveal: () -> Unit,
    /** Teste la clé du formulaire, et ne l'enregistre que si le fournisseur l'accepte. */
    val onValidate: () -> Unit,
    /** La nouvelle est partie vers le hub : il n'y a plus rien à consommer. */
    val onValidationShown: () -> Unit,
    /** Ouvre l'écran qui explique comment obtenir une clé chez ce fournisseur. */
    val onGetKey: (AiProvider) -> Unit,
    val onClose: () -> Unit,
)
