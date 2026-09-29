package app.hexavore.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.hexavore.core.designsystem.component.CameraGlyph
import app.hexavore.core.designsystem.component.DraftTextField
import app.hexavore.core.designsystem.component.aiErrorMessage
import app.hexavore.core.designsystem.theme.Radius
import app.hexavore.core.designsystem.theme.Spacing

/**
 * La barre d'ajout, en bas et sur toute la largeur.
 *
 * **Elle remplace la colonne de boutons flottants** ([D131][decisions]). Quatre boutons
 * empilés dans un coin demandaient de viser, puis de reconnaître un glyphe, puis
 * d'ouvrir un écran pour *commencer* à saisir : le geste le plus fréquent de
 * l'application coûtait trois décisions avant la première lettre. Ici, ce geste est le
 * champ lui-même — on écrit ce qu'on a mangé, et c'est parti.
 *
 * **Trois éléments, et leur ordre dit leur fréquence** : la phrase, l'appareil photo,
 * puis le reste sous un « + ». Les trois modes qui restent — scanner, chercher,
 * rejouer un favori — ne sont pas cachés : ils sont à un geste, et c'est le même geste
 * pour les trois.
 *
 * **Elle ne s'efface plus devant la bulle des sources**, contrairement à la colonne
 * qu'elle remplace ([D122][decisions]) : ancrée en bas, elle n'est plus posée sur ce
 * que la bulle a à dire — c'est le `Scaffold` qui lui réserve sa place, et la page
 * s'arrête au-dessus.
 *
 * [decisions]: docs/11-decisions.md
 */
@Composable
internal fun QuickEntryBar(actions: HomeActions, aiConfigured: Boolean, entry: QuickEntry) {
    val state = entry.state
    var menuOpen by rememberSaveable { mutableStateOf(false) }
    var explaining by rememberSaveable { mutableStateOf(false) }
    var text by rememberSaveable { mutableStateOf("") }

    // Le champ garde son texte lui-meme (D45) : le vider demande donc de le
    // reconstruire, et c'est ce numero qui le fait. Il ne bouge qu'a un envoi reussi --
    // un echec laisse la phrase, parce que la retaper serait la punition d'une panne
    // dont l'utilisateur n'est pas l'auteur.
    var round by rememberSaveable { mutableIntStateOf(0) }
    val keyboard = LocalSoftwareKeyboardController.current

    LaunchedEffect(state.proposed) {
        if (!state.proposed) return@LaunchedEffect
        text = ""
        round++
    }

    EntryLayers(
        actions = actions,
        menuOpen = menuOpen,
        explaining = explaining,
        onCloseMenu = { menuOpen = false },
        onStopExplaining = { explaining = false },
    )
    val onMore = { menuOpen = true }

    BarSurface {
        Analysis(state, entry.onCancel)
        Failure(state, entry.onDismissError, actions.onAddDish)

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FieldSlot(
                aiConfigured = aiConfigured,
                round = round,
                text = text,
                analysing = state.analysing,
                onValueChange = { text = it },
                onSend = {
                    keyboard?.hide()
                    entry.onSend(text)
                },
                onExplain = { explaining = true },
                modifier = Modifier.weight(1f),
            )
            BarAction(
                onClick = { if (aiConfigured) actions.onShoot() else explaining = true },
                available = aiConfigured,
            ) {
                CameraGlyph(contentDescription = stringResource(R.string.home_shoot))
            }
            BarAction(onClick = onMore, available = true) {
                Icon(imageVector = Icons.Filled.Add, contentDescription = stringResource(R.string.home_more_ways))
            }
        }
    }
}

/**
 * Le contenant de la barre : sa forme, sa teinte, et **ce qu'elle laisse au système**.
 *
 * **Elle ne connaît pas le clavier**, et c'est ce qui la remet d'aplomb : c'est l'écran
 * qui remonte, d'un seul `imePadding` posé sur le `Scaffold`. La barre ne garde que la
 * marge de la barre de navigation, **retranchée de celle du clavier** : quand celui-ci
 * est ouvert, la barre de navigation est dessous, et lui réserver de la place y
 * ajouterait une bande vide ([D135][decisions]).
 *
 * [decisions]: docs/11-decisions.md
 */
