package app.hexavore.feature.home

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.hexavore.core.designsystem.component.AdjustmentCard
import app.hexavore.core.designsystem.component.BarcodeGlyph
import app.hexavore.core.designsystem.component.CameraGlyph
import app.hexavore.core.designsystem.component.MacroBar
import app.hexavore.core.designsystem.component.MacroHexagon
import app.hexavore.core.designsystem.component.MacroQuarter
import app.hexavore.core.designsystem.component.MacroUnit
import app.hexavore.core.designsystem.theme.Spacing
import app.hexavore.core.designsystem.theme.Timing
import app.hexavore.domain.diary.DaySummary
import app.hexavore.domain.diary.Dish
import app.hexavore.domain.goal.AdjustmentSuggestion
import app.hexavore.domain.goal.DailyGoal
import app.hexavore.domain.notice.Notice
import app.hexavore.domain.nutrition.Macro
import app.hexavore.domain.usecase.AdjustmentResponse
import kotlinx.coroutines.withTimeoutOrNull
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.roundToInt

/** L'accueil, branché sur le graphe d'injection. */
@Composable
fun HomeRoute(routes: HomeRoutes) {
    val viewModel: HomeViewModel = hiltViewModel()
    val calendarViewModel: CalendarViewModel = hiltViewModel()
    val calendar by calendarViewModel.uiState.collectAsStateWithLifecycle()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val pendingUndo by viewModel.pendingUndo.collectAsStateWithLifecycle()
    val nameTaken by viewModel.favoriteNameTaken.collectAsStateWithLifecycle()
    val aiConfigured by viewModel.aiConfigured.collectAsStateWithLifecycle()
    val suggestion by viewModel.suggestion.collectAsStateWithLifecycle()
    val day by viewModel.selectedDay.collectAsStateWithLifecycle()
    val noticeViewModel: NoticeViewModel = hiltViewModel()
    val notices by noticeViewModel.notices.collectAsStateWithLifecycle()

    HomeScreen(
        state = state,
        pendingUndo = pendingUndo,
        aiConfigured = aiConfigured,
        favoriteNameTaken = nameTaken,
        onDismissFavoriteError = viewModel::onDismissFavoriteError,
        suggestion = suggestion,
        onAdjustment = viewModel::onAdjustment,
        day = day,
        onBackToToday = { viewModel.onSelectDay(null) },
        notices = notices,
        calendar = { expanded, onExpandedChange ->
            CalendarPane(
                state = calendar,
                // Toucher une pastille ne navigue plus : elle change la date de
                // l'ecran, et le calendrier reste ou il est.
                onOpenDay = viewModel::onSelectDay,
                onVisibleMonth = calendarViewModel::onVisibleMonth,
                selected = day,
                // La pastille de la veille se pose sur la journee concernee, et non
                // sur une icone : c'est la qu'on la touche pour agir.
                flagYesterday = Notice.YESTERDAY_EMPTY in notices,
                expanded = expanded,
                onExpandedChange = onExpandedChange,
            )
        },
        actions = remember(viewModel, routes) {
            HomeActions(
                onAddDish = routes.onAddDish,
                onScan = routes.onScan,
                onDescribe = routes.onDescribe,
                onPhotograph = routes.onPhotograph,
                onEditDish = routes.onEditDish,
                onDeleteDish = viewModel::onDeleteDish,
                onDeleteEntry = viewModel::onDeleteEntry,
                onUndo = viewModel::onUndo,
                onUndoExpired = viewModel::onUndoExpired,
                onRetry = viewModel::retry,
                onSetUpGoal = routes.onSetUpGoal,
                onOpenSettings = routes.onOpenSettings,
                onConfigureAi = routes.onConfigureAi,
                onToggleFavorite = viewModel::onToggleFavorite,
                onOpenFavorites = routes.onOpenFavorites,
                onOpenWeight = routes.onOpenWeight,
            )
        },
    )
}

