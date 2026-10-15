package app.hexavore.feature.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MailOutline
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.hexavore.core.designsystem.component.AnnounceKeyAcceptance
import app.hexavore.core.designsystem.component.NoticeDot
import app.hexavore.core.designsystem.component.ScreenTopBar
import app.hexavore.core.designsystem.component.SparkleGlyph
import app.hexavore.core.designsystem.theme.Spacing
import app.hexavore.domain.ai.AcceptedKey
import app.hexavore.domain.food.CONTRIBUTION_OFFERED
import app.hexavore.domain.notice.Notice

/**
 * Le hub de réglages, **né avec sa deuxième section**.
 *
 * [D59][decisions] l'avait remis à plus tard pour une raison précise : un écran de
 * transit qui ne désigne qu'une seule destination est un écran de trop, et quatre
 * entrées qui n'ouvrent rien ne sont pas une avance. La deuxième section arrive, donc
 * lui aussi — c'est exactement l'échéance qui avait été écrite.
 *
 * **Sauvegarde arrive avec son écran**, et pas avant : c'est la même règle qui l'avait
 * tenue dehors. **Apparence arrive de la même façon**, le jour où le thème est devenu
 * réglable. **À propos arrive en dernier** ([D161][decisions]) : elle n'ouvrait rien
 * tant qu'on la pensait faite d'une version et d'un lien de dépôt ; elle porte
 * désormais ce que la publication exige — la politique de confidentialité, les
 * attributions des deux bases et les licences.
 *
 * [parcours]: docs/02-parcours-et-ecrans.md
 * [decisions]: docs/11-decisions.md
 */
/**
 * Le hub, branché sur les pastilles.
 *
 * Il emprunte le modèle de l'écran des notifications plutôt que d'en avoir un à lui :
 * c'est le même flux, lu au lieu d'être écrit, et un second modèle aurait fait deux
 * abonnements pour une seule question.
 */
@Composable
internal fun SettingsHubRoute(
    sections: SettingsSections,
    onClose: () -> Unit,
    viewModel: NoticeSettingsViewModel = hiltViewModel(),
    acceptance: KeyAcceptanceViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val accepted by acceptance.accepted.collectAsStateWithLifecycle()

    SettingsHubScreen(
        sections = sections,
        onClose = onClose,
        aiFlagged = state.active.any { it == Notice.AI_NOT_CONFIGURED || it == Notice.AI_KEY_REJECTED },
        keyAccepted = accepted,
        onKeyAcceptedShown = acceptance::onShown,
    )
}

/**
 * Les sept portes du hub, réunies.
 *
 * **Nées quand le seuil de paramètres a mordu**, à la septième section, et le
 * regroupement suit ce que les choses sont : sept lambdas qui font toutes la même chose,
 * ouvrir un écran. C'est la forme de `HomeActions` et de `EntryActions`, appliquée
 * là où le même symptôme est apparu.
 */
@Immutable
internal data class SettingsSections(
    val onOpenProfile: () -> Unit,
    val onOpenAi: () -> Unit,
    val onOpenContribution: () -> Unit,
    val onOpenBackup: () -> Unit,
    val onOpenPhotos: () -> Unit,
    val onOpenNotices: () -> Unit,
    val onOpenAppearance: () -> Unit,
    val onOpenFavorites: () -> Unit,
    val onOpenContact: () -> Unit,
    val onOpenAbout: () -> Unit,
)

@Composable
internal fun SettingsHubScreen(
    sections: SettingsSections,
    onClose: () -> Unit,
    /** La section d'IA porte une pastille : aucune clé ne sert, ou la dernière a été refusée. */
    aiFlagged: Boolean = false,
    /**
     * Non nul quand une clé vient d'être acceptée sur l'écran d'IA, qui s'est refermé.
     *
     * **La confirmation se lit où l'on revient.** Une boîte posée sur l'écran d'IA
     * aurait demandé un appui de plus pour une bonne nouvelle, et l'écran se referme de
     * toute façon : la nouvelle le suit plutôt que de le retenir. Le hub n'est qu'un des
     * deux endroits où l'on revient — l'accueil est l'autre, et le plus fréquent
     * ([D157][decisions]).
     *
     * [decisions]: docs/11-decisions.md
     */
    keyAccepted: AcceptedKey? = null,
    onKeyAcceptedShown: () -> Unit = {},
) {
    val snackbarHostState = remember { SnackbarHostState() }

    AnnounceKeyAcceptance(accepted = keyAccepted, host = snackbarHostState, onShown = onKeyAcceptedShown)

    Scaffold(
        topBar = {
            ScreenTopBar(
                title = stringResource(R.string.settings_title),
                onClose = onClose,
                closeLabel = stringResource(R.string.settings_close),
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                // **La liste defile**, et il a fallu une neuvieme entree pour s'en
                // apercevoir : les sept premieres tenaient a l'ecran, si bien qu'une
                // colonne sans defilement se comportait comme une colonne qui defile.
                // « Contact » tombait dessous, et rien ne permettait d'y arriver (D151).
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.screenMargin),
            verticalArrangement = Arrangement.spacedBy(Spacing.betweenCards),
        ) {
            HubSections(sections = sections, aiFlagged = aiFlagged)
        }
    }
}

/**
 * Les entrées, dans l'ordre où l'on va les chercher.
 *
 * Sortie du corps de l'écran quand le seuil de longueur a mordu, et le découpage suit
 * ce que les choses sont : une liste de destinations, là où ce qui l'entoure est un
 * écran — une barre, une colonne, des marges.
 */
