package app.hexavore.core.designsystem.component

import java.math.RoundingMode
import java.text.NumberFormat
import java.util.Locale

/**
 * Un nombre tel qu'on l'écrit dans la langue de l'application ([D173][decisions]).
 *
 * « 65,3 » en français, « 65.3 » en anglais, et « 64 » plutôt que « 64,0 ». Le profil
 * écrivait « Poids actuel : 65.3 kg » et la boîte de pesée préremplissait « 65.1 », par
 * un `toString()` qui ne connaît que le point, quand la liste des pesées disait
 * « 65,3 kg » à côté.
 *
 * **Sans séparateur de milliers**, parce que le même texte préremplit des champs : un
 * « 1 736 » à espace insécable ne passerait plus le filtre d'un champ numérique, qui
 * refuserait alors toute frappe. Arrondi au plus proche, la moitié vers le haut.
 *
 * La langue est celle que l'application impose : `AppCompatDelegate` en fait la langue
 * par défaut du processus, et c'est déjà ce que lisent les phrases du corps.
 *
 * [decisions]: docs/11-decisions.md
 */
fun Double.asDecimalText(fractionDigits: Int = 1, locale: Locale = Locale.getDefault()): String =
    NumberFormat.getNumberInstance(locale).apply {
        maximumFractionDigits = fractionDigits
        isGroupingUsed = false
        roundingMode = RoundingMode.HALF_UP
    }.format(this)

/**
 * Le nombre qu'un champ contient, virgule ou point : le clavier offre l'un ou l'autre
 * selon la langue. Vide ou illisible, `null` — inconnu n'est pas zéro.
 */
fun String.toDecimalOrNull(): Double? = trim().replace(',', '.').toDoubleOrNull()
