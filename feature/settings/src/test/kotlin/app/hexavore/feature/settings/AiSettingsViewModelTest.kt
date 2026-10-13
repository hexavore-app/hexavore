package app.hexavore.feature.settings

import app.hexavore.core.designsystem.component.messageRes
import app.hexavore.core.testing.InMemoryAiCredentials
import app.hexavore.core.testing.InMemoryAiUsage
import app.hexavore.core.testing.InMemoryKeyAcceptance
import app.hexavore.core.testing.InMemoryKeyRejection
import app.hexavore.domain.ai.AcceptedKey
import app.hexavore.domain.ai.AiConfiguration
import app.hexavore.domain.ai.AiError
import app.hexavore.domain.ai.AiProbe
import app.hexavore.domain.ai.AiProvider
import app.hexavore.domain.ai.ApiKey
import app.hexavore.domain.ai.ProbeOutcome
import app.hexavore.domain.ai.ProviderCredentials
import app.hexavore.domain.ai.ProviderStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
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
 * L'écran des fournisseurs : ce qu'il préremplit, ce qu'il éprouve, ce qu'il écrit.
 *
 * L'affichage n'est pas éprouvé ici — il n'y a pas d'émulateur. Ce qui l'est, c'est la
 * séparation qui structure l'écran et qui casserait en silence : **le formulaire n'est
 * pas les réglages enregistrés**. L'essai porte sur ce qu'on tape, et l'enregistrement
 * n'a lieu **qu'après** un essai accepté — c'est la règle que [D156][decisions] a
 * ramenée d'un bouton à l'autre, et celle qui empêche une clé jamais éprouvée d'allumer
 * les boutons d'analyse.
 *
 * [decisions]: docs/11-decisions.md
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal class AiSettingsViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val credentials = InMemoryAiCredentials()
    private val rejection = InMemoryKeyRejection()
    private val acceptance = InMemoryKeyAcceptance()
    private val usage = InMemoryAiUsage()
    private var probed: AiConfiguration? = null
    private var outcome: ProbeOutcome = ProbeOutcome.Reachable(vision = true)

    private val probe = AiProbe { configuration ->
        probed = configuration
        outcome
    }

    @BeforeEach
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterEach
    fun tearDown() = Dispatchers.resetMain()

    /**
     * **Le fournisseur recommandé est ouvert dès l'arrivée** ([D156][decisions]).
     *
     * C'est le premier des trois murs retirés : tout était replié, et il fallait deviner
     * qu'une carte se déplie avant de voir qu'il y a une clé à coller. Aucun testeur ne
     * l'a deviné.
     *
     * [decisions]: docs/11-decisions.md
     */
    @Test
    fun `le fournisseur recommande est deplie d emblee`() = runTest {
        val viewModel = viewModel()
        advanceUntilIdle()

        assertEquals(AiProvider.GEMINI, viewModel.uiState.value.open)
    }

    @Test
    fun `ouvrir un fournisseur vierge propose son URL et son premier modele`() = runTest {
        // Un champ obligatoire laisse vide est une enigme : celui qui monte un relais
        // saura remplacer l'URL, celui qui colle une cle ne devrait pas avoir a la
        // chercher. Les deux vivent desormais sous « Reglages avances » (D156), ce qui
        // ne change rien a la valeur qu'ils portent.
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.onOpen(AiProvider.ANTHROPIC)
        advanceUntilIdle()

        assertEquals(AiProvider.ANTHROPIC.defaultBaseUrl, viewModel.uiState.value.form.baseUrl)
        assertEquals(AiProvider.ANTHROPIC.suggestedModels.first(), viewModel.uiState.value.form.model)
        assertEquals("", viewModel.uiState.value.form.apiKey)
    }

    @Test
    fun `ouvrir un fournisseur configure relit ce qui est enregistre`() = runTest {
        credentials.save(AiProvider.ANTHROPIC, CLE)
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.onOpen(AiProvider.ANTHROPIC)
        advanceUntilIdle()

        // La cle se relit : docs/05 veut un champ masque avec revelation temporaire,
        // pas un champ qu'on ne peut que reecrire.
        assertEquals(CLE.apiKey.value, viewModel.uiState.value.form.apiKey)
        assertEquals(CLE.model, viewModel.uiState.value.form.model)
    }

    @Test
    fun `rouvrir le meme fournisseur le referme`() = runTest {
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.onOpen(AiProvider.ANTHROPIC)
        viewModel.onOpen(AiProvider.ANTHROPIC)
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.open)
    }

    @Test
    fun `valider eprouve ce qui est dans le formulaire, pas ce qui est enregistre`() = runTest {
        // C'est la regle qui justifie que le formulaire existe. Eprouver l'enregistre
        // obligerait a ecrire une cle fausse pour decouvrir qu'elle est fausse.
        credentials.save(AiProvider.ANTHROPIC, CLE)
        val viewModel = viewModel()
        advanceUntilIdle()
        viewModel.onOpen(AiProvider.ANTHROPIC)

        viewModel.onKey("sk-ant-tapee-a-l-instant")
        viewModel.onValidate()
        advanceUntilIdle()

        assertEquals(ApiKey("sk-ant-tapee-a-l-instant"), probed?.apiKey)
    }

    /**
     * **Un refus ne laisse rien derrière lui.**
     *
     * C'est la moitié de l'écran qui compte : enregistrer d'abord rendrait le
     * fournisseur actif avec une clé que personne n'a éprouvée, les boutons d'analyse
     * s'allumeraient, et le refus se découvrirait devant une assiette.
     */
    @Test
    fun `une cle refusee n est pas enregistree`() = runTest {
        outcome = ProbeOutcome.Failed(AiError.InvalidKey)
        val viewModel = viewModel()
        advanceUntilIdle()
        viewModel.onOpen(AiProvider.ANTHROPIC)
        viewModel.onKey(CLE.apiKey.value)

        viewModel.onValidate()
        advanceUntilIdle()

        assertNull(credentials.current(), "une cle refusee ne doit pas s ecrire")
        assertNull(acceptance.current, "rien a annoncer")
        assertFalse(viewModel.uiState.value.validated, "l ecran reste ouvert sur le refus")
    }

    @Test
    fun `une cle refusee se dit avec un message, jamais avec un code`() = runTest {
        outcome = ProbeOutcome.Failed(AiError.InvalidKey)
        val viewModel = viewModel()
        advanceUntilIdle()
        viewModel.onOpen(AiProvider.ANTHROPIC)
        viewModel.onKey(CLE.apiKey.value)

        viewModel.onValidate()
        advanceUntilIdle()

        assertEquals(KeyCheck.Refused(AiError.InvalidKey.messageRes), viewModel.uiState.value.check)
    }

    /**
     * Un fournisseur débordé **n'accuse pas la clé**.
     *
     * Le `503` des modèles gratuits les plus demandés arrivait sous la forme « le
     * service est indisponible », affiché pendant qu'on vient de coller sa première
     * clé : la phrase se lisait comme un refus de cette clé ([D156][decisions]).
     *
     * [decisions]: docs/11-decisions.md
     */
    @Test
    fun `un fournisseur deborde a son propre message`() = runTest {
        outcome = ProbeOutcome.Failed(AiError.ProviderBusy)
        val viewModel = viewModel()
        advanceUntilIdle()
        viewModel.onOpen(AiProvider.ANTHROPIC)
        viewModel.onKey(CLE.apiKey.value)

        viewModel.onValidate()
        advanceUntilIdle()

        assertEquals(KeyCheck.Refused(AiError.ProviderBusy.messageRes), viewModel.uiState.value.check)
    }

    @Test
    fun `un formulaire incomplet ne part pas sur le reseau`() = runTest {
        // Un modele vide rend un 404 que l'utilisateur lirait comme une cle refusee.
        val viewModel = viewModel()
        advanceUntilIdle()
        viewModel.onOpen(AiProvider.ANTHROPIC)
        viewModel.onModel("")

        viewModel.onValidate()
        advanceUntilIdle()

        assertNull(probed, "rien d incomplet ne se paie")
    }

    @Test
    fun `valider une cle acceptee l ecrit, l active, et le dit sur le bouton`() = runTest {
        val viewModel = viewModel()
        advanceUntilIdle()
        viewModel.onOpen(AiProvider.ANTHROPIC)
        viewModel.onKey(CLE.apiKey.value)

        viewModel.onValidate()
        advanceUntilIdle()

        assertEquals(CLE.apiKey, credentials.current()?.apiKey)
        assertTrue(viewModel.uiState.value.inUse, "le bouton dit « Utilise »")
    }

    @Test
    fun `valider une cle acceptee oublie le refus precedent`() = runTest {
        // La cle qui avait ete refusee n'est plus celle-la : la pastille n'a plus rien
        // a designer. Elle se rallumera d'elle-meme si la neuve est refusee aussi.
        rejection.note()
        val viewModel = viewModel()
        advanceUntilIdle()
        viewModel.onOpen(AiProvider.ANTHROPIC)
        viewModel.onKey(CLE.apiKey.value)

        viewModel.onValidate()
        advanceUntilIdle()

        assertFalse(rejection.noted)
    }

    /**
     * La bonne nouvelle **part ailleurs que dans l'état de l'écran**.
     *
     * Cet écran se referme au moment même où il l'annonce : c'est celui sur lequel on
     * revient — l'accueil le plus souvent — qui la dit. Elle était posée dans l'état de
     * navigation, et seul le hub savait l'y lire ; venus de l'accueil, on ne voyait rien
     * (D157).
     */
    @Test
    fun `valider annonce la nouvelle a l ecran sur lequel on revient`() = runTest {
        val viewModel = viewModel()
        advanceUntilIdle()
        viewModel.onOpen(AiProvider.ANTHROPIC)
        viewModel.onKey(CLE.apiKey.value)

        viewModel.onValidate()
        advanceUntilIdle()

        assertEquals(AcceptedKey(vision = true), acceptance.current)
    }

    @Test
    fun `valider referme l ecran, une fois`() = runTest {
        val viewModel = viewModel()
        advanceUntilIdle()
        viewModel.onOpen(AiProvider.ANTHROPIC)
        viewModel.onKey(CLE.apiKey.value)

        viewModel.onValidate()
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.validated)

        // La route l'a consomme : revenir sur l'ecran ne doit pas le refermer aussitot.
        viewModel.onValidationShown()
        assertFalse(viewModel.uiState.value.validated)
    }

    @Test
    fun `un modele qui ne lit pas les images le dit dans la nouvelle`() = runTest {
        // Promettre la photo sous un modele qui ne la lit pas serait mentir au moment
        // precis ou l'on vient d'obtenir la confiance de quelqu'un.
        outcome = ProbeOutcome.Reachable(vision = false)
        val viewModel = viewModel()
        advanceUntilIdle()
        viewModel.onOpen(AiProvider.ANTHROPIC)
        viewModel.onKey(CLE.apiKey.value)

        viewModel.onValidate()
        advanceUntilIdle()

        assertEquals(AcceptedKey(vision = false), acceptance.current)
    }

    @Test
    fun `modifier la cle apres coup redonne le bouton`() = runTest {
        // Sans cela, « Utilise » resterait affiche sous une cle qu'on vient de changer
        // et qui n'est enregistree nulle part -- exactement le contraire de la verite.
        val viewModel = viewModel()
        advanceUntilIdle()
        viewModel.onOpen(AiProvider.ANTHROPIC)
        viewModel.onKey(CLE.apiKey.value)
        viewModel.onValidate()
        advanceUntilIdle()

        viewModel.onKey("sk-ant-une-autre")

        assertFalse(viewModel.uiState.value.inUse, "le bouton redevient « Valider la cle »")
    }

    @Test
    fun `afficher la cle ne redonne pas le bouton`() = runTest {
        // Montrer sa cle ne la modifie pas : la comparaison porte sur les trois valeurs
        // saisies, jamais sur la revelation.
        val viewModel = viewModel()
        advanceUntilIdle()
        viewModel.onOpen(AiProvider.ANTHROPIC)
        viewModel.onKey(CLE.apiKey.value)
        viewModel.onValidate()
        advanceUntilIdle()

        viewModel.onReveal()

        assertTrue(viewModel.uiState.value.inUse, "l oeil n est pas une modification")
    }

    @Test
    fun `un fournisseur qui ne sert pas n a pas le bouton utilise`() = runTest {
        // Deux conditions, pas une : enregistre ne suffit pas, il faut que ce soit
        // celui-la qui analyse.
        val viewModel = viewModel()
        advanceUntilIdle()
        viewModel.onOpen(AiProvider.ANTHROPIC)
        viewModel.onKey(CLE.apiKey.value)
        viewModel.onValidate()
        advanceUntilIdle()

        viewModel.onOpen(AiProvider.GEMINI)

        assertFalse(viewModel.uiState.value.inUse, "Gemini n est pas celui qui sert")
    }

    @Test
    fun `toute frappe efface le refus du dernier essai`() = runTest {
        // Un « cle refusee » sous une cle qu'on vient de corriger est la pire forme
        // d'information perimee : elle a l'air fraiche.
        outcome = ProbeOutcome.Failed(AiError.InvalidKey)
        val viewModel = viewModel()
        advanceUntilIdle()
        viewModel.onOpen(AiProvider.ANTHROPIC)
        viewModel.onKey(CLE.apiKey.value)
        viewModel.onValidate()
        advanceUntilIdle()

        viewModel.onKey("sk-ant-autre-chose")
        advanceUntilIdle()

        assertEquals(KeyCheck.Idle, viewModel.uiState.value.check)
    }

    @Test
    fun `la liste ne montre que les fournisseurs eprouves`() = runTest {
        // **Deux et non six** (D138). Le code des six existe et passe ses tests ; ce qui
        // manque aux quatre autres est un appel reel avec une vraie cle. Les afficher
        // grises revenait a poser six questions a quelqu'un qui n'en cherchait aucune.
        val viewModel = viewModel()
        advanceUntilIdle()

        // Les memes, sans l'ordre : celui-ci a sa propre regle, et son propre cas.
        val montres = viewModel.uiState.value.rows.map { it.provider }.toSet()
        assertEquals(AiProvider.entries.filter { it.status != ProviderStatus.SUSPENDED }.toSet(), montres)
    }

    @Test
    fun `le fournisseur gratuit vient en premier`() = runTest {
        // C'est la seule information qui decide vraiment : quelqu'un qui decouvre se
        // demande si la fonctionnalite va lui couter de l'argent.
        val viewModel = viewModel()
        advanceUntilIdle()

        assertEquals(AiProvider.GEMINI, viewModel.uiState.value.rows.first().provider)
        assertNotNull(viewModel.uiState.value.rows.firstOrNull { it.provider == AiProvider.ANTHROPIC })
    }

    @Test
    fun `aucun fournisseur affiche n est en reserve`() = runTest {
        // La consequence du filtre, dite a part : une carte inerte ne peut plus arriver
        // dans la liste, donc plus personne n'a a la griser.
        val viewModel = viewModel()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.rows.none { it.suspended })
    }

    private fun viewModel() = AiSettingsViewModel(credentials, rejection, probe, acceptance, usage)

    private companion object {
        val CLE = ProviderCredentials(
            apiKey = ApiKey("sk-ant-de-test"),
            model = "claude-opus-5",
            baseUrl = "https://api.anthropic.com/",
        )
    }
}
