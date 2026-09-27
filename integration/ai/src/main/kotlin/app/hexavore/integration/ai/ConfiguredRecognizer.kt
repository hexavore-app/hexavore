package app.hexavore.integration.ai

import app.hexavore.domain.ai.AiConfiguration
import app.hexavore.domain.ai.AiError
import app.hexavore.domain.ai.AiProbe
import app.hexavore.domain.ai.AiProvider
import app.hexavore.domain.ai.AiSettings
import app.hexavore.domain.ai.AiUsageLog
import app.hexavore.domain.ai.CatalogueTool
import app.hexavore.domain.ai.EstimationOutcome
import app.hexavore.domain.ai.FoodRecognizer
import app.hexavore.domain.ai.NutritionEstimator
import app.hexavore.domain.ai.ProbeOutcome
import app.hexavore.domain.ai.RecognitionInput
import app.hexavore.domain.ai.RecognitionOutcome
import app.hexavore.domain.ai.TokenUsage
import app.hexavore.domain.ai.VisionSupport
import app.hexavore.domain.language.ContentLanguage
import app.hexavore.domain.language.ContentLanguages

/**
 * La fabrique : le seul endroit du projet qui sache qu'il existe plusieurs
 * fournisseurs.
 *
 * L'application n'injecte que `FoodRecognizer`. Aucun écran, aucun cas d'usage et
 * aucun test n'a de raison de nommer un fournisseur — c'est le critère de fin de la
 * tranche, et [docs/12][plan] nomme le contraire comme le signal que l'abstraction a
 * fui.
 *
 * **Un `when` exhaustif plutôt qu'une carte de liaisons Hilt.** La carte serait plus
 * savante et strictement moins sûre : oublier d'y inscrire un fournisseur donne un
 * plantage à l'exécution, sur l'appareil de quelqu'un. Ici, ajouter une entrée à
 * [AiProvider] sans écrire sa classe **ne compile pas**. La vérification
 * d'exhaustivité de Kotlin fait gratuitement le travail qu'une carte demanderait de
 * se rappeler.
 *
 * **La configuration est relue à chaque appel.** Une clé corrigée s'applique donc à
 * l'analyse suivante sans rien redémarrer, et une analyse déjà partie ne change pas
 * de fournisseur en vol.
 *
 * [plan]: docs/12-plan-de-developpement.md
 */
