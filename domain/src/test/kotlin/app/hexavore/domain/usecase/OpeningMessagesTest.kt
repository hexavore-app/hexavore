package app.hexavore.domain.usecase

import app.hexavore.core.testing.FixedClock
import app.hexavore.core.testing.InMemoryOpeningLedger
import app.hexavore.domain.opening.OpeningAnswer
import app.hexavore.domain.opening.OpeningMessage
import app.hexavore.domain.opening.OpeningRules
import app.hexavore.domain.opening.OpeningSituation
import app.hexavore.domain.opening.OpeningTip
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.LocalDate

/**
 * Le rythme des messages à l'ouverture ([D179][decisions]).
 *
 * Ce qui s'éprouve est **quand l'accueil se tait** autant que ce qu'il dit : une astuce
 * de trop, ou une demande d'avis qui insiste, se paient en désinstallations. Chaque
 * ouverture du décor avance l'horloge d'une heure — au-delà de l'absence qui en fait une —,
 * et l'accueil s'efface juste après avoir paru.
 *
 * [decisions]: docs/11-decisions.md
 */
internal class OpeningMessagesTest {
    private val clock = FixedClock.atNoon(LocalDate.of(2026, 10, 25))
    private val ledger = InMemoryOpeningLedger()
    private var situation = OpeningSituation(aiConfigured = false, tourSeen = true, crashPending = false, dishes = 0)
    private val messages = OpeningMessages(ledger, ledger, { situation }, clock)

    @Test
    fun `la premiere ouverture compte, et ne dit rien`() = runTest {
        assertNull(ouvrir())

        assertEquals(1, ledger.record.openings)
    }

    @Test
    fun `un retour avant trente minutes n est pas une ouverture`() = runTest {
        // Une photo, l'appareil, la validation : on revient dix fois sur l'accueil
        // pendant un repas, et ce n'est qu'une ouverture.
        ouvrir()

        revenirApres(OpeningRules.ABSENCE.minusMinutes(1))

        assertEquals(1, ledger.record.openings)
    }

    @Test
    fun `trente minutes d absence font une ouverture`() = runTest {
        ouvrir()

        revenirApres(OpeningRules.ABSENCE)

        assertEquals(2, ledger.record.openings)
    }

    @Test
    fun `rien avant la troisieme ouverture, et une astuce a la troisieme`() = runTest {
        assertNull(ouvrir())
        assertNull(ouvrir())

        assertEquals(OpeningMessage.Tip(OpeningTip.FREE_KEY), ouvrir())
    }

    @Test
    fun `avec une cle, l astuce de la cle gratuite passe son tour`() = runTest {
        situation = situation.copy(aiConfigured = true)

        assertEquals(OpeningMessage.Tip(OpeningTip.QUARTER_SOURCES), troisiemeOuverture())
    }

    @Test
    fun `un message au plus toutes les quatre ouvertures`() = runTest {
        troisiemeOuverture()

        val suivantes = List(OpeningRules.SPACING) { ouvrir() }

        assertEquals(listOf(null, null, null), suivantes.dropLast(1))
        assertEquals(OpeningMessage.Tip(OpeningTip.QUARTER_SOURCES), suivantes.last())
    }

    @Test
    fun `une astuce ne parait qu une fois, et elles finissent`() = runTest {
        val vues = List(OpeningRules.FIRST_MESSAGE + OpeningRules.SPACING * OpeningTip.entries.size) { ouvrir() }
            .filterIsInstance<OpeningMessage.Tip>()
            .map { it.tip }

        // Sans cle : toutes, sauf la photo et l'estimation qui en demandent une.
        assertEquals(OpeningTip.entries.filter { it.audience.includes(false) }, vues)
    }

    @Test
    fun `ne plus afficher d astuces les eteint, jusqu aux reglages`() = runTest {
        troisiemeOuverture()

        messages.answer(OpeningAnswer.NO_MORE_TIPS)

        assertFalse(ledger.observe().first())
        assertNull(List(OpeningRules.SPACING) { ouvrir() }.last())
    }

