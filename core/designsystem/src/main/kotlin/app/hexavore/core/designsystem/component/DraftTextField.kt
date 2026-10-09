package app.hexavore.core.designsystem.component

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.hexavore.core.designsystem.theme.Radius

/**
 * Un champ dont l'affichage ne dépend d'aucun aller-retour d'état.
 *
 * **Le texte affiché vit ici**, et non dans le `ViewModel`. La forme habituelle —
 * `value = state.texte`, `onValueChange = { viewModel.change(it) }` — suppose que
 * l'état revienne avant la frappe suivante. Il ne revient pas toujours : entre la
 * frappe et le nouvel état, il y a un `StateFlow`, un `combine` et une
 * recomposition, et une frappe rapide arrive avant la fin du trajet. Le champ se
 * réaffiche alors avec un texte d'il y a deux caractères, et la position du curseur
 * repart avec lui — on tape « Bolognaise », on lit « Boognaseil ».
 *
 * Ici, chaque frappe est appliquée immédiatement à l'état local ; le `ViewModel` est
 * prévenu ensuite et ne renvoie rien. Il n'y a plus qu'un seul écrivain, donc plus
 * de course.
 *
 * **Il vit dans le design system parce que la règle vaut pour tout champ du projet**
 * ([D45][decisions]), et qu'une seconde copie dans un autre écran divergerait le
 * jour où l'une des deux apprendrait quelque chose que l'autre ignore.
 *
 * [initial] n'est lu qu'à la première composition. C'est voulu et suffisant : chaque
 * champ est identifié par sa position dans une liste à clés, donc rouvrir un
 * formulaire le reconstruit avec le bon texte, et rien d'autre ne réécrit ce que
 * l'utilisateur tape.
 *
 * [decisions]: docs/11-decisions.md
 *
 * @param accept ce que le champ laisse entrer. Une frappe refusée ne change rien —
 *   ni ici, ni dans le brouillon —, ce qui évite qu'une saisie devienne
 *   silencieusement invalide à cause d'un caractère parasite.
 * @param visualTransformation ce qui s'affiche à la place de ce qui est saisi. Elle
 *   masque une clé d'API sans la remplacer : [docs/05][ia] veut un champ masqué qui se
 *   révèle à la demande, donc un texte qu'on peut relire mais qui ne traîne pas à
 *   l'écran. Une chaîne d'astérisques stockée à la place aurait fait enregistrer les
 *   astérisques.
 *
 * [ia]: docs/05-ia.md
 */
