package app.hexavore.core.designsystem.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import app.hexavore.core.designsystem.theme.NeonTheme

/**
 * Une bulle qui commente un point de la figure, et sa pointe qui le désigne.
 *
 * **Légère, donc sans voile derrière elle.** Une modale assombrit ce qu'elle recouvre
 * pour annoncer qu'un appui à côté la referme ; celle-ci ne le fait pas, et c'est ce
 * qui lui permet d'apparaître et de changer de macro sans faire clignoter l'écran
 * entier. En contrepartie, c'est l'appelant qui doit refermer sur un appui à côté :
 * rien ici ne capte le doigt ([D123][decisions]).
 *
 * **Elle se pose du côté opposé à ce qu'elle explique** ([bubbleSpot]) : recouvrir le
 * quartier qu'on vient de mettre en avant serait l'annuler.
 *
 * **La pointe est tracée par le parent, pas par la bulle.** Sa position le long du bord
 * n'est connue qu'une fois la bulle mesurée et placée, alors que le fond de la bulle,
 * lui, doit être décrit avant. Faire redescendre cette position dans une forme
 * obligerait à recomposer l'enfant pour un nombre que la mise en page vient de calculer
 * — un aller-retour visible à l'image près. Le parent, lui, dessine après.
 *
 * @param anchor le point visé, dans le repère de cette bulle.
 * @param centre le centre de la figure : c'est lui qui sépare le haut du bas.
 *
 * [decisions]: docs/11-decisions.md
 */
@Composable
fun AnchoredBubble(anchor: Offset, centre: Offset, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val fond = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = BUBBLE_ALPHA)
    val densite = LocalDensity.current
    val marge = with(densite) { BubbleMargin.roundToPx() }
    val retrait = with(densite) { (BubbleCorner + TailWidth / 2).roundToPx() }
    val pointe = with(densite) { Offset(TailWidth.toPx(), TailHeight.toPx()) }
    val maxLargeur = with(densite) { BubbleMaxWidth.roundToPx() }

    // Ecrit a la mise en page, lu au trace. L'ordre des deux est garanti, et c'est ce
    // qui permet a la pointe de connaitre une place que la bulle vient seulement de
    // recevoir, sans passer par une recomposition.
    var place by remember { mutableStateOf<PlacedBubble?>(null) }

    val apparition = remember { Animatable(0f) }
    // Lue en composition : le bloc d'effet n'est pas composable, et le theme s'y lit.
    val duree = NeonTheme.motion.sheetEnterMillis
    LaunchedEffect(Unit) { apparition.animateTo(1f, tween(duree)) }

    Layout(
        modifier = modifier
            .graphicsLayer { alpha = apparition.value }
            .drawBehind { place?.let { drawTail(it, fond, pointe) } },
        content = {
            Box(
                Modifier
                    .widthIn(max = BubbleMaxWidth)
                    .clip(RoundedCornerShape(BubbleCorner))
                    .background(fond),
            ) { content() }
        },
    ) { measurables, constraints ->
        val mesure = measurables.first().measure(
            constraints.copy(minWidth = 0, minHeight = 0, maxWidth = minOf(constraints.maxWidth, maxLargeur)),
        )
        val zone = IntSize(constraints.maxWidth, constraints.maxHeight)
        val spot = bubbleSpot(anchor, centre, IntSize(mesure.width, mesure.height), zone, marge, retrait)

        layout(constraints.maxWidth, constraints.maxHeight) {
            place = PlacedBubble(spot, IntSize(mesure.width, mesure.height))
            mesure.place(spot.offset)
        }
    }
}

/** Où la bulle a fini par se poser, et de quelle taille. Ce qu'il faut pour lui tracer une pointe. */
private data class PlacedBubble(val spot: BubbleSpot, val size: IntSize)

/**
 * La pointe, posée contre le bord de la bulle et non dessus.
 *
 * Contre, parce que le fond est translucide : deux surfaces superposées feraient une
 * tache plus dense à l'endroit exact où la pointe rejoint le corps, et le raccord se
 * verrait au lieu de disparaître.
 */
private fun DrawScope.drawTail(place: PlacedBubble, colour: Color, pointe: Offset) {
    val base = (place.spot.offset.x + place.spot.tailX).toFloat()
    val bord = if (place.spot.tailOnTop) {
        place.spot.offset.y.toFloat()
    } else {
        (place.spot.offset.y + place.size.height).toFloat()
    }
    val sommet = if (place.spot.tailOnTop) bord - pointe.y else bord + pointe.y

    drawPath(
        path = Path().apply {
            moveTo(base - pointe.x / 2f, bord)
            lineTo(base + pointe.x / 2f, bord)
            lineTo(base, sommet)
            close()
        },
        color = colour,
    )
}

/**
 * Ce que la bulle laisse voir du fond.
 *
 * Assez transparente pour qu'on sache ce qu'il y a dessous — c'est ce qui la distingue
 * d'un écran —, assez opaque pour qu'un chiffre de trois points reste lisible sur un
 * quartier allumé. En dessous, les deux se disputent le même pixel.
 */
private const val BUBBLE_ALPHA = 0.95f

/** Entre le centre de la figure et le corps de la bulle. Plus grand que la pointe, qui vient s'y loger. */
private val BubbleMargin: Dp = 14.dp

private val BubbleCorner: Dp = 16.dp

private val TailWidth: Dp = 18.dp

private val TailHeight: Dp = 9.dp

/** La bulle ne prend pas toute la largeur : une bulle pleine largeur est un bandeau. */
private val BubbleMaxWidth: Dp = 260.dp
