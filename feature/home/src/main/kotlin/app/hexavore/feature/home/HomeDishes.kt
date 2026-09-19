package app.hexavore.feature.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import app.hexavore.core.designsystem.component.SourceBadge
import app.hexavore.core.designsystem.component.dishTitleText
import app.hexavore.core.designsystem.theme.NeonTheme
import app.hexavore.core.designsystem.theme.Spacing
import app.hexavore.domain.appearance.DishDisplayStyle
import app.hexavore.domain.diary.DishSummary
import app.hexavore.domain.diary.FoodEntry
import app.hexavore.domain.goal.DailyGoal
import app.hexavore.domain.nutrition.Macro
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlin.math.roundToInt

/**
 * Les plats de la journée, du plus ancien au plus récent.
 *
 * Chaque plat porte un nom, déduit de son heure ou écrit à la main ([D118][decisions]) :
 * c'est ce qui permet de lire une journée sans lire chaque aliment.
 *
 * [decisions]: docs/11-decisions.md
 */
@Composable
internal fun DishList(
    dishes: List<DishSummary>,
    zone: ZoneId,
    goal: DailyGoal?,
    style: DishDisplayStyle,
    actions: HomeActions,
    favoriteNameTaken: Boolean,
    onDismissFavoriteError: () -> Unit,
) {
    val timeFormatter = remember { DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT) }

    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xl)) {
        dishes.forEach { dish ->
            DishBlock(
                summary = dish,
                zone = zone,
                goal = goal,
                style = style,
                timeFormatter = timeFormatter,
                actions = actions,
                favoriteNameTaken = favoriteNameTaken,
                onDismissFavoriteError = onDismissFavoriteError,
            )
        }
    }
}

/**
 * Un plat, du badge de source à ses apports.
 *
 * **Le plat entier est la cible tactile.** Il l'est parce qu'il est l'unité de
 * saisie ([D31][decisions]) : ses lignes ont été entrées ensemble et se corrigent
 * ensemble. Restreindre le tap aux seules lignes d'aliment laissait l'heure, la
 * pastille, le total et les apports inertes — c'est-à-dire la moitié de la surface
 * du plat, sans que rien ne dise pourquoi elle ne répond pas ([D48][decisions]).
 *
 * **Plus aucun balayage ici.** Celui qui supprimait une ligne a été retiré, et
 * l'horizontal appartient désormais à la journée entière : il change de jour
 * ([D117][decisions]).
 *
 * [decisions]: docs/11-decisions.md
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
@Suppress("LongParameterList")
private fun DishBlock(
    summary: DishSummary,
    zone: ZoneId,
    goal: DailyGoal?,
    style: DishDisplayStyle,
    timeFormatter: DateTimeFormatter,
    actions: HomeActions,
    favoriteNameTaken: Boolean,
    onDismissFavoriteError: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    var confirming by remember { mutableStateOf(false) }
    var naming by remember { mutableStateOf(false) }
    val favorite = summary.dish.favoriteId != null

    Column(
        modifier = Modifier
            .fillMaxWidth()
            // Aucun rognage : les coins arrondis coupaient la pastille de source et
            // le total de calories, qui sont aux deux extremites de la premiere
            // ligne. L'ondulation deborde donc en rectangle, ce qui est le prix a
            // payer pour que rien ne soit tronque.
            .combinedClickable(
                onClickLabel = stringResource(R.string.home_dish_edit),
                onLongClickLabel = stringResource(R.string.home_dish_menu),
                onLongClick = { menuOpen = true },
                onClick = { actions.onEditDish(summary.dish.id) },
            ),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        DishMenu(
            expanded = menuOpen,
            favorite = favorite,
            onDismiss = { menuOpen = false },
            onEdit = { actions.onEditDish(summary.dish.id) },
            onDelete = { confirming = true },
            // Retirer ne demande pas de nom : le favori est deja designe par le
            // plat. Le mettre en demande un, et c'est la boite qui le fait saisir.
            onToggleFavorite = { if (favorite) actions.onToggleFavorite(summary.dish, null) else naming = true },
        )
        DishDialogs(
            summary = summary,
            actions = actions,
            naming = naming,
            confirming = confirming,
            favoriteNameTaken = favoriteNameTaken,
            onNamingChange = { naming = it },
            onConfirmingChange = { confirming = it },
            onDismissFavoriteError = onDismissFavoriteError,
        )
        DishHeader(summary, zone, goal, timeFormatter)
        // Le trait et les lignes d'aliments vont ensemble : sans lignes, le trait ne
        // separerait plus rien de rien.
        if (style == DishDisplayStyle.DETAILED) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            summary.entries.forEach { entry -> EntryRow(entry = entry) }
        }
        DishMacros(summary, goal)
    }
}

/**
 * Les deux boîtes du plat : nommer un favori, confirmer une suppression.
 *
 * Sorties de [DishBlock] pour que celui-ci reste lisible — il portait déjà le clic, le
 * clic long, le menu, l'en-tête, les lignes et les apports.
 */
