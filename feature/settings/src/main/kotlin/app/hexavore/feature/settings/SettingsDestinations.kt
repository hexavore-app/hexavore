package app.hexavore.feature.settings

import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import app.hexavore.domain.ai.AiProvider
import app.hexavore.domain.diary.FavoriteDishId
import kotlinx.serialization.Serializable

/**
 * Le hub, désormais réel : l'accueil y mène, et les sections mènent aux écrans.
 *
 * C'est ce que [docs/02][parcours] décrivait depuis la conception et que
 * [D59][decisions] avait mis en attente de sa deuxième section.
 *
 * [parcours]: docs/02-parcours-et-ecrans.md
 * [decisions]: docs/11-decisions.md
 */
@Serializable
data object SettingsDestination

/** Les réglages du profil. Aucun argument. */
@Serializable
data object ProfileDestination

/** Les fournisseurs d'IA, leurs clés et leurs modèles. Aucun argument. */
@Serializable
data object AiSettingsDestination

/** Le compte Open Food Facts, et l'instance visée. Aucun argument. */
@Serializable
data object ContributionSettingsDestination

/** Exporter, restaurer, tout effacer. Aucun argument. */
@Serializable
data object BackupDestination

/** Les quatre pastilles et leurs interrupteurs. Aucun argument. */
@Serializable
data object NoticeSettingsDestination

/** Le thème, et bientôt le système d'unités. Aucun argument. */
@Serializable
data object AppearanceDestination

/** Garder les photos des plats, et les effacer. Aucun argument. */
@Serializable
data object PhotoSettingsDestination

/**
 * Comment obtenir une clé chez ce fournisseur. Ouverte depuis les réglages d'IA.
 *
 * **Le fournisseur voyage en chaîne et non en énumération** : une route est un
 * identifiant sérialisé, et `AiProvider` est un type du domaine qui n'a aucune raison
 * d'apprendre à se sérialiser pour que la navigation en parle.
 */
@Serializable
data class AiKeyRetrievalDestination(val provider: String)

/** Les plats enregistrés, et le chemin pour les corriger. Aucun argument. */
@Serializable
data object FavoritesSettingsDestination

/** Le dépôt, le site et l'adresse. Aucun argument. */
@Serializable
data object ContactDestination

fun NavController.navigateToSettings() = navigate(SettingsDestination)

/**
 * Déclare les neuf écrans de réglages dans un graphe.
 *
 * Ensemble parce qu'ils partagent une sortie et une seule : **le retour rend l'écran
 * précédent**. Le module ne sait pas lequel c'est, ce qui lui évite de dépendre de
 * `:feature:home`.
 */
fun NavGraphBuilder.settingsScreens(
    navController: NavController,
    /**
     * Ouvre un favori pour le corriger.
     *
     * Elle vient de `:app` parce que l'écran est celui de la saisie : ce module ne
     * dépend pas de `:feature:entry`, et c'est ce qui l'empêche de devenir le carrefour
     * de tous les autres (D149).
     */
    onEditFavorite: (FavoriteDishId) -> Unit,
) {
    composable<SettingsDestination> {
        SettingsHubRoute(
            sections = SettingsSections(
                onOpenProfile = { navController.navigate(ProfileDestination) },
                onOpenAi = { navController.navigate(AiSettingsDestination) },
                onOpenContribution = { navController.navigate(ContributionSettingsDestination) },
                onOpenBackup = { navController.navigate(BackupDestination) },
                onOpenPhotos = { navController.navigate(PhotoSettingsDestination) },
                onOpenNotices = { navController.navigate(NoticeSettingsDestination) },
                onOpenAppearance = { navController.navigate(AppearanceDestination) },
                onOpenFavorites = { navController.navigate(FavoritesSettingsDestination) },
                onOpenContact = { navController.navigate(ContactDestination) },
            ),
            onClose = { navController.popBackStack() },
        )
    }
    composable<ProfileDestination> {
        ProfileRoute(onClose = { navController.popBackStack() })
    }
    aiScreens(navController)
    composable<ContributionSettingsDestination> {
        ContributionSettingsRoute(onClose = { navController.popBackStack() })
    }
    composable<BackupDestination> {
        BackupRoute(onClose = { navController.popBackStack() })
    }
    composable<NoticeSettingsDestination> {
        NoticeSettingsRoute(onClose = { navController.popBackStack() })
    }
    composable<AppearanceDestination> {
        AppearanceRoute(onClose = { navController.popBackStack() })
    }
    composable<PhotoSettingsDestination> {
        PhotoSettingsRoute(onClose = { navController.popBackStack() })
    }
    composable<FavoritesSettingsDestination> {
        FavoritesSettingsRoute(onClose = { navController.popBackStack() }, onEdit = onEditFavorite)
    }
    composable<ContactDestination> {
        ContactRoute(onClose = { navController.popBackStack() })
    }
}

/**
 * Les deux écrans de l'IA : les fournisseurs, et comment obtenir une clé.
 *
 * **Sortis du graphe des réglages quand son seuil de longueur a mordu**, et le découpage
 * suit ce que les choses sont : les neuf autres destinations se déclarent en deux lignes
 * chacune — une route, un retour — là où celles-ci en demandent dix, parce qu'elles se
 * passent quelque chose : l'une ouvre l'autre en lui nommant un fournisseur, et l'autre
 * se referme d'elle-même une fois le site ouvert ([D156][decisions], [D157][decisions]).
 *
 * [decisions]: docs/11-decisions.md
 */
private fun NavGraphBuilder.aiScreens(navController: NavController) {
    composable<AiSettingsDestination> {
        AiSettingsRoute(
            onClose = { navController.popBackStack() },
            onGetKey = { navController.navigate(AiKeyRetrievalDestination(it.name)) },
            // **Un simple retour.** La nouvelle est deja partie par `KeyAcceptance`, et
            // c'est l'ecran sur lequel on revient qui la dit. Elle etait posee sur
            // l'ecran precedent dans l'etat de navigation, et seul le hub savait l'y
            // lire : venus de l'accueil -- c'est-a-dire presque tout le monde --, on ne
            // voyait rien (D157).
            onValidated = { navController.popBackStack() },
        )
    }
    composable<AiKeyRetrievalDestination> { entry ->
        // Le fournisseur est relu par son nom plutot que par `valueOf` : une route
        // forgee a la main ou survivante d'une version ou l'entree existait ne doit pas
        // faire tomber l'application. Sans fournisseur, il n'y a rien a expliquer.
        val named = entry.toRoute<AiKeyRetrievalDestination>().provider
        val provider = AiProvider.entries.firstOrNull { it.name == named }

        if (provider == null) {
            LaunchedEffect(named) { navController.popBackStack() }
        } else {
            AiKeyRetrievalRoute(provider = provider, onClose = { navController.popBackStack() })
        }
    }
}
