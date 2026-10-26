package app.hexavore.feature.home

import app.hexavore.core.testing.FixedClock
import app.hexavore.core.testing.InMemoryOpeningLedger
import app.hexavore.domain.opening.OpeningAnswer
import app.hexavore.domain.opening.OpeningMessage
import app.hexavore.domain.opening.OpeningRecord
import app.hexavore.domain.opening.OpeningSituation
import app.hexavore.domain.opening.OpeningTip
import app.hexavore.domain.usecase.OpeningMessages
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.LocalDate

/**
 * L'accueil et le message de son ouverture ([D179][decisions]).
 *
 * Le rythme se juge dans le domaine ; ce qui se juge ici est ce que l'écran en fait : il
 * montre ce qu'on lui rend, le garde à travers une rotation — qui fait repasser l'accueil
 * au premier plan sans être une ouverture —, et le referme à la première réponse.
 *
 * [decisions]: docs/11-decisions.md
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal class OpeningMessageViewModelTest {
    private val clock = FixedClock.atNoon(LocalDate.of(2026, 10, 25))

    /** Deux ouvertures déjà comptées, la dernière il y a deux heures : la suivante peut parler. */
    private val ledger = InMemoryOpeningLedger(
        OpeningRecord(openings = 2, lastSeenAt = clock.now().minus(Duration.ofHours(2)), firstOpenedAt = clock.now()),
    )
    private val situation = OpeningSituation(aiConfigured = false, tourSeen = true, crashPending = false, dishes = 0)
    private val viewModel = OpeningMessageViewModel(OpeningMessages(ledger, ledger, { situation }, clock))

    @BeforeEach
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterEach
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `le message de l ouverture se montre, et une rotation ne l efface pas`() = runTest {
        viewModel.onStarted()
        val montre = viewModel.message.value

        viewModel.onStopped()
        viewModel.onStarted()

        assertEquals(OpeningMessage.Tip(OpeningTip.FREE_KEY), montre)
        assertEquals(montre, viewModel.message.value, "le passage d'une rotation n'est pas une ouverture")
    }

    @Test
    fun `repondre referme la boite, et la reponse est retenue`() = runTest {
        viewModel.onStarted()

        viewModel.onAnswer(OpeningAnswer.NO_MORE_TIPS)

        assertNull(viewModel.message.value)
        assertFalse(ledger.observe().first(), "les astuces doivent s'eteindre")
    }
}
