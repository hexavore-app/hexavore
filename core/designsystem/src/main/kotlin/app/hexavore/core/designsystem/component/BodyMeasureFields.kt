package app.hexavore.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import app.hexavore.core.designsystem.R
import app.hexavore.core.designsystem.theme.Spacing
import app.hexavore.domain.profile.UnitSystem
import app.hexavore.domain.profile.centimetresToFeetAndInches
import app.hexavore.domain.profile.feetAndInchesToCentimetres
import app.hexavore.domain.profile.kilogramsToPounds
import app.hexavore.domain.profile.poundsToKilograms
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Les deux mesures du corps, saisies dans le système choisi et **rangées en métrique**.
 *
 * **Ici et non dans chaque écran** : le profil, l'onboarding et le journal de poids
 * posent la même question, et trois copies de la conversion auraient divergé le jour où
 * l'une d'elles apprend quelque chose. C'est la leçon que ce projet a déjà payée deux
 * fois.
 *
 * **La conversion vit au bord du champ.** Le formulaire garde des kilogrammes et des
 * centimètres ; ces composables les montrent autrement et retraduisent ce qu'on tape.
 * Rien ne dérive, parce que le champ est **non contrôlé** ([D45][decisions]) : il ne rend
 * une valeur que si quelqu'un a frappé une touche, et un profil qu'on ouvre puis referme
 * ressort avec le chiffre exact qui y était.
 *
 * **Ce qui ne va pas se dit sous le champ** ([D160][decisions]) : [problem] le colore et
 * écrit la plage attendue, en chiffres et dans le système choisi.
 *
 * [decisions]: docs/11-decisions.md
 */
@Composable
fun BodyWeightField(
    kilograms: Double?,
    units: UnitSystem,
    label: String,
    onKilograms: (Double?) -> Unit,
    modifier: Modifier = Modifier,
    problem: FieldProblem? = null,
) {
    val imperial = units == UnitSystem.IMPERIAL

    Checked(problem = problem, modifier = modifier) { flagged ->
        NumberField(
            value = kilograms?.let { if (imperial) kilogramsToPounds(it).rounded() else it },
            label = measureLabel(label, if (imperial) R.string.ds_unit_pound else R.string.ds_unit_kg),
            onValueChange = { saisi -> onKilograms(saisi?.let { if (imperial) poundsToKilograms(it) else it }) },
            modifier = Modifier,
            isError = flagged,
        )
    }
}

/**
 * La taille : un champ en métrique, **deux en impérial**.
 *
 * Personne n'énonce sa taille en pouces, donc le formulaire change de forme plutôt que
 * de demander un calcul. Les deux champs se relisent ensemble : taper les pieds sans les
 * pouces donne une taille ronde, ce qui est exactement ce qu'on voulait dire.
 */
@Composable
fun HeightFields(
    centimetres: Double?,
    units: UnitSystem,
    label: String,
    onCentimetres: (Double?) -> Unit,
    modifier: Modifier = Modifier,
    problem: FieldProblem? = null,
) {
    Checked(problem = problem, modifier = modifier) { flagged ->
        if (units == UnitSystem.METRIC) {
            NumberField(
                value = centimetres,
                label = measureLabel(label, R.string.ds_unit_cm),
                onValueChange = onCentimetres,
                modifier = Modifier,
                isError = flagged,
            )
        } else {
            FeetAndInchesFields(centimetres, label, onCentimetres, flagged)
        }
    }
}

/** Les deux champs de l'impérial, qui se relisent ensemble. */
@Composable
private fun FeetAndInchesFields(
    centimetres: Double?,
    label: String,
    onCentimetres: (Double?) -> Unit,
    flagged: Boolean,
) {
    val taille = centimetres?.let { centimetresToFeetAndInches(it) }

    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        NumberField(
            value = taille?.feet?.toDouble(),
            label = measureLabel(label, R.string.ds_unit_feet),
            onValueChange = { pieds -> onCentimetres(joined(pieds, taille?.inches?.toDouble())) },
            modifier = Modifier.weight(1f),
            isError = flagged,
        )
        NumberField(
            value = taille?.inches?.toDouble(),
            label = measureLabel(label, R.string.ds_unit_inches),
            onValueChange = { pouces -> onCentimetres(joined(taille?.feet?.toDouble(), pouces)) },
            modifier = Modifier.weight(1f),
            isError = flagged,
        )
    }
}

