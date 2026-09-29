package app.hexavore.feature.settings

import android.Manifest
import android.app.TimePickerDialog
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import app.hexavore.core.designsystem.theme.Spacing
import app.hexavore.domain.reminder.Reminder
import app.hexavore.domain.reminder.ReminderSetup
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * Les quatre rappels, leurs interrupteurs et leurs heures.
 *
 * **Sous les pastilles, sur le même écran** ([D134][decisions]). Les deux répondent à la
 * même question — *de quoi l'application a-t-elle le droit de me parler* — et les
 * séparer obligerait à chercher dans deux endroits pour faire taire ce qui agace. Ce
 * qui les distingue est dit en une phrase en tête de section, parce que c'est une vraie
 * différence : les unes se voient en ouvrant l'application, les autres sonnent dehors.
 *
 * **L'heure se touche.** Un rappel dont on ne peut pas déplacer l'heure est un rappel
 * qu'on éteint : personne ne déjeune à la même heure, et 12 h 30 n'est qu'un défaut.
 *
 * [decisions]: docs/11-decisions.md
 */
@Composable
internal fun ReminderSection(
    setup: ReminderSetup,
    onToggle: (Reminder, Boolean) -> Unit,
    onTime: (Reminder, LocalTime) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.betweenCards)) {
        Text(text = stringResource(R.string.reminders_title), style = MaterialTheme.typography.titleMedium)
        Text(
            text = stringResource(R.string.reminders_intro),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        PermissionNotice(anyEnabled = setup.enabled.isNotEmpty())

        // L'ordre de l'enumeration, comme pour les pastilles : un cinquieme rappel
        // apparait ici sans qu'une ligne d'affichage bouge.
        Reminder.entries.forEach { reminder ->
            ReminderRow(
                reminder = reminder,
                enabled = reminder in setup,
                time = setup[reminder],
                onToggle = { onToggle(reminder, it) },
                onTime = { onTime(reminder, it) },
            )
        }
    }
}

/**
 * Ce qu'on dit quand le système, lui, s'y oppose.
 *
 * **Demandée ici et pas au démarrage** : une permission réclamée avant qu'on sache à
 * quoi elle sert se refuse, et Android ne la redemande plus. Celle-ci arrive à
 * l'endroit où quelqu'un vient de régler un rappel — le seul moment où la question a un
 * sens.
 *
 * Rien du tout quand elle est accordée, ou quand la version d'Android ne la demande
 * pas : une ligne qui dit « tout va bien » est une ligne de trop.
 */
@Composable
private fun PermissionNotice(anyEnabled: Boolean) {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(context.notificationsAllowed()) }
    val request = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }

    if (granted || !anyEnabled) return

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(Spacing.cardPadding),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            Text(text = stringResource(R.string.reminders_blocked), style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = { request.launch(Manifest.permission.POST_NOTIFICATIONS) }) {
                Text(stringResource(R.string.reminders_allow))
            }
        }
    }
}

/** Un rappel : son nom, ce qu'il dit, son heure, et son interrupteur. */
@Composable
private fun ReminderRow(
    reminder: Reminder,
    enabled: Boolean,
    time: LocalTime,
    onToggle: (Boolean) -> Unit,
    onTime: (LocalTime) -> Unit,
) {
    val context = LocalContext.current

    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(Spacing.cardPadding),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                Text(text = stringResource(reminder.titleRes), style = MaterialTheme.typography.titleSmall)
                Text(
                    text = stringResource(reminder.bodyRes),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                // Grise quand le rappel est eteint, mais toujours tapable : regler
                // l'heure avant d'allumer est un ordre de gestes comme un autre.
                TextButton(onClick = { context.pickTime(time, onTime) }) {
                    Text(text = time.format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)))
                }
            }
            Switch(checked = enabled, onCheckedChange = onToggle)
        }
    }
}

/**
 * Le sélecteur d'heure du système, et **pas un champ à taper**.
 *
 * C'est la règle que l'onboarding applique déjà à la date de naissance : un vrai
 * sélecteur, jamais une saisie au format imposé. Celui du système apporte le format
 * 12 ou 24 heures de l'appareil, que l'application n'a pas à deviner.
 */
private fun Context.pickTime(current: LocalTime, onPicked: (LocalTime) -> Unit) {
    TimePickerDialog(
        this,
        { _, hour, minute -> onPicked(LocalTime.of(hour, minute)) },
        current.hour,
        current.minute,
        android.text.format.DateFormat.is24HourFormat(this),
    ).show()
}

/** La permission, à la seule version qui la demande. */
private fun Context.notificationsAllowed(): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
    ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
    PackageManager.PERMISSION_GRANTED

/**
 * Le titre d'un rappel, et ce qu'il dit.
 *
 * Deux tables plutôt qu'un `when` dans la composable, comme pour les pastilles : un
 * cinquième rappel **ne compile pas** tant que ses deux libellés n'existent pas.
 */
private val Reminder.titleRes: Int
    get() = when (this) {
        Reminder.BREAKFAST -> R.string.reminders_breakfast_title
        Reminder.LUNCH -> R.string.reminders_lunch_title
        Reminder.DINNER -> R.string.reminders_dinner_title
        Reminder.STREAK -> R.string.reminders_streak_title
    }

private val Reminder.bodyRes: Int
    get() = when (this) {
        Reminder.BREAKFAST -> R.string.reminders_meal_body
        Reminder.LUNCH -> R.string.reminders_meal_body
        Reminder.DINNER -> R.string.reminders_meal_body
        Reminder.STREAK -> R.string.reminders_streak_body
    }
