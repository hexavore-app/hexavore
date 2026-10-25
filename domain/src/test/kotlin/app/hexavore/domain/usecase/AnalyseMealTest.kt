package app.hexavore.domain.usecase

import app.hexavore.core.testing.FixedClock
import app.hexavore.core.testing.InMemoryDishPhotos
import app.hexavore.core.testing.InMemoryFoodCatalog
import app.hexavore.core.testing.InMemoryPhotoSettings
import app.hexavore.core.testing.InMemorySelectedDay
import app.hexavore.core.testing.SequentialIdGenerator
import app.hexavore.domain.ai.AiError
import app.hexavore.domain.ai.EstimatedFood
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
import app.hexavore.domain.nutrition.NutrientValues
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.time.LocalDate

/**
 * L'analyse, telle que les deux écrans qui la déclenchent la partagent.
 *
 * Ce qui s'éprouve ici est ce que le cas d'usage décide **à leur place** : la source du
 * plat, le sort de la photo, et le fait que rien ne soit déposé quand le modèle échoue.
 * Ces trois règles vivaient dans l'écran d'IA tant qu'il était seul à analyser ; la
 * barre du bas en a fait des règles du domaine ([D131][decisions]).
 *
 * **Et ce qui est déposé est un brouillon résolu** ([D176][decisions]) : l'estimation de
 * ce que le catalogue ne connaît pas se fait ici, sous l'indicateur qui attend déjà, et
 * non plus derrière un écran de validation resté noir.
 *
 * [decisions]: docs/11-decisions.md
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal class AnalyseMealTest {
    private val pending = InMemoryPendingProposal()
    private val photos = InMemoryDishPhotos()
    private val keeping = InMemoryPhotoSettings()
    private var outcome: RecognitionOutcome = RecognitionOutcome.Recognized(Recognition(listOf(RIZ)))

    /** Combien de fois le port a ete appele : c'est ce que la relance change. */
    private var calls = 0

    /** Ce que le fournisseur met a repondre, en temps virtuel : zero par defaut. */
    private var latence = 0L

    /** Ce que l'etape 4 repondra. Rien par defaut : la plupart des cas n'en parlent pas. */
    private var repondre: suspend (List<String>) -> EstimationOutcome = { EstimationOutcome.Estimated(emptyList()) }

    private val analyse = AnalyseMeal(
        recognizer = FoodRecognizer {
            calls++
            delay(latence)
            outcome
        },
        pending = pending,
        stagePhoto = StageDishPhoto(photos, keeping),
        resolve = ResolveRecognition(
            resolve = ResolveFoodLabel(InMemoryFoodCatalog(), FRANCAIS),
            create = CreateDraft(FixedClock.atNoon(JOUR), SequentialIdGenerator(), InMemorySelectedDay(JOUR)),
            estimate = { labels -> repondre(labels) },
            languages = FRANCAIS,
        ),
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
     * L'étape 4 se fait **avant le dépôt**, et ce qui est déposé porte ses valeurs (D176).
     *
     * C'est la règle qui fait disparaître l'écran noir : l'écran de validation n'a plus
     * rien à demander au fournisseur, il ouvre un brouillon déjà complet.
     */
    @Test
    fun `la proposition est deposee deja resolue, estimation comprise`() = runTest {
        outcome = RecognitionOutcome.Recognized(Recognition(listOf(TOFU)))
        repondre = { EstimationOutcome.Estimated(listOf(EstimatedFood("tofu fume", NutrientValues(kcal = 180.0)))) }

        analyse(RecognitionInput.Text("du tofu fume"))

        assertEquals(180.0, pending.take()?.lines?.single()?.values?.kcal)
    }

    /** L'estimation s'annonce : c'est elle qu'on attend, et l'écran le dit (D176). */
    @Test
    fun `l estimation s annonce a l ecran qui attend`() = runTest {
        outcome = RecognitionOutcome.Recognized(Recognition(listOf(TOFU)))
        var annonces = 0

        analyse(
            RecognitionInput.Text("du tofu fume"),
            object : AnalysisProgress {
                override suspend fun estimating() {
                    annonces++
                }
            },
        )

        assertEquals(1, annonces)
    }

    /**
     * Annuler pendant l'estimation **ne dépose rien**, comme annuler la reconnaissance.
     *
     * Le bouton « Annuler » couvre désormais toute l'attente : un plat déposé après
     * qu'on y a renoncé ressurgirait à la prochaine ouverture de la validation.
     */
    @Test
    fun `annuler pendant l estimation ne depose rien`() = runTest {
        outcome = RecognitionOutcome.Recognized(Recognition(listOf(TOFU)))
        repondre = { awaitCancellation() }

        val enCours = launch { analyse(RecognitionInput.Text("du tofu fume")) }
        runCurrent()
        enCours.cancel()
        runCurrent()

        assertNull(pending.take())
    }

    /**
     * Passé dix secondes, l'attente **s'annonce, une fois** (D177).
     *
     * L'horloge virtuelle de `runTest` saute l'attente : ce qui s'éprouve est le seuil et
     * le compte, pas trente secondes.
     */
    @Test
    fun `passe dix secondes, l attente s annonce une fois`() = runTest {
        latence = SLOW_ANALYSIS_MILLIS * 3
        val lenteurs = compteur()

        analyse(RecognitionInput.Text("un bol de riz"), lenteurs)

        assertEquals(1, lenteurs.fois)
    }

    @Test
    fun `neuf secondes ne s annoncent pas`() = runTest {
        // Le seuil est celui que l'auteur a demande : une analyse lente mais ordinaire
        // n'a pas a s'excuser (D177).
        latence = SLOW_ANALYSIS_MILLIS - 1_000
        val lenteurs = compteur()

        analyse(RecognitionInput.Text("un bol de riz"), lenteurs)

        assertEquals(0, lenteurs.fois)
    }

    @Test
    fun `une analyse finie ne s annonce plus, meme dix secondes plus tard`() = runTest {
        // La surveillance s'arrete avec l'analyse : une note tombee apres coup parlerait
        // d'une attente deja finie, sur un ecran parti ailleurs.
        val lenteurs = compteur()

        analyse(RecognitionInput.Text("un bol de riz"), lenteurs)
        advanceTimeBy(SLOW_ANALYSIS_MILLIS * 2)

        assertEquals(0, lenteurs.fois)
    }

    /** Un écran qui compte les annonces de lenteur, et rien d'autre. */
    private fun compteur() = object : AnalysisProgress {
        var fois = 0

        override suspend fun slow() {
            fois++
        }
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

        analyse(
            RecognitionInput.Text("un bol de riz"),
            object : AnalysisProgress {
                override suspend fun retrying() {
                    annonce++
                }
            },
        )

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
        val JOUR: LocalDate = LocalDate.of(2026, 10, 25)
        val RIZ = RecognizedItem(label = "riz", quantity = 1.0, unit = EstimatedUnit.BOWL, confidence = 0.9f)

        /** Inconnu du catalogue vide de ces cas : il part toujours a l'etape 4. */
        val TOFU = RecognizedItem(label = "tofu fume", quantity = 100.0, unit = EstimatedUnit.G, confidence = 0.9f)
        val JPEG = byteArrayOf(1, 2, 3)
    }
}
