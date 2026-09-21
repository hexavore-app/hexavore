package app.hexavore.feature.home

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import app.hexavore.core.designsystem.component.MacroSegmentRing
import app.hexavore.core.designsystem.component.NoticeDot
import app.hexavore.core.designsystem.theme.NeonTheme
import app.hexavore.domain.nutrition.Macro
import app.hexavore.domain.usecase.CalendarDay
import java.time.LocalDate

/**
 * Une journée, réduite à ce qui tient sous le pouce.
 *
 * **Trois états, et ils ne se confondent pas.**
 *
 * - Une journée **notée** porte son anneau segmenté, rempli selon l'objectif du jour.
 * - Une journée **sans saisie** n'a pas d'anneau du tout — ni vide, ni gris :
 *   **absent**. C'est la seule façon de ne pas confondre « je n'ai rien noté » avec
 *   « je n'ai rien mangé », et c'est un critère de fin de la tranche 7.
 * - Une journée **à venir** s'affiche en retrait et ne s'ouvre pas : [docs/02][parcours]
 *   interdit la saisie dans le futur, et un écran Journée d'un jour à venir ne pourrait
 *   rien proposer d'utile tout en laissant croire le contraire.
 *
 * **Elle ne calcule pas sa taille.** Elle prend la place que `CalendarPane` lui donne
 * et y loge un anneau. C'est le désaccord entre deux comptes de marges — un ici, un
 * dans le calcul — qui rendait les pastilles ovales ; il ne reste qu'un compte.
 *
 * [parcours]: docs/02-parcours-et-ecrans.md
 */
@Composable
internal fun DayCell(
    date: LocalDate,
    state: CalendarUiState,
    /** La place que la cellule occupe ; l'anneau tient dedans. */
    footprint: Dp,
    selected: LocalDate?,
    /** La veille est vide : elle porte une pastille, sur la journée concernée. */
    flagYesterday: Boolean,
    onOpenDay: (LocalDate) -> Unit,
) {
    val future = state.isFuture(date)
    val day = state.days[date]
    // Le jour affiche par l'ecran. `null` veut dire aujourd'hui : c'est la meme
    // convention que partout ailleurs, et elle evite qu'un ecran laisse ouvert
    // pendant la nuit garde son cerne sur la veille.
    val shown = date == (selected ?: state.today)
    val flagged = flagYesterday && date == state.today.minusDays(1)

    Box(
        contentAlignment = Alignment.TopEnd,
        modifier = Modifier
            // La cellule prend sa place, sans en retirer ni en ajouter : c'est ce
            // qui garantit que sept tiennent dans la largeur. Elle ne compte rien
            // elle-meme, elle consomme ce que `CalendarPane` a calcule.
            .size(footprint)
            // Un jour a venir n'est pas cliquable : pas de ride au toucher, pas de
            // navigation, et le lecteur d'ecran ne l'annonce pas comme un bouton.
            .let { base -> if (future) base else base.clickable { onOpenDay(date) } },
    ) {
        SelectedDisc(shown = shown, diameter = footprint)

        MacroSegmentRing(
            modifier = Modifier.align(Alignment.Center),
            progress = day.progress(),
            diameter = ringDiameter(footprint),
            contentDescription = stringResource(date.labelOf(day, future), date.dayOfMonth),
            center = {
                Text(
                    text = date.dayOfMonth.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = when {
                        future -> MaterialTheme.colorScheme.outline
                        // Le jour affiche, et non plus seulement aujourd'hui : c'est
                        // lui que les six compteurs decrivent, et sur lui que le
                        // bouton d'ajout ecrit.
                        shown -> MaterialTheme.colorScheme.onSurface
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    fontWeight = if (shown) FontWeight.Bold else null,
                )
            },
        )

        if (flagged) {
            NoticeDot(
                label = stringResource(R.string.notice_yesterday_empty),
                modifier = Modifier.align(Alignment.TopEnd),
            )
        }
    }
}

/**
 * Le disque du jour regardé, sous son anneau.
 *
 * **Un chiffre en gras ne suffisait pas.** C'était tout ce qui distinguait le jour
 * affiché de ses six voisins, au milieu de sept anneaux colorés qui attirent l'œil bien
 * davantage — « on ne sait pas trop où on se situe » commençait là. Un disque plein se
 * voit sans être cherché, et il ne coûte aucune couleur de plus : c'est la teinte du
 * texte, très assourdie.
 *
 * **Il grandit à sa place plutôt que de glisser jusqu'à elle.** Celui qu'on quitte se
 * rétracte pendant que celui qu'on rejoint s'ouvre, et l'œil lit un déplacement — sans
 * qu'aucune des deux cellules ait à connaître la position de l'autre, ce qu'un
 * `LazyRow` ne dit de toute façon pas.
 */
@Composable
private fun BoxScope.SelectedDisc(shown: Boolean, diameter: Dp) {
    val taille by animateFloatAsState(
        targetValue = if (shown) 1f else 0f,
        animationSpec = tween(NeonTheme.motion.gaugeValueMillis),
        label = "pastille du jour regarde",
    )
    if (taille <= 0f) return

    Box(
        modifier = Modifier
            .align(Alignment.Center)
            .size(diameter)
            .graphicsLayer {
                scaleX = taille
                scaleY = taille
                alpha = taille
            }
            .background(
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = DISC_ALPHA),
                shape = CircleShape,
            ),
    )
}

/** Assez pour se voir contre le fond, trop peu pour concurrencer l'anneau qu'il porte. */
private const val DISC_ALPHA = 0.14f

/** Ce que le lecteur d'écran annonce : un anneau absent ne s'entend pas. */
private fun LocalDate.labelOf(day: CalendarDay?, future: Boolean): Int = when {
    future -> R.string.calendar_day_future_a11y
    day == null -> R.string.calendar_day_empty_a11y
    else -> R.string.calendar_day_a11y
}

/**
 * Ce que l'anneau doit remplir, macro par macro.
 *
 * Vide quand la journée n'existe pas **ou** qu'aucun objectif ne la couvrait : dans les
 * deux cas il n'y a rien à comparer, et un anneau plein dirait le contraire. Une
 * journée notée avant qu'un objectif existe garde donc ses chiffres — ils se lisent
 * dans l'écran Journée — sans que la pastille prétende les juger.
 */
internal fun CalendarDay?.progress(): Map<Macro, Float> {
    val goal = this?.goal ?: return emptyMap()
    return Macro.entries.associateWith { macro ->
        val target = goal[macro]
        if (target <= 0.0) 0f else (totals[macro].value / target).toFloat()
    }
}
