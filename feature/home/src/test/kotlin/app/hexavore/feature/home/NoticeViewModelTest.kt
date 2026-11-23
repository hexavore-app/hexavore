package app.hexavore.feature.home

import app.hexavore.core.testing.FixedClock
import app.hexavore.core.testing.InMemoryAiCredentials
import app.hexavore.core.testing.InMemoryDiaryRepository
import app.hexavore.core.testing.InMemoryKeyRejection
import app.hexavore.core.testing.InMemoryNoticeSettings
import app.hexavore.core.testing.InMemoryWeightLog
import app.hexavore.domain.notice.Notice
import app.hexavore.domain.usecase.ObserveNotices
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDate

/** Les pastilles de l'accueil : ce que la règle allume, et rien sur une panne. */
@OptIn(ExperimentalCoroutinesApi::class)
internal class NoticeViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val diary = InMemoryDiaryRepository()

    @BeforeEach
    fun installer() = Dispatchers.setMain(dispatcher)

    @AfterEach
    fun retirer() = Dispatchers.resetMain()

    @Test
    fun `sans cle, la pastille de l'IA s'allume`() = runTest(dispatcher) {
        val modele = modele()
        backgroundScope.launch { modele.notices.collect { } }

        assertTrue(Notice.AI_NOT_CONFIGURED in modele.notices.value)
    }

    @Test
    fun `une lecture qui echoue n'allume rien`() = runTest(dispatcher) {
        diary.failure = IllegalStateException("base illisible")
        val modele = modele()
        backgroundScope.launch { modele.notices.collect { } }

        assertEquals(emptySet<Notice>(), modele.notices.value)
    }

    private fun modele() = NoticeViewModel(
        ObserveNotices(
            InMemoryNoticeSettings(),
            InMemoryAiCredentials(),
            InMemoryKeyRejection(),
            InMemoryWeightLog(),
            diary,
            FixedClock.atNoon(LocalDate.of(2026, 3, 15)),
        ),
    )
}
