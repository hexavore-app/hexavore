package app.hexavore.domain.usecase

import app.hexavore.domain.ai.FoodRecognizer
import app.hexavore.domain.ai.PendingRecognition
import app.hexavore.domain.ai.RecognitionInput
import app.hexavore.domain.ai.RecognitionOutcome
import app.hexavore.domain.diary.EntrySource

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
     * @return l'issue telle que le port la rend. Le succès a déjà été déposé quand
     *   elle revient ; l'appelant n'a plus qu'à céder la place.
     */
    suspend operator fun invoke(input: RecognitionInput): RecognitionOutcome {
        val outcome = recognizer.recognize(input)
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

/** D'où vient le plat, du seul fait de ce qu'on a envoyé. */
private fun RecognitionInput.source(): EntrySource = when (this) {
    is RecognitionInput.Photo -> EntrySource.PHOTO_AI
    is RecognitionInput.Text -> EntrySource.TEXT_AI
}
