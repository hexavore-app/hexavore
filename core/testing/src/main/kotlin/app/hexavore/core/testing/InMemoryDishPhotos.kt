package app.hexavore.core.testing

import app.hexavore.domain.diary.DishId
import app.hexavore.domain.diary.DishPhotos
import app.hexavore.domain.diary.PhotoFile
import app.hexavore.domain.diary.PhotoSettings
import app.hexavore.domain.diary.PhotoWeight
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Les photos des plats, en mémoire.
 *
 * **Aucun fichier**, mais la même mécanique : un emplacement de dépôt unique, un
 * rangement par identifiant de plat, et un balayage qui ne regarde que ce qui est rangé.
 * C'est là que sont toutes les règles éprouvables ; l'adaptateur réel n'ajoute que des
 * appels au système de fichiers.
 *
 * Les chemins sont inventés et n'ouvrent rien. Ils servent à vérifier qu'une photo est
 * bien celle d'un plat, jamais à lire quoi que ce soit : ce port désigne un fichier, il
 * ne le lit pas.
 */
class InMemoryDishPhotos : DishPhotos {
    private val kept = MutableStateFlow(emptyMap<DishId, PhotoFile>())

    /** Ce que le brouillon en cours a déposé, ou `null`. */
    var stagedBytes: ByteArray? = null
        private set

    /** Ce qui est rangé, pour qu'un cas vérifie sans passer par le flux. */
    val photos: Map<DishId, PhotoFile> get() = kept.value

    override fun observeKept(): Flow<Map<DishId, PhotoFile>> = kept.asStateFlow()

    override suspend fun photoOf(dish: DishId): PhotoFile? = kept.value[dish]

    override suspend fun staged(): PhotoFile? = stagedBytes?.let { PhotoFile(DRAFT_PATH) }

    override suspend fun stage(jpeg: ByteArray) {
        stagedBytes = jpeg
    }

    override suspend fun discardStaged() {
        stagedBytes = null
    }

    override suspend fun attach(dish: DishId) {
        if (stagedBytes == null) return
        stagedBytes = null
        kept.value = kept.value + (dish to PhotoFile("memoire/" + dish.value + ".jpg"))
    }

    override suspend fun forget(dish: DishId) {
        kept.value = kept.value - dish
    }

    override suspend fun forgetAll() {
        stagedBytes = null
        kept.value = emptyMap()
    }

    override suspend fun sweep(known: Set<DishId>) {
        kept.value = kept.value.filterKeys { it in known }
    }

    /**
     * Un poids inventé mais cohérent : un octet par photo.
     *
     * Ce que l'écran des réglages en fait est d'afficher deux nombres ; ce qu'un cas
     * peut en vérifier est qu'ils bougent quand on efface, et cela suffit.
     */
    override suspend fun weight(): PhotoWeight = PhotoWeight(count = kept.value.size, bytes = kept.value.size.toLong())
}

/** Le réglage des photos, en mémoire. Allumé, comme sur une installation neuve. */
class InMemoryPhotoSettings(keep: Boolean = true) : PhotoSettings {
    private val state = MutableStateFlow(keep)

    override fun observeKeeping(): Flow<Boolean> = state.asStateFlow()

    override suspend fun keeping(): Boolean = state.value

    override suspend fun setKeeping(keep: Boolean) {
        state.value = keep
    }
}

private const val DRAFT_PATH = "memoire/brouillon.jpg"
