package app.hexavore.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import app.hexavore.core.designsystem.component.ScreenTopBar
import app.hexavore.core.designsystem.theme.Spacing
import app.hexavore.core.designsystem.R as DesignStrings

/**
 * Ce qu'est l'application, et ce qu'elle doit à d'autres ([D161][decisions]).
 *
 * ### Pourquoi l'écran existe enfin
 *
 * Il était annoncé depuis la conception, et différé « parce qu'il n'ouvrirait rien ».
 * Il ouvre au moins quatre choses que la publication exige, et qu'aucun autre écran ne
 * portait :
 *
 * - **la politique de confidentialité**, que Google Play veut atteignable depuis
 *   l'application elle-même, et pas seulement depuis la fiche du Store ;
 * - **l'avertissement médical**, accepté une fois à l'onboarding puis introuvable ;
 * - **la provenance des données** : la Licence Ouverte de l'Anses demande de nommer la
 *   source, l'ODbL d'Open Food Facts de dire que la base en contient ;
 * - **les licences** de ce que l'APK embarque, dont une MIT qui demande que son texte
 *   accompagne chaque copie.
 *
 * ### Ce qui n'y est pas
 *
 * Aucun texte de licence long : la GPL, l'OFL et l'Apache 2.0 se lisent à leur adresse,
 * écrite en toutes lettres. La MIT fait exception parce qu'elle tient en trois
 * paragraphes et qu'elle exige d'être reproduite.
 *
 * [decisions]: docs/11-decisions.md
 */
@Composable
internal fun AboutRoute(onClose: () -> Unit) {
    AboutScreen(version = BuildConfig.VERSION_LABEL, onClose = onClose)
}

@Composable
internal fun AboutScreen(version: String, onClose: () -> Unit) {
    val barre = remember { SnackbarHostState() }
    val ouvrir = rememberLinkOpener(barre)
    val politique = stringResource(R.string.about_privacy_url)

    Scaffold(
        topBar = {
            ScreenTopBar(
                title = stringResource(R.string.settings_about_title),
                onClose = onClose,
                closeLabel = stringResource(R.string.about_close),
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
            Identity(version)
            // La premiere carte, et non la derniere : c'est la seule que la publication
            // exige de trouver ici, et quelqu'un qui ouvre cet ecran pour elle ne doit pas
            // avoir a la chercher sous les licences.
            LinkCard(
                title = stringResource(R.string.about_privacy_title),
                address = politique,
                detail = stringResource(R.string.about_privacy_body),
            ) { ouvrir(politique) }
            Disclaimer()
            Sources(onOpen = ouvrir)
            Licences(onOpen = ouvrir)
        }
    }
}

/** Le nom, la version, et ce que l'application promet en une ligne. */
@Composable
private fun Identity(version: String) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Text(
            text = stringResource(R.string.about_app_name),
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            text = stringResource(R.string.about_version, version),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = stringResource(R.string.about_tagline),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * L'avertissement médical, **le même** que celui de l'onboarding.
 *
 * Il vit dans le design system pour cette raison : accepté une fois au premier écran,
 * il doit pouvoir se relire mot pour mot ensuite.
 */
@Composable
private fun Disclaimer() {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(Spacing.cardPadding),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            // Muette : la phrase juste a cote dit tout.
            Icon(imageVector = Icons.Filled.Info, contentDescription = null)
            Text(
                text = stringResource(DesignStrings.string.ds_medical_disclaimer),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

/**
 * D'où viennent les valeurs nutritionnelles.
 *
 * **Les deux formulations sont celles que les licences demandent**, et non un
 * remerciement : la Licence Ouverte veut la source et sa date — l'édition 2025 —,
 * l'ODbL veut qu'il soit dit que l'application contient des informations de la base.
 */
@Composable
private fun Sources(onOpen: (String) -> Unit) {
    Intertitle(stringResource(R.string.about_sources_title))
    LinkCard(
        title = stringResource(R.string.about_ciqual_title),
        address = CIQUAL_URL,
        detail = stringResource(R.string.about_ciqual_body),
    ) { onOpen(CIQUAL_URL) }
    LinkCard(
        title = stringResource(R.string.about_off_title),
        address = OFF_URL,
        detail = stringResource(R.string.about_off_body),
    ) { onOpen(OFF_URL) }
}

/**
 * Sous quelles licences tout cela est publié.
 *
 * **La liste suit ce que l'APK embarque**, pas ce que le dépôt compile : les outils de
 * test et de construction n'y figurent pas. Le jour où une bibliothèque entre ou sort,
 * c'est ici qu'elle se déclare.
 */
@Composable
private fun Licences(onOpen: (String) -> Unit) {
    val context = LocalContext.current
    val mit = remember(context) {
        context.resources.openRawResource(R.raw.licence_calendar_mit).bufferedReader().use { it.readText() }
    }

    Intertitle(stringResource(R.string.about_licences_title))
    LinkCard(
        title = stringResource(R.string.about_app_name),
        address = GPL_URL,
        detail = stringResource(R.string.about_licence_app_body),
    ) { onOpen(GPL_URL) }
    LinkCard(
        title = stringResource(R.string.about_licence_inter_title),
        address = OFL_URL,
        detail = stringResource(R.string.about_licence_inter_body),
    ) { onOpen(OFL_URL) }
    LinkCard(
        title = stringResource(R.string.about_licence_apache_title),
        address = APACHE_URL,
        detail = stringResource(R.string.about_licence_apache_body),
    ) { onOpen(APACHE_URL) }
    LinkCard(
        title = stringResource(R.string.about_licence_desugar_title),
        address = DESUGAR_URL,
        detail = stringResource(R.string.about_licence_desugar_body),
    ) { onOpen(DESUGAR_URL) }
    LinkCard(
        title = stringResource(R.string.about_licence_calendar_title),
        address = CALENDAR_URL,
        detail = stringResource(R.string.about_licence_calendar_body),
    ) { onOpen(CALENDAR_URL) }
    Text(
        text = mit,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/**
 * Un intertitre, annoncé comme tel au lecteur d'écran.
 *
 * Plus petit que le `SectionTitle` du profil, qui titre un écran entier : ici, il sépare
 * deux groupes de cartes dans la même page.
 */
@Composable
private fun Intertitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(top = Spacing.sm).semantics { heading() },
    )
}

private const val CIQUAL_URL = "https://ciqual.anses.fr"
private const val OFF_URL = "https://world.openfoodfacts.org"
private const val GPL_URL = "$GITHUB_URL/blob/main/LICENSE"
private const val OFL_URL = "https://openfontlicense.org"
private const val APACHE_URL = "https://www.apache.org/licenses/LICENSE-2.0"
private const val DESUGAR_URL = "https://github.com/google/desugar_jdk_libs/blob/master/LICENSE"
private const val CALENDAR_URL = "https://github.com/kizitonwose/calendar/blob/main/LICENSE.md"
