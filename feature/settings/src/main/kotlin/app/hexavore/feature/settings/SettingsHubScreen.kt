package app.hexavore.feature.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
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

/**
 * Le hub de réglages, **né avec sa deuxième section**.
 *
 * [D59][decisions] l'avait remis à plus tard pour une raison précise : un écran de
 * transit qui ne désigne qu'une seule destination est un écran de trop, et quatre
 * entrées qui n'ouvrent rien ne sont pas une avance. La deuxième section arrive, donc
 * lui aussi — c'est exactement l'échéance qui avait été écrite.
 *
 * **Sauvegarde arrive avec son écran**, et pas avant : c'est la même règle qui l'avait
 * tenue dehors. **Apparence arrive de la même façon**, le jour où le thème est devenu
 * réglable. ~~À propos n'y figure toujours pas~~, et pour la raison inchangée : elle
 * n'ouvrirait rien.
 *
 * [parcours]: docs/02-parcours-et-ecrans.md
 * [decisions]: docs/11-decisions.md
 */
/**
 * Le hub, branché sur les pastilles.
 *
 * Il emprunte le modèle de l'écran des notifications plutôt que d'en avoir un à lui :
 * c'est le même flux, lu au lieu d'être écrit, et un second modèle aurait fait deux
 * abonnements pour une seule question.
 */
@Composable
internal fun SettingsHubRoute(
    sections: SettingsSections,
    onClose: () -> Unit,
    viewModel: NoticeSettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    SettingsHubScreen(
        sections = sections,
        onClose = onClose,
        aiFlagged = state.active.any { it == Notice.AI_NOT_CONFIGURED || it == Notice.AI_KEY_REJECTED },
    )
}

/**
 * Les sept portes du hub, réunies.
 *
 * **Nées quand le seuil de paramètres a mordu**, à la septième section, et le
 * regroupement suit ce que les choses sont : sept lambdas qui font toutes la même chose,
 * ouvrir un écran. C'est la forme de `HomeActions` et de `EntryActions`, appliquée
 * là où le même symptôme est apparu.
 */
@Immutable
internal data class SettingsSections(
    val onOpenProfile: () -> Unit,
    val onOpenAi: () -> Unit,
    val onOpenContribution: () -> Unit,
    val onOpenBackup: () -> Unit,
    val onOpenPhotos: () -> Unit,
    val onOpenNotices: () -> Unit,
    val onOpenAppearance: () -> Unit,
)

@Composable
internal fun SettingsHubScreen(
    sections: SettingsSections,
    onClose: () -> Unit,
    /** La section d'IA porte une pastille : aucune clé ne sert, ou la dernière a été refusée. */
    aiFlagged: Boolean = false,
) {
    Scaffold(
        topBar = {
            ScreenTopBar(
                title = stringResource(R.string.settings_title),
                onClose = onClose,
                closeLabel = stringResource(R.string.settings_close),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = Spacing.screenMargin),
            verticalArrangement = Arrangement.spacedBy(Spacing.betweenCards),
        ) {
            SectionCard(
                titleRes = R.string.settings_profile_title,
                subtitleRes = R.string.settings_profile_subtitle,
                onClick = sections.onOpenProfile,
            )
            SectionCard(
                titleRes = R.string.settings_ai_title,
                subtitleRes = R.string.settings_ai_subtitle,
                onClick = sections.onOpenAi,
                flagged = aiFlagged,
            )
            SectionCard(
                titleRes = R.string.settings_contribution_title,
                subtitleRes = R.string.settings_contribution_subtitle,
                onClick = sections.onOpenContribution,
            )
            SectionCard(
                titleRes = R.string.settings_backup_title,
                subtitleRes = R.string.settings_backup_subtitle,
                onClick = sections.onOpenBackup,
            )
            // Juste apres la sauvegarde : les deux parlent de ce que l'application
            // garde sur le telephone, et de ce qu'on peut en effacer.
            SectionCard(
                titleRes = R.string.settings_photos_title,
                subtitleRes = R.string.settings_photos_subtitle,
                onClick = sections.onOpenPhotos,
            )
            SectionCard(
                titleRes = R.string.settings_notices_title,
                subtitleRes = R.string.settings_notices_subtitle,
                onClick = sections.onOpenNotices,
            )
            SectionCard(
                titleRes = R.string.settings_appearance_title,
                subtitleRes = R.string.settings_appearance_subtitle,
                onClick = sections.onOpenAppearance,
            )
        }
    }
}

@Composable
private fun SectionCard(
    @StringRes titleRes: Int,
    @StringRes subtitleRes: Int,
    onClick: () -> Unit,
    flagged: Boolean = false,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(Spacing.cardPadding),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = stringResource(titleRes), style = MaterialTheme.typography.titleMedium)
                if (flagged) {
                    NoticeDot(
                        label = stringResource(R.string.settings_section_flagged),
                        modifier = Modifier.padding(start = Spacing.sm),
                    )
                }
            }
            Text(
                text = stringResource(subtitleRes),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
