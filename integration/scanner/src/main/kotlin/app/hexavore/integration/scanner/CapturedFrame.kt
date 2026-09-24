package app.hexavore.integration.scanner

import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.camera.core.ImageProxy
import java.io.ByteArrayOutputStream

/**
 * La trame sur laquelle un code vient d'être lu, redressée et réduite.
 *
 * **CameraX ne redresse pas la trame d'analyse.** `toBitmap()` rend les pixels tels
 * que le capteur les a livrés ; `imageInfo.rotationDegrees` dit de combien il faut la
 * tourner pour retrouver ce que l'aperçu montrait. Sans cette rotation, l'image figée
 * est couchée alors que celle qu'on vient de quitter ne l'était pas — et l'écran se
 * met à mentir sur ce qu'il a lu, ce qui est précisément le contraire du but.
 *
 * `null` quand la conversion échoue. Ce n'est pas un cas théorique — un format que
 * CameraX ne sait pas convertir suffit — et l'appelant en tire la seule conclusion
 * utile : sans trame à figer, il n'y a rien à figer, et l'aperçu continue de tourner
 * comme avant.
 */
internal fun ImageProxy.capture(): Bitmap? = runCatching {
    val source = toBitmap()
    val reduction = frameScale(source.width, source.height, FRAME_MAX_SIDE)
    val transform = Matrix().apply {
        postScale(reduction, reduction)
        postRotate(imageInfo.rotationDegrees.toFloat())
    }
    Bitmap.createBitmap(source, 0, 0, source.width, source.height, transform, true)
}.getOrNull()

/**
 * Le facteur de réduction d'une trame, pour que son côté long tienne dans [maxSide].
 *
 * **Jamais supérieur à 1.** Une trame d'analyse est déjà petite — CameraX en rend une
 * de l'ordre de 640 × 480 — et l'agrandir n'ajoute aucun détail tout en multipliant
 * l'octet retenu. C'est la moitié utile de la fonction : la borne ne sert que sur les
 * appareils qui livrent plus grand, l'absence d'agrandissement sert sur tous.
 */
internal fun frameScale(width: Int, height: Int, maxSide: Int): Float =
    minOf(1f, maxSide.toFloat() / maxOf(width, height))

/**
 * Le côté long au-delà duquel une trame est réduite.
 *
 * Cette image est un **témoin** : elle sert à savoir ce que l'appareil a lu et à juger
 * si le cadrage était en cause, pas à être examinée de près. 720 px suffisent aux deux
 * et plafonnent la trame retenue à un mégaoctet et demi. C'est un budget de pixels et
 * non une valeur de style : il vit ici et non dans `:core:designsystem`, comme les
 * 1024 px de l'image envoyée à un modèle en vivent dans docs/02-parcours-et-ecrans.md.
 */
private const val FRAME_MAX_SIDE = 720

/**
 * La trame figée, en JPEG.
 *
 * C'est le format que tout le reste du projet manipule : `:feature:capture` réduit sa
 * photo en JPEG avant de l'envoyer à un modèle, et les photos de plats sont des JPEG
 * sur le disque. Rendre un `Bitmap` obligerait l'écran de scan à connaître un type
 * Android, ce que [D66][decisions] lui interdit pour de bonnes raisons.
 *
 * Qualité 80, comme l'image envoyée au modèle : au-delà, le poids monte sans que
 * l'oeil suive, et une trame d'analyse de 720 px n'a pas de détail fin à préserver.
 *
 * [decisions]: docs/11-decisions.md
 */
internal fun Bitmap.toJpeg(): ByteArray = ByteArrayOutputStream().use { out ->
    compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
    out.toByteArray()
}

private const val JPEG_QUALITY = 80
