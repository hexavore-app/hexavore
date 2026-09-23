package app.hexavore.integration.scanner

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.hexavore.domain.food.Barcode

/**
 * L'aperçu caméra, le décodage qui tourne dessus, et la trame sur laquelle il s'arrête.
 *
 * **Ce module porte une composable, ce qu'aucun autre `:integration` ne fait.** Une
 * caméra n'est pas une source de données qu'on puisse mettre derrière un port : c'est
 * une **surface**, et l'abstraire demanderait au domaine de connaître un type de vue —
 * exactement ce que l'architecture lui interdit. Le module fournit donc la surface et
 * le décodage ; l'écran qui l'utilise garde la permission, les états et la navigation.
 *
 * **L'aperçu se fige au lieu de continuer.** À la confirmation, la caméra est déliée
 * et la trame qui a porté la lecture prend sa place. Ce n'est pas la couper : l'image
 * reste, elle cesse de bouger. C'est ce qui dit *ce que* l'appareil a lu, et ce qui
 * permet de juger le cadrage quand la lecture n'aboutit à rien ([D69][decisions]).
 *
 * **[onFrame] rend la même trame en JPEG**, sur un fil d'arrière-plan. C'est ce qui
 * permet à l'écran de validation de la montrer et à l'enregistrement de la garder,
 * sans qu'aucun `Bitmap` ne traverse `:feature:scan` ([D66][decisions]).
 *
 * [onBarcode] n'est appelé qu'après deux lectures d'accord ([SteadyBarcode]), et une
 * seule fois : c'est [resumeKey] qui rouvre la lecture et fait repartir la caméra. Un
 * compteur plutôt qu'un booléen, parce que rescanner le **même** produit doit
 * remarcher, et qu'un booléen repassé à la même valeur ne relance rien.
 *
 * **Rien n'est vérifiable ici sans appareil.** Le liage CameraX, la rotation de
 * l'image et la torche ne s'éprouvent pas sur la JVM ; les deux règles qui pouvaient
 * l'être sont dans [SteadyBarcode] et [frameScale].
 *
 * [decisions]: docs/11-decisions.md
 * @see docs/02-parcours-et-ecrans.md
 */
@Composable
fun BarcodeCamera(
    onBarcode: (Barcode) -> Unit,
    modifier: Modifier = Modifier,
    onFrame: (ByteArray) -> Unit = {},
    torchOn: Boolean = false,
    resumeKey: Int = 0,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    // Sans cette indirection, la lambda capturee a la premiere composition serait
    // celle que l'analyseur appellerait pour toujours.
    val latest by rememberUpdatedState(onBarcode)
    val latestFrame by rememberUpdatedState(onFrame)
    val session = remember(context) { CameraSession(context, { latest(it) }, { latestFrame(it) }) }

    LaunchedEffect(session, resumeKey) { session.resume(lifecycleOwner) }
    LaunchedEffect(session, torchOn) { session.torch(torchOn) }
    DisposableEffect(session) { onDispose { session.release() } }

    Box(modifier) {
        AndroidView(factory = { session.preview }, modifier = Modifier.fillMaxSize())
        // Posee par-dessus et non a la place : la vue d'apercu reste dans l'arbre,
        // donc garde sa surface, et la reprise n'a qu'a relier la camera.
        session.frozen?.let { FrozenFrame(it) }
    }
}

@Composable
private fun FrozenFrame(frame: Bitmap) {
    Image(
        bitmap = frame.asImageBitmap(),
        // Decorative au sens de docs/08 : un lecteur d'ecran n'a rien a en dire, et
        // l'issue de la lecture est annoncee par la surimpression qui la recouvre.
        contentDescription = null,
        // Le meme cadrage que PreviewView, qui recadre au centre. Sans cela l'image
        // figee ne coincide pas avec celle qu'on vient de quitter, et le saut se voit.
        contentScale = ContentScale.Crop,
        modifier = Modifier.fillMaxSize(),
    )
}
