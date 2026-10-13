package app.hexavore.core.designsystem.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import app.hexavore.core.designsystem.R
import app.hexavore.domain.ai.AiError

/**
 * Ce qu'une issue d'IA dit à l'utilisateur, en une phrase.
 *
 * **Ici et non dans un écran**, parce que [docs/02][parcours] l'exige pour les modales
 * de capture : *« mêmes erreurs, mêmes messages »* que la photo — et le bouton
 * « Tester » des réglages pose exactement la même question à exactement le même port.
 * Trois écrans qui rédigent chacun leur version d'« il n'y a pas de réseau » finissent
 * par en avoir trois, dont deux qui vieillissent mal. C'est le raisonnement de
 * [SourceBadge], qui traduit déjà une énumération du domaine au même endroit.
 *
 * Chaque phrase dit **ce qui s'est passé et ce qu'on peut faire**, jamais un code.
 * Celui du fournisseur, quand il en donne un, s'affiche à côté et non à la place
 * ([D78][decisions]) : le message dit le geste, le détail dit la cause.
 *
 * [parcours]: docs/02-parcours-et-ecrans.md
 * [decisions]: docs/11-decisions.md
 */
@Composable
fun aiErrorMessage(error: AiError): String = stringResource(error.messageRes)

/**
 * La ressource, pour les cas qui portent le texte plutôt que de l'afficher.
 *
 * `NothingRecognized` et `Unparseable` partagent la leur : ce que l'utilisateur peut
 * faire est le même — reformuler, ou reprendre la photo —, et distinguer « le modèle
 * n'a rien vu » de « sa réponse était illisible » lui demanderait de savoir ce qu'est
 * une réponse de modèle.
 */
val AiError.messageRes: Int
    get() = when (this) {
        AiError.InvalidKey -> R.string.ds_ai_error_invalid_key
        AiError.QuotaExceeded -> R.string.ds_ai_error_quota
        // **La phrase dit que la cle est hors de cause**, ce qui est l'information
        // qui manquait : un « service indisponible » affiche pendant qu'on vient de
        // coller sa premiere cle se lit comme un refus de cette cle (D156).
        AiError.ProviderBusy -> R.string.ds_ai_error_busy
        AiError.NoNetwork -> R.string.ds_ai_error_network
        AiError.Timeout -> R.string.ds_ai_error_timeout
        AiError.VisionUnsupported -> R.string.ds_ai_error_vision
        AiError.NoProviderConfigured -> R.string.ds_ai_error_not_configured
        AiError.Unparseable, AiError.NothingRecognized -> R.string.ds_ai_error_unreadable
        is AiError.Server -> R.string.ds_ai_error_server
    }

/**
 * Ce que le fournisseur reproche, quand il le dit — et rien quand il ne dit rien.
 *
 * `null` sur les issues qui nomment déjà le geste : une clé refusée n'a pas besoin
 * qu'on lui ajoute la prose du fournisseur, et l'afficher encombrerait la seule chose
 * qu'il y ait à lire ([D78][decisions]).
 *
 * [decisions]: docs/11-decisions.md
 */
val AiError.diagnostic: String?
    get() = (this as? AiError.Server)?.let { listOfNotNull("HTTP ${it.status}", it.detail).joinToString(" · ") }