@Composable
fun DraftTextField(
    initial: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    labelColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    keyboardType: KeyboardType = KeyboardType.Text,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    /**
     * Le nombre de lignes visibles, et **ce que fait la touche entrée**.
     *
     * Au-delà d'une, le champ cesse d'être sur une seule ligne et la touche entrée y
     * saute une ligne au lieu de valider : c'est ce qu'attend une description de repas,
     * où l'on énumère. Un champ d'une ligne, lui, garde son comportement — la touche
     * emmène au champ suivant.
     */
    minLines: Int = 1,
    /**
     * Le nombre de lignes **au-delà duquel le texte ne se déplie plus**.
     *
     * Un champ d'une ligne fait défiler son texte horizontalement, et le curseur part
     * à la fin : d'un libellé de l'ANSES on ne voyait donc que la queue, et le seul
     * moyen d'en lire le début était d'effacer la fin. Au-delà de un, le texte revient
     * à la ligne et le début reste visible.
     *
     * **Distinct de [minLines]**, qui décide de la hauteur au repos et de ce que fait
     * la touche entrée. Un champ de nom veut une seule ligne quand le nom est court,
     * deux quand il est long, et jamais de retour à la ligne dans sa valeur — les
     * trois se règlent séparément.
     */
    maxLines: Int = 1,
    /**
     * `true` quand ce champ est **celui qui manque**.
     *
     * Il se colore, et son libellé avec. Un formulaire de vingt-quatre champs dont un
     * seul bloque l'enregistrement ne se parcourt pas à l'œil : c'est le champ qui
     * doit se désigner, pas l'utilisateur qui doit le chercher.
     */
    isError: Boolean = false,
    /**
     * `true` quand la valeur affichée **vient d'un modèle et non d'une mesure**.
     *
     * Le champ prend alors un contour en pointillés, la forme que le projet réserve
     * depuis [D25][decisions] à ce qui a été estimé — jamais une couleur, qui
     * travaillerait seule et ne dirait rien à qui ne distingue pas les teintes.
     *
     * Elle disparaît dès que l'utilisateur touche au champ : la valeur est alors la
     * sienne, et continuer à la présenter comme incertaine serait faux.
     *
     * [decisions]: docs/11-decisions.md
     */
    estimated: Boolean = false,
    /**
     * Ce que porte la touche d'action du clavier, quand ce n'est pas « suivant ».
     *
     * **Le défaut reste une règle et non un choix** : un champ d'une ligne emmène au
     * champ suivant, un champ qui en tolère plusieurs saute une ligne. Ce paramètre
     * existe pour le seul cas où la touche a quelque chose à déclencher — la barre du
     * bas, où écrire *est* l'action ([D131][decisions]) — et il vient toujours avec
     * [onImeAction], sans quoi la touche promettrait ce qu'elle ne fait pas.
     *
     * [decisions]: docs/11-decisions.md
     */
    imeAction: ImeAction? = null,
    onImeAction: () -> Unit = {},
    /**
     * Ce qui se pose **dans** le champ, à droite.
     *
     * Le bouton d'envoi de la barre du bas y vit plutôt qu'à côté : une cible posée à
     * l'extérieur ajoute une colonne à une ligne qui en compte déjà trois, et le geste
     * — écrire, puis envoyer — se fait alors sans que le doigt quitte le champ.
     */
    trailingIcon: @Composable (() -> Unit)? = null,
    /**
     * `true` pour un champ **posé dans une barre**, et non dans un formulaire.
     *
     * Material réserve 56 dp à un champ, et son libellé flottant occupe le haut de
     * cette hauteur. C'est juste dans un formulaire, où le libellé dit ce que la ligne
     * attend une fois remplie ; c'est trop dans une barre qu'on voit en permanence
     * au-dessus du pouce ([D135][decisions]).
     *
     * Le libellé devient alors un **texte d'invite** — il ne sert qu'à vide, puisque la
     * barre n'a qu'un champ et qu'on ne se demande pas ce qu'il attend — et la marge
     * intérieure se resserre. Le champ descend à [CompactHeight].
     *
     * [decisions]: docs/11-decisions.md
     */
    compact: Boolean = false,
    accept: (String) -> Boolean = { true },
) {
    var value by remember { mutableStateOf(TextFieldValue(initial, TextRange(initial.length))) }
    val ink = MaterialTheme.colorScheme.onSurfaceVariant

    if (compact) {
        CompactField(
            value = value,
            onValueChange = { candidate ->
                if (accept(candidate.text) && (minLines > 1 || candidate.text.isSingleLine())) {
                    value = candidate
                    onValueChange(candidate.text)
                }
            },
            modifier = modifier,
            maxLines = maxOf(minLines, maxLines),
            keyboard = FieldKeyboard(
                type = keyboardType,
                action = imeAction ?: ImeAction.Default,
                onAction = onImeAction,
            ),
            decoration = FieldDecoration(
                hint = label,
                hintColor = labelColor,
                visualTransformation = visualTransformation,
                trailingIcon = trailingIcon,
            ),
        )
        return
    }

    OutlinedTextField(
        value = value,
        onValueChange = { candidate ->
            // Le champ **refuse** la frappe plutot que de l'accepter puis de la
            // nettoyer : nettoyer obligerait a reecrire le texte affiche, donc a
            // repositionner le curseur -- exactement ce que ce composant evite.
            if (accept(candidate.text) && (minLines > 1 || candidate.text.isSingleLine())) {
                value = candidate
                onValueChange(candidate.text)
            }
        },
        label = { Text(text = label, color = if (isError) MaterialTheme.colorScheme.error else labelColor) },
        isError = isError,
        // `singleLine` fait defiler au lieu de replier : il ne vaut que pour un champ
        // qui ne montre qu'une ligne, jamais pour un champ qui en tolere deux.
        singleLine = minLines == 1 && maxLines == 1,
        minLines = minLines,
        maxLines = maxOf(minLines, maxLines),
        // Decimal et non Number : le separateur decimal doit etre atteignable, et
        // la virgule est ce que produit un clavier en francais.
        keyboardOptions = KeyboardOptions(
            keyboardType = keyboardType,
            imeAction = imeAction ?: if (minLines == 1) ImeAction.Next else ImeAction.Default,
        ),
        // Les trois memes gestes : la touche du clavier fait ce que fait le bouton,
        // quelle que soit celle que l'appelant a demandee.
        keyboardActions = KeyboardActions(
            onSend = { onImeAction() },
            onDone = { onImeAction() },
            onGo = { onImeAction() },
        ),
        trailingIcon = trailingIcon,
        visualTransformation = visualTransformation,
        modifier = if (estimated) modifier.dashedOutline(ink) else modifier,
    )
}

