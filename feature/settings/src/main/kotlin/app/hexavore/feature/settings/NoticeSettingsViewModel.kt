package app.hexavore.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.hexavore.domain.notice.Notice
import app.hexavore.domain.notice.NoticeSettings
import app.hexavore.domain.reminder.Reminder
import app.hexavore.domain.reminder.ReminderSetup
import app.hexavore.domain.usecase.ChooseReminder
import app.hexavore.domain.usecase.ObserveNotices
import app.hexavore.domain.usecase.ObserveReminders
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Les quatre interrupteurs, et ce qu'ils allument en ce moment.
 *
 * **Les deux ensemble et non l'un sans l'autre.** Un interrupteur qui dit seulement
 * « allumee » laisse celui qui l'a mise en route se demander si elle sert : l'ecran
 * montre aussi laquelle est **active maintenant**, c'est-a-dire ce qu'il se passerait
 * si l'on retournait a l'accueil. C'est ce qui permet de comprendre ce qu'une pastille
 * surveille sans lire de documentation.
 */
@HiltViewModel
internal class NoticeSettingsViewModel @Inject constructor(
    private val settings: NoticeSettings,
    private val chooseReminder: ChooseReminder,
    observeNotices: ObserveNotices,
    observeReminders: ObserveReminders,
) : ViewModel() {
    val uiState: StateFlow<NoticeUiState> =
        combine(settings.observe(), observeNotices(), observeReminders()) { allumees, actives, rappels ->
            NoticeUiState(enabled = allumees, active = actives, reminders = rappels)
        }
            .catch { emit(NoticeUiState()) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MILLIS), NoticeUiState())

    fun onToggle(notice: Notice, enabled: Boolean) {
        viewModelScope.launch { settings.setEnabled(notice, enabled) }
    }

    /**
     * Regler un rappel le **replace** dans le temps, et c'est le cas d'usage qui le
     * tient : l'ecran n'a pas a savoir qu'un travail planifie existe.
     */
    fun onToggleReminder(reminder: Reminder, enabled: Boolean) {
        viewModelScope.launch { chooseReminder.setEnabled(reminder, enabled) }
    }

    fun onReminderTime(reminder: Reminder, time: java.time.LocalTime) {
        viewModelScope.launch { chooseReminder.setTime(reminder, time) }
    }

    private companion object {
        const val SUBSCRIPTION_TIMEOUT_MILLIS = 5_000L
    }
}

/**
 * Ce que l'ecran des notifications montre.
 *
 * [active] est **contenu dans** [enabled] par construction : une pastille eteinte n'est
 * jamais active, puisque le cas d'usage filtre avant d'evaluer. L'ecran s'en sert pour
 * dire « en ce moment » a cote de l'interrupteur.
 */
internal data class NoticeUiState(
    val enabled: Set<Notice> = Notice.entries.toSet(),
    val active: Set<Notice> = emptySet(),
    /** Les quatre rappels : lesquels sonnent, et a quelle heure. */
    val reminders: ReminderSetup = ReminderSetup(),
)
