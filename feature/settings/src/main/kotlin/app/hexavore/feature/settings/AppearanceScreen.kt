package app.hexavore.feature.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.hexavore.core.designsystem.component.ScreenTopBar
import app.hexavore.core.designsystem.theme.Spacing
import app.hexavore.domain.appearance.DishDisplayStyle
import app.hexavore.domain.appearance.ThemeMode
import app.hexavore.domain.profile.UnitSystem

/**
 * L'apparence de l'application.
 *
 * **La section que [docs/02][parcours] annonçait depuis la conception et qui « n'ouvrait
 * rien ».** Elle en ouvre une maintenant : le thème. Le système d'unités la rejoindra,
 * et c'est la raison pour laquelle l'écran est une liste de cartes plutôt qu'un
 * interrupteur solitaire.
 *
 * **Trois choix exclusifs et non un interrupteur.** Un interrupteur « sombre » ne saurait
 * pas dire « suivre le système », qui est le défaut et le comportement que l'application
 * avait avant d'être réglable. Trois boutons radio disent les trois états sans qu'aucun
 * ne soit un cas particulier caché.
 *
 * [parcours]: docs/02-parcours-et-ecrans.md
 */
@Composable
internal fun AppearanceRoute(onClose: () -> Unit, viewModel: AppearanceViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    AppearanceScreen(
        state = state,
        onTheme = viewModel::onTheme,
        onUnits = viewModel::onUnits,
        onDishStyle = viewModel::onDishStyle,
        onClose = onClose,
    )
}

@Composable
private fun AppearanceScreen(
    state: AppearanceUiState,
    onTheme: (ThemeMode) -> Unit,
    onUnits: (UnitSystem) -> Unit,
    onDishStyle: (DishDisplayStyle) -> Unit,
    onClose: () -> Unit,
) {
    Scaffold(
        topBar = {
            ScreenTopBar(
                title = stringResource(R.string.appearance_title),
                onClose = onClose,
                closeLabel = stringResource(R.string.appearance_close),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = Spacing.screenMargin)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(Spacing.betweenCards),
        ) {
            // L'ordre des enumerations : un quatrieme theme, un troisieme style
            // apparaitraient ici sans qu'une ligne d'affichage bouge, et sans libelle
            // ils ne compileraient pas.
            ChoiceSection(
                titleRes = R.string.appearance_theme_title,
                noteRes = R.string.appearance_theme_note,
                options = ThemeMode.entries,
                selected = state.theme,
                labelOf = { it.labelRes },
                onSelect = onTheme,
            )
            ChoiceSection(
                titleRes = R.string.appearance_units_title,
                noteRes = R.string.appearance_units_note,
                options = UnitSystem.entries,
                selected = state.units,
                labelOf = { it.labelRes },
                onSelect = onUnits,
            )
            ChoiceSection(
                titleRes = R.string.appearance_dishes_title,
                noteRes = R.string.appearance_dishes_note,
                options = DishDisplayStyle.entries,
                selected = state.dishStyle,
                labelOf = { it.labelRes },
                onSelect = onDishStyle,
            )
        }
    }
}

/**
 * Un réglage : son titre, ses choix exclusifs, et la phrase qui dit ce qu'il fait.
 *
 * **Les trois réglages posent la même question et la posent pareil**, donc ils ne se
 * dessinent qu'une fois. Les recopier aurait laissé le troisième diverger du premier
 * — une carte sans note, un intervalle différent — le jour où l'un des trois apprend
 * quelque chose.
 */
@Composable
private fun <T> ChoiceSection(
    @StringRes titleRes: Int,
    @StringRes noteRes: Int,
    options: List<T>,
    selected: T,
    labelOf: (T) -> Int,
    onSelect: (T) -> Unit,
) {
    SectionTitle(stringResource(titleRes))

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(Spacing.cardPadding).selectableGroup(),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            options.forEach { option ->
                ChoiceRow(
                    labelRes = labelOf(option),
                    selected = option == selected,
                    onSelect = { onSelect(option) },
                )
            }
        }
    }

    Body(stringResource(noteRes))
}

/** Une ligne à choix unique. Les deux réglages posent la même question, et la posent pareil. */
@Composable
private fun ChoiceRow(labelRes: Int, selected: Boolean, onSelect: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect)
            .padding(vertical = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // `null` : c'est la ligne entiere qui porte le geste, et un bouton cliquable a
        // l'interieur d'une ligne cliquable annoncerait deux cibles pour une seule.
        RadioButton(selected = selected, onClick = null)
        Text(
            text = stringResource(labelRes),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(start = Spacing.sm),
        )
    }
}

/**
 * Le libellé d'un thème.
 *
 * Une table plutôt qu'un `when` avec un `else`, pour la raison habituelle : un quatrième
 * thème **ne compile pas** tant qu'il n'a pas de nom à montrer.
 */
private val ThemeMode.labelRes: Int
    get() = when (this) {
        ThemeMode.LIGHT -> R.string.appearance_theme_light
        ThemeMode.DARK -> R.string.appearance_theme_dark
        ThemeMode.SYSTEM -> R.string.appearance_theme_system
    }

/** Le libellé d'un système d'unités, par la même table et pour la même raison. */
private val UnitSystem.labelRes: Int
    get() = when (this) {
        UnitSystem.METRIC -> R.string.appearance_units_metric
        UnitSystem.IMPERIAL -> R.string.appearance_units_imperial
    }

/** Le libellé d'un style d'affichage, par la même table et pour la même raison. */
private val DishDisplayStyle.labelRes: Int
    get() = when (this) {
        DishDisplayStyle.SIMPLE -> R.string.appearance_dishes_simple
        DishDisplayStyle.DETAILED -> R.string.appearance_dishes_detailed
    }
