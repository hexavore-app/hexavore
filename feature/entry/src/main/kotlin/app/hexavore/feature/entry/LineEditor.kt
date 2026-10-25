package app.hexavore.feature.entry

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import app.hexavore.core.designsystem.component.DraftTextField
import app.hexavore.core.designsystem.component.WholeNumberField
import app.hexavore.core.designsystem.component.isNumberField
import app.hexavore.core.designsystem.theme.NeonTheme
import app.hexavore.core.designsystem.theme.Spacing
import app.hexavore.domain.nutrition.Macro
import app.hexavore.domain.profile.UnitSystem
import kotlin.math.roundToInt

/**
 * Une ligne à saisir : nom, quantité, et les six valeurs.
 *
 * **L'énergie est toujours visible, les cinq autres se déplient.** Ce n'est pas un
 * gain de place : c'est la hiérarchie des obligations. Les calories sont exigées —
 * une ligne sans énergie n'entre pas dans le journal — alors que les cinq autres
 * peuvent rester inconnues, et un champ laissé vide veut dire *inconnu* et non zéro.
 * Les afficher toutes au même niveau laisserait croire qu'il faut les remplir.
 *
 * @see docs/02-parcours-et-ecrans.md
 */
@Composable
internal fun LineEditor(
    line: EntryFormLine,
    actions: EntryActions,
    units: UnitSystem,
    modifier: Modifier = Modifier,
    flagged: MissingField? = null,
) {
    // Une carte par aliment. Le defaut precedent n'etait pas d'etre laid mais d'etre
    // **continu** : six champs les uns sous les autres, puis six autres, et rien ne
    // disait ou finissait le riz et ou commencait le poulet.
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            // Toute la ligne revit quand elle change d'aliment -- le nom et la
            // quantite comme les valeurs. La cle des macros, elle, reste interieure :
            // elle repond a un recalcul, qui ne doit pas deplacer le curseur du champ
            // de quantite qu'on est en train de remplir.
            key(line.substitutions) {
                NameRow(line, actions, flagged == MissingField.NAME)
                SuggestionRow(line, actions)
                QuantityRow(line, actions, units, flagged == MissingField.QUANTITY)
                MacroGrid(line, actions, flagged == MissingField.CALORIES)
                // Sous la grille et non a cote du champ d'energie : la proposition
                // se deduit des trois autres valeurs, et la placer contre l'une
                // d'elles laisserait croire qu'elle n'en regarde qu'une.
                EnergyProposalRow(line, actions)
            }
        }
    }
}

/**
 * Les six valeurs, **toutes visibles**, deux par ligne.
 *
 * **Plus de plier-déplier.** Cette application sert à suivre des macros : les cacher
 * derrière un bouton demandait un geste de plus par aliment pour voir ce qu'on est
 * venu voir. Le repli tenait à un raisonnement qui ne vaut plus — « les cinq autres
 * sont facultatives, les afficher laisserait croire qu'il faut les remplir » — parce
 * que ce qui manque se dit maintenant autrement : le champ vide se signale à
 * l'enregistrement, et l'écran y emmène.
 *
 * **Deux par ligne**, parce qu'un champ pleine largeur pour écrire « 254 » gaspille
 * cinquante caractères d'espace et allonge l'écran d'autant. L'ordre reste celui de
 * l'hexagone et des barres de l'accueil : la position sert de second canal en cas de
 * daltonisme, et elle ne renseigne que si elle est la même partout.
 */
