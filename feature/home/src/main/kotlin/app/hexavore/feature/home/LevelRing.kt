package app.hexavore.feature.home

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.hexavore.core.designsystem.theme.NeonTheme
import app.hexavore.domain.progress.Progress

/**
 * Le niveau, en anneau, dans la barre du haut.
 *
 * ### Ce qu'il remplace
 *
 * Un bandeau pleine largeur posé au-dessus de l'hexagone ([D133][decisions]). Il disait
 * la même chose en dix fois plus de place, et il la disait **partout** — y compris en
 * remontant dans l'historique, où un niveau n'a aucun sens : la progression est une
 * chose du présent, et l'afficher au-dessus d'une journée d'il y a trois semaines
 * laissait croire qu'il en parlait ([D135][decisions]).
 *
 * Il ne paraît donc que sur aujourd'hui, et il tient dans la ligne du titre, à côté des
 * deux portes qui y étaient déjà.
 *
 * ### Pourquoi un anneau et pas une barre
 *
 * Une barre horizontale a besoin de largeur pour dire quelque chose ; un anneau dit la
 * même fraction dans un carré de 44 dp, et son centre est libre — c'est là que va le
 * numéro, qui devient ainsi ce qu'on lit d'abord. C'est exactement la forme que le
 * calendrier emploie déjà pour ses pastilles, à la même taille.
 *
 * **En `primary`, et non dans une teinte de macro.** Les six teintes désignent les six
 * compteurs et rien d'autre ; emprunter celle des calories pour un niveau ferait croire
 * à un septième compteur.
 *
 * [decisions]: docs/11-decisions.md
 */
@Composable
internal fun LevelRing(progress: Progress, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val days = progress.streaks.logging
    val label = stringResource(
        R.string.home_level_a11y,
        progress.level.number,
        pluralStringResource(R.plurals.home_streak_days, days, days),
    )

    // La jauge glisse jusqu'a sa nouvelle valeur plutot que d'y sauter : c'est le seul
    // endroit de l'ecran ou un progres se voit *arriver*, et un saut le ferait manquer.
    val fraction by animateFloatAsState(
        targetValue = progress.level.fraction.coerceIn(0f, 1f),
        animationSpec = tween(NeonTheme.motion.gaugeValueMillis),
        label = "niveau",
    )
    val track = MaterialTheme.colorScheme.outline
    val ink = MaterialTheme.colorScheme.primary

    Box(
        modifier = modifier
            .size(RingSize)
            .clip(CircleShape)
            .clickable(onClick = onOpen)
            // `semantics` et non `clearAndSetSemantics` : celui-ci efface aussi
            // **l'action** que `clickable` vient de poser, et le lecteur d'ecran se
            // retrouvait devant un bouton qu'il annonce sans pouvoir l'activer. La
            // fusion suffit a ce qu'on voulait -- une seule phrase a la place du
            // chiffre nu du centre.
            .semantics(mergeDescendants = true) { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = Thickness.toPx()
            val inset = stroke / 2f
            val box = Size(size.width - stroke, size.height - stroke)

            drawArc(
                color = track,
                startAngle = 0f,
                sweepAngle = FULL_TURN,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = box,
                style = Stroke(width = stroke),
            )
            drawArc(
                color = ink,
                // Depuis le haut, dans le sens des aiguilles : c'est le sens de
                // lecture d'une jauge ronde, et celui des pastilles du calendrier.
                startAngle = TOP,
                sweepAngle = FULL_TURN * fraction,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = box,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
        }
        Text(
            text = progress.level.number.toString(),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/** La taille des pastilles du calendrier : la barre du haut porte déjà des cibles de ce gabarit. */
private val RingSize: Dp = 44.dp

private val Thickness: Dp = 4.dp

private const val FULL_TURN = 360f

/** Midi sur un cadran : `drawArc` compte depuis trois heures. */
private const val TOP = -90f
