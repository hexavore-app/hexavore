package app.hexavore.integration.ai

import app.hexavore.core.testing.FixedLanguage
import app.hexavore.core.testing.InMemoryAiUsage
import app.hexavore.domain.ai.AiConfiguration
import app.hexavore.domain.ai.AiError
import app.hexavore.domain.ai.AiProvider
import app.hexavore.domain.ai.AiSettings
import app.hexavore.domain.ai.ApiKey
import app.hexavore.domain.ai.CatalogueTool
import app.hexavore.domain.ai.EstimationOutcome
import app.hexavore.domain.ai.ProbeOutcome
import app.hexavore.domain.ai.Recognition
import app.hexavore.domain.ai.RecognitionInput
import app.hexavore.domain.ai.RecognitionOutcome
import app.hexavore.domain.ai.TokenUsage
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * La fabrique, sans réseau : ce qu'elle décide n'a rien à voir avec ce qu'un
 * fournisseur répond.
 *
 * Deux règles seulement, et la première est celle qui rend les boutons IA utilisables
 * sans clé : **une absence de configuration ne doit atteindre aucun fournisseur.**
 * Appeler quand même rendrait une erreur d'authentification là où il n'y a
 * simplement rien de configuré — et enverrait une requête payante dans le vide.
 */
class ConfiguredRecognizerTest {
    @Test
    fun `sans configuration, aucun fournisseur n est appele`() = runTest {
        var seen: AiConfiguration? = null
        val recognizer = factory(settings = { null }, anthropic = record { seen = it })

        val outcome = recognizer.recognize(RecognitionInput.Text("un jus"))

        assertEquals(RecognitionOutcome.Failed(AiError.NoProviderConfigured), outcome)
        assertNull(seen, "une analyse sans cle ne doit pas partir sur le reseau")
    }

    @Test
    fun `la configuration courante accompagne l appel`() = runTest {
        var seen: AiConfiguration? = null
        val recognizer = factory(settings = { CONFIGURATION }, anthropic = record { seen = it })

        recognizer.recognize(RecognitionInput.Text("un jus"))

        assertEquals(CONFIGURATION, seen)
    }

    @Test
    fun `le sondage marche sans rien d enregistre`() = runTest {
        // C'est tout l'interet du bouton : on eprouve ce qui est dans le formulaire,
        // avant d'ecrire. Lire les reglages ici obligerait a enregistrer une cle
        // fausse pour decouvrir qu'elle est fausse.
        val recognizer = factory(anthropic = record { })

        assertEquals(ProbeOutcome.Reachable(vision = true), recognizer.probe(CONFIGURATION))
    }

    @Test
    fun `une reponse illisible reste une configuration valide`() = runTest {
        // Le fournisseur a repondu : la cle est bonne et le modele existe. Echouer ici
        // enverrait quelqu'un corriger une cle qui n'a rien.
        val recognizer = factory(anthropic = recognizing { RecognitionOutcome.Failed(AiError.Unparseable) })

        assertEquals(ProbeOutcome.Reachable(vision = true), recognizer.probe(CONFIGURATION))
    }

    @Test
    fun `une cle refusee fait echouer le sondage`() = runTest {
        val recognizer = factory(anthropic = recognizing { RecognitionOutcome.Failed(AiError.InvalidKey) })

        assertEquals(ProbeOutcome.Failed(AiError.InvalidKey), recognizer.probe(CONFIGURATION))
    }

    @Test
    fun `le sondage emprunte le chemin d une vraie analyse`() = runTest {
        // Un appel special, plus leger, aurait pu reussir la ou l'analyse echoue. Un
        // bouton « Tester » qui dit oui a tort est pire que pas de bouton.
        var input: RecognitionInput? = null
        val recognizer = factory(
            anthropic = recognizing { received ->
                input = received
                RecognitionOutcome.Recognized(Recognition(items = emptyList()))
            },
        )

        recognizer.probe(CONFIGURATION)

        assertTrue(input is RecognitionInput.Text, "le sondage doit passer par le contrat de reconnaissance")
    }