@Composable
private fun HubSections(sections: SettingsSections, aiFlagged: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.betweenCards)) {
        SectionCard(
            titleRes = R.string.settings_profile_title,
            icon = Icons.Filled.Person,
            subtitleRes = R.string.settings_profile_subtitle,
            onClick = sections.onOpenProfile,
        )
        // Les deux etincelles, et non l'etoile : l'etoile designe les favoris dans
        // toute l'application, et la section juste en dessous est la leur (D148).
        SectionCard(
            titleRes = R.string.settings_ai_title,
            // Muet, comme les autres icones de cette liste : le titre est juste a
            // cote, et le repeter ferait deux arrets pour une seule entree.
            glyph = { SparkleGlyph(contentDescription = "") },
            subtitleRes = R.string.settings_ai_subtitle,
            onClick = sections.onOpenAi,
            flagged = aiFlagged,
        )
        SectionCard(
            titleRes = R.string.settings_favorites_title,
            icon = Icons.Filled.Star,
            subtitleRes = R.string.settings_favorites_subtitle,
            onClick = sections.onOpenFavorites,
        )
        ContributionSection(onClick = sections.onOpenContribution)
        SectionCard(
            titleRes = R.string.settings_backup_title,
            icon = Icons.Filled.Lock,
            subtitleRes = R.string.settings_backup_subtitle,
            onClick = sections.onOpenBackup,
        )
        // Juste apres la sauvegarde : les deux parlent de ce que l'application
        // garde sur le telephone, et de ce qu'on peut en effacer.
        SectionCard(
            titleRes = R.string.settings_photos_title,
            icon = Icons.Filled.Face,
            subtitleRes = R.string.settings_photos_subtitle,
            onClick = sections.onOpenPhotos,
        )
        SectionCard(
            titleRes = R.string.settings_notices_title,
            icon = Icons.Filled.Notifications,
            subtitleRes = R.string.settings_notices_subtitle,
            onClick = sections.onOpenNotices,
        )
        SectionCard(
            titleRes = R.string.settings_appearance_title,
            icon = Icons.Filled.Settings,
            subtitleRes = R.string.settings_appearance_subtitle,
            onClick = sections.onOpenAppearance,
        )
        ProjectSections(sections)
    }
}

/**
 * Les deux entrées qui ne règlent rien, en dernier ([D161][decisions]).
 *
 * On ne va pas les chercher pour changer quelque chose : on y va quand on a quelque
 * chose à dire au projet, ou à apprendre de lui. Sorties de [HubSections] quand « À
 * propos » l'a fait passer au seuil de longueur, et le découpage suit, là encore, ce que
 * les entrées sont.
 *
 * [decisions]: docs/11-decisions.md
 */
@Composable
private fun ProjectSections(sections: SettingsSections) {
    SectionCard(
        titleRes = R.string.settings_contact_title,
        icon = Icons.Filled.MailOutline,
        subtitleRes = R.string.settings_contact_subtitle,
        onClick = sections.onOpenContact,
    )
    // Apres Contact : c'est la page qu'on ouvre le moins souvent, et la seule qu'on
    // vienne y chercher -- la politique de confidentialite, une licence.
    SectionCard(
        titleRes = R.string.settings_about_title,
        icon = Icons.Filled.Info,
        subtitleRes = R.string.settings_about_subtitle,
        onClick = sections.onOpenAbout,
    )
}

/**
 * Open Food Facts, **tenu en réserve** ([D158][decisions]).
 *
 * Aucun envoi réel n'a encore atteint la vraie base, et c'est la seule écriture publique
 * de l'application : la section se tait tant que la contribution n'a pas été éprouvée.
 * L'écran existe toujours, et la section revient avec le drapeau.
 *
 * [decisions]: docs/11-decisions.md
 */
@Composable
private fun ContributionSection(onClick: () -> Unit) {
    if (!CONTRIBUTION_OFFERED) return

    SectionCard(
        titleRes = R.string.settings_contribution_title,
        icon = Icons.Filled.Share,
        subtitleRes = R.string.settings_contribution_subtitle,
        onClick = onClick,
    )
}

/**
 * Une section : son icône, son nom, et ce qu'elle règle.
 *
 * **L'icône se cherche, le nom se lit.** Sept cartes de texte se parcourent en lisant
 * les sept titres ; sept icônes se parcourent d'un coup d'œil, et c'est à ce prix
 * qu'une liste de réglages cesse d'être une liste ([D137][decisions]).
 *
 * **Elle est muette pour un lecteur d'écran** : le titre est juste à côté, et une
 * icône qui répéterait « profil » ferait deux arrêts pour une seule entrée.
 *
 * [decisions]: docs/11-decisions.md
 */
@Composable
private fun SectionCard(
    @StringRes titleRes: Int,
    @StringRes subtitleRes: Int,
    onClick: () -> Unit,
    icon: ImageVector? = null,
    /**
     * Le signe, quand `material-icons-core` n'en a pas.
     *
     * Deux formes pour une seule place : la plupart des sections tiennent avec un
     * vecteur de Material, et celles qui n'y sont pas — les étincelles de l'IA —
     * apportent leur tracé. Deux cartes auraient divergé au premier changement de marge.
     */
    glyph: (@Composable () -> Unit)? = null,
    flagged: Boolean = false,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(Spacing.cardPadding),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.primary) {
                    if (icon != null) {
                        Icon(imageVector = icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    } else {
                        glyph?.invoke()
                    }
                }
                Text(text = stringResource(titleRes), style = MaterialTheme.typography.titleMedium)
                if (flagged) {
                    NoticeDot(
                        label = stringResource(R.string.settings_section_flagged),
                        modifier = Modifier.padding(start = Spacing.sm),
                    )
                }
            }
            Text(
                text = stringResource(subtitleRes),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
