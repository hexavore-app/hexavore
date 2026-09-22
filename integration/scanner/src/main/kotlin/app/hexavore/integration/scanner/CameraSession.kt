package app.hexavore.integration.scanner

import android.content.Context
import android.graphics.Bitmap
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import app.hexavore.domain.food.Barcode
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.Executors

/**
 * La caméra du scan : ce qui est lié, ce qui est figé, et le passage de l'un à l'autre.
 *
 * **Une seule règle gouverne l'objet : la caméra tourne tant que rien n'est figé.**
 * Deux signaux qui diraient la même chose — l'état de l'écran et la confirmation du
 * décodeur — finiraient par ne plus être d'accord, et la caméra resterait allumée
 * derrière une issue, ou éteinte devant un viseur.
 *
 * **C'est ici que vit le `Bitmap`, et c'est délibéré.** Le mettre dans l'état de
 * l'écran ferait entrer un type Android dans `ScanViewModel`, qui est la seule chose
 * que cet écran ait de vérifiable sur la JVM ([D66][decisions]). La trame appartient
 * à la surface qui l'a produite ; l'écran n'a pas à savoir qu'elle existe.
 *
 * **Rien ici ne s'éprouve sans appareil** — le liage CameraX, la rotation, la torche.
 * Ce qui pouvait en être sorti l'a été : l'anti-rebond dans [SteadyBarcode], la
 * réduction de la trame dans [frameScale].
 *
 * [decisions]: docs/11-decisions.md
 */
internal class CameraSession(private val context: Context, private val onBarcode: (Barcode) -> Unit) {
    /**
     * La vue d'aperçu.
     *
     * Elle survit aux liages et aux déliages, et reste dans l'arbre même quand une
     * trame la recouvre : la recréer à chaque scan coûterait une surface à démonter
     * et à reconstruire, c'est-à-dire le délai qu'on cherche justement à éviter au
     * moment de reprendre.
     */
    val preview = PreviewView(context)

    /**
     * La trame figée, ou `null` tant que la lecture est ouverte.
     *
     * Elle est aussi le verrou : la caméra est déliée exactement quand cette valeur
     * n'est pas nulle.
     */
    var frozen: Bitmap? by mutableStateOf(null)
        private set

    // Un seul fil pour l'analyse : le decodeur y lit une image a la fois, et c'est
    // suffisant -- CameraX jette les images en trop plutot que de les empiler. C'est
    // aussi le seul fil qui touche l'anti-rebond, qui n'a donc rien a synchroniser.
    private val steady = SteadyBarcode()
    private val analysisThread = Executors.newSingleThreadExecutor()
    private val mainThread = ContextCompat.getMainExecutor(context)

    // Le code lu traverse jusqu'au fil principal : delier la camera et ecrire un etat
    // de composition ne se font que la.
    private val decoder = BarcodeAnalyzer(steady) { code, frame -> mainThread.execute { settle(code, frame) } }

    private val previewCase = Preview.Builder().build().apply { surfaceProvider = preview.surfaceProvider }

    private val analysisCase = ImageAnalysis
        .Builder()
        // Le scan lit le present : une file d'images en retard ferait decoder un
        // cadrage qu'on a deja quitte.
        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
        .build()
        .apply { setAnalyzer(analysisThread, decoder) }

    private var provider: ProcessCameraProvider? = null
    private var bound: Camera? = null
    private var torchOn = false
    private var released = false

    /**
     * Rouvre la lecture : la trame tombe, l'anti-rebond oublie, la caméra se relie.
     *
     * Appelée aussi à la première composition — ouvrir et rouvrir sont le même geste,
     * et en faire deux chemins distincts serait s'assurer qu'un des deux dérive.
     *
     * **L'oubli part sur le fil d'analyse**, pas ici : c'est le seul fil qui touche
     * l'anti-rebond. Posé dans la file avant que la caméra se relie, il passe avant
     * la première image de la reprise.
     */
    suspend fun resume(owner: LifecycleOwner) {
        frozen = null
        analysisThread.execute(steady::resume)

        val cameras = provider ?: ProcessCameraProvider.getInstance(context).await(context).also { provider = it }
        cameras.unbindAll()
        bound = cameras.bindToLifecycle(owner, CameraSelector.DEFAULT_BACK_CAMERA, previewCase, analysisCase)
        // La torche ne survit pas au deliage : la reprise la repose, sans quoi elle
        // s'eteindrait a chaque scan sans que personne ne l'ait demande.
        bound?.cameraControl?.enableTorch(torchOn)
    }

    fun torch(on: Boolean) {
        torchOn = on
        bound?.cameraControl?.enableTorch(on)
    }

    /** Tout ce qui a été ouvert : la caméra et le fil d'analyse. */
    fun release() {
        released = true
        provider?.unbindAll()
        bound = null
        analysisCase.clearAnalyzer()
        analysisThread.shutdown()
    }

    /**
     * Un code confirmé, et la trame qui l'a porté.
     *
     * **Sans trame, on ne fige rien et la caméra continue de tourner.** C'est le
     * comportement d'avant, et c'est le bon repli : un écran qui aurait délié sa
     * caméra sans avoir d'image à mettre à la place montrerait un rectangle noir, ce
     * qui est la seule chose pire que l'aperçu qui bouge.
     *
     * **Rien n'arrive après [release].** Un code confirmé à l'instant où l'écran se
     * referme est déjà en route vers le fil principal ; le livrer ouvrirait une fiche
     * depuis un écran que l'utilisateur vient de quitter.
     */
    private fun settle(code: Barcode, frame: Bitmap?) {
        if (released) return
        if (frame != null) {
            frozen = frame
            provider?.unbindAll()
            bound = null
        }
        onBarcode(code)
    }
}

/**
 * L'attente du fournisseur, en suspension plutôt qu'en blocage.
 *
 * `ProcessCameraProvider.getInstance` rend un `ListenableFuture` ; l'attendre avec
 * `get()` immobiliserait le fil principal le temps que la caméra s'initialise, ce qui
 * se voit à l'ouverture de l'écran.
 */
private suspend fun ListenableFuture<ProcessCameraProvider>.await(context: Context): ProcessCameraProvider =
    suspendCancellableCoroutine { continuation ->
        addListener({ continuation.resumeWith(runCatching { get() }) }, ContextCompat.getMainExecutor(context))
    }
