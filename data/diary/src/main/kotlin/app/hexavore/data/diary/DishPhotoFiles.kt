package app.hexavore.data.diary

import android.content.Context
import app.hexavore.domain.concurrency.DispatcherProvider
import app.hexavore.domain.diary.DishId
import app.hexavore.domain.diary.DishPhotos
import app.hexavore.domain.diary.PhotoFile
import app.hexavore.domain.diary.PhotoWeight
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.onSubscription
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Les photos des plats, dans un dossier de l'application.
 *
 * **`filesDir` et non `cacheDir`.** Le cache est ce que le système vide quand le
 * stockage manque, et une photo que l'utilisateur a choisi de garder n'a pas à
 * disparaître sans qu'il l'ait demandé. C'est l'écart avec le dossier de capture de
 * `:feature:capture`, qui lui est bien un cache : une photo en route vers un modèle ne
 * survit pas à sa lecture.
 *
 * **Le nom du fichier est l'identifiant du plat**, et c'est ce qui rend la base et le
 * disque impossibles à désaccorder : aucune colonne ne peut annoncer une photo absente,
 * aucun fichier ne peut se rattacher au mauvais plat. Une restauration qui ramène les
 * mêmes identifiants retrouve donc les mêmes photos, sans que rien n'ait à les apparier.
 *
 * **Un identifiant qui ne serait pas un nom de fichier n'a pas de photo.** Ceux du
 * projet sont des UUID, mais une sauvegarde se répare à la main ([docs/09][donnees]) et
 * rien n'empêche d'y écrire autre chose. Refuser est la seule réponse qui ne puisse pas
 * écrire hors du dossier.
 *
 * [donnees]: docs/09-donnees-et-sauvegarde.md
 * @see app.hexavore.domain.diary.DishPhotos
 */
@Singleton
class DishPhotoFiles @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dispatchers: DispatcherProvider,
) : DishPhotos {
    private val folder: File by lazy { File(context.filesDir, DIRECTORY) }

    // La carte est tenue en memoire et reecrite apres chaque ecriture : l'accueil s'y
    // abonne, et un systeme de fichiers ne sait pas notifier.
    private val kept = MutableStateFlow(emptyMap<DishId, PhotoFile>())

    /**
     * Le dossier n'est lu qu'au premier abonnement.
     *
     * Le lire à la construction ferait une lecture disque dans le graphe Hilt, donc au
     * démarrage, pour un écran que l'utilisateur n'a peut-être pas ouvert.
     */
    override fun observeKept(): Flow<Map<DishId, PhotoFile>> =
        kept.onSubscription { withContext(dispatchers.io) { kept.value = folder.keptPhotos() } }

    override suspend fun photoOf(dish: DishId): PhotoFile? = withContext(dispatchers.io) {
        folder.photoOf(dish)?.takeIf { it.exists() }?.let { PhotoFile(it.path) }
    }

    override suspend fun staged(): PhotoFile? = withContext(dispatchers.io) {
        folder.draft().takeIf { it.exists() }?.let { PhotoFile(it.path) }
    }

    override suspend fun stage(jpeg: ByteArray) {
        withContext(dispatchers.io) {
            folder.mkdirs()
            folder.draft().writeBytes(jpeg)
        }
    }

    override suspend fun discardStaged() {
        withContext(dispatchers.io) { folder.draft().delete() }
    }

    /**
     * Le rangement est un **renommage**, et la recopie n'est qu'un repli.
     *
     * Les deux fichiers sont dans le même dossier, donc sur le même volume : le
     * renommage est atomique et ne relit pas deux cents kilo-octets. Le repli couvre le
     * cas où le système le refuse quand même.
     *
     * Sans rien de déposé, rien ne se passe. C'est ce qui permet à l'écran de validation
     * d'appeler ce verbe sans distinguer les plats qui arrivent avec une image de ceux
     * qui arrivent sans.
     */
    override suspend fun attach(dish: DishId) {
        withContext(dispatchers.io) {
            val staged = folder.draft().takeIf { it.exists() } ?: return@withContext
            val destination = folder.photoOf(dish) ?: return@withContext

            destination.delete()
            if (!staged.renameTo(destination)) {
                staged.copyTo(destination, overwrite = true)
                staged.delete()
            }
            kept.value = folder.keptPhotos()
        }
    }

    override suspend fun forget(dish: DishId) {
        withContext(dispatchers.io) {
            folder.photoOf(dish)?.delete()
            kept.value = folder.keptPhotos()
        }
    }

    override suspend fun forgetAll() {
        withContext(dispatchers.io) {
            folder.listFiles()?.forEach { it.delete() }
            kept.value = folder.keptPhotos()
        }
    }

    override suspend fun sweep(known: Set<DishId>) {
        withContext(dispatchers.io) {
            val names = known.mapTo(mutableSetOf()) { it.value + EXTENSION }
            // Le brouillon n'est pas une orpheline : il attend un plat qui n'existe pas
            // encore, et c'est l'ouverture du prochain brouillon qui l'ecarte.
            folder.photoFiles().filterNot { it.name in names }.forEach { it.delete() }
            kept.value = folder.keptPhotos()
        }
    }

    override suspend fun weight(): PhotoWeight = withContext(dispatchers.io) {
        val photos = folder.photoFiles()
        PhotoWeight(count = photos.size, bytes = photos.sumOf { it.length() })
    }
}

/** L'emplacement unique où un brouillon dépose son image, en attendant un identifiant. */
private fun File.draft(): File = File(this, DRAFT)

/**
 * Le fichier d'un plat, ou `null` si son identifiant n'est pas un nom de fichier.
 *
 * C'est la garde qui empêche d'écrire hors du dossier, et elle porte sur l'identifiant
 * plutôt que sur le chemin : un `..` refusé ici ne peut plus être construit ailleurs.
 */
private fun File.photoOf(dish: DishId): File? =
    if (SAFE_NAME.matches(dish.value)) File(this, dish.value + EXTENSION) else null

/** Les photos rangées, le dépôt du brouillon exclu. */
private fun File.photoFiles(): List<File> =
    listFiles().orEmpty().filter { it.name != DRAFT && it.name.endsWith(EXTENSION) }

/**
 * Ce que le dossier contient, sous la forme que l'accueil attend.
 *
 * Quelques centaines d'entrées de nom de fichier, relues après chaque écriture : moins
 * cher qu'une colonne de plus à tenir d'accord avec le disque.
 */
private fun File.keptPhotos(): Map<DishId, PhotoFile> =
    photoFiles().associate { DishId(it.name.removeSuffix(EXTENSION)) to PhotoFile(it.path) }

private const val DIRECTORY = "plats"
private const val DRAFT = "brouillon.jpg"
private const val EXTENSION = ".jpg"

/** Ce qu'un identifiant doit valoir pour devenir un nom de fichier. */
private val SAFE_NAME = Regex("[A-Za-z0-9_-]{1,64}")