@Composable
private fun MacroGrid(line: EntryFormLine, actions: EntryActions, flagged: Boolean = false) {
    MACRO_PAIRS.forEach { pair ->
        key(pair.first()) {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                pair.forEach { macro ->
                    key(macro) {
                        MacroField(
                            line = line,
                            macro = macro,
                            actions = actions,
                            modifier = Modifier.weight(1f),
                            isError = flagged && macro == Macro.CALORIES,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Le nom de l'aliment, et la corbeille.
 *
 * **Le balayage ne suffit pas.** Un geste sans représentation visible est
 * introuvable pour qui ne le connaît pas, hors d'atteinte au lecteur d'écran, et
 * difficile pour une main qui tient mal le téléphone. Une action destructrice a
 * besoin d'un chemin qui se voie ; le balayage reste le raccourci de celui qui le
 * connaît.
 */
@Composable
private fun NameRow(line: EntryFormLine, actions: EntryActions, flagged: Boolean = false) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DraftTextField(
            initial = line.name,
            onValueChange = { actions.onLineEdit(line.id, LineEdit.Name(it)) },
            label = stringResource(R.string.entry_field_name),
            modifier = Modifier.weight(1f),
            // Deux lignes : un libelle de l'ANSES depasse largement la largeur d'un
            // champ, et sur une seule ligne le curseur partait a la fin -- on ne
            // voyait que la queue du titre, et l'effacer etait le seul moyen d'en
            // lire le debut.
            maxLines = NAME_LINES,
            isError = flagged,
        )
        IconButton(onClick = { actions.onRemoveLine(line.id) }) {
            Icon(
                imageVector = Icons.Filled.Delete,
                contentDescription = stringResource(R.string.entry_remove_line_a11y, line.name),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    OriginalLabel(line)
}

/**
 * Le libellé publié par l'ANSES, sous le champ, quand un titre court l'a remplacé.
 *
 * **C'est lui qui distingue deux préparations du même aliment** — sans peau ou avec,
 * cuit au four ou frit — et le titre court, par construction, en abandonne une partie.
 * Les listes s'en passent parce qu'on y balaie ; ici on valide ce qui va entrer au
 * journal, et c'est le seul moment où la précision vaut la place qu'elle prend.
 *
 * Absent dès que le champ a été retouché : ce qui est affiché n'est alors plus le
 * titre court de la fiche, et rappeler un libellé « d'origine » sous un nom que
 * l'utilisateur a écrit lui-même désignerait autre chose que ce qu'il lit.
 */
@Composable
private fun OriginalLabel(line: EntryFormLine) {
    val original = line.food?.name ?: return
    if (original == line.name) return

    Text(
        text = original,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/**
 * Ce que le modèle a proposé, et les autres fiches qu'on aurait acceptées.
 *
 * **Deux incertitudes affichées séparément**, parce qu'elles se trompent séparément :
 * la confiance du modèle sur ce qu'il a compris, et le fait que la quantité vienne
 * d'un forfait plutôt que d'une portion mesurée. [docs/04][sources] exige la seconde —
 * *« toute conversion appuyée sur un défaut doit être signalée »* — et [docs/02][parcours]
 * la première.
 *
 * Les alternatives ne se posent pas en question à trancher : la ligne est déjà
 * remplie avec le meilleur candidat, et elles sont là **au cas où**. Obliger à choisir
 * ferait payer trois lectures à chaque ligne douteuse, y compris quand le premier
 * candidat était le bon.
 *
 * [parcours]: docs/02-parcours-et-ecrans.md
 * [sources]: docs/04-sources-de-donnees.md
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SuggestionRow(line: EntryFormLine, actions: EntryActions) {
    val suggestion = line.suggestion ?: return
    val confidence = (suggestion.confidence * PERCENT).roundToInt()

    // Trois affirmations distinctes, assemblees plutot que declinees en huit
    // chaines : ce que le modele a compris, d'ou vient la quantite, et d'ou viennent
    // les valeurs. Elles se trompent separement, donc elles se lisent separement.
    val marques = listOfNotNull(
        stringResource(
            if (suggestion.unsure) R.string.entry_suggestion_unsure else R.string.entry_suggestion,
            confidence,
        ),
        stringResource(R.string.entry_suggestion_quantity).takeIf { suggestion.estimated },
        stringResource(R.string.entry_suggestion_macros).takeIf { suggestion.estimatedMacros },
    )

    // Un doute du modele se voit, et se lit : la couleur et les mots, jamais la
    // couleur seule (D25). Une confiance de 20 % s'affichait dans le gris d'une de
    // 95 % (D162).
    Text(
        text = marques.joinToString(separator = MARK_SEPARATOR),
        style = MaterialTheme.typography.labelSmall,
        color = if (suggestion.unsure) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
    )

    if (suggestion.alternatives.isEmpty()) return

    Text(
        text = stringResource(R.string.entry_alternatives),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        suggestion.alternatives.forEach { food ->
            key(food.id.value) {
                FilterChip(
                    selected = false,
                    onClick = { actions.onLineEdit(line.id, LineEdit.Substitute(food)) },
                    label = { Text(text = food.name, style = MaterialTheme.typography.labelSmall) },
                )
            }
        }
    }
}

/**
 * La quantité et son unité.
 *
 * Les unités proposées dépendent de la ligne : les grammes et les millilitres
 * toujours, plus les portions que la fiche d'origine déclare — « 1 pomme moyenne »,
 * « 1 tranche ». Une ligne tapée à la main n'en a aucune, faute de fiche pour dire
 * ce que pèse une tranche.
 *
 * Les pastilles passent à la ligne quand elles ne tiennent pas : un aliment peut en
 * proposer trois, et un libellé de portion est long.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun QuantityRow(line: EntryFormLine, actions: EntryActions, units: UnitSystem, flagged: Boolean = false) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        DraftTextField(
            initial = line.quantity,
            onValueChange = { actions.onLineEdit(line.id, LineEdit.Quantity(it)) },
            label = stringResource(R.string.entry_field_quantity),
            modifier = Modifier.fillMaxWidth(),
            keyboardType = KeyboardType.Decimal,
            isError = flagged,
            accept = String::isNumberField,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            line.units(units).forEach { unit ->
                FilterChip(
                    selected = line.chose(unit),
                    onClick = { actions.onLineEdit(line.id, LineEdit.Measurement(unit)) },
                    label = { Text(unit.code) },
                )
            }
        }
    }
}

/**
 * Le champ d'une macro.
 *
 * Son libellé porte la teinte de la macro, la même que sur l'accueil : c'est ce qui
 * fait qu'une valeur saisie ici se retrouve sans traduction dans la barre du haut.
 * La teinte ne renseigne pas seule — le nom est écrit à côté.
 */
@Composable
private fun MacroField(
    line: EntryFormLine,
    macro: Macro,
    actions: EntryActions,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
) {
    // La cle porte la revision : un recalcul reconstruit le champ avec sa nouvelle
    // valeur, alors qu'une frappe -- qui ne l'incremente pas -- laisse le curseur ou
    // il est. Sans elle, le brouillon changerait sans que l'ecran bouge.
    key(line.revision) {
        MacroTextField(line, macro, actions, modifier, isError)
    }
}

@Composable
private fun MacroTextField(
    line: EntryFormLine,
    macro: Macro,
    actions: EntryActions,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
) {
    // Des entiers : personne ne compte les demi-grammes. Le clavier offre pourtant la
    // virgule, et le champ dit pourquoi il la refuse (D172).
    WholeNumberField(
        initial = line.macros[macro].orEmpty(),
        onValueChange = { actions.onLineEdit(line.id, LineEdit.MacroValue(macro, it)) },
        label = stringResource(macro.fieldRes),
        modifier = modifier,
        labelColor = NeonTheme.macros[macro].base,
        isError = isError,
        // Le contour en pointilles de D25 : cette valeur vient d'un modele, pas
        // d'une mesure. Une fiche dont trois teneurs sur six ont ete completees le
        // dit champ par champ, parce qu'une marque de ligne mentirait sur les trois
        // autres.
        estimated = macro in line.estimated,
    )
}

/**
 * Les six valeurs par paires, dans l'ordre angulaire de l'hexagone.
 *
 * Le même ordre que les barres de l'accueil et que les quartiers de la figure, lu de
 * gauche à droite puis ligne suivante : la position sert de second canal en cas de
 * daltonisme, et elle ne renseigne que si elle est la même partout.
 */
private val MACRO_PAIRS = listOf(
    listOf(Macro.CALORIES, Macro.PROTEIN),
    listOf(Macro.FIBER, Macro.CARBS),
    listOf(Macro.SUGARS, Macro.FAT),
)

private val Macro.fieldRes: Int
    @StringRes get() = when (this) {
        Macro.CALORIES -> R.string.entry_field_calories
        Macro.PROTEIN -> R.string.entry_field_protein
        Macro.CARBS -> R.string.entry_field_carbs
        Macro.SUGARS -> R.string.entry_field_sugars
        Macro.FAT -> R.string.entry_field_fat
        Macro.FIBER -> R.string.entry_field_fiber
    }

/** La confiance s'affiche en pourcentage : « 0,9 » ne se lit pas. */
/**
 * Deux lignes pour un nom d'aliment, et pas trois.
 *
 * Deux suffisent à la quasi-totalité des libellés de l'ANSES, et une troisième
 * repousserait les six valeurs hors de l'écran sur un téléphone étroit — on lirait le
 * titre en entier au prix de ce qu'on est venu vérifier.
 */
internal const val NAME_LINES = 2

private const val PERCENT = 100

/** Ce qui separe les marques d une ligne proposee. */
private const val MARK_SEPARATOR = " · "
