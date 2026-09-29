package app.hexavore.feature.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import app.hexavore.core.designsystem.component.BarcodeGlyph
import app.hexavore.core.designsystem.theme.Spacing

/**
 * Les trois autres façons d'ajouter, sous le « + ».
 *
 * **Une feuille et non un arc de boutons** ([D131][decisions]). L'ancienne colonne
 * montrait ses quatre modes en permanence, chacun réduit à un glyphe : le code-barres
 * et l'étoile se reconnaissaient, mais rien ne disait ce qu'ils ouvraient avant d'y
 * être. Ici chaque mode porte son nom et une ligne qui dit ce qu'il fait — c'est le
 * coût d'un geste de plus, payé une fois par les trois gestes les moins fréquents.
 *
 * **Scanner vient en premier** parce que c'est le plus rapide des trois quand il
 * s'applique, et « à la main » en dernier des deux premiers parce que c'est celui qui
 * marche toujours : le repli se cherche moins souvent qu'on ne l'utilise.
 *
 * **Chaque entrée referme la feuille avant de naviguer.** Une feuille qui survit à la
 * navigation revient par-dessus l'écran ouvert quand on fait « retour ».
 *
 * [decisions]: docs/11-decisions.md
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
internal fun AddMenu(actions: HomeActions, onDismiss: () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = Spacing.lg),
        ) {
            MenuEntry(
                title = stringResource(R.string.home_scan),
                subtitle = stringResource(R.string.home_scan_hint),
                onClick = {
                    onDismiss()
                    actions.onScan()
                },
            ) { BarcodeGlyph(contentDescription = "") }

            MenuEntry(
                title = stringResource(R.string.home_add_by_hand),
                subtitle = stringResource(R.string.home_add_by_hand_hint),
                onClick = {
                    onDismiss()
                    actions.onAddDish()
                },
            ) { Icon(imageVector = Icons.Filled.Search, contentDescription = null) }

            MenuEntry(
                title = stringResource(R.string.home_open_favorites),
                subtitle = stringResource(R.string.home_open_favorites_hint),
                onClick = {
                    onDismiss()
                    actions.onOpenFavorites()
                },
            ) { Icon(imageVector = Icons.Filled.Star, contentDescription = null) }
        }
    }
}

/**
 * Une entrée : une icône, un nom, et ce qu'elle ouvre.
 *
 * La ligne entière est la cible, icône et sous-titre compris — la règle que l'accueil
 * applique déjà à ses plats, et qui vaut partout où un bloc entier désigne une seule
 * action.
 *
 * **Le lecteur d'écran n'entend qu'une phrase.** Un nom, un sous-titre et une icône
 * font trois arrêts pour une seule action ; l'entrée les réunit et l'icône se tait,
 * puisqu'elle ne dit rien que le nom ne dise déjà.
 */
@Composable
private fun MenuEntry(title: String, subtitle: String, onClick: () -> Unit, icon: @Composable () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = Spacing.xl, vertical = Spacing.lg)
            .clearAndSetSemantics {
                contentDescription = "$title. $subtitle"
                role = Role.Button
            },
        horizontalArrangement = Arrangement.spacedBy(Spacing.lg),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon()
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Ce qu'on dit quand il n'y a pas de clé, et **où aller**.
 *
 * Une explication sans chemin obligerait à chercher soi-même la bonne section des
 * réglages ; le bouton l'ouvre — la section d'IA directement, et non le hub, parce que
 * quelqu'un qui vient d'appuyer sur l'appareil photo cherche l'endroit où mettre une
 * clé, pas la liste des réglages. [docs/02][parcours] veut cette explication courte :
 * ce qui manque, ce que ça coûte, et rien de plus.
 *
 * Venue de la colonne de boutons flottants, qui la portait avant la barre du bas : la
 * question qu'elle répond n'a pas changé de nature en changeant de bouton.
 *
 * [parcours]: docs/02-parcours-et-ecrans.md
 */
@Composable
internal fun AiUnavailableDialog(onConfigure: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.home_ai_title)) },
        text = { Text(stringResource(R.string.home_ai_explanation)) },
        confirmButton = { TextButton(onClick = onConfigure) { Text(stringResource(R.string.home_ai_configure)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.home_ai_later)) } },
    )
}
