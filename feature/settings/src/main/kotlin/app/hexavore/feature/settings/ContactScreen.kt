package app.hexavore.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.hexavore.core.designsystem.component.ScreenTopBar
import app.hexavore.core.designsystem.theme.Spacing
import app.hexavore.domain.opening.STORE_LISTING
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
 * ### Les quatre, et pas une de plus
 *
 * Le dépôt pour **lire le code** et ouvrir un ticket, le site pour **savoir ce que
 * c'est**, l'adresse pour **écrire**. Chacune s'adresse à quelqu'un de différent, et
 * aucune ne remplace les deux autres. La fiche du Play Store s'y ajoute pour **noter
 * l'application** quand on le veut, et non seulement quand l'ouverture le propose
 * ([D179][decisions]) : c'est la même adresse que la demande d'avis.
 *
 * **L'adresse est celle du signalement**, lue depuis le domaine et non recopiée : deux
 * adresses écrites à deux endroits finissent par différer, et c'est la seconde que
 * personne ne relève.
 *
 * ### Ce qui ouvre quoi
 *
 * `LocalUriHandler` pour les quatre, y compris le `mailto:` — c'est le même geste pour
 * l'utilisateur, et le système sait seul quelle application s'en charge. Un `Intent`
 * construit à la main demanderait de savoir ce qui est installé. Quand rien ne s'en
 * charge, [rememberLinkOpener] le dit au lieu de faire tomber l'écran ([D162][decisions]).
 *
 * [decisions]: docs/11-decisions.md
 */
@Composable
internal fun ContactRoute(onClose: () -> Unit) {
    ContactScreen(onClose = onClose)
}

@Composable
internal fun ContactScreen(onClose: () -> Unit) {
    val barre = remember { SnackbarHostState() }
    val ouvrir = rememberLinkOpener(barre)

    Scaffold(
        topBar = {
            ScreenTopBar(
                title = stringResource(R.string.settings_contact_title),
                onClose = onClose,
                closeLabel = stringResource(R.string.settings_close),
            )
        },
        snackbarHost = { SnackbarHost(barre) },
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
            LinkCard(stringResource(R.string.settings_contact_site), SITE_URL) { ouvrir(SITE_URL) }
            LinkCard(stringResource(R.string.settings_contact_github), GITHUB_URL) { ouvrir(GITHUB_URL) }
            LinkCard(stringResource(R.string.settings_contact_mail), REPORT_ADDRESS) {
                ouvrir("mailto:$REPORT_ADDRESS")
            }
            LinkCard(stringResource(R.string.settings_contact_rate), STORE_LISTING) { ouvrir(STORE_LISTING) }
        }
    }
}

internal const val SITE_URL = "https://hexavore.app"
internal const val GITHUB_URL = "https://github.com/hexavore-app/hexavore"
