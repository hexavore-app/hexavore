package app.hexavore.feature.entry

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import app.hexavore.core.designsystem.component.DraftTextField
import app.hexavore.core.designsystem.component.NeonChip
import app.hexavore.core.designsystem.component.SourceBadge
import app.hexavore.core.designsystem.component.dishTitleText
import app.hexavore.core.designsystem.component.momentLabel
import app.hexavore.core.designsystem.theme.Spacing
import app.hexavore.domain.diary.DishTitle
import app.hexavore.domain.diary.MealMoment
import java.time.format.DateTimeFormatter

/**
 * Le haut d'une validation : ce qu'on est en train de faire, et sur quoi.
 *
 * Sorti de `EntryScreen` quand le seuil de fonctions par fichier a mordu, et le
 * decoupage suit ce que les choses sont : l'en-tete dit **de quoi il s'agit** -- un
 * plat neuf, un plat modifie, un favori reecrit -- et **ou cela ira**. Le reste du
 * fichier d'origine dit comment on le saisit.
 */

@Composable
internal fun DraftHeader(state: EntryUiState.Content, actions: EntryActions, dateFormatter: DateTimeFormatter) {
    val context = LocalContext.current
    val titre = dishTitleText(DishTitle.Moment(state.form.moment, rank = 1))

    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(
                    when {
                        state.editingFavorite -> R.string.entry_title_favorite
                        state.form.dishId == null -> R.string.entry_title_new
                        else -> R.string.entry_title_edit
                    },
                ),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            // L'etoile n'apparait que sur un brouillon complet : un favori sans
            // ligne enregistrable ne rejouerait rien, et il n'y a rien a expliquer
            // sur un plat qu'on est en train de remplir.
            if (state.favoritable) {
                FavoriteStar(
                    favorite = state.favorite,
                    onToggle = {
                        if (state.favorite) {
                            actions.onUnfavorite()
                        } else {
                            actions.onNaming(state.form.title ?: titre) { base, rank ->
                                context.getString(R.string.entry_title_ranked, base, rank)
                            }
                        }
                    },
                )
            }
        }

        // Rien a titrer quand on reecrit un modele : un favori porte deja son nom, et
        // il se change dans la liste ou on le lit.
        if (!state.editingFavorite) {
            DishTitleField(state, actions, titre)
        }

        state.favoriteProposal?.let { proposal ->
            FavoriteNameDialog(
                proposal = proposal,
                nameTaken = state.favoriteNameTaken,
                onConfirm = actions.onFavorite,
                // Refermer remet l'etat entier a zero, message de nom pris compris :
                // il ne s'applique plus a rien.
                onDismiss = actions.onDismissNaming,
            )
        }
        SourceAndDay(state, dateFormatter)
    }
}

/**
 * Le nom du plat, et les quatre moments qui le proposent.
 *
 * **Le champ montre le nom du moment sans l'avoir enregistré.** Tant que personne n'y
 * touche, le plat s'appelle du nom de son moment et rien n'est écrit : ce qui est
 * proposé n'est pas ce qui est saisi ([D118][decisions]). C'est la même distinction
 * qu'entre une valeur estimée et une valeur corrigée à la main.
 *
 * **Les quatre pastilles existent parce que l'heure d'un plat est celle de sa saisie.**
 * Le dîner d'hier noté ce matin s'appellerait « Petit-déjeuner », et personne n'a envie
 * de retaper un mot que l'application connaît. Un tap remplace le titre.
 *
 * **Le champ est recréé à chaque pastille**, et seulement là : il est non contrôlé
 * ([D45][decisions]) et garde donc le texte de sa première composition. Le recréer à
 * chaque frappe ramènerait le curseur à la fin au milieu d'une correction ; ne jamais
 * le recréer rendrait les pastilles inertes à l'œil.
 *
 * [decisions]: docs/11-decisions.md
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DishTitleField(state: EntryUiState.Content, actions: EntryActions, moment: String) {
    var generation by remember { mutableIntStateOf(0) }

    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        key(generation) {
            DraftTextField(
                initial = state.form.title ?: moment,
                onValueChange = actions.onTitle,
                label = stringResource(R.string.entry_dish_title),
                modifier = Modifier.fillMaxWidth(),
                maxLines = TITLE_LINES,
            )
        }

        // `FlowRow` et non `Row` : les quatre libelles mis bout a bout font 329 dp, et
        // un ecran de 360 dp n'en offre que 328 une fois ses marges prises. La
        // difference tient dans un cheveu, donc elle basculerait d'un telephone a
        // l'autre -- et ce qui deborde d'une rangee ne se voit pas, il se fait rogner.
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            MealMoment.entries.forEach { moment ->
                NeonChip(
                    label = momentLabel(moment),
                    selected = state.form.moment == moment && state.form.title == null,
                    onClick = {
                        actions.onMoment(moment)
                        generation++
                    },
                )
            }
        }
    }
}

/** Un titre tient sur deux lignes : « Poke bowl saumon avocat » n'est pas un cas rare. */
private const val TITLE_LINES = 2

/**
 * D'ou vient ce plat, et quel jour il ira.
 *
 * **La meme ligne, deux tons.** Aujourd'hui, la date est un rappel discret ; un autre
 * jour, elle dit ce que l'appui va faire et se lit comme le reste du texte.
 *
 * Depuis qu'on peut rattraper un repas oublie, c'est le dernier endroit avant
 * l'ecriture. L'accueil annonce bien le jour en titre, mais entre les deux il y a eu
 * la recherche, un scan, ou une modale d'IA -- et une date en petit gris a cote d'un
 * badge est exactement ce qu'on ne lit pas.
 */
@Composable
private fun SourceAndDay(state: EntryUiState.Content, dateFormatter: DateTimeFormatter) {
    val jour = state.otherDay

    Row(
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SourceBadge(source = state.form.source)
        Text(
            text = when (jour) {
                null -> dateFormatter.format(state.form.date)
                else -> stringResource(R.string.entry_other_day, dateFormatter.format(jour))
            },
            style = when (jour) {
                null -> MaterialTheme.typography.labelSmall
                else -> MaterialTheme.typography.bodyMedium
            },
            color = when (jour) {
                null -> MaterialTheme.colorScheme.onSurfaceVariant
                else -> MaterialTheme.colorScheme.onSurface
            },
        )
    }
}
