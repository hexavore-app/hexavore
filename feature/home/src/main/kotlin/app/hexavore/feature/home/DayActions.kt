package app.hexavore.feature.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import app.hexavore.core.designsystem.component.BarcodeGlyph
import app.hexavore.core.designsystem.component.SparkleGlyph
import app.hexavore.core.designsystem.theme.Spacing

/**
 * Ce qu'on peut ajouter à la journée, et rien d'autre.
 *
 * Sorti de `HomeScreen` quand le seuil de fonctions par fichier a mordu, et le
 * découpage suit ce que les choses sont : ce fichier porte les **entrées** de la
 * journée — les modes de saisie et ce qu'on dit quand l'un d'eux n'est pas
 * disponible — là où le reste de l'écran porte ce qu'elle **contient**.
 */

/**
 * Les boutons flottants, empilés.
 *
 * L'étoile ouvre les plats déjà composés, « Ajouter » la recherche — qui porte aussi
 * la saisie manuelle, puisqu'un aliment tapé à la main devient une fiche. « Ajouter »
 * reste le geste principal : c'est le seul qui porte un libellé.
 *
 * **Un seul bouton d'IA** ([D120][decisions]), là où il y en avait deux. « Décrire » et
 * « Photographier » menaient à deux écrans qui ne différaient que par ce qu'ils
 * envoyaient ; ils n'en font plus qu'un, et la colonne y gagne une place — celle qui
 * manquait le plus, puisque quatre boutons empilés laissaient peu de vide au-dessus du
 * pouce.
 *
 * Le bouton d'IA reste **grisé sans clé** : caché, il ne s'apprendrait jamais ; inerte,
 * il n'apprendrait rien non plus, donc l'appui ouvre l'explication ([D73][decisions]).
 *
 * **Toute la colonne s'efface pendant qu'une bulle de sources est ouverte**
 * ([D122][decisions]). Les boutons flottants sont une couche du `Scaffold`, donc
 * dessinés par-dessus tout ce que la page contient : posés sur la colonne des chiffres
 * de la bulle, ils la rendaient illisible. Les faire passer dessous demanderait de
 * sortir la bulle du contenu ; les retirer le temps d'une lecture dit la même chose, et
 * plus justement — on ne note rien pendant qu'on regarde d'où vient une macro.
 *
 * [decisions]: docs/11-decisions.md
 */
@Composable
internal fun DayActions(actions: HomeActions, aiConfigured: Boolean, visible: Boolean = true) {
    var explaining by rememberSaveable { mutableStateOf(false) }

    if (explaining) {
        AiUnavailableDialog(
            onConfigure = {
                explaining = false
                actions.onConfigureAi()
            },
            onDismiss = { explaining = false },
        )
    }

    AnimatedVisibility(visible = visible, enter = fadeIn(), exit = fadeOut()) {
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            AiButton(
                label = stringResource(R.string.home_analyse),
                configured = aiConfigured,
                onClick = actions.onAnalyse,
                onExplain = { explaining = true },
            ) { label -> SparkleGlyph(contentDescription = label) }
            SmallFloatingActionButton(onClick = actions.onScan) {
                BarcodeGlyph(contentDescription = stringResource(R.string.home_scan))
            }
            SmallFloatingActionButton(onClick = actions.onOpenFavorites) {
                Icon(
                    imageVector = Icons.Filled.Star,
                    contentDescription = stringResource(R.string.home_open_favorites),
                )
            }
            ExtendedFloatingActionButton(onClick = actions.onAddDish) {
                Text(text = stringResource(R.string.home_add_dish))
            }
        }
    }
}

/**
 * Visible et grisé plutôt que caché ([D73][decisions]), et **tapable dans les deux
 * cas**.
 *
 * Un bouton absent tant qu'aucune clé n'est saisie ne s'apprend jamais : personne ne
 * cherche dans les réglages une fonctionnalité dont rien n'indique l'existence. Un
 * bouton inerte n'apprend rien non plus — c'est pourquoi l'appui ouvre l'explication
 * et le chemin vers les réglages, au lieu de ne rien faire.
 *
 * [decisions]: docs/11-decisions.md
 */
@Composable
private fun AiButton(
    label: String,
    configured: Boolean,
    onClick: () -> Unit,
    onExplain: () -> Unit,
    icon: @Composable (String) -> Unit,
) {
    val unavailable = stringResource(R.string.home_analyse_unavailable)

    SmallFloatingActionButton(
        // **Sans cle, le bouton explique avant d'emmener.** Y aller directement
        // deposait quelqu'un dans un ecran de reglages sans lui avoir dit pourquoi --
        // ce qu'il cherchait etait de photographier une assiette, pas de configurer
        // un fournisseur. La boite dit ce qui manque et ce que ca coute ; son bouton
        // ouvre la **section d'IA** et non le hub, pour ne pas faire choisir deux fois.
        onClick = if (configured) onClick else onExplain,
        containerColor = if (configured) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        },
        // Le grisé ne se voit pas au lecteur d'ecran : sans cette phrase, le bouton
        // s'annonce comme n'importe quel autre et l'appui semble sans effet.
        modifier = Modifier.semantics { if (!configured) stateDescription = unavailable },
    ) {
        // Les deux glyphes suivent la couleur du contenu : le grise se decide ici,
        // une fois, plutot que dans chaque appelant.
        CompositionLocalProvider(
            LocalContentColor provides
                if (configured) LocalContentColor.current else MaterialTheme.colorScheme.onSurfaceVariant,
        ) {
            icon(label)
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
 * [parcours]: docs/02-parcours-et-ecrans.md
 */
@Composable
private fun AiUnavailableDialog(onConfigure: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.home_ai_title)) },
        text = { Text(stringResource(R.string.home_ai_explanation)) },
        confirmButton = { TextButton(onClick = onConfigure) { Text(stringResource(R.string.home_ai_configure)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.home_ai_later)) } },
    )
}
