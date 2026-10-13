package app.hexavore.feature.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
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
 * Ce qu'on saisit pour un fournisseur : la clé, et le bouton qui la valide.
 *
 * Sorti de `AiSettingsScreen` quand le seuil de fonctions par fichier a mordu, et le
 * découpage suit ce que les choses sont : ce fichier dit **ce qu'on écrit** pour un
 * fournisseur, là où l'autre dit **lesquels existent** et comment ils se présentent en
 * liste.
 */

/**
 * Le formulaire d'un fournisseur, réduit à ce qu'il faut pour démarrer.
 *
 * ### Trois choses sont parties, et c'est le sujet
 *
 * **« Tester »** : personne ne sait qu'il faut appuyer sur deux boutons dans l'ordre, et
 * ceux qui n'appuyaient que sur le premier repartaient avec un « clé valide » sans rien
 * d'enregistré. Valider teste.
 *
 * **« Effacer la clé »** : un bouton destructeur sous le champ qu'on vient de remplir,
 * pour un geste que personne ne cherchait. Remplacer une clé se fait en collant la
 * nouvelle — le champ sélectionne tout ce qu'il contient dès qu'on le touche, donc le
 * coller écrase l'ancienne. L'effacement complet reste où il a toujours été, dans
 * « Tout effacer » de l'écran de sauvegarde.
 *
 * **Le modèle et l'URL** : repliés sous « Réglages avancés ». Préremplis, ils n'avaient
 * rien à demander à quelqu'un qui ne peut ni les connaître ni les vérifier — c'est le
 * raisonnement de [D143][decisions] sur l'URL de base, appliqué au champ voisin.
 *
 * Reste un champ, et trois boutons qui disent ce qu'ils font, dans l'ordre où on s'en
 * sert : en obtenir une, la coller, la valider ([D156][decisions], [D157][decisions]).
 *
 * [decisions]: docs/11-decisions.md
 */
@Composable
internal fun ProviderEditor(
    provider: AiProvider,
    form: ProviderForm,
    check: KeyCheck,
    inUse: Boolean,
    actions: AiSettingsActions,
) {
    var advanced by rememberSaveable { mutableStateOf(false) }

    // **Saveable**, et c'est ce qui le fait survivre au detour : la navigation garde
    // l'etat sauvegardable d'un ecran pendant qu'un autre est par-dessus. Au retour du
    // navigateur, il dit donc encore qu'on est parti chercher une cle -- et que le geste
    // suivant est de la coller (D157).
    var wentForKey by rememberSaveable { mutableStateOf(false) }
    var paste by remember { mutableStateOf(PasteState()) }
    val clipboard = LocalClipboardManager.current

    KeyField(form, actions, paste) { paste = paste.copy(nothingToPaste = false) }

    KeyButtons(
        provider = provider,
        emphasis = when {
            form.apiKey.isNotBlank() -> KeyStep.VALIDATE
            wentForKey -> KeyStep.PASTE
            else -> KeyStep.GET
        },
        onGetKey = {
            wentForKey = true
            actions.onGetKey(provider)
        },
        onPaste = {
            // **Lu a l'appui, et a l'appui seulement** : Android annonce chaque lecture
            // du presse-papiers, et une lecture que personne n'a demandee serait une
            // indiscretion. Les blancs partent -- un navigateur en copie souvent un.
            val text = clipboard.getText()?.text?.trim().orEmpty()
            paste = if (text.isEmpty()) {
                paste.copy(nothingToPaste = true)
            } else {
                actions.onKey(text)
                PasteState(text = text, count = paste.count + 1)
            }
        },
    )

    if (paste.nothingToPaste) {
        Text(
            text = stringResource(R.string.ai_key_paste_empty),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
        )
    }

    AdvancedSettings(provider, form, actions, expanded = advanced) { advanced = !advanced }

    Refusal(check)

    ValidateButton(form, check, inUse, actions)
}

