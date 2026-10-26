package app.hexavore.core.designsystem.component

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import app.hexavore.core.designsystem.R
import app.hexavore.core.designsystem.theme.Spacing
import app.hexavore.domain.ai.AiProvider

/**
 * Qui fait attendre, tel que la note le nomme ([D177][decisions]).
 *
 * @property provider le nom du fournisseur, « Google Gemini » ; vide quand la
 *   configuration n'a pas pu être relue, et la note dit alors « L'IA ».
 * @property free vrai pour un palier gratuit. C'est lui, et lui seul, dont les demandes
 *   passent après celles qu'on paie : dire « c'est le prix de la gratuité » à quelqu'un
 *   qui paie sa clé serait faux.
 *
 * [decisions]: docs/11-decisions.md
 */
@Immutable
data class SlowAnalysis(val provider: String, val free: Boolean) {
    companion object {
        /** La note d'un fournisseur, ou celle d'une configuration qu'on n'a pas pu relire. */
        fun of(provider: AiProvider?): SlowAnalysis =
            SlowAnalysis(provider = provider?.displayName.orEmpty(), free = provider?.free == true)
    }
}

/**
 * La note qui paraît quand l'IA prend plus de dix secondes ([D177][decisions]).
 *
 * ### Ce qu'elle répond
 *
 * La question qu'on se pose devant une attente qui s'allonge : **est-ce que ça marche ?**
 * Avec une clé gratuite, la réponse est presque toujours oui — les demandes gratuites
 * passent après les autres, et c'est le prix de la gratuité. La note le dit, nomme le
 * fournisseur, et précise que l'analyse continue : on n'a rien à faire, et surtout pas à
 * tout refermer en perdant sa photo.
 *
 * ### Pourquoi discrète
 *
 * Elle ne remplace ni l'indicateur ni « Annuler », et elle n'est pas une erreur : un fond
 * de carte en retrait, un petit texte. Partagée par l'écran d'IA et la barre du bas,
 * comme les messages d'erreur ([aiErrorMessage]) : deux endroits qui attendent la même
 * chose doivent le dire de la même façon.
 *
 * **Annoncée au lecteur d'écran** quand elle paraît, poliment : elle arrive sans qu'on ait
 * rien touché, et une phrase qui apparaît en silence n'existe pas pour qui ne la voit pas.
 *
 * [decisions]: docs/11-decisions.md
 */
@Composable
fun SlowAnalysisNote(slow: SlowAnalysis, modifier: Modifier = Modifier) {
    val name = slow.provider.ifBlank { stringResource(R.string.ds_ai_slow_unnamed) }

    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        shape = MaterialTheme.shapes.small,
        modifier = modifier
            .fillMaxWidth()
            .semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        Text(
            text = stringResource(if (slow.free) R.string.ds_ai_slow_free else R.string.ds_ai_slow_paid, name),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm),
        )
    }
}