/**
 * L'accueil, sans état.
 *
 * Tout tient en un défilement vertical : le bloc de la journée, puis les plats.
 * Le bandeau calendrier arrive avec la tranche qui lui donne une destination — un
 * bandeau qui n'ouvre rien n'est pas une avance.
 *
 * @see docs/02-parcours-et-ecrans.md
 */
@Composable
fun HomeScreen(
    state: HomeUiState,
    pendingUndo: Dish?,
    actions: HomeActions,
    modifier: Modifier = Modifier,
    aiConfigured: Boolean = false,
    favoriteNameTaken: Boolean = false,
    onDismissFavoriteError: () -> Unit = {},
    /**
     * La correction que l'adaptation propose, ou `null` — ce qui est le cas normal.
     *
     * **En tête**, sous le calendrier et avant les compteurs ([docs/03][calculs]) :
     * c'est un changement de cap, et il se lit avant les chiffres qu'il change. Aucune
     * notification ne l'accompagne ; la carte suffit.
     *
     * [calculs]: docs/03-nutrition-calculs.md
     */
    suggestion: AdjustmentSuggestion? = null,
    onAdjustment: (AdjustmentResponse) -> Unit = {},
    /**
     * Le jour affiche, ou `null` pour aujourd'hui.
     *
     * L'ecran Journee a disparu : c'est cette date qui dit ce que montrent les six
     * compteurs et la liste des plats, et c'est sur elle que le bouton d'ajout ecrit.
     */
    day: LocalDate? = null,
    onBackToToday: () -> Unit = {},
    /** Ce qui merite une pastille. Vide dans les apercus, qui n'ont rien a signaler. */
    notices: Set<Notice> = emptySet(),
    /**
     * L'en-tete escamotable, ou rien.
     *
     * Un emplacement et non un composant : l'accueil n'a pas a connaitre le calendrier
     * ni son `ViewModel`, et les apercus se composent sans lui.
     *
     * **Il recoit son etat de repli**, parce que c'est l'ecran qui l'a. Le defilement
     * de la page arrive ici, et c'est lui qui doit replier ce qui est deploye : l'etat
     * vit la ou le geste arrive, et non dans ce qu'il replie.
     */
    calendar: @Composable (expanded: Boolean, onExpandedChange: (Boolean) -> Unit) -> Unit = { _, _ -> },
) {
    var calendarExpanded by rememberSaveable { mutableStateOf(false) }

    // Le retour du systeme ramene a aujourd'hui plutot que de quitter l'application :
    // depuis que l'ecran Journee a disparu, se promener dans l'historique n'empile
    // plus rien, et le geste n'aurait sinon aucune cible.
    BackHandler(enabled = day != null) { onBackToToday() }

    // La regle est dans `collapsingDelta`, ou elle se laisse eprouver. Ce qui reste
    // ici est le seul effet : ce qu'on consomme, on l'a referme.
    //
    // **La connexion n'est posee que sur le contenu**, jamais sur le calendrier :
    // `onPreScroll` va du parent vers l'enfant, donc une connexion englobant le mois
    // deplie le refermerait avant qu'il ait pu defiler.
    val collapseOnScroll = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset =
                collapsingDelta(calendarExpanded, available).also { if (it != Offset.Zero) calendarExpanded = false }
        }
    }
    val snackbarHostState = remember { SnackbarHostState() }
    val deleted = stringResource(R.string.home_entry_deleted)
    val undo = stringResource(R.string.home_entry_undo)

    // La barre reste affichee cinq secondes, ni quatre ni dix : SnackbarDuration
    // n'offre que ces deux-la, donc la fenetre est tenue par le delai et la barre
    // par un affichage indefini. Voir Timing dans :core:designsystem.
    LaunchedEffect(pendingUndo) {
        if (pendingUndo == null) return@LaunchedEffect
        val result = withTimeoutOrNull(Timing.UNDO_WINDOW_MILLIS) {
            snackbarHostState.showSnackbar(
                message = deleted,
                actionLabel = undo,
                duration = SnackbarDuration.Indefinite,
            )
        }
        if (result == SnackbarResult.ActionPerformed) actions.onUndo() else actions.onUndoExpired()
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = { DayActions(actions, aiConfigured) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = Spacing.screenMargin),
            verticalArrangement = Arrangement.spacedBy(Spacing.xl),
        ) {
            // Le titre et le calendrier ne defilent pas : docs/02 les veut fixes en
            // haut, et c'est aussi ce qui permet au mois deplie de defiler pour son
            // propre compte -- il n'est plus sous la connexion qui replie.
            DayHeader(actions, day, onBackToToday, notices)
            calendar(calendarExpanded) { calendarExpanded = it }

            DayScroll(collapseOnScroll) {
                suggestion?.let {
                    AdjustmentCard(
                        suggestion = it,
                        onAccept = { onAdjustment(AdjustmentResponse.ACCEPT) },
                        onIgnore = { onAdjustment(AdjustmentResponse.IGNORE) },
                        onStop = { onAdjustment(AdjustmentResponse.STOP) },
                    )
                }

                when (state) {
                    HomeUiState.Loading -> Unit
                    is HomeUiState.Content -> DayContent(
                        summary = state.summary,
                        actions = actions,
                        favoriteNameTaken = favoriteNameTaken,
                        onDismissFavoriteError = onDismissFavoriteError,
                    )

                    HomeUiState.Error -> UnreadableDay(actions.onRetry)
                }
            }
        }
    }
}

