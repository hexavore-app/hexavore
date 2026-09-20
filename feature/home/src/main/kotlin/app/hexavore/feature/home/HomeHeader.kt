package app.hexavore.feature.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import app.hexavore.core.designsystem.component.TrendGlyph
import app.hexavore.core.designsystem.component.WithNoticeDot
import app.hexavore.core.designsystem.theme.NeonTheme
import app.hexavore.core.designsystem.theme.Spacing
import app.hexavore.domain.notice.Notice
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * La barre du jour : ce qu'on regarde, et les deux portes de l'accueil.
 *
 * Sortie de `HomeScreen` quand le seuil de fonctions par fichier a mordu, et le
 * decoupage suit ce que les choses sont : cette barre dit **quel jour** l'ecran
 * montre -- depuis que l'accueil en porte un -- la ou le reste du fichier dit ce que
 * ce jour contient.
 */

/**
 * Le titre du jour, et les deux portes de la barre : le poids et le profil.
 *
 * **Le titre est la date quand ce n'est pas aujourd'hui.** C'est le seul endroit de
 * l'écran qui dise quel jour on regarde, et c'est ce que le bouton d'ajout va écrire.
 *
 * En dessous, et seulement alors, le chemin du retour ([TodayChip]).
 */
@Composable
internal fun DayHeader(
    actions: HomeActions,
    day: LocalDate?,
    today: LocalDate,
    swipe: DaySwipeState,
    onBackToToday: () -> Unit,
    notices: Set<Notice> = emptySet(),
) {
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        DayTitle(actions, day, today, swipe, notices)
        // Rien du tout quand on est aujourd'hui : un bouton grise qui ne fait rien
        // occuperait la place et poserait la question de ce qu'il fait la.
        AnimatedVisibility(visible = day != null) { TodayChip(onBackToToday) }
    }
}

/**
 * Le chemin visible du retour à aujourd'hui.
 *
 * Le bouton retour du système y ramène aussi, mais **un geste sans représentation
 * visible est introuvable pour qui ne le connaît pas** — c'est la règle que
 * [docs/02][parcours] applique déjà au balayage qui supprime une ligne. Il reste le
 * raccourci de celui qui le connaît, jamais le seul chemin.
 *
 * La pastille d'aujourd'hui est un troisième chemin, mais elle sort du calendrier dès
 * qu'on remonte de plus d'une semaine.
 *
 * [parcours]: docs/02-parcours-et-ecrans.md
 */
@Composable
private fun TodayChip(onBackToToday: () -> Unit) {
    AssistChip(
        onClick = onBackToToday,
        label = { Text(stringResource(R.string.home_back_to_today)) },
        leadingIcon = {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = null,
                modifier = Modifier.size(AssistChipDefaults.IconSize),
            )
        },
    )
}