@Composable
private fun BarSurface(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        // Carree en bas, adoucie en haut : les deux coins du bas tombent hors de
        // l'ecran, et les arrondir n'y decouperait que deux triangles de fond.
        shape = RoundedCornerShape(topStart = Radius.card, topEnd = Radius.card),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                // **Les deux ensemble, et non l'une puis l'autre.** Empilees, elles
                // s'additionnent : la barre montait de la hauteur du clavier **plus**
                // celle de la barre de navigation, qui est pourtant dessous. `union`
                // prend la plus grande des deux, ce qui est exactement ce qu'on veut a
                // chaque instant -- le clavier ouvert, ou rien (D135).
                .windowInsetsPadding(WindowInsets.navigationBars.exclude(WindowInsets.ime))
                .padding(horizontal = Spacing.md, vertical = Spacing.xs),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            content = content,
        )
    }
}

/**
 * Ce qui se pose **par-dessus** la barre : la feuille du « + », et l'explication.
 *
 * Sorties du corps de la barre quand le seuil de longueur a mordu, et le découpage suit
 * ce que les choses sont : deux couches qui recouvrent l'écran, là où le reste de la
 * barre est une ligne de trois éléments.
 *
 * **L'explication referme avant d'emmener** : quelqu'un qui revient des réglages ne
 * doit pas retrouver la boîte qui l'y a envoyé.
 */
@Composable
private fun EntryLayers(
    actions: HomeActions,
    menuOpen: Boolean,
    explaining: Boolean,
    onCloseMenu: () -> Unit,
    onStopExplaining: () -> Unit,
) {
    if (menuOpen) AddMenu(actions = actions, onDismiss = onCloseMenu)
    if (explaining) {
        AiUnavailableDialog(
            onConfigure = {
                onStopExplaining()
                actions.onConfigureAi()
            },
            onDismiss = onStopExplaining,
        )
    }
}

/**
 * La place du champ : celui où l'on écrit, ou celui qui explique.
 *
 * **Le champ se reconstruit à chaque envoi réussi**, et c'est ainsi qu'il se vide : il
 * garde son texte lui-même ([D45][decisions]), donc personne ne peut le lui reprendre
 * de l'extérieur sans lui voler son curseur au passage.
 *
 * [decisions]: docs/11-decisions.md
 */
@Composable
private fun FieldSlot(
    aiConfigured: Boolean,
    round: Int,
    text: String,
    analysing: Boolean,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    onExplain: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier) {
        if (aiConfigured) {
            key(round) {
                MealField(initial = text, analysing = analysing, onValueChange = onValueChange, onSend = onSend)
            }
        } else {
            LockedField(onClick = onExplain)
        }
    }
}

/**
 * Le champ, et **la touche qui envoie**.
 *
 * Le clavier porte « envoyer » plutôt que « suivant » : il n'y a pas de champ suivant,
 * et une touche qui n'emmène nulle part apprend à ne pas s'en servir. Le bouton dit la
 * même chose dans le champ, pour qui ne regarde pas son clavier.
 */
@Composable
private fun MealField(initial: String, analysing: Boolean, onValueChange: (String) -> Unit, onSend: () -> Unit) {
    var written by rememberSaveable { mutableStateOf(initial) }

    DraftTextField(
        initial = initial,
        onValueChange = {
            written = it
            onValueChange(it)
        },
        label = stringResource(R.string.home_describe_label),
        modifier = Modifier.fillMaxWidth(),
        // **Une ligne au repos, quatre au plus.** Une phrase de repas en fait souvent
        // deux ou trois, et un champ d'une seule ligne les faisait defiler
        // horizontalement : on ecrivait sans voir le debut de ce qu'on ecrivait. Il
        // grandit donc avec le texte, et s'arrete avant de manger l'ecran (D135).
        maxLines = TEXT_LINES,
        imeAction = ImeAction.Send,
        onImeAction = onSend,
        // Une barre, pas un formulaire : le libelle devient une invite qui s'efface a
        // la premiere lettre, et le champ descend a 48 dp (D135).
        compact = true,
        trailingIcon = {
            // Rien tant qu'il n'y a rien a envoyer : un bouton grise dans un champ vide
            // occupe la place et ne dit pas ce qui le reveillerait.
            if (written.isNotBlank() && !analysing) {
                IconButton(onClick = onSend) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = stringResource(R.string.home_send_description),
                    )
                }
            }
        },
    )
}

