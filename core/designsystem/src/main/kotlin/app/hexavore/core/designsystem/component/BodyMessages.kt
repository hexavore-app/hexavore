package app.hexavore.core.designsystem.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import app.hexavore.core.designsystem.R
import app.hexavore.domain.goal.TargetProblem
import app.hexavore.domain.profile.BodyBounds
import app.hexavore.domain.profile.FeetAndInches
import app.hexavore.domain.profile.UnitSystem
import app.hexavore.domain.profile.centimetresToInches
import app.hexavore.domain.profile.kilogramsToPounds
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

/*
 * Ce que les champs du corps disent quand une valeur sort de ce qu'un corps peut etre
 * (D160).
 *
 * Les phrases vivent ici, a cote des champs qui les portent : l'onboarding, le profil et
 * le journal de poids posent la meme question, et trois copies de "entre 37 et 245 kg"
 * auraient fini par annoncer trois plages.
 *
 * En chiffres, et dans le systeme choisi : "pour 5 pi 9 po, entre 82 et 540 lb" se
 * corrige, "valeur hors limites" ne dit pas dans quel sens aller. Une borne qui change
 * d'unite s'arrondit vers l'interieur : ce que la phrase annonce doit etre une valeur que
 * la regle accepte.
 */

/**
 * Ce que le champ de taille a à dire, ou `null`.
 *
 * `null` aussi pour une taille absente : elle manque, et l'écran le demande déjà.
 */
@Composable
fun heightProblem(heightCm: Double?, units: UnitSystem): FieldProblem? {
    val plausible = BodyBounds.HEIGHTS_CM
    if (heightCm == null || heightCm in plausible) return null

    return FieldProblem(
        text = stringResource(
            R.string.ds_body_height_range,
            height(plausible.start, units, Rounding.UP),
            height(plausible.endInclusive, units, Rounding.DOWN),
        ),
        settled = heightCm > plausible.endInclusive,
    )
}

/**
 * Ce que le champ de poids a à dire, jugé **sur la taille** quand elle est plausible.
 *
 * La phrase nomme alors la taille — « pour 175 cm, entre 37 et 245 kg » — : sans elle,
 * un poids refusé à 150 kg ne dirait pas que c'est le couple qui ne va pas.
 */
@Composable
fun weightProblem(weightKg: Double?, heightCm: Double?, units: UnitSystem): FieldProblem? {
    val plausible = BodyBounds.weightsFor(heightCm)
    if (weightKg == null || weightKg in plausible) return null

    val lightest = weight(plausible.start, units, Rounding.UP)
    val heaviest = weight(plausible.endInclusive, units, Rounding.DOWN)
    val text = if (heightCm != null && heightCm in BodyBounds.HEIGHTS_CM) {
        stringResource(R.string.ds_body_weight_range_for_height, height(heightCm, units), lightest, heaviest)
    } else {
        stringResource(R.string.ds_body_weight_range, lightest, heaviest)
    }
    return FieldProblem(text = text, settled = weightKg > plausible.endInclusive)
}

/**
 * Ce que le champ du poids visé a à dire.
 *
 * **Le moment suit le sens de la frappe.** Un poids trop haut pour perdre — « 95 » depuis
 * 88 — ne redescendra pas en ajoutant un chiffre, et se dit tout de suite ; un poids trop
 * bas peut n'être que le début d'un autre, et attend qu'on quitte le champ.
 */
@Composable
fun targetProblem(problem: TargetProblem?, units: UnitSystem): FieldProblem? = when (problem) {
    null -> null
    is TargetProblem.NotBelowCurrent -> FieldProblem(
        text = stringResource(R.string.ds_target_not_below, weight(problem.currentKg, units)),
        settled = true,
    )
    is TargetProblem.NotAboveCurrent -> FieldProblem(
        text = stringResource(R.string.ds_target_not_above, weight(problem.currentKg, units)),
        settled = false,
    )
    is TargetProblem.BelowLean -> FieldProblem(
        text = stringResource(
            R.string.ds_target_below_lean,
            height(problem.heightCm, units),
            weight(problem.leanKg, units, Rounding.UP),
            decimal(BodyBounds.LEAN_BMI),
        ),
        settled = false,
    )
    is TargetProblem.AboveRange -> FieldProblem(
        text = stringResource(
            R.string.ds_target_above_range,
            height(problem.heightCm, units),
            weight(problem.maxKg, units, Rounding.DOWN),
        ),
        settled = true,
    )
    is TargetProblem.NoRoomToLose -> FieldProblem(
        text = stringResource(
            R.string.ds_target_no_room,
            height(problem.heightCm, units),
            weight(problem.leanKg, units, Rounding.UP),
            decimal(BodyBounds.LEAN_BMI),
        ),
        settled = true,
    )
}

/**
 * Comment une valeur s'écrit : au dixième pour une mesure, à l'unité vers l'intérieur
 * pour une borne.
 */
private enum class Rounding { NEAREST, UP, DOWN }

@Composable
private fun weight(kilograms: Double, units: UnitSystem, rounding: Rounding = Rounding.NEAREST): String = when (units) {
    UnitSystem.METRIC -> stringResource(R.string.ds_value_kg, decimal(kilograms.rounded(rounding)))
    UnitSystem.IMPERIAL ->
        stringResource(R.string.ds_value_pound, decimal(kilogramsToPounds(kilograms).rounded(rounding)))
}

@Composable
private fun height(centimetres: Double, units: UnitSystem, rounding: Rounding = Rounding.NEAREST): String =
    when (units) {
        UnitSystem.METRIC -> stringResource(R.string.ds_value_cm, decimal(centimetres.rounded(rounding)))
        UnitSystem.IMPERIAL -> {
            val taille = feetAndInches(centimetresToInches(centimetres), rounding)
            stringResource(R.string.ds_value_feet_inches, taille.feet, taille.inches)
        }
    }

/** Les pouces s'arrondissent **avant** d'être répartis, comme dans le champ lui-même. */
private fun feetAndInches(inches: Double, rounding: Rounding): FeetAndInches {
    val total = when (rounding) {
        Rounding.NEAREST -> inches.roundToInt()
        Rounding.UP -> ceil(inches).toInt()
        Rounding.DOWN -> floor(inches).toInt()
    }
    return FeetAndInches(feet = total / INCHES_PER_FOOT, inches = total % INCHES_PER_FOOT)
}

private fun Double.rounded(rounding: Rounding): Double = when (rounding) {
    Rounding.NEAREST -> (this * TENTHS).roundToInt() / TENTHS
    Rounding.UP -> ceil(this)
    Rounding.DOWN -> floor(this)
}

/** « 88,4 » en français, « 88.4 » en anglais : le séparateur est celui de la langue. */
private fun decimal(value: Double): String = value.asDecimalText()

private const val TENTHS = 10.0
private const val INCHES_PER_FOOT = 12
