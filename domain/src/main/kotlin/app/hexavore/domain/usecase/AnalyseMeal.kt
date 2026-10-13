package app.hexavore.domain.usecase

import app.hexavore.domain.ai.AiError
import app.hexavore.domain.ai.BUSY_RETRY_DELAY_MILLIS
import app.hexavore.domain.ai.FoodRecognizer
import app.hexavore.domain.ai.PendingRecognition
import app.hexavore.domain.ai.RecognitionInput
import app.hexavore.domain.ai.RecognitionOutcome
import app.hexavore.domain.diary.EntrySource
import kotlinx.coroutines.delay

/**
 * Soumet un repas au modèle, et dépose ce qu'il propose.
 *
 * **Sorti de l'écran d'IA le jour où un second endroit a eu la même chose à faire**
 * ([D131][decisions]) : la barre du bas envoie une phrase sans ouvrir d'écran, et
 * recopier ces quinze lignes aurait donné deux chemins d'analyse — donc deux sources
 * possibles, deux traitements de la photo, et un jour deux comportements.
 *
 * **Trois gestes, dans cet ordre, et l'ordre compte.** La photo est rangée *avant* le
 * dépôt de la proposition : l'écran de validation lit les deux, et les inverser lui
 * ferait voir une proposition sans son image le temps d'une recomposition.
 *
 * **La source se déduit de l'entrée**, elle ne se demande pas. Une photo donne
 * `PHOTO_AI`, une phrase `TEXT_AI` ; laisser l'appelant la nommer aurait permis à un
 * écran de se tromper, et [D32][decisions] veut que l'origine d'un plat soit un fait,
 * pas une déclaration.
 *
 * **Rien n'est déposé quand l'analyse échoue.** L'appelant garde ce qu'il avait —
 * la phrase, la photo — et c'est ce que [docs/02][parcours] exige : une clé refusée ne
 * doit jamais obliger à retaper.
 *
 * [parcours]: docs/02-parcours-et-ecrans.md
 * [decisions]: docs/11-decisions.md
 */
class AnalyseMeal(
    private val recognizer: FoodRecognizer,
    private val pending: PendingRecognition,
    private val stagePhoto: StageDishPhoto,
) {
    /**
     * @param onBusy appelé quand le fournisseur s'est annoncé débordé et qu'une seconde
     *   tentative part. L'écran s'en sert pour le dire — « service saturé, nouvelle
     *   tentative » — et c'est la seule raison pour laquelle la relance est ici plutôt
     *   que dans un intercepteur HTTP, où elle serait invisible ([D156][decisions]).
     *
     *   Vide par défaut : la barre du bas analyse sans écran d'attente à renseigner.
     * @return l'issue telle que le port la rend. Le succès a déjà été déposé quand
     *   elle revient ; l'appelant n'a plus qu'à céder la place.
     */
    suspend operator fun invoke(input: RecognitionInput, onBusy: suspend () -> Unit = {}): RecognitionOutcome {
        val outcome = recognizer.recognizeRetryingWhenBusy(input, onBusy)
        if (outcome !is RecognitionOutcome.Recognized) return outcome

        // Une description seule ecarte la photo qui trainait : le depot n'a qu'un
        // emplacement, et l'ecran de validation le lit sans savoir laquelle des deux
        // analyses l'a rempli. L'echec de ce rangement n'annule pas l'analyse -- elle
        // est payee, et une image manquante se corrige a l'ecran suivant.
        runCatching { stagePhoto((input as? RecognitionInput.Photo)?.jpeg) }
        pending.offer(outcome.recognition, input.source())

        return outcome
    }
}

/**
 * Un `503` ne se montre pas sans avoir été réessayé une fois.
 *
 * ### Pourquoi l'application attend à la place de l'utilisateur
 *
 * Les modèles gratuits les plus demandés — `gemini-3.5-flash-lite` en tête — se
 * déclarent débordés aux heures de repas, c'est-à-dire exactement quand on photographie
 * une assiette. L'embouteillage dure quelques secondes. Afficher une erreur et attendre
 * un second appui demande à quelqu'un de refaire un geste que la machine peut refaire
 * seule, et lui laisse croire que l'application est en panne ([D156][decisions]).
 *
 * ### Une seule relance, et elle s'annonce
 *
 * **Une** parce que deux font attendre une quinzaine de secondes pour un service qui,
 * passé le premier échec, est probablement saturé plus que momentanément — et parce
 * qu'au deuxième refus, l'information utile est *reviens plus tard*, pas *patiente
 * encore*.
 *
 * **Elle s'annonce** par [onBusy] : une attente qu'on n'explique pas se lit comme une
 * lenteur de l'application, alors que la cause est chez le fournisseur et que la dire
 * est la moitié de ce que [docs/05][ia] demande d'un message d'erreur.
 *
 * ### Ce que la relance ne coûte pas
 *
 * **Rien.** Un `503` n'est pas facturé — le fournisseur n'a rien produit —, et c'est ce
 * qui distingue ce cas de [AiError.QuotaExceeded], où repartir se paierait et échouerait
 * pareil.
 *
 * [ia]: docs/05-ia.md
 * [decisions]: docs/11-decisions.md
 */
private suspend fun FoodRecognizer.recognizeRetryingWhenBusy(
    input: RecognitionInput,
    onBusy: suspend () -> Unit,
): RecognitionOutcome {
    val first = recognize(input)
    if (!first.busy()) return first

    onBusy()
    delay(BUSY_RETRY_DELAY_MILLIS)
    return recognize(input)
}

private fun RecognitionOutcome.busy(): Boolean = this is RecognitionOutcome.Failed && error == AiError.ProviderBusy

/** D'où vient le plat, du seul fait de ce qu'on a envoyé. */
private fun RecognitionInput.source(): EntrySource = when (this) {
    is RecognitionInput.Photo -> EntrySource.PHOTO_AI
    is RecognitionInput.Text -> EntrySource.TEXT_AI
}
