package app.hexavore.domain.usecase

import app.hexavore.domain.diary.DiaryRepository
import app.hexavore.domain.diary.DishId
import app.hexavore.domain.diary.DishPhotos
import app.hexavore.domain.diary.PhotoFile
import app.hexavore.domain.diary.PhotoSettings
import app.hexavore.domain.diary.PhotoWeight
import kotlinx.coroutines.flow.Flow

/**
 * Dépose l'image qui vient d'arriver, si l'utilisateur garde ses photos.
 *
 * **Le réglage est consulté ici et nulle part ailleurs.** L'écran d'analyse et l'écran
 * de scan produisent tous deux une image et n'ont aucune raison de connaître chacun la
 * règle ; le jour où elle se nuance, elle se nuance une fois.
 *
 * **Éteint, le dépôt est vidé plutôt que laissé tel quel.** Quelqu'un qui vient
 * d'éteindre le réglage entre deux analyses ne doit pas voir arriver la photo de la
 * précédente.
 */
class StageDishPhoto(private val photos: DishPhotos, private val settings: PhotoSettings) {
    /**
     * @param jpeg nul quand le repas a été décrit et non photographié. Le dépôt est
     *   alors vidé : il n'a qu'un emplacement, et l'écran de validation le lit sans
     *   savoir laquelle des deux analyses l'a rempli.
     */
    suspend operator fun invoke(jpeg: ByteArray?) {
        if (jpeg != null && settings.keeping()) photos.stage(jpeg) else photos.discardStaged()
    }
}

/**
 * L'image que l'écran de validation doit montrer, selon d'où vient le brouillon.
 *
 * **Deux origines apportent une image** : une proposition de modèle et un produit
 * scanné. Un plat qu'on rouvre montre la sienne, s'il en a une.
 *
 * **Les autres vident le dépôt**, et c'est la règle qui empêche le seul vrai défaut de
 * ce mécanisme : l'emplacement de dépôt est unique, donc une analyse abandonnée y
 * laisse son image, et la saisie manuelle suivante l'aurait adoptée.
 */
class OpenDraftPhoto(private val photos: DishPhotos) {
    suspend operator fun invoke(origin: DraftOrigin): PhotoFile? {
        if (origin == DraftOrigin.Proposed || origin is DraftOrigin.Scanned) return photos.staged()

        photos.discardStaged()
        return (origin as? DraftOrigin.Dish)?.let { photos.photoOf(it.id) }
    }
}

/**
 * Range l'image du brouillon avec le plat que l'enregistrement vient de nommer.
 *
 * @param keep faux quand la croix a retiré l'image à l'écran. La photo du plat part
 *   alors avec le dépôt : retirer l'image d'un plat qu'on corrige et refuser celle qui
 *   vient d'arriver sont le même geste pour qui le fait.
 */
class AttachDishPhoto(private val photos: DishPhotos) {
    suspend operator fun invoke(dish: DishId, keep: Boolean) {
        if (keep) {
            photos.attach(dish)
        } else {
            photos.discardStaged()
            photos.forget(dish)
        }
    }
}

/** Les plats qui ont une photo, pour l'accueil. */
class ObserveDishPhotos(private val photos: DishPhotos) {
    operator fun invoke(): Flow<Map<DishId, PhotoFile>> = photos.observeKept()
}

/**
 * Retire les photos dont le plat n'existe plus.
 *
 * **Au démarrage, et là seulement.** Une photo survit à la suppression de son plat le
 * temps que la barre d'annulation soit passée, et survit à une restauration le temps
 * qu'on puisse revenir à la copie de sécurité. Ce qui reste après cela n'a plus de plat
 * à décrire, et se ramasse au lancement suivant.
 *
 * **L'échec est avalé.** Ce balayage est du ménage : une lecture de journal qui échoue
 * au démarrage ne doit pas empêcher l'application de s'ouvrir.
 */
class SweepDishPhotos(private val photos: DishPhotos, private val diary: DiaryRepository) {
    suspend operator fun invoke() {
        runCatching { photos.sweep(diary.dishIds()) }
    }
}

/** Ce que les photos occupent, pour l'écran qui propose de les effacer. */
class WeighDishPhotos(private val photos: DishPhotos) {
    suspend operator fun invoke(): PhotoWeight = runCatching { photos.weight() }.getOrDefault(PhotoWeight.NOTHING)
}

/**
 * Efface toutes les photos, et rien d'autre.
 *
 * Distinct d'éteindre le réglage : l'un arrête d'en garder, l'autre emporte celles
 * qui sont là. Les confondre aurait fait disparaître des mois d'images à qui voulait
 * seulement cesser d'en accumuler.
 */
class PurgeDishPhotos(private val photos: DishPhotos) {
    suspend operator fun invoke() = photos.forgetAll()
}

/** Le réglage, tel que l'écran des photos le montre et le change. */
class ObservePhotoKeeping(private val settings: PhotoSettings) {
    operator fun invoke(): Flow<Boolean> = settings.observeKeeping()
}

/** Garder les photos, ou ne plus en garder. Celles qui sont là ne bougent pas. */
class ChoosePhotoKeeping(private val settings: PhotoSettings, private val photos: DishPhotos) {
    suspend operator fun invoke(keep: Boolean) {
        settings.setKeeping(keep)
        // Eteindre emporte le depot : une analyse en cours de validation ne doit pas
        // ranger son image apres que la reponse a change.
        if (!keep) photos.discardStaged()
    }
}
