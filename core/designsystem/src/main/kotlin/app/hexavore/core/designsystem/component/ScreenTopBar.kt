package app.hexavore.core.designsystem.component

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.hexavore.core.designsystem.theme.Spacing

/**
 * La barre du haut d'un écran modal : une croix, un titre, et **de l'air au-dessus**.
 *
 * ### Le défaut rapporté à l'usage
 *
 * Les boutons de fermeture touchaient le bord de l'écran, sous l'horloge et les icônes
 * du système. La cause n'était pas une marge oubliée sept fois : l'application dessine
 * d'un bord à l'autre (`edgeToEdgeFollowing`), ce qui est le comportement moderne
 * voulu — et **`Scaffold` n'applique pas les encoches à ce qu'on met dans son
 * emplacement `topBar`**. Il les retire du corps, en supposant que la barre s'en
 * charge. Aucune des sept ne s'en chargeait.
 *
 * [statusBarsPadding] répare cela à la source : la barre commence sous l'horloge, quelle
 * que soit la hauteur de l'encoche, du poinçon ou de la barre d'état.
 *
 * ### Pourquoi un composant plutôt qu'un modificateur ajouté sept fois
 *
 * Parce que c'est la même barre. Sept copies de la même `Row` avaient déjà divergé :
 * **deux d'entre elles passaient `contentDescription = null` à leur croix**, qui ne
 * s'annonçait donc pas au lecteur d'écran — un bouton de fermeture introuvable pour qui
 * ne voit pas la croix. Personne ne l'avait remarqué parce qu'il n'y avait rien à
 * comparer. Le paramètre est ici obligatoire.
 *
 * Une règle de disposition tenue à sept endroits est sept règles qui se ressemblent,
 * jusqu'au jour où l'une change ([D103][decisions]).
 *
 * [decisions]: docs/11-decisions.md
 */
@Composable
fun ScreenTopBar(
    title: String,
    onClose: () -> Unit,
    /** Ce que le lecteur d'écran annonce sur la croix. Jamais `null` : une croix muette ne se ferme pas. */
    closeLabel: String,
    modifier: Modifier = Modifier,
    /** Ce qui vient après le titre, à droite. Vide par défaut. */
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            // Les encoches d'abord, la marge ensuite : l'ordre compte, et l'inverse
            // mettrait la marge sous l'horloge plutot qu'entre elle et le titre.
            .statusBarsPadding()
            .padding(horizontal = Spacing.screenMargin, vertical = TopBarBreathing),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onClose) {
            Icon(Icons.Default.Close, contentDescription = closeLabel)
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(start = Spacing.xs),
        )
        actions()
    }
}

/**
 * L'air au-dessus et au-dessous du titre, **en plus** des encoches du système.
 *
 * Les encoches disent où le système dessine ; elles ne disent rien du confort. Sans
 * cette marge, le titre commence exactement au pixel où l'horloge finit — techniquement
 * correct, et désagréable à lire.
 */
private val TopBarBreathing: Dp = 12.dp
