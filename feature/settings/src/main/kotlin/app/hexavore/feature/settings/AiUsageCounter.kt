package app.hexavore.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import app.hexavore.core.designsystem.theme.Spacing

/**
 * Ce que les analyses ont consommé — **des appels et des jetons, jamais un montant**.
 *
 * **Rien tant que rien n'a été consommé** : un compteur à zéro sur une installation
 * neuve n'apprend rien et occupe le bas de l'écran de quelqu'un qui cherche où coller
 * sa clé.
 *
 * L'estimation de coût est retirée. Elle reposait sur une table de tarifs embarquée,
 * relevée un jour donné : elle vieillit sans prévenir, personne ne la corrige, et un
 * montant approximatif affiché avec l'autorité d'un chiffre est pire qu'aucun montant.
 * Le fournisseur, lui, facture exactement — et c'est chez lui qu'on lit sa facture.
 */
@Composable
internal fun UsageCounter(rows: List<UsageRow>) {
    if (rows.isEmpty()) return

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Text(text = stringResource(R.string.ai_usage_title), style = MaterialTheme.typography.titleMedium)

            rows.forEach { row ->
                Text(
                    text = pluralStringResource(
                        R.plurals.ai_usage_line,
                        row.calls,
                        row.provider.displayName,
                        row.model,
                        row.calls,
                        row.tokens,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            Text(
                text = stringResource(R.string.ai_usage_billing),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
