package app.hexavore.core.designsystem.component

import androidx.compose.runtime.Immutable

/**
 * Ce qu'un quartier de l'hexagone doit montrer.
 *
 * **Plus de drapeau de complétude** ([D119][decisions]) : l'accueil ne signale plus
 * qu'un total est amputé d'une valeur inconnue, donc la figure n'a plus à l'estomper.
 * Ce que la base sait reste ce qu'elle sait — `MacroTotal.complete` existe toujours —
 * mais aucun écran ne le montre, et un paramètre que personne ne renseigne serait une
 * décoration.
 *
 * [decisions]: docs/11-decisions.md
 *
 * @param ratio avancement, où 1 vaut l'objectif atteint. Au-delà, le quartier sort
 *   du contour.
 */
@Immutable
data class MacroQuarter(val ratio: Float)
