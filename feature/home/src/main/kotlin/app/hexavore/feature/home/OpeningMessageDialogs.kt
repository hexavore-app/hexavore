package app.hexavore.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.hexavore.core.designsystem.theme.Spacing
import app.hexavore.domain.opening.OpeningAnswer
import app.hexavore.domain.opening.OpeningMessage
import app.hexavore.domain.opening.OpeningTip
import app.hexavore.domain.opening.STORE_LISTING

/**
 * Le message de cette ouverture, s'il y en a un ([D179][decisions]).
 *
 * **Une boîte, et non une carte dans la journée** : c'est ce que l'auteur a choisi. Elle
 * se referme d'un geste — toucher à côté vaut « J'ai compris » pour une astuce, et
 * « Plus tard » pour la demande d'avis : se débarrasser d'une boîte n'est jamais un refus
 * définitif.
 *
 * **L'accueil dit seulement quand il paraît et quand il s'efface**, au rythme de son
 * cycle de vie ; ce qui fait une ouverture se décide plus bas, dans le domaine.
 *
 * [decisions]: docs/11-decisions.md
 */
@Composable
internal fun OpeningMessageHost(onConfigureAi: () -> Unit, viewModel: OpeningMessageViewModel = hiltViewModel()) {
    LifecycleStartEffect(viewModel) {
        viewModel.onStarted()
        onStopOrDispose { viewModel.onStopped() }
    }

    val message by viewModel.message.collectAsStateWithLifecycle()
    val uriHandler = LocalUriHandler.current

    when (val shown = message) {
        null -> Unit
        is OpeningMessage.Tip -> TipDialog(
            tip = shown.tip,
            onAnswer = viewModel::onAnswer,
            onGetKey = {
                viewModel.onAnswer(OpeningAnswer.SEEN)
                onConfigureAi()
            },
        )

        OpeningMessage.Rating -> RatingDialog(
            onStar = {
                viewModel.onAnswer(OpeningAnswer.RATED)
                // Un telephone sans navigateur ni Play Store n'a rien a ouvrir : la boite
                // se referme quand meme, et la demande ne reviendra pas pour autant.
                runCatching { uriHandler.openUri(STORE_LISTING) }
            },
            onAnswer = viewModel::onAnswer,
        )
    }
}

/**
 * Une astuce : un titre, une phrase, et deux issues.
 *
 * **« Ne plus afficher d'astuces » éteint, il ne supprime pas** : l'interrupteur se
 * retrouve dans l'écran des notifications. L'astuce de la clé gratuite fait exception :
 * son bouton mène au guide de la clé, et l'autre la remet à plus tard.
 *
 * **Les deux issues d'une astuce sont empilées**, comme celles de la demande d'avis : côte
 * à côte, elles ne tiennent pas sur une ligne, et la boîte les séparait alors d'un grand
 * vide, la plus définitive en bas, isolée.
 */
@Composable
private fun TipDialog(tip: OpeningTip, onAnswer: (OpeningAnswer) -> Unit, onGetKey: () -> Unit) {
    val leadsToKey = tip == OpeningTip.FREE_KEY

    AlertDialog(
        onDismissRequest = { onAnswer(OpeningAnswer.SEEN) },
        title = { Text(stringResource(tip.titleRes)) },
        text = { Text(stringResource(tip.bodyRes)) },
        confirmButton = {
            if (leadsToKey) {
                TextButton(onClick = onGetKey) { Text(stringResource(R.string.opening_tip_get_key)) }
            } else {
                Column(horizontalAlignment = Alignment.End) {
                    TextButton(onClick = { onAnswer(OpeningAnswer.SEEN) }) {
                        Text(stringResource(R.string.opening_tip_done))
                    }
                    TextButton(onClick = { onAnswer(OpeningAnswer.NO_MORE_TIPS) }) {
                        Text(stringResource(R.string.opening_tip_stop))
                    }
                }
            }
        },
        dismissButton = if (leadsToKey) {
            {
                TextButton(onClick = { onAnswer(OpeningAnswer.SEEN) }) { Text(stringResource(R.string.opening_later)) }
            }
        } else {
            null
        },
    )
}

