package app.hexavore.feature.progress

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.hexavore.core.designsystem.component.ScreenTopBar
import app.hexavore.core.designsystem.theme.Spacing
import app.hexavore.domain.progress.Progress

/** L'écran de progression, branché sur le graphe d'injection. */
@Composable
internal fun ProgressRoute(onClose: () -> Unit, viewModel: ProgressViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    ProgressScreen(progress = state, onClose = onClose)
}

/**
 * Où l'on en est, déplié.
 *
 * **L'ordre va du présent au passé** : les deux séries d'abord — c'est ce qui est en
 * jeu aujourd'hui —, le niveau ensuite, et les paliers en dernier, qui sont une
 * histoire. Un écran qui ouvrirait sur dix-huit trophées dont deux pris dirait d'abord
 * ce qui manque.
 *
 * **Aucun chiffre n'y est un jugement.** C'est la règle de l'accueil ([docs/02][parcours])
 * et elle vaut ici : pas de rouge sur une série cassée, pas de message sur une journée
 * ratée. Une série à zéro se lit « 0 jour », et c'est tout ce qu'il y a à en dire.
 *
 * [parcours]: docs/02-parcours-et-ecrans.md
 */
@Composable
internal fun ProgressScreen(progress: Progress, onClose: () -> Unit, modifier: Modifier = Modifier) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            ScreenTopBar(
                title = stringResource(R.string.progress_title),
                onClose = onClose,
                closeLabel = stringResource(R.string.progress_close),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = Spacing.screenMargin)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(Spacing.xl),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.lg),
            ) {
                StreakCard(
                    days = progress.streaks.logging,
                    label = stringResource(R.string.progress_streak_logging),
                    hint = stringResource(R.string.progress_streak_logging_hint),
                    modifier = Modifier.weight(1f),
                )
                StreakCard(
                    days = progress.streaks.perfect,
                    label = stringResource(R.string.progress_streak_perfect),
                    hint = stringResource(R.string.progress_streak_perfect_hint),
                    modifier = Modifier.weight(1f),
                )
            }

            LevelBlock(progress)
            BadgeSection(progress)
        }
    }
}

/**
 * Une série, en grand.
 *
 * Le chiffre porte le poids visuel et le mot dit de quelle série il s'agit : les deux
 * cartes sont côte à côte, et deux nombres sans étiquette au même endroit ne se
 * distinguent pas.
 */
@Composable
private fun StreakCard(days: Int, label: String, hint: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Text(text = days.toString(), style = MaterialTheme.typography.displayMedium)
        Text(
            text = pluralStringResource(R.plurals.progress_days, days, days),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(text = label, style = MaterialTheme.typography.titleSmall, textAlign = TextAlign.Center)
        Text(
            text = hint,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * Le niveau, et ce qu'il reste avant le suivant.
 *
 * La jauge dit la distance mieux que deux nombres : « 340 sur 900 » demande une
 * division, une barre à un tiers se lit d'un coup d'œil. Les nombres restent en
 * dessous, pour qui veut savoir exactement.
 */
@Composable
private fun LevelBlock(progress: Progress) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Text(
            text = stringResource(R.string.progress_level, progress.level.number),
            style = MaterialTheme.typography.headlineSmall,
        )
        LinearProgressIndicator(
            progress = { progress.level.fraction },
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = stringResource(
                R.string.progress_level_points,
                progress.level.pointsInto,
                progress.level.pointsToNext,
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
