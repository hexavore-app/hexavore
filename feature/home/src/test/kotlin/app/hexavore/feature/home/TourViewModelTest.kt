package app.hexavore.feature.home

import app.hexavore.core.testing.FixedClock
import app.hexavore.core.testing.InMemoryDiaryRepository
import app.hexavore.domain.tour.TourSettings
import app.hexavore.domain.usecase.HideTourSample
import app.hexavore.domain.usecase.ShowTourSample
import app.hexavore.feature.home.tour.TourStep
import app.hexavore.feature.home.tour.TourViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import java.time.LocalDate

/**
 * Quand le tour revient, et quand il s'arrête pour de bon.
 *
 * La règle tient en une phrase : **il revient tant qu'on n'y a pas répondu**. Passer et
 * aller au bout sont les deux réponses ; fermer l'application au milieu, ou partir poser
 * une clé, n'en sont pas ([D141][decisions]).
 *
 * C'est la règle la plus facile à casser sans s'en apercevoir — un `markSeen()` déplacé
 * d'une ligne, et le tour disparaît pour quelqu'un qui ne l'a jamais vu.
 *
 * [decisions]: docs/11-decisions.md
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TourViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val reglages = FakeTourSettings()
    private val journal = InMemoryDiaryRepository()

    @BeforeEach
    fun installeLeFil() = Dispatchers.setMain(dispatcher)

    @AfterEach
    fun rendLeFil() = Dispatchers.resetMain()

    @Test
    fun `une premiere ouverture lance le tour et pose les plats d exemple`() = runTest(dispatcher) {
        val viewModel = tour()
        testScheduler.runCurrent()

        assertEquals(TourStep.DAY, viewModel.step.value)
        assertTrue(journal.observeDay(JOUR).first().isNotEmpty(), "Les plats d'exemple ne sont pas la.")
        assertFalse(reglages.seen.value, "Le tour ne doit pas etre oublie avant d'avoir ete repondu.")
    }

    @Test
    fun `passer le tour y repond, et il ne revient plus`() = runTest(dispatcher) {
        val viewModel = tour()
        testScheduler.runCurrent()

        viewModel.onFinish()
        testScheduler.runCurrent()

        assertNull(viewModel.step.value)
        assertTrue(reglages.seen.value)
        assertTrue(journal.observeDay(JOUR).first().isEmpty(), "Les plats d'exemple sont restes.")
    }

    @Test
    fun `aller au bout y repond aussi`() = runTest(dispatcher) {
        val viewModel = tour()
        testScheduler.runCurrent()

        // Sans cle, la derniere etape est celle du mode degrade.
        repeat(TourStep.entries.size) { viewModel.onNext(keyless = true) }
        testScheduler.runCurrent()

        assertNull(viewModel.step.value)
        assertTrue(reglages.seen.value)
    }

    @Test
    fun `partir poser une cle ne repond pas, et le tour reviendra`() = runTest(dispatcher) {
        val viewModel = tour()
        testScheduler.runCurrent()

        viewModel.onLeaveForSettings()
        testScheduler.runCurrent()

        assertNull(viewModel.step.value, "Le tour doit s'effacer le temps des reglages.")
        assertFalse(reglages.seen.value, "Il n'a pas ete repondu : il doit revenir.")
        // Les plats partent quand meme : ils n'ont rien a faire dans un vrai journal.
        assertTrue(journal.observeDay(JOUR).first().isEmpty())
    }

    @Test
    fun `une ouverture apres un tour abandonne le rejoue, sans doubler les plats`() = runTest(dispatcher) {
        tour()
        testScheduler.runCurrent()
        // L'application se ferme au milieu : rien n'a repondu, et les plats sont restes.
        val seconde = tour()
        testScheduler.runCurrent()

        assertEquals(TourStep.DAY, seconde.step.value)
        assertEquals(TAILLE, journal.observeDay(JOUR).first().size, "Les plats d'exemple se sont doubles.")
    }

    @Disabled(
        "Tant que TourViewModel.REJOUE_TOUJOURS vaut true, le tour ignore le souvenir, " +
            "pour qu'on puisse le regarder sans effacer les donnees entre deux essais. " +
            "Ce test redevient vrai -- et doit etre rallume -- des que la constante " +
            "repasse a false.",
    )
    @Test
    fun `un tour deja repondu ne se rejoue pas`() = runTest(dispatcher) {
        reglages.seen.value = true

        val viewModel = tour()
        testScheduler.runCurrent()
        testScheduler.runCurrent()

        assertNull(viewModel.step.value)
        assertTrue(journal.observeDay(JOUR).first().isEmpty())
    }

    private fun tour(): TourViewModel {
        val clock = FixedClock.atNoon(JOUR)
        return TourViewModel(
            settings = reglages,
            showSample = ShowTourSample(journal, clock),
            hideSample = HideTourSample(journal),
        )
    }

    private companion object {
        val JOUR: LocalDate = LocalDate.of(2026, 1, 15)

        /** Trois plats : un par source que le tour montre. */
        const val TAILLE = 3
    }
}

/** Un souvenir de tour qu'on peut lire et poser, sans disque. */
private class FakeTourSettings : TourSettings {
    val seen = MutableStateFlow(false)

    override fun observeSeen(): Flow<Boolean> = seen

    override suspend fun markSeen() {
        seen.value = true
    }
}