/**
 * Ce qui défile sous le calendrier, et rien d'autre.
 *
 * **La connexion de repli est posée ici**, pas sur la page entière. `onPreScroll` va
 * du parent vers l'enfant : une connexion qui englobait le calendrier voyait le geste
 * avant lui et le refermait, alors que défiler *dans* le mois déplié doit le faire
 * défiler. La portée du geste est une affaire de disposition, pas de condition.
 */
@Composable
private fun ColumnScope.DayScroll(
    collapseOnScroll: NestedScrollConnection,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .weight(1f)
            .nestedScroll(collapseOnScroll)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(Spacing.xl),
        content = content,
    )
}

/**
 * Les boutons flottants, empilés.
 *
 * L'étoile ouvre les plats déjà composés, « Ajouter » la recherche — qui porte aussi
 * la saisie manuelle, puisqu'un aliment tapé à la main devient une fiche. « Ajouter »
 * reste le geste principal : c'est le seul qui porte un libellé.
 *
 * **Les quatre modes de saisie y sont enfin**, et c'est [docs/02][parcours] au complet
 * à une forme près : une colonne plutôt qu'un arc déployé par un bouton unique. L'arc
 * viendra quand il aura quelque chose à replier — quatre boutons empilés se visent
 * aussi bien, et se codent sans animation.
 *
 * Les deux modes d'IA sont **grisés ensemble** : c'est la même clé qui leur manque.
 *
 * [parcours]: docs/02-parcours-et-ecrans.md
 */
@Composable
private fun DayActions(actions: HomeActions, aiConfigured: Boolean) {
    var explaining by rememberSaveable { mutableStateOf(false) }

    if (explaining) {
        AiUnavailableDialog(
            onConfigure = {
                explaining = false
                actions.onConfigureAi()
            },
            onDismiss = { explaining = false },
        )
    }

    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        AiButton(
            label = stringResource(R.string.home_photograph),
            configured = aiConfigured,
            onClick = actions.onPhotograph,
            onExplain = { explaining = true },
        ) { label -> CameraGlyph(contentDescription = label) }
        AiButton(
            label = stringResource(R.string.home_describe),
            configured = aiConfigured,
            onClick = actions.onDescribe,
            onExplain = { explaining = true },
        ) { label -> Icon(imageVector = Icons.Filled.Edit, contentDescription = label) }
        SmallFloatingActionButton(onClick = actions.onScan) {
            BarcodeGlyph(contentDescription = stringResource(R.string.home_scan))
        }
        SmallFloatingActionButton(onClick = actions.onOpenFavorites) {
            Icon(
                imageVector = Icons.Filled.Star,
                contentDescription = stringResource(R.string.home_open_favorites),
            )
        }
        ExtendedFloatingActionButton(onClick = actions.onAddDish) {
            Text(text = stringResource(R.string.home_add_dish))
        }
    }
}

