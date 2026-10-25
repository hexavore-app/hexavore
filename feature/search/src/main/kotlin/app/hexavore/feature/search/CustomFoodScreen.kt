package app.hexavore.feature.search

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.hexavore.core.designsystem.component.DraftTextField
import app.hexavore.core.designsystem.component.NeonButton
import app.hexavore.core.designsystem.component.NeonButtonAvailability
import app.hexavore.core.designsystem.component.NeonButtonStyle
import app.hexavore.core.designsystem.component.WholeNumberField
import app.hexavore.core.designsystem.component.isNumberField
import app.hexavore.core.designsystem.theme.NeonTheme
import app.hexavore.core.designsystem.theme.Spacing
import app.hexavore.domain.food.FoodId
import app.hexavore.domain.nutrition.Macro

/** Le formulaire d'aliment personnel, branché sur le graphe d'injection. */
@Composable
internal fun CustomFoodRoute(
    onSaved: (FoodId) -> Unit,
    onClose: () -> Unit,
    viewModel: CustomFoodViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    // Enregistre : l'ecran se referme sur la fiche. Un effet plutot qu'un rappel
    // depuis onSave, pour que la fermeture suive l'etat reellement atteint.
    LaunchedEffect(state) {
        val saved = state as? CustomFoodUiState.Saved ?: return@LaunchedEffect
        onSaved(saved.id)
    }

    CustomFoodScreen(
        state = state,
        actions = remember(viewModel, onClose) {
            CustomFoodActions(
                onNameChange = viewModel::onNameChange,
                onBrandChange = viewModel::onBrandChange,
                onServingChange = viewModel::onServingChange,
                onMacroChange = viewModel::onMacroChange,
                onSave = viewModel::onSave,
                onRetry = viewModel::onRetry,
                onContribute = viewModel::onContribute,
                onDeclineContribution = viewModel::onDeclineContribution,
                onClose = onClose,
            )
        },
    )
}

/**
 * Créer un aliment réutilisable.
 *
 * **Ce que l'utilisateur a pris la peine de saisir est ce qu'il mange vraiment** :
 * une fiche personnelle passe devant les lignes de la table dans les résultats. C'est
 * ce qui rend cet écran utile plutôt que consolant — sans réutilisation, créer un
 * aliment ne vaudrait pas mieux que de le retaper.
 *
 * @see docs/04-sources-de-donnees.md
 */
@Composable
internal fun CustomFoodScreen(state: CustomFoodUiState, actions: CustomFoodActions, modifier: Modifier = Modifier) {
    Surface(color = MaterialTheme.colorScheme.background, modifier = modifier.fillMaxSize()) {
        when (state) {
            is CustomFoodUiState.Saved -> Unit
            is CustomFoodUiState.Error -> WriteFailed(actions)
            is CustomFoodUiState.Editing -> Form(state, actions)
            // Le formulaire reste dessous : le dialogue se pose par-dessus une fiche
            // deja ecrite, et le refuser ne defait rien.
            is CustomFoodUiState.Offering -> ContributionDialog(state, actions)
        }
    }
}

@Composable
private fun Form(state: CustomFoodUiState.Editing, actions: CustomFoodActions) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(horizontal = Spacing.screenMargin)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Text(
            text = stringResource(R.string.custom_title),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = stringResource(R.string.custom_subtitle),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        IdentityFields(state.form, actions)
        NutrientFields(state.form, actions)
        FormActions(state, actions)
    }
}

