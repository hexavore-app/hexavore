package app.hexavore.feature.scan

import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.ui.Modifier
import app.hexavore.core.designsystem.theme.Spacing

/**
 * Sous l'heure, et à l'écart du bord ([D163][decisions]).
 *
 * L'aperçu va jusqu'aux bords de l'écran, et c'est voulu : une caméra qui s'arrêterait
 * sous la barre d'état laisserait une bande morte. Ce qui se pose dessus, non : sans ses
 * encarts, « Fermer » se dessinait par-dessus l'heure, et les issues d'un produit
 * inconnu sous la barre de geste. Les encarts sont pris **par barre** : un panneau posé
 * en bas n'a rien à faire de la hauteur de la barre d'état.
 *
 * [decisions]: docs/11-decisions.md
 */
internal fun Modifier.belowStatusBar(): Modifier = statusBarsPadding().padding(Spacing.md)

/** Au-dessus de la barre de geste, pour ce qui se pose en bas de l'aperçu. */
internal fun Modifier.aboveNavigationBar(): Modifier = navigationBarsPadding()
