package app.hexavore.feature.entry

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import kotlinx.coroutines.delay

/**
 * L'ouverture d'un brouillon, **quand elle se fait attendre**.
 *
 * ### Ce qui se voyait
 *
 * Un fond vide. L'écran de validation n'affichait rien tant que le brouillon n'était pas
 * ouvert, et une proposition d'IA s'y ouvrait en appelant une seconde fois le
 * fournisseur : jusqu'à une minute de noir, sans rien pour dire qu'il se passait quelque
 * chose ([D176][decisions]). Cet appel se fait désormais pendant l'analyse ; ouvrir est
 * redevenu une lecture.
 *
 * ### Pourquoi un indicateur quand même, et pourquoi après un délai
 *
 * **Pour la lenteur qu'on n'a pas prévue** : un téléphone chargé, une base qu'on recopie.
 * Un fond vide qui dure se lit comme une application figée, et c'est le seul défaut que
 * cet écran ne doit plus jamais montrer.
 *
 * **Après un délai de grâce**, parce que l'ouverture se compte d'ordinaire en
 * millisecondes : un indicateur qui paraîtrait une image à chaque ouverture serait un
 * clignotement, pire que le vide qu'il remplace.
 *
 * [decisions]: docs/11-decisions.md
 */
@Composable
internal fun DraftOpening() {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(OPENING_GRACE_MILLIS)
        shown = true
    }
    if (!shown) return

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

/** Assez pour qu'une ouverture ordinaire ne fasse rien clignoter, trop peu pour qu'on doute. */
private const val OPENING_GRACE_MILLIS = 400L
