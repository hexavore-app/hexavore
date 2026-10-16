package app.hexavore.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import app.hexavore.core.designsystem.theme.Spacing
import kotlinx.coroutines.launch

/**
 * Une destination hors de l'application : ce que c'est, éventuellement ce qu'on y
 * trouve, et l'adresse elle-même.
 *
 * **L'adresse est écrite en toutes lettres** et non cachée derrière un libellé. Elle
 * sert deux fois : elle dit où l'on va avant d'y aller — ce qui compte quand on
 * s'apprête à quitter l'application —, et elle reste lisible pour qui voudra la
 * recopier ailleurs.
 *
 * **Sortie de l'écran Contact** quand « À propos » en a eu besoin à son tour
 * ([D161][decisions]) : deux cartes qui disent la même chose de la même façon, écrites
 * deux fois, auraient divergé à la première marge retouchée.
 *
 * [decisions]: docs/11-decisions.md
 */
@Composable
internal fun LinkCard(title: String, address: String, detail: String? = null, onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(Spacing.cardPadding),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            if (detail != null) {
                Text(
                    text = detail,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = address,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

/**
 * Ouvre une adresse, et dit quand rien ne sait l'ouvrir ([D162][decisions]).
 *
 * `openUri` lève une exception quand aucune application ne prend l'adresse : un
 * téléphone sans navigateur, ou sans messagerie pour un `mailto:`. L'appui sur une carte
 * faisait alors tomber l'application. L'échec se dit maintenant dans une barre, qui
 * renvoie à l'adresse : elle est écrite sur la carte, en toutes lettres, justement pour
 * qu'on puisse la recopier.
 *
 * [decisions]: docs/11-decisions.md
 */
@Composable
internal fun rememberLinkOpener(host: SnackbarHostState): (String) -> Unit {
    val liens = LocalUriHandler.current
    val scope = rememberCoroutineScope()
    val message = stringResource(R.string.settings_link_unavailable)

    return remember(liens, scope, host, message) {
        { adresse ->
            runCatching { liens.openUri(adresse) }.onFailure {
                scope.launch {
                    // Une seule barre a la fois : trois appuis n'empilent pas trois
                    // fois la meme phrase.
                    host.currentSnackbarData?.dismiss()
                    host.showSnackbar(message)
                }
            }
        }
    }
}