/**
 * Ce que le bouton « Coller la clé » vient de poser dans le champ.
 *
 * **Le champ garde sa propre valeur** ([DraftTextField], [D45][decisions]) et ne relit
 * `initial` qu'en naissant : écrire dans le modèle ne change donc pas ce qu'il affiche.
 * Un collage le fait **renaître**, avec le texte collé comme valeur de départ — [count]
 * est la clé de cette renaissance, et le texte voyage avec elle plutôt que d'être relu
 * dans le modèle, qui pourrait ne pas l'avoir encore reçu à la recomposition suivante.
 *
 * [decisions]: docs/11-decisions.md
 */
private data class PasteState(
    val text: String? = null,
    val count: Int = 0,
    /** Le dernier appui n'a rien trouvé à coller. S'efface à la frappe ou au collage suivant. */
    val nothingToPaste: Boolean = false,
)

/** L'étape où l'on en est, et donc le bouton qui doit attirer l'œil. */
private enum class KeyStep { GET, PASTE, VALIDATE }

/**
 * Le champ de la clé : masqué, révélable, et **tout sélectionné au toucher**.
 *
 * Le masquage est ce que [docs/05][ia] demande — on doit pouvoir vérifier une clé sans
 * la recoller, mais pas la laisser traîner à l'écran.
 *
 * La sélection totale est ce qui remplace le bouton « Effacer la clé » : une clé est
 * une chaîne de trente-neuf caractères sans sens qu'on ne corrige jamais, on la
 * remplace. La sélectionner d'office rend le collage écrasant, ce qui est le seul geste
 * qu'on ait sur ce champ ([D156][decisions]).
 *
 * [ia]: docs/05-ia.md
 * [decisions]: docs/11-decisions.md
 */
@Composable
private fun KeyField(form: ProviderForm, actions: AiSettingsActions, paste: PasteState, onTyped: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        key(paste.count) {
            DraftTextField(
                initial = paste.text ?: form.apiKey,
                onValueChange = {
                    onTyped()
                    actions.onKey(it)
                },
                label = stringResource(R.string.ai_key_label),
                modifier = Modifier.weight(1f),
                visualTransformation = if (form.revealed) VisualTransformation.None else PasswordVisualTransformation(),
                selectAllOnFocus = true,
            )
        }
        Text(
            text = stringResource(if (form.revealed) R.string.ai_hide else R.string.ai_reveal),
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.clickable(onClick = actions.onReveal).padding(Spacing.sm),
        )
    }
}

/**
 * Les deux premiers gestes, côte à côte : obtenir une clé, puis la coller.
 *
 * ### Pourquoi « Coller » a son bouton
 *
 * Coller dans un champ demande un appui long, puis de viser une bulle du système — sur un
 * champ masqué par des points, où l'on ne voit même pas si le texte est arrivé. C'est le
 * geste que les testeurs ratent, au moment précis où ils reviennent du navigateur avec la
 * clé en poche ([D157][decisions]). Un bouton le réduit à un appui.
 *
 * ### Celui qui brille suit l'étape
 *
 * Un seul des trois boutons de l'écran est plein à la fois, et c'est le prochain geste :
 * « Récupérer » tant qu'on n'est parti chercher aucune clé, « Coller » au retour, puis
 * « Valider » — en bas — dès que le champ a quelque chose à valider. L'œil suit le
 * chemin sans qu'une phrase ait à le décrire.
 *
 * « Récupérer » est absent quand il n'y a pas de console unique à ouvrir — c'est le cas
 * du relais générique — et « Coller » prend alors toute la largeur.
 *
 * [decisions]: docs/11-decisions.md
 */
@Composable
private fun KeyButtons(provider: AiProvider, emphasis: KeyStep, onGetKey: () -> Unit, onPaste: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        if (provider.consoleUrl.isNotEmpty()) {
            NeonButton(
                text = stringResource(R.string.ai_key_get),
                onClick = onGetKey,
                modifier = Modifier.weight(1f),
                style = if (emphasis == KeyStep.GET) NeonButtonStyle.FILLED else NeonButtonStyle.OUTLINED,
            )
        }
        NeonButton(
            text = stringResource(R.string.ai_key_paste),
            onClick = onPaste,
            modifier = Modifier.weight(1f),
            style = if (emphasis == KeyStep.PASTE) NeonButtonStyle.FILLED else NeonButtonStyle.OUTLINED,
        )
    }
}

