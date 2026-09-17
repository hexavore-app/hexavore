package app.hexavore.feature.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import app.hexavore.core.designsystem.theme.NeonTheme
import kotlinx.coroutines.launch
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.sign

/**
 * Le jour qu'un glissement désigne, ou `null` quand il n'y en a pas.
 *
 * **Vers la gauche, le lendemain** : c'est le sens d'une page qu'on tourne, et celui
 * du calendrier au-dessus, où le temps va vers la droite.
 *
 * **Deux bornes, et chacune pour sa raison.** Le journal ne va pas dans le futur —
 * le calendrier refuse déjà d'ouvrir un jour à venir, et noter un repas qu'on n'a pas
 * pris n'a pas de sens. Vers le passé, la borne est celle que le calendrier sait
 * montrer : au-delà, on se promènerait dans des journées qu'aucune pastille ne
 * désigne, sans moyen visible de revenir.
 *
 * @param shown le jour affiché, jamais `null` ici — l'appelant a déjà résolu
 *   « aujourd'hui ».
 * @param forward vers la gauche, donc vers demain.
 */
internal fun dayAfterSwipe(shown: LocalDate, today: LocalDate, earliest: LocalDate, forward: Boolean): LocalDate? =
    when {
        forward -> shown.plusDays(1).takeIf { it <= today }
        else -> shown.minusDays(1).takeIf { it >= earliest }
    }

/**
 * Le glissement emporte-t-il la journée, ou la laisse-t-il revenir en place ?
 *
 * **Deux façons d'emporter**, et il faut les deux. La distance seule oblige à traîner
 * la page sur un quart de l'écran, ce qui est long quand on remonte cinq jours d'un
 * coup ; la vitesse seule ferait partir la journée sur un frôlement. L'une sert le
 * geste appuyé, l'autre le geste vif.
 *
 * La vitesse ne compte que si elle va **dans le sens du déplacement** : un doigt qui
 * repart en arrière à la dernière fraction de seconde annule, il ne confirme pas.
 */
internal fun swipeCarries(offset: Float, velocity: Float, width: Float): Boolean =
    abs(offset) >= width * SWIPE_FRACTION || (abs(velocity) >= SWIPE_VELOCITY && sign(velocity) == sign(offset))

/** Le quart de la largeur : en deçà, c'est une hésitation. */
private const val SWIPE_FRACTION = 0.25f

/** Pixels par seconde. Un geste vif de lecture, pas un frôlement. */
private const val SWIPE_VELOCITY = 800f

/** Ce qui reste d'un glissement refusé : le tiers, pour qu'il se sente sans aboutir. */
private const val REFUSED_RESISTANCE = 3f

/**
 * La journée, qui suit le doigt et cède la place à sa voisine.
 *
 * **Le contenu suit le doigt**, et c'est ce qui distingue ce geste d'un bouton : on
 * voit la journée partir avant de l'avoir lâchée, donc on sait ce qu'on est en train
 * de faire, et on peut y renoncer.
 *
 * **Un glissement refusé résiste au lieu de ne rien faire.** Vers demain depuis
 * aujourd'hui, la page se décale du tiers et revient : c'est la réponse d'une butée,
 * et elle apprend la règle sans une phrase. Un geste qui ne produirait rien du tout se
 * lirait comme un geste non reconnu.
 *
 * **Le contenu sort, puis le suivant entre de l'autre côté.** La journée voisine n'est
 * pas dessinée pendant le geste : la lire d'avance demanderait de tenir trois journées
 * en mémoire pour n'en montrer qu'une, et la base répond plus vite que l'animation ne
 * dure.
 */
@Composable
internal fun SwipingDay(
    shown: LocalDate,
    today: LocalDate,
    earliest: LocalDate,
    onDay: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val offset = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    // Zero quand l'appareil demande moins de mouvement : le jour change alors d'un
    // coup, ce qui reste le bon comportement -- ce n'est pas une decoration qu'on
    // raccourcit, c'est un deplacement qu'on supprime.
    val glissement = NeonTheme.motion.daySwipeMillis

    BoxWithConstraints(modifier) {
        val width = with(LocalDensity.current) { maxWidth.toPx() }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .draggable(
                    orientation = Orientation.Horizontal,
                    state = rememberDraggableState { delta ->
                        // `snapTo` et non une valeur posee : l'animation en cours doit
                        // ceder au doigt, sinon les deux se disputent la position.
                        scope.launch { offset.snapTo(dragged(offset.value, delta, shown, today, earliest)) }
                    },
                    onDragStopped = { velocity ->
                        val forward = offset.value < 0f
                        val target = dayAfterSwipe(shown, today, earliest, forward)
                        if (target != null && swipeCarries(offset.value, velocity, width)) {
                            offset.carryTo(target, forward, width, glissement, onDay)
                        } else {
                            offset.animateTo(0f, tween(glissement))
                        }
                    },
                )
                .graphicsLayer { translationX = offset.value },
            content = { content() },
        )
    }
}

/**
 * Ce que le doigt déplace vraiment.
 *
 * Le déplacement est rendu tel quel quand la journée voisine existe, et divisé par
 * trois quand elle n'existe pas — la butée se sent, elle ne bloque pas net.
 */
private fun dragged(current: Float, delta: Float, shown: LocalDate, today: LocalDate, earliest: LocalDate): Float {
    val wanted = current + delta
    val refused = dayAfterSwipe(shown, today, earliest, forward = wanted < 0f) == null
    return if (refused) wanted / REFUSED_RESISTANCE else wanted
}

/**
 * La journée sort, l'autre entre.
 *
 * Le changement de date est demandé **entre les deux moitiés** : avant, la journée
 * sortante afficherait déjà le contenu de l'entrante ; après son entrée, on verrait
 * l'ancienne glisser en place puis se remplacer d'un coup.
 */
private suspend fun Animatable<Float, *>.carryTo(
    target: LocalDate,
    forward: Boolean,
    width: Float,
    millis: Int,
    onDay: (LocalDate) -> Unit,
) {
    val sortie = if (forward) -width else width
    animateTo(sortie, tween(millis))
    onDay(target)
    snapTo(-sortie)
    animateTo(0f, tween(millis))
}