    @Test
    fun `chaque entree atteint l implementation qui lui revient`() = runTest {
        // La regle que le `when` de la fabrique porte, et la seule chose qu'il decide.
        // Router vers la mauvaise donnerait une cle refusee sur une cle parfaitement
        // valide -- et, entre les quatre derniers, un schema envoye a qui le refuse.
        val attendu = mapOf(
            AiProvider.ANTHROPIC to "anthropic",
            AiProvider.GEMINI to "gemini",
            AiProvider.OPENAI to "openAi",
            AiProvider.DEEPSEEK to "compatible",
            AiProvider.MISTRAL to "compatible",
            AiProvider.COMPATIBLE to "compatible",
        )

        // Toutes les entrees, et non celles qu'on a pensees : une entree ajoutee sans
        // branche ne compile pas, mais une entree ajoutee **avec** la mauvaise branche
        // compile tres bien.
        assertEquals(AiProvider.entries.toSet(), attendu.keys, "un fournisseur sans attente")

        attendu.forEach { (provider, implementation) ->
            var atteint: String? = null
            val recognizer = factory(
                anthropic = mark("anthropic") { atteint = it },
                gemini = mark("gemini") { atteint = it },
                openAi = mark("openAi") { atteint = it },
                compatible = mark("compatible") { atteint = it },
            )

            recognizer.probe(CONFIGURATION.copy(provider = provider))

            assertEquals(implementation, atteint, "mauvaise implementation pour $provider")
        }
    }

    @Test
    fun `une liste vide n atteint aucun fournisseur`() = runTest {
        // Le cas courant : toutes les lignes ont ete resolues. Un appel qui partirait
        // quand meme ne rendrait rien et se paierait.
        val recognizer = factory(settings = { CONFIGURATION })

        assertEquals(EstimationOutcome.Estimated(emptyList()), recognizer.estimate(emptyList()))
    }

    @Test
    fun `sans configuration, aucune estimation ne part`() = runTest {
        // Le repli ne doit pas devenir la raison pour laquelle une cle manquante se
        // voit : la ligne reste a completer a la main, comme avant l'appel.
        val recognizer = factory(settings = { null })

        assertEquals(
            EstimationOutcome.Failed(AiError.NoProviderConfigured),
            recognizer.estimate(listOf("sauce maison")),
        )
    }

    @Test
    fun `l estimation emprunte le meme routage que l analyse`() = runTest {
        // Sinon une cle valide chez l'un partirait chez l'autre, et le 401 accuserait
        // la cle.
        var atteint: String? = null
        val recognizer = factory(
            settings = { CONFIGURATION.copy(provider = AiProvider.MISTRAL) },
            compatible = estimating("compatible") { atteint = it },
        )

        recognizer.estimate(listOf("sauce maison"))

        assertEquals("compatible", atteint)
    }

    /** Un fournisseur qui ne sait qu'estimer, pendant du [recognizing] ci-dessous. */
    private fun estimating(implementation: String, onCall: (String) -> Unit) = object : ProviderRecognizer {
        override suspend fun recognize(input: RecognitionInput, configuration: AiConfiguration): RecognitionOutcome =
            error("ce cas ne parle pas de la reconnaissance")

        override suspend fun estimate(labels: List<String>, configuration: AiConfiguration): EstimationOutcome {
            onCall(implementation)
            return EstimationOutcome.Estimated(emptyList())
        }
    }

    @Test
    fun `une analyse reussie est comptee, avec ses jetons`() = runTest {
        // L'utilisateur paie ses appels : il a le droit de savoir combien.
        val recognizer = factory(
            settings = { CONFIGURATION },
            anthropic = recognizing {
                RecognitionOutcome.Recognized(Recognition(emptyList(), TokenUsage(input = 900, output = 80)))
            },
        )

        recognizer.recognize(RecognitionInput.Text("un jus"))

        val compte = usage.recorded.single()
        assertEquals(AiProvider.ANTHROPIC, compte.provider)
        assertEquals(CONFIGURATION.model, compte.model)
        assertEquals(900, compte.input)
        assertEquals(80, compte.output)
    }

    @Test
    fun `une reponse illisible est comptee, sans ses jetons`() = runTest {
        // Elle a ete produite, donc payee. Annoncer zero jeton serait pire que de n'en
        // annoncer aucun.
        val recognizer = factory(
            settings = { CONFIGURATION },
            anthropic = recognizing { RecognitionOutcome.Failed(AiError.Unparseable) },
        )

        recognizer.recognize(RecognitionInput.Text("un jus"))

        assertEquals(1, usage.recorded.single().calls)
        assertEquals(0, usage.recorded.single().input)
    }