internal class ConfiguredRecognizer(
    private val settings: AiSettings,
    private val languages: ContentLanguages,
    private val usage: AiUsageLog,
    private val catalogue: CatalogueTool,
    private val providers: ProviderRecognizers,
) : FoodRecognizer,
    NutritionEstimator,
    AiProbe {
    override suspend fun recognize(input: RecognitionInput): RecognitionOutcome {
        val configuration = settings.current() ?: return RecognitionOutcome.Failed(AiError.NoProviderConfigured)
        val provider = configuration.provider.recognizer()
        val outcome = provider.analyse(input, configuration)

        usage.recordIfBilled(configuration, outcome.billing())
        return outcome
    }

    /**
     * L'analyse approfondie **avec repli sur l'ordinaire**.
     *
     * Le repli est la moitié qui compte. Une boucle d'outillage a plus de façons
     * d'échouer qu'un aller-retour — un modèle qui répond en texte, trois tours sans
     * conclure, un fournisseur qui refuse le champ `tools` — et aucune ne justifie de
     * rendre l'utilisateur bredouille alors que le chemin ordinaire, lui, marche.
     *
     * **Le second appel se paie**, et c'est le prix assumé d'un mode qui s'annonce plus
     * lent. Il ne se paie que sur l'échec, c'est-à-dire jamais dans le cas courant.
     *
     * Un échec de **réseau** ne repart pas : réessayer hors ligne coûterait une minute
     * pour échouer deux fois de la même façon.
     */
    private suspend fun ProviderRecognizer.analyse(
        input: RecognitionInput,
        configuration: AiConfiguration,
    ): RecognitionOutcome {
        if (!configuration.deepAnalysis) return recognize(input, configuration)

        val deep = deepRecognize(input, configuration, catalogue)
        return when {
            deep is RecognitionOutcome.Recognized -> deep
            deep is RecognitionOutcome.Failed && deep.error.worthRetrying -> recognize(input, configuration)
            else -> deep
        }
    }

    /**
     * L'étape 4, routée comme le reste.
     *
     * **Sans configuration, aucun appel** — et surtout aucune estimation : une ligne
     * non résolue reste alors à compléter à la main, ce qui est exactement ce qu'elle
     * était avant. Le repli ne doit pas devenir la raison pour laquelle une clé
     * manquante se voit.
     *
     * Une liste vide ne part jamais non plus : c'est le cas courant — tout a été
     * résolu — et il ne coûte rien.
     */
    override suspend fun estimate(labels: List<String>): EstimationOutcome = when {
        labels.isEmpty() -> EstimationOutcome.Estimated(emptyList())
        else ->
            settings
                .current()
                ?.let { configuration ->
                    val outcome = configuration.provider.recognizer().estimate(labels, configuration)
                    usage.recordIfBilled(configuration, outcome.billing())
                    outcome
                }
                ?: EstimationOutcome.Failed(AiError.NoProviderConfigured)
    }

    /**
     * **Le sondage est une vraie reconnaissance**, sur une phrase minuscule.
     *
     * C'est ce qui le rend digne de confiance : il emprunte exactement le chemin
     * qu'empruntera la première photo — même prompt, même schéma, même parseur, même
     * traduction des codes. Un appel spécial, plus léger, aurait pu réussir là où
     * l'analyse échoue, et un bouton « Tester » qui dit oui à tort est pire que pas de
     * bouton du tout.
     *
     * Il utilise la configuration **du formulaire** et non celle qui est enregistrée :
     * tester après avoir écrit reviendrait à enregistrer une clé fausse pour découvrir
     * qu'elle est fausse.
     */
    override suspend fun probe(configuration: AiConfiguration): ProbeOutcome {
        val probe = RecognitionInput.Text(languages.current().probe)
        return when (val outcome = configuration.provider.recognizer().recognize(probe, configuration)) {
            is RecognitionOutcome.Recognized -> configuration.provider.reachable()
            is RecognitionOutcome.Failed -> outcome.error.toProbe(configuration.provider)
        }
    }

    /**
     * **Six entrées, quatre implémentations.** Les quatre derniers fournisseurs
     * parlent le même protocole et ne diffèrent que par un réglage — le schéma que
     * l'un accepte et que les autres refusent —, porté par deux instances de la même
     * classe. C'est ici, et nulle part ailleurs, qu'on sait laquelle va à qui.
     */
    private fun AiProvider.recognizer(): ProviderRecognizer = when (this) {
        AiProvider.ANTHROPIC -> providers.anthropic
        AiProvider.GEMINI -> providers.gemini
        AiProvider.OPENAI -> providers.openAi
        AiProvider.DEEPSEEK, AiProvider.MISTRAL, AiProvider.COMPATIBLE -> providers.compatible
    }
}

/**
 * Ce qu'une issue d'analyse dit de la **configuration**, qui n'est pas la même
 * question.
 *
 * Une réponse illisible ou vide est un échec de l'analyse et une **réussite** du
 * sondage : le fournisseur a répondu, donc la clé est bonne et le modèle existe. Les
 * confondre ferait échouer le test pour une phrase que le modèle a mal comprise, et
 * enverrait l'utilisateur corriger une clé qui n'a rien.
 */
private fun AiError.toProbe(provider: AiProvider): ProbeOutcome = when (this) {
    AiError.Unparseable, AiError.NothingRecognized -> provider.reachable()
    else -> ProbeOutcome.Failed(this)
}

/**
 * Les quatre implémentations, en un objet.
 *
 * **Le même remède que pour [AiPrompts] et les trois interfaces Retrofit**, et pour la
 * même raison : passées une par une, elles poussaient le constructeur au-delà du seuil de
 * paramètres le jour où la langue s'y est ajoutée. Le regroupement dit en outre quelque
 * chose de vrai — ce sont les quatre classes qui savent parler à un fournisseur, là où le
 * reste de ce constructeur est ce qui décide **lequel** et **comment**.
 *
 * Quatre et non six : les quatre derniers fournisseurs parlent le même protocole, et
 * `compatible` les sert tous. C'est [ConfiguredRecognizer] qui sait qui va à qui, et c'est
 * le seul endroit qui le sache.
 */
