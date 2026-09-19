package app.hexavore.feature.home

import androidx.compose.runtime.Immutable
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import app.hexavore.domain.diary.DishId
import kotlinx.serialization.Serializable

/** L'accueil, comme destination. Aucun argument : c'est l'écran de départ. */
@Serializable
data object HomeDestination

/**
 * Les sorties de l'accueil, en un seul objet.
 *
 * **Des sorties et non des destinations** : le module ne sait pas vers quoi il envoie,
 * ce qui lui évite de dépendre de `:feature:entry`, de `:feature:capture`, de
 * `:feature:onboarding` ni de `:feature:settings`. C'est `:app` qui relie, parce que
 * c'est lui qui assemble.
 *
 * Rassemblées le jour où les quatre modes de saisie ont été là : huit rappels passés
 * un par un font une signature qu'on ne lit plus, et c'est la forme que le projet a
 * déjà retenue pour [HomeActions].
 */
@Immutable
data class HomeRoutes(
    val onAddDish: () -> Unit,
    val onScan: () -> Unit,
    val onAnalyse: () -> Unit,
    val onEditDish: (DishId) -> Unit,
    val onSetUpGoal: () -> Unit,
    val onOpenSettings: () -> Unit,
    val onConfigureAi: () -> Unit,
    val onOpenFavorites: () -> Unit,
    val onOpenWeight: () -> Unit,
)

/**
 * Declare l'accueil dans un graphe.
 *
 * **Une seule destination**, depuis que l'ecran Journee a disparu : l'accueil porte
 * une date, et se promener dans l'historique ne navigue plus. C'est ce qui permet au
 * calendrier de rester a l'ecran pendant qu'on remonte le temps -- un second ecran
 * l'aurait laisse derriere lui.
 */
fun NavGraphBuilder.homeScreen(routes: HomeRoutes) {
    composable<HomeDestination> { HomeRoute(routes = routes) }
}