/** La date et les deux icônes, sur une ligne. */
@Composable
private fun DayTitle(
    actions: HomeActions,
    day: LocalDate?,
    today: LocalDate,
    swipe: DaySwipeState,
    notices: Set<Notice>,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = dayLabelOf(day, today).text(),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .travelling(swipe)
                .pulsing(day),
        )
        // Des icones seules, sans libelle : ce sont les portes les moins frequentees
        // de l'ecran, et le titre du jour doit rester ce qu'on lit en premier.
        Row {
            IconButton(onClick = actions.onOpenWeight) {
                WithNoticeDot(
                    visible = Notice.WEIGHT_STALE in notices,
                    label = stringResource(R.string.notice_weight_stale),
                ) {
                    TrendGlyph(contentDescription = stringResource(R.string.home_open_weight))
                }
            }
            IconButton(onClick = actions.onOpenSettings) {
                // Les deux pastilles d'IA se posent au meme endroit : le geste a faire
                // est le meme -- ouvrir les reglages et s'occuper de la cle.
                WithNoticeDot(
                    visible = notices.any { it == Notice.AI_NOT_CONFIGURED || it == Notice.AI_KEY_REJECTED },
                    label = stringResource(
                        if (Notice.AI_KEY_REJECTED in notices) {
                            R.string.notice_ai_rejected
                        } else {
                            R.string.notice_ai_absent
                        },
                    ),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Person,
                        contentDescription = stringResource(R.string.home_open_profile),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/**
 * Ce que le titre dit du jour regardé.
 *
 * **Deux jours ont un nom, et c'est celui-là qu'on emploie.** « Hier » se lit plus vite
 * qu'une date qu'il faut décoder, et c'est exactement ce qu'on cherchait : savoir où
 * l'on est sans avoir à lire. Au-delà d'avant-hier, aucun mot ne s'impose et la date
 * longue reprend la main.
 *
 * Un jour égal à aujourd'hui se dit comme aujourd'hui, alors que la convention de
 * l'écran est de porter `null` : les deux existent — le calendrier peut renvoyer la
 * date du jour —, et se contredire d'un chemin à l'autre serait pire que la redondance.
 */
internal fun dayLabelOf(day: LocalDate?, today: LocalDate): DayLabel = when (day) {
    null, today -> DayLabel.Today
    today.minusDays(1) -> DayLabel.Yesterday
    today.minusDays(2) -> DayLabel.BeforeYesterday
    else -> DayLabel.On(day)
}

/** Les quatre façons de nommer un jour. Trois sont des mots, la dernière une date. */
internal sealed interface DayLabel {
    data object Today : DayLabel

    data object Yesterday : DayLabel

    data object BeforeYesterday : DayLabel

    data class On(val date: LocalDate) : DayLabel
}

/**
 * Le libellé écrit.
 *
 * « lundi 10 août 2026 » en format long et non abrégé : le titre est ce qui dit sur quel
 * jour le bouton d'ajout va écrire, et « 10/08 » se confond avec le mois d'à côté d'un
 * coup d'œil trop rapide.
 */
@Composable
private fun DayLabel.text(): String = when (this) {
    DayLabel.Today -> stringResource(R.string.home_title)
    DayLabel.Yesterday -> stringResource(R.string.home_title_yesterday)
    DayLabel.BeforeYesterday -> stringResource(R.string.home_title_before_yesterday)
    is DayLabel.On -> date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL))
}

/**
 * Le titre s'en va avec la journée.
 *
 * **Il glisse moins loin qu'elle, et s'efface en chemin.** Le suivre au pixel près le
 * ferait sortir de l'écran par le côté, alors qu'il est le seul repère qui dise où l'on
 * va : un tiers du déplacement suffit à le rendre solidaire du geste, et l'effacement
 * fait le reste du travail — c'est lui qui donne le sentiment que le titre change, là où
 * une substitution nette passerait inaperçue.
 *
 * Les deux valeurs sont lues au tracé et non en composition : un titre qui se
 * recomposerait à chaque image du geste coûterait bien plus qu'il ne rapporte.
 */
private fun Modifier.travelling(swipe: DaySwipeState): Modifier = graphicsLayer {
    translationX = swipe.offset.value * TITLE_DRIFT
    alpha = 1f - swipe.progress
}

/** Ce que le titre parcourt, rapporté à la journée. Assez pour être solidaire, trop peu pour partir. */
private const val TITLE_DRIFT = 0.35f

/**
 * Le titre marque le coup en arrivant.
 *
 * Un glissement lâché à mi-course ramène la même journée, et un changement de jour
 * décidé depuis le calendrier ne fait glisser personne : dans les deux cas, la
 * pulsation est ce qui distingue « il s'est passé quelque chose » de « rien n'a bougé ».
 *
 * **Rien du tout quand l'appareil demande moins de mouvement.** Ce n'est pas une
 * animation qu'on raccourcit — un grossissement instantané suivi d'un retour instantané
 * serait un clignotement —, c'est un effet qu'on supprime.
 */
@Composable
private fun Modifier.pulsing(day: LocalDate?): Modifier {
    val millis = NeonTheme.motion.contentEnterMillis
    val pulse = remember { Animatable(1f) }

    LaunchedEffect(day) {
        if (millis <= 0) return@LaunchedEffect
        pulse.snapTo(PULSE_PEAK)
        pulse.animateTo(1f, tween(millis))
    }

    return graphicsLayer {
        scaleX = pulse.value
        scaleY = pulse.value
        // Depuis le bord gauche : centre, le titre deborderait de la marge d'ecran
        // d'un cote comme de l'autre.
        transformOrigin = TransformOrigin(0f, PIVOT_MIDDLE)
    }
}

/** Ce que le titre prend en arrivant, avant de revenir. Un douzieme : on le voit sans le lire. */
private const val PULSE_PEAK = 1.08f

private const val PIVOT_MIDDLE = 0.5f