@Composable
@Suppress("LongParameterList")
private fun DishDialogs(
    summary: DishSummary,
    actions: HomeActions,
    naming: Boolean,
    confirming: Boolean,
    favoriteNameTaken: Boolean,
    onNamingChange: (Boolean) -> Unit,
    onConfirmingChange: (Boolean) -> Unit,
    onDismissFavoriteError: () -> Unit,
) {
    val favorite = summary.dish.favoriteId != null

    if (naming) {
        FavoriteNameDialog(
            // Le titre du plat, et non ses trois premiers aliments : c'est le nom que
            // l'utilisateur a sous les yeux, et celui qu'il reconnaitra dans sa liste
            // de modeles. Les libelles de l'ANSES faisaient des titres de cinquante
            // caracteres qu'on efface au lieu de les corriger.
            proposal = dishTitleText(summary.title),
            nameTaken = favoriteNameTaken,
            onConfirm = { actions.onToggleFavorite(summary.dish, it) },
            onDismiss = {
                onNamingChange(false)
                onDismissFavoriteError()
            },
        )
    }
    // La boite se referme des que le plat porte son favori : c'est le seul signal
    // fiable que l'ecriture a abouti.
    LaunchedEffect(favorite) {
        if (favorite) onNamingChange(false)
    }
    if (confirming) {
        DeleteDishConfirmation(
            lines = summary.entries.size,
            onConfirm = {
                onConfirmingChange(false)
                actions.onDeleteDish(summary.dish)
            },
            onDismiss = { onConfirmingChange(false) },
        )
    }
}

/**
 * Le titre du plat, sa pastille de source, son heure et son total.
 *
 * **Le titre est ce qui a manqué le plus longtemps.** Un plat se lisait par ses
 * aliments, ce qui oblige à tous les lire pour savoir de quel repas il s'agit ; il
 * porte désormais un nom, déduit de son moment ou écrit à la main ([D118][decisions]).
 *
 * [decisions]: docs/11-decisions.md
 */
@Composable
private fun DishHeader(summary: DishSummary, zone: ZoneId, goal: DailyGoal?, timeFormatter: DateTimeFormatter) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalAlignment = Alignment.Bottom,
    ) {
        SourceBadge(source = summary.dish.source)
        Text(
            text = dishTitleText(summary.title),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = timeFormatter.format(summary.dish.loggedAt.atZone(zone)),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Shared(macro = Macro.CALORIES, share = summary.share(Macro.CALORIES, goal)) {
            Text(
                text = stringResource(R.string.home_dish_kcal, summary.totals[Macro.CALORIES].value.roundToInt()),
                style = MaterialTheme.typography.labelLarge,
                color = NeonTheme.macros[Macro.CALORIES].base,
            )
        }
    }
}

/**
 * Le menu de l'appui long : modifier, supprimer.
 *
 * **Il double le tap plutôt que de le remplacer.** Le tap ouvre la modification, qui
 * reste le geste courant ; l'appui long donne accès à ce qui n'a pas sa place sur la
 * surface du plat. « Modifier » y figure quand même — un menu dont la moitié des
 * entrées manque oblige à se souvenir de quel geste sert à quoi.
 */
@Composable
private fun DishMenu(
    expanded: Boolean,
    favorite: Boolean,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onToggleFavorite: () -> Unit,
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        DropdownMenuItem(
            text = { Text(stringResource(R.string.home_dish_menu_edit)) },
            onClick = {
                onDismiss()
                onEdit()
            },
        )
        DropdownMenuItem(
            text = {
                Text(
                    stringResource(
                        if (favorite) R.string.home_dish_menu_unfavorite else R.string.home_dish_menu_favorite,
                    ),
                )
            },
            onClick = {
                onDismiss()
                onToggleFavorite()
            },
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.home_dish_menu_delete)) },
            onClick = {
                onDismiss()
                onDelete()
            },
        )
    }
}

/**
 * La confirmation avant de supprimer un plat entier.
 *
 * Un dialogue, là où le balayage d'une ligne se contente de sa barre d'annulation :
 * celui-ci emporte *n* lignes d'un coup, et le nombre est dit. La barre reste offerte
 * ensuite — la confirmation évite l'accident, la barre rattrape le regret.
 */
@Composable
private fun DeleteDishConfirmation(lines: Int, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.home_dish_delete_title)) },
        text = { Text(pluralStringResource(R.plurals.home_dish_delete_body, lines, lines)) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(stringResource(R.string.home_dish_delete_confirm)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.home_dish_delete_cancel)) }
        },
    )
}

private val DishSummary.entries: List<FoodEntry> get() = dish.entries

/**
 * Une ligne d'aliment.
 *
 * Elle ne porte pas le clic : c'est le plat qui l'a, et il l'a en entier. Ce qu'elle
 * garde est sa **phrase** — nom, quantité, calories en une fois plutôt qu'en trois
 * arrêts. Le plat étant devenu une cible tactile, il fusionne les nœuds qu'il
 * contient ; cette phrase est ce qui rend l'annonce du plat lisible au lieu d'être
 * une suite de fragments.
 */
@Composable
private fun EntryRow(entry: FoodEntry) {
    val description = stringResource(
        R.string.home_entry_a11y,
        entry.displayName,
        formatGrams(entry.quantity),
        entry.unit,
        entry.macros.kcal.roundToInt(),
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) { contentDescription = description }
            .padding(vertical = Spacing.xs),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = entry.displayName,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = stringResource(R.string.home_entry_quantity, formatGrams(entry.quantity), entry.unit),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = stringResource(R.string.home_entry_kcal, entry.macros.kcal.roundToInt()),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
