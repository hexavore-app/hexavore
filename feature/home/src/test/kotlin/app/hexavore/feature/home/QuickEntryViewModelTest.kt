package app.hexavore.feature.home

import app.hexavore.core.designsystem.component.SlowAnalysis
import app.hexavore.core.testing.InMemoryDishPhotos
import app.hexavore.core.testing.InMemoryPhotoSettings
import app.hexavore.core.testing.resolutionForTests
import app.hexavore.domain.ai.AiConfiguration
import app.hexavore.domain.ai.AiError
import app.hexavore.domain.ai.AiProvider
import app.hexavore.domain.ai.AiSettings
import app.hexavore.domain.ai.ApiKey
import app.hexavore.domain.ai.EstimatedUnit
import app.hexavore.domain.ai.EstimationOutcome
import app.hexavore.domain.ai.FoodRecognizer
import app.hexavore.domain.ai.InMemoryPendingProposal
import app.hexavore.domain.ai.Recognition
import app.hexavore.domain.ai.RecognitionInput
import app.hexavore.domain.ai.RecognitionOutcome
import app.hexavore.domain.ai.RecognizedItem
import app.hexavore.domain.ai.SLOW_ANALYSIS_MILLIS
import app.hexavore.domain.diary.EntrySource
import app.hexavore.domain.usecase.AnalyseMeal
import app.hexavore.domain.usecase.StageDishPhoto
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * La barre du bas, sans écran.
 *
 * Ce qui s'éprouve ici est ce que l'envoi direct invente ([D131][decisions]) : une
 * phrase part **sans écran intermédiaire**, donc sans rien pour rattraper une erreur
 * de trajet. Le blanc qui ne part pas, le double appui qui ne paie pas deux fois,
 * l'annulation qui coupe vraiment et l'échec qui ne dépose rien sont les quatre cas où
 * l'absence d'écran coûterait quelque chose — de l'argent, ou un plat fantôme.
 *
 * [decisions]: docs/11-decisions.md
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal class QuickEntryViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val pending = InMemoryPendingProposal()
    private val sent = mutableListOf<RecognitionInput>()
    private var outcome: RecognitionOutcome = RecognitionOutcome.Recognized(Recognition(listOf(RIZ)))

    private val recognizer = FoodRecognizer { input ->
        sent += input
        outcome
    }

    private val photos = InMemoryDishPhotos()
    private val stagePhoto = StageDishPhoto(photos, InMemoryPhotoSettings())

    @BeforeEach
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterEach
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `la phrase part rognee, et la proposition est deposee`() = runTest {
        val viewModel = viewModel()

        viewModel.onSend("  un bol de riz et deux oeufs  ")
        advanceUntilIdle()

        assertEquals(listOf("un bol de riz et deux oeufs"), sent.map { (it as RecognitionInput.Text).description })
        assertTrue(viewModel.uiState.value.proposed)
    }

    @Test
    fun `une phrase notee vient du texte, jamais de la photo`() = runTest {
        // La source est un fait sur le plat (D32) : l'accueil ne la declare pas, elle
        // se deduit de ce qui est parti.
        val viewModel = viewModel()

        viewModel.onSend("deux oeufs")
        advanceUntilIdle()

        assertEquals(EntrySource.TEXT_AI, pending.take()?.source)
    }

    @Test
    fun `une phrase seule ecarte la photo qui trainait`() = runTest {
        // Le depot n'a qu'un emplacement, et l'ecran de validation le lit sans savoir
        // laquelle des deux analyses l'a rempli : une assiette photographiee puis
        // abandonnee ne doit pas s'attacher au repas qu'on vient de decrire.
        photos.stage(byteArrayOf(1, 2, 3))
        val viewModel = viewModel()

        viewModel.onSend("deux oeufs")
        advanceUntilIdle()

        assertNull(photos.staged())
    }

    @Test
    fun `le blanc ne part pas`() = runTest {
        // La touche d'envoi du clavier arrive par le meme chemin que le bouton, et elle
        // ne consulte pas l'etat d'un bouton grise.
        val viewModel = viewModel()

        viewModel.onSend("   ")
        advanceUntilIdle()

        assertTrue(sent.isEmpty())
        assertFalse(viewModel.uiState.value.analysing)
    }

    @Test
    fun `un second appui pendant l analyse ne repaie pas la meme demande`() = runTest {
        // Chaque appel se paie : un double tap acheterait deux fois le meme repas.
        val viewModel = viewModel(
            FoodRecognizer {
                sent += it
                awaitCancellation()
            },
        )

        viewModel.onSend("un bol de riz")
        viewModel.onSend("un bol de riz")

        assertEquals(1, sent.size)
    }

    @Test
    fun `annuler coupe l appel en vol`() = runTest {
        // Une requete abandonnee qu'on laisse courir se paie quand meme.
        var coupe = false
        val viewModel = viewModel(
            FoodRecognizer {
                try {
                    awaitCancellation()
                } finally {
                    coupe = true
                }
            },
        )
        viewModel.onSend("un bol de riz")

        viewModel.onCancel()
        advanceUntilIdle()

        assertTrue(coupe)
        assertFalse(viewModel.uiState.value.analysing)
    }

    @Test
    fun `un echec ne depose rien, et se dit`() = runTest {
        outcome = RecognitionOutcome.Failed(AiError.NoNetwork)
        val viewModel = viewModel()

        viewModel.onSend("un bol de riz")
        advanceUntilIdle()

        assertNull(pending.take())
        assertFalse(viewModel.uiState.value.proposed)
        assertNotNull(viewModel.uiState.value.error)
    }

    @Test
    fun `relancer efface l echec precedent`() = runTest {
        outcome = RecognitionOutcome.Failed(AiError.NoNetwork)
        val viewModel = viewModel()
        viewModel.onSend("un bol de riz")
        advanceUntilIdle()
        outcome = RecognitionOutcome.Recognized(Recognition(listOf(RIZ)))

        viewModel.onSend("un bol de riz")
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.error)
        assertTrue(viewModel.uiState.value.proposed)
    }

    @Test
    fun `le drapeau de navigation se referme derriere lui`() = runTest {
        // Sans quoi revenir de la validation repartirait aussitot vers un depot vide.
        val viewModel = viewModel()
        viewModel.onSend("un bol de riz")
        advanceUntilIdle()

        viewModel.onNavigated()

        assertFalse(viewModel.uiState.value.proposed)
    }

    @Test
    fun `l estimation se dit sur la barre, puis la validation s ouvre`() = runTest {
        // La meme phrase que l'ecran d'IA : l'estimation s'attendait derriere l'ecran de
        // validation, noir le temps qu'elle revienne (D176).
        val reponse = CompletableDeferred<EstimationOutcome>()
        val viewModel = QuickEntryViewModel(
            AnalyseMeal(recognizer, pending, stagePhoto, resolutionForTests(estimate = { reponse.await() })),
            GEMINI,
        )

        viewModel.onSend("un bol de riz")

        assertTrue(viewModel.uiState.value.estimating, "l'attente de l'estimation doit se dire")
        assertFalse(viewModel.uiState.value.proposed, "la validation ne s'ouvre pas avant d'avoir de quoi s'ouvrir")

        reponse.complete(EstimationOutcome.Estimated(emptyList()))
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.estimating)
        assertTrue(viewModel.uiState.value.proposed)
    }

    @Test
    fun `passe dix secondes, la barre nomme le fournisseur qui fait attendre`() = runTest {
        // La meme note que l'ecran d'IA, sous la ligne d'attente (D177).
        val viewModel = viewModel(recognizer = { awaitCancellation() })
        viewModel.onSend("un bol de riz")

        advanceTimeBy(SLOW_ANALYSIS_MILLIS + 1)

        assertEquals(SlowAnalysis(provider = "Google Gemini", free = true), viewModel.uiState.value.slow)

        viewModel.onCancel()

        assertNull(viewModel.uiState.value.slow, "la note retombe avec l'attente")
    }

    private fun viewModel(recognizer: FoodRecognizer = this.recognizer) =
        QuickEntryViewModel(AnalyseMeal(recognizer, pending, stagePhoto, resolutionForTests()), GEMINI)

    private companion object {
        val RIZ = RecognizedItem(label = "riz", quantity = 1.0, unit = EstimatedUnit.BOWL, confidence = 0.9f)

        /** Le fournisseur gratuit, celui que la note nomme et dont elle dit la gratuité (D177). */
        val GEMINI = AiSettings {
            AiConfiguration(
                provider = AiProvider.GEMINI,
                apiKey = ApiKey("cle-gemini-de-test"),
                model = "gemini-3.5-flash-lite",
                baseUrl = "https://generativelanguage.googleapis.com/",
            )
        }
    }
}
