package app.hexavore.feature.capture

import app.hexavore.domain.ai.AiConfiguration
import app.hexavore.domain.ai.AiError
import app.hexavore.domain.ai.AiProvider
import app.hexavore.domain.ai.AiSettings
import app.hexavore.domain.ai.ApiKey
import app.hexavore.domain.ai.EstimatedUnit
import app.hexavore.domain.ai.FoodRecognizer
import app.hexavore.domain.ai.InMemoryPendingRecognition
import app.hexavore.domain.ai.PhotoConsent
import app.hexavore.domain.ai.Recognition
import app.hexavore.domain.ai.RecognitionInput
import app.hexavore.domain.ai.RecognitionOutcome
import app.hexavore.domain.ai.RecognizedItem
import app.hexavore.domain.diary.EntrySource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * L'écran d'IA, sans appareil.
 *
 * Il ne voit ni caméra, ni galerie, ni `Uri` : l'écran lui remet un JPEG déjà réduit.
 * C'est ce qui rend éprouvable **tout ce qui coûte de l'argent ou expose une donnée** —
 * le consentement, l'annulation, ce qui est déposé et ce qui survit à un échec — alors
 * que la prise de vue elle-même ne s'éprouve qu'en tenant le téléphone.
 *
 * **Ces cas viennent de deux fichiers**, ceux des deux modales fusionnées
 * ([D120][decisions]). Ce qui s'y ajoute est la seule chose que la fusion invente : ce
 * qui décide **ce qui part** — une photo, une phrase, ou une photo et sa précision.
 *
 * [decisions]: docs/11-decisions.md
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal class AnalyseViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val pending = InMemoryPendingRecognition()
    private val consent = RecordingConsent()
    private val sent = mutableListOf<RecognitionInput>()
    private var outcome: RecognitionOutcome = RecognitionOutcome.Recognized(Recognition(listOf(RIZ)))

    private val recognizer = FoodRecognizer { input ->
        sent += input
        outcome
    }

    @BeforeEach
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterEach
    fun tearDown() = Dispatchers.resetMain()

    // --- Ce qui part ------------------------------------------------------------

    @Test
    fun `sans photo, c est la phrase qui part`() = runTest {
        val viewModel = viewModel()
        viewModel.onText("  un bol de riz et deux oeufs  ")

        viewModel.onAnalyse()
        advanceUntilIdle()

        assertEquals(RecognitionInput.Text("un bol de riz et deux oeufs"), sent.single())
        assertEquals(EntrySource.TEXT_AI, pending.take()?.source)
    }

    @Test
    fun `avec photo, la phrase devient la precision`() = runTest {
        // Le levier de justesse le moins couteux qui existe : un modele voit mal les
        // quantites sans reference d'echelle.
        consent.given = true
        val viewModel = viewModel()
        viewModel.onPhoto(JPEG)
        viewModel.onText("  l assiette fait 24 cm  ")

        viewModel.onAnalyse()
        advanceUntilIdle()

        val photo = sent.single() as RecognitionInput.Photo
        assertEquals("l assiette fait 24 cm", photo.note)
        assertEquals(EntrySource.PHOTO_AI, pending.take()?.source)
    }

    @Test
    fun `une precision vide n est pas une precision`() = runTest {
        // Une chaine vide jointe au prompt ferait deux lignes vides dans la demande,
        // pour dire qu'il n'y a rien a dire.
        consent.given = true
        val viewModel = viewModel()
        viewModel.onPhoto(JPEG)
        viewModel.onText("   ")

        viewModel.onAnalyse()
        advanceUntilIdle()

        assertNull((sent.single() as RecognitionInput.Photo).note)
    }

    @Test
    fun `sans photo ni phrase, rien ne part`() = runTest {
        consent.given = true
        val viewModel = viewModel()

        viewModel.onAnalyse()
        advanceUntilIdle()

        assertEquals(emptyList<RecognitionInput>(), sent)
    }

    @Test
    fun `retirer la photo rend la main au texte`() = runTest {
        // Une photo mal cadree qu'on remplace par une phrase, sans quitter l'ecran.
        consent.given = true
        val viewModel = viewModel()
        viewModel.onPhoto(JPEG)
        viewModel.onText("un bol de riz")

        viewModel.onRemovePhoto()
        viewModel.onAnalyse()
        advanceUntilIdle()

        assertEquals(RecognitionInput.Text("un bol de riz"), sent.single())
        assertEquals(EntrySource.TEXT_AI, pending.take()?.source)
    }

    // --- Le consentement --------------------------------------------------------

    @Test
    fun `rien ne part avant que l avertissement soit accepte`() = runTest {
        // La regle de docs/05 : le mode photo envoie une image de son repas a un tiers,
        // et ca se dit avant, une fois. Un envoi qui precede l'accord rendrait
        // l'avertissement decoratif.
        val viewModel = viewModel()
        viewModel.onPhoto(JPEG)

        viewModel.onAnalyse()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.consentNeeded)
        assertEquals(emptyList<RecognitionInput>(), sent)
        assertNull(pending.take())
    }

    @Test
    fun `une phrase part sans avertissement`() = runTest {
        // Celui qui ecrit sait exactement ce qu'il envoie ; une image emporte aussi ce
        // qui entoure l'assiette. L'accord porte sur la photo, pas sur l'IA.
        val viewModel = viewModel()
        viewModel.onText("un bol de riz")

        viewModel.onAnalyse()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.consentNeeded)
        assertEquals(1, sent.size)
        assertFalse(consent.given, "rien n'a ete accepte, donc rien n'a ete enregistre")
    }

    @Test
    fun `l avertissement nomme le fournisseur`() = runTest {
        // « votre photo part chez Anthropic » se verifie ; « chez votre fournisseur »
        // ne se verifie pas.
        val viewModel = viewModel()
        viewModel.onPhoto(JPEG)

        viewModel.onAnalyse()
        advanceUntilIdle()

        assertEquals("Anthropic", viewModel.uiState.value.provider)
    }

    @Test
    fun `accepter enregistre l accord et envoie dans la foulee`() = runTest {
        val viewModel = viewModel()
        viewModel.onPhoto(JPEG)
        viewModel.onAnalyse()
        advanceUntilIdle()

        viewModel.onConsent()
        advanceUntilIdle()

        assertTrue(consent.given, "l accord doit survivre a la fermeture de l ecran")
        assertTrue(viewModel.uiState.value.analysed)
        assertEquals(EntrySource.PHOTO_AI, pending.take()?.source)
    }

    @Test
    fun `un accord deja donne ne se redemande pas`() = runTest {
        consent.given = true
        val viewModel = viewModel()
        viewModel.onPhoto(JPEG)

        viewModel.onAnalyse()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.consentNeeded, "un avertissement repete n est plus lu")
        assertEquals(1, sent.size)
    }

    @Test
    fun `refuser garde la photo et n envoie rien`() = runTest {
        val viewModel = viewModel()
        viewModel.onPhoto(JPEG)
        viewModel.onAnalyse()
        advanceUntilIdle()

        viewModel.onConsentDeclined()

        assertFalse(viewModel.uiState.value.consentNeeded)
        assertEquals(emptyList<RecognitionInput>(), sent)
        assertTrue(viewModel.uiState.value.photo != null, "changer d avis ne doit pas reprendre la photo")
    }

    // --- Les échecs -------------------------------------------------------------

    @Test
    fun `un echec garde la photo et la phrase`() = runTest {
        // docs/02 : ce qui a ete saisi est conserve le temps de proposer Reessayer.
        // Sans ca, un reseau absent obligerait a ressortir le telephone au-dessus
        // d'une assiette qu'on est peut-etre en train de manger.
        consent.given = true
        outcome = RecognitionOutcome.Failed(AiError.NoNetwork)
        val viewModel = viewModel()
        viewModel.onPhoto(JPEG)
        viewModel.onText("l assiette fait 24 cm")

        viewModel.onAnalyse()
        advanceUntilIdle()

        assertEquals(AiError.NoNetwork, viewModel.uiState.value.error)
        assertTrue(viewModel.uiState.value.photo != null)
        assertEquals("l assiette fait 24 cm", viewModel.uiState.value.text)
        assertNull(pending.take(), "un echec ne depose rien")
    }

    @Test
    fun `une nouvelle photo efface l echec de la precedente`() = runTest {
        consent.given = true
        outcome = RecognitionOutcome.Failed(AiError.NoNetwork)
        val viewModel = viewModel()
        viewModel.onPhoto(JPEG)
        viewModel.onAnalyse()
        advanceUntilIdle()

        viewModel.onPhoto(byteArrayOf(9, 9, 9))

        assertNull(viewModel.uiState.value.error, "ce qui s affichait ne se rapporte plus a ce qu on regarde")
    }

    @Test
    fun `relancer efface l erreur precedente`() = runTest {
        outcome = RecognitionOutcome.Failed(AiError.NoNetwork)
        val viewModel = viewModel()
        viewModel.onText("un bol de riz")
        viewModel.onAnalyse()
        advanceUntilIdle()
        outcome = RecognitionOutcome.Recognized(Recognition(listOf(RIZ)))

        viewModel.onAnalyse()
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.error)
        assertTrue(viewModel.uiState.value.analysed)
    }

    @Test
    fun `un second appui pendant l analyse ne repaie pas la meme demande`() = runTest {
        // Chaque appel se paie : un double tap acheterait deux fois la meme assiette.
        val viewModel = AnalyseViewModel(
            recognizer = {
                sent += it
                awaitCancellation()
            },
            pending = pending,
            consent = consent,
            settings = SETTINGS,
        )
        viewModel.onText("un bol de riz")

        viewModel.onAnalyse()
        viewModel.onAnalyse()

        assertEquals(1, sent.size)
    }

    @Test
    fun `annuler coupe l appel en vol`() = runTest {
        // docs/02 l'ecrit noir sur blanc, et c'est une question d'argent : une requete
        // abandonnee qu'on laisse courir se paie quand meme.
        // Ce qui se mesure est **l'appel**, et non l'etat du bouton : un ecran qui
        // cesse d'afficher « analyse en cours » pendant que la requete continue a
        // exactement la meme apparence, et se paie pareil.
        consent.given = true
        var coupe = false
        val viewModel = AnalyseViewModel(
            recognizer = {
                try {
                    awaitCancellation()
                } finally {
                    coupe = true
                }
            },
            pending = pending,
            consent = consent,
            settings = SETTINGS,
        )
        viewModel.onPhoto(JPEG)
        viewModel.onAnalyse()

        viewModel.onCancel()

        assertTrue(coupe, "la requete en vol doit etre coupee, pas seulement masquee")
        assertFalse(viewModel.uiState.value.analysing)
    }

    @Test
    fun `revenir sur l ecran ne repart pas vers une validation vide`() = runTest {
        val viewModel = viewModel()
        viewModel.onText("un bol de riz")
        viewModel.onAnalyse()
        advanceUntilIdle()

        viewModel.onNavigated()

        assertFalse(viewModel.uiState.value.analysed)
    }

    // --- Décor ------------------------------------------------------------------

    private fun viewModel() = AnalyseViewModel(
        recognizer = recognizer,
        pending = pending,
        consent = consent,
        settings = SETTINGS,
    )

    /**
     * Un consentement qui se souvient, comme le vrai — sans fichier.
     *
     * Le champ ne s'appelle pas `accepted` : Kotlin distingue la propriété de la
     * fonction du même nom, mais un lecteur, non.
     */
    private class RecordingConsent(var given: Boolean = false) : PhotoConsent {
        override suspend fun accepted(): Boolean = given

        override suspend fun accept() {
            given = true
        }
    }

    private companion object {
        val JPEG = byteArrayOf(1, 2, 3)
        val RIZ = RecognizedItem(label = "riz", quantity = 1.0, unit = EstimatedUnit.BOWL, confidence = 0.9f)

        val SETTINGS = AiSettings {
            AiConfiguration(
                provider = AiProvider.ANTHROPIC,
                apiKey = ApiKey("sk-ant-de-test"),
                model = "claude-opus-5",
                baseUrl = "https://api.anthropic.com/",
            )
        }
    }
}
