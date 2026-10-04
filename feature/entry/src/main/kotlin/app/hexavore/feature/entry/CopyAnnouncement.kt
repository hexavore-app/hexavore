package app.hexavore.feature.entry

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.res.stringResource
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Dit où la copie est partie, une fois, puis se tait.
 *
 * **Une copie ne se voit nulle part** : elle atterrit sur une journée qu'on ne regarde
 * pas, et l'écran d'où l'on vient n'a pas bougé d'un pixel. Sans ce mot, appuyer sur
 * « Copier » ne produirait aucun signe, et on s'y reprendrait à deux fois — ce qui
 * ferait deux copies ([D144][decisions]).
 *
 * **Le modèle est prévenu à la fin, et c'est obligatoire.** Le prévenir d'abord
 * paraissait plus prudent — la nouvelle ne traînait pas — et ne montrait rien du tout :
 * l'oublier change la clé de cet effet, donc l'annule, donc annule l'affichage qu'il
 * était en train de demander. `finally` couvre les deux sorties, celle où la barre a
 * été lue et celle où l'écran est parti avant.
 *
 * Dans son fichier parce que le seuil de fonctions par fichier a mordu, et le découpage
 * suit ce que les choses sont : c'est le troisième temps du geste de copie — l'icône, la
 * boîte, puis la nouvelle —, là où l'écran qui l'entoure assemble un formulaire.
 *
 * [decisions]: docs/11-decisions.md
 */
@Composable
internal fun AnnonceLaCopie(
    jour: LocalDate?,
    formatter: DateTimeFormatter,
    hote: SnackbarHostState,
    onShown: () -> Unit,
) {
    val libelle = stringResource(R.string.entry_copied_to, jour?.format(formatter).orEmpty())

    LaunchedEffect(jour) {
        if (jour == null) return@LaunchedEffect
        try {
            hote.currentSnackbarData?.dismiss()
            hote.showSnackbar(libelle)
        } finally {
            onShown()
        }
    }
}
