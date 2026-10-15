package app.hexavore.feature.weight

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.hexavore.core.designsystem.component.AdjustmentCard
import app.hexavore.core.designsystem.component.ScreenTopBar
import app.hexavore.core.designsystem.theme.Spacing
import app.hexavore.domain.goal.AdjustmentSuggestion
import app.hexavore.domain.profile.UnitSystem
import app.hexavore.domain.profile.kilogramsToPounds
import app.hexavore.domain.usecase.AdjustmentResponse
import app.hexavore.domain.usecase.TrendPoint
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
internal fun WeightRoute(onClose: () -> Unit) {
    val viewModel: WeightViewModel = hiltViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val suggestion by viewModel.suggestion.collectAsStateWithLifecycle()

    WeightScreen(
        state = state,
        today = viewModel.today(),
        onRecord = viewModel::onRecord,
        onClose = onClose,
        suggestion = suggestion,
        onAdjustment = viewModel::onAdjustment,
        onTrendShown = viewModel::onTrendShown,
    )
}

/**
 * Le journal de poids : la courbe, puis les pesées.
 *
 * **La courbe d'abord, la liste ensuite** ([docs/02][parcours]). On vient voir où l'on
 * va, pas relire trente chiffres — la liste existe pour vérifier une valeur ou en
 * corriger une, ce qui est le second usage.
 *
 * [parcours]: docs/02-parcours-et-ecrans.md
 */
@Composable
internal fun WeightScreen(
    state: WeightUiState,
    today: LocalDate,
    onRecord: (LocalDate, Double) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    /**
     * La correction que l'adaptation propose, ou `null` — le cas normal.
     *
     * **En tête, avant la courbe** ([docs/02][parcours]) : c'est ce qu'on doit voir en
     * arrivant, et non après avoir fait défiler trente pesées.
     *
     * [parcours]: docs/02-parcours-et-ecrans.md
     */
    suggestion: AdjustmentSuggestion? = null,
    onAdjustment: (AdjustmentResponse) -> Unit = {},
    /**
     * La case de la tendance, dans la légende de la courbe ([D159][decisions]).
     *
     * [decisions]: docs/11-decisions.md
     */
    onTrendShown: (Boolean) -> Unit = {},
) {
    var recording by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = { recording = true }) {
                Icon(Icons.Default.Add, contentDescription = null)
                Text(
                    text = stringResource(R.string.weight_add),
                    modifier = Modifier.padding(start = Spacing.sm),
                )
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = Spacing.screenMargin),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg),
        ) {
            WeightTitle(onClose)

            suggestion?.let {
                AdjustmentCard(
                    suggestion = it,
                    // Le meme systeme que la liste des pesees en dessous (D162).
                    units = (state as? WeightUiState.Loaded)?.units ?: UnitSystem.METRIC,
                    onAccept = { onAdjustment(AdjustmentResponse.ACCEPT) },
                    onIgnore = { onAdjustment(AdjustmentResponse.IGNORE) },
                    onStop = { onAdjustment(AdjustmentResponse.STOP) },
                )
            }

            when (state) {
                WeightUiState.Loading -> Unit
                WeightUiState.Error -> Unreadable()
                is WeightUiState.Loaded -> Journal(state, onTrendShown)
            }
        }
    }

    if (recording) {
        val loaded = state as? WeightUiState.Loaded
        RecordWeightDialog(
            today = today,
            initialKg = loaded?.trend?.latest?.weightKg,
            units = loaded?.units ?: UnitSystem.METRIC,
            heightCm = loaded?.heightCm,
            onConfirm = { date, kg ->
                onRecord(date, kg)
                recording = false
            },
            onDismiss = { recording = false },
        )
    }
}

@Composable
private fun WeightTitle(onClose: () -> Unit) {
    ScreenTopBar(
        title = stringResource(R.string.weight_title),
        onClose = onClose,
        closeLabel = stringResource(R.string.weight_close),
    )
}

/** La courbe, l'avertissement s'il y a lieu, puis les pesées. */
@Composable
private fun Journal(state: WeightUiState.Loaded, onTrendShown: (Boolean) -> Unit) {
    if (state.empty) {
        NothingWeighedYet()
        return
    }

    WeightChart(state.trend, state.units, trendShown = state.trendShown, onTrendShown = onTrendShown)
    if (state.curveMissing) Note(stringResource(R.string.weight_curve_missing))

    LazyColumn(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        items(state.trend.points.asReversed(), key = { it.date.toString() }) { Weighing(it, state.units) }
    }
}

/** Une pesée : la date, le poids, et la tendance du jour pour qui ne voit pas la courbe. */
@Composable
private fun Weighing(point: TrendPoint, units: UnitSystem) {
    // Dans le systeme de la ligne qu'elle lit (D162) : des livres a l'ecran ne
    // s'annoncent pas en kilogrammes.
    val description = when (units) {
        UnitSystem.METRIC -> stringResource(
            R.string.weight_row_smoothed_a11y,
            point.date.longLabel(),
            point.weightKg,
            point.trendKg,
        )
        UnitSystem.IMPERIAL -> stringResource(
            R.string.weight_row_smoothed_a11y_pounds,
            point.date.longLabel(),
            kilogramsToPounds(point.weightKg),
            kilogramsToPounds(point.trendKg),
        )
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            // Une ligne est un seul noeud d'accessibilite : date, poids, tendance, en
            // une phrase plutot qu'en trois arrets.
            .clearAndSetSemantics { contentDescription = description },
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = point.date.longLabel(),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = weightLabel(point.weightKg, units),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/**
 * Une pesée, dans le système choisi.
 *
 * La conversion vient du domaine : la base garde des kilogrammes, et c'est l'affichage
 * qui change — basculer de réglage ne réécrit pas une seule pesée.
 */
@Composable
internal fun weightLabel(kilograms: Double, units: UnitSystem): String = when (units) {
    UnitSystem.METRIC -> stringResource(R.string.weight_kg, kilograms)
    UnitSystem.IMPERIAL -> stringResource(R.string.weight_pound, kilogramsToPounds(kilograms))
}

/** Une liste vide dit quoi faire, pas seulement qu'elle est vide. */
@Composable
private fun NothingWeighedYet() {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Text(
            text = stringResource(R.string.weight_empty_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Note(stringResource(R.string.weight_empty_body))
    }
}

/**
 * Un échec de lecture se dit.
 *
 * Il ne s'affiche pas comme un journal vide : « vous ne vous êtes jamais pesé » est
 * une affirmation, pas une absence de réponse.
 */
@Composable
private fun Unreadable() {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Text(
            text = stringResource(R.string.weight_error_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Note(stringResource(R.string.weight_error_body))
    }
}

@Composable
private fun Note(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** « lundi 10 août 2026 », dans la langue de l'appareil. */
private fun LocalDate.longLabel(): String = format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