/**
 * La demande d'avis : **cinq étoiles, et la même fiche derrière chacune**.
 *
 * Une étoile touchée ouvre la fiche du Play Store, où la note se laisse vraiment ; celle
 * qu'on a touchée ici n'est pas transmise — rien ne sort de l'application. Trier selon
 * l'étoile, les bonnes vers la fiche et les mauvaises vers un courriel, est courant et
 * contraire aux consignes de Google sur les avis.
 *
 * Les trois autres réponses sont empilées, du plus doux au plus définitif : « Plus tard »
 * reviendra, « J'ai déjà noté » et « Ne plus demander » non.
 */
@Composable
private fun RatingDialog(onStar: () -> Unit, onAnswer: (OpeningAnswer) -> Unit) {
    AlertDialog(
        onDismissRequest = { onAnswer(OpeningAnswer.LATER) },
        title = { Text(stringResource(R.string.opening_rating_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                Text(stringResource(R.string.opening_rating_body))
                Stars(onStar)
                Text(
                    text = stringResource(R.string.opening_rating_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            Column(horizontalAlignment = Alignment.End) {
                TextButton(onClick = { onAnswer(OpeningAnswer.LATER) }) { Text(stringResource(R.string.opening_later)) }
                TextButton(onClick = { onAnswer(OpeningAnswer.ALREADY_RATED) }) {
                    Text(stringResource(R.string.opening_rating_already))
                }
                TextButton(onClick = { onAnswer(OpeningAnswer.NEVER) }) {
                    Text(stringResource(R.string.opening_rating_never))
                }
            }
        },
    )
}

/** Cinq étoiles de même poids : chacune dit son rang au lecteur d'écran, et toutes ouvrent la fiche. */
@Composable
private fun Stars(onStar: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
    ) {
        for (rank in 1..STAR_COUNT) {
            IconButton(onClick = onStar) {
                Icon(
                    imageVector = Icons.Filled.Star,
                    contentDescription = pluralStringResource(R.plurals.opening_rating_star, rank, rank, STAR_COUNT),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

private const val STAR_COUNT = 5

/**
 * Le titre et la phrase de chaque astuce.
 *
 * Deux tables sans branche `else`, comme les pastilles : une astuce ajoutée au domaine
 * **ne compile pas** tant que ses deux textes n'existent pas.
 */
private val OpeningTip.titleRes: Int
    get() = when (this) {
        OpeningTip.FREE_KEY -> R.string.opening_tip_free_key_title
        OpeningTip.QUARTER_SOURCES -> R.string.opening_tip_quarter_title
        OpeningTip.PHOTO_NOTE -> R.string.opening_tip_photo_note_title
        OpeningTip.DISH_MENU -> R.string.opening_tip_dish_menu_title
        OpeningTip.FAVOURITES -> R.string.opening_tip_favourites_title
        OpeningTip.CATCH_UP -> R.string.opening_tip_catch_up_title
        OpeningTip.SWIPE_DAYS -> R.string.opening_tip_swipe_title
        OpeningTip.ESTIMATES -> R.string.opening_tip_estimates_title
        OpeningTip.ACCENTS -> R.string.opening_tip_accents_title
        OpeningTip.BARCODE_OFFLINE -> R.string.opening_tip_barcode_title
        OpeningTip.WEIGH_IN -> R.string.opening_tip_weigh_in_title
        OpeningTip.REMINDERS -> R.string.opening_tip_reminders_title
        OpeningTip.BACKUP -> R.string.opening_tip_backup_title
    }

private val OpeningTip.bodyRes: Int
    get() = when (this) {
        OpeningTip.FREE_KEY -> R.string.opening_tip_free_key_body
        OpeningTip.QUARTER_SOURCES -> R.string.opening_tip_quarter_body
        OpeningTip.PHOTO_NOTE -> R.string.opening_tip_photo_note_body
        OpeningTip.DISH_MENU -> R.string.opening_tip_dish_menu_body
        OpeningTip.FAVOURITES -> R.string.opening_tip_favourites_body
        OpeningTip.CATCH_UP -> R.string.opening_tip_catch_up_body
        OpeningTip.SWIPE_DAYS -> R.string.opening_tip_swipe_body
        OpeningTip.ESTIMATES -> R.string.opening_tip_estimates_body
        OpeningTip.ACCENTS -> R.string.opening_tip_accents_body
        OpeningTip.BARCODE_OFFLINE -> R.string.opening_tip_barcode_body
        OpeningTip.WEIGH_IN -> R.string.opening_tip_weigh_in_body
        OpeningTip.REMINDERS -> R.string.opening_tip_reminders_body
        OpeningTip.BACKUP -> R.string.opening_tip_backup_body
    }
