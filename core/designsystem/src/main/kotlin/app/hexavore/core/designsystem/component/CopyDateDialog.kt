package app.hexavore.core.designsystem.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.hexavore.core.designsystem.R
import app.hexavore.core.designsystem.theme.Spacing
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * Le calendrier où choisir le jour de la copie.
 *
 * ### Pourquoi il vit dans le design system
 *
 * Il ne servait qu'à l'écran de saisie. L'accueil copie désormais un plat depuis son
 * appui long, et le geste doit ouvrir **le même calendrier** — même titre, même
 * explication, mêmes jours permis ([D157][decisions]). Deux copies de cette boîte
 * auraient divergé à la première correction, et la limite des jours à venir a déjà été
 * corrigée une fois.
 *
 * ### Ce qu'il dit avant qu'on y touche
 *
 * Un calendrier qui s'ouvre ne dit pas ce qu'il attend. Le titre est donc une phrase
 * entière, à la taille d'un titre et non d'une étiquette, et une seconde ligne dit ce
 * qui arrivera — **l'original reste** ([D144][decisions]). Sans elle, le geste ressemble
 * à un déplacement, et on hésite à s'en servir.
 *
 * ### Le jour d'origine est déjà choisi
 *
 * Il sert deux fois : il montre d'où l'on part, et il rend la copie sur **le même jour**
 * — dupliquer un plat — accessible en un appui. Ouvrir sans rien de sélectionné laissait
 * un bouton gris sans dire ce qui l'allumerait.
 *
 * ### Pas de jour à venir
 *
 * Comme partout ailleurs : on note ce qu'on a mangé, et le futur ne se mange pas
 * ([décision par défaut n°10][parcours]). La limite se compte depuis **aujourd'hui** et
 * non depuis le plat : elle recevait la date du plat ouvert, si bien que depuis une
 * journée passée on pouvait recopier vers n'importe quel jour **sauf** aujourd'hui.
 *
 * @param today le jour de l'horloge, et non celui du plat : c'est la limite du
 *   calendrier.
 * @param source le jour du plat qu'on copie, déjà sélectionné à l'ouverture.
 *
 * [parcours]: docs/02-parcours-et-ecrans.md
 * [decisions]: docs/11-decisions.md
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CopyDateDialog(today: LocalDate, source: LocalDate, onPick: (LocalDate) -> Unit, onDismiss: () -> Unit) {
    val limite = remember(today) { today.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli() }
    val state = rememberDatePickerState(
        initialSelectedDateMillis = remember(source) { source.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli() },
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean = utcTimeMillis < limite
        },
    )

    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                enabled = state.selectedDateMillis != null,
                onClick = { state.selectedDateMillis?.let { onPick(it.toLocalDate()) } },
            ) { Text(text = stringResource(R.string.ds_copy_confirm)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(text = stringResource(R.string.ds_copy_cancel)) }
        },
    ) {
        DatePicker(state = state, title = { CopyTitle() })
    }
}

/**
 * Le titre de la boîte, et la phrase qui dit ce qui va se passer.
 *
 * **À la taille d'un titre, pas d'un gros titre.** Il était en `headlineSmall` : sur deux
 * lignes, il poussait le calendrier vers le bas au point que sa première semaine venait
 * buter contre les initiales des jours ([D150][decisions]). Un titre court et une ligne
 * d'explication suffisent à dire ce que la boîte attend.
 *
 * Il porte lui-même ses marges : la boîte ne lui en donne aucune, et sans elles il se
 * posait dans le coin arrondi, qui lui rognait sa première lettre.
 *
 * [decisions]: docs/11-decisions.md
 */
@Composable
private fun CopyTitle() {
    Column(modifier = Modifier.padding(start = Spacing.xl, end = Spacing.xl, top = Spacing.md)) {
        Text(
            text = stringResource(R.string.ds_copy_title),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = stringResource(R.string.ds_copy_explain),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Le jour que le calendrier a rendu.
 *
 * Il compte en UTC, et c'est voulu : la date choisie est un jour du calendrier, pas un
 * instant. La relire dans le fuseau local décalerait d'un jour tous ceux qui sont à
 * l'ouest de Greenwich.
 */
private fun Long.toLocalDate(): LocalDate = Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()