/** Ce qui identifie la fiche : son nom, sa marque, la quantité qu'on en prend. */
@Composable
private fun ColumnScope.IdentityFields(form: CustomFoodForm, actions: CustomFoodActions) {
    // Affiche et non saisissable : ce code n'a pas ete tape, il a ete lu. Le rendre
    // modifiable ferait porter a la fiche un code qui n'est pas celui de l'emballage
    // qu'on a devant soi, et le prochain scan ne la retrouverait pas.
    form.barcode?.let { code ->
        Text(
            text = stringResource(R.string.custom_barcode_kept, code.value),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    DraftTextField(
        initial = form.name,
        onValueChange = actions.onNameChange,
        label = stringResource(R.string.custom_field_name),
        modifier = Modifier.fillMaxWidth(),
        // Deux lignes, comme sur l'ecran de validation : un nom recopie d'un
        // emballage depasse souvent la largeur d'un champ.
        maxLines = NAME_LINES,
    )
    DraftTextField(
        initial = form.brand,
        onValueChange = actions.onBrandChange,
        label = stringResource(R.string.custom_field_brand),
        modifier = Modifier.fillMaxWidth(),
    )
    DraftTextField(
        initial = form.serving,
        onValueChange = actions.onServingChange,
        label = stringResource(R.string.custom_field_serving),
        modifier = Modifier.fillMaxWidth(),
        keyboardType = KeyboardType.Decimal,
        accept = String::isNumberField,
    )
}

/**
 * Les six valeurs pour 100 g.
 *
 * Dans l'ordre angulaire de l'hexagone, comme partout ailleurs : la position sert de
 * second canal en cas de daltonisme, et elle ne renseigne que si elle est la même
 * partout. Chaque libellé porte la teinte de sa macro, la même que sur l'accueil.
 */
@Composable
private fun ColumnScope.NutrientFields(form: CustomFoodForm, actions: CustomFoodActions) {
    Text(
        text = stringResource(R.string.custom_per_100g),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    FORM_MACROS.forEach { macro ->
        // Des entiers, comme sur l'ecran de saisie : les deux ecrans decrivent les memes
        // six valeurs, et deux regles pour la meme chose divergent. Le champ dit pourquoi
        // il refuse la virgule qu'on tape en recopiant une etiquette (D172).
        WholeNumberField(
            initial = form.macros[macro].orEmpty(),
            onValueChange = { actions.onMacroChange(macro, it) },
            label = stringResource(macro.fieldRes),
            modifier = Modifier.fillMaxWidth(),
            labelColor = NeonTheme.macros[macro].base,
        )
    }
}

/**
 * Les deux issues, et l'explication de ce qui manque.
 *
 * L'explication est la **réponse** du bouton indisponible à un appui, comme sur
 * l'écran de validation ([D28][decisions]) : affichée en permanence, elle occuperait
 * quatre lignes à chaque fiche neuve pour dire ce que les champs vides disent déjà.
 *
 * [decisions]: docs/11-decisions.md
 */
@Composable
private fun ColumnScope.FormActions(state: CustomFoodUiState.Editing, actions: CustomFoodActions) {
    var explaining by remember { mutableStateOf(false) }
    val complete = state.form.complete
    LaunchedEffect(complete) { if (complete) explaining = false }

    if (explaining && !state.saving) {
        Text(
            text = stringResource(R.string.custom_incomplete_hint),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
        NeonButton(
            text = stringResource(R.string.custom_cancel),
            onClick = actions.onClose,
            modifier = Modifier.weight(1f),
            availability = if (state.saving) {
                NeonButtonAvailability.DISABLED
            } else {
                NeonButtonAvailability.AVAILABLE
            },
        )
        NeonButton(
            text = stringResource(R.string.custom_save),
            onClick = { if (complete) actions.onSave() else explaining = true },
            modifier = Modifier.weight(1f),
            style = NeonButtonStyle.FILLED,
            availability = when {
                state.saving -> NeonButtonAvailability.DISABLED
                !complete -> NeonButtonAvailability.UNAVAILABLE
                else -> NeonButtonAvailability.AVAILABLE
            },
        )
    }
}

@Composable
private fun WriteFailed(actions: CustomFoodActions) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(Spacing.screenMargin),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Text(
            text = stringResource(R.string.custom_error_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.error,
        )
        Text(
            text = stringResource(R.string.custom_error_body),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        NeonButton(
            text = stringResource(R.string.custom_error_retry),
            onClick = actions.onRetry,
            style = NeonButtonStyle.FILLED,
        )
        NeonButton(text = stringResource(R.string.custom_cancel), onClick = actions.onClose)
    }
}

/** Les six valeurs, dans l'ordre angulaire de l'hexagone. */
private val FORM_MACROS =
    listOf(Macro.CALORIES, Macro.PROTEIN, Macro.FIBER, Macro.CARBS, Macro.SUGARS, Macro.FAT)

private val Macro.fieldRes: Int
    @StringRes get() = when (this) {
        Macro.CALORIES -> R.string.custom_field_calories
        Macro.PROTEIN -> R.string.custom_field_protein
        Macro.CARBS -> R.string.custom_field_carbs
        Macro.SUGARS -> R.string.custom_field_sugars
        Macro.FAT -> R.string.custom_field_fat
        Macro.FIBER -> R.string.custom_field_fiber
    }

/** Deux lignes pour un nom d'aliment : voir `NAME_LINES` de l'écran de validation. */
private const val NAME_LINES = 2
