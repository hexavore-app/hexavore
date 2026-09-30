package app.hexavore.feature.onboarding

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.hexavore.core.designsystem.component.NeonChip
import app.hexavore.core.designsystem.theme.Spacing
import app.hexavore.domain.profile.WeeklySessions

/**
 * Le nombre de séances, en pastilles.
 *
 * **Sur une ligne qui défile**, et non empilées comme les métiers : ce sont six
 * réponses à un chiffre, et six lignes pleine largeur pour écrire « 0 », « 1 », « 2 »
 * feraient un formulaire là où il y a une graduation.
 *
 * La dernière porte « 5 et plus » : au-delà, l'écart entre six et sept séances ne
 * change presque rien au besoin calculé, et prétendre le contraire donnerait une
 * précision que la formule ne tient pas.
 */
@Composable
internal fun SessionPicker(selected: WeeklySessions, onPick: (WeeklySessions) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        SESSION_CHOICES.forEach { count ->
            val label = if (count == WeeklySessions.MAX_SESSIONS) {
                stringResource(R.string.onboarding_sessions_max, count)
            } else {
                count.toString()
            }
            NeonChip(
                label = label,
                selected = count == selected.count,
                onClick = { onPick(WeeklySessions(count)) },
            )
        }
    }
}

/** Les six reponses possibles, listees une fois : une plage parcourue est une boucle deguisee. */
private val SESSION_CHOICES = (0..WeeklySessions.MAX_SESSIONS).toList()
