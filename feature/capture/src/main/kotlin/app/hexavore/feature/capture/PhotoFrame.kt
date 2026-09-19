package app.hexavore.feature.capture

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.hexavore.core.designsystem.component.CameraGlyph
import app.hexavore.core.designsystem.component.GalleryGlyph
import app.hexavore.core.designsystem.theme.Radius
import app.hexavore.core.designsystem.theme.Spacing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.core.content.ContextCompat as AndroidPermissions

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

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .border(FrameBorder, MaterialTheme.colorScheme.outline, shape),
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

/** Les deux façons d'obtenir une photo, et ce qu'il advient du fichier. */
@Immutable
internal class MealCapture(val shoot: () -> Unit, val pick: () -> Unit)

/**
 * Tout ce qui touche à l'appareil, en un seul endroit.
 *
 * **L'appareil photo du système, pas un aperçu à nous.** [docs/02][parcours] décrit un
 * aperçu CameraX ; il demanderait une seconde implémentation — la première sert le
 * scan, qui analyse un flux en continu — pour un écran dont le seul travail est de
 * remettre un JPEG. L'appareil du système apporte sa mise au point, son flash et son
 * zoom, et il écrit directement dans notre cache.
 *
 * **La permission est quand même demandée.** Elle n'est pas nécessaire pour déléguer
 * une prise de vue — sauf quand l'application déclare `CAMERA` dans son manifeste, ce
 * que fait `:integration:scanner` pour le scan. Le système l'exige alors avant de
 * lancer l'appareil photo, et sans ce chemin le déclencheur échouerait sans rien dire.
 *
 * **Le fichier meurt avec sa lecture**, dans un `finally` : succès, échec ou annulation
 * ([docs/05][ia] § Confidentialité). Une image choisie dans la galerie, elle, n'est
 * jamais supprimée — elle ne nous appartient pas.
 *
 * [parcours]: docs/02-parcours-et-ecrans.md
 * [ia]: docs/05-ia.md
 */
@Composable
internal fun rememberMealCapture(onJpeg: (ByteArray) -> Unit): MealCapture {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // L'adresse ou l'appareil photo du systeme ecrira. Nulle des que le fichier est
    // lu : c'est elle qui dit s'il y a quelque chose a supprimer.
    var taken by remember { mutableStateOf<Uri?>(null) }

    val reduce: (Uri) -> Unit = { uri ->
        scope.launch {
            val jpeg = withContext(Dispatchers.IO) {
                try {
                    reduceToJpeg(context, uri)
                } finally {
                    taken?.let { context.contentResolver.delete(it, null, null) }
                    taken = null
                }
            }
            jpeg?.let(onJpeg)
        }
    }

    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { captured ->
        taken?.let { if (captured) reduce(it) }
    }
    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { picked ->
        picked?.let(reduce)
    }
    val askCamera = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) taken?.let(takePicture::launch)
    }

    return remember(context) {
        MealCapture(
            shoot = {
                val uri = photoUri(context, newPhotoFile(context))
                taken = uri
                if (context.hasCameraPermission()) {
                    takePicture.launch(uri)
                } else {
                    askCamera.launch(Manifest.permission.CAMERA)
                }
            },
            pick = { pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
        )
    }
}

private fun Context.hasCameraPermission(): Boolean =
    AndroidPermissions.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
