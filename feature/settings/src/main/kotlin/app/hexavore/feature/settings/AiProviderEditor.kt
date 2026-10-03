package app.hexavore.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import app.hexavore.core.designsystem.component.DraftTextField
import app.hexavore.core.designsystem.component.NeonButton
import app.hexavore.core.designsystem.component.NeonButtonAvailability
import app.hexavore.core.designsystem.component.NeonButtonStyle
import app.hexavore.core.designsystem.theme.Spacing
import app.hexavore.domain.ai.AiProvider

/**
 * Ce qu'on saisit pour un fournisseur : la clé, le modèle, l'URL, et les deux boutons.
 *
 * Sorti de `AiSettingsScreen` quand le seuil de fonctions par fichier a mordu, et le
 * découpage suit ce que les choses sont : ce fichier dit **ce qu'on écrit** pour un
 * fournisseur, là où l'autre dit **lesquels existent** et comment ils se présentent en
 * liste.
 */

/**
 * Le formulaire d'un fournisseur.
 *
 * Le champ de clé est masqué par défaut et se révèle à la demande — [docs/05][ia] veut
 * qu'on puisse vérifier une clé sans la recoller, mais pas qu'elle traîne à l'écran.
 *
 * **Enregistrer est grisé tant que le formulaire est incomplet**, sans explication
 * attachée au bouton : ce qui manque est visible juste au-dessus, dans le champ vide.
 * C'est exactement le cas que `DISABLED` décrit, et non `UNAVAILABLE`, qui existe pour
 * les boutons dont l'indisponibilité demande une phrase.
 *
 * [ia]: docs/05-ia.md
 */
@Composable
internal fun ProviderEditor(
    provider: AiProvider,
    form: ProviderForm,
    probe: ProbeState,
    inUse: Boolean,
    actions: AiSettingsActions,
) {
    var helping by rememberSaveable { mutableStateOf(false) }

    KeyField(form, actions)

    KeyHelpLink(provider) { helping = true }

    if (helping) {
        KeyHelpDialog(provider) { helping = false }
    }

    ModelField(provider, form, actions)

    // Rien a regler quand l'adresse est unique : Gemini et Claude en ont une seule, et
    // un champ qu'on ne peut que casser n'est pas un reglage (D143).
    if (provider.editableBaseUrl) {
        BaseUrlField(form, actions)
    }

    ProbeResult(probe)

    Row(
        modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        NeonButton(
            text = stringResource(if (probe == ProbeState.Running) R.string.ai_testing else R.string.ai_test),
            onClick = actions.onTest,
            availability = form.actionAvailability(probe),
        )
        // « Utiliser » et non « Enregistrer » : ce que le geste fait n'est pas de
        // ranger une cle, c'est de choisir qui analysera les prochaines photos. Une
        // fois fait, le bouton le dit au passe et ne se represse pas -- c'est la
        // confirmation, et elle se lit la ou le doigt a appuye.
        NeonButton(
            text = stringResource(if (inUse) R.string.ai_in_use else R.string.ai_use),
            onClick = actions.onSave,
            style = NeonButtonStyle.FILLED,
            availability = if (inUse) NeonButtonAvailability.DISABLED else form.actionAvailability(probe),
        )
    }

    NeonButton(
        text = stringResource(R.string.ai_forget),
        onClick = actions.onForget,
        modifier = Modifier.padding(top = Spacing.xs),
    )
}

@Composable
private fun KeyField(form: ProviderForm, actions: AiSettingsActions) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        DraftTextField(
            initial = form.apiKey,
            onValueChange = actions.onKey,
            label = stringResource(R.string.ai_key_label),
            modifier = Modifier.weight(1f),
            visualTransformation = if (form.revealed) VisualTransformation.None else PasswordVisualTransformation(),
        )
        Text(
            text = stringResource(if (form.revealed) R.string.ai_hide else R.string.ai_reveal),
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.clickable(onClick = actions.onReveal).padding(Spacing.sm),
        )
    }
}

/**
 * Le modèle : une liste qu'on déroule, et un champ qu'on peut écrire.
 *
 * **Les deux, et non l'un ou l'autre.** La liste évite la faute de frappe sur
 * `gemini-3.5-flash-lite`, qui ne se devine pas et ne se vérifie nulle part — un
 * modèle mal écrit ne se voit qu'au premier repas photographié. Mais les
 * fournisseurs en publient de nouveaux chaque mois, et une liste fermée aurait
 * empêché d'utiliser le modèle sorti hier ([D143][decisions]).
 *
 * [decisions]: docs/11-decisions.md
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModelField(provider: AiProvider, form: ProviderForm, actions: AiSettingsActions) {
    var deroule by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = deroule && provider.suggestedModels.isNotEmpty(),
        onExpandedChange = { deroule = it },
        modifier = Modifier.fillMaxWidth(),
    ) {
        DraftTextField(
            initial = form.model,
            onValueChange = actions.onModel,
            label = stringResource(R.string.ai_model_label),
            modifier = Modifier
                .menuAnchor(MenuAnchorType.PrimaryEditable)
                .fillMaxWidth(),
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = deroule) },
        )
        ExposedDropdownMenu(expanded = deroule, onDismissRequest = { deroule = false }) {
            provider.suggestedModels.forEach { modele ->
                DropdownMenuItem(
                    text = { Text(modele) },
                    onClick = {
                        actions.onModel(modele)
                        deroule = false
                    },
                )
            }
        }
    }
}

@Composable
private fun BaseUrlField(form: ProviderForm, actions: AiSettingsActions) {
    DraftTextField(
        initial = form.baseUrl,
        onValueChange = actions.onBaseUrl,
        label = stringResource(R.string.ai_base_url_label),
        modifier = Modifier.fillMaxWidth(),
        keyboardType = KeyboardType.Uri,
    )
}

@Composable
private fun ProbeResult(probe: ProbeState) {
    val message = when (probe) {
        is ProbeState.Succeeded ->
            stringResource(if (probe.vision) R.string.ai_probe_ok_vision else R.string.ai_probe_ok_text_only)

        is ProbeState.Failed -> stringResource(probe.messageRes)
        ProbeState.Idle, ProbeState.Running -> null
    }

    message?.let { Text(text = it, style = MaterialTheme.typography.bodySmall) }

    // Ce que le fournisseur a repondu, sous le message et en plus discret : c est
    // un renseignement pour diagnostiquer, pas la phrase qu on lit d abord.
    (probe as? ProbeState.Failed)?.detail?.let {
        Text(
            text = it,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Grisé pendant l'essai autant que sur un formulaire incomplet.
 *
 * Un second appui pendant qu'un appel est en vol partirait une seconde fois, et se
 * paierait deux fois.
 */
private fun ProviderForm.actionAvailability(probe: ProbeState) = when {
    probe == ProbeState.Running -> NeonButtonAvailability.DISABLED
    complete -> NeonButtonAvailability.AVAILABLE
    else -> NeonButtonAvailability.DISABLED
}
