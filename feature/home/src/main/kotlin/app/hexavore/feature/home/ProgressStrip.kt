package app.hexavore.feature.home

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import app.hexavore.core.designsystem.theme.NeonTheme
import app.hexavore.core.designsystem.theme.Radius
import app.hexavore.core.designsystem.theme.Spacing
import app.hexavore.domain.progress.Progress

/**
 * Trois choses, sur une ligne, et **rien de plus**.
 *
 * La série en cours, le niveau, et la jauge qui dit la distance du suivant. L'accueil
 * répond déjà à une question — comment va ma journée — et lui en ajouter une seconde
 * en pleine page rendrait la première moins nette. Ce qui se déplie — les dix-huit
 * paliers, les deux séries, l'histoire — vit dans l'écran de progression, à un tap.
 *
 * **Elle est en tête, au-dessus de l'hexagone.** C'est ce qu'on voit sans chercher en
 * ouvrant l'application, et c'est là que la série a un effet : la voir monter est ce
 * qui donne envie de ne pas la casser. Sous les plats, elle ne serait lue que par
 * quelqu'un qui a déjà noté.
 *
 * **Aucun jugement.** Une série à zéro se lit « 0 jour », sans couleur d'alerte ni
 * phrase — c'est la règle de l'accueil depuis toujours ([docs/02][parcours]), et elle
 * ne change pas parce qu'on ajoute un compteur.
 *
 * [parcours]: docs/02-parcours-et-ecrans.md
 */
@Composable
internal fun ProgressStrip(progress: Progress, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val days = progress.streaks.logging
    val streak = pluralStringResource(R.plurals.home_streak_days, days, days)
    val level = stringResource(R.string.home_level, progress.level.number)

    // La jauge glisse jusqu'a sa nouvelle valeur plutot que d'y sauter : c'est le seul
    // endroit de l'ecran ou un progres se voit *arriver*, et un saut le ferait manquer.
    val fraction by animateFloatAsState(
        targetValue = progress.level.fraction,
        animationSpec = tween(NeonTheme.motion.gaugeValueMillis),
        label = "niveau",
    )

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(Radius.card),
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
            .clearAndSetSemantics {
                contentDescription = "$streak. $level"
                role = Role.Button
            },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.md),
            horizontalArrangement = Arrangement.spacedBy(Spacing.lg),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = streak, style = MaterialTheme.typography.titleMedium)
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                Text(
                    text = level,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}