internal data class ProviderRecognizers(
    val anthropic: ProviderRecognizer,
    val gemini: ProviderRecognizer,
    val openAi: ProviderRecognizer,
    val compatible: ProviderRecognizer,
)

/**
 * La capacité vision, telle qu'on la connaît.
 *
 * Pour les fournisseurs dont toute la gamme lit les images, elle est **su** et non
 * détectée. Pour les autres, il faudra joindre une image minuscule au sondage, et ce
 * sera le travail de la livraison qui les apporte : l'écrire maintenant serait deviner
 * pour des fournisseurs qui n'existent pas encore.
 */
private fun AiProvider.reachable() = ProbeOutcome.Reachable(vision = vision == VisionSupport.ALWAYS)

/**
 * Aussi court que possible : le sondage se paie, et il se paie souvent.
 *
 * **Dans la langue en vigueur**, comme le prompt qui l'accompagne. Une phrase française
 * envoyée avec une consigne anglaise est exactement le cas que le bouton **Tester** est
 * censé écarter : il éprouve le chemin réel de la première photo ([D77][decisions]), et
 * un chemin qui mélange deux langues n'est pas le chemin réel.
 *
 * [decisions]: docs/11-decisions.md
 */
private val ContentLanguage.probe: String
    get() = when (this) {
        ContentLanguage.FRENCH -> "un verre d'eau"
        ContentLanguage.ENGLISH -> "a glass of water"
    }

/**
 * Ce qu'un appel a coûté, quand il a coûté quelque chose.
 *
 * **On compte ce qui est facturé, pas ce qui est tenté.** Une clé refusée, un quota
 * dépassé, un réseau absent ou un délai dépassé n'ont rien produit chez le
 * fournisseur : les compter gonflerait un chiffre dont tout l'intérêt est d'être
 * comparable à une facture.
 *
 * Une réponse **illisible ou vide**, en revanche, a été produite — donc payée —, et
 * elle est comptée sans ses jetons, que la réponse n'a pas rendus. Annoncer zéro jeton
 * serait pire que de n'en annoncer aucun : c'est la même règle que partout ailleurs
 * dans ce projet, où une valeur absente vaut *inconnu*.
 */
private sealed interface Billing {
    /** Le fournisseur a répondu, et voici ce qu'il a compté — s'il l'a dit. */
    data class Billed(val usage: TokenUsage?) : Billing

    /** Rien n'a été produit, donc rien n'a été facturé. */
    data object None : Billing
}

private fun RecognitionOutcome.billing(): Billing = when (this) {
    is RecognitionOutcome.Recognized -> Billing.Billed(recognition.usage)
    is RecognitionOutcome.Failed -> error.billing()
}

private fun EstimationOutcome.billing(): Billing = when (this) {
    is EstimationOutcome.Estimated -> Billing.Billed(usage)
    is EstimationOutcome.Failed -> error.billing()
}

/**
 * Un échec facturé, ou un échec gratuit.
 *
 * Seules deux issues signifient « le fournisseur a répondu » : une réponse qu'on n'a
 * pas su lire, et une réponse lisible sans rien d'exploitable. Toutes les autres
 * arrivent avant que le modèle ait travaillé.
 */
private fun AiError.billing(): Billing = when (this) {
    AiError.Unparseable, AiError.NothingRecognized -> Billing.Billed(usage = null)
    else -> Billing.None
}

private suspend fun AiUsageLog.recordIfBilled(configuration: AiConfiguration, billing: Billing) {
    if (billing is Billing.Billed) record(configuration.provider, configuration.model, billing.usage)
}

/**
 * Une panne d'outillage vaut-elle un second essai, sans outils ?
 *
 * **Non pour ce qui se reproduira à l'identique.** Un réseau absent le restera, une clé
 * refusée le sera encore, un quota épuisé aussi : réessayer coûterait une minute pour
 * échouer deux fois de la même façon, et sur le quota, l'appel se paierait quand même.
 *
 * Oui pour tout le reste — un modèle qui a répondu en texte, trois tours sans conclure,
 * un fournisseur qui refuse le champ `tools`. Ce sont des défaillances de la **boucle**,
 * et le chemin ordinaire n'en souffre pas.
 */
private val AiError.worthRetrying: Boolean
    get() = this !is AiError.NoNetwork && this !is AiError.InvalidKey && this !is AiError.QuotaExceeded