/**
 * Visible et grisé plutôt que caché ([D73][decisions]), et **tapable dans les deux
 * cas**.
 *
 * Un bouton absent tant qu'aucune clé n'est saisie ne s'apprend jamais : personne ne
 * cherche dans les réglages une fonctionnalité dont rien n'indique l'existence. Un
 * bouton inerte n'apprend rien non plus — c'est pourquoi l'appui ouvre l'explication
 * et le chemin vers les réglages, au lieu de ne rien faire.
 *
 * [decisions]: docs/11-decisions.md
 */
@Composable
private fun AiButton(
    label: String,
    configured: Boolean,
    onClick: () -> Unit,
    onExplain: () -> Unit,
    icon: @Composable (String) -> Unit,
) {
    val unavailable = stringResource(R.string.home_describe_unavailable)

    SmallFloatingActionButton(
        // **Sans cle, le bouton explique avant d'emmener.** Y aller directement
        // deposait quelqu'un dans un ecran de reglages sans lui avoir dit pourquoi --
        // ce qu'il cherchait etait de photographier une assiette, pas de configurer
        // un fournisseur. La boite dit ce qui manque et ce que ca coute ; son bouton
        // ouvre la **section d'IA** et non le hub, pour ne pas faire choisir deux fois.
        onClick = if (configured) onClick else onExplain,
        containerColor = if (configured) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        },
        // Le grisé ne se voit pas au lecteur d'ecran : sans cette phrase, le bouton
        // s'annonce comme n'importe quel autre et l'appui semble sans effet.
        modifier = Modifier.semantics { if (!configured) stateDescription = unavailable },
    ) {
        // Les deux glyphes suivent la couleur du contenu : le grise se decide ici,
        // une fois, plutot que dans chaque appelant.
        CompositionLocalProvider(
            LocalContentColor provides
                if (configured) LocalContentColor.current else MaterialTheme.colorScheme.onSurfaceVariant,
        ) {
            icon(label)
        }
    }
}

/**
 * Ce qu'on dit quand il n'y a pas de clé, et **où aller**.
 *
 * Une explication sans chemin obligerait à chercher soi-même la bonne section des
 * réglages ; le bouton l'ouvre — la section d'IA directement, et non le hub, parce que
 * quelqu'un qui vient d'appuyer sur l'appareil photo cherche l'endroit où mettre une
 * clé, pas la liste des réglages. [docs/02][parcours] veut cette explication courte :
 * ce qui manque, ce que ça coûte, et rien de plus.
 *
 * [parcours]: docs/02-parcours-et-ecrans.md
 */
@Composable
private fun AiUnavailableDialog(onConfigure: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.home_ai_title)) },
        text = { Text(stringResource(R.string.home_ai_explanation)) },
        confirmButton = { TextButton(onClick = onConfigure) { Text(stringResource(R.string.home_ai_configure)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.home_ai_later)) } },
    )
}

/**
 * La journée, avec ou **sans** objectif.
 *
 * Une journée sans objectif n'est pas une journée à zéro : c'est une journée qu'on ne
 * peut comparer à rien, parce qu'aucun objectif ne courait ce jour-là — avant
 * l'onboarding, ou pour une journée notée avant qu'un objectif soit posé
 * ([D04][decisions]). Les six totaux s'affichent alors sans jauge, et l'écran invite à
 * répondre aux questions plutôt que d'inventer une cible.
 *
 * [decisions]: docs/11-decisions.md
 */
