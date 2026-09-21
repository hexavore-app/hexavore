package app.hexavore.integration.scanner

import android.graphics.Bitmap
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import app.hexavore.domain.food.Barcode
import zxingcpp.BarcodeReader

/**
 * Le décodeur branché sur le flux de la caméra.
 *
 * **zxing-cpp, et non ML Kit** ([D123][decisions]). ML Kit envoyait à Google des
 * diagnostics sur l'appareil, avec un identifiant d'installation, et rien ne permettait
 * de le couper. zxing-cpp est libre, lit sur l'appareil et ne parle à personne : le
 * scan tient la promesse « sans télémétrie » au lieu de la démentir.
 *
 * **Trois formats déclarés, et pas un de plus.** Restreindre la liste n'est pas une
 * économie : le décodeur rendrait volontiers un QR code ou un code de rayonnage, et un
 * faux positif y ressemble à un scan réussi. Ce sont les symbologies des produits
 * alimentaires, et rien d'autre ([docs/02][parcours]).
 *
 * **UPC-E n'y figure pas**, et c'est la conséquence directe de [D63][decisions] : huit
 * chiffres ne disent pas s'ils sont un EAN-8 ou un UPC-E compressé. Le déclarer ici
 * ferait remonter des codes que [Barcode] lirait comme des EAN-8, donc désignant un
 * autre produit. Refuser la symbologie à la source est plus honnête que de la
 * rattraper par la clé de contrôle une fois sur dix.
 *
 * **Le décodage est synchrone, sur le fil d'analyse**, et la confirmation aussi :
 * [SteadyBarcode] n'est touché que par ce fil, [CameraSession] y envoie même sa
 * reprise. Seul le résultat en sort, par [deliver], et c'est l'appelant qui décide sur
 * quel fil le recevoir.
 *
 * **La règle est ailleurs.** Tout ce que cette classe fait — lire une image, en garder
 * la trame, refermer — n'est vérifiable que sur un appareil. L'anti-rebond, lui, est
 * dans [SteadyBarcode] et s'éprouve sur la JVM ; la réduction de la trame aussi, dans
 * [frameScale].
 *
 * [parcours]: docs/02-parcours-et-ecrans.md
 * [decisions]: docs/11-decisions.md
 */
internal class BarcodeAnalyzer(private val steady: SteadyBarcode, private val deliver: (Barcode, Bitmap?) -> Unit) :
    ImageAnalysis.Analyzer {
    private val reader = BarcodeReader(foodFormats())

    // L'image **doit** etre refermee dans tous les cas : la retenir bloque le flux, et
    // l'apercu se fige sans que rien ne le dise.
    override fun analyze(image: ImageProxy) = image.use(::confirm)

    /**
     * La confirmation, et le seul endroit d'où sort une trame.
     *
     * **C'est ici et nulle part ailleurs** : [SteadyBarcode] ne rend un code qu'à la
     * seconde lecture d'accord, donc la capture n'a lieu qu'une fois par scan, sur
     * l'image même qui a porté l'accord. Capturer ailleurs — plus tôt, à chaque
     * image — coûterait une conversion trente fois par seconde ; plus tard, on
     * n'aurait plus l'`ImageProxy`, refermé dès que l'analyse s'achève.
     */
    private fun confirm(image: ImageProxy) {
        val settled = reader.read(image).firstNotNullOfOrNull { it.text?.let(steady::read) } ?: return
        deliver(settled, image.capture())
    }
}

/**
 * Les trois symbologies, et deux efforts que ML Kit faisait sans qu'on le demande.
 *
 * **Les défauts de l'emballage Android sont plus timides que ceux de la bibliothèque** :
 * ni recherche approfondie, ni rotation. Sans `tryRotate`, un paquet tenu de travers
 * n'est plus lu, là où ML Kit le lisait dans toutes les orientations ; sans
 * `tryHarder`, un code abîmé ou mal éclairé abandonne plus tôt. Le coût est celui d'une
 * image plus longue à lire, sur des trames d'analyse de l'ordre de 640 × 480.
 *
 * Une fonction et non une constante : les options sont une `data class` aux champs
 * modifiables, et une instance partagée pourrait changer sous un lecteur qui la tient.
 */
private fun foodFormats() = BarcodeReader.Options(
    formats = setOf(BarcodeReader.Format.EAN_13, BarcodeReader.Format.EAN_8, BarcodeReader.Format.UPC_A),
    tryHarder = true,
    tryRotate = true,
)