    @Test
    fun `une cle refusee n est pas comptee`() = runTest {
        // Rien n'a ete produit chez le fournisseur : compter gonflerait un chiffre dont
        // tout l'interet est d'etre comparable a une facture.
        val recognizer = factory(
            settings = { CONFIGURATION },
            anthropic = recognizing { RecognitionOutcome.Failed(AiError.InvalidKey) },
        )

        recognizer.recognize(RecognitionInput.Text("un jus"))

        assertEquals(emptyList<Any>(), usage.recorded)
    }

    @Test
    fun `le repli est compte comme le reste`() = runTest {
        val recognizer = factory(
            settings = { CONFIGURATION },
            anthropic = estimatingWith(TokenUsage(input = 120, output = 40)),
        )

        recognizer.estimate(listOf("sauce maison"))

        assertEquals(120, usage.recorded.single().input)
    }

    private fun estimatingWith(tokens: TokenUsage) = object : ProviderRecognizer {
        override suspend fun recognize(input: RecognitionInput, configuration: AiConfiguration): RecognitionOutcome =
            error("ce cas ne parle pas de la reconnaissance")

        override suspend fun estimate(labels: List<String>, configuration: AiConfiguration): EstimationOutcome =
            EstimationOutcome.Estimated(emptyList(), tokens)
    }

    private fun mark(implementation: String, onCall: (String) -> Unit) = recognizing {
        onCall(implementation)
        RecognitionOutcome.Recognized(Recognition(items = emptyList()))
    }

    /**
     * Un fournisseur qui ne sait que reconnaitre.
     *
     * `ProviderRecognizer` porte deux methodes depuis l'etape 4, et ces cas ne parlent
     * que de la premiere. L'estimation echoue donc bruyamment : un cas de routage qui
     * l'atteindrait sans le vouloir doit s'en apercevoir.
     */
    private fun recognizing(onRecognize: suspend (RecognitionInput) -> RecognitionOutcome) =
        object : ProviderRecognizer {
            override suspend fun recognize(input: RecognitionInput, configuration: AiConfiguration) = onRecognize(input)

            override suspend fun estimate(labels: List<String>, configuration: AiConfiguration): EstimationOutcome =
                error("ce cas ne parle pas de l estimation")
        }

    // --- Le repli de l'analyse approfondie ---------------------------------------------

    @Test
    fun `sans le mode approfondi, la boucle n est meme pas tentee`() = runTest {
        val chemins = mutableListOf<String>()

        factory(settings = { configuration(deep = false) }, anthropic = tracing(chemins)).recognize(ENTREE)

        assertEquals(listOf("ordinaire"), chemins)
    }

    @Test
    fun `en mode approfondi, la boucle passe en premier`() = runTest {
        val chemins = mutableListOf<String>()

        factory(settings = { configuration(deep = true) }, anthropic = tracing(chemins)).recognize(ENTREE)

        assertEquals(listOf("approfondi"), chemins, "elle a abouti, donc rien d autre ne part")
    }

    @Test
    fun `une boucle qui echoue retombe sur l analyse ordinaire`() = runTest {
        // **La moitie qui compte.** Une boucle a plus de facons d'echouer qu'un
        // aller-retour, et aucune ne justifie de rendre l'utilisateur bredouille alors
        // que le chemin ordinaire, lui, marche.
        val chemins = mutableListOf<String>()

        factory(
            settings = { configuration(deep = true) },
            anthropic = tracing(chemins, deepFails = AiError.NothingRecognized),
        ).recognize(ENTREE)

        assertEquals(listOf("approfondi", "ordinaire"), chemins)
    }

    @Test
    fun `un reseau absent ne se retente pas`() = runTest {
        // Il le restera : reessayer couterait une minute pour echouer deux fois de la
        // meme facon.
        val chemins = mutableListOf<String>()

        factory(
            settings = { configuration(deep = true) },
            anthropic = tracing(chemins, deepFails = AiError.NoNetwork),
        ).recognize(ENTREE)

        assertEquals(listOf("approfondi"), chemins)
    }

