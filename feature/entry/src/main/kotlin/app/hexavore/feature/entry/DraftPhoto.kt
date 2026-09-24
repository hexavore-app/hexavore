package app.hexavore.feature.entry

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.hexavore.core.designsystem.component.DishPhoto
import app.hexavore.core.designsystem.theme.Radius
import app.hexavore.core.designsystem.theme.Spacing
import app.hexavore.domain.diary.PhotoFile

/**
 * La photo du plat, et la croix qui la retire.
 *
 * **Petite, et carrée.** Ce qu'on vient faire ici est corriger des quantités ; la photo
 * est un repère — « c'est bien ce repas-là » — et non un objet à examiner. Elle occupe
 * donc un coin plutôt que la largeur, et le formulaire reste le sujet de l'écran.
 *
 * **La croix en haut à droite**, comme sur le cadre de l'écran d'analyse : c'est déjà le
 * geste qui retire une image dans ce projet, et lui donner une seconde forme ici
 * obligerait à l'apprendre deux fois.
 *
 * **Elle n'efface rien tout de suite.** Le fichier part à l'enregistrement, pas au
 * toucher : annuler la modification doit rendre le plat tel qu'il était.
 */
@Composable
internal fun DraftPhoto(photo: PhotoFile, onRemove: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier = modifier.size(PhotoSize)) {
        DishPhoto(
            path = photo.path,
            contentDescription = stringResource(R.string.entry_photo_a11y),
            modifier = Modifier
                .size(PhotoSize)
                .clip(RoundedCornerShape(Radius.card)),
        )
        FilledTonalIconButton(
            onClick = onRemove,
            modifier = Modifier.align(Alignment.TopEnd).padding(Spacing.xs),
        ) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = stringResource(R.string.entry_photo_remove),
            )
        }
    }
}

/** Assez pour reconnaître le repas, assez peu pour que le formulaire reste le sujet. */
private val PhotoSize: Dp = 132.dp
