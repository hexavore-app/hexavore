package app.hexavore.feature.progress

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import kotlinx.serialization.Serializable

/** L'écran de progression, comme destination. Aucun argument : il n'y en a qu'un. */
@Serializable
data object ProgressDestination

fun NavController.navigateToProgress() = navigate(ProgressDestination)

/**
 * Déclare la progression dans un graphe.
 *
 * Une seule sortie, et c'est la fermeture : rien ne s'atteint depuis cet écran. On y
 * regarde où l'on en est, on n'y décide rien — ce qui fait avancer la progression est
 * ailleurs, dans le fait de noter ses repas.
 */
fun NavGraphBuilder.progressScreen(onClose: () -> Unit) {
    composable<ProgressDestination> { ProgressRoute(onClose = onClose) }
}
