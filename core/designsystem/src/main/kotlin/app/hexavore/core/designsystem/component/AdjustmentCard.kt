package app.hexavore.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.hexavore.core.designsystem.R
import app.hexavore.core.designsystem.preview.NeonPreviews
import app.hexavore.core.designsystem.preview.PreviewSurface
import app.hexavore.core.designsystem.theme.Spacing
import app.hexavore.domain.goal.AdjustmentSuggestion
import app.hexavore.domain.goal.DailyGoal
import app.hexavore.domain.nutrition.Macro
import app.hexavore.domain.profile.UnitSystem
import app.hexavore.domain.profile.kilogramsToPounds
import kotlin.math.abs

/**
 * La carte d'ajustement, et ses **trois issues**.
 *
 * *Accepter* écrit une nouvelle version de l'objectif, *Ignorer* la fait revenir dans
 * deux semaines, *Ne plus proposer* éteint l'adaptation. Rien n'est jamais appliqué
 * sans accord ([docs/02][parcours]).
 *
 * **Ici et non dans un module d'écran**, parce que deux écrans la portent — le journal
 * de poids et l'accueil — et qu'un `:feature` ne dépend jamais d'un autre. La recopier
 * aurait donné deux cartes qui divergent au premier mot changé.
 *
 * **Le rythme est écrit signé** — « −0,2 kg/semaine » — plutôt que raconté. « Vous
 * perdez 0,2 kg par semaine, pour 0,5 visé » se lit mieux, mais devient faux dès que
 * les deux rythmes n'ont pas le même sens : quelqu'un qui vise une prise et qui perd du
 * poids lirait « vous perdez 0,2, pour 0,5 visé » et comprendrait l'inverse.
 *
 * **Dans le système choisi** ([D162][decisions]) : la carte parlait en kilos juste au-dessus
 * d'une liste de pesées écrite en livres.
 *
 * [parcours]: docs/02-parcours-et-ecrans.md
 * [decisions]: docs/11-decisions.md
 */
@Composable
fun AdjustmentCard(
    suggestion: AdjustmentSuggestion,
    units: UnitSystem,
    onAccept: () -> Unit,
    onIgnore: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.cardPadding),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Text(
                text = stringResource(R.string.ds_adjustment_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = stringResource(
                    R.string.ds_adjustment_body,
                    pace(suggestion.actualWeeklyKg, units),
                    pace(suggestion.aimedWeeklyKg, units),
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = question(suggestion.deltaKcal),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Answers(onAccept = onAccept, onIgnore = onIgnore, onStop = onStop)
        }
    }
}

/**
 * Les trois réponses.
 *
 * *Accepter* seul porte le bouton plein : c'est celui qui écrit, et les deux autres ne
 * font que se taire — plus ou moins longtemps.
 */
@Composable
private fun Answers(onAccept: () -> Unit, onIgnore: () -> Unit, onStop: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NeonButton(
                text = stringResource(R.string.ds_adjustment_accept),
                onClick = onAccept,
                macro = Macro.CALORIES,
                style = NeonButtonStyle.FILLED,
            )
            TextButton(onClick = onIgnore) { Text(stringResource(R.string.ds_adjustment_ignore)) }
        }
        // Sur sa propre ligne, et en retrait : c'est la seule des trois qui soit
        // durable, et elle ne doit pas se toucher en visant « Ignorer ».
        TextButton(onClick = onStop) {
            Text(
                text = stringResource(R.string.ds_adjustment_stop),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * « −0,2 kg/semaine », « +0,3 kg/semaine », ou « stable » — en livres pour l'impérial.
 *
 * Le seuil de stabilité reste en kilos : c'est l'unité du calcul, et une même pesée ne
 * doit pas paraître stable dans un système et en mouvement dans l'autre.
 */
@Composable
private fun pace(weeklyKg: Double, units: UnitSystem): String = when {
    abs(weeklyKg) < STEADY_KG_PER_WEEK -> stringResource(R.string.ds_adjustment_pace_steady)
    units == UnitSystem.IMPERIAL -> stringResource(
        if (weeklyKg < 0) R.string.ds_adjustment_pace_loss_pounds else R.string.ds_adjustment_pace_gain_pounds,
        kilogramsToPounds(abs(weeklyKg)),
    )
    weeklyKg < 0 -> stringResource(R.string.ds_adjustment_pace_loss, abs(weeklyKg))
    else -> stringResource(R.string.ds_adjustment_pace_gain, weeklyKg)
}

/** Le sens de la correction est dit par un verbe, jamais par le signe d'un nombre. */
@Composable
private fun question(deltaKcal: Int): String = if (deltaKcal < 0) {
    stringResource(R.string.ds_adjustment_reduce, abs(deltaKcal))
} else {
    stringResource(R.string.ds_adjustment_raise, deltaKcal)
}

/**
 * En deçà, on n'écrit pas de rythme.
 *
 * Cinquante grammes par semaine affichés comme « −0,1 kg/semaine » après arrondi
 * annonceraient une tendance là où il n'y a que du bruit.
 */
private const val STEADY_KG_PER_WEEK = 0.05

// --- Aperçu -------------------------------------------------------------------

@NeonPreviews
@Composable
private fun AdjustmentCardPreview() {
    PreviewSurface {
        AdjustmentCard(
            suggestion = AdjustmentSuggestion(
                actualWeeklyKg = -0.2,
                aimedWeeklyKg = -0.5,
                current = DailyGoal(2400.0, 150.0, 255.0, 60.0, 67.0, 30.0),
                proposed = DailyGoal(2250.0, 150.0, 218.0, 56.0, 62.0, 30.0),
            ),
            units = UnitSystem.METRIC,
            onAccept = {},
            onIgnore = {},
            onStop = {},
        )
    }
}
