package app.hexavore.feature.home.tour

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
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
import androidx.compose.ui.geometry.CornerRadius
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
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import app.hexavore.core.designsystem.theme.Spacing
import app.hexavore.feature.home.R
import kotlin.math.roundToInt

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
 * ### La bulle se pose contre sa cible
 *
 * Elle ne reste plus en haut de l'écran : elle se glisse **juste au-dessus ou juste en
 * dessous** de ce qu'elle désigne, du côté où il y a de la place ([D143][decisions]).
 * Une bulle collée au plafond pendant qu'un bouton s'éclaire en bas laisse à l'œil le
 * soin de faire le lien — et cet œil-là découvre l'application.
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

    BoxWithConstraints(modifier = Modifier.fillMaxSize().blockingTaps()) {
        val hauteur = with(LocalDensity.current) { maxHeight.toPx() }
        val ecart = with(LocalDensity.current) { GAP.toPx() }

        Veil(cible)
        Bubble(
            step = step,
            placement = cible.placementIn(hauteur, ecart),
            keyless = keyless,
            onNext = onNext,
            onConfigureAi = onConfigureAi,
            onFinish = onFinish,
        )
    }
}

/** Où la bulle se pose par rapport à sa cible, et de combien elle s'en écarte. */
private data class BubblePlacement(val below: Boolean, val y: Float)

/**
 * Le côté où il reste de la place.
 *
 * **Sous la cible quand elle est dans la moitié haute**, au-dessus sinon : c'est la
 * règle la plus simple qui ne fasse jamais sortir la bulle de l'écran, et elle suffit —
 * une cible au milieu a de la place des deux côtés.
 *
 * `null` quand il n'y a pas de cible : la bulle se met alors au centre, ce qui est ce
 * qu'on veut d'une étape qui ne désigne rien.
 */
private fun Rect?.placementIn(height: Float, gap: Float): BubblePlacement? {
    val rect = this ?: return null
    val below = rect.center.y < height / 2
    return BubblePlacement(
        below = below,
        y = if (below) rect.bottom + gap else height - rect.top + gap,
    )
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
                cornerRadius = CornerRadius(rayon, rayon),
                blendMode = BlendMode.Clear,
            )
        }
    }
}

/** Ce que l'étape dit, et ce qu'elle propose de faire. */
@Composable
private fun Bubble(
    step: TourStep,
    placement: BubblePlacement?,
    keyless: Boolean,
    onNext: () -> Unit,
    onConfigureAi: () -> Unit,
    onFinish: () -> Unit,
) {
    val alignement = when {
        placement == null -> Alignment.Center
        placement.below -> Alignment.TopCenter
        else -> Alignment.BottomCenter
    }

    Box(
        // Les marges systeme : le voile couvre l'ecran entier, barre d'etat comprise,
        // et une bulle posee a son bord passerait sous l'heure.
        modifier = Modifier.fillMaxSize().systemBarsPadding().padding(Spacing.md),
        contentAlignment = alignement,
    ) {
        Card(
            modifier = Modifier.offset { placement.toOffset() },
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest),
        ) {
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
 * Le décalage qui colle la bulle à sa cible.
 *
 * Il part du bord de la boîte : vers le bas quand la bulle est alignée en haut, vers le
 * haut quand elle est alignée en bas. Le signe suit l'alignement, et c'est pourquoi les
 * deux se décident au même endroit.
 */
private fun BubblePlacement?.toOffset(): IntOffset = when {
    this == null -> IntOffset.Zero
    below -> IntOffset(0, y.roundToInt())
    else -> IntOffset(0, -y.roundToInt())
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

private const val VEIL_ALPHA = 0.82f

/** L'air entre la cible éclairée et le bord de la bulle. */
private val GAP = 12.dp

private val HOLE_MARGIN = 8.dp
private val HOLE_CORNER = 16.dp
