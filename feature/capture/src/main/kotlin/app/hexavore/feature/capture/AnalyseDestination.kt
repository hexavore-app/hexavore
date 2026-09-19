package app.hexavore.feature.capture

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import kotlinx.serialization.Serializable

/**
 * L'écran d'IA, comme destination.
 *
 * **Une seule, là où il y en avait deux** ([D120][decisions]). « Photographier » et
 * « Décrire » se distinguaient par ce qu'on y arrivait avec — une assiette devant soi,
 * ou une phrase à écrire — et par rien d'autre : même reconnaissance, même dépôt,
 * mêmes erreurs, même sortie. On arrive maintenant avec l'un ou l'autre.
 *
 * Aucun argument : l'intention se lit sur l'écran, pas dans la route.
 *
 * [decisions]: docs/11-decisions.md
 */
@Serializable
data object AnalyseDestination

/** Ouvre l'écran d'IA. */
fun NavController.navigateToAnalyse() {
    navigate(AnalyseDestination)
}

/**
 * Déclare l'écran dans un graphe.
 *
 * Trois sorties. La première ne porte rien : ce que l'analyse a produit attend dans le
 * dépôt des propositions, parce qu'une route ne transporte pas cinq lignes. La seconde
 * est **la saisie manuelle**, que l'échec d'une analyse offre — un fournisseur en panne
 * ne doit pas empêcher de noter son repas ([docs/02][parcours]). L'écran s'efface
 * derrière la validation, comme le scan et la recherche.
 *
 * [parcours]: docs/02-parcours-et-ecrans.md
 */
fun NavGraphBuilder.analyseScreen(onProposal: () -> Unit, onManual: () -> Unit, onClose: () -> Unit) {
    composable<AnalyseDestination> {
        AnalyseRoute(onProposal = onProposal, onManual = onManual, onClose = onClose)
    }
}
