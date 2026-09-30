package app.hexavore.core.designsystem.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.hexavore.core.designsystem.theme.NeonTheme
import app.hexavore.core.designsystem.theme.Spacing
import app.hexavore.domain.nutrition.Macro
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Ce qui se passe à l'écran quand un palier tombe.
 *
 * ### Pourquoi une célébration, dans une application qui n'en avait aucune
 *
 * [08][design] disait de la lueur des jauges qu'elle était « la seule récompense
 * visuelle de l'application, et qu'elle suffit ». Elle ne suffisait pas pour une chose :
 * elle est **continue**. Une lueur qui monte avec la journée ne peut pas dire « il
 * vient de se passer quelque chose », parce qu'elle dit déjà autre chose en permanence.
 * Un palier est un événement, et un événement se marque ([D133][decisions]).
 *
 * ### Ce qu'elle ne fait pas
 *
 * **Elle ne bloque rien.** Aucun voile, aucun bouton à fermer, rien à faire disparaître :
 * elle passe, et on continue à noter par-dessus. Une boîte de félicitations à valider
 * transformerait la récompense en interruption.
 *
 * **Elle ne dure pas.** Une seconde et demie, puis plus rien. Ce qui a été obtenu reste
 * lisible dans l'écran de progression ; l'animation n'est pas l'endroit où on lit.
 *
 * **Elle disparaît entièrement quand l'appareil demande moins de mouvement.** Pas
 * raccourcie : supprimée, et remplacée par le seul texte. Des particules instantanées
 * seraient un clignotement, ce que [08][design] refuse depuis toujours.
 *
 * ### Les six teintes, et pas une septième
 *
 * Les particules empruntent les six couleurs de macro dans l'ordre angulaire commun à
 * l'application. Inventer une couleur de fête aurait été une septième teinte dans une
 * palette qui en réserve six — le raisonnement de [D25][decisions], appliqué à une
 * animation.
 *
 * [design]: docs/08-design-system.md
 * [decisions]: docs/11-decisions.md
 */
@Composable
fun Celebration(title: String, subtitle: String, onDone: () -> Unit, modifier: Modifier = Modifier) {
    val millis = NeonTheme.motion.contentEnterMillis
    val finished = rememberUpdatedState(onDone)

    // Une seule progression de 0 a 1 pour tout : les particules s'ecartent, le texte
    // monte et s'efface. Deux animations independantes auraient fini a deux moments.
    val run = remember { Animatable(0f) }

    LaunchedEffect(title) {
        run.snapTo(0f)
        if (millis > 0) run.animateTo(1f, tween(durationMillis = millis * SLOW_FACTOR, easing = LinearEasing))
        finished.value()
    }

    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        if (millis > 0) Sparks(progress = run.value)

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.xl)
                .graphicsLayer {
                    // Le texte monte d'un cheveu et s'efface a la fin : il arrive avec
                    // les particules et part avec elles.
                    translationY = -run.value * RISE.toPx()
                    alpha = if (run.value > FADE_FROM) (1f - run.value) / (1f - FADE_FROM) else 1f
                },
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/**
 * Les particules, tracées.
 *
 * **Tirées une fois et non à chaque image.** Leur direction et leur distance sont
 * décidées à la première composition, avec une graine fixe : le même palier produit la
 * même gerbe, ce qui la rend reproductible sur une capture. Les retirer à chaque image
 * ferait vibrer les particules sur place au lieu de les envoyer.
 *
 * Elles s'écartent du centre, ralentissent, et s'effacent sur la fin — une gerbe qui
 * disparaîtrait d'un coup se lirait comme un défaut d'affichage.
 */
@Composable
private fun Sparks(progress: Float) {
    val macros = NeonTheme.macros
    val seeds = remember {
        val random = Random(SEED)
        List(SPARK_COUNT) { index ->
            Spark(
                angle = random.nextFloat() * TWO_PI,
                distance = MIN_TRAVEL + (MAX_TRAVEL - MIN_TRAVEL) * random.nextFloat(),
                color = index % SIX,
            )
        }
    }

    Canvas(modifier = Modifier.fillMaxSize()) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val eased = 1f - (1f - progress) * (1f - progress)
        val fade = (1f - progress).coerceIn(0f, 1f)

        seeds.forEach { spark ->
            val travel = spark.distance.toPx() * eased
            drawCircle(
                color = macros[Macro.entries[spark.color]].base.copy(alpha = fade),
                radius = SparkRadius.toPx(),
                center = center + Offset(cos(spark.angle) * travel, sin(spark.angle) * travel),
            )
        }
    }
}

/** Une particule : sa direction, sa distance, et laquelle des six teintes elle porte. */
private data class Spark(val angle: Float, val distance: Dp, val color: Int)

private const val SPARK_COUNT = 24

/** Fixe : la même gerbe se rejoue à l'identique, y compris sur une capture. */
private const val SEED = 6

private const val SIX = 6

private const val TWO_PI = 6.2831855f

/** La fraction de l'animation à partir de laquelle le texte s'efface. */
private const val FADE_FROM = 0.6f

/** Plus lent que l'entrée d'un contenu : c'est une gerbe, pas une apparition. */
private const val SLOW_FACTOR = 7

private val MIN_TRAVEL: Dp = 60.dp
private val MAX_TRAVEL: Dp = 160.dp
private val SparkRadius: Dp = 3.dp
private val RISE: Dp = 12.dp
