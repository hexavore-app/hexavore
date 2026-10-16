package app.hexavore.feature.search

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource

/**
 * La suggestion Open Food Facts : la dernière ligne des résultats, et ce qu'elle
 * devient.
 *
 * Dans son propre fichier parce que c'est une **source** de plus, pas un morceau de
 * l'écran de recherche : elle a son port, son état et ses quatre phrases. Le seuil de
 * fonctions de detekt a posé la question ; la coupure existait déjà dans la lecture.
 *
 * @see docs/04-sources-de-donnees.md
 */

/**
 * La dernière ligne des résultats, et ce qu'elle devient une fois touchée.
 *
 * **En bas et non en tête** : ce que l'utilisateur mange vraiment est local, et une
 * proposition qui demande le réseau ne passe pas devant une fiche déjà là.
 *
 * Les produits distants portent une clé préfixée : leurs identifiants sont
 * provisoires, comme ceux de la table de l'ANSES, et pourraient croiser ceux de la
 * liste locale.
 */
internal fun LazyListScope.remoteSection(remote: RemoteSearch, actions: SearchActions) {
    when (remote) {
        RemoteSearch.Offered -> item {
            TextButton(onClick = actions.onSearchRemotely, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.search_ask_open_food_facts))
            }
        }

        RemoteSearch.Searching -> item {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }

        is RemoteSearch.Results -> {
            item { RemoteHeading(remote.foods.isEmpty()) }
            items(remote.foods, key = { "off-" + it.id.value }) { FoodRow(it, actions) }
        }

        RemoteSearch.Unreachable -> {
            // Une phrase plutot qu'une ligne qui disparait : la ligne est offerte meme
            // hors ligne, parce qu'un test de connectivite ment -- un portail captif se
            // declare connecte.
            item {
                Text(
                    text = stringResource(R.string.search_open_food_facts_offline),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            // Et de quoi reessayer sans retaper la requete (D163) : la recherche d'Open
            // Food Facts repond « indisponible » par intermittence, et la phrase seule
            // laissait l'echec sans issue.
            item {
                TextButton(onClick = actions.onSearchRemotely, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.search_open_food_facts_retry))
                }
            }
        }
    }
}

@Composable
private fun RemoteHeading(empty: Boolean) {
    Text(
        text = stringResource(
            if (empty) R.string.search_open_food_facts_nothing else R.string.search_open_food_facts_results,
        ),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/**
 * Rien ne correspond — et la phrase dit **pourquoi**.
 *
 * Sans mot tapé, ce sont les pastilles qui ont vidé la liste. Garder « aucun aliment
 * ne correspond à "" » laisserait croire que le catalogue est vide, alors qu'il suffit
 * de retirer une pastille.
 */
