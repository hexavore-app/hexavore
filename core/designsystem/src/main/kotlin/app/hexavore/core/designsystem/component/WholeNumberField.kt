package app.hexavore.core.designsystem.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import app.hexavore.core.designsystem.R
import app.hexavore.core.designsystem.theme.Spacing

/**
 * Un champ qui n'accepte que des entiers, **et qui dit pourquoi il refuse une virgule**
 * ([D172][decisions]).
 *
 * ### Le défaut
 *
 * Les six valeurs d'une ligne, celles d'un aliment personnel et les compteurs du profil
 * sont des entiers ([D52][decisions]). Le champ refusait donc la virgule, en silence. Or
 * le clavier numérique d'Android la montre, et on la tape en recopiant une étiquette :
 * « 8,5 » devenait « 85 », dix fois trop, sans que rien ne le signale.
 *
 * ### Refuser, et le dire
 *
 * Les entiers restent la règle. Une virgule ou un point tapés ne sont toujours pas pris,
 * mais une phrase paraît sous le champ : la virgule est ignorée, il faut arrondir. Elle
 * reste tant que le texte n'est pas revenu avant l'endroit du refus, c'est-à-dire tant
 * que ce qui a été tapé ensuite peut être la suite d'une décimale — c'est précisément
 * le « 85 » qu'il faut voir. Le lecteur d'écran l'annonce dès qu'elle paraît.
 *
 * [decisions]: docs/11-decisions.md
 */
@Composable
fun WholeNumberField(
    initial: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    labelColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    isError: Boolean = false,
    estimated: Boolean = false,
) {
    // Le texte au moment du refus, ou `null` quand rien n'a ete refuse.
    var refusedAt by remember { mutableStateOf<String?>(null) }
    var current by remember { mutableStateOf(initial) }

    Column(modifier = modifier) {
        DraftTextField(
            initial = initial,
            onValueChange = { text ->
                current = text
                refusedAt = refusedAt.keptAfter(text)
                onValueChange(text)
            },
            label = label,
            // La largeur se decide dehors, sur la colonne : le champ la remplit.
            modifier = Modifier.fillMaxWidth(),
            labelColor = labelColor,
            keyboardType = KeyboardType.Number,
            isError = isError,
            estimated = estimated,
            accept = String::isWholeNumberField,
            onRefused = { refused -> if (refused.hasDecimalSeparator()) refusedAt = current },
        )
        if (refusedAt != null) {
            Text(
                text = stringResource(R.string.ds_whole_number_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier
                    .padding(start = Spacing.md, top = Spacing.xs)
                    .semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
    }
}

/** La frappe refusée portait-elle un séparateur décimal, virgule ou point ? */
fun String.hasDecimalSeparator(): Boolean = any { it == ',' || it == '.' }

/**
 * Ce qui reste d'un refus quand le texte devient [text] : le refus tient tant que le
 * texte n'est pas revenu **avant** l'endroit où la virgule a été refusée.
 *
 * « 8 », puis « , » refusée, puis « 85 » le garde — c'est le chiffre à voir ; « 8 »
 * aussi, le temps de corriger. Effacé jusqu'à « », il tombe, et un « 85 » tapé ensuite
 * est un 85 voulu.
 */
fun String?.keptAfter(text: String): String? = this?.takeIf { text.length >= it.length }
