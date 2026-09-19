package app.hexavore.feature.home

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import app.hexavore.core.designsystem.component.MacroShareBar
import app.hexavore.core.designsystem.theme.NeonTheme
import app.hexavore.core.designsystem.theme.Spacing
import app.hexavore.domain.diary.DishSummary
import app.hexavore.domain.goal.DailyGoal
import app.hexavore.domain.nutrition.Macro
import app.hexavore.domain.nutrition.MacroTotal

/**
 * Ce qu'un plat a apporté, et la part que cela prend sur la journée.
 *
 * Sorti de `HomeDishes.kt` quand le seuil de fonctions par fichier a mordu, et le
 * découpage suit ce que les choses sont : ce fichier porte les **apports** d'un plat —
 * cinq chiffres, cinq parts —, là où l'autre porte le plat lui-même, ses gestes et ses
 * boîtes.
 */

/**
 * Ce que le plat a apporté, au-delà des calories.
 *
 * Sans cette ligne, un plat ne se lit que par son énergie — or la question qu'on se
 * pose en relisant sa journée est rarement « combien de calories », c'est « d'où
 * viennent mes protéines » ou « qu'est-ce qui a fait grimper les sucres ».
 *
 * La couleur reprend celle des barres du haut, et l'initiale porte la même
 * information : une couleur ne renseigne jamais seule.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun DishMacros(summary: DishSummary, goal: DailyGoal?) {
    // `map` est inline, donc stringResource y reste appelable ; `joinToString` ne
    // l'est pas, d'ou les deux etapes.
    val parts = CHIP_MACROS.map { macro ->
        val value = stringResource(R.string.home_macro_grams, formatGrams(summary.totals[macro].value))
        "${stringResource(macro.labelRes)} $value"
    }
    val spoken = parts.joinToString(separator = ", ")

    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .clearAndSetSemantics { contentDescription = spoken },
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        CHIP_MACROS.forEach { macro ->
            Shared(macro = macro, share = summary.share(macro, goal)) {
                MacroChip(macro, summary.totals[macro])
            }
        }
    }
}

@Composable
private fun MacroChip(macro: Macro, total: MacroTotal) {
    Text(
        text = stringResource(
            R.string.home_macro_chip,
            stringResource(macro.initialRes),
            stringResource(R.string.home_macro_grams, formatGrams(total.value)),
        ),
        style = MaterialTheme.typography.labelSmall,
        color = NeonTheme.macros[macro].base,
    )
}

/**
 * Un chiffre, et sous lui la part qu'il prend sur la journée.
 *
 * **La barre fait la largeur du chiffre**, et c'est ce qui la garde discrète : elle
 * souligne une valeur, elle ne prétend pas former une colonne avec ses voisines. Une
 * grille régulière aurait fait un tableau, donc quelque chose à lire.
 *
 * `null` quand la journée n'a pas d'objectif : une barre suppose une cible, et une
 * journée antérieure au premier objectif n'en a aucune ([D55][decisions]). Le chiffre,
 * lui, reste exact et reste affiché.
 *
 * [decisions]: docs/11-decisions.md
 */
@Composable
internal fun Shared(macro: Macro, share: Float?, value: @Composable () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        value()
        share?.let { MacroShareBar(macro = macro, share = it) }
    }
}

/**
 * La part d'une macro sur l'objectif du jour, plafonnée à 1.
 *
 * `null` sans objectif, et `null` aussi sur un objectif nul — une part de quelque chose
 * qui vaut zéro n'existe pas, et le rapport serait infini.
 */
internal fun DishSummary.share(macro: Macro, goal: DailyGoal?): Float? {
    // Un objectif nul se traite comme un objectif absent : la part de quelque chose qui
    // vaut zero n'existe pas, et le rapport serait infini.
    val target = goal?.get(macro)?.takeIf { it > 0.0 } ?: return null
    return (totals[macro].value / target).toFloat().coerceIn(0f, 1f)
}

/** Les cinq macros affichées par plat. Les calories ont déjà leur chiffre en tête. */
private val CHIP_MACROS = listOf(Macro.PROTEIN, Macro.CARBS, Macro.SUGARS, Macro.FAT, Macro.FIBER)

internal val Macro.initialRes: Int
    @StringRes get() = when (this) {
        Macro.CALORIES -> R.string.macro_initial_calories
        Macro.PROTEIN -> R.string.macro_initial_protein
        Macro.CARBS -> R.string.macro_initial_carbs
        Macro.SUGARS -> R.string.macro_initial_sugars
        Macro.FAT -> R.string.macro_initial_fat
        Macro.FIBER -> R.string.macro_initial_fiber
    }