    @Test
    fun `une cle refusee ne se retente pas`() = runTest {
        val chemins = mutableListOf<String>()

        factory(
            settings = { configuration(deep = true) },
            anthropic = tracing(chemins, deepFails = AiError.InvalidKey),
        ).recognize(ENTREE)

        assertEquals(listOf("approfondi"), chemins)
    }

    @Test
    fun `un quota epuise ne se retente pas`() = runTest {
        // L'appel se paierait quand meme, et echouerait pareil.
        val chemins = mutableListOf<String>()

        factory(
            settings = { configuration(deep = true) },
            anthropic = tracing(chemins, deepFails = AiError.QuotaExceeded),
        ).recognize(ENTREE)

        assertEquals(listOf("approfondi"), chemins)
    }

    /** Un fournisseur qui note par ou on est passe, et peut faire echouer la boucle. */
    private fun tracing(chemins: MutableList<String>, deepFails: AiError? = null) = object : ProviderRecognizer {
        override suspend fun recognize(input: RecognitionInput, configuration: AiConfiguration): RecognitionOutcome {
            chemins += "ordinaire"
            return RecognitionOutcome.Recognized(Recognition(items = emptyList()))
        }

        override suspend fun deepRecognize(
            input: RecognitionInput,
            configuration: AiConfiguration,
            catalogue: CatalogueTool,
        ): RecognitionOutcome {
            chemins += "approfondi"
            return deepFails
                ?.let { RecognitionOutcome.Failed(it) }
                ?: RecognitionOutcome.Recognized(Recognition(items = emptyList()))
        }

        override suspend fun estimate(labels: List<String>, configuration: AiConfiguration): EstimationOutcome =
            error("ce cas ne parle pas de l estimation")
    }

    private fun configuration(deep: Boolean) = AiConfiguration(
        provider = AiProvider.ANTHROPIC,
        apiKey = ApiKey("sk-de-test"),
        model = "claude-opus-5",
        baseUrl = "https://exemple/",
        deepAnalysis = deep,
    )

    /**
     * La fabrique, avec des fournisseurs qui echouent bruyamment par defaut.
     *
     * Chaque cas ne nomme que ceux qu'il attend ; les autres crient s'ils sont
     * atteints, plutot que de rendre une reponse plausible sur la mauvaise branche.
     */
    private fun factory(
        settings: AiSettings = AiSettings { null },
        catalogue: CatalogueTool = CatalogueTool { emptyList() },
        anthropic: ProviderRecognizer = unused(),
        gemini: ProviderRecognizer = unused(),
        openAi: ProviderRecognizer = unused(),
        compatible: ProviderRecognizer = unused(),
    ) = ConfiguredRecognizer(
        settings,
        FixedLanguage(),
        usage,
        catalogue,
        ProviderRecognizers(anthropic, gemini, openAi, compatible),
    )

    /** Le compteur, inspecte par les cas qui parlent de ce qui est facture. */
    private val usage = InMemoryAiUsage()

    /**
     * Un fournisseur que ces cas ne doivent jamais atteindre.
     *
     * Il echoue bruyamment plutot que de rendre une reponse plausible : la fabrique
     * qui se tromperait de branche donnerait sinon un test vert sur le mauvais
     * fournisseur.
     */
    private fun unused() = object : ProviderRecognizer {
        override suspend fun recognize(input: RecognitionInput, configuration: AiConfiguration): RecognitionOutcome =
            error("la fabrique s est trompee de fournisseur")

        override suspend fun estimate(labels: List<String>, configuration: AiConfiguration): EstimationOutcome =
            error("la fabrique s est trompee de fournisseur")
    }

    private fun record(onCall: (AiConfiguration) -> Unit) = object : ProviderRecognizer {
        override suspend fun recognize(input: RecognitionInput, configuration: AiConfiguration): RecognitionOutcome {
            onCall(configuration)
            return RecognitionOutcome.Recognized(Recognition(items = emptyList()))
        }

        override suspend fun estimate(labels: List<String>, configuration: AiConfiguration): EstimationOutcome =
            error("ce cas ne parle pas de l estimation")
    }

    private companion object {
        val ENTREE = RecognitionInput.Text("un abricot")

        val CONFIGURATION = AiConfiguration(
            provider = AiProvider.ANTHROPIC,
            apiKey = ApiKey("sk-ant-de-test"),
            model = "claude-opus-5",
            baseUrl = "https://api.anthropic.com/",
        )
    }
}
