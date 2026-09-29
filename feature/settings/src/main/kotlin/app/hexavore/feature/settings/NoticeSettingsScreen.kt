package app.hexavore.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.hexavore.core.designsystem.component.NoticeDot
import app.hexavore.core.designsystem.component.ScreenTopBar
import app.hexavore.core.designsystem.theme.Spacing
import app.hexavore.domain.notice.Notice
import app.hexavore.domain.reminder.Reminder
import java.time.LocalTime

/**
 * Les quatre pastilles, et leur interrupteur.
 *
 * **Deux sortes, et l'écran dit laquelle est laquelle** ([D134][decisions]). Les
 * **pastilles** sont des points colorés que l'on voit en ouvrant l'application ; les
 * **rappels** sonnent dehors, demandent une permission et un travail de fond. Ils
 * partagent cet écran parce qu'ils répondent à la même question — de quoi l'application
 * a-t-elle le droit de me parler — et les séparer obligerait à chercher dans deux
 * endroits pour faire taire ce qui agace.
 *
 * [decisions]: docs/11-decisions.md
 *
 * **Chaque ligne montre si sa pastille est allumée *maintenant*.** Un interrupteur seul
 * laisse celui qui vient de l'activer se demander ce qu'il surveille ; le point à côté
 * du libellé répond sans documentation — c'est exactement ce qu'on verrait en revenant
 * à l'accueil.
 */
@Composable
internal fun NoticeSettingsRoute(onClose: () -> Unit, viewModel: NoticeSettingsViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    NoticeSettingsScreen(
        state = state,
        onToggle = viewModel::onToggle,
        onToggleReminder = viewModel::onToggleReminder,
        onReminderTime = viewModel::onReminderTime,
        onClose = onClose,
    )
}

@Composable
private fun NoticeSettingsScreen(
    state: NoticeUiState,
    onToggle: (Notice, Boolean) -> Unit,
    onToggleReminder: (Reminder, Boolean) -> Unit,
    onReminderTime: (Reminder, LocalTime) -> Unit,
    onClose: () -> Unit,
) {
    Scaffold(
        topBar = {
            ScreenTopBar(
                title = stringResource(R.string.notices_title),
                onClose = onClose,
                closeLabel = stringResource(R.string.notices_close),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = Spacing.screenMargin)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(Spacing.betweenCards),
        ) {
            Text(
                text = stringResource(R.string.notices_intro),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // L'ordre de l'enumeration, et non un ordre d'ecran : une cinquieme
            // pastille apparait ici sans qu'une ligne d'affichage bouge.
            Notice.entries.forEach { notice ->
                NoticeRow(
                    notice = notice,
                    enabled = notice in state.enabled,
                    active = notice in state.active,
                    onToggle = { onToggle(notice, it) },
                )
            }

            // Les rappels viennent apres les pastilles, et l'ordre dit leur cout :
            // les unes se voient en ouvrant l'application, les autres sonnent dehors.
            ReminderSection(setup = state.reminders, onToggle = onToggleReminder, onTime = onReminderTime)
        }
    }
}

@Composable
private fun NoticeRow(notice: Notice, enabled: Boolean, active: Boolean, onToggle: (Boolean) -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(Spacing.cardPadding),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = stringResource(notice.titleRes), style = MaterialTheme.typography.titleSmall)
                    if (active) {
                        NoticeDot(
                            label = stringResource(R.string.notices_active_now),
                            modifier = Modifier.padding(start = Spacing.sm),
                        )
                    }
                }
                Text(
                    text = stringResource(notice.bodyRes),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = enabled, onCheckedChange = onToggle)
        }
    }
}

/**
 * Le titre d'une pastille, et ce qu'elle surveille.
 *
 * Deux tables plutôt qu'un `when` dans la composable, pour la même raison qu'ailleurs :
 * une cinquième entrée dans l'énumération **ne compile pas** tant que ses deux libellés
 * n'existent pas, là où un `when` avec un `else` l'aurait affichée sans nom.
 */
private val Notice.titleRes: Int
    get() = when (this) {
        Notice.AI_NOT_CONFIGURED -> R.string.notices_ai_absent_title
        Notice.AI_KEY_REJECTED -> R.string.notices_ai_rejected_title
        Notice.WEIGHT_STALE -> R.string.notices_weight_title
        Notice.YESTERDAY_EMPTY -> R.string.notices_yesterday_title
    }

private val Notice.bodyRes: Int
    get() = when (this) {
        Notice.AI_NOT_CONFIGURED -> R.string.notices_ai_absent_body
        Notice.AI_KEY_REJECTED -> R.string.notices_ai_rejected_body
        Notice.WEIGHT_STALE -> R.string.notices_weight_body
        Notice.YESTERDAY_EMPTY -> R.string.notices_yesterday_body
    }