/**
 * Le même champ, **plus bas**, pour une barre.
 *
 * Il est bâti sur `BasicTextField` et la boîte de décoration de Material plutôt que sur
 * `OutlinedTextField` : celui-ci n'expose pas sa marge intérieure, et son plancher de
 * 56 dp est ce qu'on cherche justement à descendre. Tout le reste — le contour, l'état
 * de focus, l'icône de droite — vient de Material, donc le champ reste celui du système
 * et ne dérive pas au prochain palier.
 *
 * **Le texte d'invite disparaît dès la première lettre**, contrairement au libellé
 * flottant qui garde sa place en haut. C'est ce qui rend la hauteur possible.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CompactField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    modifier: Modifier,
    maxLines: Int,
    keyboard: FieldKeyboard,
    decoration: FieldDecoration,
) {
    val interactions = remember { MutableInteractionSource() }

    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.heightIn(min = CompactHeight),
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        keyboardOptions = KeyboardOptions(keyboardType = keyboard.type, imeAction = keyboard.action),
        keyboardActions = KeyboardActions(
            onSend = { keyboard.onAction() },
            onDone = { keyboard.onAction() },
            onGo = { keyboard.onAction() },
        ),
        maxLines = maxLines,
        interactionSource = interactions,
        decorationBox = { field ->
            OutlinedTextFieldDefaults.DecorationBox(
                value = value.text,
                innerTextField = field,
                enabled = true,
                singleLine = maxLines == 1,
                visualTransformation = decoration.visualTransformation,
                interactionSource = interactions,
                placeholder = { Text(text = decoration.hint, color = decoration.hintColor) },
                trailingIcon = decoration.trailingIcon,
                contentPadding = PaddingValues(horizontal = CompactPadding, vertical = CompactPadding),
                container = {
                    OutlinedTextFieldDefaults.Container(
                        enabled = true,
                        isError = false,
                        interactionSource = interactions,
                        // **Un fond, et non un simple contour.** Le champ compact vit
                        // dans la barre du bas, sur `surfaceContainerHigh` : un
                        // contour seul s'y fondait, et beaucoup de gens ne voyaient
                        // pas qu'on pouvait ecrire la ([D143][decisions]). Le ton le
                        // plus bas s'en detache dans les deux themes -- plus clair sur
                        // le clair, plus sombre sur le sombre -- sans devenir un
                        // bouton.
                        //
                        // [decisions]: docs/11-decisions.md
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                        ),
                        shape = RoundedCornerShape(Radius.field),
                    )
                },
            )
        },
    )
}

/**
 * Ce que la frappe déclenche : le clavier demandé, et ce que fait sa touche d'action.
 *
 * Les trois vont ensemble — un type de clavier sans sa touche, ou une touche sans ce
 * qu'elle appelle, ne veulent rien dire séparément.
 */
@Immutable
private data class FieldKeyboard(val type: KeyboardType, val action: ImeAction, val onAction: () -> Unit)

/**
 * Ce qui habille un champ compact, en un objet.
 *
 * Regroupés plutôt que passés un par un : le seuil de paramètres a mordu, et le
 * découpage suit ce que les choses sont — d'un côté ce qui reçoit la frappe, de l'autre
 * ce qui l'entoure.
 */
