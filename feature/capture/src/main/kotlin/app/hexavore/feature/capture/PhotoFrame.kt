package app.hexavore.feature.capture

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.hexavore.core.designsystem.component.CameraGlyph
import app.hexavore.core.designsystem.component.GalleryGlyph
import app.hexavore.core.designsystem.theme.Radius
import app.hexavore.core.designsystem.theme.Spacing
import app.hexavore.core.media.MealCapture

/**
 * Le cadre du haut : l'image, et les deux façons d'en obtenir une.
 *
 * **Les deux boutons vivent dans le cadre**, ronds et côte à côte, plutôt qu'en une
 * rangée de boutons sous lui ([D120][decisions]). Ce qu'on regarde et ce qui le change
 * sont alors au même endroit : le cadre vide invite, le cadre plein montre, et les
 * mêmes boutons remplacent l'image sans qu'on ait à chercher ailleurs.
 *
 * **Vide, le cadre est en pointillés** — la forme que le projet réserve à ce qui n'est
 * pas encore renseigné ([D25][decisions]) — et il dit ce qu'on attend de lui : une
 * assiette entière, vue de dessus. C'est le conseil de cadrage que [docs/02][parcours]
 * voulait en surimpression de l'aperçu, et il se lit mieux **avant** d'appuyer.
 *
 * [parcours]: docs/02-parcours-et-ecrans.md
 * [decisions]: docs/11-decisions.md
 */
@Composable
internal fun PhotoFrame(
    photo: ReducedPhoto?,
    capture: MealCapture,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(Radius.card)
    val edge = MaterialTheme.colorScheme.outline

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            // **Pointille tant qu'il n'y a pas d'image**, plein des qu'il y en a une :
            // c'est la forme que le projet reserve depuis D25 a ce qui n'est pas encore
            // renseigne, et elle dit « il manque quelque chose ici » sans une phrase.
            .then(if (photo == null) Modifier.dashedFrame(edge) else Modifier.border(FrameBorder, edge, shape)),
    ) {
        if (photo == null) {
            Text(
                text = stringResource(R.string.analyse_photo_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.align(Alignment.Center).padding(Spacing.xl),
            )
        } else {
            Picture(photo)
            // La croix est en haut a droite, loin des deux boutons : retirer une image
            // et en reprendre une sont deux gestes, et ils ne se visent pas au meme
            // endroit.
            FilledTonalIconButton(
                onClick = onRemove,
                modifier = Modifier.align(Alignment.TopEnd).padding(Spacing.sm),
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = stringResource(R.string.analyse_photo_remove),
                )
            }
        }

        Row(
            modifier = Modifier.align(Alignment.BottomCenter).padding(Spacing.lg),
            horizontalArrangement = Arrangement.spacedBy(Spacing.lg),
        ) {
            RoundAction(onClick = capture.shoot) {
                CameraGlyph(contentDescription = stringResource(R.string.analyse_shoot))
            }
            RoundAction(onClick = capture.pick) {
                GalleryGlyph(contentDescription = stringResource(R.string.analyse_pick))
            }
        }
    }
}

/**
 * Ce qu'on vient de prendre.
 *
 * `Crop` et non `Fit` : le cadre a une forme, et une photo qui la remplit se juge
 * mieux qu'une photo posée sur des bandes vides. Ce qu'on juge ici est le cadrage —
 * l'assiette est-elle entière — et le rognage d'un bord ne le change pas.
 */
@Composable
private fun Picture(photo: ReducedPhoto) {
    val bitmap = remember(photo) { BitmapFactory.decodeByteArray(photo.jpeg, 0, photo.jpeg.size) } ?: return

    Image(
        bitmap = bitmap.asImageBitmap(),
        contentDescription = stringResource(R.string.analyse_photo_a11y),
        modifier = Modifier.fillMaxSize(),
        contentScale = ContentScale.Crop,
    )
}

/**
 * Un bouton rond, posé sur ce qu'il change.
 *
 * Plein et non tonal : il se pose sur une photo dont on ne maîtrise ni la couleur ni
 * la luminosité, et un bouton tonal y disparaîtrait une fois sur deux.
 */
@Composable
private fun RoundAction(onClick: () -> Unit, content: @Composable () -> Unit) {
    FilledIconButton(
        onClick = onClick,
        modifier = Modifier.size(RoundActionSize),
        colors = IconButtonDefaults.filledIconButtonColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ),
        content = { content() },
    )
}

/** Plus large que la cible minimale : ils se visent d'un pouce, sur une photo. */
private val RoundActionSize: Dp = 56.dp

private val FrameBorder: Dp = 1.dp

/**
 * Le contour en pointillés d'un cadre encore vide.
 *
 * Le même tracé que celui d'une valeur estimée ([DraftTextField][app.hexavore.core
 * .designsystem.component.DraftTextField]) : un trait de 1,5 dp, plus épais que la
 * bordure pleine, parce qu'un pointillé fin se lit comme un défaut de rendu et qu'il
 * doit se voir pour signaler quelque chose.
 */
private fun Modifier.dashedFrame(color: Color): Modifier = drawBehind {
    val stroke = DashedStroke.toPx()
    val inset = stroke / 2f
    drawRoundRect(
        color = color,
        topLeft = Offset(inset, inset),
        size = Size(size.width - stroke, size.height - stroke),
        cornerRadius = CornerRadius(Radius.card.toPx()),
        style = Stroke(
            width = stroke,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(DashOn.toPx(), DashOff.toPx())),
        ),
    )
}

private val DashedStroke: Dp = 1.5.dp
private val DashOn: Dp = 6.dp
private val DashOff: Dp = 4.dp
