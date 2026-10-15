package app.hexavore.core.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.ExifInterface
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.Duration
import java.time.Instant

/**
 * Où atterrit une photo prise, et ce qu'il en reste après.
 *
 * **Le cache de l'application, jamais la galerie** ([docs/05][ia] § Confidentialité) :
 * une photo de repas prise pour être analysée n'a pas à se retrouver dans la pellicule
 * entre deux photos de vacances, ni à être indexée par les applications qui lisent
 * `MediaStore`.
 *
 * **Le fichier est lu puis supprimé dans la foulée.** Ce qui voyage ensuite est un
 * tableau d'octets en mémoire, que le processus emporte en mourant.
 *
 * **Un seul nom, réutilisé.** Il ne peut pas y avoir deux prises en vol — l'appareil
 * photo du système est une activité modale — et un nom horodaté aurait demandé de lire
 * l'horloge, ce que le projet réserve au port `Clock` pour de bonnes raisons qui n'ont
 * rien à voir avec un nom de fichier. Le bénéfice est ailleurs : une prise qui écrase
 * un résidu nettoie le cache au lieu de l'encombrer.
 *
 * [ia]: docs/05-ia.md
 */
fun newPhotoFile(context: Context): File = File(photoCache(context), PHOTO_FILE)

/**
 * L'adresse que l'appareil photo du système sait écrire.
 *
 * Un `FileProvider` et non un `file://` : depuis Android 7, exposer un chemin brut à
 * une autre application lève `FileUriExposedException`. L'autorité est dérivée de
 * l'`applicationId`, donc distincte en `debug` et en `release` — deux variantes
 * installées côte à côte ne peuvent pas se marcher dessus.
 */
fun photoUri(context: Context, file: File): Uri =
    FileProvider.getUriForFile(context, context.packageName + FILE_PROVIDER_SUFFIX, file)

/**
 * Balaie ce qu'un processus tué en cours de route a laissé.
 *
 * [docs/05][ia] le demande explicitement. Le cas normal supprime dans un `finally` ;
 * celui-ci couvre l'anormal — l'application tuée entre le déclencheur et la lecture.
 *
 * **Sauf une prise en cours** ([D163][decisions]). Android tue volontiers Hexavore
 * pendant que l'appareil photo du système est ouvert, et c'est ce lancement-ci — celui
 * que provoque l'écriture du fichier, ou le retour de l'appareil photo — qui balayait :
 * il effaçait la photo qu'on venait de prendre, avant que l'écran ne la lise. Une image
 * de moins de [CAPTURE_GRACE] reste ; un résidu plus vieux n'est la prise de personne.
 *
 * @param now l'instant du lancement, lu par l'appelant sur l'horloge du domaine.
 *
 * [ia]: docs/05-ia.md
 * [decisions]: docs/11-decisions.md
 */
fun sweepCapturePhotos(context: Context, now: Instant) {
    runCatching {
        photoCache(context).listFiles()
            ?.filter { abandonedCapture(Instant.ofEpochMilli(it.lastModified()), now) }
            ?.forEach { it.delete() }
    }
}

/** Une prise est abandonnée quand plus personne ne peut l'attendre : au-delà de [CAPTURE_GRACE]. */
internal fun abandonedCapture(writtenAt: Instant, now: Instant): Boolean =
    Duration.between(writtenAt, now) > CAPTURE_GRACE

/**
 * Le temps qu'une prise peut rester dans l'appareil photo du système.
 *
 * Large exprès : un résidu épargné n'est qu'un fichier du cache privé, que la prise
 * suivante écrase et que le lancement d'après emporte ; une photo effacée est un repas
 * à reprendre en photo, et l'assiette n'est souvent plus là.
 */
internal val CAPTURE_GRACE: Duration = Duration.ofHours(1)

private fun photoCache(context: Context): File = File(context.cacheDir, PHOTO_DIRECTORY).apply { mkdirs() }

/**
 * Le JPEG réduit, prêt à partir — ou `null` si l'image est illisible.
 *
 * Trois choses s'y font, dans cet ordre, et l'ordre est ce qui empêche la mémoire
 * d'exploser : mesurer sans décoder, décoder en sautant des pixels
 * ([sampleSizeFor]), puis ramener le côté long à [LONG_SIDE_PX] et compresser.
 *
 * **L'orientation EXIF est appliquée.** Un téléphone tenu debout écrit souvent une
 * image couchée accompagnée d'une étiquette « tourne-moi » : sans elle, le modèle
 * reçoit une assiette de profil et estime des quantités sur une image qu'aucun humain
 * ne verrait ainsi.
 *
 * `null` plutôt qu'une exception : une image illisible est une réponse possible du
 * système de fichiers — l'utilisateur a pu choisir un fichier corrompu ou révoquer
 * l'accès entre le choix et la lecture — et l'écran sait le dire.
 */
fun reduceToJpeg(context: Context, uri: Uri): ByteArray? = runCatching {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }

    val options = BitmapFactory.Options().apply {
        inSampleSize = sampleSizeFor(bounds.outWidth, bounds.outHeight)
    }
    val decoded = context.contentResolver.openInputStream(uri)?.use {
        BitmapFactory.decodeStream(it, null, options)
    } ?: return null

    val rotated = decoded.uprightened(context, uri)
    val (width, height) = scaledSizeFor(rotated.width, rotated.height)
    val scaled = Bitmap.createScaledBitmap(rotated, width, height, true)

    ByteArrayOutputStream().use { out ->
        scaled.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
        out.toByteArray()
    }
}.getOrNull()

/** L'image remise d'aplomb, d'après ce que l'étiquette EXIF déclare. */
private fun Bitmap.uprightened(context: Context, uri: Uri): Bitmap {
    val orientation = context.contentResolver.openInputStream(uri)?.use {
        ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
    } ?: ExifInterface.ORIENTATION_NORMAL

    val degrees = rotationFor(orientation)
    if (degrees == 0f) return this

    val matrix = android.graphics.Matrix().apply { postRotate(degrees) }
    return Bitmap.createBitmap(this, 0, 0, width, height, matrix, true)
}

/**
 * Les trois seules rotations qui existent en pratique.
 *
 * **`android.media.ExifInterface` plutôt que celle d'AndroidX**, et c'est une
 * dépendance en moins : celle de la plateforme lit une orientation JPEG depuis un flux
 * depuis l'API 24, et le projet démarre à 26. Celle d'AndroidX apporte des formats que
 * l'appareil photo du système ne produit pas.
 *
 * Les symétries d'EXIF — miroirs et transpositions — ne sortent d'aucun appareil
 * photo ; les traiter demanderait une matrice de plus pour un cas que personne ne
 * rencontre, et le défaut est de ne rien faire, ce qui est exactement ce qu'il faut
 * pour une image déjà droite.
 */
fun rotationFor(orientation: Int): Float = when (orientation) {
    ExifInterface.ORIENTATION_ROTATE_90 -> DEGREES_90
    ExifInterface.ORIENTATION_ROTATE_180 -> DEGREES_180
    ExifInterface.ORIENTATION_ROTATE_270 -> DEGREES_270
    else -> 0f
}

private const val DEGREES_90 = 90f
private const val DEGREES_180 = 180f
private const val DEGREES_270 = 270f
private const val PHOTO_DIRECTORY = "capture"
private const val PHOTO_FILE = "repas.jpg"
private const val FILE_PROVIDER_SUFFIX = ".capture"
