package app.hexavore.feature.progress

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.hexavore.core.designsystem.theme.Spacing
import app.hexavore.domain.progress.Badge
import app.hexavore.domain.progress.Progress
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * Les paliers, pris et à prendre.
 *
 * **Tous affichés, y compris ceux qui ne sont pas pris.** Un palier caché tant qu'il
 * n'est pas atteint ne donne envie de rien : c'est la liste entière qui dit ce que
 * l'application récompense, donc ce qu'elle attend. Ceux qui ne sont pas pris sont
 * estompés et sans date — la même distinction qu'entre un chiffre connu et un chiffre
 * manquant, faite par le ton et non par l'absence.
 *
 * **Groupés par famille**, dans l'ordre où on les rencontre : la régularité, le volume,
 * la justesse, puis la découverte. Mélangés, dix-huit paliers seraient une liste ; par
 * famille, ce sont quatre échelles dont on voit où l'on se situe.
 */
@Composable
internal fun BadgeSection(progress: Progress, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(Spacing.lg)) {
        Text(text = stringResource(R.string.progress_badges), style = MaterialTheme.typography.titleMedium)

        Badge.Family.entries.forEach { family ->
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Text(
                    text = stringResource(family.label()),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Badge.entries.filter { it.family == family }.forEach { badge ->
                    BadgeRow(badge = badge, progress = progress)
                }
            }
        }
    }
}

/**
 * Un palier : sa pastille, son nom, et **ce qui le débloque ou l'a débloqué**.
 *
 * La date d'obtention remplace la description une fois le palier pris. C'est ce qui
 * fait de cette liste une histoire plutôt qu'un catalogue : « le 3 mars » dit quelque
 * chose que « sept jours d'affilée » ne dit plus une fois que c'est fait.
 */
@Composable
private fun BadgeRow(badge: Badge, progress: Progress) {
    val taken = badge in progress
    val discovery = badge.family == Badge.Family.DISCOVERY
    // Le nom se construit du seuil pour les trois familles chiffrees : dix-huit
    // libelles diraient dix-huit fois la meme phrase avec un nombre different, et la
    // traduction les porterait tous.
    val name = if (discovery) stringResource(badge.discoveryName()) else stringResource(badge.nameOf(), badge.threshold)
    val detail = when {
        taken -> stringResource(R.string.progress_badge_on, progress.unlocked.getValue(badge).format())
        discovery -> stringResource(badge.discoveryHint())
        else -> stringResource(badge.hint(), badge.threshold)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = Spacing.xs)
            .clearAndSetSemantics { contentDescription = "$name. $detail" },
        horizontalArrangement = Arrangement.spacedBy(Spacing.lg),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Medal(taken = taken, badge = badge)
        Column {
            Text(
                text = name,
                style = MaterialTheme.typography.bodyLarge,
                color = if (taken) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
            Text(
                text = detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * La pastille d'un palier.
 *
 * **Le seuil est écrit dedans**, et c'est le second canal : un disque allumé et un
 * disque éteint se distinguent par la teinte, ce qu'une règle du projet interdit de
 * laisser travailler seul ([08][design]). Le chiffre dit aussi ce que le palier vaut,
 * ce qu'aucune couleur ne dirait.
 *
 * [design]: docs/08-design-system.md
 */
@Composable
private fun Medal(taken: Boolean, badge: Badge) {
    Surface(
        shape = CircleShape,
        color = if (taken) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        },
        modifier = Modifier.size(MedalSize),
    ) {
        Column(
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = if (badge.family == Badge.Family.DISCOVERY) DISCOVERY_MARK else badge.threshold.toString(),
                style = MaterialTheme.typography.labelLarge,
                color = if (taken) {
                    MaterialTheme.colorScheme.onSecondaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
    }
}

/** Le nom d'une famille, tel que l'écran la titre. */
private fun Badge.Family.label(): Int = when (this) {
    Badge.Family.STREAK -> R.string.progress_family_streak
    Badge.Family.VOLUME -> R.string.progress_family_volume
    Badge.Family.PERFECT -> R.string.progress_family_perfect
    Badge.Family.DISCOVERY -> R.string.progress_family_discovery
}

/** Le nom d'un palier chiffré : sa famille, et son seuil en paramètre. */
private fun Badge.nameOf(): Int = when (family) {
    Badge.Family.STREAK -> R.string.progress_name_streak
    Badge.Family.VOLUME -> R.string.progress_name_volume
    Badge.Family.PERFECT -> R.string.progress_name_perfect
    Badge.Family.DISCOVERY -> R.string.progress_name_streak
}

/**
 * Ce qui reste à faire pour le prendre.
 *
 * Une phrase par famille et non par palier : dix-huit chaînes diraient dix-huit fois
 * la même chose avec un nombre différent, et la traduction les porterait toutes.
 */
private fun Badge.hint(): Int = when (family) {
    Badge.Family.STREAK -> R.string.progress_hint_streak
    Badge.Family.VOLUME -> R.string.progress_hint_volume
    Badge.Family.PERFECT -> R.string.progress_hint_perfect
    Badge.Family.DISCOVERY -> R.string.progress_hint_streak
}

/** Les quatre découvertes ont chacune leur nom : ce sont quatre gestes différents. */
private fun Badge.discoveryName(): Int = when (this) {
    Badge.FIRST_SCAN -> R.string.progress_name_first_scan
    Badge.FIRST_PHOTO -> R.string.progress_name_first_photo
    Badge.FIRST_TEXT -> R.string.progress_name_first_text
    else -> R.string.progress_name_first_favorite
}

/** Et sa phrase, qui dit le geste plutôt qu'un seuil. */
private fun Badge.discoveryHint(): Int = when (this) {
    Badge.FIRST_SCAN -> R.string.progress_hint_first_scan
    Badge.FIRST_PHOTO -> R.string.progress_hint_first_photo
    Badge.FIRST_TEXT -> R.string.progress_hint_first_text
    else -> R.string.progress_hint_first_favorite
}

private fun java.time.LocalDate.format(): String = format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))

private val MedalSize: Dp = 44.dp

/** Une découverte n'a pas de seuil à écrire : on l'a fait, ou pas. */
private const val DISCOVERY_MARK = "·"
