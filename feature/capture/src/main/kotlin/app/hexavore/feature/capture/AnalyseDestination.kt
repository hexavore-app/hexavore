package app.hexavore.feature.capture

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import kotlinx.serialization.Serializable

/**
 * L'écran d'IA, comme destination.
 *
 * **Une seule, là où il y en avait deux** ([D120][decisions]). « Photographier » et
 * « Décrire » se distinguaient par ce qu'on y arrivait avec — une assiette devant soi,
 * ou une phrase à écrire — et par rien d'autre : même reconnaissance, même dépôt,
 * mêmes erreurs, même sortie. On arrive maintenant avec l'un ou l'autre.
 *
 * **Un seul argument, et il ne dit pas ce qu'on envoie** : il dit par quoi on commence
 * ([D131][decisions]). L'intention se lit toujours sur l'écran — une photo, une phrase,
 * ou les deux — mais quelqu'un qui vient d'appuyer sur l'appareil photo de la barre du
 * bas a déjà fait son geste, et lui présenter un cadre vide le lui ferait refaire.
 *
 * [decisions]: docs/11-decisions.md
 */
@Serializable
data class AnalyseDestination(
    /**
     * `true` quand l'appareil photo doit s'ouvrir sans qu'on le redemande.
     *
     * **Une seule fois par arrivée**, et l'écran s'en charge : rouvrir l'appareil au
     * retour de la prise de vue ferait une boucle dont on ne sortirait que par le
     * bouton « retour ».
     */
    val shoot: Boolean = false,
)

/**
 * Ouvre l'écran d'IA.
 *
 * @param shoot vrai pour que l'appareil photo s'ouvre dans la foulée — le geste de la
 *   barre du bas, où l'appui sur l'appareil photo *est* la demande de photographier.
 */
fun NavController.navigateToAnalyse(shoot: Boolean = false) {
    navigate(AnalyseDestination(shoot = shoot))
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
    composable<AnalyseDestination> { entry ->
        AnalyseRoute(
            shoot = entry.toRoute<AnalyseDestination>().shoot,
            onProposal = onProposal,
            onManual = onManual,
            onClose = onClose,
        )
    }
}
