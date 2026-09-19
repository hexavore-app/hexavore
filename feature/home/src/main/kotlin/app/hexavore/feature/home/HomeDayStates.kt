package app.hexavore.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.hexavore.core.designsystem.component.NeonButton
import app.hexavore.core.designsystem.theme.Spacing
import app.hexavore.domain.diary.DaySummary
import app.hexavore.domain.nutrition.Macro
import kotlin.math.roundToInt

/**
 * Ce que la journée affiche à la place de ses jauges, quand elle n'en a pas.
 *
 * **Aucun de ces quatre états n'est une liste vide silencieuse.** Chacun dit ce qui se
 * passe et, quand il y a quelque chose à faire, comment le faire — c'est la règle que
 * ce projet applique partout : une absence de réponse ne s'affiche jamais comme un zéro.
 *
 * Sortis de `HomeScreen.kt`, qui atteignait le seuil de fonctions par fichier. La
 * coupure existait déjà dans la lecture : d'un côté la structure de l'écran, de l'autre
 * ce qu'il montre quand il n'a rien à montrer.
 */

/**
 * Ce qu'on voit tant qu'aucun objectif n'a été posé.
 *
 * Une invitation, pas une jauge à zéro : afficher six barres vides laisserait croire
 * qu'un objectif existe et qu'il n'est pas atteint. Le bouton mène aux cinq questions
 * qui permettent de le calculer.
 */
@Composable
internal fun NoGoal(onSetUpGoal: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        Text(
            text = stringResource(R.string.home_no_goal_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = stringResource(R.string.home_no_goal_body),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        NeonButton(text = stringResource(R.string.home_no_goal_action), onClick = onSetUpGoal)
    }
}

/**
 * Les six totaux, sans référence à quoi que ce soit.
 *
 * Ce qui a été mangé reste une information exacte même sans objectif ; c'est la
 * **comparaison** qui manque, pas la mesure. Les afficher en texte plutôt qu'en jauge
 * dit exactement cela.
 */
@Composable
internal fun MacroTotalsOnly(summary: DaySummary) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Macro.entries.forEach { macro ->
            val total = summary.totals[macro]
            Text(
                text = stringResource(
                    R.string.home_total_line,
                    stringResource(macro.labelRes),
                    total.value.roundToInt(),
                ),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * L'écran qui dit « je n'ai pas pu lire », plutôt que de montrer zéro.
 *
 * Sans lui, un échec de lecture s'afficherait comme une journée vide — et une
 * journée vide est une affirmation, pas une absence de réponse. C'est exactement
 * le genre de mensonge que le reste de l'application s'interdit sur les valeurs
 * inconnues ; il n'y a aucune raison de se l'autoriser sur la journée entière.
 *
 * Encart inline et non dialogue : rien n'est détruit, rien n'est irréversible, et
 * un dialogue bloquerait un écran qu'il suffit de relire.
 */
@Composable
internal fun UnreadableDay(onRetry: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        Text(
            text = stringResource(R.string.home_error_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.error,
        )
        Text(
            text = stringResource(R.string.home_error_body),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        NeonButton(text = stringResource(R.string.home_error_retry), onClick = onRetry)
    }
}

@Composable
internal fun EmptyDay() {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Text(
            text = stringResource(R.string.home_empty_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = stringResource(R.string.home_empty_body),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
