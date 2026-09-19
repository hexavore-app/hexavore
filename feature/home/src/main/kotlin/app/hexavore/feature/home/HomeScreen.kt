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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.hexavore.core.designsystem.component.AdjustmentCard
import app.hexavore.core.designsystem.component.MacroBar
import app.hexavore.core.designsystem.component.MacroHexagon
import app.hexavore.core.designsystem.component.MacroQuarter
import app.hexavore.core.designsystem.component.MacroUnit
import app.hexavore.core.designsystem.theme.Spacing
import app.hexavore.core.designsystem.theme.Timing
import app.hexavore.domain.appearance.DishDisplayStyle
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
    val dishStyle by viewModel.dishStyle.collectAsStateWithLifecycle()
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
        dishStyle = dishStyle,
        day = day,
        today = calendar.today,
        onBackToToday = { viewModel.onSelectDay(null) },
        onSelectDay = viewModel::onSelectDay,
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
                onAnalyse = routes.onAnalyse,
                onEditDish = routes.onEditDish,
                onDeleteDish = viewModel::onDeleteDish,
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
     * Le style d'affichage des plats, tel que les réglages l'ont posé.
     *
     * **Simplifié par défaut** : le détaillé cite chaque aliment de chaque plat, ce qui
     * fait beaucoup de texte dès qu'une journée est chargée. Il reste à un réglage.
     */
    dishStyle: DishDisplayStyle = DishDisplayStyle.SIMPLE,
    /**
     * Le jour affiche, ou `null` pour aujourd'hui.
     *
     * L'ecran Journee a disparu : c'est cette date qui dit ce que montrent les six
     * compteurs et la liste des plats, et c'est sur elle que le bouton d'ajout ecrit.
     */
    day: LocalDate? = null,
    /**
     * La date du jour, telle que l'horloge la voit.
     *
     * Elle vient du calendrier, qui la connaît déjà, plutôt que d'une seconde lecture :
     * deux sources pour « aujourd'hui », c'est une application qui change de jour à
     * deux moments différents. Elle sert à borner le glissement — le journal ne va pas
     * dans le futur.
     */
    today: LocalDate,
    onBackToToday: () -> Unit = {},
    /** Change le jour affiché. Le port range « aujourd'hui » comme `null` lui-même. */
    onSelectDay: (LocalDate) -> Unit = {},
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

    val collapseOnScroll = rememberCalendarScroll(calendarExpanded) { calendarExpanded = it }
    val snackbarHostState = rememberUndoBar(pendingUndo, actions.onUndo, actions.onUndoExpired)

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

            // Le glissement porte sur ce qui defile, jamais sur le calendrier : celui-ci
            // a son propre defilement horizontal, de semaine en semaine, et les deux
            // gestes se disputeraient le meme doigt au meme endroit.
            SwipingDay(
                shown = day ?: today,
                today = today,
                earliest = today.minusMonths(MONTHS_BACK),
                onDay = onSelectDay,
                modifier = Modifier.weight(1f),
            ) {
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
                            style = dishStyle,
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
}

/**
 * La barre qui rattrape un plat supprimé.
 *
 * **Elle reste affichée cinq secondes, ni quatre ni dix** : `SnackbarDuration` n'offre
 * que ces deux-là, donc la fenêtre est tenue par le délai et la barre par un affichage
 * indéfini. Voir `Timing` dans `:core:designsystem`, et la raison pour laquelle ce
 * délai-là ne suit pas le réglage d'animations réduites.
 *
 * Sortie de l'écran quand celui-ci a atteint le seuil de longueur, et le découpage
 * suit ce que les choses sont : une barre d'annulation est un dispositif à elle seule
 * — un état, un délai, deux issues.
 */
@Composable
private fun rememberUndoBar(pendingUndo: Dish?, onUndo: () -> Unit, onExpired: () -> Unit): SnackbarHostState {
    val host = remember { SnackbarHostState() }
    val deleted = stringResource(R.string.home_dish_deleted)
    val undo = stringResource(R.string.home_dish_undo)

    LaunchedEffect(pendingUndo) {
        if (pendingUndo == null) return@LaunchedEffect
        val result = withTimeoutOrNull(Timing.UNDO_WINDOW_MILLIS) {
            host.showSnackbar(message = deleted, actionLabel = undo, duration = SnackbarDuration.Indefinite)
        }
        if (result == SnackbarResult.ActionPerformed) onUndo() else onExpired()
    }

    return host
}

/**
 * Ce que le défilement de la page fait au calendrier : l'ouvrir, ou le replier.
 *
 * **Les deux règles sont ailleurs**, dans `collapsingDelta` et `pulledBy`, où elles
 * s'éprouvent sans écran. Ce qui vit ici est le branchement : ce qu'on consomme, on
 * l'a refermé ; ce qu'on a accumulé, on l'a ouvert.
 *
 * **La connexion n'est posée que sur le contenu**, jamais sur le calendrier :
 * `onPreScroll` va du parent vers l'enfant, donc une connexion englobant le mois
 * déplié le refermerait avant qu'il ait pu défiler ([D103][decisions]).
 *
 * [decisions]: docs/11-decisions.md
 */
@Composable
private fun rememberCalendarScroll(expanded: Boolean, onExpandedChange: (Boolean) -> Unit): NestedScrollConnection {
    // Lues a chaque geste et non capturees une fois : la connexion survit aux
    // recompositions, l'etat du calendrier non.
    val deplie by rememberUpdatedState(expanded)
    val change by rememberUpdatedState(onExpandedChange)
    val pullToExpand = with(LocalDensity.current) { ExpandPull.toPx() }

    // Ce que la page n'a pas pu defiler, accumule : c'est la traction vers le bas
    // quand on est deja en haut. Remis a zero par `pulledBy` des que le geste cesse
    // d'en etre une.
    var pulled by remember { mutableFloatStateOf(0f) }

    return remember(pullToExpand) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset =
                collapsingDelta(deplie, available).also { if (it != Offset.Zero) change(false) }

            // `onPostScroll` et non `onPreScroll` : ce qui nous interesse est ce que
            // l'enfant n'a **pas** consomme, et lui seul sait s'il lui restait de la
            // course. Aucun delta n'est repris ici -- la page a deja fini son geste.
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                pulled = pulledBy(pulled, deplie, available, source == NestedScrollSource.UserInput)
                if (pulled >= pullToExpand) {
                    change(true)
                    pulled = 0f
                }
                return Offset.Zero
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
private fun DayScroll(collapseOnScroll: NestedScrollConnection, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            // Toute la place que le glissement lui donne : c'est lui qui porte
            // desormais le poids dans la colonne de l'ecran.
            .fillMaxSize()
            .nestedScroll(collapseOnScroll)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(Spacing.xl),
        content = content,
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
    style: DishDisplayStyle,
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
            goal = goal,
            style = style,
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
    val target = goal[macro]
    MacroQuarter(ratio = if (target > 0.0) (totals[macro].value / target).toFloat() else 0f)
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