@Composable
internal fun DayContent(
    summary: DaySummary,
    actions: HomeActions,
    favoriteNameTaken: Boolean,
    onDismissFavoriteError: () -> Unit,
) {
    val goal = summary.goal
    if (goal != null) {
        RemainingBlock(summary, goal)
        MacroBars(summary, goal)
    } else {
        NoGoal(actions.onSetUpGoal)
        MacroTotalsOnly(summary)
    }
    if (summary.logged) {
        DishList(
            dishes = summary.dishes,
            zone = summary.zone,
            actions = actions,
            favoriteNameTaken = favoriteNameTaken,
            onDismissFavoriteError = onDismissFavoriteError,
        )
    } else {
        EmptyDay()
    }
}

/**
 * L'hexagone des macros, puis le grand chiffre.
 *
 * Le chiffre est sous la figure et non en son centre : les six quartiers prennent
 * naissance au centre, un texte y serait recouvert dès la première bouchée.
 *
 * C'est le **restant** qui s'affiche, pas le consommé — l'information dont on a
 * besoin au moment de décider quoi manger. Un dépassement l'affiche en négatif,
 * sans rouge d'alerte ni message : c'est une donnée, pas un jugement.
 */
@Composable
private fun RemainingBlock(summary: DaySummary, dailyGoal: DailyGoal) {
    val consumed = summary.totals[Macro.CALORIES].value
    val goal = dailyGoal.kcal
    val remaining = goal - consumed

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        // Plus d'intervalle a rajouter sous la figure : les six lettres tiennent
        // desormais dans sa zone, donc le « G » des glucides ne vient plus buter
        // contre le grand chiffre et le « C » ne sort plus par le haut.
        MacroHexagon(quarters = summary.quarters(dailyGoal))
        Text(
            text = abs(remaining).roundToInt().toString(),
            style = MaterialTheme.typography.displayLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = stringResource(if (remaining < 0) R.string.home_over_label else R.string.home_remaining_label),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = stringResource(R.string.home_consumed_of_goal, consumed.roundToInt(), goal.roundToInt()),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Les six quartiers, dérivés des totaux et de l'objectif du jour.
 *
 * Un objectif nul rendrait un ratio infini : le quartier est alors vide, ce qui est
 * la seule lecture honnête d'une cible qui n'existe pas.
 */
private fun DaySummary.quarters(goal: DailyGoal): Map<Macro, MacroQuarter> = Macro.entries.associateWith { macro ->
    val total = totals[macro]
    val target = goal[macro]
    MacroQuarter(
        ratio = if (target > 0.0) (total.value / target).toFloat() else 0f,
        complete = total.complete,
    )
}

@Composable
private fun MacroBars(summary: DaySummary, goal: DailyGoal) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        BAR_MACROS.forEach { macro ->
            MacroBar(
                macro = macro,
                label = stringResource(macro.labelRes),
                consumed = summary.totals[macro].value.toFloat(),
                goal = goal[macro].toFloat(),
                unit = MacroUnit.GRAM,
            )
        }

        // D29 : un total ampute d'une valeur inconnue ne doit pas se lire comme
        // exact. Le dire une fois sous les barres, plutot que de bruiter chacune.
        if (BAR_MACROS.any { !summary.totals[it].complete }) {
            Text(
                text = stringResource(R.string.home_incomplete_totals),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Les cinq barres, dans l'**ordre angulaire des quartiers**.
 *
 * Les calories n'en ont pas : elles ont le quartier du haut et le grand chiffre.
 * L'ordre suit celui de l'hexagone pour que l'œil passe de l'un à l'autre sans
 * traduction — deux ordres différents rendraient la couleur seule porteuse du lien.
 */
private val BAR_MACROS =
    listOf(Macro.PROTEIN, Macro.FIBER, Macro.CARBS, Macro.SUGARS, Macro.FAT)
