package app.hexavore.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import app.hexavore.core.designsystem.theme.NeonTheme
import app.hexavore.core.designsystem.theme.Spacing
import app.hexavore.domain.diary.MacroSources
import app.hexavore.domain.goal.DailyGoal
import app.hexavore.domain.nutrition.Macro
import app.hexavore.domain.nutrition.MacroTotal
import kotlin.math.roundToInt

/**
 * Ce qui a donné une macro, dans une bulle posée contre son quartier.
 *
 * **Elle répond à une question qu'aucun chiffre ne posait.** L'hexagone dit *comment va
 * ma journée*, les barres disent *où j'en suis* ; ni l'un ni l'autre ne dit **d'où ça
 * vient**, et c'est pourtant la seule des trois sur laquelle on puisse agir au repas
 * suivant.
 *
 * **Cinq aliments, puis le reste en une ligne.** Une bulle qui défilerait au-dessus
 * d'une page qui défile mettrait deux gestes sous le même doigt, et une bulle haute de
 * quinze lignes serait exactement ce qu'« envahissante » veut dire. Le reste est compté
 * plutôt que tu : sans cette ligne, la somme de ce qu'on lit passerait pour le total.
 *
 * @see docs/11-decisions.md — D122
 */
@Composable
internal fun MacroSourcesBubble(sources: MacroSources, total: MacroTotal, goal: DailyGoal?) {
    Column(
        modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        BubbleHeader(sources.macro, total, goal)
        sources.top.forEach { SourceRow(sources.macro, it.name, it.value, goal) }
        sources.others?.let {
            SourceRow(
                macro = sources.macro,
                name = pluralStringResource(R.plurals.home_sources_others, it.count, it.count),
                value = it.value,
                goal = goal,
                faded = true,
            )
        }
        if (sources.unknown.isNotEmpty()) UnknownNote(sources.unknown)
    }
}

/** Le nom de la macro, et où elle en est du jour. La même phrase que sa barre, en plus court. */
@Composable
private fun BubbleHeader(macro: Macro, total: MacroTotal, goal: DailyGoal?) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(macro.labelRes),
            style = MaterialTheme.typography.titleSmall,
            color = NeonTheme.macros[macro].base,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = goal?.get(macro)?.let {
                stringResource(R.string.home_sources_of_goal, macro.amount(total.value), macro.amount(it))
            } ?: macro.amount(total.value),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Un aliment, ce qu'il a donné, et la part que cela prend sur la journée.
 *
 * **La barre est celle des plats, à la lettre** : même hauteur, même teinte, même
 * référence — l'objectif du jour. Une deuxième règle de lecture pour un trait qui
 * ressemble au premier serait pire que pas de trait du tout.
 */
@Composable
private fun SourceRow(macro: Macro, name: String, value: Double, goal: DailyGoal?, faded: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = name,
            style = MaterialTheme.typography.bodyMedium,
            color = if (faded) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Shared(macro = macro, share = shareOf(value, macro, goal)) {
            Text(
                text = macro.amount(value),
                style = MaterialTheme.typography.bodyMedium,
                color = NeonTheme.macros[macro].base,
            )
        }
    }
}

/**
 * Ce qu'on ne sait pas, nommé.
 *
 * Sans barre et sans chiffre, parce qu'il n'y en a pas : un aliment dont la valeur
 * n'est pas renseignée n'a pas apporté zéro gramme, on ignore combien. C'est la seule
 * ligne de l'accueil qui dise encore qu'un total est minoré — et la seule qui dise par
 * **quoi**, donc quoi aller corriger.
 */
@Composable
private fun UnknownNote(names: List<String>) {
    Text(
        text = stringResource(R.string.home_sources_unknown, names.joinToString(separator = ", ")),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/**
 * La quantité d'une macro, dans son unité.
 *
 * Les calories se comptent en kilocalories et s'écrivent entières — personne ne lit
 * « 195,4 kcal » —, les cinq autres en grammes avec une décimale au plus.
 */
@Composable
private fun Macro.amount(value: Double): String = when (this) {
    Macro.CALORIES -> stringResource(R.string.home_dish_kcal, value.roundToInt())
    else -> stringResource(R.string.home_macro_grams, formatGrams(value))
}

/**
 * La part d'une valeur sur l'objectif du jour, plafonnée à 1.
 *
 * `null` sans objectif, et `null` aussi sur un objectif nul — une part de quelque chose
 * qui vaut zéro n'existe pas, et le rapport serait infini.
 */
internal fun shareOf(value: Double, macro: Macro, goal: DailyGoal?): Float? {
    val target = goal?.get(macro)?.takeIf { it > 0.0 } ?: return null
    return (value / target).toFloat().coerceIn(0f, 1f)
}
