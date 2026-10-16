package app.hexavore.core.designsystem.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import app.hexavore.core.designsystem.R
import app.hexavore.domain.profile.BodyBounds
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * Un champ de date qui se **choisit**, jamais qui se tape.
 *
 * Demander « AAAA-MM-JJ » au clavier fait porter à l'utilisateur un format que la
 * machine sait très bien deviner elle-même : il tape, il se trompe d'un tiret, et le
 * champ reste vide sans dire pourquoi. Une saisie qui n'accepte qu'une écriture n'est
 * pas une saisie, c'est une devinette.
 *
 * **Le champ est en lecture seule et la boîte le recouvre.** `OutlinedTextField` ne
 * relaie pas les clics quand il est `readOnly`, et le désactiver le grise — ce qui
 * dirait « indisponible » alors qu'il est parfaitement utilisable. Une surface
 * transparente par-dessus reçoit le tap, et la sémantique est écrite à la main pour
 * que TalkBack annonce un bouton et non un champ de texte.
 *
 * La date s'affiche dans le **format local** — « 4 mars 1991 » — et non en ISO : ce
 * qu'on relit doit se lire, l'ISO n'existe que pour le stockage.
 *
 * @param yearRange les années proposées. Une date de naissance ne se cherche pas en
 *   feuilletant les mois : la grille des années est le seul chemin praticable, et la
 *   borner évite d'en dérouler deux mille.
 * @param earliest la date la plus ancienne qu'on peut choisir, quand la première année de
 *   la grille ne suffit pas à la dire : un âge se borne au jour, pas à l'année.
 * @param opening le mois sur lequel le calendrier s'ouvre **tant que rien n'est choisi**.
 *   Sans lui, il s'ouvre sur le mois en cours, ramené dans la grille — c'est-à-dire, pour
 *   une naissance, sur la dernière année permise ([D160][decisions]).
 *
 * [decisions]: docs/11-decisions.md
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NeonDateField(
    value: LocalDate?,
    onValueChange: (LocalDate) -> Unit,
    label: String,
    yearRange: IntRange,
    modifier: Modifier = Modifier,
    latest: LocalDate? = null,
    earliest: LocalDate? = null,
    opening: LocalDate? = null,
) {
    var picking by remember { mutableStateOf(false) }
    val formatter = remember { DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG) }
    val shown = value?.format(formatter).orEmpty()
    val description = if (value == null) label else "$label : $shown"

    Box(
        modifier = modifier.clearAndSetSemantics {
            contentDescription = description
            role = Role.Button
            onClick(label = null) {
                picking = true
                true
            }
        },
    ) {
        OutlinedTextField(
            value = shown,
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            label = { Text(text = label, color = MaterialTheme.colorScheme.onSurfaceVariant) },
            trailingIcon = { Icon(imageVector = Icons.Filled.DateRange, contentDescription = null) },
            modifier = Modifier.fillMaxWidth(),
        )
        // La surface qui recoit reellement le tap. Sans elle, il faut viser l'icone.
        Box(
            modifier = Modifier
                .matchParentSize()
                .clickable { picking = true },
        )
    }

    if (picking) {
        DatePicker(
            initial = value,
            opening = opening,
            yearRange = yearRange,
            allowed = Allowed(earliest = earliest, latest = latest),
            onDismiss = { picking = false },
            onPicked = {
                picking = false
                onValueChange(it)
            },
        )
    }
}

/** Les deux bornes au jour près, l'une ou l'autre facultative. */
private data class Allowed(val earliest: LocalDate?, val latest: LocalDate?) {
    operator fun contains(date: LocalDate): Boolean =
        (earliest == null || date >= earliest) && (latest == null || date <= latest)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DatePicker(
    initial: LocalDate?,
    opening: LocalDate?,
    yearRange: IntRange,
    allowed: Allowed,
    onDismiss: () -> Unit,
    onPicked: (LocalDate) -> Unit,
) {
    // **Seule une date choisissable se presente comme choisie**, et seul un mois de la
    // grille s'ouvre : Material refuse un etat initial hors de ses annees, et le refuse
    // en levant une exception. Une naissance enregistree avant que les bornes ne
    // bougent (D160) ouvre donc le calendrier sur le mois d'ouverture, sans selection.
    val selected = initial?.takeIf { it.year in yearRange && it in allowed }
    val shown = (selected ?: opening)?.takeIf { it.year in yearRange }

    val state = rememberDatePickerState(
        initialSelectedDateMillis = selected?.toUtcMillis(),
        initialDisplayedMonthMillis = shown?.toUtcMillis(),
        yearRange = yearRange,
        selectableDates = object : SelectableDates {
            // Une date de naissance dans le futur n'existe pas, et une echeance
            // passee non plus : la borne est portee par le selecteur plutot que par
            // un message d'erreur apres coup.
            override fun isSelectableDate(utcTimeMillis: Long): Boolean = utcTimeMillis.toLocalDate() in allowed

            override fun isSelectableYear(year: Int): Boolean = year in yearRange
        },
    )

    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = { state.selectedDateMillis?.let { onPicked(it.toLocalDate()) } },
                enabled = state.selectedDateMillis != null,
            ) {
                Text(stringResource(R.string.ds_date_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.ds_date_cancel)) }
        },
    ) {
        DatePicker(state = state)
    }
}

/**
 * La date de naissance : bornée aux âges que le calcul accepte, et **ouverte trente ans
 * en arrière**.
 *
 * ### Pourquoi le calendrier proposait 2013
 *
 * Rien n'étant choisi, il s'ouvrait sur le mois en cours, ramené dans sa grille d'années
 * — c'est-à-dire sur décembre de la dernière année permise : 2013, quand le plus jeune
 * âge accepté était treize ans. Il proposait ainsi, en premier, l'âge le moins probable
 * de tous ceux qu'il acceptait ([D160][decisions]).
 *
 * Trente ans en arrière est un point de départ au milieu des âges adultes, d'où la grille
 * atteint vite tous les autres. **Ce n'est pas une valeur proposée** : le champ reste vide,
 * et « Valider » ne s'allume qu'une fois un jour touché.
 *
 * **Une seule règle pour l'onboarding et le profil.** Les deux bornaient la grille
 * chacun de leur côté, avec deux copies des mêmes constantes ; ce sont désormais celles
 * de [BodyBounds], au jour près.
 *
 * [decisions]: docs/11-decisions.md
 */
@Composable
fun BirthDateField(
    value: LocalDate?,
    onValueChange: (LocalDate) -> Unit,
    label: String,
    today: LocalDate,
    modifier: Modifier = Modifier,
) {
    val allowed = BodyBounds.birthDatesOn(today)

    NeonDateField(
        value = value,
        onValueChange = onValueChange,
        label = label,
        yearRange = allowed.start.year..allowed.endInclusive.year,
        modifier = modifier,
        latest = allowed.endInclusive,
        earliest = allowed.start,
        opening = today.minusYears(OPENING_AGE),
    )
}

/** L'âge sur lequel s'ouvre le calendrier d'une naissance, en années. */
private const val OPENING_AGE = 30L

/**
 * Le sélecteur raisonne en millisecondes **UTC**, et il faut le prendre au mot.
 *
 * Convertir dans le fuseau local ferait basculer d'un jour toute personne à l'ouest de
 * Greenwich : la date choisie le 4 mars reviendrait le 3. C'est un décalage qu'on ne
 * voit qu'en voyageant, ou qu'en habitant du mauvais côté du méridien.
 */
private fun LocalDate.toUtcMillis(): Long = atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

private fun Long.toLocalDate(): LocalDate = Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()
