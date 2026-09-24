package app.hexavore.domain.usecase

import app.hexavore.core.testing.InMemoryDiaryRepository
import app.hexavore.core.testing.InMemoryDishPhotos
import app.hexavore.core.testing.InMemoryPhotoSettings
import app.hexavore.core.testing.SampleDiary
import app.hexavore.domain.diary.DishId
import app.hexavore.domain.diary.FavoriteDishId
import app.hexavore.domain.food.FoodId
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate

/**
 * Ce que deviennent les photos d'un plat, du dépôt au rangement.
 *
 * **Toutes les règles de ce mécanisme sont ici.** L'adaptateur réel n'ajoute que des
 * appels au système de fichiers : où l'image attend, quelle origine en apporte une,
 * quelle origine doit écarter celle qui traîne, et ce qu'un balayage a le droit de
 * retirer.
 *
 * **Le cas qui compte le plus est celui du brouillon abandonné.** Le dépôt n'a qu'un
 * emplacement ; une analyse qu'on ne valide pas y laisse son image, et la saisie
 * manuelle suivante l'aurait adoptée. C'est le seul vrai défaut du mécanisme, et c'est
 * [OpenDraftPhoto] qui le tient.
 */
class DishPhotoTest {
    private val photos = InMemoryDishPhotos()

    // --- Déposer ---------------------------------------------------------------------

    @Test
    fun `une photo se depose quand on garde ses photos`() = runTest {
        StageDishPhoto(photos, InMemoryPhotoSettings(keep = true))(JPEG)

        assertNotNull(photos.staged(), "l'image devait attendre son plat")
    }

    @Test
    fun `rien ne se depose quand on ne garde pas ses photos`() = runTest {
        StageDishPhoto(photos, InMemoryPhotoSettings(keep = false))(JPEG)

        assertNull(photos.staged())
    }

    @Test
    fun `eteindre le reglage vide ce qui attendait`() = runTest {
        // Quelqu'un qui vient d'eteindre entre deux analyses ne doit pas voir arriver
        // la photo de la precedente.
        photos.stage(JPEG)

        StageDishPhoto(photos, InMemoryPhotoSettings(keep = false))(JPEG)

        assertNull(photos.staged())
    }

    @Test
    fun `une analyse sans photo ecarte celle qui attendait`() = runTest {
        // Une description seule ne produit aucune image, et le depot n'a qu'un
        // emplacement : sans cet ecart, le plat decrit adopterait la photo du
        // precedent.
        photos.stage(JPEG)

        StageDishPhoto(photos, InMemoryPhotoSettings())(null)

        assertNull(photos.staged())
    }

    // --- Ouvrir un brouillon ---------------------------------------------------------

    @Test
    fun `une proposition et un produit scanne arrivent avec l image deposee`() = runTest {
        photos.stage(JPEG)

        assertNotNull(OpenDraftPhoto(photos)(DraftOrigin.Proposed))
        assertNotNull(OpenDraftPhoto(photos)(DraftOrigin.Scanned(FoodId("f-jus"))))
    }

    @Test
    fun `une saisie neuve n adopte pas l image d une analyse abandonnee`() = runTest {
        photos.stage(JPEG)

        val image = OpenDraftPhoto(photos)(DraftOrigin.New())

        assertNull(image, "une saisie manuelle n'a pas de photo")
        assertNull(photos.staged(), "et celle qui trainait a ete ecartee")
    }

    @Test
    fun `un favori rejoue non plus`() = runTest {
        photos.stage(JPEG)

        assertNull(OpenDraftPhoto(photos)(DraftOrigin.Favorite(FavoriteDishId("fav-1"))))
        assertNull(photos.staged())
    }

    @Test
    fun `un plat rouvert montre la sienne, pas celle qui trainait`() = runTest {
        photos.stage(JPEG)
        photos.attach(DISH)
        photos.stage(AUTRE_JPEG)

        val image = OpenDraftPhoto(photos)(DraftOrigin.Dish(DISH))

        assertEquals(photos.photoOf(DISH), image)
        assertNull(photos.staged(), "l'image qui attendait n'appartient pas a ce plat")
    }

    // --- Ranger ----------------------------------------------------------------------

    @Test
    fun `enregistrer range l image deposee sous le plat`() = runTest {
        photos.stage(JPEG)

        AttachDishPhoto(photos)(DISH, keep = true)

        assertNotNull(photos.photoOf(DISH))
        assertNull(photos.staged(), "elle est rangee, donc elle n'attend plus")
    }

    @Test
    fun `enregistrer sans rien de depose ne touche pas a la photo du plat`() = runTest {
        // Le cas courant d'une correction : on rouvre un plat photographie, on change
        // une quantite, on enregistre. L'image doit rester.
        photos.stage(JPEG)
        photos.attach(DISH)

        AttachDishPhoto(photos)(DISH, keep = true)

        assertNotNull(photos.photoOf(DISH))
    }

    @Test
    fun `la croix emporte la photo du plat et celle qui attendait`() = runTest {
        photos.stage(JPEG)
        photos.attach(DISH)
        photos.stage(AUTRE_JPEG)

        AttachDishPhoto(photos)(DISH, keep = false)

        assertNull(photos.photoOf(DISH))
        assertNull(photos.staged())
    }

    // --- Balayer ---------------------------------------------------------------------

    @Test
    fun `le balayage retire les photos dont le plat n existe plus`() = runTest {
        val note = SampleDiary.day(LocalDate.of(2026, 3, 12)).first()
        val journal = InMemoryDiaryRepository(listOf(note))
        photos.stage(JPEG)
        photos.attach(note.id)
        photos.stage(AUTRE_JPEG)
        photos.attach(DishId("plat-disparu"))

        SweepDishPhotos(photos, journal)()

        assertNotNull(photos.photoOf(note.id), "le plat existe, sa photo reste")
        assertNull(photos.photoOf(DishId("plat-disparu")))
    }

    @Test
    fun `un journal illisible ne fait pas tomber le demarrage`() = runTest {
        // Ce balayage est du menage : une lecture qui echoue au lancement ne doit pas
        // empecher l'application de s'ouvrir.
        val journal = InMemoryDiaryRepository().apply { failure = IllegalStateException("base illisible") }
        photos.stage(JPEG)
        photos.attach(DISH)

        SweepDishPhotos(photos, journal)()

        assertNotNull(photos.photoOf(DISH), "rien n'a ete efface, et rien n'a explose")
    }

    // --- Effacer ---------------------------------------------------------------------

    @Test
    fun `effacer les photos ne touche pas au reglage`() = runTest {
        // Deux gestes : l'un arrete d'en garder, l'autre emporte celles qui sont la.
        val reglage = InMemoryPhotoSettings(keep = true)
        photos.stage(JPEG)
        photos.attach(DISH)

        PurgeDishPhotos(photos)()

        assertTrue(photos.photos.isEmpty())
        assertTrue(reglage.keeping(), "purger n'est pas eteindre")
    }

    @Test
    fun `eteindre le reglage n efface pas les photos gardees`() = runTest {
        val reglage = InMemoryPhotoSettings(keep = true)
        photos.stage(JPEG)
        photos.attach(DISH)

        ChoosePhotoKeeping(reglage, photos)(false)

        assertNotNull(photos.photoOf(DISH), "eteindre n'est pas purger")
    }

    private companion object {
        val DISH = DishId("plat-1")
        val JPEG = byteArrayOf(1, 2, 3)
        val AUTRE_JPEG = byteArrayOf(4, 5, 6, 7)
    }
}
