package app.hexavore.data.backup

import app.hexavore.domain.backup.Snapshot
import app.hexavore.domain.backup.SnapshotArchive
import app.hexavore.domain.backup.SnapshotCodec
import app.hexavore.domain.backup.SnapshotRead
import app.hexavore.domain.concurrency.DispatcherProvider
import app.hexavore.domain.diary.DishId
import app.hexavore.domain.diary.DishPhotos
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.File
import java.io.FilterOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * L'archive de sauvegarde : un zip, le journal d'abord, les photos ensuite.
 *
 * **Un zip et non un second format.** Le journal reste exactement le fichier que
 * [JsonSnapshotCodec] produit, sous son nom habituel : une archive ouverte avec
 * n'importe quel outil rend un `hexavore.json.gz` qu'on décompresse et répare à la
 * main, comme avant. Ce que le zip ajoute est un dossier de photos à côté, pas une
 * couche qui rendrait le journal opaque ([docs/09][donnees]).
 *
 * **Le journal est la première entrée**, et c'est ce qui permet de refuser un fichier
 * trop récent avant d'avoir écrit la moindre image.
 *
 * **Un ancien fichier reste lisible.** Un export écrit avant que les photos existent
 * est un gzip nu ; il se reconnaît à ses deux premiers octets, et se lit par le codec
 * comme il l'a toujours été.
 *
 * [donnees]: docs/09-donnees-et-sauvegarde.md
 */
@Singleton
class ZipSnapshotArchive @Inject constructor(
    private val codec: SnapshotCodec,
    private val photos: DishPhotos,
    private val dispatchers: DispatcherProvider,
) : SnapshotArchive {
    override suspend fun write(snapshot: Snapshot, sink: OutputStream): Long {
        val journal = codec.encode(snapshot)
        val kept = photos.observeKept().first()

        return withContext(dispatchers.io) {
            val counted = CountingOutputStream(sink)
            ZipOutputStream(counted).use { zip ->
                zip.putNextEntry(ZipEntry(JOURNAL))
                zip.write(journal)
                zip.closeEntry()

                // Lues une par une et recopiees telles quelles : une photo passe par
                // un tampon de quelques kilo-octets, jamais par un tableau qui
                // porterait l'annee entiere.
                kept.forEach { (dish, photo) ->
                    val file = File(photo.path)
                    if (!file.exists()) return@forEach
                    zip.putNextEntry(ZipEntry(PHOTOS + dish.value + EXTENSION))
                    file.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                }
            }
            counted.written
        }
    }

    override suspend fun read(source: InputStream): SnapshotRead = withContext(dispatchers.io) {
        val buffered = BufferedInputStream(source)
        if (!buffered.looksLikeZip()) return@withContext codec.decode(buffered.readBytes())

        var journal: SnapshotRead = SnapshotRead.Unreadable
        ZipInputStream(buffered).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                // Le journal decide : refuse, on s'arrete sans avoir touche a une
                // seule image.
                if (entry.name == JOURNAL) {
                    journal = codec.decode(zip.readBytes())
                    if (journal !is SnapshotRead.Readable) return@use
                } else {
                    entry.dishId()?.let { dish -> keep(dish, zip.readBytes()) }
                }
                entry = zip.nextEntry
            }
        }
        journal
    }

    /**
     * Range une photo de l'archive, par les mêmes gestes que l'application.
     *
     * Déposer puis attacher plutôt qu'écrire un fichier : c'est le seul chemin que le
     * port expose, il valide l'identifiant, et il tient la carte que l'accueil observe.
     */
    private suspend fun keep(dish: DishId, jpeg: ByteArray) {
        photos.stage(jpeg)
        photos.attach(dish)
    }
}

/**
 * Le nom d'une entrée de photo, ramené à son plat.
 *
 * `null` pour tout le reste : un dossier, un fichier ajouté par quelqu'un, un chemin
 * qui remonte. Une archive est un fichier que l'utilisateur a pu ouvrir et modifier, et
 * la seule réponse sûre à une entrée qu'on ne reconnaît pas est de la laisser.
 */
private fun ZipEntry.dishId(): DishId? = name
    .takeIf { !isDirectory && it.startsWith(PHOTOS) && it.endsWith(EXTENSION) }
    ?.removePrefix(PHOTOS)
    ?.removeSuffix(EXTENSION)
    ?.takeIf { SAFE_NAME.matches(it) }
    ?.let(::DishId)

/**
 * Les deux premiers octets disent le format.
 *
 * `PK` pour un zip, `1f 8b` pour un gzip. Le flux est remis là où il était : c'est le
 * lecteur suivant qui le consommera, du début.
 */
private fun BufferedInputStream.looksLikeZip(): Boolean {
    mark(SIGNATURE.size)
    val head = ByteArray(SIGNATURE.size)
    var filled = 0
    // Une lecture rend ce qu'elle veut, pas ce qu'on demande : quatre octets peuvent
    // arriver en quatre fois, et `readNBytes` n'existe qu'a partir d'Android 13.
    while (filled < head.size) {
        val read = read(head, filled, head.size - filled)
        if (read < 0) break
        filled += read
    }
    reset()
    return filled == SIGNATURE.size && head.contentEquals(SIGNATURE)
}

/**
 * Un flux qui compte ce qui passe.
 *
 * L'écran annonce la taille de l'export, et c'est ce qui rassure : une sauvegarde vide
 * ne rassure pas. Le compter ici évite de rouvrir le document pour le mesurer.
 */
private class CountingOutputStream(sink: OutputStream) : FilterOutputStream(sink) {
    var written: Long = 0L
        private set

    override fun write(b: Int) {
        out.write(b)
        written++
    }

    override fun write(b: ByteArray, off: Int, len: Int) {
        out.write(b, off, len)
        written += len
    }
}

/** Le journal, sous le nom qu'il porte quand il voyage seul. */
private const val JOURNAL = "hexavore.json.gz"

private const val PHOTOS = "photos/"
private const val EXTENSION = ".jpg"

/** La signature d'un zip : « PK », et les deux octets d'un en-tête de fichier local. */
private val SIGNATURE = byteArrayOf(0x50, 0x4B, 0x03, 0x04)

/** Ce qu'un identifiant doit valoir pour être accepté d'une archive. */
private val SAFE_NAME = Regex("[A-Za-z0-9_-]{1,64}")
