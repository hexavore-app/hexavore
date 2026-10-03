package app.hexavore.feature.home.tour

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.hexavore.core.designsystem.theme.Spacing
import app.hexavore.feature.home.R

/**
 * Le tour guidé, posé par-dessus l'accueil réel.
 *
 * ### Un voile percé, et non une suite d'écrans
 *
 * Ce que montre le tour est l'application elle-même : le voile assombrit tout **sauf**
 * ce dont la bulle parle ([D141][decisions]). Un diaporama d'images aurait expliqué une
 * application qui n'existe pas encore à l'écran, et aurait vieilli à la première
 * refonte.
 *
 * Le trou se découpe en `BlendMode.Clear`, ce qui demande une couche hors écran : sans
 * elle, l'effacement mordrait sur ce qui est derrière, c'est-à-dire sur l'accueil.
 *
 * ### La bulle fuit ce qu'elle désigne
 *
 * Elle se pose en haut quand la cible est en bas, et en bas quand la cible est en haut.
 * C'est la seule règle, et elle suffit : les quatre cibles du tour sont soit dans la
 * barre du bas, soit dans le bloc du haut.
 *
 * ### Rien ne passe au travers
 *
 * Le voile prend tous les gestes. Pendant un tour, les seuls boutons qui répondent sont
 * ceux de la bulle — on ne veut pas que quelqu'un ouvre la recherche au milieu d'une
 * phrase qui parle d'autre chose, et retrouve l'accueil sans savoir ce qui l'a quitté.
 *
 * [decisions]: docs/11-decisions.md
 */
@Composable
internal fun GuidedTour(
    step: TourStep,
    anchors: TourAnchors,
    keyless: Boolean,
    onNext: () -> Unit,
    onConfigureAi: () -> Unit,
    onFinish: () -> Unit,
) {
    val cible = step.target?.let { anchors[it] }

    Box(modifier = Modifier.fillMaxSize().blockingTaps()) {
        Veil(cible)
        Bubble(
            step = step,
            below = cible != null && cible.center.y < MIDDLE_Y,
            keyless = keyless,
            onNext = onNext,
            onConfigureAi = onConfigureAi,
            onFinish = onFinish,
        )
    }
}

/** Le voile, et le trou de lumière sur ce dont on parle. */
@Composable
private fun Veil(target: Rect?) {
    val scrim = MaterialTheme.colorScheme.scrim
    val marge = with(LocalDensity.current) { HOLE_MARGIN.toPx() }
    val rayon = with(LocalDensity.current) { HOLE_CORNER.toPx() }

    Canvas(
        modifier = Modifier
            .fillMaxSize()
            // Hors ecran : `Clear` efface ce qui est dans *cette* couche, et sans elle
            // il effacerait l'accueil lui-meme.
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen },
    ) {
        drawRect(color = scrim, alpha = VEIL_ALPHA)

        target?.let {
            drawRoundRect(
                color = scrim,
                topLeft = Offset(it.left - marge, it.top - marge),
                size = Size(it.width + marge * 2, it.height + marge * 2),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(rayon, rayon),
                blendMode = BlendMode.Clear,
            )
        }
    }
}

/** Ce que l'étape dit, et ce qu'elle propose de faire. */
@Composable
private fun Bubble(
    step: TourStep,
    below: Boolean,
    keyless: Boolean,
    onNext: () -> Unit,
    onConfigureAi: () -> Unit,
    onFinish: () -> Unit,
) {
    Box(
        // Les marges systeme : le voile couvre l'ecran entier, barre d'etat comprise,
        // et une bulle posee a son bord passerait sous l'heure.
        modifier = Modifier.fillMaxSize().systemBarsPadding().padding(Spacing.md),
        contentAlignment = if (below) Alignment.BottomCenter else Alignment.TopCenter,
    ) {
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest)) {
            Column(
                modifier = Modifier.padding(Spacing.lg),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                Text(
                    text = stringResource(step.title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = stringResource(step.body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Actions(step, keyless, onNext, onConfigureAi, onFinish)
            }
        }
    }
}

/**
 * Les boutons, qui ne sont pas les mêmes partout.
 *
 * **L'étape de l'IA a deux issues et le dit.** « Plus tard » n'est pas caché dans un
 * coin : un refus qu'on doit chercher n'est pas un choix, et le tour montre ensuite ce
 * que ce refus coûte.
 */
@Composable
private fun Actions(
    step: TourStep,
    keyless: Boolean,
    onNext: () -> Unit,
    onConfigureAi: () -> Unit,
    onFinish: () -> Unit,
) {
    val derniere = step.next(keyless) == null

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm, Alignment.End),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (step == TourStep.AI) {
            TextButton(onClick = onNext) { Text(stringResource(R.string.tour_later)) }
            Button(onClick = onConfigureAi) { Text(stringResource(R.string.tour_configure)) }
        } else {
            if (!derniere) {
                TextButton(onClick = onFinish) { Text(stringResource(R.string.tour_skip)) }
            }
            Button(onClick = if (derniere) onFinish else onNext) {
                Text(stringResource(if (derniere) R.string.tour_done else R.string.tour_next))
            }
        }
    }
}

/**
 * Le voile avale les gestes : pendant un tour, seule la bulle répond.
 *
 * `pointerInput` et non `clickable` : on ne veut ni l'ondulation, ni le rôle de bouton
 * annoncé au lecteur d'écran. Ce n'est pas un bouton, c'est un mur.
 */
private fun Modifier.blockingTaps(): Modifier = pointerInput(Unit) {
    awaitPointerEventScope {
        while (true) {
            awaitPointerEvent().changes.forEach { it.consume() }
        }
    }
}

/** Au-dessus de ce partage, une cible est « en haut » et la bulle descend. */
private const val MIDDLE_Y = 1200f

private const val VEIL_ALPHA = 0.82f
private val HOLE_MARGIN = 8.dp
private val HOLE_CORNER = 16.dp