    @Test
    fun `pendant le tour, rien ne parait`() = runTest {
        // C'est le tour qui parle, et deux voix a la fois ne se lisent pas.
        situation = situation.copy(tourSeen = false)

        assertNull(troisiemeOuverture())
    }

    @Test
    fun `un rapport de plantage passe seul`() = runTest {
        situation = situation.copy(crashPending = true)

        assertNull(troisiemeOuverture())
    }

    @Test
    fun `la demande d avis attend une semaine`() = runTest {
        situation = situation.copy(dishes = 50)

        // Trois ouvertures dans la journee : dix plats, mais pas une semaine.
        assertEquals(OpeningMessage.Tip(OpeningTip.FREE_KEY), troisiemeOuverture())
    }

    @Test
    fun `une semaine et dix plats, et la demande d avis passe devant l astuce`() = runTest {
        situation = situation.copy(dishes = OpeningRules.RATING_AFTER_DISHES)
        ouvrir()
        clock.instant = clock.instant.plus(OpeningRules.RATING_AFTER)

        assertEquals(OpeningMessage.Rating, deuxOuverturesPlusTard())
    }

    @Test
    fun `neuf plats ne suffisent pas`() = runTest {
        situation = situation.copy(dishes = OpeningRules.RATING_AFTER_DISHES - 1)
        ouvrir()
        clock.instant = clock.instant.plus(OpeningRules.RATING_AFTER)

        assertEquals(OpeningMessage.Tip(OpeningTip.FREE_KEY), deuxOuverturesPlusTard())
    }

    @Test
    fun `plus tard repousse la demande de vingt ouvertures`() = runTest {
        demandeAvisDue()
        messages.answer(OpeningAnswer.LATER)

        val ensuite = List(OpeningRules.RATING_SNOOZE) { ouvrir() }

        assertFalse(OpeningMessage.Rating in ensuite.dropLast(1), "la demande revient trop tot")
        assertEquals(OpeningMessage.Rating, ensuite.last())
    }

    @Test
    fun `une etoile, deja note ou ne plus demander closent la demande pour de bon`() = runTest {
        listOf(OpeningAnswer.RATED, OpeningAnswer.ALREADY_RATED, OpeningAnswer.NEVER).forEach { reponse ->
            ledger.record = ledger.record.copy(ratingClosed = false, lastMessageAt = null)
            demandeAvisDue()

            messages.answer(reponse)

            val ensuite = List(OpeningRules.RATING_SNOOZE * 2) { ouvrir() }
            assertFalse(OpeningMessage.Rating in ensuite, "$reponse laisse revenir la demande")
        }
    }

    // --- Decor ------------------------------------------------------------------

    /** Une heure plus tard, l'accueil parait, puis s'efface. */
    private suspend fun ouvrir(): OpeningMessage? {
        clock.instant = clock.instant.plus(Duration.ofHours(1))
        return messages.onHomeStarted().also { messages.onHomeStopped() }
    }

    private suspend fun revenirApres(absence: Duration) {
        clock.instant = clock.instant.plus(absence)
        messages.onHomeStarted()
    }

    /** Les deux premières ouvertures, muettes, puis celle qui peut parler. */
    private suspend fun troisiemeOuverture(): OpeningMessage? {
        repeat(OpeningRules.FIRST_MESSAGE - 1) { ouvrir() }
        return ouvrir()
    }

    /** Les deux ouvertures qui mènent à la troisième, celle qui peut parler. */
    private suspend fun deuxOuverturesPlusTard(): OpeningMessage? {
        ouvrir()
        return ouvrir()
    }

    /**
     * Une semaine d'usage, assez de plats, et les ouvertures **jusqu'à celle qui demande un
     * avis**, pas une de plus : la suite du cas compte à partir d'elle.
     */
    private suspend fun demandeAvisDue() {
        situation = situation.copy(dishes = OpeningRules.RATING_AFTER_DISHES)
        if (ledger.record.firstOpenedAt == null) ouvrir()
        clock.instant = clock.instant.plus(OpeningRules.RATING_AFTER)
        repeat(OpeningRules.FIRST_MESSAGE + OpeningRules.SPACING) {
            if (ouvrir() == OpeningMessage.Rating) return
        }
        error("le decor n'a pas amene la demande d'avis")
    }
}
