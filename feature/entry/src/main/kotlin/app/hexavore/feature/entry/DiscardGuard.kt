package app.hexavore.feature.entry

import androidx.activity.compose.BackHandler
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource

/**
 * Le retour du système, sur un brouillon qui perdrait quelque chose ([D161][decisions]).
 *
 * ### Pourquoi le retour, et pas « Annuler »
 *
 * Les deux fermaient l'écran sans rien demander, et c'était voulu : un garde-fou sur l'un
 * des deux chemins et pas sur l'autre ne protège rien. C'est vrai de qui veut partir. Ça
 * ne l'est pas de qui part sans le vouloir, et seul le retour se déclenche sans le
 * vouloir : un glissement depuis le bord, sur un formulaire qu'on fait défiler du pouce.
 * Il emportait jusqu'à une proposition d'IA, payée, et qui ne se rejoue pas.
 *
 * « Annuler » reste sans question : c'est un bouton qui dit ce qu'il fait, et qu'il faut
 * viser. Le confirmer pénaliserait le cas volontaire.
 *
 * ### Une fois, sans insister
 *
 * C'est la règle que [docs/02][parcours] pose pour toute modale : une question, deux
 * réponses, et rien derrière. « Quitter » ferme ; « Continuer la saisie », un appui à côté
 * ou un second retour rendent l'écran tel qu'il était.
 *
 * Une fois **par tentative**, et non une fois pour toutes : un retour accidentel se
 * répète, et le second emporterait le brouillon que le premier avait sauvé.
 *
 * ### Pendant l'écriture
 *
 * Le retour ne fait rien, comme « Annuler », grisé au même moment : quitter l'écran à cet
 * instant pourrait couper l'écriture avec le `ViewModel` qui la porte.
 *
 * [decisions]: docs/11-decisions.md
 * [parcours]: docs/02-parcours-et-ecrans.md
 */
@Composable
internal fun DiscardGuard(state: EntryUiState.Content, onDiscard: () -> Unit) {
    var asking by rememberSaveable { mutableStateOf(false) }

    BackHandler(enabled = state.saving || state.unsaved) {
        if (!state.saving) asking = true
    }

    if (asking) {
        AlertDialog(
            onDismissRequest = { asking = false },
            title = { Text(text = stringResource(R.string.entry_discard_title)) },
            text = { Text(text = stringResource(R.string.entry_discard_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        asking = false
                        onDiscard()
                    },
                ) { Text(text = stringResource(R.string.entry_discard_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { asking = false }) {
                    Text(text = stringResource(R.string.entry_discard_cancel))
                }
            },
        )
    }
}
