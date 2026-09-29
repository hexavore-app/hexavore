package app.hexavore.domain.usecase

import app.hexavore.core.testing.InMemoryDishPhotos
import app.hexavore.core.testing.InMemoryPhotoSettings
import app.hexavore.domain.ai.AiError
import app.hexavore.domain.ai.EstimatedUnit
import app.hexavore.domain.ai.FoodRecognizer
import app.hexavore.domain.ai.InMemoryPendingRecognition
import app.hexavore.domain.ai.Recognition
import app.hexavore.domain.ai.RecognitionInput
import app.hexavore.domain.ai.RecognitionOutcome
import app.hexavore.domain.ai.RecognizedItem
import app.hexavore.domain.diary.EntrySource
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * L'analyse, telle que les deux écrans qui la déclenchent la partagent.
 *
 * Ce qui s'éprouve ici est ce que le cas d'usage décide **à leur place** : la source du
 * plat, le sort de la photo, et le fait que rien ne soit déposé quand le modèle échoue.
 * Ces trois règles vivaient dans l'écran d'IA tant qu'il était seul à analyser ; la
 * barre du bas en a fait des règles du domaine ([D131][decisions]).
 *
 * [decisions]: docs/11-decisions.md
 */
internal class AnalyseMealTest {
    private val pending = InMemoryPendingRecognition()
    private val photos = InMemoryDishPhotos()
    private val keeping = InMemoryPhotoSettings()
    private var outcome: RecognitionOutcome = RecognitionOutcome.Recognized(Recognition(listOf(RIZ)))

    private val analyse = AnalyseMeal(
        recognizer = FoodRecognizer { outcome },
        pending = pending,
        stagePhoto = StageDishPhoto(photos, keeping),
    )

    @Test
    fun `une phrase se note comme une description, jamais comme une photo`() = runTest {
        analyse(RecognitionInput.Text("un bol de riz"))

        assertEquals(EntrySource.TEXT_AI, pending.take()?.source)
    }

    @Test
    fun `une photo se note comme une photo, meme accompagnee d une precision`() = runTest {
        analyse(RecognitionInput.Photo(JPEG, note = "l'assiette fait 24 cm"))

        assertEquals(EntrySource.PHOTO_AI, pending.take()?.source)
    }

    @Test
    fun `la photo analysee est rangee avec la proposition`() = runTest {
        analyse(RecognitionInput.Photo(JPEG, note = null))

        assertArrayEquals(JPEG, photos.stagedBytes)
    }

    @Test
    fun `une phrase ecarte la photo qui trainait`() = runTest {
        // Le depot n'a qu'un emplacement : une assiette photographiee puis abandonnee
        // ne doit pas s'attacher au repas qu'on vient de decrire.
        photos.stage(JPEG)

        analyse(RecognitionInput.Text("deux oeufs"))

        assertNull(photos.stagedBytes)
    }

    @Test
    fun `reglage eteint, la photo n est pas gardee et la proposition arrive quand meme`() = runTest {
        keeping.setKeeping(false)

        analyse(RecognitionInput.Photo(JPEG, note = null))

        assertNull(photos.stagedBytes)
        assertEquals(EntrySource.PHOTO_AI, pending.take()?.source)
    }

    @Test
    fun `un echec ne depose rien`() = runTest {
        outcome = RecognitionOutcome.Failed(AiError.NoNetwork)

        val result = analyse(RecognitionInput.Text("un bol de riz"))

        assertNull(pending.take())
        assertEquals(RecognitionOutcome.Failed(AiError.NoNetwork), result)
    }

    private companion object {
        val RIZ = RecognizedItem(label = "riz", quantity = 1.0, unit = EstimatedUnit.BOWL, confidence = 0.9f)
        val JPEG = byteArrayOf(1, 2, 3)
    }
}