@Immutable
private data class FieldDecoration(
    val hint: String,
    val hintColor: Color,
    val visualTransformation: VisualTransformation,
    val trailingIcon: (@Composable () -> Unit)?,
)

/** Huit dp sous le plancher de Material, et toujours au-dessus de la cible tactile. */
private val CompactHeight: Dp = 48.dp

private val CompactPadding: Dp = 12.dp

/**
 * Le contour en pointillés d'une valeur estimée, dessiné **par-dessus** celui du champ.
 *
 * `OutlinedTextField` peint sa propre bordure et ne se laisse pas remplacer sans
 * réécrire tout le composant. Un second tracé au même rayon la recouvre exactement, ce
 * qui coûte un `drawWithContent` là où une réimplémentation coûterait la gestion du
 * focus, de l'erreur et du libellé flottant.
 *
 * Le trait est plus épais que celui du champ : un pointillé fin se lit comme un défaut
 * de rendu, et il doit se voir pour signaler quelque chose ([D25][decisions]).
 *
 * [decisions]: docs/11-decisions.md
 */
private fun Modifier.dashedOutline(color: Color): Modifier = drawWithContent {
    drawContent()
    val stroke = DashedStroke.toPx()
    val inset = stroke / 2f
    drawRoundRect(
        color = color,
        topLeft = Offset(inset, inset),
        size = Size(size.width - stroke, size.height - stroke),
        cornerRadius = CornerRadius(Radius.field.toPx()),
        style = Stroke(
            width = stroke,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(DashOn.toPx(), DashOff.toPx())),
        ),
    )
}

private val DashedStroke: Dp = 1.5.dp
private val DashOn: Dp = 3.dp
private val DashOff: Dp = 2.dp

/**
 * Ce qu'un champ numérique laisse entrer.
 *
 * Des chiffres et **au plus un** séparateur décimal, virgule ou point. Le clavier
 * décimal d'Android laisse passer plus que ça selon les fabricants, et « 12,5,3 » ne
 * se convertit en aucun nombre : la saisie deviendrait invalide sans que rien ne dise
 * pourquoi.
 *
 * Le champ **refuse** la frappe plutôt que de l'accepter puis de la nettoyer.
 * Nettoyer obligerait à réécrire le texte affiché, donc à repositionner le curseur —
 * exactement le défaut que [DraftTextField] évite.
 */
fun String.isNumberField(): Boolean =
    all { it.isDigit() || it in DECIMAL_SEPARATORS } && count { it in DECIMAL_SEPARATORS } <= 1

/**
 * Ce qu'un champ de valeur nutritionnelle laisse entrer : des chiffres, rien d'autre.
 *
 * **Les six valeurs sont des grammes entiers.** Personne ne compte les demi-grammes
 * de lipides, et une décimale affichée est une précision promise que la source ne
 * tient pas — CIQUAL donne 0,25 g de protéines pour une pomme parce que la mesure
 * est en dessous du seuil de quantification, pas parce qu'elle vaut un quart de
 * gramme ([D52][decisions]).
 *
 * Le séparateur décimal disparaît donc du clavier **et** du filtre : laisser taper
 * « 12,5 » pour l'arrondir ensuite obligerait à réécrire le texte affiché, donc à
 * repositionner le curseur — exactement ce que [DraftTextField] évite.
 *
 * [decisions]: docs/11-decisions.md
 */
fun String.isWholeNumberField(): Boolean = all { it.isDigit() }

/**
 * Ce qu'un champ d'une seule ligne laisse entrer : tout, sauf un retour à la ligne.
 *
 * La règle vaut **même quand le champ en montre deux**. Un champ qui se replie n'est
 * plus `singleLine` pour Compose, donc son clavier propose une touche entrée — et un
 * nom d'aliment coupé en deux par un saut de ligne se retrouverait tel quel dans le
 * journal, puis dans une sauvegarde, puis dans une recherche qui ne le trouve plus.
 *
 * Un refus plutôt qu'un nettoyage, comme pour [isNumberField] et pour la même raison :
 * réécrire le texte affiché obligerait à repositionner le curseur.
 */
fun String.isSingleLine(): Boolean = none { it == '\n' }

private const val DECIMAL_SEPARATORS = ",."
