package app.hexavore.feature.home

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import app.hexavore.core.designsystem.component.AnnounceKeyAcceptance
import app.hexavore.domain.ai.AcceptedKey
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * Ce que l'accueil a à annoncer, une fois chacun, dans sa barre du bas.
 *
 * **Deux nouvelles, un seul paramètre** : elles se disent au même endroit, de la même
 * façon, et ne concernent ni la journée ni ses compteurs. Les passer une par une aurait
 * ajouté quatre paramètres à un écran qui en compte déjà vingt ([D157][decisions]).
 *
 * Vides par défaut : les aperçus n'ont rien à annoncer.
 *
 * [decisions]: docs/11-decisions.md
 */
@Immutable
data class HomeNews(
    /** Le jour où la dernière copie est partie, tant qu'elle n'a pas été dite. */
    val copiedTo: LocalDate? = null,
    val onCopyShown: () -> Unit = {},
    /** Une clé vient d'être acceptée sur l'écran des clés, ouvert depuis l'accueil. */
    val keyAccepted: AcceptedKey? = null,
    val onKeyAcceptedShown: () -> Unit = {},
)

/**
 * Les deux annonces, sur l'hôte de la barre d'annulation.
 *
 * **Le même hôte, et c'est voulu.** Il fait passer les barres l'une après l'autre : une
 * annonce qui arrive pendant qu'on peut encore rattraper un plat supprimé attend son
 * tour au lieu de le chasser — la chasser rendrait la suppression définitive.
 */
@Composable
internal fun AnnonceLesNouvelles(news: HomeNews, host: SnackbarHostState) {
    AnnounceKeyAcceptance(accepted = news.keyAccepted, host = host, onShown = news.onKeyAcceptedShown)
    AnnonceLaCopie(jour = news.copiedTo, host = host, onShown = news.onCopyShown)
}

/**
 * Dit où la copie est partie, une fois, puis se tait.
 *
 * **Une copie ne se voit nulle part** : elle atterrit sur une journée qu'on ne regarde
 * pas, et l'accueil n'a pas bougé d'un pixel. Sans ce mot, l'appui sur « Copier » ne
 * produirait aucun signe, et on s'y reprendrait à deux fois — ce qui ferait deux copies
 * ([D144][decisions]).
 *
 * **Sans bouton « Voir »** : on reste sur le jour qu'on regardait, ce qui permet de
 * recopier le même plat sur plusieurs jours de suite sans revenir le chercher
 * ([D157][decisions]).
 *
 * Le modèle est prévenu **à la fin**, dans un `finally`, pour la raison que l'écran de
 * saisie a apprise à ses dépens : le prévenir d'abord change la clé de cet effet, donc
 * l'annule, donc annule l'affichage qu'il demandait.
 *
 * [decisions]: docs/11-decisions.md
 */
@Composable
private fun AnnonceLaCopie(jour: LocalDate?, host: SnackbarHostState, onShown: () -> Unit) {
    // Le format long de l'ecran de saisie : « dimanche 12 octobre 2026 ». Le jour de la
    // semaine compte, c'est souvent lui qu'on a en tete en recopiant un repas.
    val formatter = remember { DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL) }
    val libelle = stringResource(R.string.home_dish_copied_to, jour?.format(formatter).orEmpty())

    LaunchedEffect(jour) {
        if (jour == null) return@LaunchedEffect
        try {
            host.showSnackbar(libelle)
        } finally {
            onShown()
        }
    }
}
