package app.hexavore.core.media

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.core.content.ContextCompat as AndroidPermissions

/** Les deux façons d'obtenir une photo, et ce qu'il advient du fichier. */
@Immutable
class MealCapture(val shoot: () -> Unit, val pick: () -> Unit)

/**
 * Tout ce qui touche à l'appareil, en un seul endroit.
 *
 * ### Pourquoi `:core:media` et non `:feature:capture`
 *
 * Il y vivait, et il y était bien tant qu'un seul écran prenait des photos. L'écran de
 * validation en prend maintenant aussi — l'image d'un plat se remplace et s'ajoute, pas
 * seulement se supprime ([D156][decisions]) — et faire dépendre `:feature:entry` de
 * `:feature:capture` aurait ouvert la porte que [D149][decisions] tient fermée : un
 * module d'écran qui en importe un autre finit par devenir le carrefour de tous les
 * autres. Ce qui est partagé n'est d'ailleurs pas un écran : c'est le dialogue avec
 * l'appareil photo du système, le fournisseur de fichiers et la réduction JPEG — trois
 * choses qui n'appartiennent à aucune des deux fonctions.
 *
 * ### Ce que la fonction fait, et pourquoi ainsi
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
 * [decisions]: docs/11-decisions.md
 */
@Composable
fun rememberMealCapture(onJpeg: (ByteArray) -> Unit): MealCapture {
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
