package app.hexavore.feature.entry

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.hexavore.core.designsystem.component.CameraGlyph
import app.hexavore.core.designsystem.component.DishPhoto
import app.hexavore.core.designsystem.component.GalleryGlyph
import app.hexavore.core.designsystem.theme.Radius
import app.hexavore.core.designsystem.theme.Spacing
import app.hexavore.domain.diary.PhotoFile

/**
 * L'image du plat : on la regarde, on la remplace, on la retire, on en ajoute une.
 *
 * ### Ce qui a changé, et pourquoi
 *
 * Il n'y avait qu'une croix. On pouvait donc **supprimer** l'image qu'une analyse avait
 * apportée, et rien d'autre : ni la remplacer quand le modèle avait photographié de
 * travers, ni en mettre une sur un repas noté à la main ou scanné. Le plat était pourtant
 * déjà là, la photo aussi — il manquait les deux gestes qui en font un réglage et non une
 * fatalité ([D156][decisions]).
 *
 * ### Un seul endroit à apprendre
 *
 * **Appuyer sur l'image ouvre les choix.** Pas deux icônes en coin d'une vignette de
 * 132 dp — deux cibles de 48 dp y tiennent mal, et il faudrait quand même une feuille
 * derrière celle qui remplace. Pas un appui long non plus : rien ne dit qu'il existe,
 * et c'est exactement le reproche fait à l'écran de la clé d'API.
 *
 * **Sans image, un cadre en pointillés à la même place**, qui ouvre la même feuille
 * moins la ligne « Supprimer ». Le pointillé est la forme que le projet réserve depuis
 * [D25][decisions] à ce qui n'est pas renseigné, et il invite sans une phrase.
 *
 * ### Rien n'est effacé avant l'enregistrement
 *
 * Ni la suppression, ni le remplacement : l'image choisie va au **dépôt**, et c'est
 * l'enregistrement qui la range avec le plat. Annuler la modification doit rendre le
 * plat tel qu'il était, photo comprise.
 *
 * [decisions]: docs/11-decisions.md
 */
@Composable
internal fun DraftPhoto(
    photo: PhotoFile?,
    /**
     * Combien de fois l'image a été choisie à la main depuis l'ouverture de l'écran.
     *
     * **Le dépôt n'a qu'un emplacement**, donc remplacer une image n'en change pas le
     * chemin, et [DishPhoto] mémorise son décodage par chemin — à juste titre, sans quoi
     * une vignette se redécoderait à chaque recomposition. Sans ce compteur, choisir une
     * nouvelle photo laissait la précédente à l'écran jusqu'à ce qu'on quitte l'écran.
     */
    revision: Int,
    onShoot: () -> Unit,
    onPick: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var choosing by remember { mutableStateOf(false) }

    if (photo == null) {
        AddFrame(modifier) { choosing = true }
    } else {
        Thumbnail(photo, revision, modifier) { choosing = true }
    }

    if (choosing) {
        PhotoChoices(
            removable = photo != null,
            onDismiss = { choosing = false },
            onShoot = onShoot,
            onPick = onPick,
            onRemove = onRemove,
        )
    }
}

/**
 * La photo, **petite et carrée**.
 *
 * Ce qu'on vient faire ici est corriger des quantités ; la photo est un repère — « c'est
 * bien ce repas-là » — et non un objet à examiner. Elle occupe donc un coin plutôt que la
 * largeur, et le formulaire reste le sujet de l'écran.
 */
@Composable
private fun Thumbnail(photo: PhotoFile, revision: Int, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .size(PhotoSize)
            .clip(RoundedCornerShape(Radius.card))
            // Le role est annonce : un lecteur d'ecran dirait « image » d'un element
            // qu'on peut activer, et l'action resterait invisible.
            .clickable(role = Role.Button, onClick = onClick),
    ) {
        DishPhoto(
            path = photo.path,
            revision = revision,
            contentDescription = stringResource(R.string.entry_photo_a11y),
            modifier = Modifier.size(PhotoSize),
        )
    }
}

/** Le cadre vide : des pointillés, un appareil photo, et ce qu'il propose. */
@Composable
private fun AddFrame(modifier: Modifier, onClick: () -> Unit) {
    val edge = MaterialTheme.colorScheme.outline

    Column(
        modifier = modifier
            .size(PhotoSize)
            .dashedFrame(edge)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(Spacing.sm),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CameraGlyph(contentDescription = "")
        Text(
            text = stringResource(R.string.entry_photo_add),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = Spacing.xs),
        )
    }
}

/**
 * Les trois gestes, dans une feuille du bas.
 *
 * **« Supprimer » n'est proposé que s'il y a quelque chose à supprimer.** Une ligne
 * grisée sur un cadre vide demanderait de comprendre pourquoi elle est là.
 *
 * Chaque choix referme la feuille avant d'agir : l'appareil photo du système ouvre une
 * activité par-dessus, et revenir sur une feuille encore posée donnerait l'impression
 * que rien n'a eu lieu.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PhotoChoices(
    removable: Boolean,
    onDismiss: () -> Unit,
    onShoot: () -> Unit,
    onPick: () -> Unit,
    onRemove: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(bottom = Spacing.xl),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            Choice(
                label = stringResource(R.string.entry_photo_shoot),
                glyph = { CameraGlyph(contentDescription = "") },
                onClick = {
                    onDismiss()
                    onShoot()
                },
            )
            Choice(
                label = stringResource(R.string.entry_photo_pick),
                glyph = { GalleryGlyph(contentDescription = "") },
                onClick = {
                    onDismiss()
                    onPick()
                },
            )
            if (removable) {
                Choice(
                    label = stringResource(R.string.entry_photo_remove),
                    icon = Icons.Filled.Delete,
                    destructive = true,
                    onClick = {
                        onDismiss()
                        onRemove()
                    },
                )
            }
        }
    }
}

/** Une ligne de la feuille : son signe, son mot, et toute la largeur pour le doigt. */
@Composable
private fun Choice(
    label: String,
    onClick: () -> Unit,
    icon: ImageVector? = null,
    glyph: (@Composable () -> Unit)? = null,
    destructive: Boolean = false,
) {
    // Le rouge de la suppression accompagne un mot qui dit deja ce qu'il fait : la
    // couleur ne travaille jamais seule dans ce projet.
    val ink = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = Spacing.screenMargin, vertical = Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        if (icon != null) {
            Icon(imageVector = icon, contentDescription = null, tint = ink)
        } else {
            glyph?.invoke()
        }
        Text(text = label, style = MaterialTheme.typography.bodyLarge, color = ink)
    }
}

/**
 * Le contour en pointillés du cadre vide.
 *
 * Le même tracé que le cadre de l'écran d'analyse, et pour la même raison : c'est la
 * forme que le projet réserve à ce qui n'est pas renseigné. Il est redessiné ici plutôt
 * que partagé — quatre lignes de `drawBehind` contre un composant de plus dans le design
 * system, dont la seule variation serait le rayon.
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

/** Assez pour reconnaître le repas, assez peu pour que le formulaire reste le sujet. */
private val PhotoSize: Dp = 132.dp

private val DashedStroke: Dp = 1.5.dp
private val DashOn: Dp = 6.dp
private val DashOff: Dp = 4.dp
