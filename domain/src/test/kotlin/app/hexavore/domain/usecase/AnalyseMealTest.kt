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

    /** Combien de fois le port a ete appele : c'est ce que la relance change. */
    private var calls = 0

    private val analyse = AnalyseMeal(
        recognizer = FoodRecognizer {
            calls++
            outcome
        },
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

    /**
     * Un fournisseur deborde est **reessaye une fois**, et sans qu'on le demande.
     *
     * Les modeles gratuits les plus demandes rendent `503` aux heures de repas,
     * c'est-a-dire exactement quand on photographie une assiette ; l'embouteillage dure
     * quelques secondes, et rien n'est facture. Afficher une erreur reviendrait a
     * demander un geste que la machine peut refaire seule (D156).
     *
     * **L'attente est sautee par l'horloge virtuelle de `runTest`** : ce que le cas
     * eprouve est la relance, pas trois secondes.
     */
    @Test
    fun `un fournisseur deborde est reessaye une fois`() = runTest {
        outcome = RecognitionOutcome.Failed(AiError.ProviderBusy)

        analyse(RecognitionInput.Text("un bol de riz"))

        assertEquals(2, calls, "un 503 ne se montre pas sans avoir ete reessaye")
    }

    /** La relance s'annonce : une attente qu'on n'explique pas se lit comme une panne. */
    @Test
    fun `la relance est annoncee a l ecran`() = runTest {
        outcome = RecognitionOutcome.Failed(AiError.ProviderBusy)
        var annonce = 0

        analyse(RecognitionInput.Text("un bol de riz")) { annonce++ }

        assertEquals(1, annonce)
    }

    /** Deux refus de suite remontent l'issue : l'information utile devient *reviens plus tard*. */
    @Test
    fun `deux refus de suite remontent l issue`() = runTest {
        outcome = RecognitionOutcome.Failed(AiError.ProviderBusy)

        val result = analyse(RecognitionInput.Text("un bol de riz"))

        assertEquals(RecognitionOutcome.Failed(AiError.ProviderBusy), result)
        assertNull(pending.take())
    }

    /**
     * Les autres echecs **ne repartent pas**.
     *
     * Une cle refusee le restera, un reseau absent aussi : reessayer coutait une attente
     * pour echouer deux fois de la meme facon.
     */
    @Test
    fun `une cle refusee ne se reessaie pas`() = runTest {
        outcome = RecognitionOutcome.Failed(AiError.InvalidKey)

        analyse(RecognitionInput.Text("un bol de riz"))

        assertEquals(1, calls)
    }

    private companion object {
        val RIZ = RecognizedItem(label = "riz", quantity = 1.0, unit = EstimatedUnit.BOWL, confidence = 0.9f)
        val JPEG = byteArrayOf(1, 2, 3)
    }
}
