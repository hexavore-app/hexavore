package app.hexavore.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.hexavore.core.designsystem.theme.NeonTheme
import app.hexavore.domain.nutrition.Macro
import kotlin.math.roundToInt

/**
 * La part qu'un plat a prise sur la journée, en un trait.
 *
 * **Ce n'est pas une `MacroBar` en petit.** La barre des compteurs répond à « où j'en
 * suis » sur la journée entière, avec sa valeur écrite et son dépassement qui rétrécit
 * l'échelle. Celle-ci répond à une question qu'aucun chiffre ne posait : **ce plat,
 * c'était combien de ma journée ?** Elle n'a ni valeur, ni échelle, ni légende — le
 * chiffre est juste au-dessus.
 *
 * **Pleine au-delà de l'objectif**, sans saturation ni dents de scie : un plat qui
 * dépasse à lui seul l'objectif du jour est un fait rare et déjà lisible dans le
 * chiffre. Rétrécir l'échelle comme le fait la grande barre demanderait de la lire, et
 * il n'y a rien à lire ici.
 *
 * **Muette pour un lecteur d'écran** : la ligne qui la porte annonce déjà « protéines
 * 24 g », et « barre de progression, 17 % » ne dirait rien de plus que la même chose
 * autrement. La règle du projet veut qu'une couleur ne renseigne jamais seule ; elle ne
 * renseigne pas du tout ici — elle rappelle celle du chiffre.
 *
 * @param share part dans `[0, 1]`, déjà plafonnée par l'appelant.
 */
@Composable
fun MacroShareBar(macro: Macro, share: Float, modifier: Modifier = Modifier) {
    val palette = NeonTheme.macros[macro]

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(BarHeight)
            .clearAndSetSemantics { }
            .clip(RoundedCornerShape(BarHeight / 2))
            // La piste est la teinte de la macro, tres assourdie : un gris neutre
            // aurait fait une septieme couleur dans une figure qui en compte six.
            .background(palette.base.copy(alpha = TRACK_ALPHA)),
    ) {
        Box(
            modifier = Modifier
                .height(BarHeight)
                // `layout` et non `fillMaxWidth(fraction)` : la fraction se calcule sur
                // les contraintes recues, et un arrondi de pixel ferait disparaitre une
                // part tres faible au lieu de la montrer minuscule.
                .layout { measurable, constraints ->
                    val largeur = (constraints.maxWidth * share).roundToInt().coerceAtLeast(0)
                    val placeable = measurable.measure(constraints.copy(minWidth = largeur, maxWidth = largeur))
                    layout(placeable.width, placeable.height) { placeable.place(0, 0) }
                }
                .clip(RoundedCornerShape(BarHeight / 2))
                .background(palette.base),
        )
    }
}

/** Trois points : assez pour se voir sous une étiquette, trop peu pour se lire. */
private val BarHeight: Dp = 3.dp

private const val TRACK_ALPHA = 0.18f