/**
 * Le modèle et l'adresse, **derrière un dépliant fermé**.
 *
 * Les deux sont préremplis et justes pour tout le monde sauf celui qui monte un relais.
 * Les laisser au premier plan faisait d'un écran à un champ un formulaire à trois, dont
 * deux que personne ne peut vérifier — et un champ qu'on ne peut que casser n'est pas un
 * réglage ([D143][decisions]).
 *
 * **Ils ne disparaissent pas pour autant.** Changer de modèle est le seul recours quand
 * celui qu'on utilise est saturé une heure durant, et c'est un recours réel : il vit
 * maintenant là où on le cherche — sous « Réglages avancés » — au lieu d'être le
 * deuxième champ d'une mise en route.
 *
 * [decisions]: docs/11-decisions.md
 */
@Composable
private fun AdvancedSettings(
    provider: AiProvider,
    form: ProviderForm,
    actions: AiSettingsActions,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(vertical = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.ai_advanced),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Icon(
            imageVector = if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
            // Muet : le libelle est juste a cote, et le chevron ne dit rien de plus
            // qu'une liste depliee ne dise deja.
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    AnimatedVisibility(visible = expanded) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            ModelField(provider, form, actions)

            // Rien a regler quand l'adresse est unique : Gemini et Claude en ont une
            // seule, et un champ qu'on ne peut que casser n'est pas un reglage (D143).
            if (provider.editableBaseUrl) {
                BaseUrlField(form, actions)
            }
        }
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

/**
 * Le refus, **sous le champ qu'il demande de corriger**.
 *
 * Pas une boîte à fermer : il faudrait la fermer pour revoir la clé, et le message
 * disparaîtrait au moment où l'on s'en sert. Ici il reste lisible pendant la
 * correction, et s'efface à la première frappe — ce qui est vrai, puisqu'il ne
 * concerne plus ce qui est écrit ([D156][decisions]).
 *
 * [decisions]: docs/11-decisions.md
 */
@Composable
private fun Refusal(check: KeyCheck) {
    val refused = check as? KeyCheck.Refused ?: return

    Text(
        text = stringResource(refused.messageRes),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.error,
    )

    // Ce que le fournisseur a repondu, sous le message et en plus discret : c'est un
    // renseignement pour diagnostiquer, pas la phrase qu'on lit d'abord.
    refused.detail?.let {
        Text(
            text = it,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Le seul bouton d'envoi de l'écran, et les trois phrases qu'il porte.
 *
 * « Valider la clé » quand il y a quelque chose à valider, « Vérification… » pendant
 * l'appel, « Utilisé » quand la clé ouverte est déjà celle qui sert et que rien n'a
 * bougé. Les trois se lisent à l'endroit où le doigt appuie, ce qui est la place d'une
 * confirmation.
 *
 * **Grisé pendant l'essai autant que sur un formulaire vide** : un second appui
 * pendant qu'un appel est en vol partirait une seconde fois.
 */
@Composable
private fun ValidateButton(form: ProviderForm, check: KeyCheck, inUse: Boolean, actions: AiSettingsActions) {
    val label = when {
        check == KeyCheck.Running -> R.string.ai_validating
        inUse -> R.string.ai_in_use
        else -> R.string.ai_validate
    }

    NeonButton(
        text = stringResource(label),
        onClick = actions.onValidate,
        modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm),
        style = NeonButtonStyle.FILLED,
        availability = when {
            check == KeyCheck.Running || inUse -> NeonButtonAvailability.DISABLED
            form.complete -> NeonButtonAvailability.AVAILABLE
            else -> NeonButtonAvailability.DISABLED
        },
    )
}