/**
 * Le champ **sans clé d'IA** : il a la forme d'un champ, et il explique.
 *
 * Un champ désactivé ne se touche pas, donc n'apprend rien ; celui-ci se touche et dit
 * ce qui manque — c'est ce que [docs/02][parcours] demande des modes d'IA sans clé :
 * visibles, grisés, et une explication courte à l'appui. Ce n'est pas un champ de saisie
 * déguisé : rien ne s'y tape, et le lecteur d'écran l'annonce comme indisponible plutôt
 * que comme un endroit où écrire.
 *
 * [parcours]: docs/02-parcours-et-ecrans.md
 */
@Composable
private fun LockedField(onClick: () -> Unit) {
    val unavailable = stringResource(R.string.home_analyse_unavailable)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = FieldHeight)
            .clip(RoundedCornerShape(Radius.field))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
            .semantics { stateDescription = unavailable }
            .padding(horizontal = Spacing.md, vertical = Spacing.md),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            text = stringResource(R.string.home_describe_label),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Un bouton de la barre : plein quand il sert, effacé quand il explique.
 *
 * Il reste tapable dans les deux cas, et seule sa teinte change : un bouton caché ne
 * s'apprend jamais ; un bouton inerte n'apprend rien non plus. C'est la règle que
 * [docs/02][parcours] pose pour les modes d'IA sans clé.
 *
 * [parcours]: docs/02-parcours-et-ecrans.md
 */
@Composable
private fun BarAction(onClick: () -> Unit, available: Boolean, content: @Composable () -> Unit) {
    FilledIconButton(
        onClick = onClick,
        modifier = Modifier.size(ActionSize),
        colors = IconButtonDefaults.filledIconButtonColors(
            containerColor = if (available) {
                MaterialTheme.colorScheme.secondaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
            contentColor = if (available) {
                MaterialTheme.colorScheme.onSecondaryContainer
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        ),
        content = { content() },
    )
}

/**
 * Ce qui se passe pendant qu'une phrase part, **sans quitter l'accueil**.
 *
 * L'attente se lit sur une ligne au-dessus du champ plutôt que sur un écran : c'est
 * tout l'intérêt de l'envoi direct — la journée reste visible, et on peut encore
 * annuler. L'annulation coupe vraiment, comme sur l'écran d'IA : une requête abandonnée
 * qu'on laisse courir se paie quand même.
 */
@Composable
private fun Analysis(state: QuickEntryUiState, onCancel: () -> Unit) {
    if (!state.analysing) return

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(modifier = Modifier.size(SpinnerSize))
        Text(
            text = stringResource(R.string.home_analysing),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onCancel) { Text(stringResource(R.string.home_analyse_cancel)) }
    }
}

/**
 * L'échec, et **la porte de sortie**.
 *
 * [docs/02][parcours] l'exige : un fournisseur en panne ne doit pas empêcher de noter
 * son repas. La phrase reste dans le champ — on réessaie sans retaper — et la recherche
 * est à un mot de là.
 *
 * [parcours]: docs/02-parcours-et-ecrans.md
 */
@Composable
private fun Failure(state: QuickEntryUiState, onDismiss: () -> Unit, onManual: () -> Unit) {
    val error = state.error ?: return

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = aiErrorMessage(error),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        TextButton(
            onClick = {
                onDismiss()
                onManual()
            },
        ) {
            Text(stringResource(R.string.home_analyse_manual))
        }
    }
}

/**
 * Assez pour un pouce, et **plus bas qu'un champ**.
 *
 * 48 dp et non 56 : c'est la cible tactile minimale du projet, et la barre au repos est
 * ce qu'on voit en permanence au-dessus du pouce. Les huit dp gagnes sur chaque bouton
 * et sur les marges rendent a la page une ligne de plat entiere (D135).
 */
private val ActionSize: Dp = 48.dp

/** La hauteur du champ compact : le faux champ a la même, pour que la barre ne saute pas. */
private val FieldHeight: Dp = 48.dp

/** Assez pour une phrase de repas, pas assez pour manger l'écran. */
private const val TEXT_LINES = 4

/** Plus petit que le standard : il partage une ligne avec du texte, pas un écran. */
private val SpinnerSize: Dp = 18.dp
