package app.hexavore.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.hexavore.core.designsystem.component.WholeNumberField
import app.hexavore.core.designsystem.theme.Spacing
import app.hexavore.domain.nutrition.Macro
import kotlin.math.roundToInt

/**
 * **Vos six compteurs**, et le switch qui décide d'où ils viennent.
 *
 * Deux états, sans entre-deux ([D60][decisions]). Calculés, ils suivent le profil, le
 * poids visé et l'échéance, et l'écran les montre en lecture. Saisis à la main, ils
 * deviennent six champs et **aucun recalcul n'y touche plus** — c'est la promesse que
 * [docs/02][parcours] fait, sous une forme qui se voit d'un coup d'œil au lieu de se
 * déduire de six marqueurs.
 *
 * Rien tant que le formulaire est incomplet : six chiffres dérivés d'un profil à moitié
 * rempli seraient l'objectif de quelqu'un d'autre.
 *
 * [parcours]: docs/02-parcours-et-ecrans.md
 * [decisions]: docs/11-decisions.md
 */
@Composable
internal fun CountersSection(state: ProfileUiState, actions: ProfileActions) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        SectionTitle(stringResource(R.string.profile_counters_title))
        ModeSwitch(manual = state.form.manual, onManual = actions.onManual)
        Body(
            stringResource(if (state.form.manual) R.string.profile_mode_manual else R.string.profile_mode_calculated),
        )

        val calculated = state.plan?.goal
        when {
            // Un champ vide ne s'annonce pas ici : le bouton refusera et dira lequel
            // des six manque, par la barre que D56 a retenue.
            state.form.manual -> Macro.entries.forEach { MacroField(it, state.form.macros[it], actions) }
            calculated == null -> Body(stringResource(R.string.profile_counters_unavailable))
            else -> Macro.entries.forEach { CounterLine(it, calculated[it]) }
        }
    }
}

@Composable
private fun CounterLine(macro: Macro, value: Double) {
    ReadLine(
        stringResource(
            R.string.profile_counter_line,
            stringResource(macro.labelRes),
            value.roundToInt(),
            stringResource(macro.unitRes),
        ),
    )
}

/**
 * Un compteur saisi à la main.
 *
 * `initial` n'est relu qu'à la première composition ([D45][decisions]), et c'est
 * exactement ce qu'il faut : le champ est posé au moment de la bascule, avec le chiffre
 * que le calcul proposait, et **plus rien ne le réécrit ensuite**. C'est tout le propos
 * du mode manuel, et c'est le seul écran du projet où [D51][decisions] ne s'applique pas.
 *
 * [decisions]: docs/11-decisions.md
 */
@Composable
private fun MacroField(macro: Macro, value: Double?, actions: ProfileActions) {
    // Des entiers, et le champ dit pourquoi il refuse une virgule (D172).
    WholeNumberField(
        initial = value?.roundToInt()?.toString().orEmpty(),
        onValueChange = { actions.onMacroChange(macro, it.toDoubleOrNull()) },
        label = stringResource(
            R.string.profile_counter_field,
            stringResource(macro.labelRes),
            stringResource(macro.unitRes),
        ),
        modifier = Modifier.fillMaxWidth(),
    )
}