/**
 * Ce qu'un champ de mesure a à dire de sa valeur.
 *
 * @property settled `true` quand **aucune frappe de plus** ne fera rentrer la valeur dans
 *   sa plage : « 999 » ne redescendra pas en ajoutant un chiffre, et le message se dit
 *   aussitôt. `false` quand elle n'est peut-être que le début d'une valeur juste —
 *   « 17 » avant « 175 » — : il attend alors qu'on quitte le champ, faute de quoi chaque
 *   première frappe allumerait un rouge qui n'a rien à dire.
 */
@Immutable
data class FieldProblem(val text: String, val settled: Boolean)

/**
 * Un champ, et ce qui ne va pas dedans, **dit au bon moment**.
 *
 * Le moment est celui de [FieldProblem.settled] : tout de suite pour ce qu'aucune frappe
 * ne corrigera, à la sortie du champ pour le reste. Le focus se lit sur la colonne et non
 * sur un champ, parce que la taille en impérial en compte deux : passer des pieds aux
 * pouces n'est pas quitter la taille.
 */
@Composable
private fun Checked(problem: FieldProblem?, modifier: Modifier, field: @Composable (flagged: Boolean) -> Unit) {
    var typing by remember { mutableStateOf(false) }
    val shown = problem?.takeIf { it.settled || !typing }

    Column(modifier = modifier.fillMaxWidth().onFocusChanged { typing = it.hasFocus }) {
        field(shown != null)
        shown?.let {
            Text(
                text = it.text,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(start = Spacing.md, top = Spacing.xs),
            )
        }
    }
}

/** « Poids actuel » et « kg » deviennent « Poids actuel (kg) ». L'écran dit quoi, le composant dit en quoi. */
@Composable
private fun measureLabel(label: String, unitRes: Int): String =
    stringResource(R.string.ds_measure_label, label, stringResource(unitRes))

/**
 * Deux champs, une mesure.
 *
 * Vider les deux rend `null` — la taille n'est pas renseignée. Vider **un** des deux le
 * lit comme un zéro : « 5 pieds » tout court est une taille, pas une absence.
 */
private fun joined(feet: Double?, inches: Double?): Double? = when {
    feet == null && inches == null -> null
    else -> feetAndInchesToCentimetres(feet?.roundToInt() ?: 0, inches?.roundToInt() ?: 0)
}

/**
 * Un champ numérique qui refuse une frappe non numérique au lieu de la nettoyer.
 *
 * Nettoyer obligerait à réécrire le texte affiché, donc à repositionner le curseur
 * ([D45][decisions]).
 *
 * **Le texte tapé survit à la mort du processus** ([D174][decisions]). La valeur gardée
 * est un nombre, et un nombre ne se souvient pas d'une virgule en attente : « 64, »
 * revenait « 64 ». Le champ garde donc aussi son texte, et le reprend s'il dit encore
 * le même nombre — [fieldText].
 *
 * [decisions]: docs/11-decisions.md
 */
@Composable
private fun NumberField(
    value: Double?,
    label: String,
    onValueChange: (Double?) -> Unit,
    modifier: Modifier,
    isError: Boolean,
) {
    var typed by rememberSaveable { mutableStateOf<String?>(null) }

    DraftTextField(
        initial = fieldText(typed, value),
        onValueChange = { text ->
            typed = text
            onValueChange(text.toDecimalOrNull())
        },
        label = label,
        keyboardType = KeyboardType.Decimal,
        accept = { it.isNumberField() },
        modifier = modifier.fillMaxWidth(),
        isError = isError,
    )
}

/** Un dixième d'unité : 0,1 lb pèse 45 g, ce qui est déjà sous le bruit d'une balance. */
private fun Double.rounded(): Double = (this * TENTHS).roundToInt() / TENTHS

/**
 * Le texte d'un champ numérique quand il se recompose de zéro : ce qui avait été tapé,
 * **s'il dit encore le même nombre** que la valeur gardée ; sinon la valeur, écrite dans
 * la langue de l'application ([D173][decisions], [D174][decisions]).
 *
 * La valeur reste la vérité : un texte qui dirait autre chose qu'elle afficherait un
 * nombre que l'écran n'enregistrera pas.
 *
 * [decisions]: docs/11-decisions.md
 */
fun fieldText(typed: String?, value: Double?, locale: Locale = Locale.getDefault()): String =
    if (typed != null && typed.toDecimalOrNull() == value) typed else value?.asDecimalText(locale = locale).orEmpty()

private const val TENTHS = 10.0
