package app.hexavore.feature.settings

import app.hexavore.core.testing.FixedClock
import app.hexavore.core.testing.InMemoryAiCredentials
import app.hexavore.core.testing.InMemoryDiaryRepository
import app.hexavore.core.testing.InMemoryKeyRejection
import app.hexavore.core.testing.InMemoryNoticeSettings
import app.hexavore.core.testing.InMemoryOpeningLedger
import app.hexavore.core.testing.InMemoryReminderSettings
import app.hexavore.core.testing.InMemoryWeightLog
import app.hexavore.domain.notice.Notice
import app.hexavore.domain.notice.NoticeSettings
import app.hexavore.domain.reminder.Reminder
import app.hexavore.domain.reminder.ReminderScheduler
import app.hexavore.domain.reminder.ReminderSetup
import app.hexavore.domain.usecase.ChooseReminder
import app.hexavore.domain.usecase.ObserveNotices
import app.hexavore.domain.usecase.ObserveReminders
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalTime

/** Les pastilles, les astuces et les rappels, réglés depuis un seul écran. */
@OptIn(ExperimentalCoroutinesApi::class)
internal class NoticeSettingsViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val notices = InMemoryNoticeSettings()
    private val reminders = InMemoryReminderSettings()
    private val ledger = InMemoryOpeningLedger()
    private val planned = mutableListOf<ReminderSetup>()

    @BeforeEach
    fun installer() = Dispatchers.setMain(dispatcher)

    @AfterEach
    fun retirer() = Dispatchers.resetMain()

    @Test
    fun `eteindre une pastille l'enregistre et la montre`() = runTest(dispatcher) {
        val modele = modele()
        backgroundScope.launch { modele.uiState.collect { } }

        modele.onToggle(Notice.AI_NOT_CONFIGURED, enabled = false)

        assertFalse(Notice.AI_NOT_CONFIGURED in modele.uiState.value.enabled)
    }

    @Test
    fun `eteindre les astuces l'enregistre`() = runTest(dispatcher) {
        val modele = modele()
        backgroundScope.launch { modele.uiState.collect { } }

        modele.onToggleTips(false)

        assertFalse(modele.uiState.value.tips)
    }

    @Test
    fun `un rappel regle ici se replanifie`() = runTest(dispatcher) {
        val modele = modele()
        backgroundScope.launch { modele.uiState.collect { } }

        modele.onToggleReminder(Reminder.LUNCH, enabled = false)
        modele.onReminderTime(Reminder.DINNER, LocalTime.of(20, 0))

        assertFalse(Reminder.LUNCH in modele.uiState.value.reminders)
        assertEquals(LocalTime.of(20, 0), modele.uiState.value.reminders[Reminder.DINNER])
        assertEquals(2, planned.size, "chaque geste replanifie")
    }

    @Test
    fun `un reglage illisible laisse l'ecran par defaut`() = runTest(dispatcher) {
        val illisible = object : NoticeSettings {
            override fun observe(): Flow<Set<Notice>> = flow { error("fichier abime") }

            override suspend fun setEnabled(notice: Notice, enabled: Boolean) = Unit
        }
        val modele = modele(illisible)
        backgroundScope.launch { modele.uiState.collect { } }

        assertEquals(NoticeUiState(), modele.uiState.value)
        assertTrue(modele.uiState.value.tips)
    }

    private fun modele(settings: NoticeSettings = notices) = NoticeSettingsViewModel(
        settings = settings,
        chooseReminder = ChooseReminder(
            reminders,
            object : ReminderScheduler {
                override suspend fun reschedule(setup: ReminderSetup) {
                    planned += setup
                }
            },
        ),
        tips = ledger,
        observeNotices = ObserveNotices(
            settings,
            InMemoryAiCredentials(),
            InMemoryKeyRejection(),
            InMemoryWeightLog(),
            InMemoryDiaryRepository(),
            FixedClock.atNoon(LocalDate.of(2026, 3, 15)),
        ),
        observeReminders = ObserveReminders(reminders),
    )
}
