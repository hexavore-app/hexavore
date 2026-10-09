package app.hexavore.feature.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import app.hexavore.core.designsystem.component.ScreenTopBar
import app.hexavore.core.designsystem.theme.Spacing
import app.hexavore.domain.report.REPORT_ADDRESS

/**
 * Où écrire, et où regarder ce que l'application fait.
 *
 * ### Pourquoi un écran plutôt qu'une mention
 *
 * Il n'y avait aucun chemin vers les auteurs. Le seul courriel que l'application sache
 * ouvrir part d'un bouton de signalement, qui n'existe que sur un plat proposé par un
 * modèle : qui voulait écrire pour autre chose n'avait nulle part où aller
 * ([D148][decisions]).
 *
 * ### Les trois, et pas une de plus
 *
 * Le dépôt pour **lire le code** et ouvrir un ticket, le site pour **savoir ce que
 * c'est**, l'adresse pour **écrire**. Chacune s'adresse à quelqu'un de différent, et
 * aucune ne remplace les deux autres.
 *
 * **L'adresse est celle du signalement**, lue depuis le domaine et non recopiée : deux
 * adresses écrites à deux endroits finissent par différer, et c'est la seconde que
 * personne ne relève.
 *
 * ### Ce qui ouvre quoi
 *
 * [LocalUriHandler] pour les trois, y compris le `mailto:` — c'est le même geste pour
 * l'utilisateur, et le système sait seul quelle application s'en charge. Un `Intent`
 * construit à la main demanderait de savoir ce qui est installé.
 *
 * [decisions]: docs/11-decisions.md
 */
@Composable
internal fun ContactRoute(onClose: () -> Unit) {
    ContactScreen(onClose = onClose)
}

@Composable
internal fun ContactScreen(onClose: () -> Unit) {
    val liens = LocalUriHandler.current

    Scaffold(
        topBar = {
            ScreenTopBar(
                title = stringResource(R.string.settings_contact_title),
                onClose = onClose,
                closeLabel = stringResource(R.string.settings_close),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.screenMargin),
            verticalArrangement = Arrangement.spacedBy(Spacing.betweenCards),
        ) {
            Text(
                text = stringResource(R.string.settings_contact_intro),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            ContactCard(R.string.settings_contact_site, SITE_URL) { liens.openUri(SITE_URL) }
            ContactCard(R.string.settings_contact_github, GITHUB_URL) { liens.openUri(GITHUB_URL) }
            ContactCard(R.string.settings_contact_mail, REPORT_ADDRESS) { liens.openUri("mailto:$REPORT_ADDRESS") }
        }
    }
}

/**
 * Une destination : ce que c'est, et l'adresse elle-même.
 *
 * **L'adresse est écrite en toutes lettres** et non cachée derrière un libellé. Elle
 * sert deux fois : elle dit où l'on va avant d'y aller — ce qui compte quand on s'apprête
 * à quitter l'application —, et elle reste lisible pour qui voudra la recopier ailleurs.
 */
@Composable
private fun ContactCard(@StringRes titleRes: Int, adresse: String, onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(Spacing.cardPadding),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            Text(text = stringResource(titleRes), style = MaterialTheme.typography.titleMedium)
            Text(
                text = adresse,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

private const val SITE_URL = "https://hexavore.app"
private const val GITHUB_URL = "https://github.com/hexavore-app/hexavore"
