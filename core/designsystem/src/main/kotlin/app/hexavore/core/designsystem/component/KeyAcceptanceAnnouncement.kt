package app.hexavore.core.designsystem.component

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.res.stringResource
import app.hexavore.core.designsystem.R
import app.hexavore.domain.ai.AcceptedKey

/**
 * Dit qu'une clé vient d'être acceptée, une fois, sur l'écran où l'on revient.
 *
 * ### Ici et non dans un écran
 *
 * Deux écrans peuvent avoir à la dire — l'accueil, d'où partent la plupart des gens, et
 * le hub des réglages — et ce doit être **la même phrase** : c'est le raisonnement de
 * [aiErrorMessage], qui vit ici pour que trois écrans ne rédigent pas chacun leur
 * version d'« il n'y a pas de réseau » ([D157][decisions]).
 *
 * ### Ce qu'elle dit
 *
 * **Ce qui devient possible**, et non qu'une clé est rangée : « chargement photo et
 * texte possible » est la seule chose qu'on attendait de cette démarche. Un modèle qui
 * ne lit pas les images a sa propre phrase — promettre la photo serait mentir au moment
 * précis où l'on vient d'obtenir la confiance de quelqu'un.
 *
 * ### Combien de temps
 *
 * **La durée longue, avec une croix.** C'est la conclusion d'une démarche qui a demandé
 * de sortir de l'application ; elle doit pouvoir se lire sans se presser, et se fermer
 * dès qu'elle l'est.
 *
 * ### Ce qu'elle ne fait pas
 *
 * **Elle ne chasse pas la barre en cours.** Sur l'accueil, ce peut être celle qui
 * rattrape un plat supprimé : la fermer avant terme rendrait la suppression définitive.
 * L'hôte les fait passer l'une après l'autre.
 *
 * Le modèle est prévenu **à la fin**, dans un `finally` : le prévenir d'abord changerait
 * la clé de cet effet, donc l'annulerait, donc annulerait l'affichage qu'il demandait.
 * C'est la leçon de la copie de plat, dans `:feature:entry`.
 *
 * [decisions]: docs/11-decisions.md
 */
@Composable
fun AnnounceKeyAcceptance(accepted: AcceptedKey?, host: SnackbarHostState, onShown: () -> Unit) {
    val message = accepted?.let {
        stringResource(if (it.vision) R.string.ds_ai_key_accepted else R.string.ds_ai_key_accepted_text_only)
    }

    LaunchedEffect(accepted) {
        if (message == null) return@LaunchedEffect
        try {
            host.showSnackbar(message = message, withDismissAction = true, duration = SnackbarDuration.Long)
        } finally {
            onShown()
        }
    }
}
