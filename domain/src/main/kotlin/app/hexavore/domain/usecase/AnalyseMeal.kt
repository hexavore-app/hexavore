package app.hexavore.domain.usecase

import app.hexavore.domain.ai.AiError
import app.hexavore.domain.ai.BUSY_RETRY_DELAY_MILLIS
import app.hexavore.domain.ai.FoodRecognizer
import app.hexavore.domain.ai.PendingProposal
import app.hexavore.domain.ai.RecognitionInput
import app.hexavore.domain.ai.RecognitionOutcome
import app.hexavore.domain.diary.EntrySource
import kotlinx.coroutines.delay

/**
 * Soumet un repas au modèle, résout ce qu'il propose, et dépose le brouillon.
 *
 * **Sorti de l'écran d'IA le jour où un second endroit a eu la même chose à faire**
 * ([D131][decisions]) : la barre du bas envoie une phrase sans ouvrir d'écran, et
 * recopier ces quinze lignes aurait donné deux chemins d'analyse — donc deux sources
 * possibles, deux traitements de la photo, et un jour deux comportements.
 *
 * **La résolution est ici, et non plus à l'ouverture de l'écran de validation**
 * ([D176][decisions]). Elle peut demander un second appel au fournisseur — l'étape 4,
 * qui estime ce que le catalogue ne connaît pas —, et cet appel se faisait derrière un
 * écran vide : jusqu'à une minute de noir, d'autant plus longue que le fournisseur était
 * lent, puisque c'était le même. Ici, il attend sous l'indicateur qui attendait déjà la
 * reconnaissance, et le même bouton l'annule.
 *
 * **Quatre gestes, dans cet ordre, et l'ordre compte.** La reconnaissance, la résolution,
 * la photo, le dépôt. La photo est rangée *avant* le dépôt : l'écran de validation lit
 * les deux, et les inverser lui ferait voir une proposition sans son image le temps
 * d'une recomposition. Et rien n'est rangé tant que la résolution peut encore être
 * annulée.
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
    private val pending: PendingProposal,
    private val stagePhoto: StageDishPhoto,
    private val resolve: ResolveRecognition,
) {
    /**
     * @param progress ce que l'écran qui attend doit pouvoir dire : une seconde tentative
     *   qui part ([D156][decisions]), une estimation qui commence ([D176][decisions]).
     *   Muet par défaut.
     * @return l'issue telle que le port la rend. Le succès a déjà été résolu et déposé
     *   quand elle revient ; l'appelant n'a plus qu'à céder la place.
     */
    suspend operator fun invoke(
        input: RecognitionInput,
        progress: AnalysisProgress = AnalysisProgress.Silent,
    ): RecognitionOutcome {
        val outcome = recognizer.recognizeRetryingWhenBusy(input, progress)
        if (outcome !is RecognitionOutcome.Recognized) return outcome

        val draft = resolve(outcome.recognition, input.source(), onEstimating = progress::estimating)

        // Une description seule ecarte la photo qui trainait : le depot n'a qu'un
        // emplacement, et l'ecran de validation le lit sans savoir laquelle des deux
        // analyses l'a rempli. L'echec de ce rangement n'annule pas l'analyse -- elle
        // est payee, et une image manquante se corrige a l'ecran suivant.
        runCatching { stagePhoto((input as? RecognitionInput.Photo)?.jpeg) }
        pending.offer(draft)

        return outcome
    }
}

/**
 * Ce que l'analyse annonce à l'écran qui l'attend.
 *
 * **Une interface plutôt qu'un rappel par étape.** L'analyse a deux choses à dire en
 * cours de route, et deux paramètres fonctionnels côte à côte se confondent à l'appel ;
 * un objet qui les nomme ne se trompe pas de rôle. Chaque étape est muette par défaut :
 * la barre du bas et les tests n'implémentent que ce qu'ils affichent.
 *
 * **Ce qui change est la raison d'attendre, pas le fait d'attendre** ([D156][decisions]) :
 * l'écran garde son indicateur et change sa phrase.
 *
 * [decisions]: docs/11-decisions.md
 */
interface AnalysisProgress {
    /** Le fournisseur s'est annoncé débordé, et une seconde tentative part ([D156][decisions]). */
    suspend fun retrying() = Unit

    /**
     * Le modèle a répondu, et l'étape 4 part : il estime ce que le catalogue ne connaît
     * pas ([D176][decisions]). Jamais appelé quand tout a été résolu — le cas courant.
     */
    suspend fun estimating() = Unit

    /** Rien à dire. */
    object Silent : AnalysisProgress
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
 * **Elle s'annonce** par [AnalysisProgress.retrying] : une attente qu'on n'explique pas
 * se lit comme une lenteur de l'application, alors que la cause est chez le fournisseur
 * et que la dire est la moitié de ce que [docs/05][ia] demande d'un message d'erreur.
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
    progress: AnalysisProgress,
): RecognitionOutcome {
    val first = recognize(input)
    if (!first.busy()) return first

    progress.retrying()
    delay(BUSY_RETRY_DELAY_MILLIS)
    return recognize(input)
}

private fun RecognitionOutcome.busy(): Boolean = this is RecognitionOutcome.Failed && error == AiError.ProviderBusy

/** D'où vient le plat, du seul fait de ce qu'on a envoyé. */
private fun RecognitionInput.source(): EntrySource = when (this) {
    is RecognitionInput.Photo -> EntrySource.PHOTO_AI
    is RecognitionInput.Text -> EntrySource.TEXT_AI
}
