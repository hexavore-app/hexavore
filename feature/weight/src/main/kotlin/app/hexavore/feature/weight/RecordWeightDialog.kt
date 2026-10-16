package app.hexavore.feature.weight

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import app.hexavore.core.designsystem.component.BodyWeightField
import app.hexavore.core.designsystem.component.NeonDateField
import app.hexavore.core.designsystem.component.weightProblem
import app.hexavore.core.designsystem.theme.Spacing
import app.hexavore.domain.profile.BodyBounds
import app.hexavore.domain.profile.UnitSystem
import java.time.LocalDate

/**
 * Ajouter une pesée : un poids, une date, et c'est tout ([docs/02][parcours]).
 *
 * **La date par défaut est aujourd'hui**, et le poids proposé est le dernier connu :
 * une pesée varie de quelques centaines de grammes d'une fois sur l'autre, et
 * reproposer le dernier chiffre transforme la saisie en correction.
 *
 * **Aucune date à venir n'est proposable.** [NeonDateField] borne son sélecteur à
 * aujourd'hui plutôt que de refuser après coup : un choix impossible qu'on peut faire
 * quand même est un choix qu'il faut ensuite expliquer.
 *
 * **Un poids que ce corps ne peut pas peser se dit sous le champ** ([D160][decisions]),
 * jugé sur la taille du profil, avec la phrase de l'onboarding et du profil : c'est le
 * même champ. Le bouton s'éteint avec lui — et la phrase dit pourquoi, ce qu'un bouton
 * éteint seul ne fait pas.
 *
 * Le champ est **non contrôlé** ([D45][decisions]) : il garde sa propre valeur, et
 * l'écran n'y écrit rien pendant la frappe. Un champ décimal contrôlé par un `Double`
 * efface la virgule que l'on vient de taper, parce que « 82, » ne se relit pas en
 * nombre.
 *
 * [parcours]: docs/02-parcours-et-ecrans.md
 * [decisions]: docs/11-decisions.md
 */
@Composable
internal fun RecordWeightDialog(
    today: LocalDate,
    initialKg: Double?,
    units: UnitSystem,
    heightCm: Double?,
    onConfirm: (LocalDate, Double) -> Unit,
    onDismiss: () -> Unit,
) {
    // Gardes avec l'ecran (D166) : Android peut tuer l'application pendant qu'on va
    // chercher son poids ailleurs. Le jour en nombre de jours, le poids en kilogrammes.
    var epochDay by rememberSaveable { mutableLongStateOf(today.toEpochDay()) }
    val date = LocalDate.ofEpochDay(epochDay)
    // Ce qui est tape est dans le systeme choisi ; ce qui est enregistre reste en
    // kilogrammes, comme la colonne qui l accueille. Le champ fait la conversion.
    var weightKg by rememberSaveable { mutableStateOf(initialKg) }
    val accepted = weightKg?.takeIf { it in BodyBounds.weightsFor(heightCm) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.weight_add_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                BodyWeightField(
                    // Le poids garde et non celui d'ouverture : apres une recreation, le
                    // champ repart de ce qui etait tape. Il ne le lit qu'a la premiere
                    // composition, et la frappe ne le reinitialise donc pas.
                    kilograms = weightKg,
                    units = units,
                    label = stringResource(R.string.weight_field),
                    onKilograms = { weightKg = it },
                    problem = weightProblem(weightKg, heightCm, units),
                )
                NeonDateField(
                    value = date,
                    onValueChange = { epochDay = it.toEpochDay() },
                    label = stringResource(R.string.weight_date_field),
                    yearRange = (today.year - JOURNAL_YEARS)..today.year,
                    latest = today,
                )
            }
        },
        confirmButton = {
            // Desactive plutot que refuse : le domaine garde la meme regle en filet,
            // mais un bouton qui echoue en silence ne dit pas ce qui manque.
            TextButton(onClick = { accepted?.let { onConfirm(date, it) } }, enabled = accepted != null) {
                Text(stringResource(R.string.weight_add_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.weight_add_cancel)) }
        },
    )
}

/** Dix ans de journal : au-delà, la grille des années n'aide plus à retrouver un jour. */
private const val JOURNAL_YEARS = 10
