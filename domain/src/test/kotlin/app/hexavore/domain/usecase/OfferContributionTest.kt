package app.hexavore.domain.usecase

import app.hexavore.core.testing.InMemoryFoodCatalog
import app.hexavore.domain.food.ContributionOutcome
import app.hexavore.domain.food.ContributionSettings
import app.hexavore.domain.food.ContributionSetup
import app.hexavore.domain.food.Food
import app.hexavore.domain.food.FoodContribution
import app.hexavore.domain.food.FoodContributionTarget
import app.hexavore.domain.food.FoodId
import app.hexavore.domain.food.FoodSource
import app.hexavore.domain.food.OffAccount
import app.hexavore.domain.nutrition.NutrientValues
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * Quand la contribution se propose, et sous quelle identité elle part.
 *
 * **Quatre raisons de ne rien proposer, et aucune n'est une erreur.** L'écran se
 * referme alors comme avant, sans rien dire : un message qui expliquerait pourquoi on
 * ne propose pas encombrerait le moment où l'on vient de finir une saisie.
 *
 * **La première est la réserve** ([D158][decisions]) : tant qu'aucun envoi réel n'a
 * atteint la vraie base, rien n'est proposé, compte ou pas. Les autres cas éprouvent la
 * proposition **ouverte**, parce que le code est en réserve et non mort — il reviendra
 * en changeant un mot, et doit revenir juste.
 *
 * [decisions]: docs/11-decisions.md
 */
class OfferContributionTest {
    private val settings = FakeContributionSettings()

    /** La proposition telle qu'elle sera le jour où la réserve sera levée. */
    private fun proposer(catalogue: InMemoryFoodCatalog) = OfferContribution(catalogue, settings, offered = true)

    @Test
    fun `tenue en reserve, la contribution ne se propose pas, meme avec un compte`() = runTest {
        // Le cas d'un testeur qui avait saisi son compte avant la mise en reserve : la
        // proposition ne doit pas reparaitre pour lui.
        settings.set(ContributionSetup(account = COMPTE))
        val catalogue = InMemoryFoodCatalog(initial = listOf(TAPENADE))

        assertNull(OfferContribution(catalogue, settings)(TAPENADE.id))
    }

    @Test
    fun `une fiche personnelle scannee se propose`() = runTest {
        settings.set(ContributionSetup(account = COMPTE))
        val catalogue = InMemoryFoodCatalog(initial = listOf(TAPENADE))

        val proposition = proposer(catalogue)(TAPENADE.id)

        assertNotNull(proposition)
        assertEquals("3017620422003", proposition!!.barcode.value)
    }

    @Test
    fun `sans compte, rien n est propose`() = runTest {
        // Le compte est la condition d'ouverture : sans lui, l'envoi serait refuse et
        // proposer reviendrait a promettre ce qu'on ne peut pas tenir.
        settings.set(ContributionSetup(account = null))
        val catalogue = InMemoryFoodCatalog(initial = listOf(TAPENADE))

        assertNull(proposer(catalogue)(TAPENADE.id))
    }

    @Test
    fun `une fiche non contribuable ne se propose pas`() = runTest {
        settings.set(ContributionSetup(account = COMPTE))
        val sansCode = TAPENADE.copy(sourceRef = null)
        val catalogue = InMemoryFoodCatalog(initial = listOf(sansCode))

        assertNull(proposer(catalogue)(sansCode.id))
    }

    @Test
    fun `une fiche absente du catalogue ne se propose pas`() = runTest {
        // Elle n'a pas pu etre relue : proposer d'envoyer ce qu'on n'a pas su lire
        // enverrait ce qu'on croyait avoir saisi.
        settings.set(ContributionSetup(account = COMPTE))

        assertNull(proposer(InMemoryFoodCatalog())(FoodId("inconnu")))
    }

    @Test
    fun `la fiche relue fait foi, pas le formulaire`() = runTest {
        // Ce qui part est ce qui a ete enregistre. C'est aussi ce qui garantit que la
        // source vaut bien CUSTOM -- une condition que seule l'ecriture connait.
        settings.set(ContributionSetup(account = COMPTE))
        val catalogue = InMemoryFoodCatalog(initial = listOf(TAPENADE.copy(name = "Nom enregistre")))

        assertEquals("Nom enregistre", proposer(catalogue)(TAPENADE.id)!!.name)
    }

    // --- L'envoi ---------------------------------------------------------------

    @Test
    fun `l envoi part sous le compte configure`() = runTest {
        settings.set(ContributionSetup(account = COMPTE))
        val cible = RecordingTarget()

        SendContribution(cible, settings)(CONTRIBUTION)

        assertEquals(COMPTE, cible.account)
    }

    @Test
    fun `un compte efface entre-temps refuse l envoi`() = runTest {
        // Le compte est relu au moment d'envoyer, pas porte depuis la proposition :
        // entre les deux, il a pu etre efface dans les reglages.
        settings.set(ContributionSetup(account = null))
        val cible = RecordingTarget()

        val issue = SendContribution(cible, settings)(CONTRIBUTION)

        assertEquals(ContributionOutcome.Rejected, issue)
        assertNull(cible.account, "et rien n est parti")
    }

    @Test
    fun `un compte a moitie saisi refuse l envoi`() = runTest {
        settings.set(ContributionSetup(account = OffAccount("charly", "")))
        val cible = RecordingTarget()

        assertEquals(ContributionOutcome.Rejected, SendContribution(cible, settings)(CONTRIBUTION))
        assertNull(cible.account)
    }

    private class FakeContributionSettings : ContributionSettings {
        private val setup = MutableStateFlow(ContributionSetup())

        fun set(value: ContributionSetup) {
            setup.value = value
        }

        override fun observe(): Flow<ContributionSetup> = setup

        override suspend fun save(account: OffAccount) {
            setup.value = setup.value.copy(account = account)
        }

        override suspend fun forget() {
            setup.value = setup.value.copy(account = null)
        }

        override suspend fun useSandbox(sandbox: Boolean) {
            setup.value = setup.value.copy(sandbox = sandbox)
        }
    }

    /** Retient ce qu'on lui a demandé d'envoyer, et sous quelle identité. */
    private class RecordingTarget : FoodContributionTarget {
        var account: OffAccount? = null

        override suspend fun contribute(contribution: FoodContribution, account: OffAccount): ContributionOutcome {
            this.account = account
            return ContributionOutcome.Sent
        }
    }

    private companion object {
        val COMPTE = OffAccount(userId = "charly", password = "secret")

        val TAPENADE = Food(
            id = FoodId("f-tapenade"),
            source = FoodSource.CUSTOM,
            sourceRef = "3017620422003",
            name = "Tapenade maison",
            per100g = NutrientValues(kcal = 39.0, protein = 2.18, carbs = 3.5, fat = 0.86, fiber = 3.6),
        )

        val CONTRIBUTION = checkNotNull(FoodContribution.of(TAPENADE))
    }
}
