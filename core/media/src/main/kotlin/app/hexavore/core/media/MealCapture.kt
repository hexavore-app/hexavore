package app.hexavore.core.media

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
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
 * **Un refus définitif se signale** ([D162][decisions]). Refusée deux fois, la permission
 * ne se redemande plus : Android rend « refusé » sans rien afficher, et « Prendre une
 * photo » ne faisait plus rien du tout. [onCameraRefused] est appelé quand Android ne
 * rouvrira plus sa boîte — pas au premier refus, qu'on vient de donner en connaissance de
 * cause. L'écran dit alors où se rend l'autorisation, par la boîte du design system
 * prévue pour cela, `CameraRefusedDialog`.
 *
 * [parcours]: docs/02-parcours-et-ecrans.md
 * [ia]: docs/05-ia.md
 * [decisions]: docs/11-decisions.md
 */
@Composable
fun rememberMealCapture(onJpeg: (ByteArray) -> Unit, onCameraRefused: () -> Unit): MealCapture {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val refused by rememberUpdatedState(onCameraRefused)

    // L'adresse ou l'appareil photo du systeme ecrira. Nulle des que le fichier est
    // lu : c'est elle qui dit s'il y a quelque chose a supprimer.
    //
    // **Sauvee, et pas seulement retenue** (D163). Pendant la prise de vue, Hexavore
    // est en arriere-plan, et Android peut le tuer -- c'est courant sur un telephone a
    // court de memoire, l'appareil photo en consomme beaucoup. Le resultat revenait a
    // une activite recreee dont l'adresse etait nulle : la photo prise et validee
    // etait ignoree sans un mot.
    var taken by rememberSaveable { mutableStateOf<Uri?>(null) }

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
        when {
            granted -> taken?.let(takePicture::launch)
            // Android ne rouvrira plus sa boite : sans ce mot, l'appui ne produisait
            // plus rien (D162).
            !context.mayAskCameraAgain() -> refused()
        }
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

/**
 * Android rouvrira-t-il sa boîte si l'on redemande ?
 *
 * Lu **après** un refus : c'est le seul moment où la réponse d'Android ne confond pas
 * « jamais demandée » et « refusée pour de bon ». Sans activité à interroger, la réponse
 * prudente est non : mieux vaut une explication de trop qu'un bouton muet.
 */
private fun Context.mayAskCameraAgain(): Boolean =
    findActivity()?.let { ActivityCompat.shouldShowRequestPermissionRationale(it, Manifest.permission.CAMERA) }
        ?: false

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
