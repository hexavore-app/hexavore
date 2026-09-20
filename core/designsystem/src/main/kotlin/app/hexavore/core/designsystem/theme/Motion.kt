package app.hexavore.core.designsystem.theme

import android.provider.Settings
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode

/**
 * Les durées d'animation du projet, en millisecondes.
 *
 * Le néon invite à trop animer, d'où un cadre étroit et une seule façon d'en
 * sortir : ajouter une entrée ici, en revue. Une durée écrite dans un composant
 * est un défaut au même titre qu'une couleur écrite en dur.
 *
 * Les instances ne sont pas lues directement : elles passent par `NeonTheme.motion`,
 * qui rend zéro quand l'utilisateur a demandé moins d'animations.
 *
 * @see docs/08-design-system.md
 */
@Immutable
data class Motion(
    /** Variation de la valeur d'une jauge. */
    val gaugeValueMillis: Int,
    /** Ouverture d'une feuille modale. */
    val sheetEnterMillis: Int,
    /** Appui sur un bouton. */
    val buttonPressMillis: Int,
    /** Apparition d'un contenu. */
    val contentEnterMillis: Int,
    /** Décalage appliqué à chaque élément successif d'une apparition. */
    val contentStaggerMillis: Int,
    /** Sortie d'une journée et entrée de sa voisine, au glissement horizontal. */
    val daySwipeMillis: Int,
) {
    companion object {
        /** Le cadre nominal. */
        val Standard =
            Motion(
                gaugeValueMillis = 400,
                sheetEnterMillis = 300,
                buttonPressMillis = 100,
                contentEnterMillis = 200,
                contentStaggerMillis = 30,
                daySwipeMillis = 200,
            )

        /**
         * Toutes les durées à zéro.
         *
         * Ce n'est pas une préférence esthétique : le réglage d'accessibilité
         * correspondant existe le plus souvent pour des raisons vestibulaires.
         * Une animation « juste un peu plus courte » ne répond pas au besoin.
         */
        val None =
            Motion(
                gaugeValueMillis = 0,
                sheetEnterMillis = 0,
                buttonPressMillis = 0,
                contentEnterMillis = 0,
                contentStaggerMillis = 0,
                daySwipeMillis = 0,
            )

        /** Courbe des jauges et des apparitions de contenu. */
        val GaugeEasing: Easing = FastOutSlowInEasing

        /** Courbe d'ouverture des feuilles modales. */
        val DecelerateEasing: Easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)

        /** Courbe des appuis. */
        val PressEasing: Easing = LinearOutSlowInEasing
    }
}

/**
 * Les délais qui ne sont pas des animations.
 *
 * Séparés de [Motion] pour une raison précise : `Motion.None` ramène toutes ses
 * durées à zéro quand l'appareil demande moins de mouvement. Une fenêtre
 * d'annulation à zéro seconde ne serait pas une animation plus sobre, ce serait une
 * suppression définitive sans recours. Ces délais-là ne dépendent d'aucun réglage.
 */
object Timing {
    /**
     * Le temps laissé pour annuler une suppression.
     *
     * Cinq secondes : assez pour lire le message et revenir sur un geste involontaire,
     * trop peu pour que la barre gêne la suite. `SnackbarDuration.Short` en vaut
     * quatre et `Long` dix ; ni l'un ni l'autre n'est ce que demande docs/02.
     */
    const val UNDO_WINDOW_MILLIS: Long = 5_000L
}

/**
 * Le jeu de durées à appliquer, compte tenu des réglages de l'appareil.
 *
 * Lit `ANIMATOR_DURATION_SCALE` : c'est le réglage système que l'utilisateur met à
 * zéro quand le mouvement le gêne. Le respecter n'est pas optionnel.
 */
@Composable
internal fun rememberMotion(): Motion {
    if (LocalInspectionMode.current) return Motion.Standard
    val context = LocalContext.current
    return remember(context) {
        val scale =
            Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f,
            )
        if (scale == 0f) Motion.None else Motion.Standard
    }
}
