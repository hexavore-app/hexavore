package app.hexavore.feature.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.stringResource
import app.hexavore.core.designsystem.component.AnchoredBubble
import app.hexavore.core.designsystem.component.MacroBar
import app.hexavore.core.designsystem.component.MacroHexagon
import app.hexavore.core.designsystem.component.MacroQuarter
import app.hexavore.core.designsystem.component.MacroUnit
import app.hexavore.core.designsystem.component.macroAnchor
import app.hexavore.core.designsystem.theme.Spacing
import app.hexavore.domain.diary.DaySummary
import app.hexavore.domain.diary.MacroSources
import app.hexavore.domain.diary.sourcesOf
import app.hexavore.domain.goal.DailyGoal
import app.hexavore.domain.nutrition.Macro
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Ce que la journée a apporté : la figure, le grand chiffre, les six barres.
 *
 * Sorti de `HomeScreen.kt` quand le seuil de fonctions par fichier a mordu, et le
 * découpage suit ce que les choses sont : ce fichier porte les **compteurs du jour**,
 * là où l'autre porte l'écran qui les encadre — la barre du titre, le calendrier, le
 * glissement, le défilement, la barre d'annulation. `DishMacros.kt` lui fait pendant,
 * avec les apports d'un plat.
 */

/**
 * La figure, le grand chiffre, les six barres — et la bulle posée dessus.
 *
 * **La boîte porte les trois parce que la bulle a besoin de place.** Elle se pose sous
 * la figure, et une journée à six aliments lui demande deux cents points de haut : la
 * seule zone de l'écran qui les offre sous l'hexagone est celle qui descend jusqu'aux
 * barres. Bornée au seul bloc de l'hexagone, la bulle remontait sur la figure et
 * effaçait la surbrillance qu'elle est censée commenter.
 *
 * **`figure` est le rectangle de l'hexagone dans le repère de cette boîte**, et non
 * dans celui de l'écran : c'est la boîte qui place la bulle, donc c'est elle qui compte.
 */
@Composable
internal fun MacroBlock(summary: DaySummary, goal: DailyGoal, focus: MacroFocus) {
    var zone by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var figure by remember { mutableStateOf(Rect.Zero) }

    Box(modifier = Modifier.fillMaxWidth().onGloballyPositioned { zone = it }) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(Spacing.xl),
        ) {
            RemainingBlock(
                summary = summary,
                dailyGoal = goal,
                focus = focus,
                onFigure = { coords -> figure = zone?.localBoundingBoxOf(coords) ?: Rect.Zero },
            )
            MacroBars(summary, goal, focus)
        }

        // `matchParentSize` : la bulle se place dans la boite sans la dimensionner,
        // sinon la page changerait de hauteur a chaque ouverture.
        focus.macro?.let { macro ->
            AnchoredBubble(
                anchorX = figure.macroAnchor(macro).x,
                below = figure.bottom,
                modifier = Modifier.matchParentSize(),
            ) {
                MacroSourcesBubble(
                    sources = summary.sourcesOf(macro, MacroSources.DETAILED),
                    total = summary.totals[macro],
                    goal = goal,
                )
            }
        }
    }
}

/**
 * L'hexagone des macros, puis le grand chiffre.
 *
 * Le chiffre est sous la figure et non en son centre : les six quartiers prennent
 * naissance au centre, un texte y serait recouvert dès la première bouchée.
 *
 * C'est le **restant** qui s'affiche, pas le consommé — l'information dont on a
 * besoin au moment de décider quoi manger. Un dépassement l'affiche en négatif,
 * sans rouge d'alerte ni message : c'est une donnée, pas un jugement.
 */
@Composable
private fun RemainingBlock(
    summary: DaySummary,
    dailyGoal: DailyGoal,
    focus: MacroFocus,
    onFigure: (LayoutCoordinates) -> Unit,
) {
    val consumed = summary.totals[Macro.CALORIES].value
    val goal = dailyGoal.kcal
    val remaining = goal - consumed
    // `associateWith` est inline, donc `stringResource` y reste appelable : les six
    // libelles sont lus en composition, la ou les actions ne le sont pas.
    val actions = Macro.entries.associateWith {
        stringResource(R.string.home_sources_action, stringResource(it.labelRes))
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        // Plus d'intervalle a rajouter sous la figure : les six lettres tiennent
        // desormais dans sa zone, donc le « G » des glucides ne vient plus buter
        // contre le grand chiffre et le « C » ne sort plus par le haut.
        MacroHexagon(
            quarters = summary.quarters(dailyGoal),
            modifier = Modifier.onGloballyPositioned(onFigure),
            selected = focus.macro,
            label = { actions.getValue(it) },
            onSelect = { focus.tapped(summary, it) },
        )
        Text(
            text = abs(remaining).roundToInt().toString(),
            style = MaterialTheme.typography.displayLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = stringResource(if (remaining < 0) R.string.home_over_label else R.string.home_remaining_label),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = stringResource(R.string.home_consumed_of_goal, consumed.roundToInt(), goal.roundToInt()),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Les six quartiers, dérivés des totaux et de l'objectif du jour.
 *
 * Un objectif nul rendrait un ratio infini : le quartier est alors vide, ce qui est
 * la seule lecture honnête d'une cible qui n'existe pas.
 */
private fun DaySummary.quarters(goal: DailyGoal): Map<Macro, MacroQuarter> = Macro.entries.associateWith { macro ->
    val target = goal[macro]
    MacroQuarter(ratio = if (target > 0.0) (totals[macro].value / target).toFloat() else 0f)
}

@Composable
private fun MacroBars(summary: DaySummary, goal: DailyGoal, focus: MacroFocus) {
    // **Le chemin visible des sources.** Toucher un triangle est un geste que rien
    // n'annonce ; une barre pleine largeur est une cible qu'on trouve sans la
    // connaitre, et que le lecteur d'ecran annonce deja par son nom et sa valeur.
    val ouvrir = stringResource(R.string.home_sources_open)

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        BAR_MACROS.forEach { macro ->
            MacroBar(
                macro = macro,
                label = stringResource(macro.labelRes),
                consumed = summary.totals[macro].value.toFloat(),
                goal = goal[macro].toFloat(),
                unit = MacroUnit.GRAM,
                // Muette quand la macro n'a rien a montrer : la meme regle que le
                // quartier, parce que c'est une regle sur la macro et non sur la porte.
                modifier = Modifier.clickable(
                    enabled = !summary.sourcesOf(macro, MacroSources.DETAILED).isEmpty,
                    onClickLabel = ouvrir,
                ) { focus.tapped(summary, macro) },
            )
        }
    }
}

/**
 * Les cinq barres, dans l'**ordre angulaire des quartiers**.
 *
 * Les calories n'en ont pas : elles ont le quartier du haut et le grand chiffre.
 * L'ordre suit celui de l'hexagone pour que l'œil passe de l'un à l'autre sans
 * traduction — deux ordres différents rendraient la couleur seule porteuse du lien.
 */
private val BAR_MACROS =
    listOf(Macro.PROTEIN, Macro.FIBER, Macro.CARBS, Macro.SUGARS, Macro.FAT)
